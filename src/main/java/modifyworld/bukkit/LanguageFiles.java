// SPDX-License-Identifier: GPL-2.0-or-later
// Modified on 2026-10-07: append missing diagnostic keys with backups and validation.
// Modified on 2026-09-30: support and validate Brazilian Portuguese, Polish and Turkish.
// Modified or added for the Paper port on 2026-09-28 and 2026-09-29; see NOTICE.
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

/** Installs editable languages and appends missing diagnostic keys without replacing existing text. */
final class LanguageFiles {
    static final java.util.List<String> LANGUAGES = java.util.List.of("en", "de", "es", "fr", "pt_br", "pl", "tr");

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

    static YamlConfiguration bundled(String language)
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

    /** Prepare all additions before rewriting any existing language file. */
    static void migrateChecks(File directory) throws IOException, InvalidConfigurationException {
        migrateChecks(directory, null);
    }

    static void migrateChecks(File directory, YamlConfiguration ownDefaults)
            throws IOException, InvalidConfigurationException {
        migrateChecks(directory, ownDefaults, LANGUAGES);
    }

    /** Missing standard files are installed completely; own is upgraded only if present. */
    static void migrateChecks(File directory, YamlConfiguration ownDefaults, java.util.List<String> languages)
            throws IOException, InvalidConfigurationException {
        Path folder = directory.toPath().resolve("lang");
        var upgrades = new java.util.LinkedHashMap<Path, String>();
        var installations = new java.util.LinkedHashMap<Path, String>();
        var codes = new java.util.ArrayList<>(languages);
        if (Files.exists(folder.resolve("own.yml"))) codes.add("own");
        for (String code : codes) {
            Path path = folder.resolve(code + ".yml");
            if (!Files.exists(path)) {
                // Newly introduced or deleted standard language: copy the complete resource.
                bundled(code); // Validate YAML before writing any language file.
                try (var stream = LanguageFiles.class.getResourceAsStream("/lang/" + code + ".yml")) {
                    if (stream == null) throw new IOException("Missing bundled language: " + code);
                    installations.put(path, new String(stream.readAllBytes(), StandardCharsets.UTF_8));
                }
                continue;
            }
            YamlConfiguration existing = new YamlConfiguration();
            existing.options().pathSeparator('/');
            existing.load(path.toFile());
            YamlConfiguration defaults = code.equals("own") && ownDefaults != null
                    ? ownDefaults : bundled(code.equals("own") ? "en" : code);
            StringBuilder additions = new StringBuilder();
            for (String key : defaults.getKeys(false)) {
                if (!key.startsWith("check.")) continue;
                if (existing.contains(key, true)) {
                    CheckMessages.validate(key, existing.get(key));
                } else {
                    String text = defaults.getString(key);
                    CheckMessages.validate(key, text);
                    additions.append(key).append(": '").append(text.replace("'", "''")).append("'\n");
                }
            }
            if (!additions.isEmpty()) {
                String original = Files.readString(path, StandardCharsets.UTF_8);
                String upgraded = original + (original.endsWith("\n") || original.isEmpty() ? "" : "\n")
                        + "\n# Added diagnostic command messages; existing values are preserved.\n" + additions;
                YamlConfiguration verified = new YamlConfiguration();
                verified.options().pathSeparator('/');
                verified.loadFromString(upgraded);
                for (String key : existing.getKeys(false)) {
                    if (!java.util.Objects.equals(existing.get(key), verified.get(key))) {
                        throw new InvalidConfigurationException("Cannot preserve language key: " + key);
                    }
                }
                for (String key : defaults.getKeys(false)) {
                    if (key.startsWith("check.")) CheckMessages.validate(key, verified.get(key));
                }
                upgrades.put(path, upgraded);
            }
        }
        Files.createDirectories(folder);
        for (var installation : installations.entrySet()) {
            Files.writeString(installation.getKey(), installation.getValue(), StandardCharsets.UTF_8,
                    java.nio.file.StandardOpenOption.CREATE_NEW);
        }
        for (var upgrade : upgrades.entrySet()) {
            Path path = upgrade.getKey();
            int suffix = 0;
            Path backup;
            do {
                backup = path.resolveSibling(path.getFileName() + ".bak" + (suffix == 0 ? "" : "." + suffix));
                suffix++;
            } while (Files.exists(backup));
            Files.copy(path, backup);
            Path temporary = Files.createTempFile(folder, "language-", ".tmp");
            try {
                Files.writeString(temporary, upgrade.getValue(), StandardCharsets.UTF_8);
                Files.move(temporary, path, java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            } finally {
                Files.deleteIfExists(temporary);
            }
        }
    }

    private static void overlay(YamlConfiguration target, YamlConfiguration source)
            throws InvalidConfigurationException {
        for (String key : source.getKeys(false)) {
            if (key.equals("individual-messages") && source.isBoolean(key)) {
                target.set(key, source.getBoolean(key));
                continue;
            }
            CheckMessages.validate(key, source.get(key));
            if (!source.isString(key)) throw new InvalidConfigurationException("Language message must be text: " + key);
            target.set(key, source.getString(key));
        }
    }
}
