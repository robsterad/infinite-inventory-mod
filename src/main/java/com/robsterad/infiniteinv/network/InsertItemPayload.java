package com.robsterad.infiniteinv.network;

import net.fabricmc.fabric.api.networking.v1.FabricPacket;
import net.fabricmc.fabric.api.networking.v1.PacketType;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;

public record InsertItemPayload(int slotId, boolean single) implements FabricPacket {
    public static final PacketType<InsertItemPayload> ID = PacketType.create(
            new Identifier("infinite-inventory", "insert_item"), InsertItemPayload::new);

    public InsertItemPayload(PacketByteBuf buf) {
        this(buf.readInt(), buf.readBoolean());
    }

    @Override
    public PacketType<InsertItemPayload> getType() {
        return ID;
    }

    @Override
    public void write(PacketByteBuf buf) {
        buf.writeInt(slotId);
        buf.writeBoolean(single);
    }
}
