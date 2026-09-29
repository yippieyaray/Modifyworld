# Modifyworld for Paper

Control what players can build, use, pick up, craft, and transfer through
inventories, with permissions that can vary by group and world.

This fork ports [PEXPlugins/Modifyworld](https://github.com/PEXPlugins/Modifyworld)
to **Paper 26.2, Java 25, and LuckPerms**. WorldGuard remains responsible for
regions. Modifyworld adds permission checks and does not clear another plugin's
event cancellation.

> **Beta — 2.0.0-BETA.2**

## Requirements and compatibility

| Component | Target / status |
| --- | --- |
| Minecraft edition | Java Edition |
| Server | Paper for Minecraft 26.2 |
| Java | Java 25 |
| Compile-time Paper API | `26.2.build.129-stable`, pinned in [pom.xml](pom.xml) |
| Permissions | LuckPerms is the intended provider; Modifyworld uses Bukkit permission checks |
| Regions | Optional WorldGuard installation with its required dependencies |
| PermissionsEx / Vault | Neither is required or used |
| Other platforms | Spigot, Folia, other Paper forks, and other Minecraft versions are not supported targets of this port |

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

Modifyworld has no administration commands or live configuration reload.
Restart after changing its configuration. LuckPerms permission changes are
evaluated on subsequent checks without a Modifyworld-specific permission cache.

An invalid configuration or listener startup failure disables Modifyworld and
logs that protection is **not active**. This does not shut down the Minecraft
server. Existing configuration files are not automatically rewritten at startup.

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

### Other retained permissions

| Permission | Meaning |
| --- | --- |
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

## Configuration

The full default file is [config.yml](src/main/resources/config.yml).

| Setting | Default | Effect |
| --- | --- | --- |
| `use-material-names` | `true` | Required for this port; `false` stops startup |
| `check-metadata` | `false` | Legacy data-value checks are unsupported; `true` stops startup |
| `item-use-check` | `true` | Check the item used on a block/entity, including the actual hand; otherwise use ordinary interaction permissions |
| `inform-players` | `true` | Send configured denial messages; deliberately silent checks such as pickup remain silent |
| `item-restrictions` | `false` | Scan inventory on monitored interactions, pickup/drop, and held-slot changes; remove items denied by `items.have` |
| `drop-restricted-item` | `false` | During those inventory scans, drop removed items in the world instead of deleting them |
| `op-bypass` | `false` | Operators bypass all Modifyworld permission checks, including explicit denials; other plugins and Minecraft admission checks remain effective |
| `language` | `en` | Server message language: `en` or `de` |
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

Boolean settings must use YAML booleans (`true`/`false`, without quotes). Defaults fill
missing options in memory; existing files and comments are preserved at startup.

### Messages

At first configuration load, `lang/en.yml` and `lang/de.yml` are copied into the
plugin folder. Select `language: de` for German or `language: en` for English.
Restart after changes. Existing language files are never overwritten.
Missing keys fall back to the bundled selected language, then bundled English,
without rewriting your files. Invalid selected language files stop startup.
Material and entity names in placeholders remain English.

Explicit `messages` entries in `config.yml` override language files. Existing
installations may already contain a complete message section: remove the entries
you want the selected language to supply, retaining any personal overrides.
New configurations contain only `messages: {}`. Bundled German and English messages describe the denied action directly;
legacy configuration switches are not imported.


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
The next startup recreates them; explicit `messages` overrides still take precedence.

Use `%s` in `message-format`, `$permission` for the checked permission, and
`$1`, `$2`, etc. for action arguments where available. `&` color codes are supported
by normal denial messages. The optional login rejection uses its own message
without the normal message-format wrapper.

PEX/Vault per-player or per-world denial-message metadata is no longer read.
Move needed text into this configuration. The historical `individual-messages`
option has no active effect in this port.

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

## Build and validation

Use JDK 25 and Maven (development testing used Maven 3.9.11):

```sh
java -version
mvn -version
mvn clean verify
```

Expected artifacts are `target/Modifyworld.jar` and `target/Modifyworld-bin.zip`.
The first build downloads the Paper API and build/test dependencies. The API is
provided by the server and is not bundled into the plugin.

Build validation uses automated tests with mocked server services and selected
real event classes. Run `mvn verify` to execute them and package the beta.

### Before the first production release

- [ ] Complete and inspect a clean packaged build, including filtered `plugin.yml`.
- [ ] Include the license text in the distribution and finish the source/license audit.
- [ ] Start an isolated Paper 26.2 server with LuckPerms and WorldGuard; record exact versions.
- [ ] Verify group/world rules with non-OP players, world changes, wildcards, and explicit denials.
- [ ] Test both hands: TNT placement/ignition, fire charges, creeper ignition, lava sources and cauldrons.
- [ ] Test normal/shift crafting, hotbar/offhand/drop output extraction, and custom results.
- [ ] Test chest/barrel/shulker/workbench transfers, dragging, double-click, swaps, and bundles.
- [ ] Confirm WorldGuard denials remain effective when Modifyworld grants access, and vice versa.
- [ ] Test Minecraft whitelist independently of the optional `modifyworld.login` requirement.
- [ ] Verify malformed configuration and startup failures clearly leave the plugin disabled.
- [ ] Review remaining creative, command-driven, and automation paths against the intended server policy.

## Credits and license

Original Modifyworld by t3hk0d3 and the contributors to
[PEXPlugins/Modifyworld](https://github.com/PEXPlugins/Modifyworld).
Paper port maintained in [this fork](https://github.com/yippieyaray/Modifyworld).

The inherited source notices grant **GNU GPL version 2 or later**. Original
copyright notices are retained. Before distributing a release, the full license
text and corresponding source distribution requirements must be addressed; the
inherited `PlayerInformer.java` also needs its missing file-level license notice
and provenance reviewed. See the [GNU GPLv2 text](https://www.gnu.org/licenses/gpl-2.0).
