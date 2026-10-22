# Push notifications — plan

**Decision:** in-app ntfy receiver. The app subscribes to an ntfy topic itself,
so no second app is installed. The cost is one quiet foreground-service entry in
the notification shade.

Status: implemented. See `docs/PUSH_NTFY.md` for the transport contract and
`implementation_plan.md` for the build order.

> **History.** This started as "ntfy app receives", which needed no Kotlin but
> installed a second app. The foreground-service route was chosen instead so the
> notification carries this app's icon and channels. FCM is the other option
> (no ongoing notification, but a Firebase project, a service-account key and a
> hard requirement on Play Services).
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

## Phase 0 — server plugin (no Kotlin)

Validates the whole idea before any app work. **Do this first.**

File: `~/.config/opencode/plugins/notify.js`

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

**Verified against the schema, not the docs.** The published docs list `session.error`;
that event **does not exist** in this version. The real one is
`session.execution.failed`, whose `data` is `{ sessionID, error: { type, message,
status?, response?, retryAfterMs? } }`. A plugin subscribing to `session.error` would
compile, run, and never fire.

Note `data.sessionID` only — **no session title**. The app resolves the title locally
from its session list, which it already has.

Config:
- `NTFY_TOPIC` and `NTFY_TOKEN` from env, **never committed**
- Self-hosted ntfy, or `ntfy.sh` with an access token

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

1. **Self-host ntfy, or `ntfy.sh`?** Self-hosted is free and private; `ntfy.sh` needs
   a token and trusts a third party with your file paths.
2. **Where does the topic live on the phone** — typed into settings, or scanned from
   the same pairing QR? The QR already carries host + token, so a second field is a
   step backwards.

