package com.robsterad.infiniteinv.mixin;

import com.robsterad.infiniteinv.config.InfiniteInvConfig;
import com.robsterad.infiniteinv.gui.InfiniteInventoryOverlay;
import com.robsterad.infiniteinv.network.InsertItemPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractContainerScreen.class)
public abstract class HandledScreenMixin {

    @Shadow protected Slot hoveredSlot;

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void interceptDepositClick(MouseButtonEvent event, boolean bl, CallbackInfoReturnable<Boolean> cir) {
        if (!InfiniteInventoryOverlay.panelActive || !InfiniteInventoryOverlay.panelVisible) return;
        if (this.hoveredSlot == null || !this.hoveredSlot.hasItem()) return;

        int button = event.button();
        if (InfiniteInvConfig.INSTANCE.sendStack.matchesMouse(button)) {
            ClientPlayNetworking.send(new InsertItemPayload(this.hoveredSlot.index, false));
            cir.setReturnValue(true);
        } else if (InfiniteInvConfig.INSTANCE.sendOne.matchesMouse(button)) {
            ClientPlayNetworking.send(new InsertItemPayload(this.hoveredSlot.index, true));
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void interceptDepositKeyPress(KeyEvent event, CallbackInfoReturnable<Boolean> cir) {
        if (!InfiniteInventoryOverlay.panelActive || !InfiniteInventoryOverlay.panelVisible) return;
        if (InfiniteInventoryOverlay.isSearchFocused()) return;
        if (this.hoveredSlot == null || !this.hoveredSlot.hasItem()) return;

        int key = event.key();
        int keycode = event.keycode();
        if (InfiniteInvConfig.INSTANCE.sendStack.matchesKey(key, keycode)) {
            ClientPlayNetworking.send(new InsertItemPayload(this.hoveredSlot.index, false));
            cir.setReturnValue(true);
        } else if (InfiniteInvConfig.INSTANCE.sendOne.matchesKey(key, keycode)) {
            ClientPlayNetworking.send(new InsertItemPayload(this.hoveredSlot.index, true));
            cir.setReturnValue(true);
        }
    }
}
