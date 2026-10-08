// SPDX-License-Identifier: GPL-2.0-or-later
// Added on 2026-10-07: resolve single-line diagnostic texts with English fallback.
package modifyworld.bukkit;

import java.io.IOException;
import java.util.Locale;
import java.util.regex.Pattern;
import modifyworld.PermissionDecision;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

final class CheckMessages {
    private static final Pattern LINE_BREAK = Pattern.compile("\\R");
    private static final YamlConfiguration ENGLISH = english();
    private final ConfigurationSection messages;

    CheckMessages(ConfigurationSection config) {
        messages = config.getConfigurationSection("messages");
    }

    String text(String suffix) {
        String key = "check." + suffix;
        String fallback = ENGLISH.getString(key);
        if (fallback == null) throw new IllegalArgumentException("Unknown diagnostic message: " + key);
        return messages == null ? fallback : messages.getString(key, fallback);
    }

    String bool(boolean value) {
        return text(value ? "value.true" : "value.false");
    }

    String reason(PermissionDecision decision) {
        return text("reason." + decision.name().toLowerCase(Locale.ROOT).replace('_', '-'));
    }

    static void validate(String key, Object value) throws InvalidConfigurationException {
        if (key.startsWith("check.") && (!(value instanceof String text) || LINE_BREAK.matcher(text).find())) {
            throw new InvalidConfigurationException(key + " must be single-line text");
        }
    }

    private static YamlConfiguration english() {
        try {
            return LanguageFiles.bundled("en");
        } catch (IOException | InvalidConfigurationException failure) {
            throw new IllegalStateException("Cannot load English diagnostic messages", failure);
        }
    }
}
