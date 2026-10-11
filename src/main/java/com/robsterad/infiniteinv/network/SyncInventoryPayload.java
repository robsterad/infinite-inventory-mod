package com.robsterad.infiniteinv.network;

import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;
import java.util.ArrayList;
import java.util.List;

public final class SyncInventoryPayload implements InventoryPacket {
    public static final Identifier ID = new Identifier("infinite-inventory", "sync_inventory");
    private final List<NetworkItemData> items;

    public static final class NetworkItemData {
        private final ItemStack stack;
        private final long count;
        private final long timestamp;

        public NetworkItemData(ItemStack stack, long count, long timestamp) {
            this.stack = stack;
            this.count = count;
            this.timestamp = timestamp;
        }

        public NetworkItemData(PacketByteBuf buf) {
            this(buf.readItemStack(), buf.readLong(), buf.readLong());
        }

        public ItemStack stack() { return stack; }
        public long count() { return count; }
        public long timestamp() { return timestamp; }

        public void write(PacketByteBuf buf) {
            buf.writeItemStack(stack);
            buf.writeLong(count);
            buf.writeLong(timestamp);
        }
    }

    public SyncInventoryPayload(List<NetworkItemData> items) {
        this.items = items;
    }

    public SyncInventoryPayload(PacketByteBuf buf) {
        int size = buf.readVarInt();
        items = new ArrayList<>();
        for (int i = 0; i < size; i++) items.add(new NetworkItemData(buf));
    }

    public List<NetworkItemData> items() { return items; }

    @Override
    public Identifier getId() { return ID; }

    @Override
    public void write(PacketByteBuf buf) {
        buf.writeVarInt(items.size());
        for (NetworkItemData item : items) item.write(buf);
    }
}

