# LuckPerms example: four ranks, two worlds

Run these commands in the Minecraft server console. Create `admin`, `vip` and
`player` if they do not exist; LuckPerms already provides `default`.
Replace `spawn` and `survival` with your actual world names.
Use `item-use-check: true` and `op-bypass: false` in Modifyworld.

| Rank | Access |
| --- | --- |
| Admin | All Modifyworld permissions globally |
| VIP | All Modifyworld permissions in survival; command access globally; basic access in spawn |
| Player | Survival access with the specific TNT, lava and ignition restrictions below |
| Default | Basic chat, movement and inventory access; empty-hand block interaction in spawn |

The ranks below are independent: do not make VIP or Admin inherit Player's
restrictions. Each may inherit the basic grants from `default`. The listed order
is for readability and does not configure LuckPerms group weights or inheritance.
Existing user permissions and other group assignments can change the result.

## Admin

```text
lp group admin permission set modifyworld.* true
```

## VIP

```text
lp group vip permission set modifyworld.command.* true
lp group vip permission set modifyworld.* true world=survival
```

## Player

```text
lp group player permission set modifyworld.command.check true
lp group player permission set modifyworld.* true world=survival
lp group player permission set modifyworld.blocks.place.tnt false world=survival
lp group player permission set modifyworld.blocks.place.fire false world=survival
lp group player permission set modifyworld.bucket.empty.lava false world=survival
lp group player permission set modifyworld.bucket.fill.lava false world=survival
lp group player permission set modifyworld.items.use.flintandsteel.on.block.* false world=survival
lp group player permission set modifyworld.items.use.flintandsteel.on.entity.* false world=survival
lp group player permission set modifyworld.items.use.firecharge.on.block.* false world=survival
lp group player permission set modifyworld.items.use.firecharge.on.entity.* false world=survival
lp group player permission set modifyworld.items.take.tnt.of.* false world=survival
lp group player permission set modifyworld.items.put.tnt.of.* false world=survival
lp group player permission set modifyworld.items.pickup.tnt false world=survival
lp group player permission set modifyworld.items.drop.tnt false world=survival
lp group player permission set modifyworld.items.craft.tnt false world=survival
```

The global `modifyworld.command.check` grant lets Player use diagnostics in
both worlds, including `spawn`, where Player has no `modifyworld.*` grant.
Admin already has command access globally through `modifyworld.*`; VIP receives
it globally through `modifyworld.command.*`. Player also receives it in
`survival` through the world-scoped wildcard.
The command permission permits self-checks and checks for other online players:

```text
/modifyworld check modifyworld.blocks.place.tnt
/modifyworld check PlayerName modifyworld.blocks.place.tnt
```

The server console can always use the named-player form (without `/`). OPs
receive command access by default, independently of `op-bypass`. See
[Permission diagnostics](../README.md#permission-diagnostics) for details.

## Default

```text
lp group default permission set modifyworld.login true
lp group default permission set modifyworld.chat true
lp group default permission set modifyworld.sneak true
lp group default permission set modifyworld.sprint true
lp group default permission set modifyworld.digestion true
lp group default permission set modifyworld.damage.take.* true
lp group default permission set modifyworld.mobtarget.* true
lp group default permission set modifyworld.items.have.* true
lp group default permission set modifyworld.items.hold.* true
lp group default permission set modifyworld.items.use.air.on.block.* true world=spawn
```

These are illustrative action restrictions, not a complete dangerous-item ban.
For example, Player may still transfer lava buckets; emptying lava is checked
separately. Empty-hand interaction does not grant container take/put permission.
The broad `modifyworld.*` grants also cover movement, chat and damage checks.
`modifyworld.login` is only checked with `require-login-permission: true`.

WorldGuard still controls regions, including for Admin. Check each rank with a
non-OP account in both worlds before adopting the example. See the
[LuckPerms permission precedence documentation](https://luckperms.net/wiki/Advanced-Setup)
when combining these rules with existing permissions.
