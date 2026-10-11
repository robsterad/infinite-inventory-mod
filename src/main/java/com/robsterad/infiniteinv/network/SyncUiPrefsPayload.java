package com.robsterad.infiniteinv.network;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;

public final class SyncUiPrefsPayload implements InventoryPacket {
    public static final Identifier ID = new Identifier("infinite-inventory", "sync_ui_prefs");
    private final boolean panelVisible;
    private final String sortMode;
    private final boolean showTooltips;

    public SyncUiPrefsPayload(boolean panelVisible, String sortMode, boolean showTooltips) {
        this.panelVisible = panelVisible;
        this.sortMode = sortMode;
        this.showTooltips = showTooltips;
    }

    public SyncUiPrefsPayload(PacketByteBuf buf) {
        this(buf.readBoolean(), buf.readString(32767), buf.readBoolean());
    }

    public boolean panelVisible() { return panelVisible; }
    public String sortMode() { return sortMode; }
    public boolean showTooltips() { return showTooltips; }

    @Override
    public Identifier getId() { return ID; }

    @Override
    public void write(PacketByteBuf buf) {
        buf.writeBoolean(panelVisible);
        buf.writeString(sortMode);
        buf.writeBoolean(showTooltips);
    }
}

