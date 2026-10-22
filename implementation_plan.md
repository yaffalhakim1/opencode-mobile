# Implementation plan — push notifications

Companion to `PUSH_NOTIFICATIONS_PLAN.md` (design rationale). This file is the
build record. **Decision: FCM direct.** Status: app side and plugin implemented
and compiling; end-to-end delivery needs a Firebase project and the server
endpoint.

---

## 1. Verified facts

Everything below was checked against the running server (OpenCode v2.0.32,
`opencode serve`) and the installed binary — not the docs site, which mixes v1
and v2.

### The docs are wrong about two things

| Claim in docs | Reality in v2.0.32 |
|---|---|
| OpenAPI spec is at `/doc` | `/doc` serves the web-app HTML. The spec is at **`/openapi.json`** (280 KB, 119 paths) |
| Prompt body is `{ parts: [{type:"text",...}] }` | Body is flat **`{ text, files, skills, agents, ... }`**. `parts` is the v1 shape and is rejected |

The app already uses the v2 shape (`PromptPayload.kt`), so this is a docs issue
only.

### Event contract — captured from a live `/api/event` stream

Envelope: `{ id, created, type, location: { directory }, data, durable? }`

| Event | `data` | Notify |
|---|---|---|
| `permission.asked` | `{ id, sessionID, action, resources[], save[], metadata, source?, message? }` | **yes** — turn is blocked |
| `permission.replied` | `{ sessionID, requestID, reply }` | clears it |
| `session.execution.started` | `{ sessionID }` | no |
| `session.execution.succeeded` | `{ sessionID }` | no |
| `session.execution.failed` | `{ sessionID, error: { type, message, status?, response? } }` | **yes** |
| `session.execution.interrupted` | `{ sessionID, reason }` | no |
| `session.idle` | `{ sessionID }` | no |
| `session.retry.scheduled` | `{ sessionID, assistantMessageID, attempt, at, error }` | folded into failure |
| `form.created` | `{ form: { id, sessionID, title, fields[] } }` | **yes** — question blocks the turn |

`permission.asked` and `form.created` are the two that block a turn. A
notification for either is the difference between "the agent is waiting" and
"the agent looks hung".

### Plugins cannot register HTTP routes

Confirmed in the binary: `addRoute` belongs to Effect's internal HTTP router, and
no plugin hook exposes routing. So the server can never be a webhook target and
the app cannot call the plugin directly. Endpoint ↔ plugin communication has to
go through a file. This is what forces the token-reporting design below.

### Endpoints the app did not use

| Method | Path | Why it matters |
|---|---|---|
| `POST` | `/api/experimental/fs/write` | Writes a file on the server. Available, but not used — a purpose-built endpoint is cleaner |
| `GET` | `/api/permission/request?location=` | **All** pending permissions for a project |
| `POST` | `/api/experimental/session/{id}/wait` | Long-poll until the session goes idle (204) |

---

## 2. The blocker, and why FCM

`EventStream` is owned by `ChatViewModel`, so the socket dies with the chat
screen. The phone is behind NAT, so the server cannot call it. Something
reachable by both sides is required.

FCM is that thing. It was rejected in the original plan as "most coupled", which
is true, but the coupling buys the two properties that matter:

- no second app installed
- no foreground service and no permanent notification

The costs, stated plainly:

| Cost | Detail |
|---|---|
| Firebase project | `google-services.json` in the build |
| Service-account key | on disk in `~/.config/opencode/`, never committed |
| OAuth2 in the plugin | RS256 JWT → token exchange, cached; `node:crypto` only, no dependency |
| Token provisioning | the app must tell the server *which* phone; no plugin route exists, so it is an endpoint + a file |
| Play Services | **required**; the app still works without it, it just cannot be woken |

The last one is not abstract here: this project deliberately ships the *bundled*
ML Kit barcode model rather than the Play-Services one because that variant
"never completes on a device without the Play Store". FCM reverses that decision
for the notification path only.

---

## 3. Architecture as built

```
server event ──> plugins/fcm-push.mjs
                   │  reads ~/.config/opencode/push-devices.json
                   │  signs a JWT, exchanges it for an access token
                   ▼
                 FCM HTTP v1 ──> phone
                                  │
                                  ▼
                          core/push/PushService (FirebaseMessagingService)
                                  │  PushMessage.fromData
                                  │  preference check
                                  ▼
                          core/push/PushNotifier
                                  │  channel by kind
                                  │  per-session tag
                                  ▼
                          opencode://session/<id>  ──> MainActivity
```

Token reporting runs the other way:

```
app connected ──> PushTokenReporter ──> POST /api/push/token
                                            │
                                            └──> push-devices.json (read by the plugin)
```

---

## 4. What was built

### App (`app/src/main/java/dev/opencode/mobile/core/push/`)

| File | Responsibility |
|---|---|
| `PushMessage.kt` | Parses the FCM data map into a typed message. No `android.*` imports, so it is unit tested |
| `NotificationChannels.kt` | `blocked` (High) and `failures` (Default) |
| `PushNotifier.kt` | Builds and posts the notification; `PendingIntent` with `FLAG_IMMUTABLE`; per-session tag |
| `PushService.kt` | `FirebaseMessagingService`; handles `onMessageReceived` and `onNewToken` |
| `PushPreferences.kt` | Synchronous reads of `SettingsStore` for the service callbacks |
| `PushTokenReporter.kt` | Fetches the token, reports it, remembers which server acknowledged it |

Supporting changes: `SettingsStore` (enabled / token / reported-to),
`OpenCodeApi.registerPushToken`, manifest (`POST_NOTIFICATIONS`, the service),
`ic_notification.xml`, `AppContainer`, `AppRoot` (permission request after the
first connection), `SettingsScreen` + `NotificationsSection`.

### Server (`~/.config/opencode/plugins/fcm-push.mjs`)

Publishes `permission.asked`, `form.created`, `session.execution.failed` as data
messages. `FCM_DRY_RUN=1` logs instead of sending. Missing configuration logs a
warning and disables itself rather than failing the turn.

### The endpoint that is still missing

`POST /api/push/token` — specified in `docs/PUSH_SERVER.md`. Until it exists the
app logs nothing to the user and Settings says the server has not acknowledged
the device.

---

## 5. Decisions worth keeping

- **Data messages, not notification messages.** A `notification` block makes
  Android draw the notification and `onMessageReceived` never runs, losing the
  deep link and the grouping.
- **`specialUse` was not needed.** FCM needs no foreground service at all, so
  the 6 h/24 h `dataSync` cap on targetSdk 35+ never applies.
- **The permission prompt comes after the first connection**, not on launch.
  Asking before the app has done anything trains people to refuse.
- **The token is reported per server.** Pairing a different server later means
  the new one has never heard of this device.
- **Denial is silent.** A refused permission leaves every other feature working.

---

## 6. Risks

| Risk | Mitigation |
|---|---|
| Service-account key leaks | path not inline, never committed, `~/.config/opencode/` only |
| Notification appears to approve something | no action buttons; a tap only opens the chat |
| Deep link opens a blank session | resolve via `/api/session/:id`, else land on the list |
| Stale notification after answering elsewhere | per-session tag means the next one replaces it |
| Token rotates silently | `onNewToken` re-reports; Settings shows the last 12 chars |
| No Play Services | documented; the app is fully usable, just not wakeable |
| Duplicate failure pings (`retry.scheduled` + `execution.failed`) | only the terminal event is sent |

## 7. Not in scope

- Notifying on text deltas
- Approving permissions from the notification
- Multiple servers per install
- Sweeping dead tokens out of `push-devices.json`
