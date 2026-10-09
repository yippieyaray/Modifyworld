# Modifyworld 2.0.0-BETA.7

Beta release of the unofficial Modifyworld port for Paper.

### Requirements

- Paper 26.2; compiled against `26.2.build.130-stable`.
- Java 25.
- LuckPerms recommended; permissions are checked through Bukkit.
- Newer Paper versions have not been verified. Spigot and Folia are not supported targets.

### Changes

- Add `/mw` as a shortcut for `/modifyworld`, with the same arguments and permissions.
- Add `command-alias-mw: true` to `config.yml`; set it to `false` and restart to disable
  the shortcut. `/modifyworld` remains available, and another plugin's `/mw` command
  is preserved when disabling this alias.
- Resolve null-safety, deprecated API and unused-import warnings in production code
  and tests. Diagnostic texts retain English fallback.
- Add an optional update check at startup, enabled by default with
  `check-for-updates: true`. Update notifications link to Hangar; no automatic
  downloads or installation.

### Installation / update

Stop the server, back up your configuration and permissions, replace the existing
Modifyworld JAR in `plugins/`, and restart. Configuration changes require a restart.

If `command-alias-mw` is missing from an existing configuration, the shortcut is
also enabled. Add `command-alias-mw: false` manually to disable it. Another plugin
may already own `/mw`; the full `/modifyworld` command remains available.

Existing BETA.6 migrations keep their fixed threshold and do not repeat on an
upgrade from BETA.6. After successful loading, `config-version` advances to
`2.0.0-BETA.7`; version-only updates preserve other configuration text and create
no backup. Permission behavior is unchanged.

### Status

Beta verification: 322 automated tests passed (0 failures, 0 errors, 0 skipped)
with `./build.sh -B -o clean verify`, Java 25 and Maven 3.9.11.
Automated tests do not replace live-server validation of this beta.
Use GitHub's Pre-release setting and tag `v2.0.0-BETA.7+paper.min.26.2.build.130` when publishing.

### Downloads

- `Modifyworld.jar`: plugin for installation.
- `Modifyworld-2.0.0-BETA.7.zip`: plugin, documentation, license and corresponding source.

### License

GPL-2.0-or-later. See LICENSE and NOTICE in the distribution.
