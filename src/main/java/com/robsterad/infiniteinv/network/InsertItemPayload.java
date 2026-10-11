package com.robsterad.infiniteinv.network;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;

public final class InsertItemPayload implements InventoryPacket {
    public static final Identifier ID = new Identifier("infinite-inventory", "insert_item");
    private final int slotId;
    private final boolean single;

    public InsertItemPayload(int slotId, boolean single) {
        this.slotId = slotId;
        this.single = single;
    }

    public InsertItemPayload(PacketByteBuf buf) {
        this(buf.readInt(), buf.readBoolean());
    }

    public int slotId() { return slotId; }
    public boolean single() { return single; }

    @Override
    public Identifier getId() { return ID; }

    @Override
    public void write(PacketByteBuf buf) {
        buf.writeInt(slotId);
        buf.writeBoolean(single);
    }
}

