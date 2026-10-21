# OpenCode Mobile

A native Android client for a self-hosted [OpenCode](https://opencode.ai) server.
Kotlin, Jetpack Compose, Material 3.

## Why native

The app is network-bound, so raw throughput was never the reason. The wins that
actually matter on a phone are platform ones:

- **Keyboard insets.** Chat that stays above the IME instead of jumping.
- **Encrypted credential storage.** The server password is sealed with an
  AES-256-GCM key that lives in the Android keystore and never leaves it.
- **Background streaming.** SSE keeps running while the app is backgrounded.
- **Predictive back.** Native back gesture integration.

## Requirements

- Android 8.0 (API 26) or newer
- A running OpenCode server you can reach from the phone

## Connect

There are two ways in, and the app opens on the first.

**Scan a pairing code.** When the server is shared through a tunnel, it prints a
QR for a link of the shape `https://host/auth/connect/<code>`. Scanning it (or
pasting the link) does two things at once: it sets the host *and* redeems the
code for a token, so nothing has to be retyped. Codes are single-use and expire
in about five minutes.

**URL and password.** For a server on your own network, no pairing is needed:

```sh
opencode serve --hostname 0.0.0.0 --port 4096
```

Enter `http://<your-lan-ip>:4096` and the printed password.

Under the hood the two flows are the same: pairing just exchanges a code for a
token (`POST /api/pair` then `GET /auth/connect/{code}`) and stores that token as
the Basic-auth password.

## Architecture

```
core/net        OkHttp clients, connection value type, typed errors
core/security   Keystore-backed AES-GCM cipher for the password
data/remote     HTTP API wrapper, SSE feed, pairing client, DTOs
data/chat       UI timeline model, message mapping, pure reducers
data/settings   DataStore persistence
ui/theme        Colour, type, shape tokens
ui/markdown     GFM parser adapter, block model, Compose renderer
ui/components   Reusable chat primitives
ui/connect      Pairing (QR/paste) and URL + password
ui/sessions     Session list
ui/chat         Timeline, composer, selection sheet, live streaming
ui/settings     Theme and disconnect
```

One rule shapes the data layer: **the event feed is live-only**. `/api/event` has
no replay, and a slow consumer is dropped rather than buffered. So the client
never tries to repair a partial timeline. It applies deltas for the live feel,
then reconciles by refetching the message history when a turn ends or the stream
reconnects. `TimelineReducer` is pure, so that reconciliation is the only place
correctness depends on the server.

## Features

**Markdown.** Assistant text renders as GFM: headings, emphasis, strikethrough,
inline code, fenced code with a language label, nested lists, task checkboxes,
block quotes, GitHub alerts, tables with column alignment, and horizontal rules.
Parsing happens once per message into an immutable block model
(`ui/markdown/MdModel.kt`), so the renderer is a pure function of the document
and long answers do not re-parse on every recomposition.

**Images.** A model shows a screenshot by writing `![alt](path)` as its own
paragraph, and that path is on the machine running the server — the phone has no
such file. Such a paragraph becomes an image block, and the path is resolved
into a request for the server's own copy (`/api/fs/read/...`, with the project
as `location[directory]`). A `data:` URI is decoded locally instead, which is
how a picture the user attached comes back: Coil does not decode those itself.
A picture that cannot be loaded falls back to its description rather than
leaving a gap.

The user's own message is rendered through the same markdown path, so an
attachment the server stored as `files` shows as the picture it is rather than
as the literal word "image".

**Agent, model and effort.** One control in the composer opens a sheet with all
three, because they are a single decision about *who answers this*. The sheet is
populated from `/api/agent` and `/api/model`; effort options come from the
selected model's `variants`, so a model without variants simply hides the
control. Changes are pushed with `POST /api/session/{id}/agent` and
`.../model`, so they persist on the server and are visible to the desktop client.

**Attachments.** Files are picked through the system document picker and sent as
inline `data:` URIs. The prompt endpoint takes a single `uri` per file and
rejects a server-local path, which is the point: a phone cannot produce a
path on the server's filesystem. Base64 inflates by ~33%, so the picker rejects
anything over 8 MB before the request is built.

**Mentions.** Typing `@` in the composer searches the project for files, and
`/` lists the skills the server has registered. Picking one puts it in the
prompt text and attaches it: a mentioned file goes to the server as a `file:`
URI pointing at the server's own filesystem, so the bytes never travel, while
a mentioned skill is named by its id. Both carry the span of the prompt text
they cover, so the server knows which characters the file or skill was named
at — and a mention added later moves the ones that were already there.

The marker has to start a word, so an email address or a path in the middle of
a sentence does not open the list.

**Jump to bottom.** The timeline follows the tail only while the agent is
streaming. Scrolling up stops the follow and shows a button; the button (or
sending a prompt) returns to the newest message.

**Reasoning and tool cards.** Both are "the agent did something" rows, so they
share one surface, border and header rhythm (`AgentActivityCard`); only the
leading glyph and status pill differ. Reasoning collapses by default. Failed tool
calls open automatically so an error is never hidden behind a tap.

## Design notes

**Palette.** The brand green (`#26E085` dark, `#00DA7D` light) is a *signal*
colour, not a surface colour: it marks the logo, the caret and anything that is
live. Structure is carried by a near-neutral ramp with a faint green bias.
`primary` is deliberately darker than the brand green in light mode, because the
brand green cannot carry text on a pale surface; the roles split into an
accessible action colour and the raw mark colour.

**Material 3, without the Expressive API.** `MaterialExpressiveTheme`,
`MotionScheme.expressive()` and `ExperimentalMaterial3ExpressiveApi` are all
`internal` in material3 1.4.0, so the spring motion system is unreachable from
app code until 1.5.0 ships (which needs compileSdk 37). The visible part of
Expressive (the emphasized type weights, the corner scale and the tonal palette)
 is applied directly through `MaterialTheme`.

**Deliberate deviation.** The workspace rule asks for a uniform 24dp corner
radius. This project uses the M3 shape scale (16/20/28dp) instead, because the
brief was explicitly Material 3 and mixed fixed radii break the nesting rule
(`inner = outer − padding`). Spacing still follows the 8dp grid and 24dp screen
gutters.

**Icons.** `material-icons-core` plus glyphs drawn in `Glyphs.kt` on the same
24dp grid and 1.8dp stroke, rather than pulling in the multi-megabyte extended
pack. The QR, paperclip, sliders and scroll-to-bottom marks are hand-drawn
because the core set does not contain them.

## Build

```sh
./gradlew :app:assembleDebug
```

Install:

```sh
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

**To run it on your own phone**, build the signed release and install that:

```sh
./gradlew :app:assembleRelease
adb install -r app/build/outputs/apk/release/app-release.apk
```

`app-release.apk` is signed with a release key that is **not in this repo**. The
keystore lives in CI as a repository secret and is restored at build time; a
local release build needs an untracked `keystore.properties` pointing at a
keystore of your own. See [RELEASING.md](RELEASING.md) for the setup.

The key is not in the repo on purpose. The signing certificate is embedded in
every published APK, so a key that sits in a public repository is a key anyone
can use to sign an update. The key that was committed here was rotated when
the repository went public, which means **1.0.7 and later cannot be installed
over an earlier release** — uninstall the old build first.

Test:

```sh
./gradlew :app:testDebugUnitTest
```

The debug APK is ~38 MB because it bundles Compose `ui-tooling`; the minified
release build is ~23 MB, most of which is the barcode model.

### Pinned toolchain

Versions are pinned deliberately; several newer AndroidX artifacts declare
`minCompileSdk=37`, which needs an SDK platform that is not yet installable here.

| Component | Version |
|---|---|
| Gradle | 8.14.6 |
| AGP | 8.13.2 |
| Kotlin | 2.2.21 |
| Compose BOM | 2026.06.01 (UI 1.11.4, Material3 1.4.0) |
| OkHttp | 5.4.0 (5.5.0 requires compileSdk 37) |
| CameraX | 1.6.2 |
| ML Kit barcode (bundled) | 17.3.0 |
| intellij-markdown | 0.7.18 |
| compileSdk / targetSdk | 36 |
| minSdk | 26 |
| JDK | 17 |

The **bundled** barcode model is used, not the Play-Services variant. The
Play-Services one is ~19 MB smaller but downloads its model on first scan, which
never completes on a device without the Play Store. The scanner then reports
"Waiting for the barcode module to be downloaded" forever. That trade is not
worth it for a feature that must work on the first try.

## API surface used

| Method | Path |
|---|---|
| `GET` | `/api/info` — validate credentials |
| `POST` | `/api/pair` — mint a pairing code |
| `GET` | `/auth/connect/{code}` — redeem it for a token |
| `GET` | `/api/session` — list, paged with `cursor` |
| `POST` | `/api/session` — create |
| `DELETE` | `/api/session/{id}` |
| `GET` | `/api/session/{id}/message` — history |
| `POST` | `/api/session/{id}/prompt` — send (text + `files` + `skills`) |
| `POST` | `/api/session/{id}/interrupt` — stop |
| `POST` | `/api/session/{id}/agent` — switch agent |
| `POST` | `/api/session/{id}/model` — switch model and effort |
| `GET` | `/api/agent` — agent catalog |
| `GET` | `/api/model` — model catalog and variants |
| `GET` | `/api/skill` — skills, for `/` mentions |
| `GET` | `/api/fs/find` — files, for `@` mentions |
| `GET` | `/api/fs/read/*` — one file's bytes, for images in a reply |
| `GET` | `/api/event` — SSE |

Auth is `Authorization: Basic base64("opencode:" + password)`.

The location of a project is passed as `location[directory]=/absolute/path`, or
omitted to ask for the server's default one.

DTOs decode with `ignoreUnknownKeys = true`. The HTTP API is published as
experimental and adds fields between releases; unknown message types are dropped
rather than failing the whole page.

Two shapes are worth calling out because they are easy to get wrong:

- The prompt endpoint's file input takes `{ uri }`, while stored messages return
  `{ data, mime, source }`. Sending the response shape back is rejected with
  `Missing key at files[0].uri`.
- `variants` on a model are its reasoning efforts (`none`, `low`, `high`, …) and
  are passed as `model.variant`; an absent variant means the model's default.
- A `file:` attachment has to be an **absolute** path on the server, and a
  Windows path needs the extra leading slash or the drive letter is read as
  the host. A bare relative path is rejected.

## Not built yet

- Session search over message bodies (the server's own `search` only matches
  titles)
- Steered prompts (`delivery: "steer"` is unused; `"queue"` is used)

