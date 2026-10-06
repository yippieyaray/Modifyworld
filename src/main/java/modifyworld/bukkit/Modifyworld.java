// SPDX-License-Identifier: GPL-2.0-or-later
// Modified on 2026-10-05: expose the permission diagnostic command.
// Modified on 2026-09-30: clarify comments and current Paper plugin description.
// Modified or added for the Paper port on 2026-09-28 and 2026-09-29; see NOTICE.
// Modified on 2026-09-28: validate configuration and make listener startup transactional.
/*
 * Modifyworld - Permission rules for Paper
 * Copyright (C) 2011 t3hk0d3 http://www.tehkode.ru
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package modifyworld.bukkit;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.logging.Level;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.java.JavaPlugin;
import modifyworld.ModifyworldListener;
import modifyworld.PlayerInformer;
import modifyworld.handlers.*;

public class Modifyworld extends JavaPlugin {
    protected List<ModifyworldListener> listeners = List.of();
    protected PlayerInformer informer;
    protected FileConfiguration config;

    @Override
    public boolean onCommand(org.bukkit.command.CommandSender sender, org.bukkit.command.Command command,
            String label, String[] args) {
        return new PermissionCheckCommand().execute(sender, args, getConfig());
    }

    @Override
    public void onEnable() {
        try {
            config = getConfig();
            informer = new PlayerInformer(config);
            // Construct every listener before registering any of them.
            listeners = createListeners();
            for (ModifyworldListener listener : listeners) {
                getServer().getPluginManager().registerEvents(listener, this);
            }
            getServer().getConsoleSender().sendMessage(
                    Component.text("[Modifyworld] Modifyworld enabled!", NamedTextColor.GREEN));
        } catch (RuntimeException | LinkageError failure) {
            clearListeners();
            getLogger().log(Level.SEVERE,
                    "Modifyworld startup failed. Protection is NOT active. Fix the error and restart the server.",
                    failure);
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    protected List<ModifyworldListener> createListeners() {
        java.util.ArrayList<ModifyworldListener> result = new java.util.ArrayList<>();
        result.add(new PlayerListener(this, config, informer));
        result.add(new ContainerListener(this, config, informer));
        result.add(new EntityListener(this, config, informer));
        result.add(new BlockListener(this, config, informer));
        result.add(new VehicleListener(this, config, informer));
        if (config.getBoolean("require-login-permission")) {
            result.add(new LoginListener(this, config, informer));
        }
        return List.copyOf(result);
    }

    protected void clearListeners() {
        HandlerList.unregisterAll(this);
        listeners = List.of();
        informer = null;
    }

    @Override
    public void onDisable() {
        clearListeners();
        config = null;
        getLogger().info("Modifyworld disabled.");
    }

    @Override
    public FileConfiguration getConfig() {
        if (config == null) reloadConfig();
        return config;
    }

    @Override
    public void reloadConfig() {
        if (listeners != null && !listeners.isEmpty()) {
            throw new IllegalStateException("Live configuration reload is unsupported; restart the server.");
        }
        try {
            config = PluginConfiguration.load(new File(getDataFolder(), "config.yml"),
                    getResource("config.yml"));
            String language = config.getString("language");
            getLogger().info("Language " + language + " (lang/" + language + ".yml loaded)");
        } catch (IOException | InvalidConfigurationException failure) {
            throw new IllegalStateException("Cannot load Modifyworld config.yml", failure);
        }
    }

    @Override
    public void saveConfig() {
        if (config == null) throw new IllegalStateException("No valid configuration to save");
        try {
            config.save(new File(getDataFolder(), "config.yml"));
        } catch (IOException failure) {
            throw new IllegalStateException("Cannot save Modifyworld config.yml", failure);
        }
    }
}
