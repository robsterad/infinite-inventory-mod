package com.robsterad.infiniteinv.network;

import net.fabricmc.fabric.api.networking.v1.FabricPacket;
import net.fabricmc.fabric.api.networking.v1.PacketType;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;

import java.util.List;

public record SyncInventoryPayload(List<NetworkItemData> items) implements FabricPacket {
    public static final PacketType<SyncInventoryPayload> ID = PacketType.create(
            new Identifier("infinite-inventory", "sync_inventory"), SyncInventoryPayload::new);

    public record NetworkItemData(ItemStack stack, long count, long timestamp) {
        public NetworkItemData(PacketByteBuf buf) {
            this(buf.readItemStack(), buf.readLong(), buf.readLong());
        }

        public void write(PacketByteBuf buf) {
            buf.writeItemStack(stack);
            buf.writeLong(count);
            buf.writeLong(timestamp);
        }
    }

    public SyncInventoryPayload(PacketByteBuf buf) {
        this(buf.readList(NetworkItemData::new));
    }

    @Override
    public PacketType<SyncInventoryPayload> getType() {
        return ID;
    }

    @Override
    public void write(PacketByteBuf buf) {
        buf.writeCollection(items, (buffer, item) -> item.write(buffer));
    }
}
