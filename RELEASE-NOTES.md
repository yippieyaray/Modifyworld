# Modifyworld 2.0.0-BETA.5

Beta release of the unofficial Modifyworld port for Paper.

### Requirements

- Paper 26.2; compiled against `26.2.build.130-stable`.
- Java 25.
- LuckPerms recommended; permissions are checked through Bukkit.
- Newer Paper versions may work but have not been verified.
- Spigot and Folia are not supported targets.

### Changes

- Update the pinned Paper API to `26.2.build.130-stable`.

- Add `/modifyworld check [player] <permission>` with shared action-policy diagnostics,
  clear player/plugin labels, distinct colors and a green/red `Action: ALLOWED` / `Action: DENIED`
  result followed by an explanatory note. Document syntax, examples and diagnostic fields.
- Use `modifyworld.command.check` for diagnostic command access.
- With `op-bypass: false`, reject implicit OP permission grants for normal actions.
- Preserve assigned permissions, including LuckPerms group, wildcard and world-context resolution.
- Keep the explicit OP bypass and the default-allow unknown-inventory-action policy.
- Add regression coverage for OP fallback, permission changes and cancelled placement events.
- Include a compact four-rank, two-world LuckPerms example and operational safety guidance.

### Features

- Permission checks for building, item use, crafting and container transfers.
- Optional OP bypass, disabled by default.
- English, German, Spanish, French, Brazilian Portuguese, Polish and Turkish messages.
- Automatic configuration migration with backups.
- Legacy custom messages migrated to `lang/own.yml`.
- Obsolete material-name and metadata settings removed during migration.

### Installation / update

OPs that relied on implicit OP grants must receive explicit Modifyworld permissions
or use `op-bypass: true` to deliberately bypass all Modifyworld checks.
A targeted OP/non-OP test with LuckPerms on a live server is still required.

Stop the server, back up your configuration, replace the existing Modifyworld
JAR in `plugins/`, and restart. Configuration changes require a restart.

Set `language: pt_br`, `language: pl`, or `language: tr` in `config.yml` to use a
new language server-wide, then restart. Material and entity names remain English.

Existing language files are preserved. Edit the selected `lang/<language>.yml`
file or `lang/own.yml` to update your messages. Configuration migration preserves
existing values and creates `config.yml.bak` (then numbered backups); a conflicting
`lang/own.yml` stops startup without overwriting that file or the configuration.

Legacy numeric permissions must be converted separately.
WorldGuard remains responsible for region protection; Modifyworld does not lift
its denials. Automation and natural fire/lava spread are not generally attributed
to players. A startup failure disables this plugin, not the server.

### Status

Beta — 179 automated tests pass (0 failures, 0 errors, 0 skipped) with
`mvn -B clean verify`, Java 25 and Maven 3.9.11. Tests run with Mockito loaded at JVM startup and dynamic agent loading disabled.
The Brazilian Portuguese, Polish and Turkish translations have not yet received native-speaker review.
Manual server testing is ongoing; automated tests do not replace live-server validation.
Publish this beta as a GitHub pre-release with tag `v2.0.0-BETA.5`.

### Downloads

- `Modifyworld.jar`: plugin for installation.
- `Modifyworld-2.0.0-BETA.5.zip`: plugin, license, documentation and corresponding source code.

### License

GPL-2.0-or-later. See LICENSE and NOTICE in the distribution.
