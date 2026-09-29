// SPDX-License-Identifier: GPL-2.0-or-later
// Modified or added for the Paper port on 2026-09-28 and 2026-09-29; see NOTICE.
// Modified on 2026-09-28: move to the neutral modifyworld namespace.
package modifyworld;

import java.util.Locale;
import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import modifyworld.handlers.BlockListener;
import modifyworld.handlers.PlayerListener;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PermissionRegressionTest {
    private Player player;
    private Block block;
    private PlayerListener players;
    private BlockListener blocks;

    @BeforeEach
    void setup() {
        Plugin plugin = mock(Plugin.class);
        Server server = mock(Server.class);
        when(plugin.getServer()).thenReturn(server);
        when(server.getPluginManager()).thenReturn(mock(PluginManager.class));
        YamlConfiguration config = new YamlConfiguration();
        config.set("item-use-check", true);
        PlayerInformer informer = mock(PlayerInformer.class);
        players = new PlayerListener(plugin, config, informer);
        blocks = new BlockListener(plugin, config, informer);
        player = mock(Player.class);
        block = mock(Block.class);
        when(block.getType()).thenReturn(Material.TNT);
    }

    private ItemStack item(Material material) {
        ItemStack item = mock(ItemStack.class);
        when(item.getType()).thenReturn(material);
        return item;
    }

    @Test
    void materialNamesAreIndependentOfLocaleAndItemDamage() {
        Locale previous = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            assertEquals("flintandsteel", players.getItemPermission(item(Material.FLINT_AND_STEEL)));
            assertEquals("lavabucket", players.getItemPermission(item(Material.LAVA_BUCKET)));
            assertEquals("air", players.getItemPermission(null));
        } finally {
            Locale.setDefault(previous);
        }
    }

    @Test
    void blockChecksUseFreshPermissionsAndNeverUndoCancellation() {
        String node = "modifyworld.blocks.destroy.tnt";
        when(player.hasPermission(node)).thenReturn(false, true, true);
        BlockBreakEvent denied = new BlockBreakEvent(block, player);
        blocks.onBlockBreak(denied);
        assertTrue(denied.isCancelled());
        BlockBreakEvent allowed = new BlockBreakEvent(block, player);
        blocks.onBlockBreak(allowed);
        assertFalse(allowed.isCancelled());
        BlockBreakEvent protectedRegion = new BlockBreakEvent(block, player);
        protectedRegion.setCancelled(true);
        blocks.onBlockBreak(protectedRegion);
        assertTrue(protectedRegion.isCancelled());
        verify(player, times(3)).hasPermission(node);
    }

    @Test
    void tntPlacementIsDenied() {
        BlockPlaceEvent event = new BlockPlaceEvent(block, null, block,
                item(Material.TNT), player, true, EquipmentSlot.OFF_HAND);
        blocks.onBlockPlace(event);
        assertTrue(event.isCancelled());
        verify(player).hasPermission("modifyworld.blocks.place.tnt");
    }

    @Test
    void lavaEmptyingChecksContentInBothHands() {
        for (EquipmentSlot hand : new EquipmentSlot[] {EquipmentSlot.HAND, EquipmentSlot.OFF_HAND}) {
            PlayerBucketEmptyEvent event = new PlayerBucketEmptyEvent(player, block, block,
                    BlockFace.UP, Material.LAVA_BUCKET, item(Material.BUCKET), hand);
            players.onPlayerBucketEmpty(event);
            assertTrue(event.isCancelled());
        }
        verify(player, times(2)).hasPermission("modifyworld.bucket.empty.lava");
    }

    @Test
    void lavaFillingUsesResultInsteadOfClickedBlock() {
        when(block.getType()).thenReturn(Material.STONE);
        PlayerBucketFillEvent event = new PlayerBucketFillEvent(player, block, block,
                BlockFace.UP, Material.BUCKET, item(Material.LAVA_BUCKET), EquipmentSlot.OFF_HAND);
        players.onPlayerBucketFill(event);
        assertTrue(event.isCancelled());
        verify(player).hasPermission("modifyworld.bucket.fill.lava");
        verify(player, never()).hasPermission("modifyworld.bucket.fill.stone");
    }

    @Test
    void offhandIgnitionUsesEventItem() {
        PlayerInteractEvent event = new PlayerInteractEvent(player, Action.RIGHT_CLICK_BLOCK,
                item(Material.FLINT_AND_STEEL), block, BlockFace.UP, EquipmentSlot.OFF_HAND);
        players.onPlayerInteract(event);
        assertEquals(Event.Result.DENY, event.useItemInHand());
        verify(player).hasPermission("modifyworld.items.use.flintandsteel.on.block.tnt");
        verify(player, never()).getInventory();
    }

    @Test
    void allowedItemUseDoesNotOverrideRegionDenial() {
        when(player.hasPermission("modifyworld.items.use.flintandsteel.on.block.tnt")).thenReturn(true);
        PlayerInteractEvent event = new PlayerInteractEvent(player, Action.RIGHT_CLICK_BLOCK,
                item(Material.FLINT_AND_STEEL), block, BlockFace.UP, EquipmentSlot.HAND);
        event.setCancelled(true);
        players.onPlayerInteract(event);
        assertEquals(Event.Result.DENY, event.useItemInHand());
        assertEquals(Event.Result.DENY, event.useInteractedBlock());
    }
    @Test
    void emptyHandUsesAirPermission() {
        PlayerInteractEvent event = new PlayerInteractEvent(player, Action.RIGHT_CLICK_BLOCK,
                null, block, BlockFace.UP, EquipmentSlot.HAND);
        players.onPlayerInteract(event);
        verify(player).hasPermission("modifyworld.items.use.air.on.block.tnt");
        assertEquals(Event.Result.DENY, event.useInteractedBlock());
    }

    @Test
    void allowedLavaFillingPreservesExistingCancellation() {
        when(player.hasPermission("modifyworld.bucket.fill.lava")).thenReturn(true);
        PlayerBucketFillEvent event = new PlayerBucketFillEvent(player, block, block,
                BlockFace.UP, Material.BUCKET, item(Material.LAVA_BUCKET), EquipmentSlot.HAND);
        players.onPlayerBucketFill(event);
        assertFalse(event.isCancelled());
        event.setCancelled(true);
        players.onPlayerBucketFill(event);
        assertTrue(event.isCancelled());
    }

    @Test
    void playerPickupChecksModernMaterialName() {
        org.bukkit.entity.Item dropped = mock(org.bukkit.entity.Item.class);
        ItemStack tnt = item(Material.TNT);
        when(dropped.getItemStack()).thenReturn(tnt);
        org.bukkit.event.entity.EntityPickupItemEvent event =
                new org.bukkit.event.entity.EntityPickupItemEvent(player, dropped, 0);
        players.onPlayerPickupItem(event);
        assertTrue(event.isCancelled());
        verify(player).hasPermission("modifyworld.items.pickup.tnt");
    }

}
