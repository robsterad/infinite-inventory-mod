package com.robsterad.infiniteinv.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record UpdateUiPrefsPayload(boolean panelVisible, String sortMode, boolean showTooltips) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<UpdateUiPrefsPayload> ID =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath("infinite-inventory", "update_ui_prefs"));

    @Override
    public CustomPacketPayload.Type<UpdateUiPrefsPayload> type() {
        return ID;
    }

    public static final StreamCodec<RegistryFriendlyByteBuf, UpdateUiPrefsPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, UpdateUiPrefsPayload::panelVisible,
            ByteBufCodecs.STRING_UTF8, UpdateUiPrefsPayload::sortMode,
            ByteBufCodecs.BOOL, UpdateUiPrefsPayload::showTooltips,
            UpdateUiPrefsPayload::new
    );
}
