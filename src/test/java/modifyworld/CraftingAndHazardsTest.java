// SPDX-License-Identifier: GPL-2.0-or-later
// Modified on 2026-10-08: clarify nullness at test API boundaries.
// Modified or added for the Paper port on 2026-09-28 and 2026-09-29; see NOTICE.
package modifyworld;

import modifyworld.handlers.BlockListener;
import modifyworld.handlers.ContainerListener;
import modifyworld.handlers.PlayerListener;
import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.block.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.*;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

// Local null-warning suppressions cover Mockito's unannotated mock return types.
class CraftingAndHazardsTest {
    private PlayerListener players;
    private BlockListener blocks;
    private ContainerListener containers;
    private Player player;
    private Block block;

    @BeforeAll
    static void registries() {
        TestRegistries.initializeInventoryTypes();
    }

    @BeforeEach
    void setup() {
        @SuppressWarnings("null")
        Plugin plugin = mock(Plugin.class);
        @SuppressWarnings("null")
        Server server = mock(Server.class);
        when(plugin.getServer()).thenReturn(server);
        when(server.getPluginManager()).thenReturn(mock(PluginManager.class));
        YamlConfiguration config = new YamlConfiguration();
        config.set("item-use-check", true);
        @SuppressWarnings("null")
        PlayerInformer informer = mock(PlayerInformer.class);
        players = new PlayerListener(plugin, config, informer);
        blocks = new BlockListener(plugin, config, informer);
        containers = new ContainerListener(plugin, config, informer);
        player = mock(Player.class);
        when(player.hasPermission(anyString())).thenReturn(true);
        block = mock(Block.class);
        when(block.getType()).thenReturn(Material.TNT);
    }

    private ItemStack item(Material material) {
        @SuppressWarnings("null")
        ItemStack item = mock(ItemStack.class);
        when(item.getType()).thenReturn(material);
        return item;
    }

    private CraftItemEvent craft(Material result, ClickType click, InventoryAction action, InventoryType type) {
        @SuppressWarnings("null")
        CraftingInventory inventory = mock(CraftingInventory.class);
        when(inventory.getType()).thenReturn(type);
        when(inventory.getSize()).thenReturn(type == InventoryType.WORKBENCH ? 10 : 5);
        @SuppressWarnings("null")
        InventoryView view = mock(InventoryView.class);
        when(view.getPlayer()).thenReturn(player);
        when(view.getTopInventory()).thenReturn(inventory);
        when(view.getInventory(0)).thenReturn(inventory);
        ItemStack output = result == null ? null : item(result);
        when(view.getItem(0)).thenReturn(output);
        // A modified output must be checked instead of this recipe's nominal result.
        @SuppressWarnings("null")
        Recipe recipe = mock(Recipe.class);
        ItemStack nominal = item(Material.STONE);
        when(recipe.getResult()).thenReturn(nominal);
        return new CraftItemEvent(recipe, view, InventoryType.SlotType.RESULT, 0, click, action, 1);
    }

    @ParameterizedTest
    @CsvSource({
        "TNT,LEFT,PICKUP_ALL,CRAFTING,tnt",
        "TNT,RIGHT,PICKUP_HALF,CRAFTING,tnt",
        "TNT,SWAP_OFFHAND,HOTBAR_SWAP,WORKBENCH,tnt",
        "TNT,SHIFT_LEFT,MOVE_TO_OTHER_INVENTORY,WORKBENCH,tnt",
        "TNT,SHIFT_RIGHT,MOVE_TO_OTHER_INVENTORY,WORKBENCH,tnt",
        "FLINT_AND_STEEL,NUMBER_KEY,HOTBAR_SWAP,WORKBENCH,flintandsteel",
        "FLINT_AND_STEEL,DROP,DROP_ONE_SLOT,CRAFTING,flintandsteel",
        "FLINT_AND_STEEL,CONTROL_DROP,DROP_ALL_SLOT,WORKBENCH,flintandsteel",
        "LAVA_BUCKET,LEFT,PICKUP_ALL,WORKBENCH,lavabucket"
    })
    void craftingDenialCoversExtractionModesAndActualOutput(Material material, ClickType click,
            InventoryAction action, InventoryType type, String name) {
        CraftItemEvent event = craft(material, click, action, type);
        when(player.hasPermission("modifyworld.items.craft." + name)).thenReturn(false);
        players.onItemCraft(event);
        assertTrue(event.isCancelled());
        verify(player).hasPermission("modifyworld.items.craft." + name);
        verify(player, never()).hasPermission("modifyworld.items.craft.stone");
    }

    @Test
    void craftingAndContainerRulesBothApply() {
        CraftItemEvent event = craft(Material.TNT, ClickType.SHIFT_LEFT,
                InventoryAction.MOVE_TO_OTHER_INVENTORY, InventoryType.WORKBENCH);
        when(player.hasPermission("modifyworld.items.take.tnt.of.workbench")).thenReturn(false);
        containers.onClick(event);
        players.onItemCraft(event);
        assertTrue(event.isCancelled());
    }

    @Test
    void allowedCraftAndExistingCancellationArePreserved() {
        CraftItemEvent event = craft(Material.FLINT_AND_STEEL, ClickType.SHIFT_LEFT,
                InventoryAction.MOVE_TO_OTHER_INVENTORY, InventoryType.WORKBENCH);
        players.onItemCraft(event);
        assertFalse(event.isCancelled());
        event.setCancelled(true);
        players.onItemCraft(event);
        assertTrue(event.isCancelled());
    }

    @Test
    void absentCraftingOutputDoesNotCheckNominalRecipe() {
        CraftItemEvent event = craft(null, ClickType.LEFT, InventoryAction.NOTHING, InventoryType.CRAFTING);
        players.onItemCraft(event);
        verify(player, never()).hasPermission(anyString());
    }

    @ParameterizedTest
    @CsvSource({
        "FLINT_AND_STEEL,TNT,HAND,flintandsteel,tnt",
        "FLINT_AND_STEEL,TNT,OFF_HAND,flintandsteel,tnt",
        "FIRE_CHARGE,TNT,HAND,firecharge,tnt",
        "FIRE_CHARGE,TNT,OFF_HAND,firecharge,tnt",
        "FLINT_AND_STEEL,STONE,OFF_HAND,flintandsteel,stone",
        "FIRE_CHARGE,STONE,HAND,firecharge,stone",
        "LAVA_BUCKET,CAULDRON,HAND,lavabucket,cauldron",
        "LAVA_BUCKET,CAULDRON,OFF_HAND,lavabucket,cauldron"
    })
    void dangerousItemUseChecksCorrectHandAndTarget(Material held, Material target,
            EquipmentSlot hand, String itemName, String blockName) {
        when(block.getType()).thenReturn(target);
        PlayerInteractEvent event = new PlayerInteractEvent(player, Action.RIGHT_CLICK_BLOCK,
                item(held), block, BlockFace.UP, hand);
        when(player.hasPermission("modifyworld.items.use." + itemName + ".on.block." + blockName))
                .thenReturn(false);
        players.onPlayerInteract(event);
        assertEquals(Event.Result.DENY, event.useItemInHand());
        assertEquals(Event.Result.DENY, event.useInteractedBlock());
    }

    @ParameterizedTest
    @EnumSource(value = EquipmentSlot.class, names = {"HAND", "OFF_HAND"})
    void allowedTntUseAndPlacementAreIndependent(EquipmentSlot hand) {
        PlayerInteractEvent use = new PlayerInteractEvent(player, Action.RIGHT_CLICK_BLOCK,
                item(Material.FLINT_AND_STEEL), block, BlockFace.UP, hand);
        players.onPlayerInteract(use);
        assertNotEquals(Event.Result.DENY, use.useItemInHand());
        when(player.hasPermission("modifyworld.blocks.place.tnt")).thenReturn(false);
        BlockPlaceEvent place = new BlockPlaceEvent(block, mock(BlockState.class), block,
                item(Material.TNT), player, true, hand);
        blocks.onBlockPlace(place);
        assertTrue(place.isCancelled());
    }

    @Test
    void playerFirePlacementNeedsLegacyFirePermission() {
        BlockIgniteEvent event = new BlockIgniteEvent(block, BlockIgniteEvent.IgniteCause.FLINT_AND_STEEL, player);
        when(player.hasPermission("modifyworld.blocks.place.fire")).thenReturn(false);
        blocks.onBlockIgnite(event);
        assertTrue(event.isCancelled());
    }

    @Test
    void allowedIgnitionNeverClearsExistingDenial() {
        BlockIgniteEvent event = new BlockIgniteEvent(block, BlockIgniteEvent.IgniteCause.FLINT_AND_STEEL, player);
        blocks.onBlockIgnite(event);
        assertFalse(event.isCancelled());
        event.setCancelled(true);
        blocks.onBlockIgnite(event);
        assertTrue(event.isCancelled());
    }

    @Test
    void naturalIgnitionHasNoPlayerPermissionCheck() {
        BlockIgniteEvent event = new BlockIgniteEvent(block, BlockIgniteEvent.IgniteCause.SPREAD, (Entity) null);
        blocks.onBlockIgnite(event);
        assertFalse(event.isCancelled());
        verify(player, never()).hasPermission(anyString());
    }

    @ParameterizedTest
    @CsvSource({"BUCKET_EMPTY,LAVA_CAULDRON,lava,empty", "BUCKET_FILL,LAVA_CAULDRON,lava,fill",
        "BUCKET_EMPTY,WATER_CAULDRON,water,empty", "BUCKET_FILL,POWDER_SNOW_CAULDRON,powdersnow,fill"})
    void cauldronUsesSameBucketPermissions(CauldronLevelChangeEvent.ChangeReason reason,
            Material content, String name, String action) {
        @SuppressWarnings("null")
        BlockState newState = mock(BlockState.class);
        when(newState.getType()).thenReturn(reason == CauldronLevelChangeEvent.ChangeReason.BUCKET_EMPTY
                ? content : Material.CAULDRON);
        when(block.getType()).thenReturn(reason == CauldronLevelChangeEvent.ChangeReason.BUCKET_FILL
                ? content : Material.CAULDRON);
        CauldronLevelChangeEvent event = new CauldronLevelChangeEvent(block, player, reason, newState);
        when(player.hasPermission("modifyworld.bucket." + action + "." + name)).thenReturn(false);
        @SuppressWarnings("null")
        PlayerInformer messages = mock(PlayerInformer.class);
        blocks = new BlockListener(mock(Plugin.class), new YamlConfiguration(), messages);
        blocks.onCauldronBucket(event);
        assertTrue(event.isCancelled());
        verify(messages).informPlayer(player, "modifyworld.bucket." + action + "." + name, name);
    }

    @Test
    void cauldronAllowsGrantedLavaAndPreservesCancellation() {
        @SuppressWarnings("null")
        BlockState state = mock(BlockState.class);
        when(state.getType()).thenReturn(Material.LAVA_CAULDRON);
        CauldronLevelChangeEvent event = new CauldronLevelChangeEvent(block, player,
                CauldronLevelChangeEvent.ChangeReason.BUCKET_EMPTY, state);
        blocks.onCauldronBucket(event);
        assertFalse(event.isCancelled());
        event.setCancelled(true);
        blocks.onCauldronBucket(event);
        assertTrue(event.isCancelled());
    }

    @ParameterizedTest
    @EnumSource(value = CauldronLevelChangeEvent.ChangeReason.class,
            names = {"NATURAL_FILL", "EXTINGUISH", "BOTTLE_FILL"})
    void unrelatedCauldronChangesAreNotBucketTransfers(CauldronLevelChangeEvent.ChangeReason reason) {
        CauldronLevelChangeEvent event = new CauldronLevelChangeEvent(block, player, reason, mock(BlockState.class));
        blocks.onCauldronBucket(event);
        assertFalse(event.isCancelled());
        verify(player, never()).hasPermission(anyString());
    }
    @ParameterizedTest
    @EnumSource(value = EquipmentSlot.class, names = {"HAND", "OFF_HAND"})
    void creeperIgnitionChecksItemInEventHand(EquipmentSlot hand) {
        @SuppressWarnings("null")
        org.bukkit.entity.Creeper creeper = mock(org.bukkit.entity.Creeper.class);
        when(creeper.getType()).thenReturn(org.bukkit.entity.EntityType.CREEPER);
        @SuppressWarnings("null")
        PlayerInventory inventory = mock(PlayerInventory.class);
        when(player.getInventory()).thenReturn(inventory);
        ItemStack flint = item(Material.FLINT_AND_STEEL);
        when(inventory.getItem(hand)).thenReturn(flint);
        when(player.hasPermission("modifyworld.items.use.flintandsteel.on.entity.monster.creeper"))
                .thenReturn(false);
        PlayerInteractEntityEvent event = new PlayerInteractEntityEvent(player, creeper, hand);
        players.onPlayerInteractEntity(event);
        assertTrue(event.isCancelled());
        verify(inventory).getItem(hand);
    }

    @ParameterizedTest
    @CsvSource({"TNT,tnt", "FLINT_AND_STEEL,flintandsteel", "LAVA_BUCKET,lavabucket"})
    void dangerousItemPickupHonorsDenyAndAllow(Material material, String name) {
        @SuppressWarnings("null")
        org.bukkit.entity.Item dropped = mock(org.bukkit.entity.Item.class);
        ItemStack stack = item(material);
        when(dropped.getItemStack()).thenReturn(stack);
        when(player.hasPermission("modifyworld.items.pickup." + name)).thenReturn(false, true);
        org.bukkit.event.entity.EntityPickupItemEvent denied =
                new org.bukkit.event.entity.EntityPickupItemEvent(player, dropped, 0);
        players.onPlayerPickupItem(denied);
        assertTrue(denied.isCancelled());
        org.bukkit.event.entity.EntityPickupItemEvent allowed =
                new org.bukkit.event.entity.EntityPickupItemEvent(player, dropped, 0);
        players.onPlayerPickupItem(allowed);
        assertFalse(allowed.isCancelled());
    }

    @ParameterizedTest
    @EnumSource(value = EquipmentSlot.class, names = {"HAND", "OFF_HAND"})
    void lavaBucketFillAndEmptyRespectBothPermissionOutcomes(EquipmentSlot hand) {
        when(player.hasPermission("modifyworld.bucket.fill.lava")).thenReturn(false, true);
        when(player.hasPermission("modifyworld.bucket.empty.lava")).thenReturn(false, true);
        for (boolean denied : new boolean[] {true, false}) {
            PlayerBucketFillEvent fill = new PlayerBucketFillEvent(player, block, block,
                    BlockFace.UP, Material.BUCKET, item(Material.LAVA_BUCKET), hand);
            players.onPlayerBucketFill(fill);
            assertEquals(denied, fill.isCancelled());
            PlayerBucketEmptyEvent empty = new PlayerBucketEmptyEvent(player, block, block,
                    BlockFace.UP, Material.LAVA_BUCKET, item(Material.BUCKET), hand);
            players.onPlayerBucketEmpty(empty);
            assertEquals(denied, empty.isCancelled());
        }
    }

}
