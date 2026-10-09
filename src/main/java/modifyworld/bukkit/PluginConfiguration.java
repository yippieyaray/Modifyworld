// SPDX-License-Identifier: GPL-2.0-or-later
// Modified on 2026-10-08: validate the command alias setting and preserve non-null migration entries.
// Modified on 2026-10-07: validate diagnostic lines and gate migrations by a fixed release threshold.
// Modified on 2026-09-30: support and validate Brazilian Portuguese, Polish and Turkish.
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
import org.jspecify.annotations.NonNull;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

/** Validates and migrates configuration, retaining a backup before each rewrite. */
final class PluginConfiguration {
    @FunctionalInterface
    private interface MigrationAction {
        void apply(File file, YamlConfiguration defaults, YamlConfiguration config, boolean exists,
                java.util.function.Consumer<String> log)
                throws IOException, InvalidConfigurationException;
    }

    private record MigrationStep(ReleaseVersion target, MigrationAction action) { }

    /*
     * Append independent migration steps here in strictly increasing target order.
     * Never replace an old target with the current plugin release.
     * Example: BETA.6 -> BETA.7 -> BETA.8. An unversioned config runs all steps;
     * a BETA.6 config runs only 7 then 8. Each step completes before the next starts
     * and operates on the previous step's config and language files. A new step must
     * contain only its own changes, never call or include earlier migrations.
     * A failure stops the chain; the last successfully saved target remains on disk.
     */
    private static final java.util.List<@NonNull MigrationStep> MIGRATIONS = java.util.List.of(
            new MigrationStep(ReleaseVersion.parse("2.0.0-BETA.6"), PluginConfiguration::migrateToBeta6));

    private PluginConfiguration() { }

    static YamlConfiguration load(File file, InputStream resource)
            throws IOException, InvalidConfigurationException {
        return load(file, resource, message -> { });
    }

    static YamlConfiguration load(File file, InputStream resource, java.util.function.Consumer<String> log)
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
        String currentVersion = defaults.getString("config-version");
        if (currentVersion == null || currentVersion.isBlank()) {
            throw new InvalidConfigurationException("Bundled config-version must contain the release version");
        }
        ReleaseVersion runningVersion = parseVersion(currentVersion);
        ReleaseVersion storedVersion = null;
        if (candidate.contains("config-version", true)) {
            // Accept the previous numeric migration marker once when upgrading.
            if (candidate.isInt("config-version") && candidate.getInt("config-version") == 1) {
                storedVersion = null;
            } else if (candidate.isString("config-version") && !candidate.getString("config-version").isBlank()) {
                storedVersion = parseVersion(candidate.getString("config-version"));
            } else {
                throw new InvalidConfigurationException("config-version must contain a release version");
            }
        }
        String fromVersion = storedVersion == null ? "unversioned" : storedVersion.toString();
        boolean migrated = false;
        ReleaseVersion previousTarget = null;
        for (MigrationStep step : MIGRATIONS) {
            if (previousTarget != null && previousTarget.compareTo(step.target()) >= 0) {
                throw new IllegalStateException("Migration targets must be strictly increasing");
            }
            previousTarget = step.target();
            if (storedVersion == null || storedVersion.compareTo(step.target()) < 0) {
                step.action().apply(file, defaults, candidate, exists, exists ? log : message -> { });
                // Persist the completed step before starting a later one.
                candidate.set("config-version", step.target().toString());
                if (exists) {
                    YamlConfiguration original = new YamlConfiguration();
                    original.options().pathSeparator('/');
                    original.load(file);
                    if (configurationData(original).equals(configurationData(candidate))) {
                        updateVersion(file, step.target().toString());
                    } else {
                        saveConfiguration(file, candidate, true);
                    }
                }
                storedVersion = step.target();
                migrated = exists;
            }
        }
        materializeDefaults(candidate, defaults);
        validate(candidate);
        if (!exists) {
            candidate.set("config-version", currentVersion);
            saveConfiguration(file, candidate, false);
        }
        YamlConfiguration messages = LanguageFiles.load(file.getAbsoluteFile().getParentFile(),
                candidate.getString("language"));
        for (var entry : candidate.getConfigurationSection("messages").getValues(false).entrySet()) {
            messages.set(entry.getKey(), entry.getValue());
        }
        candidate.createSection("messages", messages.getValues(false));
        validate(candidate);
        if (exists && storedVersion != null && storedVersion.compareTo(runningVersion) < 0) {
            updateVersion(file, currentVersion);
            candidate.set("config-version", currentVersion);
        }
        if (migrated) {
            log.accept("Configuration migration " + fromVersion + " -> "
                    + candidate.getString("config-version") + " completed.");
        }
        return candidate;
    }

    private static java.util.Map<String, Object> configurationData(YamlConfiguration config) {
        var data = new java.util.LinkedHashMap<>(config.getValues(true));
        data.keySet().removeIf(key -> key.equals("config-version") || config.isConfigurationSection(key));
        return data;
    }

    /** Update only the release marker, preserving ordinary YAML formatting and comments. */
    private static void updateVersion(File file, String version) throws IOException, InvalidConfigurationException {
        var path = file.toPath();
        String original = Files.readString(path, StandardCharsets.UTF_8);
        var pattern = java.util.regex.Pattern.compile(
                "(?m)^((?:config-version|\"config-version\"|'config-version')\\s*:[ \\t]*)"
                + "(?:'[^'\\r\\n]*'|\"[^\"\\r\\n]*\"|[^#\\r\\n]*?)([ \\t]*(?:#[^\\r\\n]*)?)$");
        var match = pattern.matcher(original);
        String updated;
        if (match.find()) {
            updated = match.replaceFirst(java.util.regex.Matcher.quoteReplacement(
                    match.group(1) + "'" + version + "'" + match.group(2)));
        } else {
            // Unusual YAML representations (e.g. a multiline scalar) still update safely.
            YamlConfiguration config = new YamlConfiguration();
            config.options().pathSeparator('/');
            config.load(file);
            if (!config.contains("config-version", true)) {
                updated = "config-version: '" + version + "'\n" + original;
            } else {
                config.set("config-version", version);
                updated = config.saveToString();
            }
        }
        YamlConfiguration verified = new YamlConfiguration();
        verified.loadFromString(updated);
        if (!version.equals(verified.getString("config-version"))) {
            throw new InvalidConfigurationException("Cannot update config-version");
        }
        var temporary = Files.createTempFile(path.toAbsolutePath().getParent(), "config-", ".tmp");
        try {
            Files.writeString(temporary, updated, StandardCharsets.UTF_8);
            Files.move(temporary, path, java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static void migrateToBeta6(File file, YamlConfiguration defaults,
            YamlConfiguration candidate, boolean exists, java.util.function.Consumer<String> log)
            throws IOException, InvalidConfigurationException {
        log.accept("Migrating configuration settings");
        // Resolve explicit legacy settings before adding defaults for the new key.
        if (candidate.contains("whitelist", true)) {
            if (!candidate.isBoolean("whitelist")) {
                throw new InvalidConfigurationException("Legacy whitelist must be a boolean");
            }
            if (!candidate.contains("require-login-permission", true)) {
                candidate.set("require-login-permission", candidate.getBoolean("whitelist"));
            }
        }
        materializeDefaults(candidate, defaults);
        validate(candidate);
        var legacy = candidate.getConfigurationSection("messages").getValues(false);
        boolean migrateMessages = exists && !legacy.isEmpty();
        log.accept("Migrating language files");
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
                // An otherwise matching pre-upgrade own file may lack the new diagnostic keys.
                for (String key : messages.getKeys(false)) {
                    if (key.startsWith("check.") && !existing.contains(key, true)) {
                        existing.set(key, messages.get(key));
                    }
                }
                if (!existing.getValues(false).equals(messages.getValues(false))) {
                    throw new IOException("Cannot migrate messages: lang/own.yml already exists. "
                            + "Move or merge it manually; config.yml was not changed.");
                }
            }
        }
        // Freeze the languages affected by this step. Later languages belong to later steps.
        LanguageFiles.migrateChecks(file.getAbsoluteFile().getParentFile(), migrateMessages ? messages : null,
                java.util.List.of("en", "de", "es", "fr", "pt_br", "pl", "tr"));
        if (migrateMessages) {
            // Only legacy custom messages justify creating own.yml; diagnostic migration never does.
            log.accept("Migrating legacy messages to lang/own.yml");
            var own = file.toPath().toAbsolutePath().getParent().resolve("lang/own.yml");
            if (!Files.exists(own)) Files.writeString(own, messages.saveToString(),
                    StandardCharsets.UTF_8, java.nio.file.StandardOpenOption.CREATE_NEW);
            candidate.set("language", "own");
        }
        candidate.set("whitelist", null);
        candidate.set("use-material-names", null);
        candidate.set("check-metadata", null);
        candidate.createSection("messages");
    }

    private static void materializeDefaults(YamlConfiguration candidate, YamlConfiguration defaults) {
        candidate.setDefaults(defaults);
        candidate.options().copyDefaults(true);
        // Explicit-fallback Bukkit getters ignore configured defaults.
        for (String key : defaults.getKeys(false)) {
            if (!defaults.isConfigurationSection(key) && !candidate.contains(key, true)) {
                candidate.set(key, defaults.get(key));
            }
        }
    }

    private static void saveConfiguration(File file, YamlConfiguration candidate, boolean exists) throws IOException {
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
        var temporary = Files.createTempFile(path.getParent(), "config-", ".tmp");
        try {
            Files.writeString(temporary, candidate.saveToString(), StandardCharsets.UTF_8);
            Files.move(temporary, path, java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static ReleaseVersion parseVersion(String text) throws InvalidConfigurationException {
        try {
            return ReleaseVersion.parse(text);
        } catch (IllegalArgumentException failure) {
            throw new InvalidConfigurationException("Invalid config-version: " + text, failure);
        }
    }

    static void validate(YamlConfiguration config) throws InvalidConfigurationException {
        if (config.contains("check-for-updates", true) && !config.isBoolean("check-for-updates")) {
            throw new InvalidConfigurationException("check-for-updates must be a boolean");
        }
        if (config.contains("command-alias-mw", true) && !config.isBoolean("command-alias-mw")) {
            throw new InvalidConfigurationException("command-alias-mw must be a boolean");
        }
        for (String key : new String[] {"item-use-check",
                "op-bypass", "inform-players", "item-restrictions", "drop-restricted-item", "require-login-permission"}) {
            if (!config.isBoolean(key)) throw new InvalidConfigurationException(key + " must be a boolean");
        }
        if (!config.isString("language") || !(LanguageFiles.LANGUAGES.contains(config.getString("language")) || "own".equals(config.getString("language")))) {
            throw new InvalidConfigurationException("language must be " + String.join(", ", LanguageFiles.LANGUAGES) + " or own");
        }
        ConfigurationSection messages = config.getConfigurationSection("messages");
        if (messages == null) throw new InvalidConfigurationException("messages must be a mapping");
        for (String key : messages.getKeys(false)) {
            if (key.equals("individual-messages") && messages.isBoolean(key)) continue;
            CheckMessages.validate(key, messages.get(key));
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
