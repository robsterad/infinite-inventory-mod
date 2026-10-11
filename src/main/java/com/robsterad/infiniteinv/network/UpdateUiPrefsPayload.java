package com.robsterad.infiniteinv.network;

import net.fabricmc.fabric.api.networking.v1.FabricPacket;
import net.fabricmc.fabric.api.networking.v1.PacketType;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;

public record UpdateUiPrefsPayload(boolean panelVisible, String sortMode, boolean showTooltips) implements FabricPacket {
    public static final PacketType<UpdateUiPrefsPayload> ID = PacketType.create(
            new Identifier("infinite-inventory", "update_ui_prefs"), UpdateUiPrefsPayload::new);

    public UpdateUiPrefsPayload(PacketByteBuf buf) {
        this(buf.readBoolean(), buf.readString(), buf.readBoolean());
    }

    @Override
    public PacketType<UpdateUiPrefsPayload> getType() {
        return ID;
    }

    @Override
    public void write(PacketByteBuf buf) {
        buf.writeBoolean(panelVisible);
        buf.writeString(sortMode);
        buf.writeBoolean(showTooltips);
    }
}
