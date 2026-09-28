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
}
