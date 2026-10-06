// SPDX-License-Identifier: GPL-2.0-or-later
// Modified on 2026-10-06: clarify diagnostic labels and place the explanatory note last.
// Added on 2026-10-05: explain Modifyworld decisions for online players.
package modifyworld.bukkit;

import modifyworld.PermissionDecision;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

public final class PermissionCheckCommand {
    public boolean execute(CommandSender sender, String[] args, ConfigurationSection config) {
        if (!(sender instanceof ConsoleCommandSender) && !sender.hasPermission("modifyworld.command.check")) {
            sender.sendMessage("You do not have permission to use this command.");
            return true;
        }
        if (args.length < 2 || args.length > 3 || !args[0].equalsIgnoreCase("check")) {
            usage(sender);
            return true;
        }
        String node = args[args.length - 1];
        if (!node.matches("modifyworld\\.[A-Za-z0-9_]+(?:\\.[A-Za-z0-9_]+)*")
                || node.startsWith("modifyworld.command.")) {
            sender.sendMessage("Specify a concrete Modifyworld action permission, without wildcards.");
            return true;
        }
        Player target;
        if (args.length == 2) {
            if (!(sender instanceof Player player)) {
                usage(sender);
                return true;
            }
            target = player;
        } else {
            target = sender.getServer().getPlayerExact(args[1]);
            if (target == null) {
                sender.sendMessage("Player not found. The target must be online.");
                return true;
            }
        }
        boolean bypass = config.getBoolean("op-bypass", false);
        PermissionDecision decision = PermissionDecision.evaluate(target, node, bypass);
        sender.sendMessage(Component.text("[Modifyworld] Permission check", NamedTextColor.GOLD));
        detail(sender, "Player: ", target.getName());
        detail(sender, "Player is OP: ", Boolean.toString(target.isOp()));
        detail(sender, "World: ", target.getWorld().getName());
        detail(sender, "Permission: ", node);
        detail(sender, "Modifyworld OP bypass: ", Boolean.toString(bypass));
        detail(sender, "Bukkit permission result: ", Boolean.toString(target.hasPermission(node)));
        detail(sender, "Permission explicitly assigned or inherited: ", target.isPermissionSet(node) ? "yes" : "no");
        detail(sender, "Reason: ", decision.reason());
        sender.sendMessage(Component.text("Action: ", NamedTextColor.GRAY)
                .append(Component.text(decision.allowed() ? "ALLOWED" : "DENIED",
                        decision.allowed() ? NamedTextColor.GREEN : NamedTextColor.RED)
                        .decorate(TextDecoration.BOLD)));
        sender.sendMessage(Component.text(
                "Note: This checks Modifyworld permissions only. Other rules or plugins may still block the action.",
                NamedTextColor.YELLOW));
        return true;
    }

    private void detail(CommandSender sender, String label, String value) {
        sender.sendMessage(Component.text(label, NamedTextColor.GRAY)
                .append(Component.text(value, NamedTextColor.AQUA)));
    }

    private void usage(CommandSender sender) {
        sender.sendMessage("Usage: /modifyworld check <permission> (in-game)");
        sender.sendMessage("Usage: /modifyworld check <player> <permission> (online target)");
    }
}
