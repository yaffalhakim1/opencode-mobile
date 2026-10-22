# Implementation plan — push notifications (in-app ntfy)

Companion to `PUSH_NOTIFICATIONS_PLAN.md` (rationale) and `docs/PUSH_NTFY.md`
(wire contract). **Decision: in-app ntfy receiver — no second app.**
Status: implemented and building.

---

## 1. Verified facts

Checked against the running server (OpenCode v2.0.32) and the installed binary,
not the docs site, which mixes v1 and v2.

### The docs are wrong about two things

| Claim in docs | Reality in v2.0.32 |
|---|---|
| OpenAPI spec is at `/doc` | `/doc` serves the web-app HTML. The spec is at **`/openapi.json`** |
| Prompt body is `{ parts: [{type:"text",...}] }` | Body is flat **`{ text, files, skills, agents }`**; `parts` is v1 and is rejected |

The app already used the v2 shape, so this is a docs issue only.

### Event contract — captured from a live `/api/event` stream

| Event | `data` | Notify |
|---|---|---|
| `permission.asked` | `{ id, sessionID, action, resources[], save[], source?, message? }` | **yes** — turn blocked |
| `form.created` | `{ form: { id, sessionID, title, fields[] } }` | **yes** — question blocks the turn |
| `session.execution.failed` | `{ sessionID, error: { type, message, status? } }` | **yes** |
| `session.execution.started` / `succeeded` / `interrupted` | `{ sessionID }` | no |
| `session.idle` | `{ sessionID }` | no |
| `session.retry.scheduled` | `{ sessionID, attempt, at, error }` | folded into failure |

`form.created` is the one the original plan missed. A blocked `question` looks
exactly like a hung session from the phone.

### ntfy SSE, captured live

```
event: open
data: {"id":"…","event":"open","topic":"…"}

data: {"id":"…","event":"message","topic":"…","title":"…","message":"…","priority":4,"tags":["lock"],"click":"opencode://session/ses_X"}
```

The `open` handshake has no message and must not become a notification.

### Plugins cannot register HTTP routes

`addRoute` in the binary belongs to Effect's internal router; no plugin hook
exposes routing. Irrelevant for this design — the app never calls the server
about push — but it is why the FCM variant needed an endpoint and this one
does not.

---

## 2. The blocker

`EventStream` is owned by `ChatViewModel`, so the socket dies with the chat
screen. Background the app and there is nothing left to raise a notification.
The phone is behind NAT, so the server cannot call it either.

Two things are needed:

1. a relay both sides can reach — **ntfy**
2. something in the app that keeps a socket open while it is closed — a
   **foreground service**

(2) is the whole cost of this route. It is what removes the second app.

---

## 3. Architecture as built

```
plugin (event hook) ──POST──> ntfy.sh/<topic> ──SSE──> PushService
                                                          │
                                                    NtfyPayload
                                                          │
                                                    PushNotifier
                                                          │
                                              tap → opencode://session/<id>
                                                          │
                                                     MainActivity
```

---

## 4. What was built

| File | Responsibility |
|---|---|
| `NtfySubscriber.kt` | SSE to `<server>/<topic>/sse`, OkHttp, no resume |
| `dto/NtfyMessageDto.kt` | ntfy's wire shape |
| `NtfyPayload.kt` | Message → `PushMessage`; validates the click link. No `android.*`, so it is unit tested |
| `PushService.kt` | Foreground service, `specialUse`, reconnect with backoff |
| `PushMessage.kt` | The typed notification; parses a data map |
| `NotificationChannels.kt` | `blocked` (High), `failures` (Default), `push-service` (Min) |
| `PushNotifier.kt` | Builds and posts; `FLAG_IMMUTABLE`; per-session tag |
| `PushPreferences.kt` | Config reads for the service |

Supporting: `SettingsStore` (enabled, topic, server), `DeepLink` +
`SessionTarget`, `OpenCodeApi.session`, manifest, `ic_notification.xml`,
`AppContainer`, `AppRoot` (permission after first connection, deep-link
resolution), `NotificationsSection`.

---

## 5. Decisions worth keeping

- **`specialUse`, not `dataSync`.** `dataSync` is capped at 6 h/24 on
  targetSdk 35+ and would go silent every afternoon. This is the single most
  important platform detail in the branch.
- **The topic is validated as a link, not trusted.** The topic is a shared
  secret, so a publisher could otherwise make the app open an arbitrary URI.
  Only `opencode://session/<id>` is accepted.
- **The `open` handshake is not a notification.** It has the same JSON shape and
  no message.
- **The permission prompt comes after the first connection**, not on launch.
- **One socket.** The service cancels before resubscribing; two subscriptions
  would show everything twice.
- **No replay on reconnect.** ntfy's `since` would re-deliver shown messages.

---

## 6. Risks

| Risk | Mitigation |
|---|---|
| Topic leaks → file paths and error text readable | 32+ char random topic, never committed |
| A publisher makes the app open an arbitrary URI | click links are validated against the scheme |
| Notification approves a permission | no action buttons anywhere |
| Deep link opens a blank session | resolve via `/api/session/:id`, else land on the list |
| OEM battery managers kill the service | documented; the only fix is a battery exemption |
| Two sockets after a config change | cancelled before resubscribe |
| Doze delays delivery | foreground service is the mitigation available without FCM |

## 7. Not in scope

- Notifying on text deltas
- Approving permissions from the notification
- Multiple topics per install
- FCM (separate branch: no ongoing notification, but Play Services required)
