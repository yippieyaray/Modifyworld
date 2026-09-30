// SPDX-License-Identifier: GPL-2.0-or-later
// Modified on 2026-09-30: clarify comments and current Paper plugin description.
// Modified or added for the Paper port on 2026-09-28 and 2026-09-29; see NOTICE.
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

package modifyworld;

import java.util.LinkedHashMap;
import java.util.Map;
import org.bukkit.entity.*;


public enum EntityCategory {
	PLAYER("player", Player.class),
	ITEM("item", Item.class),
	ANIMAL("animal", Animals.class, Squid.class),
	MONSTER("monster", Monster.class, Slime.class, EnderDragon.class, Ghast.class ),
	NPC("npc", NPC.class),
	PROJECTILE("projectile", Projectile.class);
	
	private String name;
	private Class<? extends Entity> classes[];
	
	private final static Map<Class<? extends Entity>, EntityCategory> map = new LinkedHashMap<Class<? extends Entity>, EntityCategory>();
	
	static {
		for (EntityCategory cat : EntityCategory.values()) {
			for (Class<? extends Entity> catClass : cat.getClasses()) {
				map.put(catClass, cat);
			}
		}
	}
	
	@SafeVarargs
	private EntityCategory(String name, Class<? extends Entity>... classes) {
		this.name = name;
		this.classes = classes;
	}
	
	public String getName() {
		return this.name;
	}
	
	public String getNameDot() {
		return this.getName() + ".";
	}
	
	public Class<? extends Entity>[] getClasses() {
		return this.classes;
	}
	
	public static EntityCategory fromEntity(Entity entity) {
		for (Class<? extends Entity> entityClass : map.keySet()) {
			if (entityClass.isAssignableFrom(entity.getClass())) {
				return map.get(entityClass);
			}
		}
		
		return null;
	}
	
}
