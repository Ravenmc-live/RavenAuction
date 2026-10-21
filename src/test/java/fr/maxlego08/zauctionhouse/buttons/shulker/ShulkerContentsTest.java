package fr.maxlego08.zauctionhouse.buttons.shulker;

import fr.maxlego08.menu.api.engine.InventoryEngine;
import fr.maxlego08.menu.api.engine.ItemButton;
import org.bukkit.Material;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;
import java.util.HashMap;
import static org.mockito.Mockito.*;

class ShulkerContentsTest {
    @Test
    void previewPreservesEmptySlotsAndCancelsItemClicks() {
        checkRender(0);
    }

    @Test
    void purchaseContentsUseOnlyTheBottomTwentySevenSlots() {
        checkRender(27);
    }

    private void checkRender(int offset) {
        var engine = mock(InventoryEngine.class, RETURNS_DEEP_STUBS);
        when(engine.getItems()).thenReturn(new HashMap<>());
        var contents = new ItemStack[27];
        var stack = mock(ItemStack.class);
        var copy = mock(ItemStack.class);
        when(stack.getType()).thenReturn(mock(Material.class));
        when(stack.clone()).thenReturn(copy);
        contents[26] = stack;
        var button = new ItemButton(copy, offset + 26, false, engine);
        when(engine.addItem(offset + 26, copy)).thenReturn(button);
        ShulkerPreviewContentsButton.render(engine, contents, offset);
        for (int slot = 0; slot < 26; slot++) verify(engine.getSpigotInventory()).setItem(offset + slot, null);
        verify(engine).addItem(offset + 26, copy);
        var click = mock(InventoryClickEvent.class);
        button.onClick(click);
        verify(click).setCancelled(true);
        if (offset == 27) {
            verify(engine.getSpigotInventory(), never()).setItem(eq(11), any());
            verify(engine.getSpigotInventory(), never()).setItem(eq(13), any());
            verify(engine.getSpigotInventory(), never()).setItem(eq(15), any());
        }
    }
}
