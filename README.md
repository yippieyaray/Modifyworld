# Modifyworld-Reloaded

An unofficial community port of Modifyworld for Paper.

Control what players can build, use, pick up, craft, and transfer through
inventories, with permissions that can vary by group and world.

This fork ports [PEXPlugins/Modifyworld](https://github.com/PEXPlugins/Modifyworld)
to **Paper 26.2, Java 25, and LuckPerms**. WorldGuard remains responsible for
regions. Modifyworld adds permission checks and does not clear another plugin's
event cancellation.

> **Beta — 2.0.0-BETA.7**

### Changes in this beta

- Add `/mw` as a shortcut for `/modifyworld`.
- Add `command-alias-mw: true` to `config.yml`; set it to `false` and restart to disable the shortcut.
- Resolve null-safety, deprecated API and unused-import warnings in production code and tests.
- Add an optional GitHub update check at startup, enabled by default.

## Requirements and compatibility

| Component | Target / status |
| --- | --- |
| Minecraft edition | Java Edition |
| Server | Developed and tested against Paper for Minecraft 26.2 |
| Newer Paper versions | May work but have not yet been verified |
| Java | Java 25 |
| Permissions | LuckPerms is the intended provider; Modifyworld uses Bukkit permission checks |
| Regions | Optional WorldGuard installation with its required dependencies |
| PermissionsEx / Vault | Neither is required or used |
| Other platforms | Spigot, Folia are not supported targets of this port |

Confirmed server tests do not establish compatibility with newer Paper versions.

## Installation

1. Prepare a Paper 26.2 server running Java 25. Back up existing worlds,
   permissions and configuration before upgrading.
2. Install LuckPerms. If you use regions, also install WorldGuard and its dependencies.
3. Download the JAR from [Hangar](https://hangar.papermc.io/Yippie/Modifyworld-Reloaded).
   Stop the server and place it in `plugins/`, replacing any older Modifyworld JAR.
   For building from source, see [Build and validation](#build-and-validation).
4. Start the server and confirm `Modifyworld enabled!` in the console.
   Configuration is created in `plugins/Modifyworld/config.yml`.
5. Review the configuration, configure LuckPerms and restart. Convert legacy
   numeric permissions if you are moving from the original Modifyworld.
6. Test with a **non-OP** account in each relevant world. Use LuckPerms verbose
   output or [permission diagnostics](#permission-diagnostics) to check permissions.

Restart after configuration changes; there is no live reload. LuckPerms permission
changes apply on subsequent checks without restarting Modifyworld.

Invalid configuration or startup failures disable Modifyworld, but leave the server
running. Check the log: protection is **not active** while the plugin is disabled.

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

- `op-bypass: false`: OPs need assigned permissions, directly or through groups,
  wildcards or contexts. An implicit OP grant alone is insufficient.
- `op-bypass: true`: OPs bypass Modifyworld checks, including explicit denials.

Neither setting bypasses WorldGuard restrictions or Minecraft admission checks.
Unknown inventory actions use the special default described below.
Other permission providers must support Bukkit assignment checks correctly.

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

Use concrete action permissions without wildcards. The target must be online;
checks use their current world and permission context.

| Where | Example |
| --- | --- |
| In-game, yourself | `/modifyworld check modifyworld.blocks.place.tnt` |
| In-game, another player | `/modifyworld check Alex modifyworld.blocks.place.tnt` |
| Server console | `modifyworld check Alex modifyworld.blocks.place.tnt` |

`/mw` is the default shortcut for `/modifyworld`.

Both player forms require `modifyworld.command.check`, granted to OPs by default
and independent of `op-bypass`. The console always has access.
To grant access to the Player group, run:

```text
lp group player permission set modifyworld.command.check true
```

See the [world permission example](examples/luckperms-worlds.md#player).

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

The fields most useful for interpreting the result are:

| Field | Meaning |
| --- | --- |
| `Modifyworld OP bypass` | Whether the bypass is enabled; it applies only to OPs. |
| `Bukkit permission result` | The raw permission result, which may include an implicit OP grant. |
| `Permission explicitly assigned or inherited` | Whether an assignment exists. `yes` can mean a grant or a denial. |
| `Action` | Modifyworld's decision: bold green `ALLOWED` or bold red `DENIED`. |

The command uses the same permission policy as the listeners. LuckPerms' own
check may differ because of implicit OP grants. Other plugins and event rules
can still block an action reported as allowed.

## Configuration

The full default file is [config.yml](src/main/resources/config.yml).

| Setting | Default | Effect |
| --- | --- | --- |
| `item-use-check` | `true` | Check the item used on a block/entity, including the actual hand; otherwise use ordinary interaction permissions |
| `inform-players` | `true` | Send configured denial messages; deliberately silent checks such as pickup remain silent |
| `item-restrictions` | `false` | Scan inventory on monitored interactions, pickup/drop, and held-slot changes; remove items denied by `items.have` |
| `drop-restricted-item` | `false` | During those inventory scans, drop removed items in the world instead of deleting them |
| `check-for-updates` | `true` | Check public GitHub releases once at startup; beta installations include betas, stable installations only stable releases. No automatic download. |
| `command-alias-mw` | `true` | Enable `/mw` as an alias for `/modifyworld`; restart after changing. Other plugins may claim `/mw`. |
| `op-bypass` | `false` | When true, operators bypass Modifyworld checks including explicit denials; when false, OP fallback alone grants no access |
| `language` | `en` | Server message language: `en`, `de`, `es`, `fr`, `pt_br`, `pl`, `tr`, or `own` |
| `require-login-permission` | `false` | Require `modifyworld.login` in addition to Minecraft's admission checks |

`item-restrictions` is an event-driven scan, not continuous monitoring. A denial
of `items.hold` can move the item out of the hotbar or drop it when storage is full.

Boolean settings must use `true` or `false`, without quotes.

### Update notifications

- Check GitHub releases once after successful startup, without blocking the server.
- Beta installations consider newer betas and stable releases; stable installations
  consider only stable releases.
- Skip releases whose declared minimum server version is newer than the running
  server, including an optional minimum Paper build for that Minecraft version.
  Releases without this information remain eligible.
- Link to Hangar for downloads; never download or install automatically.
- Set `check-for-updates: false` and restart to disable the check. A missing key defaults to `true`.

Network failures produce a short notice and do not disable protection.
A notification does not guarantee server compatibility. Requests use the public
GitHub API without credentials or player/server identifiers. GitHub receives
the connection IP address and plugin version in the user agent.

### Command shortcut

`/mw` is enabled even if `command-alias-mw` is missing from an existing file.
Add `command-alias-mw: false` and restart to disable it. `/modifyworld` remains
available; another plugin's `/mw` command is preserved when disabling the alias.

### Login permission

Leave `require-login-permission: false` if Minecraft's whitelist is your only
admission policy. Enabling it additionally requires `modifyworld.login`; its
live LuckPerms behavior still needs verification.
The old `whitelist` key is migrated to this setting unless the new key is already set.
Neither changes Minecraft's whitelist.

### Configuration upgrades

Missing settings use their defaults; they are not necessarily written into an
existing file. Configurations needing migration are updated automatically.
Obsolete `use-material-names` and `check-metadata` options are removed.
Modern material names are always used; LuckPerms permissions must be converted separately.
See [configuration and language upgrades](#configuration-and-language-upgrades)
for backups and custom messages.

### Messages

Set `language` in `config.yml` to `en` (English), `de` (German), `es` (Spanish),
`fr` (French), `pt_br` (Brazilian Portuguese), `pl` (Polish), or `tr` (Turkish).
This applies server-wide to denial messages and `/modifyworld check`.
Language files are created in `plugins/Modifyworld/lang/` at startup.

For custom messages, copy a language file to `lang/own.yml` and set `language: own`.
Edit that file directly, keep `messages: {}` in `config.yml`, and restart after
changes. Missing entries fall back to the bundled selected language, then English;
`own` uses English as its fallback. Modifyworld does not translate system-provided material or entity names.

### Configuration and language upgrades

- Existing settings and custom translations are preserved. Missing diagnostic
  texts are added when their migration applies; otherwise bundled fallback texts are used.
- Changed configuration and language files are backed up as `.bak`, then `.bak.1`, etc.
  A version-only configuration update creates no backup.
- Legacy `messages` move to `lang/own.yml`. If existing texts conflict, startup
  stops so you can merge them manually; the existing file is preserved.
- `config-version` is managed automatically; do not edit it.
- Invalid configuration or language files stop plugin startup. Check the server log.

### Message formatting

- Save files as UTF-8; umlauts and other accented characters are supported.
- Use quoted YAML strings. Every `check.*` value must contain a single line;
  the command fixes the report's order and colors.
- Denial messages support `&` color codes. `%s` in `message-format` inserts the
  message; this format also controls its prefix. Login rejection omits this wrapper.
- `$1` represents the item, bucket contents or entity; `$3` represents the
  interaction target or inventory type. `$permission` inserts the checked node.
- Parent permission messages apply to child nodes unless a more specific message
  exists; `default-message` is the final fallback.

Example entries in `lang/own.yml`:

```yaml
message-format: '&f[&2Modifyworld&f]&4 %s'
default-message: 'You do not have permission for this action.'
modifyworld.items.put: 'You may not put &a$1&4 into this container.'
```

## Migrating permissions from legacy Modifyworld

This section applies when moving from legacy Modifyworld (the PermissionsEx-era
plugin) to this Paper port. Routine updates between this port's beta releases do not
require the numeric-ID conversion described below.

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

## Testing and backups

Test new versions with your permissions, worlds and plugin combination on a
separate server before production use. Keep backups of worlds, configuration
and permissions, and check that they can be restored.

Automated tests cover selected behavior; test your actual server setup as well.
Release details and validation results are in [RELEASE-NOTES.md](RELEASE-NOTES.md).

## Build and validation

Use JDK 25 and Maven 3.9.11:

```sh
mvn clean verify
```

Alternatively, `./build.sh` selects configured user-local tools and runs the same
build. See its setup comments or [DEVELOPMENT.md](DEVELOPMENT.md) for tool paths,
offline builds and IDE test configuration.

The build runs automated tests and produces:

- `target/Modifyworld.jar`
- `target/Modifyworld-2.0.0-BETA.7.zip`

The first build downloads dependencies. Paper's API is supplied by the server;
Paper and test libraries are not bundled into the plugin.

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
`Modifyworld-2.0.0-BETA.7.zip` includes the JAR, documentation, license and the complete
corresponding project source under `source/`, including tests and Maven build files.
