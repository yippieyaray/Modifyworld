// SPDX-License-Identifier: GPL-2.0-or-later
// Modified on 2026-09-30: clarify comments and current Paper plugin description.
// Modified or added for the Paper port on 2026-09-28 and 2026-09-29; see NOTICE.
// Origin: t3hk0d3, upstream commit 46464c38 (2012-06-07); attribution added by this fork.
// Modified on 2026-09-28: move to the neutral modifyworld namespace.
package modifyworld;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.ComplexEntityPart;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class PlayerInformer {

	public final static String PERMISSION_DENIED = "Sorry, you don't have enough permissions";
	public final static String DEFAULT_MESSAGE_FORMAT = "&f[&2Modifyworld&f]&4 %s";
	// Default message format
	protected String messageFormat = DEFAULT_MESSAGE_FORMAT;
	protected Map<String, String> messages = new HashMap<String, String>();
	// Flags
	protected boolean enabled = false;
	protected boolean individualMessages = false;
	protected String defaultMessage = PERMISSION_DENIED;

	public PlayerInformer(ConfigurationSection config) {
		this.enabled = config.getBoolean("inform-players", enabled);

		this.loadConfig(config.getConfigurationSection("messages"));
	}

	private void loadConfig(ConfigurationSection config) {
		if (config == null) throw new IllegalArgumentException("Missing messages configuration");

		this.defaultMessage = config.getString("default-message", this.defaultMessage);
		this.messageFormat = config.getString("message-format", this.messageFormat);
		this.individualMessages = config.getBoolean("individual-messages", this.individualMessages);

		this.importMessages(config);

		for (String permission : config.getKeys(true)) {
			if (!config.isString(permission)) {
				continue;
			}

			setMessage(permission, config.getString(permission.replace("/", ".")));
		}
	}

	public void setMessage(String permission, String message) {
		messages.put(permission, message);
	}

	public String getMessage(String permission) {
		if (messages.containsKey(permission)) {
			return messages.get(permission);
		}

		String perm = permission;
		int index;

		while ((index = perm.lastIndexOf(".")) != -1) {
			perm = perm.substring(0, index);

			if (messages.containsKey(perm)) {
				String message = messages.get(perm);
				messages.put(permission, message);
				return message;
			}
		}

		return this.defaultMessage;
	}

	public String getMessage(Player player, String permission) {
		// Messages use the selected language file and bundled fallbacks.
		return getMessage(permission);
	}

	public void informPlayer(Player player, String permission, Object... args) {
		if (!enabled) {
			return;
		}

        String message = formatMessage(permission, args);

		if (message != null && !message.isEmpty()) {
			player.sendMessage(String.format(messageFormat, message).replaceAll("&([a-z0-9])", "\u00A7$1"));
		}
	}

    /** Resolve placeholders once, so replacement text cannot introduce new placeholders. */
    public String formatMessage(String permission, Object... args) {
        String template = getMessage(permission);
        if (template == null) template = PERMISSION_DENIED;
        var matcher = java.util.regex.Pattern.compile("\\$(permission|[1-9][0-9]*)").matcher(template);
        return matcher.replaceAll(match -> {
            String token = match.group(1);
            String replacement = match.group();
            if (token.equals("permission")) replacement = permission;
            else {
                try {
                    int index = Integer.parseInt(token) - 1;
                    if (index < args.length) replacement = describeObject(args[index]);
                } catch (NumberFormatException ignored) {
                    // Unknown placeholder numbers remain visible for configuration diagnosis.
                }
            }
            return java.util.regex.Matcher.quoteReplacement(replacement);
        });
    }

	protected String describeObject(Object obj) {
		if (obj == null) return "air";
		if (obj instanceof ComplexEntityPart) { // Complex entities
			return describeObject(((ComplexEntityPart) obj).getParent());
		} else if (obj instanceof Item) { // Dropped items
			return describeMaterial(((Item) obj).getItemStack().getType());
		} else if (obj instanceof ItemStack) { // Items
			return describeMaterial(((ItemStack) obj).getType());
		} else if (obj instanceof Entity) { // Entities
			return ((Entity) obj).getType().toString().toLowerCase(Locale.ROOT).replace("_", " ");
		} else if (obj instanceof Block) { // Blocks
			return describeMaterial(((Block) obj).getType());
		} else if (obj instanceof Enum<?>) {
            return ((Enum<?>) obj).name().toLowerCase(Locale.ROOT).replace("_", " ");
		}

		return obj.toString();
	}

	private String describeMaterial(Material material) {
		return material.toString().toLowerCase(Locale.ROOT).replace("_", " ");
	}

	// Import supported message aliases into permission-based message keys.
	private void importMessages(ConfigurationSection config) {
		if (config.isString("whitelistMessage")) {
			setMessage("modifyworld.login", config.getString("whitelistMessage"));
			config.set("whitelistMessage", null);
		}

		if (config.isString("prohibitedItem")) {
			setMessage("modifyworld.items.have", config.getString("prohibitedItem"));
			config.set("prohibitedItem", null);
		}

		if (config.isString("permissionDenied")) {
			setMessage("modifyworld", config.getString("permissionDenied"));
			config.set("permissionDenied", null);
		}
	}
}
