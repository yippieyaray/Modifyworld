// SPDX-License-Identifier: GPL-2.0-or-later
// Modified on 2026-10-08: align beta validation and nullness handling.
// Modified or added for the Paper port on 2026-09-28 and 2026-09-29; see NOTICE.
package modifyworld.bukkit;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.ConsoleCommandSender;
import java.util.List;
import java.util.logging.Logger;
import modifyworld.ModifyworldListener;
import modifyworld.handlers.LoginListener;
import org.bukkit.Server;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.RegisteredListener;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class StartupTest {
    private Modifyworld plugin;
    private PluginManager manager;
    private Logger logger;
    private ConsoleCommandSender console;
    private YamlConfiguration config;

    @BeforeEach
    void setup() {
        plugin = mock(Modifyworld.class, withSettings().mockMaker(org.mockito.MockMakers.INLINE)
                .defaultAnswer(CALLS_REAL_METHODS));
        manager = mock(PluginManager.class);
        // Mockito mock() returns an instance, but its generic API lacks null annotations.
        @SuppressWarnings("null")
        Server server = mock(Server.class);
        logger = mock(Logger.class);
        console = mock(ConsoleCommandSender.class);
        when(server.getConsoleSender()).thenReturn(console);
        doReturn(server).when(plugin).getServer();
        doReturn(logger).when(plugin).getLogger();
        doAnswer(invocation -> getClass().getResourceAsStream("/plugin.yml"))
                .when(plugin).getResource("plugin.yml");
        // Mockito mock() returns an instance, but its generic API lacks null annotations.
        @SuppressWarnings("null")
        var meta = mock(io.papermc.paper.plugin.configuration.PluginMeta.class);
        when(meta.getVersion()).thenReturn("2.0.0-BETA.7");
        doReturn(meta).when(plugin).getPluginMeta();
        when(server.getPluginManager()).thenReturn(manager);
        config = new YamlConfiguration();
        config.options().pathSeparator('/');
        config.createSection("messages");
        config.set("check-for-updates", false);
        doReturn(config).when(plugin).getConfig();
    }

    @Test
    void successfulStartupRegistersEachListenerOnceAndLoginPermissionOnlyWhenEnabled() {
        plugin.onEnable();
        verify(manager, times(5)).registerEvents(any(), same(plugin));
        verify(manager, never()).registerEvents(isA(LoginListener.class), same(plugin));
        var startup = inOrder(console, manager);
        startup.verify(console).sendMessage(Component.text("  |\\/| \\  /\\  /  ", NamedTextColor.YELLOW)
                .append(Component.text("Modifyworld", NamedTextColor.GREEN))
                .append(Component.text(" v2.0.0-BETA.7", NamedTextColor.YELLOW)));
        startup.verify(console).sendMessage(Component.text("  |  |  \\/  \\/   ", NamedTextColor.YELLOW)
                .append(Component.text("Tested on Paper 26.2", NamedTextColor.GRAY)));
        startup.verify(manager, times(5)).registerEvents(any(), same(plugin));
        startup.verify(console).sendMessage(Component.text("[Modifyworld] Modifyworld enabled!", NamedTextColor.GREEN));
        verify(manager, never()).disablePlugin(plugin);
    }

    @Test
    @SuppressWarnings("null")
    void enabledUpdateCheckIsScheduledOnceAndStoppedOnDisable() {
        config.set("check-for-updates", true);
        var checker = mock(UpdateChecker.class, withSettings().mockMaker(org.mockito.MockMakers.INLINE));
        var scheduler = mock(org.bukkit.scheduler.BukkitScheduler.class);
        var task = mock(org.bukkit.scheduler.BukkitTask.class);
        doReturn(checker).when(plugin).createUpdateChecker();
        when(plugin.getServer().getScheduler()).thenReturn(scheduler);
        when(scheduler.runTaskAsynchronously(plugin, checker)).thenReturn(task);
        plugin.onEnable();
        verify(scheduler).runTaskAsynchronously(plugin, checker);
        verify(checker, never()).run();
        plugin.onDisable();
        verify(checker).close();
        verify(task).cancel();
    }

    @Test
    void disabledUpdateCheckDoesNotCreateChecker() {
        plugin.onEnable();
        verify(plugin, never()).createUpdateChecker();
    }

    @Test
    @SuppressWarnings("null")
    void updateSchedulingFailureDoesNotDisableProtection() {
        config.set("check-for-updates", true);
        var checker = mock(UpdateChecker.class, withSettings().mockMaker(org.mockito.MockMakers.INLINE));
        var scheduler = mock(org.bukkit.scheduler.BukkitScheduler.class);
        doReturn(checker).when(plugin).createUpdateChecker();
        when(plugin.getServer().getScheduler()).thenReturn(scheduler);
        when(scheduler.runTaskAsynchronously(plugin, checker)).thenThrow(new IllegalStateException("stopped"));
        plugin.onEnable();
        verify(checker).close();
        verify(manager, never()).disablePlugin(plugin);
        assertFalse(plugin.listeners.isEmpty());
    }

    @Test
    void startupListsAllConfirmedPaperVersionsFromMetadata() {
        doReturn(new java.io.ByteArrayInputStream(
                "tested-paper-versions: ['26.2', '26.3']\n".getBytes(java.nio.charset.StandardCharsets.UTF_8)))
                .when(plugin).getResource("plugin.yml");
        plugin.onEnable();
        verify(console).sendMessage(Component.text("  |  |  \\/  \\/   ", NamedTextColor.YELLOW)
                .append(Component.text("Tested on Paper 26.2, 26.3", NamedTextColor.GRAY)));
        verify(manager, never()).disablePlugin(plugin);
    }

    @Test
    void startupDoesNotClaimTestedVersionsWhenMetadataIsMissing() {
        doReturn(null).when(plugin).getResource("plugin.yml");
        plugin.onEnable();
        verify(console).sendMessage(Component.text("  |  |  \\/  \\/   ", NamedTextColor.YELLOW)
                .append(Component.text("Paper test versions not recorded", NamedTextColor.GRAY)));
        verify(manager, never()).disablePlugin(plugin);
    }

    @Test
    // Mockito mock, verify and stubbing results lack null annotations.
    @SuppressWarnings("null")
    void commandAliasIsEnabledByDefault() {
        plugin.onEnable();
        verify(plugin.getServer(), never()).getCommandMap();
        verify(manager, never()).disablePlugin(plugin);
    }

    @Test
    // Mockito mock, verify and stubbing results lack null annotations.
    @SuppressWarnings("null")
    void disabledCommandAliasRemovesOnlyOurMappings() {
        config.set("command-alias-mw", false);
        var command = mock(org.bukkit.command.PluginCommand.class,
                withSettings().mockMaker(org.mockito.MockMakers.INLINE));
        var map = mock(org.bukkit.command.CommandMap.class);
        var commands = new java.util.HashMap<String, org.bukkit.command.Command>();
        commands.put("modifyworld", command);
        commands.put("mw", command);
        commands.put("modifyworld:mw", command);
        doReturn(command).when(plugin).getCommand("modifyworld");
        when(plugin.getServer().getCommandMap()).thenReturn(map);
        doReturn(commands).when(map).getKnownCommands();

        plugin.onEnable();

        assertSame(command, commands.get("modifyworld"));
        assertFalse(commands.containsKey("mw"));
        assertFalse(commands.containsKey("modifyworld:mw"));
        verify(manager, never()).disablePlugin(plugin);
    }

    @Test
    // Mockito mock, verify and stubbing results lack null annotations.
    @SuppressWarnings("null")
    void disabledCommandAliasPreservesAnotherPluginsCommand() {
        config.set("command-alias-mw", false);
        var command = mock(org.bukkit.command.PluginCommand.class,
                withSettings().mockMaker(org.mockito.MockMakers.INLINE));
        var foreign = mock(org.bukkit.command.PluginCommand.class,
                withSettings().mockMaker(org.mockito.MockMakers.INLINE));
        var map = mock(org.bukkit.command.CommandMap.class);
        var commands = new java.util.HashMap<String, org.bukkit.command.Command>();
        commands.put("modifyworld", command);
        commands.put("mw", foreign);
        commands.put("modifyworld:mw", command);
        doReturn(command).when(plugin).getCommand("modifyworld");
        when(plugin.getServer().getCommandMap()).thenReturn(map);
        doReturn(commands).when(map).getKnownCommands();

        plugin.onEnable();

        assertSame(foreign, commands.get("mw"));
        assertSame(command, commands.get("modifyworld"));
        assertFalse(commands.containsKey("modifyworld:mw"));
        verify(manager, never()).disablePlugin(plugin);
    }

    @Test
    void enabledLoginPermissionAddsCompatibilityListener() {
        config.set("require-login-permission", true);
        plugin.onEnable();
        verify(manager).registerEvents(isA(LoginListener.class), same(plugin));
        verify(manager, times(6)).registerEvents(any(), same(plugin));
    }

    @Test
    void configurationFailureRegistersNothingAndDisablesPlugin() {
        doThrow(new IllegalStateException("broken yaml")).when(plugin).getConfig();
        plugin.onEnable();
        verify(manager, never()).registerEvents(any(), any());
        verify(manager).disablePlugin(plugin);
        verify(console, never()).sendMessage(Component.text("[Modifyworld] Modifyworld enabled!", NamedTextColor.GREEN));
    }

    @Test
    void constructorFailureRegistersNothing() {
        doThrow(new NoClassDefFoundError("missing API")).when(plugin).createListeners();
        plugin.onEnable();
        verify(manager, never()).registerEvents(any(), any());
        verify(manager).disablePlugin(plugin);
    }

    @Test
    void partialRegistrationIsRolledBack() {
        // Mockito mock() returns an instance, but its generic API lacks null annotations.
        @SuppressWarnings("null")
        ModifyworldListener first = mock(ModifyworldListener.class);
        // Mockito mock() returns an instance, but its generic API lacks null annotations.
        @SuppressWarnings("null")
        ModifyworldListener second = mock(ModifyworldListener.class);
        doReturn(List.of(first, second)).when(plugin).createListeners();
        HandlerList handlers = new HandlerList();
        doAnswer(invocation -> {
            handlers.register(new RegisteredListener(first, (listener, event) -> { },
                    EventPriority.LOW, plugin, false));
            return null;
        }).when(manager).registerEvents(first, plugin);
        doThrow(new IllegalStateException("registration failed")).when(manager).registerEvents(second, plugin);
        plugin.onEnable();
        assertEquals(0, handlers.getRegisteredListeners().length);
        assertTrue(plugin.listeners.isEmpty());
        verify(manager).disablePlugin(plugin);
        verify(console, never()).sendMessage(Component.text("[Modifyworld] Modifyworld enabled!", NamedTextColor.GREEN));
    }

    @Test
    void liveReloadIsRejectedWithoutReplacingActiveConfiguration() {
        plugin.onEnable();
        assertThrows(IllegalStateException.class, plugin::reloadConfig);
        assertSame(config, plugin.config);
    }
}
