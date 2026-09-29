// SPDX-License-Identifier: GPL-2.0-or-later
// Modified or added for the Paper port on 2026-09-28 and 2026-09-29; see NOTICE.
// Added on 2026-09-28. Licensed under GPL-2.0-or-later.
package modifyworld.bukkit;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Locale;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

/** Validates and migrates configuration, retaining a backup before each rewrite. */
final class PluginConfiguration {
    private PluginConfiguration() { }

    static YamlConfiguration load(File file, InputStream resource)
            throws IOException, InvalidConfigurationException {
        if (resource == null) throw new IOException("Bundled config.yml is missing");
        YamlConfiguration defaults = new YamlConfiguration();
        defaults.options().pathSeparator('/');
        try (InputStreamReader reader = new InputStreamReader(resource, StandardCharsets.UTF_8)) {
            defaults.load(reader);
        }
        validate(defaults);
        YamlConfiguration candidate = new YamlConfiguration();
        candidate.options().pathSeparator('/');
        boolean exists = Files.exists(file.toPath());
        if (exists) candidate.load(file);
        boolean migrate = exists && (candidate.contains("whitelist", true)
                || candidate.contains("use-material-names", true) || candidate.contains("check-metadata", true)
                || defaults.getKeys(false).stream().anyMatch(key -> !candidate.contains(key, true)));
        // Resolve explicit legacy settings before adding defaults for the new key.
        if (candidate.contains("whitelist", true)) {
            if (!candidate.isBoolean("whitelist")) {
                throw new InvalidConfigurationException("Legacy whitelist must be a boolean");
            }
            if (!candidate.contains("require-login-permission", true)) {
                candidate.set("require-login-permission", candidate.getBoolean("whitelist"));
            }
        }
        candidate.setDefaults(defaults);
        candidate.options().copyDefaults(true);
        // Explicit-fallback getters in Bukkit ignore configured defaults. Materialize
        // scalar defaults in memory so every consumer sees the same effective values.
        for (String key : defaults.getKeys(false)) {
            if (!defaults.isConfigurationSection(key) && !candidate.contains(key, true)) {
                candidate.set(key, defaults.get(key));
            }
        }
        validate(candidate);
        var legacy = candidate.getConfigurationSection("messages").getValues(false);
        boolean migrateMessages = exists && !legacy.isEmpty();
        YamlConfiguration messages = LanguageFiles.load(file.getAbsoluteFile().getParentFile(),
                candidate.getString("language"));
        for (var entry : legacy.entrySet()) messages.set(entry.getKey(), entry.getValue());
        // Validate effective messages before touching the original configuration.
        YamlConfiguration effective = new YamlConfiguration();
        effective.options().pathSeparator('/');
        effective.loadFromString(candidate.saveToString());
        effective.createSection("messages", messages.getValues(false));
        validate(effective);
        if (migrateMessages) {
            var own = file.toPath().toAbsolutePath().getParent().resolve("lang/own.yml");
            if (Files.exists(own)) {
                YamlConfiguration existing = new YamlConfiguration();
                existing.options().pathSeparator('/');
                existing.load(own.toFile());
                if (!existing.getValues(false).equals(messages.getValues(false))) {
                    throw new IOException("Cannot migrate messages: lang/own.yml already exists. "
                            + "Move or merge it manually; config.yml was not changed.");
                }
            }
        }
        if (!exists || migrate || migrateMessages) {
            var path = file.toPath().toAbsolutePath();
            Files.createDirectories(path.getParent());
            if (exists) {
                int suffix = 0;
                java.nio.file.Path backup;
                do {
                    backup = path.resolveSibling("config.yml.bak" + (suffix == 0 ? "" : "." + suffix));
                    suffix++;
                } while (Files.exists(backup));
                Files.copy(path, backup);
            }
            if (migrateMessages) {
                var own = path.getParent().resolve("lang/own.yml");
                if (!Files.exists(own)) Files.writeString(own, messages.saveToString(),
                        StandardCharsets.UTF_8, java.nio.file.StandardOpenOption.CREATE_NEW);
                candidate.set("language", "own");
            }
            candidate.set("whitelist", null);
            candidate.set("use-material-names", null);
            candidate.set("check-metadata", null);
            candidate.createSection("messages");
            // Keep an empty override section for compatibility, never persist resolved messages.
            var temporary = Files.createTempFile(path.getParent(), "config-", ".tmp");
            try {
                Files.writeString(temporary, candidate.saveToString(), StandardCharsets.UTF_8);
                Files.move(temporary, path, java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            } finally {
                Files.deleteIfExists(temporary);
            }
        }
        candidate.createSection("messages", messages.getValues(false));
        validate(candidate);
        return candidate;
    }

    static void validate(YamlConfiguration config) throws InvalidConfigurationException {
        for (String key : new String[] {"item-use-check",
                "op-bypass", "inform-players", "item-restrictions", "drop-restricted-item", "require-login-permission"}) {
            if (!config.isBoolean(key)) throw new InvalidConfigurationException(key + " must be a boolean");
        }
        if (!config.isString("language") || !(LanguageFiles.LANGUAGES.contains(config.getString("language")) || "own".equals(config.getString("language")))) {
            throw new InvalidConfigurationException("language must be en, de, es, fr or own");
        }
        ConfigurationSection messages = config.getConfigurationSection("messages");
        if (messages == null) throw new InvalidConfigurationException("messages must be a mapping");
        for (String key : messages.getKeys(false)) {
            if (key.equals("individual-messages") && messages.isBoolean(key)) continue;
            if (!messages.isString(key)) {
                throw new InvalidConfigurationException("messages/" + key + " must be a string");
            }
        }
        try {
            String.format(Locale.ROOT, messages.getString("message-format", "%s"), "message");
        } catch (IllegalArgumentException failure) {
            throw new InvalidConfigurationException("Invalid messages/message-format", failure);
        }
    }
}
