# Push — server side

The phone reports its FCM token; the plugin reads it and sends FCM messages.
**No new endpoint is required** — OpenCode already has one that writes files.

```
app ──POST /api/experimental/fs/write──> .opencode/push-device.json   (in the project)
                                                   │
plugin (event hook) ──reads────────────────────────┘
        │
        └──POST firebase.googleapis.com/v1/projects/<id>/messages:send──> FCM ──> phone
```

---

## 1. Where the token lives

`.opencode/push-device.json`, **relative to the project directory**.

```json
{
  "token": "fcm-registration-token",
  "platform": "android",
  "appVersion": "1.0.15",
  "reportedAt": 1792627000000
}
```

### Why the project directory, not the home directory

The app cannot know the server's home path. Two things were tried and rejected
against a live 2.0.32 server:

| Attempt | Result |
|---|---|
| `~/.config/opencode/...` | **`~` is not expanded** — it wrote to a literal `~` folder inside the project |
| absolute path | works, but the app has no way to learn the server's home |

A relative path resolves to the project directory and works on every platform,
so that is what the app sends. The plugin looks in both places, so an install
that was configured by hand still works.

### Why not a purpose-built endpoint

An endpoint would be cleaner, but it means changing the OpenCode server. Writing
a file is a documented capability that already exists, and the plugin is a
consumer of the same file either way. No upstream change, nothing to maintain.

---

## 2. The plugin

`~/.config/opencode/plugins/fcm-push.mjs`

| Env var | Required | Meaning |
|---|---|---|
| `FCM_PROJECT_ID` | yes | Firebase project id |
| `FCM_SERVICE_ACCOUNT` | yes | absolute path to the service-account JSON, or the JSON inline |
| `FCM_DRY_RUN` | no | `1` logs what would be sent and calls nothing |

It searches for the token file in this order, first hit wins:

1. `<project>/.opencode/push-device.json` — what the app writes
2. `~/.config/opencode/push-device.json` — for a hand-configured install

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
title      the notification title
body       the notification body, truncated to 240 chars
tag        defaults to sessionID, so one chat collapses to one notification
```

**It must be a data message.** A `notification` block makes Android draw the
notification itself and `PushService.onMessageReceived` never runs — which would
lose the deep link.

---

## 3. Setup

1. Create a Firebase project, add an Android app with package `dev.opencode.mobile`
2. Download `google-services.json` into `app/` (the build detects it and applies
   the plugin; without it the app still builds and Settings says push is
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
- **One device per project.** The file holds a single device because the app
  rewrites it. Two phones on one project would overwrite each other; the plugin
  accepts a list if one is written by hand.
- **Token rotation.** FCM can retire a token at any time. `onNewToken` re-reports
  it, and the plugin logs `UNREGISTERED` when a token is dead.
- **The file is per project.** Pairing the same phone to a second project writes
  a second file there, which is the intended behaviour.

---

## 5. Secrets — this repository is public

Three files must never be committed. All three are in `.gitignore`:

| File | What it is | What leaking it costs |
|---|---|---|
| `app/google-services.json` | Firebase project config | project id and API key |
| `.opencode/push-device.json` | the token the app reported | anyone can push to that phone |
| `*-service-account.json` | the private key | **full send access to every device in the project** |

The service-account key is the dangerous one: it is a long-lived private key, and
it signs its own access tokens. Treat it like an SSH key.

The topic-style secrecy of the ntfy route does not apply here — FCM tokens are
issued by Google, not chosen by the user. But they are still credentials: a
leaked token lets its holder send a notification to that device, and a leaked
service-account key lets its holder send to all of them.

