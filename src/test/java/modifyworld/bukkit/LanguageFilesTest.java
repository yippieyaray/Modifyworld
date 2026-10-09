// SPDX-License-Identifier: GPL-2.0-or-later
// Modified on 2026-10-08: align beta validation and nullness handling.
// Added on 2026-10-07: cover diagnostic key migration, backups and fallback.
package modifyworld.bukkit;

import java.nio.file.Files;
import java.nio.file.Path;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

class LanguageFilesTest {
    @TempDir Path directory;

    private YamlConfiguration load(String language) throws Exception {
        Path config = directory.resolve("config.yml");
        Files.writeString(config, "language: " + language + "\n");
        return PluginConfiguration.load(config.toFile(), getClass().getResourceAsStream("/config.yml"));
    }

    @Test
    void migrationLogsContextAndOneOverallCompletionButNotRepeatedStartup() throws Exception {
        Path file = directory.resolve("config.yml");
        Files.writeString(file, "language: en\nmessages:\n  modifyworld.chat: Custom\n");
        var log = new java.util.ArrayList<String>();
        PluginConfiguration.load(file.toFile(), getClass().getResourceAsStream("/config.yml"), log::add);
        assertEquals(java.util.List.of("Migrating configuration settings", "Migrating language files",
                "Migrating legacy messages to lang/own.yml",
                "Configuration migration unversioned -> 2.0.0-BETA.7 completed."), log);
        log.clear();
        PluginConfiguration.load(file.toFile(), getClass().getResourceAsStream("/config.yml"), log::add);
        assertTrue(log.isEmpty());
    }

    @Test
    void failedMigrationDoesNotLogCompletion() throws Exception {
        Path folder = Files.createDirectories(directory.resolve("lang"));
        Files.writeString(folder.resolve("tr.yml"), "check.note: 42\n");
        Path file = directory.resolve("config.yml");
        Files.writeString(file, "language: en\n");
        var log = new java.util.ArrayList<String>();
        assertThrows(InvalidConfigurationException.class, () -> PluginConfiguration.load(file.toFile(),
                getClass().getResourceAsStream("/config.yml"), log::add));
        assertTrue(log.contains("Migrating language files"));
        assertFalse(log.stream().anyMatch(line -> line.endsWith("completed.")));
    }

    @Test
    void freshInstallAndVersionOnlyUpdateDoNotLogMigrations() throws Exception {
        Path file = directory.resolve("config.yml");
        var log = new java.util.ArrayList<String>();
        PluginConfiguration.load(file.toFile(), getClass().getResourceAsStream("/config.yml"), log::add);
        assertTrue(log.isEmpty());
        String defaults;
        try (var stream = getClass().getResourceAsStream("/config.yml")) {
            defaults = new String(stream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
                    .replace("2.0.0-BETA.7", "2.0.0-BETA.8");
        }
        PluginConfiguration.load(file.toFile(), new java.io.ByteArrayInputStream(
                defaults.getBytes(java.nio.charset.StandardCharsets.UTF_8)), log::add);
        assertTrue(log.isEmpty());
    }

    @Test
    void migrationInstallsMissingStandardLanguagesCompletelyWithoutCreatingOwn() throws Exception {
        LanguageFiles.migrateChecks(directory.toFile());
        for (String code : LanguageFiles.LANGUAGES) {
            Path file = directory.resolve("lang/" + code + ".yml");
            try (var stream = getClass().getResourceAsStream("/lang/" + code + ".yml")) {
                assertArrayEquals(stream.readAllBytes(), Files.readAllBytes(file));
            }
            assertFalse(Files.exists(file.resolveSibling(code + ".yml.bak")));
        }
        assertFalse(Files.exists(directory.resolve("lang/own.yml")));
    }

    @Test
    void migrationOnlyTouchesItsDeclaredLanguages() throws Exception {
        Path folder = Files.createDirectories(directory.resolve("lang"));
        String original = "check.title: Custom\n";
        Files.writeString(folder.resolve("de.yml"), original);
        LanguageFiles.migrateChecks(directory.toFile(), null, java.util.List.of("en"));
        assertTrue(Files.exists(folder.resolve("en.yml")));
        assertEquals(original, Files.readString(folder.resolve("de.yml")));
        assertFalse(Files.exists(folder.resolve("de.yml.bak")));
        assertFalse(Files.exists(folder.resolve("fr.yml")));
        assertFalse(Files.exists(folder.resolve("own.yml")));
    }

    @Test
    void configurationMigrationWithoutLegacyMessagesDoesNotCreateOwn() throws Exception {
        load("en");
        assertFalse(Files.exists(directory.resolve("lang/own.yml")));
    }

    @Test
    void completedMigrationDoesNotRestoreDeletedKeysInAnyLanguage() throws Exception {
        Path folder = Files.createDirectories(directory.resolve("lang"));
        Files.writeString(folder.resolve("own.yml"), "check.title: Custom\n");
        var config = load("own");
        String release = config.getString("config-version");
        assertEquals(YamlConfiguration.loadConfiguration(new java.io.InputStreamReader(
                getClass().getResourceAsStream("/plugin.yml"), java.nio.charset.StandardCharsets.UTF_8))
                .getString("version"), release);
        Path configFile = directory.resolve("config.yml");
        String migratedConfig = Files.readString(configFile);
        assertEquals(release, YamlConfiguration.loadConfiguration(configFile.toFile()).getString("config-version"));
        String edited = "# Intentionally sparse\ncheck.title: Custom\n";
        Files.writeString(folder.resolve("own.yml"), edited);
        Files.writeString(folder.resolve("de.yml"), edited);
        config = PluginConfiguration.load(configFile.toFile(), getClass().getResourceAsStream("/config.yml"));
        assertEquals(LanguageFiles.bundled("en").getString("check.note"), new CheckMessages(config).text("note"));
        assertEquals(edited, Files.readString(folder.resolve("own.yml")));
        assertEquals(edited, Files.readString(folder.resolve("de.yml")));
        assertEquals(migratedConfig, Files.readString(configFile));
        assertFalse(Files.exists(folder.resolve("own.yml.bak.1")));
        assertFalse(Files.exists(folder.resolve("de.yml.bak")));
        assertFalse(Files.exists(directory.resolve("config.yml.bak.1")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"-1", "2", "1.5", "''", "true", "[]", "'2.0'", "'2.0.0-BETA'", "'2.0.0-RC.1'"})
    void invalidMigrationVersionPreservesConfigAndLanguage(String version) throws Exception {
        Path folder = Files.createDirectories(directory.resolve("lang"));
        String originalOwn = "check.title: Custom\n";
        Files.writeString(folder.resolve("own.yml"), originalOwn);
        Path configFile = directory.resolve("config.yml");
        String originalConfig = "config-version: " + version + "\nlanguage: own\n";
        Files.writeString(configFile, originalConfig);
        assertThrows(InvalidConfigurationException.class,
                () -> PluginConfiguration.load(configFile.toFile(), getClass().getResourceAsStream("/config.yml")));
        assertEquals(originalConfig, Files.readString(configFile));
        assertEquals(originalOwn, Files.readString(folder.resolve("own.yml")));
        assertFalse(Files.exists(directory.resolve("config.yml.bak")));
        assertFalse(Files.exists(folder.resolve("own.yml.bak")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"1", "'2.0.0-BETA.5'", "'1.99.99'"})
    void olderReleaseMigratesEveryLanguageAndLegacyConfigOnce(String version) throws Exception {
        Path folder = Files.createDirectories(directory.resolve("lang"));
        var codes = new java.util.ArrayList<>(LanguageFiles.LANGUAGES);
        codes.add("own");
        String originalLanguage = "# Preserve custom text\ncheck.title: Custom\n";
        for (String code : codes) Files.writeString(folder.resolve(code + ".yml"), originalLanguage);
        Path configFile = directory.resolve("config.yml");
        String originalConfig = "config-version: " + version + "\nlanguage: own\nwhitelist: true\n"
                + "use-material-names: false\ncheck-metadata: true\n";
        Files.writeString(configFile, originalConfig);
        var config = PluginConfiguration.load(configFile.toFile(), getClass().getResourceAsStream("/config.yml"));
        assertEquals("2.0.0-BETA.7", config.getString("config-version"));
        assertTrue(config.getBoolean("require-login-permission"));
        assertEquals(originalConfig, Files.readString(directory.resolve("config.yml.bak")));
        String migratedConfig = Files.readString(configFile);
        assertFalse(migratedConfig.contains("whitelist:"));
        assertFalse(migratedConfig.contains("use-material-names:"));
        assertFalse(migratedConfig.contains("check-metadata:"));
        for (String code : codes) {
            assertEquals(originalLanguage, Files.readString(folder.resolve(code + ".yml.bak")));
            assertTrue(Files.readString(folder.resolve(code + ".yml")).contains("check.note:"));
        }
        PluginConfiguration.load(configFile.toFile(), getClass().getResourceAsStream("/config.yml"));
        assertEquals(migratedConfig, Files.readString(configFile));
        assertFalse(Files.exists(directory.resolve("config.yml.bak.1")));
        for (String code : codes) assertFalse(Files.exists(folder.resolve(code + ".yml.bak.1")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"2.0.0-BETA.6", "2.0.0-BETA.7", "2.0.0-BETA.10", "2.0.0", "2.0.1-BETA.1", "2.1.0", "3.0.0"})
    void equalOrNewerReleaseSkipsAllMigrationsEvenWithMissingDefaultsAndLegacyOverrides(String version) throws Exception {
        Path folder = Files.createDirectories(directory.resolve("lang"));
        var codes = new java.util.ArrayList<>(LanguageFiles.LANGUAGES);
        codes.add("own");
        String sparse = "check.title: Custom\n";
        for (String code : codes) Files.writeString(folder.resolve(code + ".yml"), sparse);
        Path configFile = directory.resolve("config.yml");
        String original = "# Do not rewrite\nconfig-version: '" + version + "'\nwhitelist: true\n"
                + "use-material-names: false\ncheck-metadata: true\nmessages:\n  modifyworld.chat: Custom\n";
        Files.writeString(configFile, original);
        var config = PluginConfiguration.load(configFile.toFile(), getClass().getResourceAsStream("/config.yml"));
        assertFalse(config.getBoolean("require-login-permission"));
        assertEquals("Custom", config.getString("messages/modifyworld.chat"));
        assertEquals(version.equals("2.0.0-BETA.6")
                ? original.replace("2.0.0-BETA.6", "2.0.0-BETA.7") : original, Files.readString(configFile));
        assertFalse(Files.exists(directory.resolve("config.yml.bak")));
        for (String code : codes) {
            assertEquals(sparse, Files.readString(folder.resolve(code + ".yml")));
            assertFalse(Files.exists(folder.resolve(code + ".yml.bak")));
        }
    }

    @Test
    void freshConfigurationUsesRunningReleaseEvenWhenLastMigrationIsOlder() throws Exception {
        String defaults;
        try (var stream = getClass().getResourceAsStream("/config.yml")) {
            defaults = new String(stream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
                    .replace("2.0.0-BETA.7", "2.0.0-BETA.8");
        }
        Path file = directory.resolve("config.yml");
        var config = PluginConfiguration.load(file.toFile(), new java.io.ByteArrayInputStream(
                defaults.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        assertEquals("2.0.0-BETA.8", config.getString("config-version"));
        assertEquals("2.0.0-BETA.8", YamlConfiguration.loadConfiguration(file.toFile()).getString("config-version"));
        assertFalse(Files.exists(directory.resolve("config.yml.bak")));
    }

    @Test
    void markerOnlyMigrationDoesNotCreateBackupOrRewriteOtherContent() throws Exception {
        String original;
        try (var stream = getClass().getResourceAsStream("/config.yml")) {
            original = new String(stream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
                    .replaceAll("(?m)^config-version:.*\\R", "");
        }
        Path file = directory.resolve("config.yml");
        Files.writeString(file, original);
        PluginConfiguration.load(file.toFile(), getClass().getResourceAsStream("/config.yml"));
        assertEquals("config-version: '2.0.0-BETA.7'\n" + original, Files.readString(file));
        assertFalse(Files.exists(directory.resolve("config.yml.bak")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"config-version: '2.0.0-BETA.6' # keep comment",
            "\"config-version\": \"2.0.0-BETA.6\"", "config-version: 2.0.0-BETA.6"})
    void releaseOnlyUpdatePreservesSparseConfigAndCommentsWithoutBackup(String marker) throws Exception {
        Path file = directory.resolve("config.yml");
        String original = "# Header\r\n" + marker + "\r\nlanguage: en\r\n# Keep flags sparse\r\n";
        Files.writeString(file, original);
        String defaults;
        try (var stream = getClass().getResourceAsStream("/config.yml")) {
            defaults = new String(stream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
                    .replace("2.0.0-BETA.7", "2.0.0-BETA.8");
        }
        PluginConfiguration.load(file.toFile(), new java.io.ByteArrayInputStream(
                defaults.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        String updatedMarker = marker.replace("'2.0.0-BETA.6'", "'2.0.0-BETA.8'")
                .replace("\"2.0.0-BETA.6\"", "'2.0.0-BETA.8'")
                .replace(": 2.0.0-BETA.6", ": '2.0.0-BETA.8'");
        assertEquals(original.replace(marker, updatedMarker), Files.readString(file));
        assertFalse(Files.exists(directory.resolve("config.yml.bak")));
    }

    @Test
    void laterPluginReleaseDoesNotRepeatCompletedLegacyMigrations() throws Exception {
        Path configFile = directory.resolve("config.yml");
        load("en");
        String completed = Files.readString(configFile);
        Path english = directory.resolve("lang/en.yml");
        String sparse = "check.title: Custom\n";
        Files.writeString(english, sparse);
        String futureDefaults;
        try (var stream = getClass().getResourceAsStream("/config.yml")) {
            futureDefaults = new String(stream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
                    .replace("2.0.0-BETA.7", "2.0.0-BETA.8");
        }
        var config = PluginConfiguration.load(configFile.toFile(), new java.io.ByteArrayInputStream(
                futureDefaults.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        assertEquals("2.0.0-BETA.8", config.getString("config-version"));
        assertEquals(completed.replace("config-version: '2.0.0-BETA.7'", "config-version: '2.0.0-BETA.8'"),
                Files.readString(configFile));
        assertEquals(sparse, Files.readString(english));
        assertFalse(Files.exists(english.resolveSibling("en.yml.bak")));
        assertFalse(Files.exists(directory.resolve("config.yml.bak.1")));
    }

    @Test
    void allExistingLanguagesAndOwnGainOnlyMissingKeysWithExactBackups() throws Exception {
        Path folder = Files.createDirectories(directory.resolve("lang"));
        var languages = new java.util.ArrayList<>(LanguageFiles.LANGUAGES);
        languages.add("own");
        String original = "# My comment\r\nmodifyworld.chat: 'My custom denial'\r\n"
                + "check.title: 'My custom heading'\r\nextra: 'Keep this'";
        for (String code : languages) Files.writeString(folder.resolve(code + ".yml"), original);
        Files.writeString(folder.resolve("de.yml.bak"), "Earlier backup");
        var config = load("de");
        assertEquals("My custom heading", config.getString("messages/check.title"));
        assertEquals("My custom denial", config.getString("messages/modifyworld.chat"));
        for (String code : languages) {
            Path file = folder.resolve(code + ".yml");
            String upgraded = Files.readString(file);
            assertTrue(upgraded.startsWith(original));
            Path backup = folder.resolve(code + ".yml.bak" + (code.equals("de") ? ".1" : ""));
            assertEquals(original, Files.readString(backup));
            var yaml = new YamlConfiguration();
            yaml.options().pathSeparator('/');
            yaml.load(file.toFile());
            var defaults = LanguageFiles.bundled(code.equals("own") ? "en" : code);
            for (String key : defaults.getKeys(false)) {
                if (key.startsWith("check.") && !key.equals("check.title")) {
                    assertEquals(defaults.getString(key), yaml.getString(key), code + ": " + key);
                }
            }
            assertFalse(yaml.contains("modifyworld.login", true));
            LanguageFiles.migrateChecks(directory.toFile());
            assertEquals(upgraded, Files.readString(file));
            assertFalse(Files.exists(folder.resolve(code + ".yml.bak" + (code.equals("de") ? ".2" : ".1"))));
        }
        assertEquals("Earlier backup", Files.readString(folder.resolve("de.yml.bak")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"en", "de", "es", "fr", "pt_br", "pl", "tr", "own"})
    void missingTextsUseBundledSelectedLanguageThenEnglish(String language) throws Exception {
        Files.createDirectories(directory.resolve("lang"));
        Files.writeString(directory.resolve("lang/" + language + ".yml"), "check.title: 'Custom'\n");
        var config = load(language);
        var texts = new CheckMessages(config);
        assertEquals("Custom", texts.text("title"));
        assertEquals(LanguageFiles.bundled(language.equals("own") ? "en" : language).getString("check.note"),
                texts.text("note"));
        config.set("messages/check.note", null);
        assertEquals(LanguageFiles.bundled("en").getString("check.note"), new CheckMessages(config).text("note"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"check.note: 42\n", "check.note: [invalid]\n", "check.note: |\n  First\n  Second\n",
        "check.note: \"First\\rSecond\"\n", "check.note: \"First\\u2028Second\"\n", "check.note: [\n"})
    void invalidDiagnosticTextPreservesOriginalFilesBeforeMigration(String invalid) throws Exception {
        Path folder = Files.createDirectories(directory.resolve("lang"));
        Path german = folder.resolve("de.yml");
        Files.writeString(german, invalid);
        Path config = directory.resolve("config.yml");
        String original = "language: de\n";
        Files.writeString(config, original);
        assertThrows(InvalidConfigurationException.class,
                () -> PluginConfiguration.load(config.toFile(), getClass().getResourceAsStream("/config.yml")));
        assertEquals(invalid, Files.readString(german));
        assertEquals(original, Files.readString(config));
        assertFalse(Files.exists(folder.resolve("de.yml.bak")));
        assertFalse(Files.exists(directory.resolve("config.yml.bak")));
    }

    @Test
    void invalidInactiveDiagnosticFileStopsMigrationBeforeAnyFileIsRewritten() throws Exception {
        var folder = Files.createDirectories(directory.resolve("lang"));
        String original = "# Custom English\ncheck.title: Custom\n";
        Files.writeString(folder.resolve("en.yml"), original);
        Files.writeString(folder.resolve("tr.yml"), "check.note: 42\n");
        assertThrows(InvalidConfigurationException.class, () -> load("en"));
        assertEquals(original, Files.readString(folder.resolve("en.yml")));
        assertFalse(Files.exists(folder.resolve("en.yml.bak")));
        assertEquals("language: en\n", Files.readString(directory.resolve("config.yml")));
    }
    @Test
    void matchingLegacyOwnFileMayLackNewKeysWithoutFalseConflict() throws Exception {
        Path folder = Files.createDirectories(directory.resolve("lang"));
        var originalOwn = LanguageFiles.bundled("de");
        for (String key : new java.util.ArrayList<>(originalOwn.getKeys(false))) {
            if (key.startsWith("check.")) originalOwn.set(key, null);
        }
        originalOwn.set("modifyworld.chat", "Custom chat");
        String original = originalOwn.saveToString();
        Files.writeString(folder.resolve("own.yml"), original);
        Path file = directory.resolve("config.yml");
        Files.writeString(file, "language: de\nmessages:\n  modifyworld.chat: Custom chat\n");
        var config = PluginConfiguration.load(file.toFile(), getClass().getResourceAsStream("/config.yml"));
        assertEquals("own", config.getString("language"));
        assertEquals("[Modifyworld] Berechtigungsprüfung", new CheckMessages(config).text("title"));
        assertEquals(original, Files.readString(folder.resolve("own.yml.bak")));
        String upgraded = Files.readString(folder.resolve("own.yml"));
        assertTrue(upgraded.startsWith(original));
        config = PluginConfiguration.load(file.toFile(), getClass().getResourceAsStream("/config.yml"));
        assertEquals("[Modifyworld] Berechtigungsprüfung", new CheckMessages(config).text("title"));
        assertEquals(upgraded, Files.readString(folder.resolve("own.yml")));
        assertFalse(Files.exists(folder.resolve("own.yml.bak.1")));
    }

    @Test
    void conflictingLegacyOwnPreservesEveryExistingLanguageFile() throws Exception {
        var folder = Files.createDirectories(directory.resolve("lang"));
        String english = "# Keep this\nmodifyworld.chat: Original\n";
        String own = "modifyworld.chat: Conflicting\n";
        Files.writeString(folder.resolve("en.yml"), english);
        Files.writeString(folder.resolve("own.yml"), own);
        Path file = directory.resolve("config.yml");
        String original = "messages:\n  modifyworld.chat: Original\n";
        Files.writeString(file, original);
        assertThrows(java.io.IOException.class,
                () -> PluginConfiguration.load(file.toFile(), getClass().getResourceAsStream("/config.yml")));
        assertEquals(english, Files.readString(folder.resolve("en.yml")));
        assertEquals(own, Files.readString(folder.resolve("own.yml")));
        assertEquals(original, Files.readString(file));
        assertFalse(Files.exists(folder.resolve("en.yml.bak")));
        assertFalse(Files.exists(folder.resolve("own.yml.bak")));
    }

}
