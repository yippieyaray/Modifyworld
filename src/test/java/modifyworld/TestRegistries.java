// SPDX-License-Identifier: GPL-2.0-or-later
// Modified on 2026-10-08: clarify nullness at test API boundaries.
// Modified or added for the Paper port on 2026-09-28 and 2026-09-29; see NOTICE.
package modifyworld;

import org.bukkit.event.inventory.InventoryType;
import io.papermc.paper.registry.RegistryAccess;
import org.jspecify.annotations.NonNull;
import static org.mockito.Mockito.*;

/** Minimal registry bootstrap for tests that do not use server-backed menus. */
final class TestRegistries {
    private TestRegistries() { }

    // Mockito mock factories and stubbing APIs lack null annotations.
    @SuppressWarnings("null")
    static void initializeInventoryTypes() {
        // These unit tests need inventory type names, not server-backed menu registries.
        RegistryAccess access = mock(
                RegistryAccess.class,
                invocation -> mock(org.bukkit.Registry.class));
        try (org.mockito.MockedStatic<@NonNull RegistryAccess> registry =
                mockStatic(RegistryAccess.class,
                        withSettings().mockMaker(org.mockito.MockMakers.INLINE))) {
            registry.when(RegistryAccess::registryAccess).thenReturn(access);
            InventoryType.values();
        }
    }
}
