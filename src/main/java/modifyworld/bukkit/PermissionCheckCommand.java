// SPDX-License-Identifier: GPL-2.0-or-later
// Modified on 2026-10-07: localize diagnostic texts while preserving layout and colors.
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
        CheckMessages texts = new CheckMessages(config);
        if (!(sender instanceof ConsoleCommandSender) && !sender.hasPermission("modifyworld.command.check")) {
            sender.sendMessage(texts.text("error.permission"));
            return true;
        }
        if (args.length < 2 || args.length > 3 || !args[0].equalsIgnoreCase("check")) {
            usage(sender, texts);
            return true;
        }
        String node = args[args.length - 1];
        if (!node.matches("modifyworld\\.[A-Za-z0-9_]+(?:\\.[A-Za-z0-9_]+)*")
                || node.startsWith("modifyworld.command.")) {
            sender.sendMessage(texts.text("error.node"));
            return true;
        }
        Player target;
        if (args.length == 2) {
            if (!(sender instanceof Player player)) {
                usage(sender, texts);
                return true;
            }
            target = player;
        } else {
            target = sender.getServer().getPlayerExact(args[1]);
            if (target == null) {
                sender.sendMessage(texts.text("error.player"));
                return true;
            }
        }
        boolean bypass = config.getBoolean("op-bypass", false);
        PermissionDecision decision = PermissionDecision.evaluate(target, node, bypass);
        sender.sendMessage(Component.text(texts.text("title"), NamedTextColor.GOLD));
        detail(sender, texts.text("label.player"), target.getName());
        detail(sender, texts.text("label.op"), texts.bool(target.isOp()));
        detail(sender, texts.text("label.world"), target.getWorld().getName());
        detail(sender, texts.text("label.permission"), node);
        detail(sender, texts.text("label.bypass"), texts.bool(bypass));
        detail(sender, texts.text("label.bukkit-result"), texts.bool(target.hasPermission(node)));
        detail(sender, texts.text("label.assigned"), texts.text(target.isPermissionSet(node) ? "value.yes" : "value.no"));
        detail(sender, texts.text("label.reason"), texts.reason(decision));
        sender.sendMessage(Component.text(texts.text("label.action"), NamedTextColor.GRAY)
                .append(Component.text(texts.text(decision.allowed() ? "value.allowed" : "value.denied"),
                        decision.allowed() ? NamedTextColor.GREEN : NamedTextColor.RED)
                        .decorate(TextDecoration.BOLD)));
        sender.sendMessage(Component.text(
                texts.text("note"),
                NamedTextColor.YELLOW));
        return true;
    }

    private void detail(CommandSender sender, String label, String value) {
        sender.sendMessage(Component.text(label, NamedTextColor.GRAY)
                .append(Component.text(value, NamedTextColor.AQUA)));
    }

    private void usage(CommandSender sender, CheckMessages texts) {
        sender.sendMessage(texts.text("usage.self"));
        sender.sendMessage(texts.text("usage.target"));
    }
}
