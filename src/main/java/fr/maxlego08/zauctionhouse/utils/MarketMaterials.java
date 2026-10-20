package fr.maxlego08.zauctionhouse.utils;

import org.bukkit.Material;

public final class MarketMaterials {

    private MarketMaterials() {}

    public static boolean isTool(Material m) {
        String n = m.name();
        return n.endsWith("_PICKAXE") || n.endsWith("_AXE") || n.endsWith("_SHOVEL")
                || n.endsWith("_HOE")  || n.endsWith("_SWORD")
                || m == Material.BOW   || m == Material.CROSSBOW
                || m == Material.TRIDENT || m == Material.FISHING_ROD
                || m == Material.SHEARS;
    }

    public static boolean isCombat(Material m) {
        String n = m.name();
        return n.endsWith("_SWORD")      || n.endsWith("_HELMET")
                || n.endsWith("_CHESTPLATE") || n.endsWith("_LEGGINGS")
                || n.endsWith("_BOOTS")
                || m == Material.BOW     || m == Material.CROSSBOW
                || m == Material.TRIDENT || m == Material.SHIELD
                || m == Material.TOTEM_OF_UNDYING;
    }

    public static boolean isPotion(Material m) {
        return m == Material.POTION         || m == Material.SPLASH_POTION
                || m == Material.LINGERING_POTION || m == Material.TIPPED_ARROW;
    }

    public static boolean isBook(Material m) {
        return m == Material.BOOK           || m == Material.ENCHANTED_BOOK
                || m == Material.WRITTEN_BOOK    || m == Material.WRITABLE_BOOK
                || m == Material.KNOWLEDGE_BOOK;
    }

    public static boolean isIngredient(Material m) {
        String n = m.name();
        return n.startsWith("RAW_") || n.endsWith("_INGOT") || n.endsWith("_NUGGET")
                || m == Material.BLAZE_ROD    || m == Material.BLAZE_POWDER
                || m == Material.GHAST_TEAR   || m == Material.MAGMA_CREAM
                || m == Material.ENDER_PEARL  || m == Material.ENDER_EYE
                || m == Material.NETHER_STAR  || m == Material.GUNPOWDER
                || m == Material.SUGAR        || m == Material.BONE_MEAL
                || m == Material.STRING       || m == Material.LEATHER
                || m == Material.FEATHER      || m == Material.FLINT;
    }

    public static boolean isUtility(Material m) {
        return m == Material.COMPASS    || m == Material.CLOCK
                || m == Material.CHEST  || m == Material.BARREL
                || m == Material.FURNACE || m == Material.CRAFTING_TABLE
                || m == Material.ENDER_CHEST || m == Material.BEACON
                || m == Material.SADDLE || m == Material.NAME_TAG
                || m == Material.FLINT_AND_STEEL || m == Material.BUCKET;
    }
}
