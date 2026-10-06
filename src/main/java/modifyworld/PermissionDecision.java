// SPDX-License-Identifier: GPL-2.0-or-later
// Added on 2026-10-05: shared action and diagnostic permission decisions.
package modifyworld;

import org.bukkit.entity.Player;

public enum PermissionDecision {
    BYPASS(true, "OP bypass is enabled."),
    UNKNOWN_DEFAULT(true, "Unassigned unknown inventory actions default to allowed."),
    OP_FALLBACK(false, "Implicit OP permission is ignored while op-bypass is false."),
    GRANTED(true, "The effective Bukkit permission check allows this action."),
    DENIED(false, "The effective Bukkit permission check denies this action.");

    private final boolean allowed;
    private final String reason;

    PermissionDecision(boolean allowed, String reason) {
        this.allowed = allowed;
        this.reason = reason;
    }

    public boolean allowed() { return allowed; }
    public String reason() { return reason; }

    public static PermissionDecision evaluate(Player player, String permission, boolean bypass) {
        boolean operator = player.isOp();
        if (operator && bypass) return BYPASS;
        boolean unknown = permission.startsWith("modifyworld.items.allowunknownaction.");
        if (unknown || operator) {
            boolean assigned = player.isPermissionSet(permission);
            if (!assigned) return unknown ? UNKNOWN_DEFAULT : OP_FALLBACK;
        }
        return player.hasPermission(permission) ? GRANTED : DENIED;
    }
}
