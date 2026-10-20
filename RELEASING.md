# Releasing OpenCode Mobile

Android only, from GitHub Actions, as a signed APK on a GitHub release. There is
no auto-updater: users install the APK themselves.

The release path is [`.github/workflows/release.yml`](.github/workflows/release.yml):

| Job | Runner | Produces |
| --- | --- | --- |
| `version` | ubuntu-latest | reads `version.properties`, refuses a mismatched `v*` tag, refuses an already-published version |
| `build` | ubuntu-latest | `opencode-mobile-v<version>.apk`, signed, tests run first |
| `release` | ubuntu-latest | a **draft** GitHub release whose body is the `CHANGELOG.md` section for that version |

There is one platform, so there is no "did this release touch Android?" gate.
every release builds the APK.

## The loop

Every change follows the same five steps.

```
1. Branch          fix/… or feat/… off a freshly-fetched master
2. PR              open it, then wait for checks to go green
3. Squash merge    gh pr merge <n> --squash --delete-branch
4. Bump            edit version.properties + add the CHANGELOG section
5. Release         dispatch the workflow, check the draft, publish it by hand
```

This repo's default branch is `master`, not `main`.

A merge into `master` runs **nothing**; tests only run inside the release
workflow. So step 2 is the only place a change is verified before it ships.

```sh
git fetch origin master && git checkout master && git pull --ff-only origin master
git checkout -b fix/what-is-wrong

# … work, commit …

gh pr create --base master --title "fix(scope): what changed" --body-file /tmp/body.md
gh pr checks <n> --watch
gh pr merge <n> --squash --delete-branch

git checkout master && git pull --ff-only origin master
```

## Step 4: the bump, and the two version sites

There is one number, written once, and one number derived from it.

| File | Field | Who reads it | Consequence if stale |
| --- | --- | --- | --- |
| `version.properties` | `version` | the release workflow, `app/build.gradle.kts`, artifact names | tag mismatch fails the run |
| `CHANGELOG.md` | `## [<version>]` section | the release body | the run **fails** rather than shipping raw commits |

`versionName` and `versionCode` are **derived**, never hand-written:

```kotlin
// app/build.gradle.kts
versionName = appVersion                              // from version.properties
versionCode = deriveVersionCode(appVersion)           // major*10000 + minor*100 + patch
```

`1.0.1` → `10001`, `1.0.2` → `10002`. Strictly increasing by one per release,
which is what an installer compares. Hand-writing `versionCode` is what lets it
repeat, and an APK whose `versionCode` did not increase is never offered as an
update. That is the classic silent-release bug. `deriveVersionCode` throws rather than
encode an ambiguous number if a component ever reaches 100.

To bump:

```sh
# 1. the number
printf 'version=1.0.2\n' >> version.properties   # or edit the line
# 2. the notes
$EDITOR CHANGELOG.md
# 3. prove the tree agrees
./gradlew :app:assembleRelease
"$ANDROID_HOME/build-tools/36.0.0/aapt2" dump badging \
  app/build/outputs/apk/release/app-release.apk | head -1
```

The `build` job re-checks that the APK's `versionCode` and `versionName` match
`version.properties` and fails if they do not, so a half-bump cannot ship.

### The CHANGELOG entry format

The release body is read by someone deciding whether to install this build, so
every entry describes what changed **for them**. The shape, from 1.0.1:

```markdown
## [1.0.1]

### Android

- **Fixed: scanning the pairing code failed with "that is not an OpenCode pairing
  link".** The code on screen is not a link, it is a small block of text holding
  the code and the address, and the app only accepted the link form, so every
  scan was rejected even though the code was valid. Both forms now work, and the
  link you can copy from the desktop still works too.
```

The rules that make it readable:

- **`## [<version>]` matching `version.properties` exactly.** The workflow
  extracts the body by this heading; a mismatch fails the run.
- **One `###` platform heading.** This repo ships Android only, so it is always
  `### Android`.
- **One bullet per user-visible change**, bolded through the first sentence.
- **Lead with the symptom, not the change.** Say what was wrong or missing and
  what it cost the reader, *then* what it does now. The symptom is how someone
  recognises their own bug; the fix is how they know it is gone.
- **A fix reads `**Fixed: <the symptom someone would have hit>.**`**. Write the
  words they saw or the thing they could not do. A new capability reads
  `**<what it does now>.**` with no prefix.
- **Second person, past tense for the bug and present for the behaviour.** "every
  scan was rejected" → "both forms now work".
- **No internals.** No file paths, function names, commit hashes, or PR numbers.
  The reader has never seen `PairingLink.parse` and does not need to:

  ```markdown
  - Parse JSON pairing payloads in PairingLink (#1).
  ```

  is the same change as:

  ```markdown
  - **Fixed: scanning the pairing code failed with "that is not an OpenCode
    pairing link".** …
  ```

- **Write it once, for the release.** Fold refinements of an unreleased feature
  into its own bullet rather than adding a second entry.

## Step 5: release and publish

Either trigger works, and they are equivalent for a version not yet published:

```sh
# No tag needed. Releases whatever version.properties says, as v<version>.
gh workflow run release.yml --ref master
```

```sh
# Or tag the bump commit. The tag must equal v<version>, or the `version` job
# exits 1 before anything builds.
git tag -a v1.0.2 -m "OpenCode Mobile v1.0.2"
git push origin v1.0.2
```

A run **skips the build** if that version is already published (drafts do not
count). Bumping is therefore mandatory: re-dispatching without a bump is a
no-op, not a rebuild.

Then watch it, check the draft, and publish:

```sh
gh run watch <run-id> --exit-status
gh release view v1.0.2 --json isDraft,assets
gh release edit v1.0.2 --draft=false      # the one manual step
```

Before publishing, check:

- `version`, `build` and `release` all succeeded.
- The release body is the CHANGELOG section, not a commit list.
- The asset is `opencode-mobile-v1.0.2.apk` and `apksigner verify` passed in the
  log.
- The APK's `versionCode` is one higher than the previous release.

## Signing

The APK is signed with a release key that is **not in this repo**. The keystore
lives in CI as a repository secret and is restored by the `build` job before
Gradle runs.

The reason is that the signing certificate is embedded in every published APK.
A key that sits in a public repository is a key anyone can use to sign an
update that Android would accept over the installed app. This repository was
private when it was written and public when it was published, so the key that
was committed here was **rotated** and moved into secrets.

The signing secrets are:

| Secret | What it holds |
| --- | --- |
| `ANDROID_KEYSTORE_BASE64` | The `.jks`, base64-encoded |
| `ANDROID_KEY_ALIAS` | The key alias |
| `ANDROID_KEY_PASSWORD` | The store and key password |

Two consequences worth knowing:

- **The key was rotated, so the last rotation costs every user an uninstall.**
  Android refuses to update an installed app whose signature differs, so
  `1.0.7` cannot be installed over `1.0.6` or earlier
  (`INSTALL_FAILED_UPDATE_INCOMPATIBLE`). Uninstall the old build first. The
  same is true switching between the debug and release builds locally.
- **Rotating again costs the same.** Generate a new keystore, replace the three
  secrets, and every user uninstalls again. Plan a rotation for an event worth
  the reinstall, not for a routine release.

A local release build needs the same two files, untracked:

```sh
# keystore.properties, next to the .jks it points at
storeFile=keystore/opencode-mobile.jks
storePassword=<the password>
keyAlias=opencode
keyPassword=<the password>
```

Without them the release build **fails** rather than producing an unsigned APK
that nobody can install. Debug and test tasks do not need the key.

Verify a build is signed and by which key:

```sh
"$ANDROID_HOME/build-tools/36.0.0/apksigner" verify --print-certs \
  app/build/outputs/apk/release/app-release.apk
```

### Which key

| Key | SHA-256 of the signing certificate | Status |
| --- | --- | --- |
| `F4:B2:4B:7A:29:BF:56:8E:D4:7B:54:C8:1C:BA:C7:14:60:83:F5:14:AF:BA:76:39:59:4F:E7:B1:95:41:B9:32` | RSA-4096, valid to 2056 | **Current.** Signs 1.0.7 and later. |
| `B0:8A:C4:02:C8:A1:5A:BF:78:67:56:72:ED:1B:4D:72:0F:CF:06:DA:7D:E9:85:3D:EF:8D:FC:6F:92:3D:2D:94` | RSA-4096, valid to 2054 | **Retired.** Signed 1.0.1 to 1.0.6. It was in the repository when the repository went public, so it is burned. |

The retired certificate is still embedded in every APK published up to 1.0.6,
and it stays in the repository's history. Purging the history would not help:
the certificate is in the binaries too, and the certificate is all that is
needed to sign an update. The only real mitigation was to rotate, which is
what happened before 1.0.7 was published.

## Testing the build before a release

Validate the **release** variant, not debug. Debug bundles Compose `ui-tooling`
and the inspector, which hides exactly what matters before shipping, and the
release APK is ~24 MB against debug's ~39 MB.

```sh
./gradlew :app:testDebugUnitTest      # 65 unit tests
./gradlew :app:assembleRelease        # signed, minified
```

A debug build has a different signature, so installing one over the other fails:

```sh
adb uninstall dev.opencode.mobile
adb install -r app/build/outputs/apk/release/app-release.apk
```

### Checking the QR scan by hand

Pairing is a user action and cannot be automated end to end without a device
holding a code. To exercise the scan path on an emulator, point its camera at a
rendered code:

```sh
# The emulator maps this image onto its sensor; tiling the code means a crop
# still contains one complete copy.
emulator -avd <name> -camera-back "imagefile:/path/to/qr.png"
```

The desktop app encodes `{"code":…,"urls":[…]}`. That is JSON, not a URL. Only its
"Copy link" button produces a `/auth/connect/…` URL. Both must parse; a change to
`PairingLink` should keep a test for each.

### Maestro flows

`maestro/` holds the device flows. Maestro is installed at
`C:\Users\yafit\maestro\maestro\bin\maestro.bat` (not on `PATH`).

```powershell
$maestro = "C:\Users\yafit\maestro\maestro\bin\maestro.bat"

& $maestro test maestro/                       # every flow
& $maestro test maestro/conversation.yaml      # real prompts, refresh, back
& $maestro test maestro/pair.yaml -e PAIRING_LINK=https://host/auth/connect/CODE
pwsh -File scripts/run-tools-flow.ps1          # tools + the question card
```

| Flow | What it proves |
| --- | --- |
| `pair.yaml` | Pasting a pairing link stores a token and lands on the list |
| `streaming.yaml` | A reply appears on its own, with no refresh — the regression for dropped deltas |
| `conversation.yaml` | Two real prompts stream, both survive a refresh, back works |
| `question.yaml` | The question card renders, answers, and clears (`scripts/run-question-flow.ps1`) |
| `permission.yaml` | The permission card renders and a decision clears it |
| `tools.yaml` | A tool card expands to Arguments/Result (`scripts/run-tools-flow.ps1`) |

Two flows need a harness rather than a bare prompt, because whether the agent
raises a question or a permission request depends on the model and on the
server's permission rules:

```powershell
pwsh -File scripts/run-question-flow.ps1   # injects a pending question form
pwsh -File scripts/run-tools-flow.ps1      # prompts a shell call, injects a form
```

`question.yaml` originally asked the agent to use the tool, which passed once and
then failed because the model answered directly instead. Injecting the form makes
the UI deterministic while still exercising the path the app owns: render the
card, submit the answer, clear it.

Two things make these flows fussier than they look, and both have cost time:

- **`hideKeyboard` presses BACK**, which this app treats as "go up a screen". A
  flow that hides the keyboard mid-session navigates away instead. Use it only
  where leaving the screen is harmless, and prefer tapping a visible target.
- **Maestro matches whole nodes, not substrings.** An answer rendered inside one
  text node cannot be matched by a word from the middle of it. Assert on the
  user's own message or on discrete labels ("Result", "Arguments").

`tools.yaml` needs `scripts/run-tools-flow.ps1` because the `question` tool is a
**form the server holds open**, and whether an agent calls it depends on that
agent's toolset. The script creates a session, runs a shell prompt, injects a
form, and passes the generated title to the flow. The flow then opens that
session by title. The server renames a session after its first turn, so the
original prompt text is not a stable selector.

## What a real failure looks like

- **`version` job fails on a tag run**: the tag disagrees with
  `version.properties`. Delete the tag, fix the file, re-tag.
- **`Extract changelog section` fails**: `CHANGELOG.md` has no
  `## [<version>]` heading for this version. Add it; do not lower the check.
- **`versionCode in the APK does not match`**: something bypassed
  `deriveVersionCode`. Check `app/build.gradle.kts` before re-running.
- **Nothing builds on a re-dispatch**: that version is already published.
  Bump, then dispatch.

A failed run leaves no release. Re-dispatch after fixing; every step is
idempotent for a version that has no published release yet.


