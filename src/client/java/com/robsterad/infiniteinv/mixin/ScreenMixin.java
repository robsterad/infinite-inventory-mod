package com.robsterad.infiniteinv.mixin;

import com.robsterad.infiniteinv.gui.InfiniteInventoryOverlay;
import net.minecraft.client.Keyboard;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Keyboard.class)
public class ScreenMixin {

    @Inject(method = "onChar", at = @At("HEAD"), cancellable = true)
    private void interceptCharTyped(long window, int codePoint, int modifiers, CallbackInfo ci) {
        MinecraftClient client = MinecraftClient.getInstance();

        if (client.currentScreen == null) return;

        if (client.currentScreen instanceof InventoryScreen) {
            if (InfiniteInventoryOverlay.onCharTyped((char) codePoint, modifiers)) {
                ci.cancel();
            }
        }
    }
}