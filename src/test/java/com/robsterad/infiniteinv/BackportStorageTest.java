package com.robsterad.infiniteinv;

import com.robsterad.infiniteinv.inventory.ItemKey;
import com.robsterad.infiniteinv.inventory.PlayerInfiniteInventory;
import com.robsterad.infiniteinv.network.*;
import com.robsterad.infiniteinv.storage.InfiniteInventoryState;
import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.networking.v1.FabricPacket;
import net.fabricmc.fabric.api.networking.v1.PacketType;
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
        SharedConstants.createGameVersion();
        Bootstrap.initialize();
    }

    private static ItemStack namedEnchantedStack() {
        ItemStack stack = new ItemStack(Items.DIAMOND_SWORD);
        stack.setCustomName(Text.literal("Steve"));
        stack.addEnchantment(Enchantments.SHARPNESS, 5);
        stack.setDamage(37);
        stack.getOrCreateNbt().putString("customTestData", "preserved");
        return stack;
    }

    @Test
    void itemIdentityPreservesNbtAndDoesNotShareMutableTags() {
        ItemStack original = namedEnchantedStack();
        ItemKey key = ItemKey.of(original);
        ItemStack restored = key.toStack(1);
        assertEquals(original.getNbt(), restored.getNbt());
        assertNotEquals(key, ItemKey.of(new ItemStack(Items.DIAMOND_SWORD)));
        original.getOrCreateNbt().putString("customTestData", "changed");
        restored.getOrCreateNbt().putString("customTestData", "also changed");
        assertEquals("preserved", key.toStack(1).getNbt().getString("customTestData"));

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
                InfiniteInventoryState::fromNbt, InfiniteInventoryState::new, "infinite_inventory");
        PlayerInfiniteInventory inventory = new PlayerInfiniteInventory();
        inventory.setItemCountDirectly(key, 5_000_000_000L);
        state.players.put(player, inventory);
        state.itemTimestamps.put(player, Map.of(key, 123456789L));
        InfiniteInventoryState.UiPrefs prefs = new InfiniteInventoryState.UiPrefs(false, "ALPHA_DESC", true);
        state.uiPrefs.put(player, prefs);
        state.markDirty();
        manager.save();
        assertTrue(Files.exists(directory.resolve("infinite_inventory.dat")));

        PersistentStateManager reloadedManager = new PersistentStateManager(directory.toFile(), Schemas.getFixer());
        InfiniteInventoryState reloaded = reloadedManager.getOrCreate(
                InfiniteInventoryState::fromNbt, InfiniteInventoryState::new, "infinite_inventory");
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

    @Test
    void oldGrassPathIdentifierNeedsAnItemDataFixBefore120CanDecodeIt(@TempDir Path directory) throws Exception {
        InfiniteInventoryState state = new InfiniteInventoryState();
        UUID player = UUID.randomUUID();
        PlayerInfiniteInventory inventory = new PlayerInfiniteInventory();
        inventory.addStack(new ItemStack(Items.DIRT_PATH));
        state.players.put(player, inventory);
        NbtCompound data = state.writeNbt(new NbtCompound());
        data.getCompound("players").getList(player.toString(), 10).getCompound(0)
                .getCompound("item").putString("id", "minecraft:grass_path");
        InfiniteInventoryState decoded = InfiniteInventoryState.fromNbt(data);
        assertTrue(decoded.players.get(player).getAllItems().isEmpty(),
                "The old identifier decodes as an empty stack and is silently omitted");
        NbtCompound root = new NbtCompound();
        root.putInt("DataVersion", 2586);
        root.put("data", data);
        Path file = directory.resolve("infinite_inventory.dat");
        net.minecraft.nbt.NbtIo.writeCompressed(root, file.toFile());
        byte[] original = Files.readAllBytes(file);
        PersistentStateManager manager = new PersistentStateManager(directory.toFile(), Schemas.getFixer());
        InfiniteInventoryState loaded = manager.getOrCreate(
                InfiniteInventoryState::fromNbt, InfiniteInventoryState::new, "infinite_inventory");
        assertTrue(loaded.players.get(player).getAllItems().isEmpty(),
                "The real 1.20 saved-data loader does not fix the nested item identifier either");
        assertArrayEquals(original, Files.readAllBytes(file), "Loading alone should not replace the original file");
    }

    private static <T extends FabricPacket> T roundTrip(T packet, PacketType<T> type) {
        PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
        try {
            packet.write(buf);
            T decoded = type.read(buf);
            assertFalse(buf.isReadable());
            return decoded;
        } finally {
            buf.release();
        }
    }

    @Test
    void allPacketTypesRoundTripWithItemNbtAndLongCounts() {
        assertEquals(new InsertItemPayload(12, true), roundTrip(new InsertItemPayload(12, true), InsertItemPayload.ID));
        assertEquals(new SyncUiPrefsPayload(false, "RECENT", true),
                roundTrip(new SyncUiPrefsPayload(false, "RECENT", true), SyncUiPrefsPayload.ID));
        assertEquals(new UpdateUiPrefsPayload(true, "DESCENDING", false),
                roundTrip(new UpdateUiPrefsPayload(true, "DESCENDING", false), UpdateUiPrefsPayload.ID));
        ItemStack stack = namedEnchantedStack();
        ExtractItemPayload extracted = roundTrip(new ExtractItemPayload(stack, true), ExtractItemPayload.ID);
        assertTrue(extracted.single());
        assertEquals(ItemKey.of(stack), ItemKey.of(extracted.stackToMatch()));
        SyncInventoryPayload synced = roundTrip(new SyncInventoryPayload(List.of(
                new SyncInventoryPayload.NetworkItemData(stack, 5_000_000_000L, 123456789L))), SyncInventoryPayload.ID);
        assertEquals(5_000_000_000L, synced.items().get(0).count());
        assertEquals(123456789L, synced.items().get(0).timestamp());
        assertEquals(ItemKey.of(stack), ItemKey.of(synced.items().get(0).stack()));
    }
}
