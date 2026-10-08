# Modifyworld 2.0.0-BETA.6

Beta release of the unofficial Modifyworld port for Paper.

### Requirements

- Paper 26.2; compiled against `26.2.build.130-stable`.
- Java 25.
- LuckPerms recommended; permissions are checked through Bukkit.
- Newer Paper versions have not been verified. Spigot and Folia are not supported targets.

### Changes

- Translate `/modifyworld check` into all seven supported languages, including
  labels, results, reasons, errors and usage hints. Report order and colors stay fixed.
- Add missing diagnostic texts to existing language files and `own.yml`, preserving
  custom messages and saving the original as `.bak` (then numbered backups).
- Use bundled translations with English fallback; custom `own.yml` defaults to English.
- Validate diagnostic entries as single-line text and preserve conflicting legacy files.
- Improve migration checks with numeric release and beta version comparison against
  fixed migration thresholds. Completed steps do not repeat on restarts or later
  releases; future steps run sequentially on the preceding migration's result.
- Record the running release after successful startup; version-only updates create
  no configuration backup, while content-changing migrations retain backups.
- Log migration context at INFO level and report overall completion only after
  successful validation and saving.
- Clarify custom messages and migration from legacy Modifyworld in the README.

### Installation / update

Stop the server, back up your configuration and permissions, replace the existing
Modifyworld JAR in `plugins/`, and restart. Configuration changes require a restart.

The configured `language` now also applies to permission diagnostics. Edit
`lang/<language>.yml`, or use `lang/own.yml` with `language: own`, for custom text.
Existing texts remain intact; only missing diagnostic entries are appended.
Existing migrations run when `config-version` is missing or older than the fixed
threshold `2.0.0-BETA.6`, using numeric major/minor/patch/beta comparison.
After success, `config-version: 2.0.0-BETA.6` is saved in `config.yml`.
Equal or newer configuration versions skip completed migrations and preserve existing
language files, including `own.yml`; missing texts use bundled fallbacks.
The recorded release advances without a backup when only the version changes.
Modifyworld does not translate system-provided material or entity names.

Legacy custom messages in `config.yml` migrate to `lang/own.yml` with a
`config.yml.bak` backup. A conflicting existing own file must be merged manually.
Numeric permission conversion is relevant when migrating from legacy Modifyworld,
not for routine beta updates of this port.

Permission behavior is unchanged from BETA.5: with `op-bypass: false`, OPs need
assigned or inherited permissions. WorldGuard protection remains unaffected.

### Status

Beta verification: 264 automated tests passed (0 failures, 0 errors, 0 skipped)
with `./build.sh -B -o clean verify`, Java 25 and Maven 3.9.11. Automated tests do not replace live-server validation.
The new diagnostic translations have not yet received native-speaker review.
Use GitHub's Pre-release setting and the planned tag `v2.0.0-BETA.6` when publishing.

### Downloads

- `Modifyworld.jar`: plugin for installation.
- `Modifyworld-2.0.0-BETA.6.zip`: plugin, documentation, license and corresponding source.

### License

GPL-2.0-or-later. See LICENSE and NOTICE in the distribution.
