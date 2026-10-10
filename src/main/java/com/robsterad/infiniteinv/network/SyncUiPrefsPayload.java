package com.robsterad.infiniteinv.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record SyncUiPrefsPayload(boolean panelVisible, String sortMode, boolean showTooltips) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<SyncUiPrefsPayload> ID =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath("infinite-inventory", "sync_ui_prefs"));

    @Override
    public CustomPacketPayload.Type<SyncUiPrefsPayload> type() {
        return ID;
    }

    public static final StreamCodec<RegistryFriendlyByteBuf, SyncUiPrefsPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, SyncUiPrefsPayload::panelVisible,
            ByteBufCodecs.STRING_UTF8, SyncUiPrefsPayload::sortMode,
            ByteBufCodecs.BOOL, SyncUiPrefsPayload::showTooltips,
            SyncUiPrefsPayload::new
    );
}
