# Releasing OpenCode Mobile

Android only, from GitHub Actions, as a signed APK on a GitHub release. There is
no auto-updater: users install the APK themselves.

The release path is [`.github/workflows/release.yml`](.github/workflows/release.yml):

| Job | Runner | Produces |
| --- | --- | --- |
| `version` | ubuntu-latest | reads `version.properties`, refuses a mismatched `v*` tag, refuses an already-published version |
| `build` | ubuntu-latest | `opencode-mobile-v<version>.apk`, signed, tests run first |
| `release` | ubuntu-latest | a **draft** GitHub release whose body is the `CHANGELOG.md` section for that version |

There is one platform, so there is no "did this release touch Android?" gate —
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

`master`, not `main` — this repo's default branch is `master`.

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
update — the classic silent-release bug. `deriveVersionCode` throws rather than
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
  the code and the address, and the app only accepted the link form — so every
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
- **A fix reads `**Fixed: <the symptom someone would have hit>.**`** — literally
  the words they saw or the thing they could not do. A new capability reads
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
# Or tag the bump commit — the tag must equal v<version>, or the `version` job
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

The APK is signed with `keystore/opencode-mobile.jks`, configured by
`keystore.properties`, both committed to this repo. That is deliberate: this is a
personal side-load build and CI needs them to produce an installable artifact.

Two consequences worth knowing:

- **Changing the key forces a reinstall.** Android refuses to update an installed
  app whose signature differs, so rotating the key means every user uninstalls
  first (`INSTALL_FAILED_UPDATE_INCOMPATIBLE`). The same is true switching
  between the debug and release builds locally.
- **If this ever ships publicly, rotate first.** Move both files to repository
  secrets and regenerate, *before* publishing to anyone — after that, rotation
  costs every user an uninstall.

Verify a build is signed and by which key:

```sh
"$ANDROID_HOME/build-tools/36.0.0/apksigner" verify --print-certs \
  app/build/outputs/apk/release/app-release.apk
```

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

The desktop app encodes `{"code":…,"urls":[…]}` — **JSON, not a URL**. Only its
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
| `conversation.yaml` | Two real prompts stream, both survive a refresh, back works |
| `tools.yaml` | A tool card expands to Arguments/Result; the question card renders and answers |

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
session by title — the server renames a session after its first turn, so the
original prompt text is not a stable selector.

## What a real failure looks like

- **`version` job fails on a tag run** — the tag disagrees with
  `version.properties`. Delete the tag, fix the file, re-tag.
- **`Extract changelog section` fails** — `CHANGELOG.md` has no
  `## [<version>]` heading for this version. Add it; do not lower the check.
- **`versionCode in the APK does not match`** — something bypassed
  `deriveVersionCode`. Check `app/build.gradle.kts` before re-running.
- **Nothing builds on a re-dispatch** — that version is already published.
  Bump, then dispatch.

A failed run leaves no release. Re-dispatch after fixing; every step is
idempotent for a version that has no published release yet.
