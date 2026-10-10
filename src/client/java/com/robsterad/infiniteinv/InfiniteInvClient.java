package com.robsterad.infiniteinv;

import com.robsterad.infiniteinv.config.InfiniteInvConfig;
import com.robsterad.infiniteinv.gui.InfiniteInventoryOverlay;
import com.robsterad.infiniteinv.network.SyncInventoryPayload;
import com.robsterad.infiniteinv.network.SyncUiPrefsPayload;
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

        ClientPlayNetworking.registerGlobalReceiver(SyncInventoryPayload.ID, (payload, context) -> {
            context.client().execute(() -> {
                InfiniteInventoryOverlay.cachedItems = payload.items();
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(SyncUiPrefsPayload.ID, (payload, context) -> {
            context.client().execute(() -> {
                InfiniteInventoryOverlay.applyUiPrefs(payload.panelVisible(), payload.sortMode(), payload.showTooltips());
            });
        });
    }
}
