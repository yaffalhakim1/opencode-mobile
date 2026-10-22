# Push notifications — plan

**Decision:** FCM direct. The server plugin posts one FCM **data** message per
blocking event, straight to the phone. No second app, no persistent notification,
no foreground service.

Status: app side and plugin implemented; needs a Firebase project and the server
endpoint (`docs/PUSH_SERVER.md`) to deliver end to end. See
`implementation_plan.md` for the build order.

> **History.** This started as an ntfy-relay plan. ntfy was chosen because the
> phone is behind NAT and something reachable by both sides is required — but
> FCM is also such a thing, and it removes the second app and the foreground
> service entirely. The cost is a Firebase project, a service-account key, and a
> hard requirement on Google Play Services. `plugins/notify.mjs` (ntfy) is kept
> as a zero-config fallback.

---

## Why not the obvious approaches

The blocker is not notifications. It is that **the phone has no way to know a turn
started.**

`EventStream.kt` opens SSE to `GET /api/event` and is owned by `ChatViewModel`, so it
lives exactly as long as the chat screen. Its own doc comment says the feed is
volatile and events during a disconnect are lost. Background the app and the socket
dies — so there is nothing left to raise a notification.

The use case that matters is *"I started a turn at my desk and walked away"*. The
phone never saw that turn begin, so it cannot know it ended. **Only the server can
tell it.**

| Option | Verdict |
|---|---|
| WorkManager polling | **Rejected.** 15-minute minimum interval. The agent is blocked *now*. |
| Foreground service + SSE | Only covers turns started from the phone. Dies to aggressive OEM battery managers. |
| FCM | Free, but Play Services + Firebase project + service-account key on the server. Most coupled. |
| **ntfy + server plugin** | **Chosen.** Works when the app is closed, covers desk-started turns, no Google. |

## Architecture

```
opencode server
   └─ plugin (event hook)
        │  permission.asked      → "Permission needed: write src/auth.ts"
        │  session.execution.failed → "Turn failed: rate limited"
        ▼
     POST https://ntfy.sh/<secret-topic>
        ▼
     phone (ntfy app, or in-app subscription later)
        ▼
     tap → deep link opencode://session/<id> → the right chat
```

---

## Phase 0 — server plugin (no Kotlin) — **DONE**

Validated the whole idea before any app work.

File: `~/.config/opencode/plugins/notify.mjs` (`.mjs`, not `.js`: the extension is
what tells Bun the file is an ES module, which is also how the other local
plugin in this config is named).

```js
export const NotifyPlugin = async () => ({
  event: async ({ event }) => {
    if (event.type === "permission.asked") {
      // data is flat: { id, sessionID, action, resources[], save[], message?, source? }
      const what = `${event.data.action} ${event.data.resources.join(", ")}`
      await publish("Permission needed", what, event.data.sessionID)
    }
    if (event.type === "session.execution.failed") {
      // data is { sessionID, error: { type, message, status?, response?, retryAfterMs? } }
      await publish("Turn failed", event.data.error.message, event.data.sessionID)
    }
  },
})
```

**Verified against a live server and the installed binary** (OpenCode 2.0.32),
not the docs. Corrections to the original notes:

- `session.error` **does** exist, but it fires for errors that do not end a turn,
  so it is noise. `session.execution.failed` is the right one — `data` is
  `{ sessionID, error: { type, message, status?, response? } }`.
- `form.created` is the third event worth notifying on. A `question` tool call
  blocks the turn exactly like a permission does, and the original plan missed it.
- No token and no account are needed. ntfy.sh's free tier allows 250 messages a
  day; a turn produces a handful. Paid plans only add *reserved topic names*,
  which an unreserved random topic makes unnecessary.

Note `data.sessionID` only — **no session title**. The app resolves the title locally
from its session list, which it already has.

Config:
- `NTFY_TOPIC` from env, **never committed** (no token: not needed)
- `NTFY_SERVER` optional, defaults to `https://ntfy.sh`
- Self-hosting is optional and not being done

### Security, non-negotiable

An ntfy topic **is the password**. There is no account and no auth by default, so a
guessable topic leaks your agent's file paths and error text, and lets anyone buzz
your phone. Requirements:

1. Random topic, 32+ chars (`opencode-<random>`), or self-hosted with an access token
2. Never commit it — env var only
3. **No action buttons in the payload.** ntfy supports "Approve"/"Deny" buttons that
   fire raw HTTP; that would be an *unauthenticated* request able to approve a
   permission. Deep-link into the app and use the existing authenticated path.

---

## Phase 1 — app side (the real work)

### 1. Notification permission
`targetSdk 36` ⇒ Android 13+ requires a runtime request for `POST_NOTIFICATIONS`.
Manifest + a prompt. Best moment: after the first successful connection, not on
launch. Must degrade gracefully if denied — the app still works.

### 2. Notification channel
Two channels, because they deserve different interruption levels:

| Channel | Importance | For |
|---|---|---|
| `blocked` | High | `permission.asked` — the agent cannot continue |
| `failures` | Default | `session.execution.failed` |

### 3. Deep links — **biggest item**
Today `AppRoot` routes with an in-memory `Route` sealed interface and
`rememberSaveable`, with no URI handling. Needs:

- `opencode://session/<id>` intent filter on `MainActivity`
- Handle **both** cold start (`onCreate`) and warm start (`onNewIntent`) — warm start
  is the one that gets forgotten and is the common case, since the app is usually
  still in memory
- Route to `Chat` once the connection is loaded; the deep link can arrive before
  `ConnectionStore.current()` resolves
- A `PendingIntent` with `FLAG_IMMUTABLE` on the notification

### 4. Settings
Topic field + **"Send test"** button. Without a test button, "notifications don't
work" is indistinguishable from a bad topic, a dead server, or a denied permission.

### 5. Optional: receive in-app
Only if the second app annoys you. Two routes:
- **UnifiedPush** registration — correct FOSS answer, still needs a distributor app
- **Foreground service** subscribing to the topic — needs `FOREGROUND_SERVICE` plus a
  declared type on Android 14+, an ongoing notification, and OEM battery-exemption
  pain on Xiaomi/Samsung

---

## Behaviour details worth deciding

- **Suppress while foregrounded.** The user is looking at the session; a notification
  is noise.
- **Dedupe failures.** `retryAfterMs` and the `session.retry.scheduled` event mean one
  failing turn can emit several failures. Collapse per session within a window —
  ntfy supports updating a notification by reusing its id.
- **Content in the notification.** Permission resources are file paths and failures
  carry error text. Consider a "hide details on the lock screen" setting.
- **Stale taps.** A permission may be answered elsewhere before you tap. The deep link
  must land on the chat and let the existing polling reconcile, not assume the
  permission is still open.

---

## Risks

| Risk | Mitigation |
|---|---|
| Topic leaks | Random 32+ char topic, env-only, self-host if possible |
| Notification approves something | Never put action buttons in the payload |
| Deep link opens the wrong/blank session | Handle warm start; wait for connection before routing |
| Missed while Doze | ntfy uses a persistent connection (or UnifiedPush); acceptable for this use case |
| Silent non-delivery | "Send test" button in settings |

## Not in scope

- Notifying on every text delta (hundreds of pings — `session.idle` granularity is right)
- `session.idle` notifications — **deliberately excluded** per the chosen scope; add
  later as a third channel if wanted
- Answering permissions from the notification

## Order

1. Phase 0 — plugin + ntfy app. Notifications working today, zero Kotlin. Confirms the
   payloads carry enough to be useful.
2. Phase 1 — deep links, then permission/channel, then settings + test button.
3. Phase 2 — only if wanted.

## Open questions

1. ~~**Self-host ntfy, or `ntfy.sh`?**~~ **Answered: `ntfy.sh`, free tier, no
   account.** Self-hosting is optional and explicitly not being done.
2. **Where does the topic live on the phone** — typed into settings, or scanned from
   the same pairing QR? The QR already carries host + token, so a second field is a
   step backwards. Still open, but it only matters for the in-app receiver: with the
   ntfy app the topic is typed there once, and OpenCode Mobile needs nothing.
