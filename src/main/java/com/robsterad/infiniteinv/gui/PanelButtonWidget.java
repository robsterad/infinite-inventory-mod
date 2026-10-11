package com.robsterad.infiniteinv.gui;

import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;

/** Keeps both borders of the vanilla 20-pixel button texture on shorter buttons. */
public final class PanelButtonWidget extends ButtonWidget {
    public PanelButtonWidget(int x, int y, int width, int height, Text text, PressAction action) {
        super(x, y, width, height, text, action);
    }

    @Override
    public void drawTexture(MatrixStack matrices, int x, int y, int u, int v, int width, int height) {
        if (height < 2 || height >= 20) {
            super.drawTexture(matrices, x, y, u, v, width, height);
            return;
        }
        int topHeight = height / 2;
        int bottomHeight = height - topHeight;
        super.drawTexture(matrices, x, y, u, v, width, topHeight);
        super.drawTexture(matrices, x, y + topHeight, u, v + 20 - bottomHeight, width, bottomHeight);
    }
}
