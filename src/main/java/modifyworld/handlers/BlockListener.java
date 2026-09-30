// SPDX-License-Identifier: GPL-2.0-or-later
// Modified on 2026-09-30: clarify comments and current Paper plugin description.
// Modified or added for the Paper port on 2026-09-28 and 2026-09-29; see NOTICE.
// Modified on 2026-09-28: cover player ignition and bucket transfers through cauldrons.
// Modified on 2026-09-28: move to the neutral modifyworld namespace.
/*
 * Modifyworld - Permission rules for Paper
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

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.CauldronLevelChangeEvent;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockMultiPlaceEvent;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.hanging.HangingPlaceEvent;
import org.bukkit.plugin.Plugin;
import modifyworld.ModifyworldListener;
import modifyworld.PlayerInformer;

/**
 *
 * @author t3hk0d3
 */
public class BlockListener extends ModifyworldListener {

	public BlockListener(Plugin plugin, ConfigurationSection config, PlayerInformer informer) {
		super(plugin, config, informer);
	}

	@EventHandler(priority = EventPriority.LOW)
	public void onBlockBreak(BlockBreakEvent event) {
		if (permissionDenied(event.getPlayer(), "modifyworld.blocks.destroy", event.getBlock())) {
			event.setCancelled(true);
		}
	}

	@EventHandler(priority = EventPriority.LOW)
	public void onBlockPlace(BlockPlaceEvent event) {
		if (event instanceof BlockMultiPlaceEvent multi) {
			for (org.bukkit.block.BlockState replaced : multi.getReplacedBlockStates()) {
				if (permissionDenied(event.getPlayer(), "modifyworld.blocks.place", replaced.getBlock())) {
					event.setCancelled(true);
					return;
				}
			}
			return;
		}
		if (permissionDenied(event.getPlayer(), "modifyworld.blocks.place", event.getBlock())) {
			event.setCancelled(true);
		}
	}

	@EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
	public void onBlockIgnite(BlockIgniteEvent event) {
		// The legacy fire-placement rule covers player ignition, including soul fire.
		// Natural spread and automation have no player permission context.
		Player player = event.getPlayer();
		if (player != null && permissionDenied(player, "modifyworld.blocks.place", Material.FIRE)) {
			event.setCancelled(true);
		}
	}

	@EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
	public void onCauldronBucket(CauldronLevelChangeEvent event) {
		if (!(event.getEntity() instanceof Player player)) {
			return;
		}
		String action;
		Material cauldron;
		switch (event.getReason()) {
			case BUCKET_EMPTY -> {
				action = "empty";
				cauldron = event.getNewState().getType();
			}
			case BUCKET_FILL -> {
				action = "fill";
				cauldron = event.getBlock().getType();
			}
			default -> { return; }
		}
		String content = switch (cauldron) {
			case LAVA_CAULDRON -> "lava";
			case WATER_CAULDRON -> "water";
			case POWDER_SNOW_CAULDRON -> "powdersnow";
			default -> null;
		};
		if (content != null && permissionDenied(player, "modifyworld.bucket." + action, content)) {
			event.setCancelled(true);
		}
	}

	@EventHandler(priority = EventPriority.LOW)
	public void onHangingBreakByEntity(HangingBreakByEntityEvent event) {
		if (event.getRemover() instanceof Player
				&& permissionDenied((Player) event.getRemover(), "modifyworld.blocks.destroy", event.getEntity().getType())) {
			event.setCancelled(true);
		}
	}

	@EventHandler(priority = EventPriority.LOW)
	public void onPaintingPlace(HangingPlaceEvent event) {
		if (event.getPlayer() != null && permissionDenied(event.getPlayer(), "modifyworld.blocks.place", event.getEntity().getType())) {
			event.setCancelled(true);
		}
	}
}
