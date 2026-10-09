// SPDX-License-Identifier: GPL-2.0-or-later
// Modified on 2026-10-08: clarify nullness at test API boundaries.
// Modified or added for the Paper port on 2026-09-28 and 2026-09-29; see NOTICE.
package modifyworld;

import io.papermc.paper.event.player.AsyncChatEvent;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;
import modifyworld.handlers.*;
import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.entity.Vehicle;
import org.bukkit.event.block.BlockMultiPlaceEvent;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.event.vehicle.VehicleEntityCollisionEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

// Local null-warning suppressions cover Mockito's unannotated mock/spy return types.
class ApiCompatibilityTest {
    private Plugin plugin;
    private BukkitScheduler scheduler;
    private Player player;
    private PlayerInformer informer;
    private YamlConfiguration config;

    @BeforeEach
    void setup() {
        plugin = mock(Plugin.class);
        @SuppressWarnings("null")
        Server server = mock(Server.class);
        scheduler = mock(BukkitScheduler.class);
        when(plugin.getServer()).thenReturn(server);
        when(plugin.getLogger()).thenReturn(mock(Logger.class));
        when(server.getScheduler()).thenReturn(scheduler);
        player = mock(Player.class);
        informer = mock(PlayerInformer.class);
        config = new YamlConfiguration();
    }

    @Test
    void constructorsNeverRegisterPartiallyConstructedListeners() {
        new PlayerListener(plugin, config, informer);
        new BlockListener(plugin, config, informer);
        verifyNoInteractions(plugin);
    }

    @Test
    void asynchronousChatDefersPermissionLookupAndMessagesToScheduler() throws Exception {
        PlayerListener listener = new PlayerListener(plugin, config, informer);
        @SuppressWarnings("null")
        AsyncChatEvent event = mock(AsyncChatEvent.class, withSettings().mockMaker(org.mockito.MockMakers.INLINE));
        when(event.isAsynchronous()).thenReturn(true);
        when(event.getPlayer()).thenReturn(player);
        CompletableFuture<Boolean> result = new CompletableFuture<>();
        CompletableFuture<Callable<Boolean>> scheduled = new CompletableFuture<>();
        when(scheduler.callSyncMethod(eq(plugin), org.mockito.ArgumentMatchers.<Callable<Boolean>>any()))
                .thenAnswer(invocation -> {
                    scheduled.complete(invocation.getArgument(1));
                    return result;
                });
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread worker = new Thread(() -> {
            try { listener.onPlayerChat(event); }
            catch (Throwable error) { failure.set(error); }
        });
        worker.start();
        try {
            Callable<Boolean> check = scheduled.get(5, java.util.concurrent.TimeUnit.SECONDS);
            verify(player, never()).hasPermission(anyString());
            verifyNoInteractions(informer);
            result.complete(check.call());
        } finally {
            result.complete(false);
            worker.join(5000);
        }
        assertFalse(worker.isAlive());
        assertNull(failure.get());
        verify(event).setCancelled(true);
        verify(informer).informPlayer(player, "modifyworld.chat");
    }

    @Test
    void synchronousChatNeverWaitsOnSchedulerAndPreservesDenials() {
        when(player.hasPermission("modifyworld.chat")).thenReturn(true);
        @SuppressWarnings("null")
        AsyncChatEvent event = mock(AsyncChatEvent.class, withSettings().mockMaker(org.mockito.MockMakers.INLINE));
        when(event.getPlayer()).thenReturn(player);
        new PlayerListener(plugin, config, informer).onPlayerChat(event);
        verifyNoInteractions(scheduler);
        verify(event, never()).setCancelled(anyBoolean());
    }

    @Test
    void chatLookupFailureCancelsMessage() {
        @SuppressWarnings("null")
        AsyncChatEvent event = mock(AsyncChatEvent.class, withSettings().mockMaker(org.mockito.MockMakers.INLINE));
        when(event.isAsynchronous()).thenReturn(true);
        CompletableFuture<Boolean> failure = CompletableFuture.failedFuture(new IllegalStateException("provider failed"));
        when(scheduler.callSyncMethod(eq(plugin), org.mockito.ArgumentMatchers.<Callable<Boolean>>any())).thenReturn(failure);
        new PlayerListener(plugin, config, informer).onPlayerChat(event);
        verify(event).setCancelled(true);
    }

    @Test
    void multiPlacementChecksEveryAffectedBlock() {
        @SuppressWarnings("null")
        Block first = mock(Block.class);
        @SuppressWarnings("null")
        Block second = mock(Block.class);
        when(first.getType()).thenReturn(Material.STONE);
        when(second.getType()).thenReturn(Material.TNT);
        @SuppressWarnings("null")
        BlockState a = mock(BlockState.class);
        @SuppressWarnings("null")
        BlockState b = mock(BlockState.class);
        when(a.getBlock()).thenReturn(first);
        when(b.getBlock()).thenReturn(second);
        @SuppressWarnings("null")
        BlockMultiPlaceEvent event = mock(BlockMultiPlaceEvent.class);
        when(event.getPlayer()).thenReturn(player);
        when(event.getReplacedBlockStates()).thenReturn(List.of(a, b));
        when(player.hasPermission("modifyworld.blocks.place.stone")).thenReturn(true);
        new BlockListener(plugin, config, informer).onBlockPlace(event);
        verify(player).hasPermission("modifyworld.blocks.place.tnt");
        verify(event).setCancelled(true);
    }

    @Test
    void vehicleCollisionUsesSupportedCancellation() {
        VehicleEntityCollisionEvent event = new VehicleEntityCollisionEvent(mock(Vehicle.class), player);
        // No vehicle name is needed when checking the already assembled permission result.
        @SuppressWarnings("null")
        VehicleListener listener = spy(new VehicleListener(plugin, config, informer));
        doReturn(true).when(listener)._permissionDenied(eq(player), eq("modifyworld.vehicle.collide"), any());
        listener.onVehicleEntityCollision(event);
        assertTrue(event.isCancelled());
    }

    @Test
    @SuppressWarnings("deprecation")
    void optionalLoginDenialPreservesEarlierBan() {
        LoginListener listener = new LoginListener(plugin, config, informer);
        @SuppressWarnings("null")
        PlayerLoginEvent event = mock(PlayerLoginEvent.class, withSettings().mockMaker(org.mockito.MockMakers.INLINE));
        when(event.getResult()).thenReturn(PlayerLoginEvent.Result.KICK_BANNED);
        listener.onLogin(event);
        verify(event, never()).getPlayer();
    }
}
