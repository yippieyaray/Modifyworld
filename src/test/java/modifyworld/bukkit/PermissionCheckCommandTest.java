// SPDX-License-Identifier: GPL-2.0-or-later
// Modified on 2026-10-06: verify diagnostic colors, final result order and online target setup.
// Added on 2026-10-05: cover self/console diagnostics and permission policy.
package modifyworld.bukkit;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.command.CommandSender;
import org.mockito.ArgumentCaptor;
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

    private java.util.List<Component> messages(CommandSender sender) {
        var captor = ArgumentCaptor.forClass(Component.class);
        verify(sender, atLeastOnce()).sendMessage(captor.capture());
        return captor.getAllValues();
    }

    private Component actionMessage(CommandSender sender) {
        var output = messages(sender);
        return output.get(output.size() - 2);
    }

    @BeforeEach
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
    void selfCheckReportsActualPolicy(boolean op, boolean bypass, boolean assigned, boolean granted, String result) {
        String node = "modifyworld.blocks.place.tnt";
        config.set("op-bypass", bypass);
        when(player.isOp()).thenReturn(op);
        when(player.isPermissionSet(node)).thenReturn(assigned);
        when(player.hasPermission(node)).thenReturn(granted);
        command.execute(player, new String[] {"check", node}, config);
        var messages = messages(player);
        var plain = messages.stream().map(PlainTextComponentSerializer.plainText()::serialize).toList();
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
}
