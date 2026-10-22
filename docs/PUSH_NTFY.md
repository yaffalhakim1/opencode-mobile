# Push — in-app ntfy receiver

The app subscribes to an ntfy topic itself, so **no second app is installed**.
The server publishes to the same topic through a plugin. ntfy is only a relay:
the phone is behind NAT, so both sides need a public endpoint to meet at.

```
openCode server ──plugin──> POST ntfy.sh/<topic> ──SSE──> app's foreground service
                                                              │
                                                              ▼
                                                     its own notification
                                                              │
                                                    tap → opencode://session/<id>
```

---

## Why a foreground service

Android will not let a process hold a socket open in the background. The only
supported way to keep the subscription alive with the app closed is a foreground
service, and a foreground service requires a visible notification.

That ongoing entry in the shade is the **entire cost** of this route, and it is
what buys not installing a second app.

### `specialUse`, not `dataSync`

`dataSync` is capped at **six hours in any twenty-four** for apps targeting API
35+. Past that the system calls `Service.onTimeout` and kills the service — the
notifications would silently stop every afternoon, which is worse than not
having them. `specialUse` has no such cap, and it is the honest label: this is
not a file transfer.

`targetSdk` is 36, so this matters today, not eventually.

Play Console asks for a declaration of the `specialUse` subtype. It is in the
manifest:

```xml
<property android:name="android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE"
          android:value="Keeps a push-notification subscription open …" />
```

The app is side-loaded, so the Play form does not apply; the property is there
because it is required by the platform, not by the store.

---

## Security: the topic is the password

ntfy has no accounts. Anyone who knows the topic can read every notification and
publish to it. So:

1. **Use a long random topic** — `opencode-` plus 32 hex characters.
   ```sh
   node -e "console.log('opencode-' + require('crypto').randomBytes(16).toString('hex'))"
   ```
2. **Never commit it.** The plugin reads `NTFY_TOPIC` from the environment; the
   app takes it from Settings, which is app-private DataStore.
3. **A click link is validated before it is trusted.** Anyone with the topic can
   publish, so `NtfyPayload.resolveTarget` accepts only
   `opencode://session/<id>` and drops anything else. Without that check, a
   stranger with the topic could make the app open an arbitrary URI.
4. **No action buttons in the payload.** ntfy's `Actions` header fires raw
   unauthenticated HTTP, so a button could approve a permission without the
   app's credentials.

---

## What the app parses

ntfy's SSE stream emits an `open` handshake and then one JSON object per
message:

```json
{
  "id": "…", "event": "message", "topic": "…",
  "title": "Permission needed",
  "message": "write src/auth.ts",
  "priority": 4,
  "tags": ["lock"],
  "click": "opencode://session/ses_abc"
}
```

| Field | Used for |
|---|---|
| `tags` | which channel — `lock`/`question` → `blocked`, `warning` → `failures` |
| `priority` | fallback when no tag is recognised; `>= 4` means "needs you now" |
| `click` | the session to open; **only** `opencode://session/<id>` is accepted |
| `title`, `message` | the notification text |

A message with no usable `click` is dropped rather than shown: tapping it could
only open the wrong place.

---

## The plugin

`~/.config/opencode/plugins/notify.mjs`

| Env var | Required | Meaning |
|---|---|---|
| `NTFY_TOPIC` | yes | the topic; nothing is sent without it |
| `NTFY_SERVER` | no | defaults to `https://ntfy.sh` |

| Event | `tags` | Channel |
|---|---|---|
| `permission.asked` | `lock` | `blocked` (High) |
| `form.created` | `question` | `blocked` (High) |
| `session.execution.failed` | `warning` | `failures` (Default) |

Cost: **$0**. ntfy.sh's free tier allows 250 messages a day with no account;
a turn produces a handful. Paid plans only add *reserved topic names*, which a
random topic makes unnecessary. Self-hosting is supported by the Server field
but is not required.

---

## Settings

`Settings → Notifications` has the topic field, the server field, **Send test**
and **Reconnect**.

`Send test` posts a local notification. If it appears, the app can notify and
any remaining problem is the subscription; if it does not, the screen says
whether the cause is the Android permission or the missing topic. Without that
button, "notifications don't work" is indistinguishable from a wrong topic, a
dead server, or a denied permission.

---

## Limits worth knowing

- **Doze and OEM killers.** A foreground service is not immune. Xiaomi, Samsung
  and others kill background services aggressively unless the app is
  battery-exempt. If delivery stops on a specific device, that is the first
  thing to check.
- **One socket.** The service cancels before resubscribing, so a config change
  cannot leave two subscriptions open and show everything twice.
- **No replay.** A reconnect resumes from now; ntfy's `since` parameter would
  re-deliver messages that were already shown.
- **The ongoing notification cannot be hidden.** Android requires it for a
  foreground service. Turning the feature off removes it.
