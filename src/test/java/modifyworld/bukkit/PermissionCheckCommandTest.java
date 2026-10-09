// SPDX-License-Identifier: GPL-2.0-or-later
// Modified on 2026-10-08: clarify nullness at test API boundaries.
// Modified on 2026-10-07: cover translated layout, decision reasons and command errors.
// Modified on 2026-10-06: verify diagnostic colors, final result order and online target setup.
// Added on 2026-10-05: cover self/console diagnostics and permission policy.
package modifyworld.bukkit;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.command.CommandSender;
import org.mockito.ArgumentCaptor;
import org.jspecify.annotations.NonNull;
import java.util.Objects;
import static org.junit.jupiter.api.Assertions.*;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import static org.mockito.Mockito.*;

class PermissionCheckCommandTest {
    private final PermissionCheckCommand command = new PermissionCheckCommand();
    private Player player;
    private YamlConfiguration config;

    private java.util.List<@NonNull Component> messages(CommandSender sender) {
        var captor = captureMessages(sender);
        java.util.List<@NonNull Component> captured = new java.util.ArrayList<>();
        for (Component component : captor.getAllValues()) {
            captured.add(Objects.requireNonNull(component));
        }
        return captured;
    }

    // Mockito lacks null contracts, and capture() returns a matcher placeholder.
    @SuppressWarnings("all")
    private ArgumentCaptor<@NonNull Component> captureMessages(CommandSender sender) {
        ArgumentCaptor<@NonNull Component> captor = ArgumentCaptor.forClass(Component.class);
        verify(sender, atLeastOnce()).sendMessage(captor.capture());
        return captor;
    }

    private @NonNull Component actionMessage(CommandSender sender) {
        var output = messages(sender);
        return Objects.requireNonNull(output.get(output.size() - 2));
    }

    @BeforeEach
    // Mockito mocks and verification results are non-null but lack null annotations.
    @SuppressWarnings("null")
    void setup() {
        player = mock(Player.class);
        when(player.getName()).thenReturn("TestPlayer");
        World world = mock(World.class);
        when(world.getName()).thenReturn("build");
        when(player.getWorld()).thenReturn(world);
        when(player.hasPermission("modifyworld.command.check")).thenReturn(true);
        config = new YamlConfiguration();
    }

    @ParameterizedTest
    @CsvSource({
        "true,false,false,true,DENIED",
        "false,false,false,false,DENIED",
        "true,false,true,true,ALLOWED",
        "true,false,true,false,DENIED",
        "true,true,true,false,ALLOWED",
        "false,true,true,false,DENIED"
    })
    // JDK collections do not declare the nullness contracts of captured test values.
    @SuppressWarnings("null")
    void selfCheckReportsActualPolicy(boolean op, boolean bypass, boolean assigned, boolean granted, String result) {
        String node = "modifyworld.blocks.place.tnt";
        config.set("op-bypass", bypass);
        when(player.isOp()).thenReturn(op);
        when(player.isPermissionSet(node)).thenReturn(assigned);
        when(player.hasPermission(node)).thenReturn(granted);
        command.execute(player, new String[] {"check", node}, config);
        var messages = messages(player);
        var plain = messages.stream().map(component -> PlainTextComponentSerializer.plainText().serialize(Objects.requireNonNull(component))).toList();
        assertTrue(plain.contains("Bukkit permission result: " + granted));
        assertTrue(plain.contains("Permission explicitly assigned or inherited: " + (assigned ? "yes" : "no")));
        assertTrue(plain.getLast().startsWith("Note: This checks Modifyworld permissions only."));
        assertEquals("Player: TestPlayer", plain.get(1));
        assertEquals("Player is OP: " + op, plain.get(2));
        assertTrue(plain.contains("Modifyworld OP bypass: " + bypass));
        assertEquals(NamedTextColor.YELLOW, messages.getLast().color());
        assertEquals("Action: " + result, plain.get(plain.size() - 2));
        Component outcome = messages.get(messages.size() - 2).children().getFirst();
        assertEquals(result.equals("ALLOWED") ? NamedTextColor.GREEN : NamedTextColor.RED, outcome.color());
        assertEquals(TextDecoration.State.TRUE, outcome.decoration(TextDecoration.BOLD));
        assertEquals(NamedTextColor.GRAY, messages.get(1).color());
        assertEquals(NamedTextColor.AQUA, messages.get(1).children().getFirst().color());
        verify(player, never()).setOp(anyBoolean());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void unassignedUnknownActionRemainsAllowed(boolean op) {
        when(player.isOp()).thenReturn(op);
        command.execute(player, new String[] {"check", "modifyworld.items.allowunknownaction.tnt"}, config);
        assertEquals("Action: ALLOWED", PlainTextComponentSerializer.plainText().serialize(actionMessage(player)));
    }

    @Test
    // Mockito mocks and verification results are non-null but lack null annotations.
    @SuppressWarnings("null")
    void consoleUsesNamedOnlineTarget() {
        var console = mock(ConsoleCommandSender.class);
        var server = mock(Server.class);
        when(console.getServer()).thenReturn(server);
        when(server.getPlayerExact("TestPlayer")).thenReturn(player);
        command.execute(console, new String[] {"check", "TestPlayer", "modifyworld.blocks.place.tnt"}, config);
        assertEquals("Action: DENIED", PlainTextComponentSerializer.plainText().serialize(actionMessage(console)));
        verify(player, never()).sendMessage(any(Component.class));
    }

    @Test
    // Mockito mocks and verification results are non-null but lack null annotations.
    @SuppressWarnings("null")
    void playerCanCheckAnotherOnlinePlayer() {
        var server = mock(Server.class);
        var target = mock(Player.class);
        when(player.getServer()).thenReturn(server);
        when(server.getPlayerExact("OtherPlayer")).thenReturn(target);
        when(target.getName()).thenReturn("OtherPlayer");
        var world = player.getWorld();
        when(target.getWorld()).thenReturn(world);
        when(target.hasPermission("modifyworld.chat")).thenReturn(true);
        command.execute(player, new String[] {"check", "OtherPlayer", "modifyworld.chat"}, config);
        assertEquals("Action: ALLOWED", PlainTextComponentSerializer.plainText().serialize(actionMessage(player)));
        verify(target, never()).sendMessage(any(Component.class));
    }

    @Test
    // Mockito mocks and verification results are non-null but lack null annotations.
    @SuppressWarnings("null")
    void consoleNeedsTargetAndRejectsOfflineTarget() {
        var console = mock(ConsoleCommandSender.class);
        var server = mock(Server.class);
        when(console.getServer()).thenReturn(server);
        command.execute(console, new String[] {"check", "modifyworld.chat"}, config);
        verify(console, times(2)).sendMessage(startsWith("Usage:"));
        command.execute(console, new String[] {"check", "Offline", "modifyworld.chat"}, config);
        verify(console).sendMessage("Player not found. The target must be online.");
    }

    @Test
    void unauthorizedPlayerCannotUseDiagnostic() {
        when(player.hasPermission("modifyworld.command.check")).thenReturn(false);
        command.execute(player, new String[] {"check", "modifyworld.chat"}, config);
        verify(player).sendMessage("You do not have permission to use this command.");
        verify(player, never()).hasPermission("modifyworld.chat");
    }

    @ParameterizedTest
    @ValueSource(strings = {"modifyworld.*", "luckperms.info", "modifyworld.command.check", "modifyworld..chat"})
    void rejectsInvalidOrNonActionNodes(String node) {
        command.execute(player, new String[] {"check", node}, config);
        verify(player).sendMessage("Specify a concrete Modifyworld action permission, without wildcards.");
    }
    private void useLanguage(String language) throws Exception {
        var yaml = LanguageFiles.bundled(language);
        config.options().pathSeparator('/');
        config.createSection("messages", yaml.getValues(false));
    }

    @ParameterizedTest
    @ValueSource(strings = {"en", "de", "es", "fr", "pt_br", "pl", "tr"})
    // JDK collections do not declare the nullness contracts of captured test values.
    @SuppressWarnings("null")
    void translatedDiagnosticsKeepElevenLinesOrderColorsAndRawValues(String language) throws Exception {
        useLanguage(language);
        String node = "modifyworld.blocks.place.tnt";
        when(player.isOp()).thenReturn(true);
        when(player.hasPermission(node)).thenReturn(true);
        command.execute(player, new String[] {"check", node}, config);
        var output = messages(player);
        var plain = output.stream().map(component -> PlainTextComponentSerializer.plainText().serialize(Objects.requireNonNull(component))).toList();
        var yaml = LanguageFiles.bundled(language);
        assertEquals(java.util.List.of(
                yaml.getString("check.title"),
                yaml.getString("check.label.player") + "TestPlayer",
                yaml.getString("check.label.op") + yaml.getString("check.value.true"),
                yaml.getString("check.label.world") + "build",
                yaml.getString("check.label.permission") + node,
                yaml.getString("check.label.bypass") + yaml.getString("check.value.false"),
                yaml.getString("check.label.bukkit-result") + yaml.getString("check.value.true"),
                yaml.getString("check.label.assigned") + yaml.getString("check.value.no"),
                yaml.getString("check.label.reason") + yaml.getString("check.reason.op-fallback"),
                yaml.getString("check.label.action") + yaml.getString("check.value.denied"),
                yaml.getString("check.note")), plain);
        assertEquals(NamedTextColor.GOLD, output.getFirst().color());
        for (int i = 1; i <= 8; i++) {
            assertEquals(NamedTextColor.GRAY, output.get(i).color());
            assertEquals(NamedTextColor.AQUA, output.get(i).children().getFirst().color());
        }
        assertEquals(NamedTextColor.GRAY, output.get(9).color());
        assertEquals(NamedTextColor.RED, output.get(9).children().getFirst().color());
        assertEquals(TextDecoration.State.TRUE, output.get(9).children().getFirst().decoration(TextDecoration.BOLD));
        assertEquals(NamedTextColor.YELLOW, output.getLast().color());
        verify(player, never()).setOp(anyBoolean());
    }

    @ParameterizedTest
    @CsvSource({
        "true,true,true,false,modifyworld.chat,bypass,allowed",
        "false,false,false,false,modifyworld.items.allowunknownaction.tnt,unknown-default,allowed",
        "true,false,false,true,modifyworld.chat,op-fallback,denied",
        "false,false,true,true,modifyworld.chat,granted,allowed",
        "false,false,true,false,modifyworld.chat,denied,denied"
    })
    void allDecisionReasonsAreLocalizedWithoutChangingPolicy(boolean op, boolean bypass,
            boolean assigned, boolean granted, String node, String reason, String result) throws Exception {
        useLanguage("de");
        config.set("op-bypass", bypass);
        when(player.isOp()).thenReturn(op);
        when(player.isPermissionSet(node)).thenReturn(assigned);
        when(player.hasPermission(node)).thenReturn(granted);
        command.execute(player, new String[] {"check", node}, config);
        var plain = messages(player).stream().map(component -> PlainTextComponentSerializer.plainText().serialize(Objects.requireNonNull(component))).toList();
        var yaml = LanguageFiles.bundled("de");
        assertEquals(yaml.getString("check.label.reason") + yaml.getString("check.reason." + reason), plain.get(8));
        assertEquals(yaml.getString("check.label.action") + yaml.getString("check.value." + result), plain.get(9));
    }

    @Test
    // Mockito mocks and verification results are non-null but lack null annotations.
    @SuppressWarnings("null")
    void localizedConsoleDiagnosticsAndErrorsUseServerLanguage() throws Exception {
        useLanguage("de");
        var console = mock(ConsoleCommandSender.class);
        var server = mock(Server.class);
        when(console.getServer()).thenReturn(server);
        when(server.getPlayerExact("TestPlayer")).thenReturn(player);
        command.execute(console, new String[] {"check", "TestPlayer", "modifyworld.chat"}, config);
        assertEquals("Aktion: VERWEIGERT", PlainTextComponentSerializer.plainText().serialize(actionMessage(console)));
        command.execute(console, new String[] {"check", "modifyworld.chat"}, config);
        verify(console).sendMessage("Verwendung: /modifyworld check <permission> (im Spiel)");
        verify(console).sendMessage("Verwendung: /modifyworld check <player> <permission> (Zielspieler online)");
        command.execute(console, new String[] {"check", "Offline", "modifyworld.chat"}, config);
        verify(console).sendMessage("Spieler nicht gefunden. Der Zielspieler muss online sein.");
        command.execute(player, new String[] {"check", "modifyworld.*"}, config);
        verify(player).sendMessage("Gib eine konkrete Modifyworld-Aktionsberechtigung ohne Platzhalter an.");
        when(player.hasPermission("modifyworld.command.check")).thenReturn(false);
        command.execute(player, new String[] {"check", "modifyworld.chat"}, config);
        verify(player).sendMessage("Du hast keine Berechtigung für diesen Befehl.");
    }

    @Test
    // JDK collections do not declare the nullness contracts of captured test values.
    @SuppressWarnings("null")
    void customTextAndMissingEnglishFallbackKeepLayout() throws Exception {
        config.options().pathSeparator('/');
        config.createSection("messages");
        config.set("messages/check.title", "Custom heading");
        command.execute(player, new String[] {"check", "modifyworld.chat"}, config);
        var plain = messages(player).stream().map(component -> PlainTextComponentSerializer.plainText().serialize(Objects.requireNonNull(component))).toList();
        assertEquals(11, plain.size());
        assertEquals("Custom heading", plain.getFirst());
        assertEquals("Action: DENIED", plain.get(9));
        assertTrue(plain.getLast().startsWith("Note: "));
    }

}
