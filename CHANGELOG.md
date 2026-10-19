# Changelog

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
