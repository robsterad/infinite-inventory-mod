package com.robsterad.infiniteinv.gui;

import com.robsterad.infiniteinv.config.InfiniteInvConfig;
import com.robsterad.infiniteinv.network.ExtractItemPayload;
import com.robsterad.infiniteinv.network.SyncInventoryPayload;
import com.robsterad.infiniteinv.network.UpdateUiPrefsPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.item.EnchantedBookItem;
import net.minecraft.nbt.NbtList;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.client.util.math.MatrixStack;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class InfiniteInventoryOverlay {

    private static TextFieldWidget searchBox;
    private static ButtonWidget sortButton, prevPageBtn, nextPageBtn, tooltipBtn, collapseBtn;
    public static List<SyncInventoryPayload.NetworkItemData> cachedItems = new ArrayList<>();

    public static boolean panelVisible = true;

    private enum SortMode {
        RECENT("N",   "Newest"),
        DESCENDING("▼", "Count: High→Low"),
        ASCENDING("▲",  "Count: Low→High"),
        ALPHA_ASC("A↑", "Name: A→Z"),
        ALPHA_DESC("A↓","Name: Z→A");

        final String shortLabel;
        final String hoverName;

        SortMode(String shortLabel, String hoverName) {
            this.shortLabel = shortLabel;
            this.hoverName  = hoverName;
        }
    }

    private static SortMode currentSort = SortMode.RECENT;
    private static int currentPage  = 0;
    private static int itemsPerPage = 1;
    private static boolean showTooltips = false;
    private static double lastMouseX = -1;
    private static double lastMouseY = -1;

    // collapse button width
    private static final int COLLAPSE_W = 20;

    public static void applyUiPrefs(boolean visible, String sortModeName, boolean tooltips) {
        panelVisible = visible;
        showTooltips = tooltips;
        try {
            currentSort = SortMode.valueOf(sortModeName);
        } catch (IllegalArgumentException e) {
            currentSort = SortMode.RECENT;
        }
        currentPage = 0;
        if (collapseBtn != null) collapseBtn.setMessage(Text.literal(panelVisible ? "◀" : "▶"));
        if (sortButton != null) sortButton.setMessage(Text.literal(currentSort.shortLabel));
    }

    private static void sendUiPrefsUpdate() {
        ClientPlayNetworking.send(new UpdateUiPrefsPayload(panelVisible, currentSort.name(), showTooltips));
    }

    public static boolean isSearchFocused() {
        return searchBox != null && searchBox.isFocused();
    }

    public static boolean onCharTyped(char chr, int modifiers) {
        if (searchBox != null && searchBox.isFocused()) {
            searchBox.charTyped(chr, modifiers);
            currentPage = 0;
            return true;
        }
        return false;
    }

    public static void register() {
        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            if (!(screen instanceof InventoryScreen)) return;

            int startX     = (scaledWidth / 2) + 93;
            int panelWidth = scaledWidth - startX - 5;
            if (panelWidth < 50) return;

            int bottomY = scaledHeight - 25;
            int sortW = 20;
            int toolW = 20;

            int searchW = panelWidth - sortW - toolW - COLLAPSE_W - 6;

            searchBox = new TextFieldWidget(client.textRenderer, startX, bottomY, searchW, 14, Text.literal(""));
            searchBox.setPlaceholder(Text.literal("Search..."));

            sortButton  = ButtonWidget.builder(Text.literal(currentSort.shortLabel), b -> {})
                    .dimensions(startX + searchW + 2, bottomY, sortW, 14).build();
            tooltipBtn  = ButtonWidget.builder(Text.literal("T"), b -> {})
                    .dimensions(startX + searchW + sortW + 4, bottomY, toolW, 14).build();
            collapseBtn = ButtonWidget.builder(Text.literal(panelVisible ? "◀" : "▶"), b -> {})
                    .dimensions(startX + searchW + sortW + toolW + 6, bottomY, COLLAPSE_W, 14).build();

            prevPageBtn = ButtonWidget.builder(Text.literal("<"), b -> {})
                    .dimensions(startX, 10, 20, 14).build();
            nextPageBtn = ButtonWidget.builder(Text.literal(">"), b -> {})
                    .dimensions(startX + panelWidth - 20, 10, 20, 14).build();

            // MOUSE CLICK
            ScreenMouseEvents.allowMouseClick(screen).register((s, mx, my, btn) -> {
                if (btn == 0 && collapseBtn.isMouseOver(mx, my)) {
                    panelVisible = !panelVisible;
                    collapseBtn.setMessage(Text.literal(panelVisible ? "◀" : "▶"));
                    sendUiPrefsUpdate();
                    return false;
                }

                // rest is only visible when overlay is on
                if (!panelVisible) return true;

                if (btn == 0) {
                    if (searchBox.isMouseOver(mx, my)) {
                        searchBox.setFocused(true);
                        searchBox.mouseClicked(mx, my, btn);
                        return false;
                    }
                    searchBox.setFocused(false);

                    if (sortButton.isMouseOver(mx, my)) {
                        currentSort = SortMode.values()[(currentSort.ordinal() + 1) % SortMode.values().length];
                        sortButton.setMessage(Text.literal(currentSort.shortLabel));
                        currentPage = 0;
                        sendUiPrefsUpdate();
                        return false;
                    }

                    if (tooltipBtn.isMouseOver(mx, my)) {
                        showTooltips = !showTooltips;
                        sendUiPrefsUpdate();
                        return false;
                    }

                    if (prevPageBtn.isMouseOver(mx, my)) {
                        if (currentPage > 0) currentPage--;
                        return false;
                    }
                    if (nextPageBtn.isMouseOver(mx, my)) {
                        currentPage++;
                        return false;
                    }
                }

                // click on items
                ItemStack hovered = findHoveredItemStack(mx, my, startX, panelWidth, scaledHeight);
                if (hovered != null) {
                    if (InfiniteInvConfig.INSTANCE.takeStack.matchesMouse(btn)) {
                        ClientPlayNetworking.send(new ExtractItemPayload(hovered, false));
                    } else if (InfiniteInvConfig.INSTANCE.takeOne.matchesMouse(btn)) {
                        ClientPlayNetworking.send(new ExtractItemPayload(hovered, true));
                    }
                    return false;
                }

                return true;
            });

            // KEYBOARD
            ScreenKeyboardEvents.allowKeyPress(screen).register((s, key, sc, mod) -> {
                if (!panelVisible) return true;
                if (searchBox.isFocused()) {
                    if (key == GLFW.GLFW_KEY_E || key == GLFW.GLFW_KEY_BACKSPACE) {
                        searchBox.keyPressed(key, sc, mod);
                        return false;
                    }
                    return true;
                }

                ItemStack hovered = findHoveredItemStack(lastMouseX, lastMouseY, startX, panelWidth, scaledHeight);
                if (hovered != null) {
                    if (InfiniteInvConfig.INSTANCE.takeStack.matchesKey(key, sc)) {
                        ClientPlayNetworking.send(new ExtractItemPayload(hovered, false));
                        return false;
                    } else if (InfiniteInvConfig.INSTANCE.takeOne.matchesKey(key, sc)) {
                        ClientPlayNetworking.send(new ExtractItemPayload(hovered, true));
                        return false;
                    }
                }
                return true;
            });

            // RENDER
            ScreenEvents.afterRender(screen).register((s, ctx, mx, my, delta) -> {
                lastMouseX = mx;
                lastMouseY = my;

                collapseBtn.render(ctx, mx, my, delta);

                if (!panelVisible) return;

                // panel background
                ctx.fill(startX, 8, scaledWidth - 5, scaledHeight - 8, 0x88222222);

                searchBox.render(ctx, mx, my, delta);
                sortButton.render(ctx, mx, my, delta);
                tooltipBtn.render(ctx, mx, my, delta);
                if (showTooltips) {
                    int tx = tooltipBtn.getX(), ty = tooltipBtn.getY();
                    int tw = tooltipBtn.getWidth(), th = tooltipBtn.getHeight();
                    int outline = 0xFFFFFFFF;
                    ctx.fill(tx, ty, tx + tw, ty + 1, outline);
                    ctx.fill(tx, ty + th - 1, tx + tw, ty + th, outline);
                    ctx.fill(tx, ty, tx + 1, ty + th, outline);
                    ctx.fill(tx + tw - 1, ty, tx + tw, ty + th, outline);
                }
                prevPageBtn.render(ctx, mx, my, delta);
                nextPageBtn.render(ctx, mx, my, delta);

                // PAGING
                List<SyncInventoryPayload.NetworkItemData> all = getSortedFilteredAll();
                int columns    = Math.max(1, (panelWidth - 8) / 18);
                int rows       = (scaledHeight - 65) / 18;
                itemsPerPage   = Math.max(1, columns * rows);
                int totalPages = Math.max(1, (int) Math.ceil((double) all.size() / itemsPerPage));
                currentPage    = Math.min(currentPage, Math.max(0, totalPages - 1));

                ctx.drawCenteredTextWithShadow(client.textRenderer,
                        (currentPage + 1) + "/" + totalPages,
                        startX + panelWidth / 2, 13, -1);

                List<SyncInventoryPayload.NetworkItemData> page = getProcessedItems(panelWidth, scaledHeight);
                ItemStack hoveredItem = null;

                for (int i = 0; i < page.size(); i++) {
                    int ix = startX + 4 + (i % columns) * 18;
                    int iy = 28 + (i / columns) * 18;

                    ctx.drawItem(page.get(i).stack(), ix, iy);


                    if (page.get(i).count() > 0) {
                        String countText = formatCount(page.get(i).count());
                        float scale      = 0.65f;
                        int   textW      = client.textRenderer.getWidth(countText);
                        int   textH      = client.textRenderer.fontHeight;


                        float anchorX = ix + 16f;
                        float anchorY = iy + 16f;

                        MatrixStack mat = ctx.getMatrices();
                        mat.push();
                        // Match vanilla's item-count depth so icons cannot cover the text.
                        mat.translate(anchorX, anchorY, 200);
                        mat.scale(scale, scale, 1);

                        ctx.drawTextWithShadow(client.textRenderer, countText, -textW, -textH, -1);
                        mat.pop();
                    }

                    if (showTooltips && mx >= ix && mx < ix + 18 && my >= iy && my < iy + 18) {
                        hoveredItem = page.get(i).stack();
                    }
                }


                // item tooltip
                if (hoveredItem != null) {
                    ctx.drawItemTooltip(client.textRenderer, hoveredItem, mx, my);
                }
            });
        });
    }

    private static ItemStack findHoveredItemStack(double mx, double my, int startX, int panelWidth, int scaledHeight) {
        if (!panelVisible) return null;
        List<SyncInventoryPayload.NetworkItemData> list = getProcessedItems(panelWidth, scaledHeight);
        int columns = Math.max(1, (panelWidth - 8) / 18);
        for (int i = 0; i < list.size(); i++) {
            int x = startX + 4 + (i % columns) * 18;
            int y = 28 + (i / columns) * 18;
            if (mx >= x && mx < x + 18 && my >= y && my < y + 18) {
                return list.get(i).stack();
            }
        }
        return null;
    }

    private static List<SyncInventoryPayload.NetworkItemData> getSortedFilteredAll() {
        String q = searchBox.getText().toLowerCase(Locale.ROOT);
        List<SyncInventoryPayload.NetworkItemData> list = new ArrayList<>();
        for (SyncInventoryPayload.NetworkItemData data : cachedItems) {
            if (matchesQuery(data.stack(), q))
                list.add(data);
        }

        list.sort((a, b) -> {
            switch (currentSort) {
                case DESCENDING: {
                    int c = Long.compare(b.count(), a.count());
                    return c != 0 ? c : a.stack().getName().getString().compareToIgnoreCase(b.stack().getName().getString());
                }
                case ASCENDING: {
                    int c = Long.compare(a.count(), b.count());
                    return c != 0 ? c : a.stack().getName().getString().compareToIgnoreCase(b.stack().getName().getString());
                }
                case ALPHA_ASC:
                    return a.stack().getName().getString().compareToIgnoreCase(b.stack().getName().getString());
                case ALPHA_DESC:
                    return b.stack().getName().getString().compareToIgnoreCase(a.stack().getName().getString());
                default: { // RECENT
                    int c = Long.compare(b.timestamp(), a.timestamp());
                    return c != 0 ? c : a.stack().getName().getString().compareToIgnoreCase(b.stack().getName().getString());
                }
            }
        });

        return list;
    }

    private static List<SyncInventoryPayload.NetworkItemData> getProcessedItems(int panelWidth, int scaledHeight) {
        List<SyncInventoryPayload.NetworkItemData> all = getSortedFilteredAll();
        int columns    = Math.max(1, (panelWidth - 8) / 18);
        int rows       = (scaledHeight - 65) / 18;
        itemsPerPage   = Math.max(1, columns * rows);
        int start = currentPage * itemsPerPage;
        if (start >= all.size()) return new ArrayList<>();
        return all.subList(start, Math.min(start + itemsPerPage, all.size()));
    }

    private static boolean matchesQuery(ItemStack stack, String q) {
        if (q.isEmpty()) return true;
        if (stack.getName().getString().toLowerCase(Locale.ROOT).contains(q)) return true;
        if (stack.getItem().getName(stack).getString().toLowerCase(Locale.ROOT).contains(q)) return true;
        // In 1.20, stored and applied enchantments are NBT lists rather than components.
        if (enchantmentMatches(EnchantedBookItem.getEnchantmentNbt(stack), q)) return true;
        if (enchantmentMatches(stack.getEnchantments(), q)) return true;
        return false;
    }

    private static boolean enchantmentMatches(NbtList enchants, String q) {
        for (var entry : EnchantmentHelper.fromNbt(enchants).entrySet()) {
            // full "Name Level" text (e.g. "Sharpness V"), same as the vanilla tooltip line,
            // so searching "sharpness v" or just "v" matches like it does in creative search
            if (entry.getKey().getName(entry.getValue()).getString().toLowerCase(Locale.ROOT).contains(q))
                return true;
        }
        return false;
    }

    private static String formatCount(long count) {
        if (count >= 1_000_000) return String.format(Locale.ROOT, "%.1fM", count / 1_000_000.0);
        if (count >= 1_000)     return String.format(Locale.ROOT, "%.1fk", count / 1_000.0);
        return String.valueOf(count);
    }
}
