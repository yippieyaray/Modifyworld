# Modifyworld 2.0.0-BETA.4

Beta release of the unofficial Modifyworld port for Paper.

### Requirements

- Paper 26.2; compiled against `26.2.build.129-stable`.
- Java 25.
- LuckPerms recommended; permissions are checked through Bukkit.
- Newer Paper versions may work but have not been verified.
- Spigot and Folia are not supported targets.

### Changes

- Replace outdated PermissionsEx plugin descriptions and remove obsolete comments.
- Correct documentation of language-file messages and update the plugin website.
- Name the distribution ZIP `Modifyworld-2.0.0-BETA.4.zip`.
- Include these release notes at the ZIP root and in the corresponding source.
- No permission behavior changes in this release.

### Features

- Permission checks for building, item use, crafting and container transfers.
- Optional OP bypass, disabled by default.
- English, German, Spanish and French messages.
- Automatic configuration migration with backups.
- Legacy custom messages migrated to `lang/own.yml`.
- Obsolete material-name and metadata settings removed during migration.

### Installation / update

Stop the server, back up your configuration, replace the existing Modifyworld
JAR in `plugins/`, and restart. Configuration changes require a restart.

Existing language files are preserved. Edit the selected `lang/<language>.yml`
file or `lang/own.yml` to update your messages. Configuration migration preserves
existing values and creates `config.yml.bak` (then numbered backups); a conflicting
`lang/own.yml` stops startup without overwriting that file or the configuration.

Legacy numeric permissions must be converted separately.
WorldGuard remains responsible for region protection; Modifyworld does not lift
its denials. Automation and natural fire/lava spread are not generally attributed
to players. A startup failure disables this plugin, not the server.

### Status

Beta — 141 automated tests pass (0 failures, 0 errors, 0 skipped) with
`mvn -B clean verify`, Java 25 and Maven 3.9.11.
Manual server testing is ongoing. This release has not been installed on a server
as part of its validation. Publish this beta as a GitHub pre-release.

### Downloads

- `Modifyworld.jar`: plugin for installation.
- `Modifyworld-2.0.0-BETA.4.zip`: plugin, license, documentation and corresponding source code.
- Release tag: `v2.0.0-BETA.4`.

### License

GPL-2.0-or-later. See LICENSE and NOTICE in the distribution.
