package fr.maxlego08.zauctionhouse.loader;

import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.File;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MissingInventoryDefaultsTest {
    @TempDir Path root;
    @Test void aFolderCreatedByThePurchaseUpdateStillGetsTheMainMenuAndAllMissingDefaults() throws Exception {
        Path confirm = root.resolve("inventories/confirms/purchase-confirm.yml");
        Files.createDirectories(confirm.getParent()); Files.writeString(confirm, "name: Preserve\nsize: 27\n");
        Path auction = root.resolve("inventories/auction.yml");
        AuctionPlugin plugin = mock(AuctionPlugin.class);
        when(plugin.getDataFolder()).thenReturn(root.toFile());
        doAnswer(call -> {
            String resource = call.getArgument(0); Path target = root.resolve(resource);
            Files.createDirectories(target.getParent());
            try (var input = getClass().getResourceAsStream("/" + resource)) {
                assertNotNull(input, resource); Files.copy(input, target);
            }
            return null;
        }).when(plugin).saveFile(anyString(), eq(false));
        ZInventoriesLoader loader = mock(ZInventoriesLoader.class, CALLS_REAL_METHODS);
        var field = ZInventoriesLoader.class.getDeclaredField("plugin"); field.setAccessible(true); field.set(loader, plugin);
        doNothing().when(loader).loadInventory(any(File.class));
        loader.loadInventories();
        assertTrue(Files.exists(auction));
        assertTrue(Files.exists(root.resolve("inventories/selling-items.yml")));
        assertTrue(Files.exists(root.resolve("inventories/confirms/shulker-purchase-confirm.yml")));
        assertEquals("name: Preserve\nsize: 27\n", Files.readString(confirm));
        Files.writeString(auction, "name: Custom\n"); loader.loadInventories();
        assertEquals("name: Custom\n", Files.readString(auction));
    }
}
