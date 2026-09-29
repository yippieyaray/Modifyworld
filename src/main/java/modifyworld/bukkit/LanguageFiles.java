// Licensed under GPL-2.0-or-later.
package modifyworld.bukkit;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

/** Installs editable language files without replacing existing files. */
final class LanguageFiles {
    static final java.util.List<String> LANGUAGES = java.util.List.of("en", "de", "es", "fr");

    private LanguageFiles() { }

    static YamlConfiguration load(File directory, String language)
            throws IOException, InvalidConfigurationException {
        YamlConfiguration result = bundled("en");
        if (!language.equals("own")) overlay(result, bundled(language));
        Path folder = directory.toPath().resolve("lang");
        Files.createDirectories(folder);
        for (String code : LANGUAGES) {
            Path target = folder.resolve(code + ".yml");
            if (!Files.exists(target)) {
                try (var stream = LanguageFiles.class.getResourceAsStream("/lang/" + code + ".yml")) {
                    if (stream == null) throw new IOException("Missing bundled language: " + code);
                    Files.copy(stream, target);
                }
            }
        }
        YamlConfiguration custom = new YamlConfiguration();
        custom.options().pathSeparator('/');
        custom.load(folder.resolve(language + ".yml").toFile());
        overlay(result, custom);
        return result;
    }

    private static YamlConfiguration bundled(String language)
            throws IOException, InvalidConfigurationException {
        YamlConfiguration config = new YamlConfiguration();
        config.options().pathSeparator('/');
        var stream = LanguageFiles.class.getResourceAsStream("/lang/" + language + ".yml");
        if (stream == null) throw new IOException("Missing bundled language: " + language);
        try (var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            config.load(reader);
        }
        return config;
    }

    private static void overlay(YamlConfiguration target, YamlConfiguration source)
            throws InvalidConfigurationException {
        for (String key : source.getKeys(false)) {
            if (key.equals("individual-messages") && source.isBoolean(key)) {
                target.set(key, source.getBoolean(key));
                continue;
            }
            if (!source.isString(key)) throw new InvalidConfigurationException("Language message must be text: " + key);
            target.set(key, source.getString(key));
        }
    }
}
