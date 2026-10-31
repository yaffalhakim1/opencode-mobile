# Changelog

## [1.0.16]

### Android

- **You could not run a command — only mention a skill.** Typing `/` offered
  skills alone, and sending `/something` quietly attached a skill named after it,
  so what you asked to run never ran. `/` now offers the server's commands too,
  and picking one writes it into the prompt with the caret waiting for its
  arguments. If you send a command while the agent is still working, it waits for
  that reply instead of running alongside it.

- **Code in a reply could not be copied on a phone.** Selecting a fenced block
  meant dragging handles around a block that scrolls inside the message. Every
  code block and every shell result now has a Copy action, and it copies the
  whole block even when only part of it is on screen.

## [1.0.15]

### Android

- **Fixed: the agent's work was one grey wall, so you could not tell what was
  still running.** Every reasoning step and tool call in a turn sat inside a
  single panel separated by hairlines, which left the step in flight looking
  exactly like the five that had already finished. Each step is its own card now,
  and a step that stopped says so in the one red surface on the screen.

- **Fixed: a tool call gave no clue how much it returned.** Expanding it was the
  only way to find out whether it was three lines or three hundred. The row now
  states the count before you tap.

- **Search is always there, and the filters take one row instead of two.** The
  list used to hide search behind a toolbar toggle and spend its first two rows
  on Material chips, so the first session sat below the fold. Both are now one
  scrolling strip under a permanent field.

- **You can pin a session to the top.** Long-press a row. Pins are kept on the
  phone, so they survive a restart and do not depend on the server.

- **The agent sheet is three choices instead of twenty loose rows.** Agent,
  effort and model each sit in their own card with the title held in place while
  the model list scrolls, and the chosen row carries both a fill and a tick.

- **Settings shows its own theme.** Each palette is painted in its own colours,
  in the mode currently in effect, so the choice is made by looking at it rather
  than by reading its name. The connected server is stated on the screen instead
  of being implied.

## [1.0.14]

### Android

- **Fixed: a question from the agent was unreadable and unanswerable.** When the
  agent asked something, the app drew it as a tool row containing the raw
  arguments — `questions: [{"header":"...","options":[...]}]` — with no way to
  reply. The question now shows its own text and its options as tappable rows,
  with the description that explains each choice.

- The card says so plainly when a question can no longer be answered, instead of
  offering a button that does nothing.

## [1.0.13]

### Android

- **Seven themes to choose from.** Settings → Appearance → Palette now offers
  OpenCode (the app's own green), Midnight, Tide, Plum, Ember, Sand and Mono.
  Each is a real Material 3 scheme generated from a single seed colour, so every
  colour role agrees with the others instead of being hand-picked.

- **The palette and light/dark are separate choices.** A theme supplies both a
  light and a dark set, so picking one does not pin it to a mode, and following
  the system still works.

- Each theme is previewed in its own colours in the picker, in the mode currently
  in effect, so the choice is made by looking at the theme rather than its name.

## [1.0.12]

### Android

- **You can now steer a running turn instead of only waiting for it.** While
  the agent is working, the composer offers **Queue** or **Steer**. Steering
  sends your correction into the turn in flight, so the agent acts on it before
  it finishes, instead of the message sitting behind the turn.

- **Fixed: a queued message was a count, not your message.** It said "2 prompts
  queued" and nothing else, so you could not read back what you had sent, take
  it back, or push it forward. A waiting prompt now appears as the message you
  wrote, with **withdraw** and **send now** on it.

- **Fixed: links in a reply could not be tapped.** They were coloured like
  links and did nothing. Tapping one now opens it in whatever your phone uses.

## [1.0.11]

### Android

- **Fixed: a picture in a reply showed as the word "image".** When the agent
  showed a screenshot it wrote it as a picture, and the app read that as text,
  so the word "image" appeared where the screenshot should be. Pictures in a
  reply now load and display.

- **Fixed: a picture you sent was not shown either.** The same problem on the
  other side of the conversation — your own message showed the word "image"
  instead of the file you attached. Your messages are rendered properly now,
  and a picture you sent is still there when you reopen the session.

## [1.0.10]

### Android

- **Fixed: the session list only changed when you pulled it.** It showed what
  was true when you opened it, so a turn that finished on your machine still
  looked like it was running on your phone, and you had to refresh to see
  otherwise. The list now follows the server: a turn starting or finishing
  updates as it happens, and a session renamed or created appears without a
  refresh.

- **Fixed: the stop button disappeared in the middle of a long turn.** The
  conversation re-read the history as the reply arrived, and when that read
  landed between two steps it decided the turn had ended. The turn's state now
  comes from the server's own start and finish signals.

- **Fixed: choosing an agent or model on your computer did not reach the
  phone.** The app was listening for the wrong name, so the composer kept
  showing the old choice.

- **Fixed: "cannot reach the server" for a request that would have worked.**
  A phone moves between networks, and a name that failed to resolve for a
  second is usually fine on the next try. Reading the session list and the
  history now retries before showing an error. Sending a message does not
  retry, because that would run your turn twice.

- **The list tells you when it has stopped following the server**, so a list
  that has gone quiet is not mistaken for a session that has gone quiet.

- **The button back to the newest message is hidden when you are already
  there**, and appears when you scroll away.

## [1.0.9]

### Android

- **Fixed: a long reply grew past the bottom of the screen.** The conversation
  did not follow the text as it arrived, so by the time the reply finished you
  were looking at a part of it you had already read. The conversation now sits
  at the newest message and stays there, the way a chat does, and the answer
  grows upward from the bottom as it is written.

- **The button back to the newest message is always there.** It used to come
  and go as you scrolled, so it appeared and disappeared with no pattern you
  could learn. It is there now, wherever you are in the conversation.

## [1.0.8]

### Android

- **Fixed: scrolling back through a long conversation lost the history you had
  loaded.** Every time a reply arrived, the conversation was replaced with the
  newest part of it, so the messages you had scrolled up to read were gone
  again. The history you have loaded now stays where it is.

- **Fixed: a file the phone could not read was left out of the prompt without
  saying so.** The message went out without the attachment, and the attachment
  stayed in the composer looking as though it had been sent. It now stops and
  tells you which file was the problem, and a failed send puts your text and
  your files back.

- **Fixed: a question the agent asked could go missing.** The agent waits for
  an answer, so if the question did not arrive the session looked frozen with
  no way to continue. The app keeps checking for as long as the agent is
  working.

- **You can now name a file or a skill in the prompt.** Type `@` to search the
  project for a file and `/` to list the skills the server has. Picking one
  puts it in your message and attaches it, and a file is read on the server
  rather than copied across.

- **The tool catalogs the server sends are readable now.** They used to arrive
  as a wall of tiny centred text with the markup still showing. They collapse
  to a single row you can open, and expand into properly formatted text.

- **The session list goes past fifty.** The server hands out a page at a time
  and the app was only asking for the first one, so older sessions could not
  be reached. There is now a way to load the rest.

- **The theme choice sticks.** Choosing dark or light was forgotten when the
  app was closed. It is remembered now, and there is a way back to following
  the phone's setting.

## [1.0.7]

### Android

- **Fixed: the tool catalogs the server sends were unreadable.** The list of
  available tools arrived as a wall of small centred text with the markup still
  showing — literal `#`, `##` and `-` markers, and code samples stranded in the
  middle of the screen. They now collapse to a single row labelled by the
  document's own heading, and open into properly rendered markdown when you tap
  them.

- **The signing key was changed, so this build cannot be installed over an
  earlier one.** This repository is public now, and the signing key that was
  committed to it could have been used to sign an update that Android would
  happily install over the app you already have. The key was replaced and moved
  out of the repository. Uninstall the previous build before installing this
  one.

## [1.0.6]

### Android

- **A turn now reads as one card instead of a stack of boxes.** The agent works in
  steps, and each step used to draw its own panel, so a few tool calls filled the
  screen and the answer got lost between them. Reasoning and tool calls now share
  one card with a faint divider between rows, so the prose is what you read.

- **Tool rows say what happened.** They read "Ran command", "Read file",
  "Edited file" rather than the tool's internal name, with a check mark when the
  step succeeded.

- **The session list is shorter and paged.** Five sessions per group, with
  "See N more" for the rest, one-line titles, and the folder icon removed, since the
  project name already says where a session lives.

- **The header is the app's name.** "Sessions" plus the connected URL became
  "OpenCode Mobile"; the URL only ever truncated itself.

- **Code uses IBM Plex Mono, bundled with the app.** It previously used whatever
  monospace the phone happened to have, so code looked different on every device
  and matched neither the desktop app nor itself.

- **Fixed: the status bar was darkened in dark mode.** It is transparent now, and
  its icons follow the app's theme rather than the system's.

## [1.0.5]

### Android

- **Fixed: the agent's questions never appeared.** When the agent asked you
  something it waited for an answer, and the phone showed only a tool card stuck
  on "running", so the session looked hung with no way forward. The app was
  listening for the wrong signal and also discarded the event because the session
  id sits in a different place on it. Questions now render as a card with their
  choices, and answering lets the agent continue.

- **The agent's permission requests now appear too.** Reading a file the rules
  mark as "ask" stops the agent until you allow or deny it. The phone showed
  nothing, so the same session looked frozen. Requests now render as a card that
  names what is being asked, with **Deny**, **Allow once** and **Always allow**,
  and it says which pattern "always" would remember.

## [1.0.4]

### Android

- **Fixed: replies appeared only after you pulled to refresh.** Sending a message
  showed your own text and then nothing. The agent was answering, but the phone
  stayed silent until you refreshed, so the app looked broken. The live updates
  were arriving and being discarded: the first words of a reply always arrive
  before the message they belong to exists, and the app only knew how to add to a
  message it already had. Replies now appear as they are written.

- **A reply can no longer lose its last few words while it is being written.**
  The app refreshes a session's history in the background during a turn, and a
  refresh that finished slightly behind the live text could shorten a sentence
  that was mid-word. Background refreshes now keep whichever version is further
  ahead instead of overwriting.

## [1.0.3]

### Android

- **Fixed: replies appeared only after you pulled to refresh.** Sending a message
  showed your own text and then nothing. The agent was answering, but the phone
  stayed silent until you refreshed, so the app looked broken. The live updates
  were arriving and being discarded: the first words of a reply always arrive
  before the message they belong to exists, and the app only knew how to add to a
  message it already had. Replies now appear as they are written.

## [1.0.2]

### Android

- **Fixed: a long conversation stopped at its beginning.** Opening a session with
  a lot of history showed the first messages you ever sent and never the newest
  turn, so the session looked frozen hours in the past. The app was reading
  history from the start of the conversation and the server only returns a page
  at a time, so anything past that page was unreachable. Sessions now open at the
  newest message, and scrolling up loads earlier pages until you reach the start.

- **The agent's questions now appear in the app.** When the agent asks you
  something, it waits for an answer before continuing, and the phone showed
  nothing at all, so the session simply looked stuck. Questions now render as a
  card with their choices, and answering one lets the agent carry on.

- **Task and reasoning cards are more compact.** Smaller labels and tighter
  spacing, so a run of tool calls reads as an activity log instead of a stack of
  panels taking a screen each.

## [1.0.1]

### Android

- **Fixed: scanning the pairing code failed with "that is not an OpenCode pairing
  link".** The code on screen is not a link, it is a small block of text holding
  the code and the address, and the app only accepted the link form, so every
  scan was rejected even though the code was valid. Both forms now work, and the
  link you can copy from the desktop still works too.

- **Fixed: the newest turn was cut off partway through.** Opening a session while
  the agent was already working showed the answer only from the moment you
  connected, so a long turn appeared to stop mid-sentence. The session now
  catches up with what the agent has produced so far, and keeps doing so as it
  writes.

- **A prompt sent while the agent is busy is now queued instead of refused.**
  Previously the send button was disabled for the whole turn, so thinking of a
  follow-up meant waiting. You can send it now; it waits and runs when the agent
  is free, and the composer tells you how many are waiting. Stop is a separate
  button, so you can still interrupt.

- **Fixed: pressing back on a session closed the app.** It now returns to the
  session list, which is where you expect to land.

## [1.0.0]

First release.

- Connect by scanning the pairing code, pasting the link, or entering the server
  address and password.
- Streaming chat with the agent: markdown answers, reasoning, and tool calls.
- Choose the agent, model and reasoning effort for a session.
- Attach files to a prompt.
- Group sessions by project or by when they were last used, filter them, and
  search titles and project names.
- Start a new session in a specific project.



