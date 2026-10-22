# Push — server side

The phone reports its FCM token to the server; the server plugin reads that list
and sends FCM messages. Two pieces, both outside the app:

```
app ──POST /api/push/token──> server ──writes──> ~/.config/opencode/push-devices.json
                                                          │
plugin (event hook) ──reads────────────────────────────────┘
        │
        └──POST firebase.googleapis.com/v1/projects/<id>/messages:send──> FCM ──> phone
```

---

## 1. The endpoint

`POST /api/push/token`

```json
{
  "token": "fcm-registration-token",
  "platform": "android",
  "appVersion": "1.0.15"
}
```

Behaviour:

| Case | Response |
|---|---|
| Accepted | `204 No Content` |
| `token` missing or blank | `400 InvalidRequestError` |
| Anything else | `204` — re-reporting the same token is not an error |

It must be **idempotent**: the app re-reports on every connection, on every
token refresh, and whenever the user taps "Refresh token" in settings.

The write target is `~/.config/opencode/push-devices.json`, which is what
`plugins/fcm-push.mjs` reads:

```json
[
  { "token": "…", "platform": "android", "appVersion": "1.0.15", "reportedAt": 1792627000000 }
]
```

A bare array of strings is also accepted by the plugin, so either shape works.

### Why the app reports the token at all

FCM can reach the phone, but it cannot tell the server *which* phone. This
endpoint is that link. There is no way for the app to call the plugin directly:
OpenCode plugins cannot register HTTP routes, so the endpoint and the plugin
communicate through a file.

### Security

This endpoint stores a token that lets its holder push notifications to the
device. It is behind the server's existing Basic auth, and the token is not
usable to read anything — only to send. If the server is exposed to the
internet, the password must be strong, which is already true of every other
endpoint.

### Until the endpoint exists

The app tolerates its absence: `registerPushToken` fails, `PushTokenReporter`
logs nothing to the user, and the Settings screen shows
"The server has not acknowledged this device yet." Nothing else degrades.

---

## 2. The plugin

`~/.config/opencode/plugins/fcm-push.mjs`

| Env var | Required | Meaning |
|---|---|---|
| `FCM_PROJECT_ID` | yes | Firebase project id |
| `FCM_SERVICE_ACCOUNT` | yes | absolute path to the service-account JSON, or the JSON inline |
| `FCM_DRY_RUN` | no | `1` logs what would be sent and calls nothing |

Events sent (verified against OpenCode 2.0.32):

| Event | `kind` | Channel |
|---|---|---|
| `permission.asked` | `permission` | `blocked` (High) |
| `form.created` | `question` | `blocked` (High) |
| `session.execution.failed` | `failure` | `failures` (Default) |

The data payload is exactly what `PushMessage.fromData` parses:

```
kind       permission | question | failure
sessionID  ses_…
title      shown as the notification title
body       shown as the notification body, truncated to 240 chars
tag        defaults to sessionID, so one chat collapses to one notification
```

**It must be a data message.** A `notification` block makes Android draw the
notification itself and `PushService.onMessageReceived` never runs — which would
lose the deep link.

---

## 3. Setup

1. Create a Firebase project, add an Android app with package `dev.opencode.mobile`
2. Download `google-services.json` into `app/` (the build detects it and applies
   the plugin; without it the app still builds, and Settings says push is
   unavailable)
3. Project settings → Service accounts → Generate new private key
4. Set the two env vars as **user** variables, then restart OpenCode
5. Install the app, pair a server, open Settings → Notifications → **Send test**

`Send test` posts a local notification. If it appears, the app side is proven
and any remaining problem is delivery; if it does not, the cause is the
permission or the build, both of which the screen names explicitly.

---

## 4. Limits worth knowing

- **No Play Services, no push.** FCM requires it. The app still works; it just
  cannot be woken in the background.
- **Token rotation.** FCM can retire a token at any time. `onNewToken` re-reports
  it, and the plugin logs `UNREGISTERED` when a token is dead.
- **Dead tokens accumulate.** The file is append-or-replace per token with no
  expiry sweep; a handful of stale entries only costs a failed request each.
