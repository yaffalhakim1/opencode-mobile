# Firebase + FCM setup

Getting push notifications working for PR #12. About 12 minutes, one time.
**No credit card. No billing account.** FCM's free tier has no expiry.

What you get at the end: the agent asks a question or a turn fails, and your
phone buzzes — with no permanent notification and no second app installed.

---

## Before you start

| You need | Why |
|---|---|
| A Google account | to create the Firebase project. You already have one if the phone has Play Services |
| The phone has **Google Play Services** | FCM cannot deliver without it. A de-Googled ROM will not work |
| ~12 minutes | 6 steps, below |

Two values are fixed by the code and must be typed exactly:

| Value | Where it comes from |
|---|---|
| Package name | `dev.opencode.mobile` — from `applicationId` in `app/build.gradle.kts` |
| Firebase project id | whatever you name it; goes in `FCM_PROJECT_ID` |

---

## Step 1 — Create the Firebase project (3 min)

1. Open <https://console.firebase.google.com>
2. **Create a project** (or *Add project*)
3. Name it anything, e.g. `opencode-mobile`
4. **Disable Google Analytics** when offered — it is not needed and adds setup
5. Create

Wait for it to finish provisioning, then continue.

> **Note the project id.** It is *not* always the display name. Find it at
> ⚙️ *Project settings* → *General* → **Project ID**, e.g. `opencode-mobile-a1b2c`.
> You will need it in Step 5.

---

## Step 2 — Register the Android app (2 min)

Still in the project:

1. On the overview page, click the **Android** icon (*Add app → Android*)
2. **Android package name**: `dev.opencode.mobile`

   This must match exactly. A typo here is the single most common reason
   notifications never arrive — Google accepts the file, and delivery silently
   fails.
3. **App nickname**: optional, skip
4. **Debug signing certificate SHA-1**: **leave blank.** It is only needed for
   Google Sign-In and Dynamic Links, neither of which this app uses.
5. **Register app**

---

## Step 3 — Download `google-services.json` (1 min)

1. The next screen offers **Download google-services.json**
2. Put the file here, exactly:

   ```
   OpenCodeMobile/
     app/
       google-services.json      <-- here
   ```

3. Click **Next** through the remaining screens (the SDK snippets can be skipped —
   the Gradle dependency is already wired up in the PR)

**Verify it landed:**

```powershell
cd "Y:\Learn\Mobile Apps\OpenCodeMobile"
Test-Path app\google-services.json     # must print True
```

That file is already in `.gitignore`, so it will not be committed to the public
repo. Confirm:

```powershell
git status --short     # google-services.json must NOT appear
```

---

## Step 4 — Create the service-account key (2 min)

This is what lets your machine *send* the messages. It is a private key —
treat it like an SSH key.

1. ⚙️ *Project settings* → **Service accounts** tab
2. Click **Generate new private key**
3. Confirm — a `.json` file downloads

**Move it somewhere safe and permanent**, e.g.:

```powershell
New-Item -ItemType Directory -Force -Path "$env:USERPROFILE\.config\opencode\keys" | Out-Null
Move-Item "$env:USERPROFILE\Downloads\<the-downloaded-file>.json" `
          "$env:USERPROFILE\.config\opencode\keys\firebase-service-account.json"
```

**Never** put it inside the repo. `*-service-account.json` is in `.gitignore`
as a second line of defence, but do not rely on that.

> **This key can send push to every device in the project.** If it leaks, delete
> it in the Firebase console and generate a new one.

---

## Step 5 — Set the two environment variables (2 min)

The plugin reads exactly two variables:

| Variable | Value |
|---|---|
| `FCM_PROJECT_ID` | the **Project ID** from Step 1 |
| `FCM_SERVICE_ACCOUNT` | **absolute path** to the key from Step 4 |

### Option A — Windows GUI (survives restarts)

1. Press <kbd>Win</kbd>+<kbd>R</kbd>, type `sysdm.cpl`, Enter
2. **Advanced** tab → **Environment Variables…**
3. Under **User variables**, click **New** for each:

   ```
   Name:  FCM_PROJECT_ID
   Value: opencode-mobile-a1b2c        (your project id)

   Name:  FCM_SERVICE_ACCOUNT
   Value: C:\Users\yafit\.config\opencode\keys\firebase-service-account.json
   ```

4. OK on all three dialogs

### Option B — PowerShell (current session only, good for testing)

```powershell
$env:FCM_PROJECT_ID = "opencode-mobile-a1b2c"
$env:FCM_SERVICE_ACCOUNT = "$env:USERPROFILE\.config\opencode\keys\firebase-service-account.json"
```

**Prefer the path, not inline JSON.** An inline key ends up in your shell
history and in process listings.

### Restart OpenCode

Plugins load at startup. Fully quit the OpenCode desktop app and reopen it.
Closing the window is not enough — check Task Manager for leftover `OpenCode`
processes.

---

## Step 6 — Build, install, verify (2 min)

```powershell
cd "Y:\Learn\Mobile Apps\OpenCodeMobile"
git checkout feat/push-fcm
./gradlew.bat :app:assembleDebug
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

On the phone:

1. Open the app, pair with your server (scan the QR or enter URL + password)
2. Android asks **"Allow notifications?"** → **Allow**

   It asks *after* the first connection on purpose, not on launch.
3. Go to **Settings → Notifications**
4. Tap **Send test**

A notification should appear immediately. **If it does, the app half is proven**
and any remaining problem is delivery from the server.

Also check the screen says the device token was reported. If it says
*"The server has not acknowledged this device yet"*, see troubleshooting below.

---

## Step 7 — Test a real one

In OpenCode, start a turn that will ask you something — anything that triggers a
`question` tool, or a tool call that hits a permission prompt.

Lock your phone and walk away. You should get:

```
🔔 OpenCode Mobile
   Permission needed
   write src/auth.ts
```

Tap it → opens straight to that chat.

### If nothing arrives, check the plugin first

```powershell
# in an OpenCode session, or just watch for plugin log lines
```

Trigger an event and look for `[fcm-push]` lines. A quick sanity check without
sending anything real:

```powershell
$env:FCM_DRY_RUN = "1"
```

With dry-run on, the plugin logs what it *would* send. If those lines appear, the
plugin is firing and the problem is credentials or the token file.

---

## Troubleshooting

| Symptom | Cause | Fix |
|---|---|---|
| `Send test` does nothing | Notification permission denied | Android Settings → Apps → OpenCode Mobile → Notifications → Allow |
| Settings: *"no Firebase configuration"* | `google-services.json` missing or wrong folder | must be `app/google-services.json`; rebuild after adding |
| Settings: *"server has not acknowledged"* | token never reported | open a session once, or tap **Refresh token** |
| Plugin logs `FCM_PROJECT_ID or FCM_SERVICE_ACCOUNT is missing` | env vars not set, or OpenCode not restarted | set them, then fully quit and reopen OpenCode |
| Plugin logs `token exchange failed: HTTP 400` | wrong project id, or key belongs to another project | re-check both |
| Plugin logs `HTTP 404 UNREGISTERED` | token is dead (app reinstalled) | open the app; `onNewToken` re-reports it |
| Plugin logs nothing at all | plugin not loaded | confirm `~/.config/opencode/plugins/fcm-push.mjs` exists, then restart |
| Everything logs fine, no notification | Play Services missing on the device | FCM cannot work; no workaround |

### Confirm the token file exists

The app writes it through OpenCode's own `fs/write`:

```powershell
cd "Y:\Learn\Mobile Apps\OpenCodeMobile"
Get-Content .opencode\push-device.json
```

You should see something like:

```json
{"token":"eXaMpLe…","platform":"android","appVersion":"1.0.15","reportedAt":1792631801359}
```

If that file is missing, the app never reached the server — check the pairing.

---

## What you will and will not see

| | |
|---|---|
| Permanent notification | **no** — FCM wakes the app without one |
| Second app to install | **no** |
| Sound / vibration | yes, when it actually matters |
| Works with the app closed | yes |
| Works in Doze | yes — that is FCM's job |
| Requires Play Services | **yes** |

You get notified on exactly three events:

| Event | Notification |
|---|---|
| `permission.asked` | "Permission needed — write src/auth.ts" |
| `form.created` | "Pick a database — Which database?" |
| `session.execution.failed` | "Turn failed — rate limited" |

Nothing else. No text deltas, no "turn finished" spam.

---

## Secrets checklist

Before you push anything:

- [ ] `app/google-services.json` — ignored, not committed
- [ ] `.opencode/push-device.json` — ignored, not committed
- [ ] `firebase-service-account.json` — **outside the repo**, in `~/.config/opencode/keys/`
- [ ] `git status --short` shows none of the above

Verify in one command:

```powershell
cd "Y:\Learn\Mobile Apps\OpenCodeMobile"
git status --short
```

The repository is public. The service-account key is the one that matters: it can
send push to every device in your project.
