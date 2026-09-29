package modifyworld;

import modifyworld.handlers.ContainerListener;
import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.*;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import java.util.Objects;
import java.util.Set;
import static org.mockito.Mockito.*;

class ContainerListenerTest {
    @org.junit.jupiter.api.BeforeAll
    static void initializeInventoryTypes() {
        TestRegistries.initializeInventoryTypes();
    }

    private ContainerListener listener;
    private Player player;
    private Inventory top;
    private PlayerInventory bottom;
    private InventoryView view;
    private ItemStack tnt;

    @BeforeEach
    void setup() {
        Plugin plugin = mock(Plugin.class);
        Server server = mock(Server.class);
        when(plugin.getServer()).thenReturn(server);
        when(server.getPluginManager()).thenReturn(mock(PluginManager.class));
        listener = new ContainerListener(plugin, new YamlConfiguration(), mock(PlayerInformer.class));
        player = mock(Player.class);
        top = mock(Inventory.class);
        bottom = mock(PlayerInventory.class);
        view = mock(InventoryView.class);
        when(view.getTopInventory()).thenReturn(top);
        when(top.getSize()).thenReturn(27);
        when(top.getType()).thenReturn(InventoryType.CHEST);
        when(player.getInventory()).thenReturn(Objects.requireNonNull(bottom));
        when(player.hasPermission(anyString())).thenReturn(true);
        tnt = mock(ItemStack.class);
        when(tnt.getType()).thenReturn(Material.TNT);
    }

    private InventoryClickEvent click(InventoryAction action, int slot) {
        // Mockito returns a mock here, but its generic factory lacks JDT null annotations.
        @SuppressWarnings("null")
        InventoryClickEvent event = mock(InventoryClickEvent.class);
        when(event.getWhoClicked()).thenReturn(player);
        when(event.getView()).thenReturn(view);
        when(event.getAction()).thenReturn(action);
        when(event.getRawSlot()).thenReturn(slot);
        when(event.getClickedInventory()).thenReturn(slot < 27 ? top : bottom);
        return event;
    }

    @ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({
        "TNT,tnt,take", "LAVA_BUCKET,lava bucket,take", "FLINT_AND_STEEL,flint and steel,take",
        "TNT,tnt,put", "LAVA_BUCKET,lava bucket,put", "FLINT_AND_STEEL,flint and steel,put"
    })
    void transferMessagesDescribeTheItemInsteadOfTheAction(Material material, String description, String direction) {
        YamlConfiguration config = new YamlConfiguration();
        config.options().pathSeparator('/');
        config.set("inform-players", true);
        config.set("messages/message-format", "%s");
        config.set("messages/modifyworld.items." + direction, "Item: $1; container: $3; permission: $permission");
        listener = new ContainerListener(mock(Plugin.class), config, new PlayerInformer(config));
        ItemStack item = mock(ItemStack.class);
        when(item.getType()).thenReturn(material);
        String permission = "modifyworld.items." + direction + "."
                + material.name().toLowerCase(java.util.Locale.ROOT).replace("_", "") + ".of.chest";
        when(player.hasPermission(permission)).thenReturn(false);
        InventoryClickEvent event = click(direction.equals("take") ? InventoryAction.PICKUP_ALL : InventoryAction.PLACE_ALL, 0);
        if (direction.equals("take")) when(event.getCurrentItem()).thenReturn(item);
        else when(event.getCursor()).thenReturn(item);
        listener.onClick(event);
        verify(event).setCancelled(true);
        verify(player).hasPermission(permission);
        verify(player).sendMessage("Item: " + description + "; container: chest; permission: " + permission);
    }

    @ParameterizedTest
    @EnumSource(value = InventoryAction.class, names = {"PICKUP_ALL", "PICKUP_HALF", "PICKUP_ONE", "PICKUP_SOME", "DROP_ALL_SLOT", "DROP_ONE_SLOT", "CLONE_STACK", "PICKUP_ALL_INTO_BUNDLE", "PICKUP_SOME_INTO_BUNDLE"})
    void takingRequiresTakePermission(InventoryAction action) {
        InventoryClickEvent event = click(action, 0);
        when(event.getCurrentItem()).thenReturn(tnt);
        when(player.hasPermission("modifyworld.items.take.tnt.of.chest")).thenReturn(false);
        listener.onClick(event);
        verify(event).setCancelled(true);
    }

    @ParameterizedTest
    @EnumSource(value = InventoryAction.class, names = {"PLACE_ALL", "PLACE_ONE", "PLACE_SOME", "PLACE_ALL_INTO_BUNDLE", "PLACE_SOME_INTO_BUNDLE"})
    void placingRequiresPutPermission(InventoryAction action) {
        InventoryClickEvent event = click(action, 0);
        when(event.getCursor()).thenReturn(tnt);
        when(player.hasPermission("modifyworld.items.put.tnt.of.chest")).thenReturn(false);
        listener.onClick(event);
        verify(event).setCancelled(true);
    }

    @Test
    void shiftFromBottomChecksPutAndShiftFromTopChecksTake() {
        for (int slot : new int[] {0, 30}) {
            InventoryClickEvent event = click(InventoryAction.MOVE_TO_OTHER_INVENTORY, slot);
            when(event.getCurrentItem()).thenReturn(tnt);
            when(player.hasPermission("modifyworld.items." + (slot == 0 ? "take" : "put") + ".tnt.of.chest")).thenReturn(false);
            listener.onClick(event);
            verify(event).setCancelled(true);
        }
    }

    @Test
    void swapChecksIncomingItemEvenWhenTakingIsAllowed() {
        InventoryClickEvent event = click(InventoryAction.SWAP_WITH_CURSOR, 0);
        when(event.getCurrentItem()).thenReturn(tnt);
        when(event.getCursor()).thenReturn(tnt);
        when(player.hasPermission("modifyworld.items.put.tnt.of.chest")).thenReturn(false);
        listener.onClick(event);
        verify(player).hasPermission("modifyworld.items.take.tnt.of.chest");
        verify(event).setCancelled(true);
    }

    @ParameterizedTest
    @EnumSource(value = ClickType.class, names = {"NUMBER_KEY", "SWAP_OFFHAND"})
    void hotbarAndOffhandCannotInsertDeniedItem(ClickType type) {
        InventoryClickEvent event = click(InventoryAction.HOTBAR_SWAP, 0);
        when(event.getClick()).thenReturn(type);
        when(event.getHotbarButton()).thenReturn(2);
        when(bottom.getItem(2)).thenReturn(tnt);
        when(bottom.getItemInOffHand()).thenReturn(tnt);
        when(player.hasPermission("modifyworld.items.put.tnt.of.chest")).thenReturn(false);
        listener.onClick(event);
        verify(event).setCancelled(true);
    }

    @Test
    void doubleClickInBottomStillChecksTopContents() {
        InventoryClickEvent event = click(InventoryAction.COLLECT_TO_CURSOR, 30);
        when(event.getCursor()).thenReturn(tnt);
        when(top.getContents()).thenReturn(new ItemStack[] {null, tnt});
        when(tnt.isSimilar(tnt)).thenReturn(true);
        when(player.hasPermission("modifyworld.items.take.tnt.of.chest")).thenReturn(false);
        listener.onClick(event);
        verify(event).setCancelled(true);
    }

    @Test
    void dragAcrossBoundaryChecksPutButBottomOnlyDragDoesNot() {
        InventoryDragEvent event = mock(InventoryDragEvent.class);
        when(event.getWhoClicked()).thenReturn(player);
        when(event.getView()).thenReturn(view);
        when(event.getOldCursor()).thenReturn(tnt);
        when(event.getRawSlots()).thenReturn(Set.of(28, 29));
        listener.onDrag(event);
        verifyNoInteractions(tnt);
        when(player.hasPermission("modifyworld.items.put.tnt.of.chest")).thenReturn(false);
        when(event.getRawSlots()).thenReturn(Set.of(0, 28));
        listener.onDrag(event);
        verify(event).setCancelled(true);
    }

    @Test
    void allowedTransferNeverClearsAnotherPluginsCancellation() {
        InventoryClickEvent event = click(InventoryAction.PICKUP_ALL, 0);
        when(event.getCurrentItem()).thenReturn(tnt);
        when(event.isCancelled()).thenReturn(true);
        listener.onClick(event);
        verify(event, never()).setCancelled(anyBoolean());
    }

    @Test
    void bottomOnlyClickAndPersonalCraftingAreNotContainerTransfers() {
        InventoryClickEvent event = click(InventoryAction.PICKUP_ALL, 30);
        when(event.getCurrentItem()).thenReturn(tnt);
        listener.onClick(event);
        when(event.getRawSlot()).thenReturn(0);
        when(top.getType()).thenReturn(InventoryType.CRAFTING);
        listener.onClick(event);
        verify(player, never()).hasPermission(anyString());
        verify(event, never()).setCancelled(anyBoolean());
    }

    @Test
    void allowedTransferIsNotCancelled() {
        InventoryClickEvent event = click(InventoryAction.PLACE_ALL, 0);
        when(event.getCursor()).thenReturn(tnt);
        listener.onClick(event);
        verify(player).hasPermission("modifyworld.items.put.tnt.of.chest");
        verify(event, never()).setCancelled(anyBoolean());
    }

    @Test
    void unknownActionWithoutItemsIsAllowed() {
        InventoryClickEvent event = click(InventoryAction.UNKNOWN, 30);
        listener.onClick(event);
        verify(event, never()).setCancelled(anyBoolean());
    }
    @ParameterizedTest
    @EnumSource(value = InventoryAction.class, names = {"PICKUP_FROM_BUNDLE", "PLACE_FROM_BUNDLE"})
    void bundleExtractionChecksContainedMaterial(InventoryAction action) {
        ItemStack bundle = mock(ItemStack.class);
        org.bukkit.inventory.meta.BundleMeta meta = mock(org.bukkit.inventory.meta.BundleMeta.class);
        when(bundle.getType()).thenReturn(Material.BUNDLE);
        when(bundle.getItemMeta()).thenReturn(meta);
        when(meta.getItems()).thenReturn(java.util.List.of(tnt));
        InventoryClickEvent event = click(action, 0);
        when(event.getCurrentItem()).thenReturn(bundle);
        when(event.getCursor()).thenReturn(bundle);
        String direction = action == InventoryAction.PICKUP_FROM_BUNDLE ? "take" : "put";
        when(player.hasPermission("modifyworld.items." + direction + ".tnt.of.chest")).thenReturn(false);
        listener.onClick(event);
        verify(event).setCancelled(true);
    }

    @Test
    void hotbarCannotExtractDeniedItemIntoEmptySlot() {
        InventoryClickEvent event = click(InventoryAction.HOTBAR_SWAP, 0);
        when(event.getClick()).thenReturn(ClickType.NUMBER_KEY);
        when(event.getHotbarButton()).thenReturn(1);
        when(event.getCurrentItem()).thenReturn(tnt);
        when(player.hasPermission("modifyworld.items.take.tnt.of.chest")).thenReturn(false);
        listener.onClick(event);
        verify(event).setCancelled(true);
    }

    @Test
    void collectionIgnoresUnrelatedItemsInContainer() {
        InventoryClickEvent event = click(InventoryAction.COLLECT_TO_CURSOR, 30);
        when(event.getCursor()).thenReturn(tnt);
        ItemStack stone = mock(ItemStack.class);
        when(stone.getType()).thenReturn(Material.STONE);
        when(top.getContents()).thenReturn(new ItemStack[] {stone});
        listener.onClick(event);
        verify(player, never()).hasPermission(anyString());
        verify(event, never()).setCancelled(anyBoolean());
    }

    @Test
    void unknownActionDefaultsToAllowedEvenWithoutNormalPermissions() {
        InventoryClickEvent event = click(InventoryAction.UNKNOWN, 0);
        when(event.getCurrentItem()).thenReturn(tnt);
        when(player.hasPermission(anyString())).thenReturn(false);
        listener.onClick(event);
        verify(player).isPermissionSet("modifyworld.items.allowunknownaction.tnt");
        verify(event, never()).setCancelled(anyBoolean());
    }

    @ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"slot", "cursor", "hotbar", "offhand"})
    void unknownActionChecksExplicitMaterialDenial(String source) {
        InventoryClickEvent event = click(InventoryAction.UNKNOWN, 30);
        switch (source) {
            case "slot" -> when(event.getCurrentItem()).thenReturn(tnt);
            case "cursor" -> when(event.getCursor()).thenReturn(tnt);
            case "hotbar" -> {
                when(event.getClick()).thenReturn(ClickType.NUMBER_KEY);
                when(event.getHotbarButton()).thenReturn(2);
                when(bottom.getItem(2)).thenReturn(tnt);
            }
            case "offhand" -> {
                when(event.getClick()).thenReturn(ClickType.SWAP_OFFHAND);
                when(bottom.getItemInOffHand()).thenReturn(tnt);
            }
        }
        when(player.isPermissionSet("modifyworld.items.allowunknownaction.tnt")).thenReturn(true);
        when(player.hasPermission("modifyworld.items.allowunknownaction.tnt")).thenReturn(false);
        listener.onClick(event);
        verify(event).setCancelled(true);
    }

    @Test
    void unknownActionExplicitAllowAndContextChangesAreRechecked() {
        String permission = "modifyworld.items.allowunknownaction.tnt";
        when(player.isPermissionSet(permission)).thenReturn(true);
        when(player.hasPermission(permission)).thenReturn(true, false);
        InventoryClickEvent first = click(InventoryAction.UNKNOWN, 0);
        when(first.getCurrentItem()).thenReturn(tnt);
        listener.onClick(first);
        verify(first, never()).setCancelled(anyBoolean());
        InventoryClickEvent second = click(InventoryAction.UNKNOWN, 0);
        when(second.getCurrentItem()).thenReturn(tnt);
        listener.onClick(second);
        verify(second).setCancelled(true);
    }

    @Test
    void unknownPermissionDoesNotBypassKnownTransferDenial() {
        when(player.isPermissionSet("modifyworld.items.allowunknownaction.tnt")).thenReturn(true);
        InventoryClickEvent event = click(InventoryAction.PICKUP_ALL, 0);
        when(event.getCurrentItem()).thenReturn(tnt);
        when(player.hasPermission("modifyworld.items.take.tnt.of.chest")).thenReturn(false);
        listener.onClick(event);
        verify(event).setCancelled(true);
        verify(player, never()).isPermissionSet(anyString());
    }

}
