# Task — OpenCode Mobile

## Done

- [x] Project scaffold, build, install, verified against the live server
- [x] Theme rebuilt on the brand green (`#26E085` dark, `#00DA7D` light)
- [x] Markdown rendering (GFM): headings, emphasis, strikethrough, inline code,
      fenced code, nested lists, task boxes, quotes, alerts, tables, rules, links
- [x] Fixed punctuation loss — apostrophes/parentheses were being dropped
- [x] Fixed reference links swallowing their label
- [x] Reasoning card now shares the tool card surface (`AgentActivityCard`)
- [x] Composer: agent / model / effort selector sheet
- [x] Attach files (system picker, inline `data:` URI, 8 MB cap)
- [x] Scroll-to-bottom button — plain down arrow, not a download glyph
- [x] QR pairing + pasted pairing link (manual URL/password still available)
- [x] Session title falls back to the first user line
- [x] Launcher icon and status-bar colours moved to the new palette
- [x] Dead code removed; 44 unit tests; release build signed and installable
- [x] README rewritten to match the code
- [x] Agent/tool cards show a header only (no preview line)
- [x] Paperclip glyph redrawn (the old arc geometry rendered as a squiggle)
- [x] GitHub repo, `.gitignore`, and a Release workflow that builds a signed APK
      and publishes it with a generated changelog

## Next — session list

- [x] Filters: All / Running / Today, plus text search over title and project
- [x] Group sessions by project
- [x] Group sessions by recency (Today / Yesterday / This week / Older)
- [x] Grouping toggle: project ⇄ updated
- [x] New-chat project picker — `POST /api/session` takes
      `location: { directory }`, populated from `GET /api/project`.
      Verified: picking "Mobile Apps" created a session with
      `location=Y:\Learn\Mobile Apps`.
- [x] Live sessions marked with a green dot (from `/api/session/active`)

## Verified on device (emulator-5554, live server)

- [x] Connect → session list with real sessions
- [x] Chat screen: user bubble, markdown answer, reasoning card, tool cards
- [x] Markdown renders correctly (apostrophes, parentheses, headings, bold)
- [x] Selection sheet lists real agents/models/effort counts
- [x] Effort switch persisted to the server (`variant` changed)
- [x] File picker opens; attachment queued; prompt sent with `files: 1`
- [x] Jump-to-bottom button appears when scrolled away
- [x] **QR pairing end to end** on the signed release APK
- [x] Signed release APK installs cleanly on a wiped device and launches
- [x] No crashes in logcat across all of the above

## Bugs found while testing QR (all fixed)

- **Camera frames were closed before ML Kit read them.** `ImageProxy.close()`
  ran immediately after the asynchronous `process()` call, invalidating the
  buffer. Now closed in `addOnCompleteListener`.
- **One frame, many codes.** A single frame can contain several copies of the
  same code; the detector reported each, so a single-use pairing code was
  redeemed repeatedly and every attempt after the first failed with "expired".
  The scanner now emits once via an atomic guard.
- **Analysis resolution was too low.** The default 640×480 left too few pixels
  per QR module; now 1280×720 via `ResolutionSelector`.
- **Paired but stuck on the connect screen.** `AppRoot` only read the saved
  connection at startup, so after pairing it routed to Sessions with a null
  connection and bounced straight back. It now reloads the store on success.
- **Preview cropped the frame.** Switched to `FIT_CENTER`.
- **Play-Services barcode model never downloads** on a device without the Play
  Store. Reverted to the bundled model.
- **Unsigned release APK** could not be installed at all. Added a signing config.

## Known gaps

- Permission approval cards are not implemented.
- Session search/pagination (the cursor is parsed but not walked).
- Queued/steered prompts (`delivery: "queue" | "steer"`) unused.
