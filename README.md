# Modifyworld-Reloaded

An unofficial community port of Modifyworld for Paper.

Control what players can build, use, pick up, craft, and transfer through
inventories, with permissions that can vary by group and world.

This fork ports [PEXPlugins/Modifyworld](https://github.com/PEXPlugins/Modifyworld)
to **Paper 26.2, Java 25, and LuckPerms**. WorldGuard remains responsible for
regions. Modifyworld adds permission checks and does not clear another plugin's
event cancellation.

> **Beta — 2.0.0-BETA.5**

## Requirements and compatibility

| Component | Target / status |
| --- | --- |
| Minecraft edition | Java Edition |
| Server | Developed and tested against Paper for Minecraft 26.2 |
| Newer Paper versions | May work but have not yet been verified |
| Java | Java 25 |
| Compile-time Paper API | `26.2.build.130-stable`, pinned in [pom.xml](pom.xml) |
| Permissions | LuckPerms is the intended provider; Modifyworld uses Bukkit permission checks |
| Regions | Optional WorldGuard installation with its required dependencies |
| PermissionsEx / Vault | Neither is required or used |
| Other platforms | Spigot, Folia are not supported targets of this port |

The API version is a build target, not proof of live compatibility. Exact tested
Paper/LuckPerms/WorldGuard versions will be recorded after integration testing.
See [Paper's project setup documentation](https://docs.papermc.io/paper/dev/project-setup/)
for the Paper dependency and Java version conventions.

## Installation

1. Prepare an isolated Paper 26.2 server running Java 25 and back up any worlds,
   permissions, and configuration you intend to reuse.
2. Install the Bukkit/Paper edition of LuckPerms. If you need regions, install
   WorldGuard and its dependencies separately.
3. Build this branch using the instructions below. Stop the server and place
   `target/Modifyworld.jar` in its `plugins/` directory. Remove any older
   Modifyworld JAR from that directory to avoid loading two copies.
4. Start the test server. Confirm that the console reports `Modifyworld enabled!`.
   The first successful configuration load creates `plugins/Modifyworld/config.yml`.
5. Stop the server, review the configuration, and set up LuckPerms permissions.
   Existing numeric permissions must be migrated before relying on this port.
6. Restart and test with a **non-OP** account in every relevant world. Use
   LuckPerms verbose output to confirm which permissions are being checked.

Modifyworld provides a permission diagnostic command, but no live configuration reload.
Restart after changing its configuration. LuckPerms permission changes are
evaluated on subsequent checks without a Modifyworld-specific permission cache.

An invalid configuration or listener startup failure disables Modifyworld and
logs that protection is **not active**. This does not shut down the Minecraft
server. Existing configurations are migrated with a backup when options are missing
or legacy messages are present; invalid configurations are not rewritten.

## How permissions work

For normal actions, Modifyworld asks whether the player has the generated
permission. Set up grants as well as restrictions: this is not an item blacklist
where everything unlisted is automatically allowed. LuckPerms resolves group
inheritance, explicit denials, wildcards, and contexts. Operator/default behavior
can affect results, so always verify with a non-OP account.

Use `world=<world-name>` to scope LuckPerms rules to a world. Use the actual world
name/context reported by your server, not a display alias. See
[LuckPerms contexts](https://luckperms.net/wiki/Context) and
[permission precedence](https://luckperms.net/wiki/Advanced-Setup).

With `op-bypass: false`, operators need an effective permission assignment for
normal actions. Modifyworld checks Bukkit `isPermissionSet` before accepting an
OP's `hasPermission` result. LuckPerms resolves assignments from users, groups,
wildcards and contexts, but excludes its implicit OP fallback from `isPermissionSet`.
With `op-bypass: true`, OPs bypass these checks, including explicit denials.
Neither setting clears WorldGuard cancellations or Minecraft admission checks.
The unknown-inventory-action exception below still defaults to allowed.
Other permission providers must implement Bukkit's assignment checks correctly.

### Naming conventions

Material and inventory names are lowercase with underscores removed. Permission
names do **not** use numeric IDs, metadata suffixes, or `minecraft:` prefixes.

| Bukkit material/type | Permission segment |
| --- | --- |
| `TNT` | `tnt` |
| `LAVA_BUCKET` | `lavabucket` |
| `FLINT_AND_STEEL` | `flintandsteel` |
| `FIRE_CHARGE` | `firecharge` |
| `COMMAND_BLOCK` | `commandblock` |
| `AIR` (empty hand) | `air` |
| `SHULKER_BOX` inventory | `shulkerbox` |

In the tables below, `<item>` and `<block>` follow this convention. `<inventory>`
is the inventory type, such as `chest`, `barrel`, `furnace`, or `workbench`.

### Building, items, and inventories

| Permission | Meaning |
| --- | --- |
| `modifyworld.blocks.place.<block>` | Place blocks, including all blocks in a multi-block placement |
| `modifyworld.blocks.destroy.<block>` | Break blocks |
| `modifyworld.blocks.place.fire` | Player-caused fire ignition, including soul fire |
| `modifyworld.blocks.interact.<block>` | Block interaction when `item-use-check` is false; also physical interactions |
| `modifyworld.items.use.<item>.on.block.<block>` | Use an item on a block when `item-use-check` is true |
| `modifyworld.items.use.<item>.on.entity.<entity>` | Use an item on an entity when `item-use-check` is true |
| `modifyworld.bucket.empty.<content>` | Empty a bucket; e.g. `lava` or `water`, including supported cauldrons |
| `modifyworld.bucket.fill.<content>` | Fill a bucket; e.g. `lava`, `water`, `milk`, or `powdersnow` |
| `modifyworld.items.pickup.<item>` | Pick up a dropped item |
| `modifyworld.items.drop.<item>` | Drop an item |
| `modifyworld.items.hold.<item>` | Hold an item through the monitored held-slot/inventory-click paths |
| `modifyworld.items.have.<item>` | Possess an item when `item-restrictions` is enabled |
| `modifyworld.items.craft.<item>` | Extract the actual crafting output, including shift crafting, hotbar/offhand swaps, and drop extraction |
| `modifyworld.items.enchant.<item>` | Enchant an item |
| `modifyworld.items.throw.<item>` | Throw eggs, snowballs, experience bottles, splash potions, or lingering potions |
| `modifyworld.items.put.<item>.of.<inventory>` | Insert an item into the top inventory |
| `modifyworld.items.take.<item>.of.<inventory>` | Take an item from the top inventory, including slot drops and creative cloning |
| `modifyworld.items.allowunknownaction.<item>` | Policy for unknown inventory actions; special default described below |

Item-use checks replace ordinary block/entity interaction checks when enabled.
For example, an empty-handed chest interaction checks
`modifyworld.items.use.air.on.block.chest`. There is no separate general
`inventory.open` permission or listener; inventories opened by another plugin
may not involve a block interaction. Transfer checks still apply.

Container checks cover normal clicks, shift-clicks, cursor swaps, hotbar and
offhand swaps, dragging, and double-click collection. A swap needs both take and
put permission. Personal player/creative/crafting inventory views are excluded
from container-transfer checks; crafting permissions still apply. Workbench
output may require both crafting and container take permission.

Bundle extraction conservatively checks all contained materials. It can deny an
extraction even when the selected entry is allowed. Moving a bundle or shulker-box
item as a whole is checked as that item; this is not a recursive content blacklist.

### Unknown inventory actions

For **unknown actions only**, `modifyworld.items.allowunknownaction.<item>` defaults
to allowed when no permission assignment applies. Set it to `false` to deny an
unknown action involving that material, or `true` to allow it.

The check examines the clicked item, cursor item, and an indicated hotbar/offhand
item. A future action could affect additional items not exposed by these fields.
This policy is therefore not a guarantee against every future transfer mechanism.
It never bypasses the normal rules for known actions.

### Other permissions

| Permission | Meaning |
| --- | --- |
| `modifyworld.command.*` | LuckPerms wildcard covering all Modifyworld command permissions |
| `modifyworld.command.check` | Use `/modifyworld check` for yourself or other online players; granted to OPs by default, independently of `op-bypass` |
| `modifyworld.login` | Additional admission check, only with `require-login-permission: true` |
| `modifyworld.chat` | Send chat messages |
| `modifyworld.chat.private` | Legacy command filter for messages starting with `/tell`; not a complete private-message filter |
| `modifyworld.sneak` | Start sneaking |
| `modifyworld.sprint` | Start sprinting |
| `modifyworld.usebeds` | Enter a bed |
| `modifyworld.digestion` | Allow food-level changes |
| `modifyworld.interact.<entity>` | Entity interaction when `item-use-check` is false |
| `modifyworld.spawn.<type>` | Use a spawn egg for the entity type |
| `modifyworld.tame.<entity>` | Tame an entity |
| `modifyworld.mobtarget.<entity>` | Allow an entity to target the player |
| `modifyworld.damage.deal.<entity>` | Deal direct player-caused damage to an entity |
| `modifyworld.damage.take.<entity>` | Receive damage from an entity |
| `modifyworld.damage.take.<cause>` | Receive environmental damage; e.g. `fall` or `lava` |
| `modifyworld.vehicle.enter.<entity>` | Enter a vehicle |
| `modifyworld.vehicle.destroy.<entity>` | Damage a vehicle directly as a player |
| `modifyworld.vehicle.collide.<entity>` | Allow a vehicle collision with the player |

Entity segments include `player.<name>`, `animal.<type>[.<owner-name>]`,
`monster.<type>`, `npc.<type>`, `projectile.<type>`, or the unprefixed type when
no category matches. Examples: `monster.creeper` and `animal.wolf`.
Spawn-egg permissions use the unprefixed type. Hanging placement/break checks
use the entity type under `modifyworld.blocks.place` / `.destroy`.
Renamed modern entity types can change old permission suffixes; inspect verbose
output rather than assuming an old boat or minecart name still matches.

## LuckPerms example

For a compact Admin / VIP / Player / Default setup across two worlds, see
[the four-rank example](examples/luckperms-worlds.md).

The following console commands create a **test group** with broad Modifyworld
access in the world `build`, then restrict lava, TNT, and ignition tools there.
They are an example, not a complete policy for your other worlds or groups.
The wildcard also grants the unrelated movement, damage, and chat permissions.

```text
lp creategroup mwtest
lp group mwtest permission set modifyworld.* true world=build
lp group mwtest permission set modifyworld.blocks.place.tnt false world=build
lp group mwtest permission set modifyworld.blocks.place.fire false world=build
lp group mwtest permission set modifyworld.bucket.empty.lava false world=build
lp group mwtest permission set modifyworld.bucket.fill.lava false world=build
lp group mwtest permission set modifyworld.items.pickup.tnt false world=build
lp group mwtest permission set modifyworld.items.take.tnt.of.* false world=build
lp group mwtest permission set modifyworld.items.craft.tnt false world=build
lp group mwtest permission set modifyworld.items.pickup.flintandsteel false world=build
lp group mwtest permission set modifyworld.items.take.flintandsteel.of.* false world=build
lp group mwtest permission set modifyworld.items.craft.flintandsteel false world=build
lp group mwtest permission set modifyworld.items.use.flintandsteel.on.block.* false world=build
lp group mwtest permission set modifyworld.items.use.flintandsteel.on.entity.* false world=build
lp group mwtest permission set modifyworld.items.use.firecharge.on.block.* false world=build
lp group mwtest permission set modifyworld.items.use.firecharge.on.entity.* false world=build
lp group mwtest permission set modifyworld.items.allowunknownaction.tnt false world=build
lp group mwtest permission set modifyworld.items.allowunknownaction.flintandsteel false world=build
lp group mwtest permission set modifyworld.items.allowunknownaction.lavabucket false world=build
lp user TestPlayer parent add mwtest
```

Replace `build` and `TestPlayer` with your actual test world and account.
Verify the effective result if the player also inherits other groups or explicit
user permissions. A placement denial does not itself deny ignition of existing
TNT; item-use permissions address direct ignition tools.

## Permission diagnostics

### Syntax

In-game, check your own permissions:

```text
/modifyworld check modifyworld.<permission>
```

From the server console, specify an online player:

```text
modifyworld check <PlayerName> modifyworld.<permission>
```

The named-player form also works in-game with a leading `/`. Replace
`<PlayerName>` with the player's name and `<permission>` with an action suffix
such as `blocks.place.tnt`, without the angle brackets. Use concrete action
permissions, without wildcards. The target must be online; the check uses their
current world and permission context.

### Command access

Both forms require `modifyworld.command.check` (granted to OPs by default);
the server console can always use them. This command permission is independent
of `op-bypass` and permits checks for yourself and other online players.
A matching `modifyworld.command.*` or `modifyworld.*` grant also includes it.

To grant command access globally to the Player group, run in the server console:

```text
lp group player permission set modifyworld.command.check true
```

See the [world permission example](examples/luckperms-worlds.md#player).

### Examples

In-game, check whether Modifyworld permissions allow you to place TNT:

```text
/modifyworld check modifyworld.blocks.place.tnt
```

From the console, check the same permission for an online player named Alex:

```text
modifyworld check Alex modifyworld.blocks.place.tnt
```

### Understanding the result

For example, a non-OP player with a permission grant sees:

```text
[Modifyworld] Permission check
Player: Alex
Player is OP: false
World: survival
Permission: modifyworld.blocks.place.tnt
Modifyworld OP bypass: false
Bukkit permission result: true
Permission explicitly assigned or inherited: yes
Reason: The effective Bukkit permission check allows this action.
Action: ALLOWED
Note: This checks Modifyworld permissions only. Other rules or plugins may still block the action.
```

| Field | Meaning |
| --- | --- |
| `Player` / `Player is OP` | The checked player and their operator status. |
| `World` | The checked player's current world. |
| `Permission` | The exact action permission being checked. |
| `Modifyworld OP bypass` | The plugin's `op-bypass` setting. It bypasses Modifyworld permission checks only when enabled and the checked player is OP. |
| `Bukkit permission result` | The raw Bukkit permission result, which may include an implicit OP grant. |
| `Permission explicitly assigned or inherited` | Whether Bukkit reports an effective assignment, for example directly, through a group or through a wildcard. `yes` may represent either a grant or a denial; it does not mean allowed. |
| `Reason` | Why Modifyworld allows or denies this permission check. |
| `Action` | Modifyworld's permission decision: `ALLOWED` or `DENIED`. |
| `Note` | A reminder that permission checks alone cannot determine whether the entire action will succeed. Configuration switches, event rules and other plugins still apply. |

Labels are gray and values aqua. The action result is bold green for `ALLOWED`
or bold red for `DENIED`; the explanatory note follows it in yellow.
The command uses the same policy as the listeners, including the default-allow
unknown-inventory-action exception. LuckPerms' own check command still shows
its native result, which may include an OP fallback rejected by Modifyworld.

## Configuration

The full default file is [config.yml](src/main/resources/config.yml).

| Setting | Default | Effect |
| --- | --- | --- |
| `item-use-check` | `true` | Check the item used on a block/entity, including the actual hand; otherwise use ordinary interaction permissions |
| `inform-players` | `true` | Send configured denial messages; deliberately silent checks such as pickup remain silent |
| `item-restrictions` | `false` | Scan inventory on monitored interactions, pickup/drop, and held-slot changes; remove items denied by `items.have` |
| `drop-restricted-item` | `false` | During those inventory scans, drop removed items in the world instead of deleting them |
| `op-bypass` | `false` | When true, operators bypass Modifyworld checks including explicit denials; when false, OP fallback alone grants no access |
| `language` | `en` | Server message language: `en`, `de`, `es`, `fr`, `pt_br`, `pl`, `tr`, or `own` |
| `require-login-permission` | `false` | Require `modifyworld.login` in addition to Minecraft's admission checks |

`item-restrictions` is an event-driven scan, not continuous monitoring. A denial
of `items.hold` can move the item out of the hotbar or drop it when storage is full.

The old `whitelist` setting remains a deprecated alias for
`require-login-permission`. An explicitly configured new key takes precedence;
otherwise the old value is used. Neither setting changes Minecraft's whitelist.
If Minecraft's whitelist is your only admission policy, leave
`require-login-permission: false`. The optional extra check currently uses an
isolated legacy login-event adapter so that Bukkit permissions can be checked
before entry. Its live LuckPerms behavior still needs verification.

Boolean settings must use YAML booleans (`true`/`false`, without quotes).
Missing options are added to existing files at startup, preserving configured values.
Before rewriting, the original is copied to `config.yml.bak` (then `.bak.1`, etc.).
YAML formatting may change; the backup preserves the exact original file.
The legacy `whitelist` key is removed after resolving `require-login-permission`.
The obsolete `use-material-names` and `check-metadata` options are removed during
migration, regardless of their values. Modern material names are always used;
legacy numeric IDs and data values are unsupported. This does not convert
permissions stored in LuckPerms; those must be migrated separately.

### Messages

Missing bundled language files are copied into the plugin folder at startup:
`lang/en.yml`, `lang/de.yml`, `lang/es.yml`, `lang/fr.yml`, `lang/pt_br.yml`,
`lang/pl.yml`, and `lang/tr.yml`.
Select `language: en` (English), `de` (German), `es` (Spanish), `fr` (French),
`pt_br` (Brazilian Portuguese), `pl` (Polish), or `tr` (Turkish).
The selected language applies server-wide, not per player.
Restart after changes. Existing language files are never overwritten.
Missing keys fall back to the bundled selected language, then bundled English,
without rewriting your files. Invalid selected language files stop startup.
Material and entity names in placeholders remain English.

Non-empty legacy `messages` sections are automatically migrated to `lang/own.yml`,
and `language` is set to `own`. Missing messages are filled from the previously
selected language (English when unspecified). The old section becomes `messages: {}`.
Edit `lang/own.yml` directly after migration. Missing keys in an own file fall back
to bundled English. Selecting `own` requires that the file exists.
A conflicting existing `own.yml` stops startup without changing the configuration
or that language file. Move or merge the conflicting file manually before retrying.
An identical existing file can be reused after an interrupted migration.
Repeated startup does not rewrite an already migrated configuration.

The following is an example of a legacy section that will be migrated:

```yaml
messages:
  message-format: '&f[&2Modifyworld&f]&4 %s'
  default-message: 'You do not have permission for this action.'
  modifyworld.items.craft: 'You may not craft $1.'
  modifyworld.bucket.empty: 'You may not empty this bucket.'
```

Keys under `messages` are literal permission names. A message for a parent node
also applies to its more specific children, with `default-message` as fallback.
$1 describes the item for container transfers and the contents for bucket actions.
For item interactions, $3 describes the target; for container transfers it describes
the inventory type. Permission names are unaffected by message formatting.
Existing language files are preserved on upgrades: to adopt the revised Beta 2
texts, back up and remove the old language files while the server is stopped.
The next startup recreates them. With `language: own`, those files do not replace
your custom messages; edit `own.yml` or explicitly select one of the bundled languages.

Use `%s` in `message-format`, `$permission` for the checked permission, and
`$1`, `$2`, etc. for action arguments where available. `&` color codes are supported
by normal denial messages. The optional login rejection uses its own message
without the normal message-format wrapper.

PEX/Vault per-player or per-world denial-message metadata is no longer read.
Move needed text into the selected `lang/<language>.yml` file or `lang/own.yml`.
The historical `individual-messages` option has no active effect in this port.

## Migrating old permissions

- Convert numeric IDs and metadata-specific entries to modern names. For example,
  old TNT `.46` entries become `.tnt`; flint-and-steel `.259` becomes
  `.flintandsteel`, and command-block `.137` becomes `.commandblock`.
- Bucket content uses names: `.bucket.empty.lava` and `.bucket.fill.lava`, not `.10`.
- Include complete suffixes: `items.take.tnt.of.*`, not just `items.take.tnt`;
  `items.use.flintandsteel.on.block.*`, not just `items.use.flintandsteel`.
- Convert PEX world sections to LuckPerms world contexts. Preserve intentional
  group differences, per-world inheritance, and explicit denials. Do not infer
  inheritance from group weights or names.
- Do not assume PEX regular expressions or precedence transfer unchanged.
  Verify effective permissions with LuckPerms verbose and non-OP accounts.

Numeric-ID compatibility is not implemented. The original permission families
are retained where possible, but historical ineffective entries are not a reliable
specification of what the server actually enforced.

## Scope and remaining limitations

- Regions, natural fire spread, explosions, redstone/TNT chain reactions, automated
  crafters, dispensers, and hopper transfers do not receive an invented player
  permission context. Use appropriate WorldGuard/server rules for those paths.
- Direct player damage checks are not a complete projectile/explosion ownership
  system. TNT placement, ignition, and explosion protection are distinct concerns.
- These checks do not provide a blanket ban on an item existing, being granted by
  commands, or being generated by another plugin. Creative and plugin-driven paths
  need explicit integration testing before relying on an item restriction.
- Custom inventories, custom recipes, entity/spawn-egg variants, and other plugins
  can affect event behavior. Test the actual server combination.
- Startup failure leaves Modifyworld disabled, not a server-wide lockdown.

## Operational safety and backups

Modifyworld adds permission checks for supported player actions. It does not
guarantee protection against every form of world modification, plugin conflict,
configuration mistake or software defect.

Before using a new version on a production server, test your permissions,
world contexts and plugin combination on a separate test server. Keep regular
backups of worlds, configurations and permissions, and verify that they can
be restored.

A stable release does not mean that the software is free of defects.
Automated tests cover selected behavior and do not replace testing your
specific server setup.

The software is provided under GPL-2.0-or-later. Warranty disclaimers and
limitations of liability are set out in LICENSE, subject to applicable law.

Release details and validation results are in [RELEASE-NOTES.md](RELEASE-NOTES.md).

## Build and validation

Use JDK 25 and Maven (development testing used Maven 3.9.11):

```sh
java -version
mvn -version
mvn clean verify
```

For persistent user-local tools, `./build.sh` runs the same clean build and prints
Java/Maven versions first. Its defaults are JDK 25.0.4.1+1 (macOS bundle) and
Maven 3.9.11 under `~/.local/share/minecraft-devtools/`. Set
`MODIFYWORLD_JAVA_HOME` and `MODIFYWORLD_MAVEN_HOME` to use other installation
paths, including non-macOS JDK layouts. `MODIFYWORLD_TOOLS_DIR` overrides the
common tools directory, shared with other Minecraft plugin projects. Setup and
troubleshooting details are included in `build.sh`. The script does not download tools or change shell settings;
missing tools produce an error. Maven normally caches dependencies in
`~/.m2/repository/`, outside temporary storage.

With dependencies already cached, run `./build.sh -B -o clean verify` offline.
Explicit arguments replace the default `-B clean verify` arguments. The script
sets Java only for its own process and Maven children. A sandbox still needs write
permission to the Maven cache when dependencies must be downloaded.

Expected artifacts are `target/Modifyworld.jar` and `target/Modifyworld-2.0.0-BETA.5.zip`.
The first build downloads the Paper API and build/test dependencies. The API is
provided by the server and is not bundled into the plugin.

Build validation uses automated tests with mocked server services and selected
real event classes. Run `mvn verify` to execute them and package the beta.
Surefire loads Mockito as a Java agent when the test JVM starts; dynamic agent
loading is disabled. The agent uses the configured local Maven repository and
the same Mockito version as the test dependency. No runtime attachment is needed.
Run tests through Maven to apply this configuration; IDE-native test runners need
the equivalent JVM agent option. Mockito remains test-only and is not in the plugin JAR.

## Links

Available on [Hangar](https://hangar.papermc.io/Yippie/Modifyworld-Reloaded).

## Credits and license

Original Modifyworld by t3hk0d3 and the contributors to
[PEXPlugins/Modifyworld](https://github.com/PEXPlugins/Modifyworld).
Paper port maintained in [this fork](https://github.com/yippieyaray/Modifyworld).

This unofficial community port is distributed under **GNU GPL version 2 or later**
(`GPL-2.0-or-later`). See [LICENSE](LICENSE) and [NOTICE](NOTICE) for the full
license text, attribution, modification summary and source provenance.
Original copyright notices are retained. The software comes without warranty.

`Modifyworld.jar` includes LICENSE and NOTICE under `META-INF/`.
`Modifyworld-2.0.0-BETA.5.zip` includes the JAR, documentation, license and the complete
corresponding project source under `source/`, including tests and Maven build files.
Publish this ZIP alongside the standalone JAR and use a release tag matching the
source used for the build. Private server configurations are not included.
