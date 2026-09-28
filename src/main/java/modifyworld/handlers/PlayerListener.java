// Modified on 2026-09-28: delegate container transfers to ContainerListener.
// Modified on 2026-09-28: move to the neutral modifyworld namespace.
/*
 * Modifyworld - PermissionsEx ruleset plugin for Bukkit
 * Copyright (C) 2011 t3hk0d3 http://www.tehkode.ru
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package modifyworld.handlers;

import java.util.logging.Logger;
import java.util.Locale;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.Event.Result;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.*;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.SpawnEggMeta;
import org.bukkit.entity.EntityType;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Vector;
import modifyworld.ModifyworldListener;
import modifyworld.PlayerInformer;

/**
 * @author t3hk0d3
 */
public class PlayerListener extends ModifyworldListener {

	protected boolean checkInventory = false;
	protected boolean dropRestrictedItem = false;

	public PlayerListener(Plugin plugin, ConfigurationSection config, PlayerInformer informer) {
		super(plugin, config, informer);

		this.checkInventory = config.getBoolean("item-restrictions", this.checkInventory);
		this.dropRestrictedItem = config.getBoolean("drop-restricted-item", this.dropRestrictedItem);

	}

	@EventHandler(priority = EventPriority.LOW)
	public void onPlayerSneak(PlayerToggleSneakEvent event) {
		Player player = event.getPlayer();

		if (event.isSneaking() && _permissionDenied(player, "modifyworld.sneak")) {
			event.setCancelled(true);
			event.getPlayer().setSneaking(false);
		}
	}

	@EventHandler(priority = EventPriority.LOW)
	public void onPlayerSprint(PlayerToggleSprintEvent event) {
		Player player = event.getPlayer();

		if (event.isSprinting() && _permissionDenied(player, "modifyworld.sprint")) {
			event.setCancelled(true);
			event.getPlayer().setSprinting(false);
		}
	}


	@EventHandler(priority = EventPriority.LOW)
	public void onPlayerLogin(PlayerLoginEvent event) {
		if (!enableWhitelist) {
			return;
		}

		Player player = event.getPlayer();

		if (_permissionDenied(player, "modifyworld.login")) {
			// String whiteListMessage = user.getOption("kick-message", worldName, this.whitelistKickMessage);
			event.disallow(PlayerLoginEvent.Result.KICK_WHITELIST, informer.getMessage(player, "modifyworld.login"));
			Logger.getLogger("Minecraft").info("Player \"" + player.getName() + "\" were kicked by Modifyworld - lack of 'modifyworld.login' permission");
		}
	}

	@EventHandler(priority = EventPriority.LOW)
	public void onPlayerBedEnter(PlayerBedEnterEvent event) {
		if (permissionDenied(event.getPlayer(), "modifyworld.usebeds")) {
			event.setCancelled(true);
		}
	}

	@EventHandler(priority = EventPriority.LOW)
	public void onPlayerBucketEmpty(PlayerBucketEmptyEvent event) {
		String bucketName = bucketContent(event.getBucket());
		if (permissionDenied(event.getPlayer(), "modifyworld.bucket.empty", bucketName)) {
			event.setCancelled(true);
		}
	}

	@EventHandler(priority = EventPriority.LOW)
	public void onPlayerBucketFill(PlayerBucketFillEvent event) {
		// The resulting bucket identifies the collected content, not the clicked block.
		ItemStack result = event.getItemStack();
		String content = result == null ? event.getBlock().getType().name().toLowerCase(Locale.ROOT)
				: bucketContent(result.getType());
		if (permissionDenied(event.getPlayer(), "modifyworld.bucket.fill", content)) {
			event.setCancelled(true);
		}
	}

	private static String bucketContent(Material bucket) {
		return bucket.name().toLowerCase(Locale.ROOT).replace("_bucket", "").replace("_", "");
	}

	@EventHandler(priority = EventPriority.LOW)
	public void onPlayerCommandPreprocess(PlayerCommandPreprocessEvent event) {
		if (event.getMessage().startsWith("/tell") && permissionDenied(event.getPlayer(), "modifyworld.chat.private")) {
			event.setCancelled(true);
		}
	}

	@EventHandler(priority = EventPriority.LOW)
	public void onPlayerChat(PlayerChatEvent event) {
		if (permissionDenied(event.getPlayer(), "modifyworld.chat")) {
			event.setCancelled(true);
		}
	}

	@EventHandler(priority = EventPriority.LOW)
	public void onPlayerPickupItem(EntityPickupItemEvent event) {
		if (!(event.getEntity() instanceof Player player)) {
			return;
		}
		// No inform to avoid spam
		if (_permissionDenied(player, "modifyworld.items.pickup", event.getItem().getItemStack())) {
			event.setCancelled(true);
		}

		this.checkPlayerInventory(player);
	}

	@EventHandler(priority = EventPriority.LOW)
	public void onPlayerDropItem(PlayerDropItemEvent event) {
		if (permissionDenied(event.getPlayer(), "modifyworld.items.drop", event.getItemDrop().getItemStack())) {
			event.setCancelled(true);
		}

		this.checkPlayerInventory(event.getPlayer());
	}

	@EventHandler(priority = EventPriority.LOW)
	public void onItemHeldChange(PlayerItemHeldEvent event) {
		Player player = event.getPlayer();
		ItemStack item = player.getInventory().getItem(event.getNewSlot());

		if (item != null && item.getType() != Material.AIR &&
				permissionDenied(player, "modifyworld.items.hold", item)) {
			int freeSlot = getFreeSlot(player.getInventory());

			if (freeSlot != 0) {
				player.getInventory().setItem(freeSlot, item);
			} else {
				player.getWorld().dropItemNaturally(player.getLocation(), item);
			}

			player.getInventory().setItem(event.getNewSlot(), new ItemStack(Material.AIR));
		}

		this.checkPlayerInventory(player);
	}

	@EventHandler(priority = EventPriority.LOW)
	public void onPlayerInventoryEvent(InventoryClickEvent event) {
		ItemStack item = event.getCursor();

		if (item == null || item.getType() == Material.AIR || event.getSlotType() != InventoryType.SlotType.QUICKBAR) {
			return;
		}

		Player player = (Player) event.getWhoClicked();

		int targetSlot = player.getInventory().getHeldItemSlot();

		if (event.getSlot() == targetSlot && permissionDenied(player, "modifyworld.items.hold", item)) {
			event.setCancelled(true);
		}
	}

	@EventHandler(priority = EventPriority.LOW)
	public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
		if (this.checkItemUse) {
			if (permissionDenied(event.getPlayer(), "modifyworld.items.use", event.getPlayer().getInventory().getItem(event.getHand()), "on.entity", event.getRightClicked())) {
				event.setCancelled(true);
			}

			return;
		}

		if (!event.isCancelled() && permissionDenied(event.getPlayer(), "modifyworld.interact", event.getRightClicked())) {
			event.setCancelled(true);
		}
	}

	@EventHandler(priority = EventPriority.LOW)
	public void onPlayerInteract(PlayerInteractEvent event) {
		Action action = event.getAction();

		if (action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK) { // item restriction check
			this.checkPlayerInventory(event.getPlayer());
		}

		Player player = event.getPlayer();

		ItemStack held = event.getItem();
		Material heldMaterial = held == null ? Material.AIR : held.getType();
		if (action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK) {
			switch (heldMaterial) {
				case SPLASH_POTION, LINGERING_POTION, EGG, SNOWBALL, EXPERIENCE_BOTTLE -> {
					if (permissionDenied(player, "modifyworld.items.throw", held)) {
						event.setUseItemInHand(Result.DENY);
					}
					return;
				}
				default -> { }
			}
			if (heldMaterial.name().endsWith("_SPAWN_EGG")) {
				SpawnEggMeta meta = (SpawnEggMeta) held.getItemMeta();
				EntityType type = meta.getCustomSpawnedType();
				if (type == null) {
					type = EntityType.valueOf(held.getType().name().replace("_SPAWN_EGG", ""));
				}
				if (permissionDenied(player, "modifyworld.spawn", type)) {
					event.setUseItemInHand(Result.DENY);
				}
				return;
			}
		}

		if (action != Action.LEFT_CLICK_BLOCK && action != Action.RIGHT_CLICK_BLOCK && action != Action.PHYSICAL) {
			return;
		}

		if (this.checkItemUse && action != Action.PHYSICAL) {
			if (permissionDenied(event.getPlayer(), "modifyworld.items.use", held == null ? Material.AIR : held, "on.block", event.getClickedBlock())) {
				event.setCancelled(true);
			}

			return;
		}

		if (!event.isCancelled() && permissionDenied(player, "modifyworld.blocks.interact", event.getClickedBlock())) {
			event.setCancelled(true);
		}
	}

	@EventHandler(priority = EventPriority.LOW)
	public void onItemEnchant(EnchantItemEvent event) {
		if (permissionDenied(event.getEnchanter(), "modifyworld.items.enchant", event.getItem())) {
			event.setCancelled(true);
		}
	}

	@EventHandler(priority = EventPriority.LOW)
	public void onItemCraft(CraftItemEvent event) {
		Player player = (Player) event.getWhoClicked();

		if (permissionDenied(player, "modifyworld.items.craft", event.getRecipe().getResult())) {
			event.setCancelled(true);
		}
	}

	@EventHandler(priority = EventPriority.LOW)
	public void onFoodLevelChange(FoodLevelChangeEvent event) {
		Player player = event.getEntity() instanceof Player ? (Player) event.getEntity() : null;

		if (player == null) {
			return;
		}

		if (_permissionDenied(player, "modifyworld.digestion")) {
			event.setCancelled(true);
		}
	}

	protected void checkPlayerInventory(Player player) {
		if (!checkInventory) {
			return;
		}

		Inventory inventory = player.getInventory();
		for (ItemStack stack : inventory.getContents()) {
			if (stack != null && permissionDenied(player, "modifyworld.items.have", stack)) {
				inventory.remove(stack);

				if (this.dropRestrictedItem) {
					player.getWorld().dropItemNaturally(player.getLocation(), stack);
				}
			}
		}
	}

	private int getFreeSlot(Inventory inventory) {
		for (int i = 9; i <= 35; i++) {
			if (inventory.getItem(i) == null || inventory.getItem(i).getType() == Material.AIR) {
				return i;
			}
		}

		return 0;
	}
}
