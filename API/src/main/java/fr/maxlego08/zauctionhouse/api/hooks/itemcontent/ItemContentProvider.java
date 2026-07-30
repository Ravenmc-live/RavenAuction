package fr.maxlego08.zauctionhouse.api.hooks.itemcontent;

import org.bukkit.inventory.ItemStack;

import java.util.List;

/**
 * Provides the contents of items that contain other items.
 * <p>
 * This abstraction allows plugins to register custom ways to extract
 * inner items from container items such as vanilla shulker boxes.
 * Providers are queried in priority order; the first one that recognizes an item wins.
 */
public interface ItemContentProvider {

    /**
     * Returns the name of this provider (for logging purposes).
     *
     * @return provider name
     */
    String getName();

    /**
     * Returns the priority of this provider. Lower values are checked first.
     * The default vanilla provider uses priority 100.
     *
     * @return priority value
     */
    default int getPriority() {
        return 100;
    }

    /**
     * Checks whether the given item is a container that this provider can handle.
     *
     * @param itemStack the item to check
     * @return true if this provider can extract contents from the item
     */
    boolean isContainer(ItemStack itemStack);

    /**
     * Extracts the contents of the given container item.
     *
     * @param itemStack the container item
     * @return a list of items inside the container, never null
     */
    List<ItemStack> getContents(ItemStack itemStack);
}
