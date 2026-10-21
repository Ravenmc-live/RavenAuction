package fr.maxlego08.zauctionhouse.utils;

import fr.maxlego08.zauctionhouse.api.item.items.AuctionItem;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.block.ShulkerBox;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ShulkerHelperTest {
    @Test
    void snapshotKeepsEverySlotAndClonesContentsWithoutChangingTheBox() {
        var stack = mock(ItemStack.class);
        when(stack.getType()).thenReturn(Material.RED_SHULKER_BOX);
        var meta = mock(BlockStateMeta.class);
        when(stack.getItemMeta()).thenReturn(meta);
        var box = mock(ShulkerBox.class, RETURNS_DEEP_STUBS);
        when(meta.getBlockState()).thenReturn(box);
        var contents = new ItemStack[27];
        for (int slot : List.of(0, 13, 26)) {
            contents[slot] = mock(ItemStack.class);
            when(contents[slot].getType()).thenReturn(mock(Material.class));
            when(contents[slot].clone()).thenReturn(mock(ItemStack.class));
        }
        when(box.getSnapshotInventory().getContents()).thenReturn(contents);
        var snapshot = ShulkerHelper.getPositionalContents(stack);
        assertEquals(27, snapshot.length);
        for (int slot = 0; slot < 27; slot++) {
            if (contents[slot] == null) assertNull(snapshot[slot]);
            else {
                assertSame(contents[slot].clone(), snapshot[slot]);
                assertNotSame(contents[slot], snapshot[slot]);
            }
        }
        verify(box, never()).getInventory();
        verify(stack, never()).setItemMeta(any());
    }

    @Test
    void emptyOrInvalidBoxesStillHaveExactlyTwentySevenEmptyPositions() {
        assertEquals(27, ShulkerHelper.getPositionalContents(null).length);
        var stack = mock(ItemStack.class);
        when(stack.getType()).thenReturn(Material.SHULKER_BOX);
        assertTrue(java.util.Arrays.stream(ShulkerHelper.getPositionalContents(stack)).allMatch(java.util.Objects::isNull));
        when(stack.getType()).thenReturn(Material.STONE);
        assertNull(ShulkerHelper.singleShulker(null));
        assertFalse(ShulkerHelper.isShulkerBox(stack));
    }

    @Test
    void onlyASingleShulkerListingUsesTheExpandedPurchaseDialog() {
        var item = mock(AuctionItem.class);
        var stack = mock(ItemStack.class);
        when(stack.getType()).thenReturn(Material.BLACK_SHULKER_BOX);
        when(item.getItemStacks()).thenReturn(List.of(stack));
        assertSame(stack, ShulkerHelper.singleShulker(item));
        when(item.getItemStacks()).thenReturn(List.of(stack, stack));
        assertNull(ShulkerHelper.singleShulker(item));
        when(item.getItemStacks()).thenReturn(List.of(stack));
        when(stack.getType()).thenReturn(Material.DIAMOND_SWORD);
        assertNull(ShulkerHelper.singleShulker(item));
    }

    @Test
    void titlePreservesCustomNamesAndUsesTheActualColourForUnnamedBoxes() {
        var stack = mock(ItemStack.class);
        when(stack.getType()).thenReturn(Material.LIGHT_BLUE_SHULKER_BOX);
        assertEquals("Light Blue Shulker Box", ShulkerHelper.title(stack));
        var meta = mock(BlockStateMeta.class);
        when(stack.getItemMeta()).thenReturn(meta);
        when(meta.hasDisplayName()).thenReturn(true);
        when(meta.displayName()).thenReturn(Component.text("My Gear • Box #2"));
        assertEquals("My Gear • Box #2", ShulkerHelper.title(stack));
    }

    @Test
    void everyVanillaShulkerColourIsRecognized() {
        long count = java.util.Arrays.stream(Material.values())
                .filter(material -> !material.name().startsWith("LEGACY_") && material.name().endsWith("SHULKER_BOX"))
                .peek(material -> assertTrue(ShulkerHelper.isShulkerBox(material))).count();
        assertEquals(17, count);
    }
    @Test
    void titleKeepsExactRgbColoursInsteadOfDownsamplingThem() {
        var stack = mock(ItemStack.class);
        var meta = mock(BlockStateMeta.class);
        when(stack.getItemMeta()).thenReturn(meta);
        when(meta.hasDisplayName()).thenReturn(true);
        when(meta.displayName()).thenReturn(Component.text("Named Box", net.kyori.adventure.text.format.TextColor.color(0x123456)));
        assertEquals("§x§1§2§3§4§5§6Named Box", ShulkerHelper.title(stack));
    }
}
