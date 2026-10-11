package com.robsterad.infiniteinv;

import com.robsterad.infiniteinv.config.InfiniteInvConfig;
import com.robsterad.infiniteinv.gui.InfiniteInventoryOverlay;
import com.robsterad.infiniteinv.network.SyncInventoryPayload;
import com.robsterad.infiniteinv.network.SyncUiPrefsPayload;
import net.minecraft.client.MinecraftClient;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

@Environment(EnvType.CLIENT)
public class InfiniteInvClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        InfiniteInvConfig.load();
        InfiniteInventoryOverlay.register();

        ClientPlayNetworking.registerGlobalReceiver(SyncInventoryPayload.ID, (client, handler, buf, responseSender) -> {
            SyncInventoryPayload payload = new SyncInventoryPayload(buf);
            MinecraftClient.getInstance().execute(() -> {
                InfiniteInventoryOverlay.cachedItems = payload.items();
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(SyncUiPrefsPayload.ID, (client, handler, buf, responseSender) -> {
            SyncUiPrefsPayload payload = new SyncUiPrefsPayload(buf);
            MinecraftClient.getInstance().execute(() -> {
                InfiniteInventoryOverlay.applyUiPrefs(payload.panelVisible(), payload.sortMode(), payload.showTooltips());
            });
        });
    }
}
