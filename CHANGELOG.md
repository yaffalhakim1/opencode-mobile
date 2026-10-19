# Changelog

## [1.0.3]

### Android

- **Fixed: replies appeared only after you pulled to refresh.** Sending a message
  showed your own text and then nothing — the agent was answering, but the phone
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
  something, it waits for an answer before continuing — and the phone showed
  nothing at all, so the session simply looked stuck. Questions now render as a
  card with their choices, and answering one lets the agent carry on.

- **Task and reasoning cards are more compact.** Smaller labels and tighter
  spacing, so a run of tool calls reads as an activity log instead of a stack of
  panels taking a screen each.

## [1.0.1]

### Android

- **Fixed: scanning the pairing code failed with "that is not an OpenCode pairing
  link".** The code on screen is not a link, it is a small block of text holding
  the code and the address, and the app only accepted the link form — so every
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
