package com.robsterad.infiniteinv.storage;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.robsterad.infiniteinv.inventory.ItemKey;
import com.robsterad.infiniteinv.inventory.PlayerInfiniteInventory;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtOps;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Uuids;
import net.minecraft.world.PersistentState;
import net.minecraft.world.World;

import java.util.*;

public class InfiniteInventoryState extends PersistentState {

    public HashMap<UUID, PlayerInfiniteInventory> players = new HashMap<>();
    // a map for each player
    public HashMap<UUID, Map<ItemKey, Long>> itemTimestamps = new HashMap<>();
    public HashMap<UUID, UiPrefs> uiPrefs = new HashMap<>();

    public record UiPrefs(boolean panelVisible, String sortMode, boolean showTooltips) {
        public static final UiPrefs DEFAULT = new UiPrefs(true, "RECENT", false);
    }

    private static final Codec<UiPrefs> UI_PREFS_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.BOOL.fieldOf("panelVisible").forGetter(UiPrefs::panelVisible),
            Codec.STRING.fieldOf("sortMode").forGetter(UiPrefs::sortMode),
            Codec.BOOL.fieldOf("showTooltips").forGetter(UiPrefs::showTooltips)
    ).apply(instance, UiPrefs::new));

    private record SavedItem(ItemStack stack, long count, long timestamp) {}

    private static final Codec<SavedItem> SAVED_ITEM_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ItemStack.CODEC.fieldOf("item").forGetter(SavedItem::stack),
            Codec.LONG.fieldOf("count").forGetter(SavedItem::count),
            Codec.LONG.fieldOf("ts").forGetter(SavedItem::timestamp)
    ).apply(instance, SavedItem::new));

    private static final Codec<PlayerStateWrapper> PLAYER_STATE_CODEC = SAVED_ITEM_CODEC.listOf().xmap(
            list -> {
                PlayerStateWrapper wrapper = new PlayerStateWrapper(new PlayerInfiniteInventory(), new HashMap<>());
                for (SavedItem saved : list) {
                    ItemKey key = ItemKey.of(saved.stack());
                    if (key != null) {
                        wrapper.inv.setItemCountDirectly(key, saved.count());
                        wrapper.times.put(key, saved.timestamp());
                    }
                }
                return wrapper;
            },
            wrapper -> {
                List<SavedItem> list = new ArrayList<>();
                for (Map.Entry<ItemKey, Long> entry : wrapper.inv.getAllItems().entrySet()) {
                    long ts = wrapper.times.getOrDefault(entry.getKey(), 0L);
                    list.add(new SavedItem(entry.getKey().toStack(1), entry.getValue(), ts));
                }
                return list;
            }
    );

    private record PlayerStateWrapper(PlayerInfiniteInventory inv, Map<ItemKey, Long> times) {}

    public static final Codec<InfiniteInventoryState> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.unboundedMap(Uuids.STRING_CODEC, PLAYER_STATE_CODEC).fieldOf("players").forGetter(state -> {
                Map<UUID, PlayerStateWrapper> map = new HashMap<>();
                state.players.forEach((uuid, inv) -> map.put(uuid, new PlayerStateWrapper(inv, state.itemTimestamps.getOrDefault(uuid, new HashMap<>()))));
                return map;
            }),
            Codec.unboundedMap(Uuids.STRING_CODEC, UI_PREFS_CODEC).optionalFieldOf("uiPrefs", Map.of()).forGetter(state -> state.uiPrefs)
    ).apply(instance, (map, prefs) -> {
        InfiniteInventoryState state = new InfiniteInventoryState();
        map.forEach((uuid, wrapper) -> {
            state.players.put(uuid, wrapper.inv);
            state.itemTimestamps.put(uuid, wrapper.times);
        });
        state.uiPrefs.putAll(prefs);
        return state;
    }));

    public static InfiniteInventoryState fromNbt(NbtCompound nbt) {
        return CODEC.parse(NbtOps.INSTANCE, nbt).getOrThrow(false,
                error -> System.err.println("[InfiniteInv] Failed to read saved inventory: " + error));
    }

    @Override
    public NbtCompound writeNbt(NbtCompound nbt) {
        NbtCompound encoded = (NbtCompound) CODEC.encodeStart(NbtOps.INSTANCE, this).getOrThrow(false,
                error -> System.err.println("[InfiniteInv] Failed to write saved inventory: " + error));
        nbt.copyFrom(encoded);
        return nbt;
    }

    public static InfiniteInventoryState getServerState(MinecraftServer server) {
        return server.getWorld(World.OVERWORLD).getPersistentStateManager().getOrCreate(
                InfiniteInventoryState::fromNbt, InfiniteInventoryState::new, "infinite_inventory");
    }

    public static PlayerInfiniteInventory getPlayerState(LivingEntity player) {
        InfiniteInventoryState state = getServerState(player.getServer());
        return state.players.computeIfAbsent(player.getUuid(), uuid -> new PlayerInfiniteInventory());
    }

    public static void updateTimestamp(LivingEntity player, ItemKey key) {
        InfiniteInventoryState state = getServerState(player.getServer());
        state.itemTimestamps.computeIfAbsent(player.getUuid(), k -> new HashMap<>()).put(key, System.currentTimeMillis());
        state.markDirty();
    }

    public static UiPrefs getUiPrefs(net.minecraft.server.network.ServerPlayerEntity player) {
        InfiniteInventoryState state = getServerState(player.getServer());
        return state.uiPrefs.getOrDefault(player.getUuid(), UiPrefs.DEFAULT);
    }

    public static void setUiPrefs(net.minecraft.server.network.ServerPlayerEntity player, UiPrefs prefs) {
        InfiniteInventoryState state = getServerState(player.getServer());
        state.uiPrefs.put(player.getUuid(), prefs);
        state.markDirty();
    }
}
