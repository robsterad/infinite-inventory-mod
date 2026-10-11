package com.robsterad.infiniteinv.network;

import net.fabricmc.fabric.api.networking.v1.FabricPacket;
import net.fabricmc.fabric.api.networking.v1.PacketType;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;

public record SyncUiPrefsPayload(boolean panelVisible, String sortMode, boolean showTooltips) implements FabricPacket {
    public static final PacketType<SyncUiPrefsPayload> ID = PacketType.create(
            new Identifier("infinite-inventory", "sync_ui_prefs"), SyncUiPrefsPayload::new);

    public SyncUiPrefsPayload(PacketByteBuf buf) {
        this(buf.readBoolean(), buf.readString(), buf.readBoolean());
    }

    @Override
    public PacketType<SyncUiPrefsPayload> getType() {
        return ID;
    }

    @Override
    public void write(PacketByteBuf buf) {
        buf.writeBoolean(panelVisible);
        buf.writeString(sortMode);
        buf.writeBoolean(showTooltips);
    }
}
