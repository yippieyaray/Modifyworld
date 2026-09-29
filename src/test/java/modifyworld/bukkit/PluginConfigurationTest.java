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
        assertEquals(original, Files.readString(file));
        Path german = directory.resolve("lang/de.yml");
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
        assertEquals(original, Files.readString(file));
    }

    @ParameterizedTest
    @ValueSource(strings = {"language: fr", "op-bypass: 'true'", "messages: [", "use-material-names: false", "check-metadata: true",
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
        assertEquals(original, Files.readString(file));
    }

}
