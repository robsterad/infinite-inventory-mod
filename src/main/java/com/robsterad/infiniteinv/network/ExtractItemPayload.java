package com.robsterad.infiniteinv.network;

import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;

public final class ExtractItemPayload implements InventoryPacket {
    public static final Identifier ID = new Identifier("infinite-inventory", "extract_item");
    private final ItemStack stackToMatch;
    private final boolean single;

    public ExtractItemPayload(ItemStack stackToMatch, boolean single) {
        this.stackToMatch = stackToMatch;
        this.single = single;
    }

    public ExtractItemPayload(PacketByteBuf buf) {
        this(buf.readItemStack(), buf.readBoolean());
    }

    public ItemStack stackToMatch() { return stackToMatch; }
    public boolean single() { return single; }

    @Override
    public Identifier getId() { return ID; }

    @Override
    public void write(PacketByteBuf buf) {
        buf.writeItemStack(stackToMatch);
        buf.writeBoolean(single);
    }
}

