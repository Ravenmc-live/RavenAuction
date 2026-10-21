package fr.maxlego08.zauctionhouse.utils;

import fr.maxlego08.zauctionhouse.api.item.Item;
import fr.maxlego08.zauctionhouse.api.item.items.AuctionItem;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Material;
import org.bukkit.block.ShulkerBox;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Utility class for handling shulker box items.
 */
public final class ShulkerHelper {

    private static final LegacyComponentSerializer NAME_SERIALIZER = LegacyComponentSerializer.builder()
            .character('§').hexColors().useUnusualXRepeatedCharacterHexFormat().build();

    private static final Set<Material> SHULKER_BOX_MATERIALS = Set.of(
            Material.SHULKER_BOX,
            Material.WHITE_SHULKER_BOX,
            Material.ORANGE_SHULKER_BOX,
            Material.MAGENTA_SHULKER_BOX,
            Material.LIGHT_BLUE_SHULKER_BOX,
            Material.YELLOW_SHULKER_BOX,
            Material.LIME_SHULKER_BOX,
            Material.PINK_SHULKER_BOX,
            Material.GRAY_SHULKER_BOX,
            Material.LIGHT_GRAY_SHULKER_BOX,
            Material.CYAN_SHULKER_BOX,
            Material.PURPLE_SHULKER_BOX,
            Material.BLUE_SHULKER_BOX,
            Material.BROWN_SHULKER_BOX,
            Material.GREEN_SHULKER_BOX,
            Material.RED_SHULKER_BOX,
            Material.BLACK_SHULKER_BOX
    );

    private ShulkerHelper() {
        // Utility class
    }

    /**
     * Checks if the given material is a shulker box.
     *
     * @param material the material to check
     * @return true if the material is a shulker box
     */
    public static boolean isShulkerBox(Material material) {
        return SHULKER_BOX_MATERIALS.contains(material);
    }

    /**
     * Checks if the given item stack is a shulker box.
     *
     * @param itemStack the item stack to check
     * @return true if the item is a shulker box
     */
    public static boolean isShulkerBox(ItemStack itemStack) {
        return itemStack != null && isShulkerBox(itemStack.getType());
    }

    /**
     * Gets all shulker boxes from a list of item stacks.
     *
     * @param itemStacks the list of item stacks to search
     * @return a list of shulker box item stacks
     */
    public static List<ItemStack> getShulkerBoxes(List<ItemStack> itemStacks) {
        if (itemStacks == null) {
            return List.of();
        }
        return itemStacks.stream()
                .filter(ShulkerHelper::isShulkerBox)
                .toList();
    }

    /**
     * Checks if a list of item stacks contains any shulker boxes.
     *
     * @param itemStacks the list of item stacks to check
     * @return true if the list contains at least one shulker box
     */
    public static boolean containsShulkerBox(List<ItemStack> itemStacks) {
        if (itemStacks == null) {
            return false;
        }
        return itemStacks.stream().anyMatch(ShulkerHelper::isShulkerBox);
    }

    /**
     * Gets the content of a shulker box.
     *
     * @param shulkerItem the shulker box item stack
     * @return a list of item stacks inside the shulker box
     */
    public static List<ItemStack> getShulkerContent(ItemStack shulkerItem) {
        if (shulkerItem == null || !isShulkerBox(shulkerItem)) {
            return List.of();
        }

        if (!(shulkerItem.getItemMeta() instanceof BlockStateMeta blockStateMeta)) {
            return List.of();
        }

        if (!(blockStateMeta.getBlockState() instanceof ShulkerBox shulkerBox)) {
            return List.of();
        }

        ItemStack[] contents = shulkerBox.getInventory().getContents();
        List<ItemStack> result = new ArrayList<>();

        for (ItemStack content : contents) {
            if (content != null && !content.getType().isAir()) {
                result.add(content.clone());
            }
        }

        return result;
    }

    public static ItemStack singleShulker(Item item) {
        if (!(item instanceof AuctionItem auctionItem)) return null;
        var stacks = auctionItem.getItemStacks();
        return stacks.size() == 1 && isShulkerBox(stacks.getFirst()) ? stacks.getFirst() : null;
    }

    public static ItemStack[] getPositionalContents(ItemStack shulkerItem) {
        ItemStack[] result = new ItemStack[27];
        if (!isShulkerBox(shulkerItem)
                || !(shulkerItem.getItemMeta() instanceof BlockStateMeta meta)
                || !(meta.getBlockState() instanceof ShulkerBox shulker)) return result;
        var contents = shulker.getSnapshotInventory().getContents();
        for (int slot = 0; slot < Math.min(result.length, contents.length); slot++) {
            var stack = contents[slot];
            if (stack != null && !stack.getType().isAir()) result[slot] = stack.clone();
        }
        return result;
    }

    public static String title(ItemStack stack) {
        var meta = stack.getItemMeta();
        if (meta != null && meta.hasDisplayName() && meta.displayName() != null) {
            return NAME_SERIALIZER.serialize(meta.displayName());
        }
        StringBuilder name = new StringBuilder();
        for (String word : stack.getType().name().toLowerCase(Locale.ROOT).split("_")) {
            if (!name.isEmpty()) name.append(' ');
            name.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return name.toString();
    }

    /**
     * Counts the number of non-empty slots in a shulker box.
     *
     * @param shulkerItem the shulker box item stack
     * @return the number of items in the shulker box
     */
    public static int getShulkerContentCount(ItemStack shulkerItem) {
        return getShulkerContent(shulkerItem).size();
    }

    /**
     * Checks if a shulker box is empty.
     *
     * @param shulkerItem the shulker box item stack
     * @return true if the shulker box is empty
     */
    public static boolean isShulkerEmpty(ItemStack shulkerItem) {
        return getShulkerContent(shulkerItem).isEmpty();
    }
}
