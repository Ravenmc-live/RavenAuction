package fr.maxlego08.zauctionhouse.search;

import com.tcoded.folialib.impl.PlatformScheduler;
import com.tcoded.folialib.wrapper.task.WrappedTask;
import fr.maxlego08.zauctionhouse.api.AuctionPlugin;
import fr.maxlego08.zauctionhouse.api.messages.Message;
import io.papermc.paper.event.packet.UncheckedSignChangeEvent;
import io.papermc.paper.math.Position;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Sign;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.sign.Side;
import org.bukkit.block.sign.SignSide;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SignSearchTest {
    MockedStatic<Bukkit> bukkit;
    AuctionPlugin plugin;
    ChatSearchListener listener;
    Player player;
    Location location;
    BlockData originalData;
    SignSide front;
    Queue<Runnable> tasks = new ArrayDeque<>();

    @BeforeEach
    void setup() {
        bukkit = mockStatic(Bukkit.class);
        plugin = mock(AuctionPlugin.class, RETURNS_DEEP_STUBS);
        PlatformScheduler scheduler = mock(PlatformScheduler.class);
        when(plugin.getScheduler()).thenReturn(scheduler);
        when(scheduler.runAtEntity(any(), any())).thenAnswer(call -> {
            Consumer<WrappedTask> action = call.getArgument(1);
            tasks.add(() -> action.accept(null));
            return CompletableFuture.completedFuture(null);
        });
        listener = new ChatSearchListener(plugin);
        player = mock(Player.class);
        World world = mock(World.class);
        when(world.getMaxHeight()).thenReturn(320);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.getWorld()).thenReturn(world);
        when(player.isOnline()).thenReturn(true);
        when(player.getLocation()).thenReturn(new Location(world, 1.5, 64, 2.5));
        location = new Location(world, 1, 65, 2);
        Block block = mock(Block.class);
        BlockState original = mock(BlockState.class);
        originalData = mock(BlockData.class);
        when(world.getBlockAt(any(Location.class))).thenReturn(block);
        when(block.getState()).thenReturn(original);
        when(original.getBlockData()).thenReturn(originalData);
        BlockData fakeData = mock(BlockData.class);
        Sign sign = mock(Sign.class);
        front = mock(SignSide.class);
        bukkit.when(() -> Bukkit.createBlockData(Material.OAK_SIGN)).thenReturn(fakeData);
        when(fakeData.createBlockState()).thenReturn(sign);
        when(sign.getBlockData()).thenReturn(fakeData);
        when(sign.getSide(Side.FRONT)).thenReturn(front);
    }

    @AfterEach
    void cleanup() { bukkit.close(); }

    void open() { listener.startSearch(player); tasks.remove().run(); }
    UncheckedSignChangeEvent submit(Location at, Side side, String value) {
        var event = new UncheckedSignChangeEvent(player, Position.block(at), side,
                List.of(Component.text(value), Component.empty(), Component.empty(), Component.empty()));
        listener.onSignChange(event);
        return event;
    }

    @Test
    void opensNativeSignWithCleanAsciiTextInsteadOfChat() {
        open();
        verify(player).openVirtualSign(Position.block(location), Side.FRONT);
        verify(front).line(1, Component.text("^^^^^^^^^^^^^"));
        verify(front).line(2, Component.text("Search"));
        verify(plugin, never()).sendMessage(eq(player), eq(Message.SEARCH_START), any(Object[].class));
    }

    @Test
    void trimsSearchConsumesOnlyOnePacketAndRestoresOriginalBlock() {
        open();
        assertTrue(submit(location, Side.FRONT, "  diamond  ").isCancelled());
        assertTrue(submit(location, Side.FRONT, "duplicate").isCancelled());
        tasks.remove().run();
        verify(plugin.getAuctionManager()).startSearch(player, "diamond");
        verify(player).sendBlockChange(location, originalData);
    }

    @Test
    void ignoresUnrelatedSignsAndWrongSide() {
        open();
        assertFalse(submit(location.clone().add(1, 0, 0), Side.FRONT, "spoof").isCancelled());
        assertFalse(submit(location, Side.BACK, "spoof").isCancelled());
        verify(plugin.getAuctionManager(), never()).startSearch(any(), any());
    }

    @Test
    void teleportInvalidatesAnAlreadySubmittedSearch() {
        open();
        submit(location, Side.FRONT, "old");
        PlayerTeleportEvent event = mock(PlayerTeleportEvent.class);
        when(event.getPlayer()).thenReturn(player);
        listener.onTeleport(event);
        tasks.remove().run();
        verify(plugin.getAuctionManager(), never()).startSearch(any(), any());
    }

    @Test
    void quitDiscardsSearchAndCancelClearsRatherThanSearchingTheWordCancel() {
        open();
        submit(location, Side.FRONT, " Cancel ");
        tasks.remove().run();
        verify(plugin.getAuctionManager()).startSearch(player, "");
        open();
        PlayerQuitEvent quit = mock(PlayerQuitEvent.class);
        when(quit.getPlayer()).thenReturn(player);
        listener.onQuit(quit);
        assertFalse(submit(location, Side.FRONT, "late").isCancelled());
    }

    @Test
    void unavailableSignFallsBackToChatRatherThanLeavingPlayerStuck() {
        doThrow(new IllegalStateException()).when(player).openVirtualSign(any(), any());
        open();
        verify(plugin).sendMessage(player, Message.SEARCH_START);
        verify(player).sendBlockChange(location, originalData);
    }

    @Test
    void shutdownDiscardsPendingPacketsAndRejectsNewRequests() {
        open();
        listener.close();
        assertFalse(submit(location, Side.FRONT, "late").isCancelled());
        while (!tasks.isEmpty()) tasks.remove().run();
        listener.startSearch(player);
        assertTrue(tasks.isEmpty());
    }
}
