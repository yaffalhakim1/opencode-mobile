# Improvement report — OpenCode Mobile

**Date:** 2026-10-20
**Scope:** full read of `app/src`, CI, docs, and a live run of the unit suite.
**Verdict:** APPROVED WITH CHANGES — 3 real bugs, 1 structural violation, 11 smaller findings.

> **Status: 15 of the 16 findings are fixed.** The work landed in
> `073bffb` and the mention feature that followed. The suite is 186 tests,
> all passing, and every source file is within the 400-line budget. The
> details below are kept as the record of what was wrong and why the fix is
> shaped the way it is.

---

## 1. Summary

| Severity | Count | What it means |
|---|---|---|
| Critical | 2 | Data loss and a crash. Both user-visible, both reproducible. |
| Major | 4 | Silent data loss, main-thread jank, a hang, and a hard structural-budget violation. |
| Medium | 4 | Feature gaps and a resource leak. |
| Low / Docs | 5 | Polish, hardening, and documentation drift. |
| Fixed | 16 | Every finding above, plus the system-notice rendering (#16). |

The code is well organised for a personal project: the layering is clean, the
timeline reducer is pure and tested, the CI verifies the APK signature and
version before release, and the security posture is sound. The problems below
are not architectural debt — they are specific, fixable defects.

**Verification status:** the suite is 186 unit tests, all passing. A
regression test was written to prove finding #1, confirmed failing against
the original code, and kept as a permanent test.

---

## 2. The three that matter most

> All three are fixed. The text below is the record of what was wrong.

### 2.1 CRITICAL — Paging back through history is silently destroyed

**Where**

- `app/src/main/java/dev/opencode/mobile/data/chat/TimelineReducer.kt:30`
- `app/src/main/java/dev/opencode/mobile/ui/chat/ChatViewModel.kt:433` and `:463`

**What**

`TimelineReducer.merge` returns *only* the server's newest page:

```kotlin
fun merge(live: List<ChatEntry>, fromServer: List<ChatEntry>): List<ChatEntry> {
    val liveById = live.associateBy { it.id }
    return fromServer.map { server -> ... }   // live entries absent from fromServer are dropped
}
```

`scheduleResync` merges the live timeline against that newest-page snapshot,
and the comment on the next lines claims the opposite of what the code does:

```kotlin
entries = TimelineReducer.merge(current.entries, fromServer),
// "Only the newest window is replaced; anything the user already paged back
//  to stays loaded."      ← this is not what merge() does
```

`refresh()` goes further and does a flat `entries = entries` replace, throwing
away the paged-in history entirely.

**Why it fires**

Both paths are routine, not edge cases:

| Trigger | Path |
|---|---|
| Every burst of text deltas | `scheduleResync()` → `merge` |
| Turn succeeded / failed / interrupted | `refresh()` → full replace |
| Any SSE reconnect | `refresh()` → full replace |

**Proof**

A regression test was written and run against the current code:

```
ScratchOlderPageWipeTest > a resync keeps the older pages the user already loaded FAILED
  expected:<[old_1, old_2, old_3, new_1, new_2]> but was:<[new_1, new_2]>
```

**Impact**

The user scrolls up, pages in two or three older pages, sends a prompt, the
turn ends — and the entire scrollback is gone, with the view jumping back to
the newest 200 messages. The "Stuck on a long session" work recorded as done in
`task.md` is only half working: the app opens at the newest page, but nothing
keeps it there once a turn runs.

**Proposed fix**

`merge` must keep the prefix of `live` that precedes the snapshot's oldest
entry, so a snapshot updates the window it covers and leaves the pages outside
it alone:

```
live   = [old_1, old_2, old_3, new_1, new_2]      ← old_* paged in by the user
server = [new_1, new_2]                            ← the newest page only
result = [old_1, old_2, old_3, new_1, new_2]       ← old_* preserved
```

`refresh()` needs the same treatment: merge the snapshot into the live timeline
instead of replacing it, so a terminal event no longer discards paged history.

---

### 2.2 CRITICAL — Attachments with an unreported size bypass the 8 MB cap → OOM

**Where**

- `app/src/main/java/dev/opencode/mobile/ui/chat/ChatViewModel.kt:220` (`describe`)
- `app/src/main/java/dev/opencode/mobile/ui/chat/ChatSelection.kt:22` (`isTooLarge`)
- `app/src/main/java/dev/opencode/mobile/ui/chat/ChatViewModel.kt:241` (`encode`)

**What**

```kotlin
// ChatViewModel.kt:220 — size stays -1 when the provider reports no SIZE
var size = -1L
...
resolver.query(uri, null, null, null, null)?.use { cursor ->
    if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) size = cursor.getLong(sizeIndex)
}

// ChatSelection.kt:22 — -1 is not > MAX_BYTES, so the file is accepted
val isTooLarge: Boolean get() = sizeBytes > MAX_BYTES

// ChatViewModel.kt:241 — the whole file is then read into memory
val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
```

Many content providers (cloud drives, some document backends) return a null
`SIZE`. A 200 MB file picked from one of them is accepted, read whole,
base64-encoded (×1.33) and serialised into the request body.

Even *within* the 8 MB cap the peak allocation is ~50–70 MB: the raw byte
array, the base64 string, and the JSON body, with Kotlin/Java strings held as
UTF-16. That is a serious risk on a 128–256 MB heap.

**Impact**

Crash (OOM) on picking a large file, or on a mid-range device with a file near
the cap. The 8 MB cap that the README documents does not actually hold.

**Proposed fix**

Enforce the cap while reading rather than trusting metadata:

1. Treat an unknown size as *unverified*, not *acceptable*.
2. Stream the input through a counting loop and abort past `MAX_BYTES`, so a
   lying or missing size cannot get past the cap.
3. Base64 the stream in chunks instead of materialising a byte array, and
   build the request body from the encoded result rather than a second copy.

---

### 2.3 MAJOR — `ChatViewModel` exceeds every structural budget

**Where:** `app/src/main/java/dev/opencode/mobile/ui/chat/ChatViewModel.kt` (689 lines)

| Budget | Limit | Actual |
|---|---|---|
| Source file length | 400 LOC | **689** |
| Function length | 60 LOC | `handleEvent` ≈ **180** (lines 544–724) |
| Type fields | 8 | `ChatUiState` = **18** |

`handleEvent` is a single `when` over 15+ event types spanning text,
reasoning, tools, agent/model switches, forms, permissions and lifecycle.
Adding a new event type means editing a 180-line function, and there is no way
to test the event→state mapping without a live stream.

**Proposed fix**

```
ui/chat/
  ChatViewModel.kt        ← orchestration only: lifecycle, wiring, state updates
  ChatEventHandler.kt     ← event -> state, one small function per event family
  ChatTimelineState.kt    ← entries, pagination, scroll
  ChatComposerState.kt    ← draft, attachments, selection
  ChatBlockingState.kt    ← forms, permissions (things the agent waits on)
```

Each family becomes a handler with its own unit-testable mapping, and the
state classes drop under the field budget.

---

## 3. Full findings

### Major

| # | Finding | Location | Note |
|---|---|---|---|
| 4 | **Fixed.** **A failed attachment encode is silently dropped.** `mapNotNull { encode(it) }` discards the null when the content resolver throws. The file is never sent and the user is never told. | `ChatViewModel.kt:277` | Collect failures and surface them; do not clear the attachment strip until the request body is built. |
| 5 | **Fixed.** **Markdown re-parses the whole document on every streamed delta.** `remember(markdown) { parse(markdown) }` keys on the full string, which changes on every delta, so the document is re-parsed and every `AnnotatedString` rebuilt on the main thread at the delta rate. | `MarkdownText.kt:52` | The README's "does not re-parse on every recomposition" is true, but streaming is the case that matters. Throttle the parse (~100 ms) or make it incremental. |
| 6 | **Fixed.** **The form/permission safety net is off during a normal turn.** `watchForForms()` is called from exactly one place — after answering a form. `refreshPermissions()` runs once in `init` and never again. The event feed is live-only and drops events on a slow consumer or a disconnect (the project's own README says so), so a form or permission raised while the app is backgrounded is lost and the turn blocks with no way to answer it. | `ChatViewModel.kt:334`, `:99`, `:350` | This is the same hang class CHANGELOG 1.0.5 claims to have fixed. Start the poll when a turn starts and keep it running while `isRunning`. |

### Medium

| # | Finding | Location | Note |
|---|---|---|---|
| 7 | **Fixed.** **`loadOlder()` swallows the failure.** `catch (e: Exception) { copy(loadingOlder = false) }` — no error reaches the user. Paging fails, the list just stops growing, with no explanation. | `ChatViewModel.kt:514` | Surface the error in the same banner the rest of the screen uses. |
| 8 | **Fixed.** **The session list is hard-capped at 50 and the cursor is never walked.** `listSessions(limit = 50, order = desc)` with no cursor. CHANGELOG 1.0.6's "paged" list is UI paging *within* those 50. Sessions 51+ are unreachable. Search matches titles and project names only, never message bodies. | `OpenCodeApi.kt:58`, `SessionListShaper.kt:110` | The cursor machinery already exists for `/message`; the session endpoint just needs walking. `task.md` already lists message-body search as "Next". |
| 9 | **Fixed.** **The theme override is not persisted.** It lives in `remember { mutableStateOf<Boolean?>(null) }` in the activity, so pinning "always light" is lost on rotation or process death. | `MainActivity.kt:36`, `SettingsViewModel.kt:35` | `ConnectionStore` (DataStore) already exists and is the natural home. |
| 10 | **Fixed.** **OkHttp clients leak.** `AppRoot` builds a new `EventStream` per chat screen, and each `EventStream` lazily builds its own `OkHttpClient` with its own dispatcher, connection pool and task runner. None are shut down when the view model is cleared. `HttpClientFactory.base()` constructs a *fresh* client each call, so clients do **not** share a pool despite the comment claiming they derive "from the same connection pool". | `AppRoot.kt:231`, `EventStream.kt:39`, `HttpClientFactory.kt:23` | Share one client per connection, or release the streaming client in `onCleared()`. |

### Low

| # | Finding | Location | Note |
|---|---|---|---|
| 11 | **Fixed.** **Composer is inconsistent while running.** The attach button and the agent/model selector are disabled, but send stays enabled so a text prompt can be queued. You can queue text mid-turn but cannot attach a file to it. | `ChatComposer.kt:195`, `:216` | Either allow attaching while running, or disable sending too. |
| 12 | **Fixed.** **`isRunning` is set optimistically** before the server confirms the prompt, so the spinner flickers on a send that fails. It does roll back correctly, so this is cosmetic. | `ChatViewModel.kt:270` | Derive the running state from the server's execution events. |
| 13 | **Fixed.** **The plaintext password is held in `ConnectUiState`** for the process lifetime. The field is masked in the UI, but the plaintext lives in a `StateFlow`. | `ConnectViewModel.kt:56` | Let `ConnectionStore` own the token; the UI state needs only the URL. |
| 14 | **Fixed.** **A missing `keystore.properties` silently produced an unsigned release APK.** `signingConfigs.findByName("release")` yielded null and the build still succeeded. This is the "unsigned release APK could not install at all" bug from the CHANGELOG, waiting to recur. | `build.gradle.kts:70` | **Fixed.** The release build now fails with an explanatory message when the signing material is absent, and CI refuses to build when the secret is missing. |
| 15 | **Fixed.** **README `Not built yet` is stale.** It still lists permission approval cards, which shipped in 1.0.5. `task.md` `Known gaps` says the same. | `README.md:218`, `task.md:64` | Keep the docs in step with the code. |
| 16 | **Fixed.** **Server-injected system messages were rendered as a centred wall of unparsed text.** The tool catalogs, injected rule sets and tool lists the server sends arrived as `NoticeKind.System`, which was drawn as a single plain `Text`: 12sp, muted, `TextAlign.Center`, and with the markdown source unparsed — so the literal `#`, `##` and `-` markers were visible. Dense technical reference was being shown as a centred, grey, unstyled blob. | `Messages.kt:263` | **Fixed in 1.0.7.** System notices now parse as markdown, sit left-aligned at body size, and a long one collapses to a row labelled by the document's own heading. See `NoticeSummary.kt` and `SystemNotice.kt`. |

---

## 4. What is already good

Do not churn these — they are the reason the codebase is pleasant to work in.

- **The reducer is pure.** `TimelineReducer` has no Android or framework
  imports and 14 passing tests. That is exactly why finding #1 was findable
  at all: it took a five-line test to prove a bug that a stateful design would
  have hidden.
- **The event feed contract is understood.** The README states plainly that
  `/api/event` has no replay and that a slow consumer is dropped, and the
  client is built around that instead of pretending to be resumable.
- **CI is real.** It runs the unit tests, then verifies the APK signature and
  that `versionCode`/`versionName` match `version.properties` before the
  release is drafted.
- **Version derivation removes a whole bug class.**
  `major * 10000 + minor * 100 + patch`, with a `require()` guarding
  ambiguity, means version code and version name can never disagree.
- **Maestro plus `scripts/inject-form.ps1` is a good answer** to "how do you
  test a server-sent-event client" — the flows exercise a real server.
- **The security posture is sound.** Keystore-backed AES-256-GCM for the
  token, masked password field, `allowBackup="false"`, `ignoreUnknownKeys`
  with `explicitNulls = false`, camera permission requested at runtime and
  the app fully usable without it.
- **The palette reasoning is sound.** The brand green is a signal colour, not
  a surface colour, and `primary` is deliberately darkened in light mode
  because the brand green cannot carry text on a pale surface.

---

## 5. Environment note

The local Gradle transform cache was corrupted — the `metadata.bin` files
under `~/.gradle/caches/8.14.6/transforms/` were missing, producing
`Could not read workspace metadata from ...` on every task. Fixed by clearing
that directory; the build and the full suite then ran clean.

If that error appears, the fix is:

```sh
./gradlew --stop
Remove-Item -Recurse -Force "$env:USERPROFILE\.gradle\caches\8.14.6\transforms"
```

---

## 6. What was done, in this order

| Step | Work | Why this order |
|---|---|---|
| 1 | Fix #1 — `merge`/`refresh` preserve paged history | Data loss, and it is provable with a test |
| 2 | Fix #2 — enforce the attachment cap while streaming | Crash, and it shares the attachment code path with #4 |
| 3 | Fix #4 — surface failed attachment encodes | Silent data loss, same code path as #2 |
| 4 | Fix #3 — split `ChatViewModel` and its state | Unblocks #5 and #6, and makes the remaining chat work testable |
| 5 | Fix #5, #6 — parse throttling, form/permission polling | Both need the split to land cleanly |
| 6 | Fix #7–#10 — paging errors, session pagination, theme persistence, client reuse | Independent, can run in any order |
| 7 | Fix #11–#15 — polish, hardening, doc drift | Cheap |

### What the fixes added

- `TimelineReducer.merge` now keeps what is outside the server's window in
  both directions, and `nextOlderCursor` keeps the paging cursor pointing
  further back. Nine new reducer tests.
- `AttachmentPayload` reads a file in chunks and enforces the cap while
  reading, so a provider that reports no size cannot get past it.
- The chat state is split into the conversation, the composer, what the agent
  is blocked on, and the mentions. The view model went from 758 lines to 367
  code lines, and its 180-line event handler became eight small functions
  across two classes.
- `SettingsStore` persists the theme, and the settings screen offers System,
  Light and Dark rather than a switch that could not go back.
- The session list walks the server's cursor instead of stopping at 50.

### Still open

- Session search over message bodies. The server's own `search` parameter
  matches **titles only** (`like(SessionTable.title, ...)` in the server's
  source), so this needs a different approach than a query parameter.
- Steered prompts. `"queue"` is used; `"steer"` is not.
