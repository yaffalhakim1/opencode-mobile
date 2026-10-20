# Task: OpenCode Mobile

## In progress

- [x] **Stuck on a long session**: history was fetched from the *start* of the
      conversation (`order=asc`, capped at 200), so the newest turns were
      unreachable. Now opens at the newest page and pages backwards on scroll.
- [x] Render the `question` tool as a card (it is a **form**, not a message)
- [x] Task/reasoning header: `labelMedium`, tighter padding, smaller chevron
- [x] Maestro flows: pairing, a real two-turn conversation, tools + question
- [x] Version 1.0.2 released

## Next

- [ ] Permission approval cards (`/api/session/{id}/permission`)
- [ ] Session search over message bodies, not just titles

## Done

- [x] Injected system messages (tool catalogs, rule sets) render as markdown and
      collapse to a row labelled by the document's own heading
- [x] Release signing key moved out of the public repo into CI secrets, and the
      exposed key rotated
- [x] Project scaffold, build, install, verified against the live server
- [x] Theme rebuilt on the brand green (`#26E085` dark, `#00DA7D` light)
- [x] Markdown rendering (GFM): headings, emphasis, strikethrough, inline code,
      fenced code, nested lists, task boxes, quotes, alerts, tables, rules, links
- [x] Fixed punctuation loss: apostrophes and parentheses were being dropped
- [x] Fixed reference links swallowing their label
- [x] Reasoning card shares the tool card surface (`AgentActivityCard`)
- [x] Composer: agent / model / effort selector sheet
- [x] Attach files (system picker, inline `data:` URI, 8 MB cap)
- [x] Scroll-to-bottom button: plain down arrow
- [x] QR pairing: parses the JSON payload the desktop app actually encodes
- [x] Prompts queue while the agent is busy (`delivery=queue`)
- [x] System back walks the route stack
- [x] Session list: group by project/recency, filter, search
- [x] New session in a chosen project
- [x] Agent/tool cards show a header only
- [x] GitHub repo, signed release workflow, `RELEASING.md`, `CHANGELOG.md`
- [x] Release v1.0.1 published

## Verified on device

- [x] Pairing by pasted link → session list (Maestro `pair.yaml` passes)
- [x] **Replies stream in without a refresh** (Maestro `streaming.yaml`)
- [x] Two-turn conversation with real prompts; both turns survive refresh
      (Maestro `conversation.yaml` passes)
- [x] Shell tool card expands to Arguments/Result; question card renders,
      answers, and the composer still works after
      (Maestro `tools.yaml` via `scripts/run-tools-flow.ps1`)
- [x] Long session (4000+ messages) opens at the newest turn, not the first
- [x] Back from chat returns to the list (BACK keyevent)
- [x] QR scan end to end (JSON payload) on a wiped install

## Bugs found while testing QR (all fixed)

- Camera frames closed before ML Kit read them
- One frame, many codes: single-use code redeemed repeatedly
- Analysis resolution too low (640×480 → 1280×720)
- Paired but stuck on connect: `AppRoot` never reloaded the saved connection
- Preview cropped the frame → `FIT_CENTER`
- Play-Services barcode model never downloads without the Play Store
- Unsigned release APK could not install at all

## Known gaps

- Permission approval cards not implemented
- Session search/pagination (the cursor is parsed but not walked)

