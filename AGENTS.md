# Migration logging

- Log executed migrations at INFO level through the plugin logger. Include context
  for the work being performed, e.g. `Migrating language files` or
  `Migrating legacy messages to lang/own.yml`.
- After the entire migration chain has completed, been validated and saved, emit
  one overall message: `Configuration migration <from> -> <to> completed.`.
  Use `unversioned` when no configuration version was present.
- Do not emit a completion message after a failure. Do not emit migration messages
  for skipped migrations, fresh installations or version-only updates.
