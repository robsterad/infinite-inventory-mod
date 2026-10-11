package com.robsterad.infiniteinv.network;

import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;

public interface InventoryPacket {
    Identifier getId();
    void write(PacketByteBuf buf);

    default PacketByteBuf toBuffer() {
        PacketByteBuf buf = PacketByteBufs.create();
        write(buf);
        return buf;
    }
}
