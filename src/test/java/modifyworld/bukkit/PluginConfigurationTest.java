// SPDX-License-Identifier: GPL-2.0-or-later
// Modified or added for the Paper port on 2026-09-28 and 2026-09-29; see NOTICE.
package modifyworld.bukkit;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.bukkit.configuration.InvalidConfigurationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

class PluginConfigurationTest {
    @TempDir Path directory;

    private InputStream defaults() {
        return getClass().getResourceAsStream("/config.yml");
    }

    @ParameterizedTest
    @ValueSource(strings = {"es", "fr"})
    void additionalLanguagesAreInstalledCompleteAndMigrated(String language) throws Exception {
        Path file = directory.resolve("config.yml");
        Files.writeString(file, "language: " + language + "\n");
        var config = PluginConfiguration.load(file.toFile(), defaults());
        var english = new org.bukkit.configuration.file.YamlConfiguration();
        english.options().pathSeparator('/');
        english.load(directory.resolve("lang/en.yml").toFile());
        var selected = new org.bukkit.configuration.file.YamlConfiguration();
        selected.options().pathSeparator('/');
        selected.load(directory.resolve("lang/" + language + ".yml").toFile());
        assertEquals(english.getKeys(false), selected.getKeys(false));
        var pattern = java.util.regex.Pattern.compile("\\$[0-9]+|%s|&[a-f0-9]");
        for (String key : english.getKeys(false)) {
            assertEquals(pattern.matcher(english.getString(key)).results().map(m -> m.group()).toList(),
                    pattern.matcher(selected.getString(key)).results().map(m -> m.group()).toList(), key);
            assertEquals(selected.getString(key), config.getString("messages/" + key));
        }
        Files.writeString(file, "language: " + language + "\nmessages:\n  modifyworld.chat: Custom\n");
        config = PluginConfiguration.load(file.toFile(), defaults());
        assertEquals("own", config.getString("language"));
        assertEquals(selected.getString("modifyworld.items.take"), config.getString("messages/modifyworld.items.take"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"use-material-names: false", "check-metadata: true",
        "use-material-names: false\ncheck-metadata: true", "use-material-names: true\ncheck-metadata: false"})
    void obsoleteMaterialSwitchesAreRemovedWithoutBlockingMigration(String switches) throws Exception {
        Path file = directory.resolve("config.yml");
        String original = switches + "\nitem-restrictions: true\ninform-players: true\nwhitelist: false\n"
                + "drop-restricted-item: true\nitem-use-check: true\nmessages:\n"
                + "  message-format: '&f[&2System&f]&4 %s'\n"
                + "  modifyworld.items.take: 'Hey, &a$1&4 das bleibt an seinem Platz!'\n";
        Files.writeString(file, original);
        var config = PluginConfiguration.load(file.toFile(), defaults());
        assertEquals("own", config.getString("language"));
        assertTrue(config.getBoolean("item-restrictions"));
        assertTrue(config.getBoolean("drop-restricted-item"));
        assertFalse(config.getBoolean("require-login-permission"));
        assertEquals("Hey, &a$1&4 das bleibt an seinem Platz!", config.getString("messages/modifyworld.items.take"));
        assertEquals(original, Files.readString(directory.resolve("config.yml.bak")));
        String migrated = Files.readString(file);
        assertFalse(migrated.contains("use-material-names:"));
        assertFalse(migrated.contains("check-metadata:"));
        assertFalse(config.contains("use-material-names"));
        assertFalse(config.contains("check-metadata"));
        PluginConfiguration.load(file.toFile(), defaults());
        assertEquals(migrated, Files.readString(file));
    }

    @Test
    void existingBackupIsPreservedAndOwnWithoutAFileIsRejected() throws Exception {
        Path file = directory.resolve("config.yml");
        Path backup = directory.resolve("config.yml.bak");
        Files.writeString(backup, "Earlier backup");
        Files.writeString(file, "language: de\n");
        PluginConfiguration.load(file.toFile(), defaults());
        assertEquals("Earlier backup", Files.readString(backup));
        assertEquals("language: de\n", Files.readString(directory.resolve("config.yml.bak.1")));
        Files.writeString(file, "language: own\n");
        assertThrows(java.io.IOException.class, () -> PluginConfiguration.load(file.toFile(), defaults()));
        assertEquals("language: own\n", Files.readString(file));
    }

    @Test
    void migrationIsIdempotentAndPreservesSettingsAndBackup() throws Exception {
        Path file = directory.resolve("config.yml");
        String original = "# Personal settings\nwhitelist: true\ninform-players: false\nmessages:\n  modifyworld.chat: Hallo $1\n";
        Files.writeString(file, original);
        var config = PluginConfiguration.load(file.toFile(), defaults());
        assertEquals("own", config.getString("language"));
        assertFalse(config.getBoolean("inform-players"));
        assertTrue(config.getBoolean("require-login-permission"));
        assertEquals(original, Files.readString(directory.resolve("config.yml.bak")));
        String migrated = Files.readString(file);
        String own = Files.readString(directory.resolve("lang/own.yml"));
        assertFalse(migrated.contains("modifyworld.chat"));
        assertFalse(migrated.contains("\nwhitelist:"));
        assertTrue(migrated.contains("op-bypass: false"));
        assertTrue(own.contains("Hallo $1"));
        PluginConfiguration.load(file.toFile(), defaults());
        assertEquals(migrated, Files.readString(file));
        assertEquals(own, Files.readString(directory.resolve("lang/own.yml")));
        assertFalse(Files.exists(directory.resolve("config.yml.bak.1")));
    }

    @Test
    void conflictingOwnFileStopsMigrationWithoutChangingUserFiles() throws Exception {
        Path file = directory.resolve("config.yml");
        String original = "messages:\n  modifyworld.chat: Original\n";
        Files.writeString(file, original);
        Files.createDirectories(directory.resolve("lang"));
        Path own = directory.resolve("lang/own.yml");
        Files.writeString(own, "modifyworld.chat: Existing\n");
        assertThrows(java.io.IOException.class, () -> PluginConfiguration.load(file.toFile(), defaults()));
        assertEquals(original, Files.readString(file));
        assertEquals("modifyworld.chat: Existing\n", Files.readString(own));
    }

    @Test
    void selectedLanguageReachesPlayerWithFormattingAndOverrides() throws Exception {
        Path file = directory.resolve("config.yml");
        Files.writeString(file, "language: de\nmessages: {}\n");
        var config = PluginConfiguration.load(file.toFile(), defaults());
        var player = org.mockito.Mockito.mock(org.bukkit.entity.Player.class);
        var informer = new modifyworld.PlayerInformer(config);
        informer.informPlayer(player, "modifyworld.bucket.fill.lava", org.bukkit.Material.LAVA);
        org.mockito.Mockito.verify(player).sendMessage("§f[§2Modifyworld§f]§4 Du darfst §alava§4 nicht mit einem Eimer aufnehmen.");
        org.mockito.Mockito.reset(player);
        Files.writeString(file, "language: de\nmessages:\n  modifyworld.bucket.fill: Custom $1\n");
        config = PluginConfiguration.load(file.toFile(), defaults());
        informer = new modifyworld.PlayerInformer(config);
        informer.informPlayer(player, "modifyworld.bucket.fill.lava", org.bukkit.Material.LAVA);
        org.mockito.Mockito.verify(player).sendMessage("§f[§2Modifyworld§f]§4 Custom lava");
        org.mockito.Mockito.reset(player);
        config.set("inform-players", false);
        new modifyworld.PlayerInformer(config).informPlayer(player, "modifyworld.bucket.fill.lava");
        org.mockito.Mockito.verifyNoInteractions(player);
    }

    @Test
    void languagesAreInstalledAndOverridesSurviveLanguageChanges() throws Exception {
        Path file = directory.resolve("config.yml");
        String original = "language: de\nmessages:\n  modifyworld.chat: Custom chat\n";
        Files.writeString(file, original);
        var config = PluginConfiguration.load(file.toFile(), defaults());
        assertTrue(Files.exists(directory.resolve("lang/en.yml")));
        assertTrue(Files.exists(directory.resolve("lang/de.yml")));
        assertEquals("Custom chat", config.getString("messages/modifyworld.chat"));
        assertEquals("Du darfst &a$1&4 nicht mit einem Eimer aufnehmen.", config.getString("messages/modifyworld.bucket.fill"));
        assertEquals(original, Files.readString(directory.resolve("config.yml.bak")));
        assertEquals("own", config.getString("language"));
        Path german = directory.resolve("lang/own.yml");
        Files.writeString(german, "modifyworld.bucket.fill: Custom bucket\n");
        config = PluginConfiguration.load(file.toFile(), defaults());
        assertEquals("Custom bucket", config.getString("messages/modifyworld.bucket.fill"));
        assertNotNull(config.getString("messages/modifyworld.login"));
        assertEquals("modifyworld.bucket.fill: Custom bucket\n", Files.readString(german));
        Files.writeString(file, "language: en\n");
        config = PluginConfiguration.load(file.toFile(), defaults());
        assertEquals("You may not collect &a$1&4 with a bucket.", config.getString("messages/modifyworld.bucket.fill"));
    }

    @Test
    void malformedLanguageFileIsNotOverwritten() throws Exception {
        Path file = directory.resolve("config.yml");
        PluginConfiguration.load(file.toFile(), defaults());
        Path english = directory.resolve("lang/en.yml");
        Files.writeString(english, "message-format: '%q'\n");
        assertThrows(InvalidConfigurationException.class,
                () -> PluginConfiguration.load(file.toFile(), defaults()));
        assertEquals("message-format: '%q'\n", Files.readString(english));
    }

    @Test
    void freshInstallCreatesValidConfigurationWithLiteralPermissionKeys() throws Exception {
        Path file = directory.resolve("plugin/config.yml");
        var config = PluginConfiguration.load(file.toFile(), defaults());
        assertTrue(Files.exists(file));
        assertTrue(config.getBoolean("item-use-check"));
        assertFalse(config.getBoolean("require-login-permission"));
        assertFalse(Files.readString(file).contains("\nwhitelist:"));
        assertNotNull(config.getString("messages/modifyworld.bucket.fill"));
        assertNull(config.getConfigurationSection("messages/modifyworld"));
    }

    @Test
    void existingFlagsAndCommentsArePreservedWhileDefaultsAreAvailable() throws Exception {
        Path file = directory.resolve("config.yml");
        String original = "# Keep this comment\nitem-use-check: false\nwhitelist: true\n";
        Files.writeString(file, original);
        var config = PluginConfiguration.load(file.toFile(), defaults());
        assertFalse(config.getBoolean("item-use-check"));
        assertTrue(config.getBoolean("require-login-permission"));
        assertNotNull(config.getString("messages/default-message"));
        assertEquals(original, Files.readString(directory.resolve("config.yml.bak")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"language: unknown", "op-bypass: 'true'", "messages: [",
        "item-use-check: 'true'", "whitelist: 'true'", "require-login-permission: 'false'", "messages: invalid", "messages:\n  message-format: '%q'",
        "messages:\n  modifyworld.chat: 42"})
    void invalidConfigurationIsRejectedWithoutRewritingIt(String original) throws Exception {
        Path file = directory.resolve("config.yml");
        Files.writeString(file, original);
        assertThrows(InvalidConfigurationException.class,
                () -> PluginConfiguration.load(file.toFile(), defaults()));
        assertEquals(original, Files.readString(file));
    }

    @Test
    void missingOrBrokenBundledDefaultsDoNotCreateAFile() {
        Path file = directory.resolve("config.yml");
        assertThrows(java.io.IOException.class, () -> PluginConfiguration.load(file.toFile(), null));
        assertThrows(InvalidConfigurationException.class, () -> PluginConfiguration.load(file.toFile(),
                new ByteArrayInputStream("[".getBytes(StandardCharsets.UTF_8))));
        assertFalse(Files.exists(file));
    }

    @Test
    void directoryInsteadOfFileIsRejected() throws Exception {
        Path file = directory.resolve("config.yml");
        Files.createDirectory(file);
        assertThrows(java.io.IOException.class, () -> PluginConfiguration.load(file.toFile(), defaults()));
        assertTrue(Files.isDirectory(file));
    }
    @ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({
        "true,true,true", "true,false,false", "false,true,true", "false,false,false",
        "true,absent,true", "false,absent,false", "absent,true,true", "absent,false,false",
        "absent,absent,false"
    })
    void loginPermissionAliasAndExplicitPrecedence(String legacy, String modern, boolean expected) throws Exception {
        Path file = directory.resolve("config.yml");
        String original = (legacy.equals("absent") ? "" : "whitelist: " + legacy + "\n")
                + (modern.equals("absent") ? "" : "require-login-permission: " + modern + "\n");
        Files.writeString(file, original);
        var config = PluginConfiguration.load(file.toFile(), defaults());
        assertEquals(expected, config.getBoolean("require-login-permission"));
        assertEquals(original, Files.readString(directory.resolve("config.yml.bak")));
    }

}
