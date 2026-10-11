package com.robsterad.infiniteinv.mixin;

import com.robsterad.infiniteinv.config.InfiniteInvConfig;
import com.robsterad.infiniteinv.gui.InfiniteInventoryOverlay;
import com.robsterad.infiniteinv.network.InsertItemPayload;
import com.robsterad.infiniteinv.network.ClientPackets;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.screen.slot.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(HandledScreen.class)
public abstract class HandledScreenMixin {

    @Shadow protected Slot focusedSlot;

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void interceptDepositClick(double mouseX, double mouseY, int button, CallbackInfoReturnable<Boolean> cir) {
        if (!((Object) this instanceof InventoryScreen)) return;
        if (!InfiniteInventoryOverlay.panelVisible) return;
        if (this.focusedSlot == null || !this.focusedSlot.hasStack()) return;

        if (InfiniteInvConfig.INSTANCE.sendStack.matchesMouse(button)) {
            ClientPackets.send(new InsertItemPayload(this.focusedSlot.id, false));
            cir.setReturnValue(true);
        } else if (InfiniteInvConfig.INSTANCE.sendOne.matchesMouse(button)) {
            ClientPackets.send(new InsertItemPayload(this.focusedSlot.id, true));
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void interceptDepositKeyPress(int key, int scancode, int modifiers, CallbackInfoReturnable<Boolean> cir) {
        if (!((Object) this instanceof InventoryScreen)) return;
        if (!InfiniteInventoryOverlay.panelVisible) return;
        if (InfiniteInventoryOverlay.isSearchFocused()) return;
        if (this.focusedSlot == null || !this.focusedSlot.hasStack()) return;

        if (InfiniteInvConfig.INSTANCE.sendStack.matchesKey(key, scancode)) {
            ClientPackets.send(new InsertItemPayload(this.focusedSlot.id, false));
            cir.setReturnValue(true);
        } else if (InfiniteInvConfig.INSTANCE.sendOne.matchesKey(key, scancode)) {
            ClientPackets.send(new InsertItemPayload(this.focusedSlot.id, true));
            cir.setReturnValue(true);
        }
    }
}
