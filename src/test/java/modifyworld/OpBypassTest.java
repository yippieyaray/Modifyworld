// SPDX-License-Identifier: GPL-2.0-or-later
// Modified on 2026-10-05: cover OP fallback, assigned permissions and placement events.
// Modified or added for the Paper port on 2026-09-28 and 2026-09-29; see NOTICE.
package modifyworld;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OpBypassTest {
    @Test
    void bypassRequiresBothConfigurationAndOperatorStatus() {
        for (boolean enabled : new boolean[] {false, true}) {
            for (boolean operator : new boolean[] {false, true}) {
                var config = new YamlConfiguration();
                config.set("op-bypass", enabled);
                var informer = mock(PlayerInformer.class);
                var listener = new ModifyworldListener(mock(Plugin.class), config, informer) { };
                var player = mock(Player.class);
                when(player.isOp()).thenReturn(operator);
                boolean denied = !(enabled && operator);
                assertEquals(denied, listener.permissionDenied(player, "modifyworld.items.put.tnt.of.chest"));
                assertEquals(denied, listener._permissionDenied(player, "modifyworld.login"));
                if (!denied) {
                    verify(player, never()).hasPermission(anyString());
                    verifyNoInteractions(informer);
                } else {
                    verify(informer).informPlayer(player, "modifyworld.items.put.tnt.of.chest");
                }
            }
        }
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({
        "false,false,true", "false,true,false", "true,false,true", "true,true,false"
    })
    void assignedPermissionHasSameResultWithAndWithoutOp(boolean operator, boolean grant, boolean denied) {
        var listener = new ModifyworldListener(mock(Plugin.class), new YamlConfiguration(), mock(PlayerInformer.class)) { };
        var player = mock(Player.class);
        when(player.isOp()).thenReturn(operator);
        // Bukkit exposes effective assignments, including inherited and wildcard grants.
        when(player.isPermissionSet("modifyworld.items.put.tnt.of.chest")).thenReturn(true);
        when(player.hasPermission("modifyworld.items.put.tnt.of.chest")).thenReturn(grant);
        assertEquals(denied, listener._permissionDenied(player, "modifyworld.items.put", "tnt", "of", "chest"));
        verify(player).hasPermission("modifyworld.items.put.tnt.of.chest");
        verify(player, never()).setOp(anyBoolean());
    }

    @Test
    void opFallbackCannotGrantUnassignedPermissions() {
        var informer = mock(PlayerInformer.class);
        var listener = new ModifyworldListener(mock(Plugin.class), new YamlConfiguration(), informer) { };
        var player = mock(Player.class);
        when(player.isOp()).thenReturn(true);
        when(player.hasPermission(anyString())).thenReturn(true);
        assertTrue(listener.permissionDenied(player, "modifyworld.blocks.place", org.bukkit.Material.TNT));
        assertTrue(listener._permissionDenied(player, "modifyworld.login"));
        verify(informer).informPlayer(player, "modifyworld.blocks.place.tnt", org.bukkit.Material.TNT);
        verify(player, never()).setOp(anyBoolean());
    }

    @Test
    void worldChangesAndPermissionRemovalAreNotCached() {
        var listener = new ModifyworldListener(mock(Plugin.class), new YamlConfiguration(), mock(PlayerInformer.class)) { };
        var player = mock(Player.class);
        var world = new java.util.concurrent.atomic.AtomicReference<String>("build");
        var assignments = new java.util.HashMap<String, Boolean>();
        assignments.put("build", true);
        assignments.put("survival", false);
        when(player.isOp()).thenReturn(true);
        when(player.isPermissionSet("modifyworld.blocks.place.tnt"))
                .thenAnswer(call -> assignments.containsKey(world.get()));
        when(player.hasPermission("modifyworld.blocks.place.tnt"))
                .thenAnswer(call -> assignments.getOrDefault(world.get(), true));
        assertFalse(listener._permissionDenied(player, "modifyworld.blocks.place.tnt"));
        world.set("survival");
        assertTrue(listener._permissionDenied(player, "modifyworld.blocks.place.tnt"));
        world.set("build");
        assignments.clear();
        assertTrue(listener._permissionDenied(player, "modifyworld.blocks.place.tnt"));
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
    void tntPlacementRejectsOpFallbackInBothHandsAndPreservesEarlierCancellation(boolean bypass) {
        var config = new YamlConfiguration();
        config.set("op-bypass", bypass);
        var listener = new modifyworld.handlers.BlockListener(mock(Plugin.class), config, mock(PlayerInformer.class));
        var player = mock(Player.class);
        when(player.isOp()).thenReturn(true);
        when(player.hasPermission(anyString())).thenReturn(true);
        var block = mock(org.bukkit.block.Block.class);
        when(block.getType()).thenReturn(org.bukkit.Material.TNT);
        for (var hand : new org.bukkit.inventory.EquipmentSlot[] {
                org.bukkit.inventory.EquipmentSlot.HAND, org.bukkit.inventory.EquipmentSlot.OFF_HAND}) {
            var event = new org.bukkit.event.block.BlockPlaceEvent(block, null, block,
                    mock(org.bukkit.inventory.ItemStack.class), player, true, hand);
            listener.onBlockPlace(event);
            assertEquals(!bypass, event.isCancelled());
            event.setCancelled(true);
            listener.onBlockPlace(event);
            assertTrue(event.isCancelled());
        }
    }
}
