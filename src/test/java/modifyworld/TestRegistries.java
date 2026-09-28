package modifyworld;

import org.bukkit.event.inventory.InventoryType;
import static org.mockito.Mockito.*;

/** Minimal registry bootstrap for tests that do not use server-backed menus. */
final class TestRegistries {
    private TestRegistries() { }

    static void initializeInventoryTypes() {
        // These unit tests need inventory type names, not server-backed menu registries.
        io.papermc.paper.registry.RegistryAccess access = mock(
                io.papermc.paper.registry.RegistryAccess.class,
                invocation -> mock(org.bukkit.Registry.class));
        try (org.mockito.MockedStatic<io.papermc.paper.registry.RegistryAccess> registry =
                mockStatic(io.papermc.paper.registry.RegistryAccess.class,
                        withSettings().mockMaker(org.mockito.MockMakers.INLINE))) {
            registry.when(io.papermc.paper.registry.RegistryAccess::registryAccess).thenReturn(access);
            InventoryType.values();
        }
    }
}
