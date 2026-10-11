package com.robsterad.infiniteinv.network;

import net.fabricmc.fabric.api.networking.v1.FabricPacket;
import net.fabricmc.fabric.api.networking.v1.PacketType;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;

public record ExtractItemPayload(ItemStack stackToMatch, boolean single) implements FabricPacket {
    public static final PacketType<ExtractItemPayload> ID = PacketType.create(
            new Identifier("infinite-inventory", "extract_item"), ExtractItemPayload::new);

    public ExtractItemPayload(PacketByteBuf buf) {
        this(buf.readItemStack(), buf.readBoolean());
    }

    @Override
    public PacketType<ExtractItemPayload> getType() {
        return ID;
    }

    @Override
    public void write(PacketByteBuf buf) {
        buf.writeItemStack(stackToMatch);
        buf.writeBoolean(single);
    }
}
