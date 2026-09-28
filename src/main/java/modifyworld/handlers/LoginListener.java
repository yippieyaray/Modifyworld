// Added on 2026-09-28. Licensed under GPL-2.0-or-later.
package modifyworld.handlers;

import modifyworld.ModifyworldListener;
import modifyworld.PlayerInformer;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.plugin.Plugin;

/** Optional compatibility listener: pre-login connections have no Bukkit Player permissions. */
@SuppressWarnings("deprecation")
public class LoginListener extends ModifyworldListener {
    public LoginListener(Plugin plugin, ConfigurationSection config, PlayerInformer informer) {
        super(plugin, config, informer);
    }

    // Registered only when require-login-permission is enabled. Preserve pre-entry denial instead of kicking after join.
    @EventHandler(priority = EventPriority.HIGH)
    public void onLogin(PlayerLoginEvent event) {
        if (event.getResult() == PlayerLoginEvent.Result.ALLOWED
                && _permissionDenied(event.getPlayer(), "modifyworld.login")) {
            event.disallow(PlayerLoginEvent.Result.KICK_WHITELIST,
                    LegacyComponentSerializer.legacyAmpersand().deserialize(informer.getMessage("modifyworld.login")));
        }
    }
}
