## [1.0.7]

### Android

- **Fixed: the tool catalogs the server sends were unreadable.** The list of
  available tools arrived as a wall of small centred text with the markup still
  showing — literal `#`, `##` and `-` markers, and code samples stranded in the
  middle of the screen. They now collapse to a single row labelled by the
  document's own heading, and open into properly rendered markdown when you tap
  them.
