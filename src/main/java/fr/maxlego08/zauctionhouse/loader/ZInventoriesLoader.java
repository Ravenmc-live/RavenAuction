package fr.maxlego08.zauctionhouse.loader;

import fr.maxlego08.menu.api.ButtonManager;
import fr.maxlego08.menu.api.InventoryManager;
import fr.maxlego08.menu.api.exceptions.InventoryException;
import fr.maxlego08.menu.api.loader.NoneLoader;
import fr.maxlego08.menu.api.pattern.PatternManager;
import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.api.InventoriesLoader;
import fr.maxlego08.zauctionhouse.api.inventories.Inventories;
import fr.maxlego08.zauctionhouse.api.messages.Message;
import fr.maxlego08.zauctionhouse.buttons.ItemContentButton;
import fr.maxlego08.zauctionhouse.buttons.ShowButton;
import fr.maxlego08.zauctionhouse.buttons.admin.*;
import fr.maxlego08.zauctionhouse.buttons.admin.history.*;
import fr.maxlego08.zauctionhouse.buttons.confirm.ConfirmPurchaseButton;
import fr.maxlego08.zauctionhouse.buttons.confirm.ConfirmRemoveListedButton;
import fr.maxlego08.zauctionhouse.buttons.history.HistoryItemsButton;
import fr.maxlego08.zauctionhouse.buttons.inventory.ExpiredInventoryButton;
import fr.maxlego08.zauctionhouse.buttons.inventory.HistoryInventoryButton;
import fr.maxlego08.zauctionhouse.buttons.inventory.PurchasedInventoryButton;
import fr.maxlego08.zauctionhouse.buttons.inventory.SellingInventoryButton;
import fr.maxlego08.zauctionhouse.buttons.list.CombinedItemsButton;
import fr.maxlego08.zauctionhouse.buttons.list.ExpiredItemsButton;
import fr.maxlego08.zauctionhouse.buttons.list.ListedItemsButton;
import fr.maxlego08.zauctionhouse.buttons.list.PurchasedItemsButton;
import fr.maxlego08.zauctionhouse.buttons.list.SellingItemsButton;
import fr.maxlego08.zauctionhouse.buttons.sell.SellCancelButton;
import fr.maxlego08.zauctionhouse.buttons.sell.SellConfirmButton;
import fr.maxlego08.zauctionhouse.buttons.sell.SellEconomyButton;
import fr.maxlego08.zauctionhouse.buttons.ClearSearchButton;
import fr.maxlego08.zauctionhouse.buttons.RemoveAllExpiredButton;
import fr.maxlego08.zauctionhouse.buttons.RemoveAllPurchasedButton;
import fr.maxlego08.zauctionhouse.buttons.RemoveAllSellingButton;
import fr.maxlego08.zauctionhouse.buttons.shulker.ShulkerInfoButton;
import fr.maxlego08.zauctionhouse.buttons.ClaimButton;
import fr.maxlego08.zauctionhouse.buttons.shulker.ShulkerOpenButton;
import fr.maxlego08.zauctionhouse.loader.buttons.CombinedItemsLoader;
import fr.maxlego08.zauctionhouse.loader.buttons.*;
import fr.maxlego08.zauctionhouse.loader.permissibles.CategoryPermissibleLoader;
import fr.maxlego08.zauctionhouse.utils.PerformanceDebug;
import fr.maxlego08.zauctionhouse.utils.ZUtils;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.io.File;

public class ZInventoriesLoader extends ZUtils implements InventoriesLoader {

    private final AuctionPlugin plugin;
    private final PatternManager patternManager;
    private final ButtonManager buttonManager;
    private final InventoryManager inventoryManager;
    private final PerformanceDebug performanceDebug;

    public ZInventoriesLoader(AuctionPlugin plugin) {
        this.plugin = plugin;

        this.buttonManager = getProvider(ButtonManager.class);
        this.inventoryManager = getProvider(InventoryManager.class);
        this.patternManager = getProvider(PatternManager.class);
        this.performanceDebug = new PerformanceDebug(plugin);
    }

    private <T> T getProvider(Class<T> classz) {
        RegisteredServiceProvider<T> provider = this.plugin.getServer().getServicesManager().getRegistration(classz);
        if (provider == null) {
            this.plugin.getLogger().severe("Unable to retrieve the provider " + classz);
            return null;
        }
        return provider.getProvider();
    }

    @Override
    public void loadInventories() {

        File folder = new File(this.plugin.getDataFolder(), "inventories");
        if (!folder.exists()) {
            folder.mkdir();
            createInventoriesFile();
        }

        files(folder, this::loadInventory);
    }

    @Override
    public void loadInventory(File file) {
        try {
            this.inventoryManager.loadInventory(this.plugin, file);
        } catch (InventoryException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void loadPatterns() {

        File folder = new File(this.plugin.getDataFolder(), "patterns");
        if (!folder.exists()) {
            folder.mkdir();
            createPatternFiles();
        }

        this.files(folder, this::loadPattern);
    }

    @Override
    public void loadPattern(File file) {
        try {
            this.patternManager.loadPattern(file);
        } catch (InventoryException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void loadButtons() {

        this.buttonManager.unregisters(this.plugin);

        // Permissibles
        this.buttonManager.registerPermissible(new CategoryPermissibleLoader(this.plugin));

        // Player
        this.buttonManager.register(new EmptySlotLoader(this.plugin, ListedItemsButton.class, "RAVENAUCTION_LISTED_ITEMS"));
        this.buttonManager.register(new EmptySlotLoader(this.plugin, ExpiredItemsButton.class, "RAVENAUCTION_EXPIRED_ITEMS"));
        this.buttonManager.register(new EmptySlotLoader(this.plugin, SellingItemsButton.class, "RAVENAUCTION_SELLING_ITEMS"));
        this.buttonManager.register(new EmptySlotLoader(this.plugin, PurchasedItemsButton.class, "RAVENAUCTION_PURCHASED_ITEMS"));
        this.buttonManager.register(new NoneLoader(this.plugin, ItemContentButton.class, "RAVENAUCTION_ITEM_CONTENT"));
        this.buttonManager.register(new CombinedItemsLoader(this.plugin));

        this.buttonManager.register(new NoneLoader(this.plugin, RemoveAllExpiredButton.class, "RAVENAUCTION_REMOVE_ALL_EXPIRED"));
        this.buttonManager.register(new NoneLoader(this.plugin, RemoveAllSellingButton.class, "RAVENAUCTION_REMOVE_ALL_SELLING"));
        this.buttonManager.register(new NoneLoader(this.plugin, RemoveAllPurchasedButton.class, "RAVENAUCTION_REMOVE_ALL_PURCHASED"));

        this.buttonManager.register(new NoneLoader(this.plugin, ExpiredInventoryButton.class, "RAVENAUCTION_EXPIRED_INVENTORY"));
        this.buttonManager.register(new NoneLoader(this.plugin, SellingInventoryButton.class, "RAVENAUCTION_SELLING_INVENTORY"));
        this.buttonManager.register(new NoneLoader(this.plugin, PurchasedInventoryButton.class, "RAVENAUCTION_PURCHASED_INVENTORY"));
        this.buttonManager.register(new NoneLoader(this.plugin, HistoryInventoryButton.class, "RAVENAUCTION_HISTORY_INVENTORY"));

        this.buttonManager.register(new NoneLoader(this.plugin, ShowButton.class, "RAVENAUCTION_SHOW"));
        this.buttonManager.register(new NoneLoader(this.plugin, ConfirmRemoveListedButton.class, "RAVENAUCTION_CONFIRM_REMOVE_LISTED"));
        this.buttonManager.register(new NoneLoader(this.plugin, ConfirmPurchaseButton.class, "RAVENAUCTION_CONFIRM_PURCHASE"));
        this.buttonManager.register(new NoneLoader(this.plugin, SellCancelButton.class, "RAVENAUCTION_SELL_CANCEL"));
        this.buttonManager.register(new NoneLoader(this.plugin, SellConfirmButton.class, "RAVENAUCTION_SELL_CONFIRM"));
        this.buttonManager.register(new NoneLoader(this.plugin, SellEconomyButton.class, "RAVENAUCTION_SELL_ECONOMY"));
        this.buttonManager.register(new LoadingSlotLoader(this.plugin, HistoryItemsButton.class, "RAVENAUCTION_HISTORY_ITEMS"));
        this.buttonManager.register(new SellShowItemLoader(this.plugin));
        this.buttonManager.register(new SellPriceButtonLoader(this.plugin));
        this.buttonManager.register(new HistorySortLoader(this.plugin));

        // Admin
        this.buttonManager.register(new NoneLoader(this.plugin, AdminSellingItemsButton.class, "RAVENAUCTION_ADMIN_SELLING_ITEMS"));
        this.buttonManager.register(new NoneLoader(this.plugin, AdminExpiredItemsButton.class, "RAVENAUCTION_ADMIN_EXPIRED_ITEMS"));
        this.buttonManager.register(new NoneLoader(this.plugin, AdminPurchasedItemsButton.class, "RAVENAUCTION_ADMIN_PURCHASED_ITEMS"));
        this.buttonManager.register(new LoadingSlotLoader(this.plugin, AdminLogsButton.class, "RAVENAUCTION_ADMIN_LOGS"));
        this.buttonManager.register(new LoadingSlotLoader(this.plugin, AdminTransactionsButton.class, "RAVENAUCTION_ADMIN_TRANSACTIONS"));
        this.buttonManager.register(new LogTypeFilterLoader(this.plugin));
        this.buttonManager.register(new LogDateFilterLoader(this.plugin));
        this.buttonManager.register(new TransactionStatusFilterLoader(this.plugin));
        this.buttonManager.register(new TransactionDateFilterLoader(this.plugin));

        this.buttonManager.register(new NoneLoader(this.plugin, AdminHistoryMainButton.class, "RAVENAUCTION_ADMIN_HISTORY_MAIN"));
        this.buttonManager.register(new NoneLoader(this.plugin, AdminHistoryMainLogsButton.class, "RAVENAUCTION_ADMIN_HISTORY_LOGS"));
        this.buttonManager.register(new NoneLoader(this.plugin, AdminHistoryMainSellingButton.class, "RAVENAUCTION_ADMIN_HISTORY_SELLING"));
        this.buttonManager.register(new NoneLoader(this.plugin, AdminHistoryMainPurchasedButton.class, "RAVENAUCTION_ADMIN_HISTORY_PURCHASED"));
        this.buttonManager.register(new NoneLoader(this.plugin, AdminHistoryMainTransactionsButton.class, "RAVENAUCTION_ADMIN_HISTORY_TRANSACTIONS"));
        this.buttonManager.register(new NoneLoader(this.plugin, AdminHistoryMainExpiredButton.class, "RAVENAUCTION_ADMIN_HISTORY_EXPIRED"));

        this.buttonManager.register(new SortLoader(this.plugin, this.inventoryManager));
        this.buttonManager.register(new RefreshLoader(this.plugin, this.inventoryManager));
        this.buttonManager.register(new CategoryButtonLoader(this.plugin));
        this.buttonManager.register(new CategorySwitcherLoader(this.plugin));
        this.buttonManager.register(new SellLimitLoader(this.plugin));

        // Claim
        this.buttonManager.register(new ClaimButtonLoader(this.plugin, this.inventoryManager));

        // Search
        this.buttonManager.register(new SearchLoader(this.plugin));
        this.buttonManager.register(new NoneLoader(this.plugin, ClearSearchButton.class, "RAVENAUCTION_CLEAR_SEARCH"));

        // Options
        this.buttonManager.register(new OptionToggleLoader(this.plugin));

        // Shulker
        this.buttonManager.register(new NoneLoader(this.plugin, ShulkerOpenButton.class, "RAVENAUCTION_SHULKER_OPEN"));
        this.buttonManager.register(new ShulkerContentLoader(this.plugin));
        this.buttonManager.register(new NoneLoader(this.plugin, ShulkerInfoButton.class, "RAVENAUCTION_SHULKER_INFO"));
        this.buttonManager.register(new ShulkerNavigationLoader(this.plugin));
    }

    @Override
    public void load() {

        this.loadButtons();
        this.loadPatterns();
        this.loadInventories();
    }

    @Override
    public void reload() {

        this.inventoryManager.deleteInventories(this.plugin);
        this.loadPatterns();
        this.loadInventories();
    }

    private void createPatternFiles() {
        copyFiles("patterns", "decoration", "pagination", "back");
    }

    private void createInventoriesFile() {
        copyFiles(
                "inventories", "auction", "expired-items", "selling-items",//
                "sell-inventory", "categories", "purchased-items", "history", //

                // Admin
                "admin/admin-selling-items", "admin/admin-expired-items", "admin/admin-purchased-items", //
                "admin/admin-logs", "admin/admin-transactions", "admin/admin-history-main", //

                // Combined
                "combined-items", //

                // Confirm
                "confirms/remove-confirm", "confirms/purchase-confirm", //
                "confirms/purchase-inventory-confirm", "confirms/remove-inventory-confirm", //

                // Shulker
                "shulker-content", //

                // Options
                "options" //
        );
    }

    private void copyFiles(String path, String... files) {
        for (String fileName : files) {
            this.plugin.saveFile(path + "/" + fileName + ".yml", false);
        }
    }

    @Override
    public InventoryManager getInventoryManager() {
        return inventoryManager;
    }

    @Override
    public ButtonManager getButtonManager() {
        return buttonManager;
    }

    @Override
    public void openInventory(Player player, Inventories inventories) {
        this.openInventory(player, inventories, 1);
    }

    @Override
    public void openInventory(Player player, Inventories inventories, int page) {
        long start = this.performanceDebug.start();

        var optional = this.inventoryManager.getInventory(this.plugin, inventories.getFileName());
        if (optional.isEmpty()) {
            this.plugin.getLogger().severe("Unable to open inventory " + inventories.getFileName() + ", inventory not found");
            message(this.plugin, player, Message.INVENTORY_NOT_FOUND, "%inventory-name%", inventories.getFileName());
            return;
        }

        var inventory = optional.get();
        this.plugin.getScheduler().runAtEntity(player, w -> this.inventoryManager.openInventoryWithOldInventories(player, inventory, page));

        this.performanceDebug.end("openInventory." + inventories.getFileName(), start, "for=" + player.getName() + ", page=" + page);
    }
}
