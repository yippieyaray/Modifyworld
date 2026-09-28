package modifyworld.bukkit;

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
    private YamlConfiguration config;

    @BeforeEach
    void setup() {
        plugin = mock(Modifyworld.class, withSettings().mockMaker(org.mockito.MockMakers.INLINE)
                .defaultAnswer(CALLS_REAL_METHODS));
        manager = mock(PluginManager.class);
        Server server = mock(Server.class);
        logger = mock(Logger.class);
        doReturn(server).when(plugin).getServer();
        doReturn(logger).when(plugin).getLogger();
        when(server.getPluginManager()).thenReturn(manager);
        config = new YamlConfiguration();
        config.options().pathSeparator('/');
        config.createSection("messages");
        doReturn(config).when(plugin).getConfig();
    }

    @Test
    void successfulStartupRegistersEachListenerOnceAndLoginPermissionOnlyWhenEnabled() {
        plugin.onEnable();
        verify(manager, times(5)).registerEvents(any(), same(plugin));
        verify(manager, never()).registerEvents(isA(LoginListener.class), same(plugin));
        verify(logger).info("Modifyworld enabled!");
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
        verify(logger, never()).info("Modifyworld enabled!");
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
        ModifyworldListener first = mock(ModifyworldListener.class);
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
        verify(logger, never()).info("Modifyworld enabled!");
    }

    @Test
    void liveReloadIsRejectedWithoutReplacingActiveConfiguration() {
        plugin.onEnable();
        assertThrows(IllegalStateException.class, plugin::reloadConfig);
        assertSame(config, plugin.config);
    }
}
