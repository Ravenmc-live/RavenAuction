package fr.maxlego08.zauctionhouse.buttons.list;

import fr.maxlego08.menu.api.engine.InventoryEngine;
import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.*;

class CombinedEmptyStateTest {
    @Test
    void installedMenusWithAnEmptySlotNeverRenderTheOldBarrier() {
        var plugin = mock(AuctionPlugin.class, RETURNS_DEEP_STUBS);
        var player = mock(Player.class);
        var engine = mock(InventoryEngine.class);
        new CombinedItemsButton(plugin, 22, false, false, false).onRender(player, engine);
        verifyNoInteractions(engine);
    }
}
