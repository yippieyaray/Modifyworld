// Added on 2026-09-28. Licensed under GPL-2.0-or-later.
package modifyworld.handlers;

import modifyworld.ModifyworldListener;
import modifyworld.PlayerInformer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BundleMeta;
import org.bukkit.plugin.Plugin;

/** Checks transfers across the top inventory boundary without changing inventories. */
public class ContainerListener extends ModifyworldListener {
    public ContainerListener(Plugin plugin, ConfigurationSection config, PlayerInformer informer) {
        super(plugin, config, informer);
    }

    private boolean isContainer(Inventory inventory) {
        return switch (inventory.getType()) {
            case CRAFTING, CREATIVE, PLAYER -> false;
            default -> true;
        };
    }

    private boolean present(ItemStack item) {
        return item != null && !item.getType().isAir();
    }

    private boolean denied(Player player, Inventory inventory, String action, ItemStack item) {
        return present(item) && permissionDenied(player, "modifyworld.items", action,
                item, "of", inventory.getType());
    }

    private boolean bundleDenied(Player player, Inventory inventory, String action, ItemStack bundle) {
        if (!present(bundle) || !(bundle.getItemMeta() instanceof BundleMeta meta)) {
            return true;
        }
        // Conservatively check all contents: the selected bundle entry is client-dependent.
        return meta.getItems().stream().anyMatch(item -> denied(player, inventory, action, item));
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        Inventory top = event.getView().getTopInventory();
        if (!isContainer(top)) return;
        boolean inTop = event.getRawSlot() >= 0 && event.getRawSlot() < top.getSize();
        ItemStack current = event.getCurrentItem();
        ItemStack cursor = event.getCursor();
        boolean blocked = false;
        switch (event.getAction()) {
            case NOTHING, DROP_ALL_CURSOR, DROP_ONE_CURSOR -> { }
            case MOVE_TO_OTHER_INVENTORY -> {
                if (event.getClickedInventory() != null) {
                    blocked = denied(player, top, inTop ? "take" : "put", current);
                }
            }
            case COLLECT_TO_CURSOR -> {
                if (present(cursor)) {
                    for (ItemStack item : top.getContents()) {
                        if (present(item) && item.isSimilar(cursor) && denied(player, top, "take", item)) {
                            blocked = true;
                            break;
                        }
                    }
                }
            }
            case PICKUP_ALL, PICKUP_HALF, PICKUP_ONE, PICKUP_SOME,
                    DROP_ALL_SLOT, DROP_ONE_SLOT, CLONE_STACK,
                    PICKUP_ALL_INTO_BUNDLE, PICKUP_SOME_INTO_BUNDLE ->
                blocked = inTop && denied(player, top, "take", current);
            case PLACE_ALL, PLACE_ONE, PLACE_SOME, PLACE_ALL_INTO_BUNDLE, PLACE_SOME_INTO_BUNDLE ->
                blocked = inTop && denied(player, top, "put", cursor);
            case SWAP_WITH_CURSOR -> blocked = inTop &&
                    (denied(player, top, "take", current) || denied(player, top, "put", cursor));
            case HOTBAR_SWAP, HOTBAR_MOVE_AND_READD -> {
                if (inTop) {
                    ItemStack incoming;
                    if (event.getClick() == ClickType.SWAP_OFFHAND) {
                        incoming = player.getInventory().getItemInOffHand();
                    } else if (event.getHotbarButton() >= 0 && event.getHotbarButton() < 9) {
                        incoming = player.getInventory().getItem(event.getHotbarButton());
                    } else {
                        event.setCancelled(true);
                        return;
                    }
                    blocked = denied(player, top, "take", current) || denied(player, top, "put", incoming);
                }
            }
            case PICKUP_FROM_BUNDLE -> blocked = inTop && bundleDenied(player, top, "take", current);
            case PLACE_FROM_BUNDLE -> blocked = inTop && bundleDenied(player, top, "put", cursor);
            // Apply the opt-out policy to the items exposed by the unknown event.
            default -> blocked = unknownActionDenied(player, event);
        }
        if (blocked) event.setCancelled(true);
    }

    private boolean unknownItemDenied(Player player, ItemStack item) {
        if (!present(item)) return false;
        String permission = assemblePermission("modifyworld.items.allowunknownaction", item);
        // Unset means allowed, including for non-OP players. LuckPerms resolves
        // explicit, inherited, contextual and wildcard assignments through Bukkit.
        return player.isPermissionSet(permission)
                && permissionDenied(player, "modifyworld.items.allowunknownaction", item);
    }

    private boolean unknownActionDenied(Player player, InventoryClickEvent event) {
        if (unknownItemDenied(player, event.getCurrentItem())
                || unknownItemDenied(player, event.getCursor())) return true;
        if (event.getClick() == ClickType.SWAP_OFFHAND) {
            return unknownItemDenied(player, player.getInventory().getItemInOffHand());
        }
        if (event.getClick() == ClickType.NUMBER_KEY
                && event.getHotbarButton() >= 0 && event.getHotbarButton() < 9) {
            return unknownItemDenied(player, player.getInventory().getItem(event.getHotbarButton()));
        }
        // Future actions could affect additional items not described by this API.
        return false;
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        Inventory top = event.getView().getTopInventory();
        if (!isContainer(top)) return;
        for (int rawSlot : event.getRawSlots()) {
            if (rawSlot >= 0 && rawSlot < top.getSize()
                    && denied(player, top, "put", event.getOldCursor())) {
                event.setCancelled(true);
                return;
            }
        }
    }
}
