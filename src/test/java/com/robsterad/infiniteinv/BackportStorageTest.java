package com.robsterad.infiniteinv;

import com.robsterad.infiniteinv.inventory.ItemKey;
import com.robsterad.infiniteinv.inventory.PlayerInfiniteInventory;
import com.robsterad.infiniteinv.network.*;
import com.robsterad.infiniteinv.storage.InfiniteInventoryState;
import io.netty.buffer.Unpooled;
import com.robsterad.infiniteinv.network.InventoryPacket;
import java.util.function.Function;
import net.minecraft.Bootstrap;
import net.minecraft.SharedConstants;
import net.minecraft.datafixer.Schemas;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.text.Text;
import net.minecraft.world.PersistentStateManager;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class BackportStorageTest {
    @BeforeAll
    static void bootstrap() {
        SharedConstants.getGameVersion();
        Bootstrap.initialize();
    }

    private static ItemStack namedEnchantedStack() {
        ItemStack stack = new ItemStack(Items.DIAMOND_SWORD);
        stack.setCustomName(new net.minecraft.text.LiteralText("Steve"));
        stack.addEnchantment(Enchantments.SHARPNESS, 5);
        stack.setDamage(37);
        stack.getOrCreateTag().putString("customTestData", "preserved");
        return stack;
    }

    @Test
    void itemIdentityPreservesNbtAndDoesNotShareMutableTags() {
        ItemStack original = namedEnchantedStack();
        ItemKey key = ItemKey.of(original);
        ItemStack restored = key.toStack(1);
        assertEquals(original.getTag(), restored.getTag());
        assertNotEquals(key, ItemKey.of(new ItemStack(Items.DIAMOND_SWORD)));
        original.getOrCreateTag().putString("customTestData", "changed");
        restored.getOrCreateTag().putString("customTestData", "also changed");
        assertEquals("preserved", key.toStack(1).getTag().getString("customTestData"));

        PlayerInfiniteInventory inventory = new PlayerInfiniteInventory();
        inventory.setItemCountDirectly(key, 5_000_000_000L);
        assertEquals(1, inventory.removeStack(key, 1).getCount());
        assertEquals(4_999_999_999L, inventory.getAllItems().get(key));
        ItemKey stone = ItemKey.of(new ItemStack(Items.STONE));
        inventory.addStack(new ItemStack(Items.STONE, 64));
        assertEquals(64, inventory.removeStack(stone, 64).getCount());
        assertFalse(inventory.getAllItems().containsKey(stone));
    }

    @Test
    void flatWorldSaveReloadsItemsLongCountsTimestampsAndPreferences(@TempDir Path directory) {
        UUID player = UUID.randomUUID();
        ItemKey key = ItemKey.of(namedEnchantedStack());
        PersistentStateManager manager = new PersistentStateManager(directory.toFile(), Schemas.getFixer());
        InfiniteInventoryState state = manager.getOrCreate(
                InfiniteInventoryState::new, "infinite_inventory");
        PlayerInfiniteInventory inventory = new PlayerInfiniteInventory();
        inventory.setItemCountDirectly(key, 5_000_000_000L);
        state.players.put(player, inventory);
        state.itemTimestamps.put(player, java.util.Collections.singletonMap(key, 123456789L));
        InfiniteInventoryState.UiPrefs prefs = new InfiniteInventoryState.UiPrefs(false, "ALPHA_DESC", true);
        state.uiPrefs.put(player, prefs);
        state.markDirty();
        manager.save();
        assertTrue(Files.exists(directory.resolve("infinite_inventory.dat")));

        PersistentStateManager reloadedManager = new PersistentStateManager(directory.toFile(), Schemas.getFixer());
        InfiniteInventoryState reloaded = reloadedManager.getOrCreate(
                InfiniteInventoryState::new, "infinite_inventory");
        assertEquals(5_000_000_000L, reloaded.players.get(player).getAllItems().get(key));
        assertEquals(123456789L, reloaded.itemTimestamps.get(player).get(key));
        assertEquals(prefs, reloaded.uiPrefs.get(player));
        assertEquals(key, ItemKey.of(reloaded.players.get(player).removeStack(key, 1)));
    }

    @Test
    void savesWithoutUiPreferencesRemainReadable() {
        InfiniteInventoryState state = new InfiniteInventoryState();
        UUID player = UUID.randomUUID();
        PlayerInfiniteInventory inventory = new PlayerInfiniteInventory();
        inventory.addStack(new ItemStack(Items.STONE, 64));
        state.players.put(player, inventory);
        NbtCompound encoded = state.writeNbt(new NbtCompound());
        encoded.remove("uiPrefs");
        InfiniteInventoryState decoded = InfiniteInventoryState.fromNbt(encoded);
        assertTrue(decoded.uiPrefs.isEmpty());
        assertEquals(64L, decoded.players.get(player).getAllItems().get(ItemKey.of(new ItemStack(Items.STONE))));
        assertEquals(InfiniteInventoryState.UiPrefs.DEFAULT, decoded.uiPrefs.getOrDefault(player,
                InfiniteInventoryState.UiPrefs.DEFAULT));
    }

    private static <T extends InventoryPacket> T roundTrip(T packet, Function<PacketByteBuf, T> decoder) {
        PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
        try {
            packet.write(buf);
            T decoded = decoder.apply(buf);
            assertEquals(packet.getId(), decoded.getId());
            assertFalse(buf.isReadable());
            return decoded;
        } finally {
            buf.release();
        }
    }

    @Test
    void allPacketTypesRoundTripWithItemNbtAndLongCounts() {
        InsertItemPayload inserted = roundTrip(new InsertItemPayload(12, true), InsertItemPayload::new);
        assertEquals(12, inserted.slotId());
        assertTrue(inserted.single());
        SyncUiPrefsPayload syncedPrefs = roundTrip(new SyncUiPrefsPayload(false, "RECENT", true), SyncUiPrefsPayload::new);
        assertFalse(syncedPrefs.panelVisible());
        assertEquals("RECENT", syncedPrefs.sortMode());
        assertTrue(syncedPrefs.showTooltips());
        UpdateUiPrefsPayload updatedPrefs = roundTrip(new UpdateUiPrefsPayload(true, "DESCENDING", false), UpdateUiPrefsPayload::new);
        assertTrue(updatedPrefs.panelVisible());
        assertEquals("DESCENDING", updatedPrefs.sortMode());
        assertFalse(updatedPrefs.showTooltips());
        ItemStack stack = namedEnchantedStack();
        ExtractItemPayload extracted = roundTrip(new ExtractItemPayload(stack, true), ExtractItemPayload::new);
        assertTrue(extracted.single());
        assertEquals(ItemKey.of(stack), ItemKey.of(extracted.stackToMatch()));
        SyncInventoryPayload synced = roundTrip(new SyncInventoryPayload(java.util.Collections.singletonList(
                new SyncInventoryPayload.NetworkItemData(stack, 5_000_000_000L, 123456789L))), SyncInventoryPayload::new);
        assertEquals(5_000_000_000L, synced.items().get(0).count());
        assertEquals(123456789L, synced.items().get(0).timestamp());
        assertEquals(ItemKey.of(stack), ItemKey.of(synced.items().get(0).stack()));
    }
}
