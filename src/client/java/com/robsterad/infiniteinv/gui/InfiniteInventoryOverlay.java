package com.robsterad.infiniteinv.gui;

import com.robsterad.infiniteinv.config.InfiniteInvConfig;
import com.robsterad.infiniteinv.mixin.AbstractContainerScreenAccessor;
import com.robsterad.infiniteinv.network.ExtractItemPayload;
import com.robsterad.infiniteinv.network.SyncInventoryPayload;
import com.robsterad.infiniteinv.network.UpdateUiPrefsPayload;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class InfiniteInventoryOverlay {

    private static EditBox searchBox;
    private static Button sortButton, prevPageBtn, nextPageBtn, tooltipBtn, collapseBtn;
    public static List<SyncInventoryPayload.NetworkItemData> cachedItems = new ArrayList<>();

    public static boolean panelVisible = true;
    public static boolean panelActive  = false;

    private enum SortMode {
        RECENT("N",    "Newest"),
        DESCENDING("▼", "Count: High→Low"),
        ASCENDING("▲",  "Count: Low→High"),
        ALPHA_ASC("A↑", "Name: A→Z"),
        ALPHA_DESC("A↓", "Name: Z→A");

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
        if (collapseBtn != null) collapseBtn.setMessage(Component.literal(panelVisible ? "◀" : "▶"));
        if (sortButton != null) sortButton.setMessage(Component.literal(currentSort.shortLabel));
    }

    private static void sendUiPrefsUpdate() {
        ClientPlayNetworking.send(new UpdateUiPrefsPayload(panelVisible, currentSort.name(), showTooltips));
    }

    public static boolean isSearchFocused() {
        return searchBox != null && searchBox.isFocused();
    }

    public static boolean onCharTyped(CharacterEvent event) {
        if (searchBox != null && searchBox.isFocused()) {
            searchBox.charTyped(event);
            currentPage = 0;
            return true;
        }
        return false;
    }

    public static void register() {
        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            panelActive = false;
            if (!(screen instanceof AbstractContainerScreen<?>)) return;

            AbstractContainerScreenAccessor acc = (AbstractContainerScreenAccessor) screen;
            int startX     = acc.getLeftPos() + acc.getImageWidth() + 5;
            int panelWidth = scaledWidth - startX - 5;
            if (panelWidth < 50) return;

            panelActive = true;

            int bottomY = scaledHeight - 25;
            int sortW = 20;
            int toolW = 20;

            int searchW = panelWidth - sortW - toolW - COLLAPSE_W - 6;

            searchBox = new EditBox(client.font, startX, bottomY, searchW, 14, Component.literal(""));
            searchBox.setHint(Component.literal("Search..."));

            sortButton  = Button.builder(Component.literal(currentSort.shortLabel), b -> {})
                    .pos(startX + searchW + 2, bottomY).size(sortW, 14).build();
            tooltipBtn  = Button.builder(Component.literal("T"), b -> {})
                    .pos(startX + searchW + sortW + 4, bottomY).size(toolW, 14).build();
            collapseBtn = Button.builder(Component.literal(panelVisible ? "◀" : "▶"), b -> {})
                    .pos(startX + searchW + sortW + toolW + 6, bottomY).size(COLLAPSE_W, 14).build();

            prevPageBtn = Button.builder(Component.literal("<"), b -> {})
                    .pos(startX, 10).size(20, 14).build();
            nextPageBtn = Button.builder(Component.literal(">"), b -> {})
                    .pos(startX + panelWidth - 20, 10).size(20, 14).build();

            // MOUSE CLICK
            ScreenMouseEvents.allowMouseClick(screen).register((s, event) -> {
                double mx = event.x();
                double my = event.y();
                int btn = event.button();

                if (btn == InputConstants.MOUSE_BUTTON_LEFT && collapseBtn.isMouseOver(mx, my)) {
                    panelVisible = !panelVisible;
                    collapseBtn.setMessage(Component.literal(panelVisible ? "◀" : "▶"));
                    sendUiPrefsUpdate();
                    return false;
                }

                if (!panelVisible) return true;

                if (btn == InputConstants.MOUSE_BUTTON_LEFT) {
                    if (searchBox.isMouseOver(mx, my)) {
                        searchBox.setFocused(true);
                        searchBox.mouseClicked(event, false);
                        return false;
                    }
                    searchBox.setFocused(false);

                    if (sortButton.isMouseOver(mx, my)) {
                        currentSort = SortMode.values()[(currentSort.ordinal() + 1) % SortMode.values().length];
                        sortButton.setMessage(Component.literal(currentSort.shortLabel));
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
            ScreenKeyboardEvents.allowKeyPress(screen).register((s, event) -> {
                if (!panelVisible) return true;
                if (searchBox.isFocused()) {
                    if (event.key() == InputConstants.KEY_E || event.key() == InputConstants.KEY_BACKSPACE) {
                        searchBox.keyPressed(event);
                        return false;
                    }
                    return true;
                }

                ItemStack hovered = findHoveredItemStack(lastMouseX, lastMouseY, startX, panelWidth, scaledHeight);
                if (hovered != null) {
                    if (InfiniteInvConfig.INSTANCE.takeStack.matchesKey(event.key(), event.keycode())) {
                        ClientPlayNetworking.send(new ExtractItemPayload(hovered, false));
                        return false;
                    } else if (InfiniteInvConfig.INSTANCE.takeOne.matchesKey(event.key(), event.keycode())) {
                        ClientPlayNetworking.send(new ExtractItemPayload(hovered, true));
                        return false;
                    }
                }
                return true;
            });

            // RENDER
            ScreenEvents.afterExtract(screen).register((s, ctx, mx, my, delta) -> {
                lastMouseX = mx;
                lastMouseY = my;

                collapseBtn.extractRenderState(ctx, mx, my, delta);

                if (!panelVisible) return;

                ctx.fill(startX, 8, scaledWidth - 5, scaledHeight - 8, 0x88222222);

                searchBox.extractRenderState(ctx, mx, my, delta);
                sortButton.extractRenderState(ctx, mx, my, delta);
                tooltipBtn.extractRenderState(ctx, mx, my, delta);
                if (showTooltips) {
                    int tx = tooltipBtn.getX(), ty = tooltipBtn.getY();
                    int tw = tooltipBtn.getWidth(), th = tooltipBtn.getHeight();
                    int outline = 0xFFFFFFFF;
                    ctx.fill(tx, ty, tx + tw, ty + 1, outline);
                    ctx.fill(tx, ty + th - 1, tx + tw, ty + th, outline);
                    ctx.fill(tx, ty, tx + 1, ty + th, outline);
                    ctx.fill(tx + tw - 1, ty, tx + tw, ty + th, outline);
                }
                prevPageBtn.extractRenderState(ctx, mx, my, delta);
                nextPageBtn.extractRenderState(ctx, mx, my, delta);

                // PAGING
                List<SyncInventoryPayload.NetworkItemData> all = getSortedFilteredAll();
                int columns    = Math.max(1, (panelWidth - 8) / 18);
                int rows       = (scaledHeight - 65) / 18;
                itemsPerPage   = Math.max(1, columns * rows);
                int totalPages = Math.max(1, (int) Math.ceil((double) all.size() / itemsPerPage));
                currentPage    = Math.min(currentPage, Math.max(0, totalPages - 1));

                ctx.centeredText(client.font,
                        (currentPage + 1) + "/" + totalPages,
                        startX + panelWidth / 2, 13, -1);

                List<SyncInventoryPayload.NetworkItemData> page = getProcessedItems(panelWidth, scaledHeight);
                ItemStack hoveredItem = null;

                for (int i = 0; i < page.size(); i++) {
                    int ix = startX + 4 + (i % columns) * 18;
                    int iy = 28 + (i / columns) * 18;

                    ctx.item(page.get(i).stack(), ix, iy);

                    if (page.get(i).count() > 0) {
                        String countText = formatCount(page.get(i).count());
                        float scale = 0.65f;
                        int textW   = client.font.width(countText);
                        int textH   = client.font.lineHeight;

                        float anchorX = ix + 16f;
                        float anchorY = iy + 16f;

                        ctx.pose().pushMatrix();
                        ctx.pose().translate(anchorX, anchorY);
                        ctx.pose().scale(scale, scale);
                        ctx.text(client.font, countText, -textW, -textH, -1);
                        ctx.pose().popMatrix();
                    }

                    if (showTooltips && mx >= ix && mx < ix + 18 && my >= iy && my < iy + 18) {
                        hoveredItem = page.get(i).stack();
                    }
                }

                if (sortButton.isMouseOver(mx, my))
                    ctx.setTooltipForNextFrame(client.font, Component.literal("Sort: " + currentSort.hoverName), mx, my);
                if (tooltipBtn.isMouseOver(mx, my))
                    ctx.setTooltipForNextFrame(client.font, Component.literal("Item Tooltips"), mx, my);
                if (collapseBtn.isMouseOver(mx, my))
                    ctx.setTooltipForNextFrame(client.font, Component.literal(panelVisible ? "Hide panel" : "Show panel"), mx, my);

                if (hoveredItem != null) {
                    ctx.setTooltipForNextFrame(client.font, hoveredItem, mx, my);
                    ctx.extractDeferredElements(mx, my, delta);
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
        String q = searchBox.getValue().toLowerCase(Locale.ROOT);
        List<SyncInventoryPayload.NetworkItemData> list = new ArrayList<>();
        for (SyncInventoryPayload.NetworkItemData data : cachedItems) {
            if (matchesQuery(data.stack(), q))
                list.add(data);
        }

        list.sort((a, b) -> {
            switch (currentSort) {
                case DESCENDING: {
                    int c = Long.compare(b.count(), a.count());
                    return c != 0 ? c : a.stack().getHoverName().getString().compareToIgnoreCase(b.stack().getHoverName().getString());
                }
                case ASCENDING: {
                    int c = Long.compare(a.count(), b.count());
                    return c != 0 ? c : a.stack().getHoverName().getString().compareToIgnoreCase(b.stack().getHoverName().getString());
                }
                case ALPHA_ASC:
                    return a.stack().getHoverName().getString().compareToIgnoreCase(b.stack().getHoverName().getString());
                case ALPHA_DESC:
                    return b.stack().getHoverName().getString().compareToIgnoreCase(a.stack().getHoverName().getString());
                default: { // RECENT
                    int c = Long.compare(b.timestamp(), a.timestamp());
                    return c != 0 ? c : a.stack().getHoverName().getString().compareToIgnoreCase(b.stack().getHoverName().getString());
                }
            }
        });

        return list;
    }

    private static List<SyncInventoryPayload.NetworkItemData> getProcessedItems(int panelWidth, int scaledHeight) {
        List<SyncInventoryPayload.NetworkItemData> all = getSortedFilteredAll();
        int columns  = Math.max(1, (panelWidth - 8) / 18);
        int rows     = (scaledHeight - 65) / 18;
        itemsPerPage = Math.max(1, columns * rows);
        int start = currentPage * itemsPerPage;
        if (start >= all.size()) return new ArrayList<>();
        return all.subList(start, Math.min(start + itemsPerPage, all.size()));
    }

    private static boolean matchesQuery(ItemStack stack, String q) {
        if (q.isEmpty()) return true;
        if (stack.getHoverName().getString().toLowerCase(Locale.ROOT).contains(q)) return true;
        if (stack.getItem().getName(stack).getString().toLowerCase(Locale.ROOT).contains(q)) return true;
        // enchanted books store enchantments under STORED_ENCHANTMENTS; enchanted gear uses ENCHANTMENTS
        if (enchantmentMatches(stack.get(DataComponents.STORED_ENCHANTMENTS), q)) return true;
        if (enchantmentMatches(stack.get(DataComponents.ENCHANTMENTS), q)) return true;
        return false;
    }

    private static boolean enchantmentMatches(ItemEnchantments enchants, String q) {
        if (enchants == null) return false;
        for (var entry : enchants.entrySet()) {
            // full "Name Level" text (e.g. "Sharpness V"), same as the vanilla tooltip line,
            // so searching "sharpness v" or just "v" matches like it does in creative search
            if (Enchantment.getFullname(entry.getKey(), entry.getIntValue()).getString().toLowerCase(Locale.ROOT).contains(q))
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
