// SPDX-License-Identifier: GPL-2.0-or-later
// Modified on 2026-10-08: add configurable command alias and startup output; clarify nullness.
// Modified on 2026-10-07: log migration context and successful completion.
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
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import org.bukkit.configuration.file.YamlConfiguration;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.NonNull;
import java.util.logging.Level;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
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
    private UpdateChecker updateChecker;
    private org.bukkit.scheduler.BukkitTask updateTask;

    @Override
    public boolean onCommand(@NonNull CommandSender sender, @NonNull Command command,
            @NonNull String label, String @NonNull [] args) {
        return new PermissionCheckCommand().execute(sender, args, getConfig());
    }

    @Override
    public void onEnable() {
        try {
            var console = getServer().getConsoleSender();
            console.sendMessage(Component.empty());
            console.sendMessage(Component.text("  |\\/| \\  /\\  /  ", NamedTextColor.YELLOW)
                    .append(Component.text("Modifyworld", NamedTextColor.GREEN))
                    .append(Component.text(" v" + getPluginMeta().getVersion(), NamedTextColor.YELLOW)));
            console.sendMessage(Component.text("  |  |  \\/  \\/   ", NamedTextColor.YELLOW)
                    .append(Component.text(testedPaperText(), NamedTextColor.GRAY)));
            console.sendMessage(Component.empty());
            config = getConfig();
            informer = new PlayerInformer(config);
            // Construct every listener before registering any of them.
            listeners = createListeners();
            for (ModifyworldListener listener : listeners) {
                getServer().getPluginManager().registerEvents(listener, this);
            }
            configureCommandAlias();
            getServer().getConsoleSender().sendMessage(
                    Component.text("[Modifyworld] Modifyworld enabled!", NamedTextColor.GREEN));
        } catch (RuntimeException | LinkageError failure) {
            clearListeners();
            getLogger().log(Level.SEVERE,
                    "Modifyworld startup failed. Protection is NOT active. Fix the error and restart the server.",
                    failure);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        startUpdateCheck();
    }

    protected UpdateChecker createUpdateChecker() {
        java.util.OptionalInt build;
        try {
            build = io.papermc.paper.ServerBuildInfo.buildInfo().buildNumber();
        } catch (java.util.NoSuchElementException | IllegalStateException unavailable) {
            build = java.util.OptionalInt.empty();
        }
        return new UpdateChecker(getPluginMeta().getVersion(), getServer().getMinecraftVersion(),
                build, getLogger()::info);
    }

    private void startUpdateCheck() {
        if (!config.getBoolean("check-for-updates", true)) return;
        try {
            updateChecker = createUpdateChecker();
            updateTask = getServer().getScheduler().runTaskAsynchronously(this, updateChecker);
        } catch (RuntimeException failure) {
            if (updateChecker != null) updateChecker.close();
            getLogger().info("Update check could not be scheduled; Modifyworld remains enabled.");
        }
    }

    private @NonNull String testedPaperText() {
        try (var resource = getResource("plugin.yml")) {
            if (resource == null) return "Paper test versions not recorded";
            var metadata = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(resource, StandardCharsets.UTF_8));
            var versions = metadata.getStringList("tested-paper-versions");
            return versions.isEmpty() ? "Paper test versions not recorded"
                    : "Tested on Paper " + String.join(", ", versions);
        } catch (IOException failure) {
            getLogger().warning("Could not read tested Paper versions: " + failure.getMessage());
            return "Paper test versions not recorded";
        }
    }

    private void configureCommandAlias() {
        if (config.getBoolean("command-alias-mw", true)) return;
        var command = Objects.requireNonNull(getCommand("modifyworld"));
        var commands = getServer().getCommandMap().getKnownCommands();
        // Remove only our alias mappings; preserve commands owned by other plugins.
        commands.remove("mw", command);
        commands.remove("modifyworld:mw", command);
        command.setAliases(List.of());
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
        if (updateChecker != null) updateChecker.close();
        if (updateTask != null) updateTask.cancel();
        clearListeners();
        config = null;
        getLogger().info("Modifyworld disabled.");
    }

    @Override
    public @NonNull FileConfiguration getConfig() {
        if (config == null) reloadConfig();
        return Objects.requireNonNull(config);
    }

    @Override
    public void reloadConfig() {
        if (listeners != null && !listeners.isEmpty()) {
            throw new IllegalStateException("Live configuration reload is unsupported; restart the server.");
        }
        try {
            config = PluginConfiguration.load(new File(getDataFolder(), "config.yml"),
                    getResource("config.yml"), getLogger()::info);
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
