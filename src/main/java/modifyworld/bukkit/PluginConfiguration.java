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

/** Loads configuration without overwriting invalid or existing user files. */
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
        validate(candidate);
        if (!exists) {
            Files.createDirectories(file.toPath().toAbsolutePath().getParent());
            candidate.save(file);
        }
        YamlConfiguration messages = LanguageFiles.load(file.getAbsoluteFile().getParentFile(),
                candidate.getString("language"));
        for (var entry : candidate.getConfigurationSection("messages").getValues(false).entrySet()) {
            messages.set(entry.getKey(), entry.getValue());
        }
        candidate.createSection("messages", messages.getValues(false));
        validate(candidate);
        return candidate;
    }

    static void validate(YamlConfiguration config) throws InvalidConfigurationException {
        for (String key : new String[] {"use-material-names", "check-metadata", "item-use-check",
                "op-bypass", "inform-players", "item-restrictions", "drop-restricted-item", "require-login-permission"}) {
            if (!config.isBoolean(key)) throw new InvalidConfigurationException(key + " must be a boolean");
        }
        if (!config.getBoolean("use-material-names") || config.getBoolean("check-metadata")) {
            throw new InvalidConfigurationException("Migrate numeric/metadata permissions first: "
                    + "use-material-names must be true and check-metadata must be false");
        }
        if (!config.isString("language") || !java.util.Set.of("en", "de").contains(config.getString("language"))) {
            throw new InvalidConfigurationException("language must be en or de");
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
