package com.robsterad.infiniteinv;

import com.robsterad.infiniteinv.inventory.ItemKey;
import com.robsterad.infiniteinv.inventory.PlayerInfiniteInventory;
import com.robsterad.infiniteinv.network.ExtractItemPayload;
import com.robsterad.infiniteinv.network.InsertItemPayload;
import com.robsterad.infiniteinv.network.SyncInventoryPayload;
import com.robsterad.infiniteinv.network.SyncUiPrefsPayload;
import com.robsterad.infiniteinv.network.UpdateUiPrefsPayload;
import com.robsterad.infiniteinv.storage.InfiniteInventoryState;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class InfiniteInv implements ModInitializer {

    @Override
    public void onInitialize() {
        // INSERT (SHIFT + CLICK)
        ServerPlayNetworking.registerGlobalReceiver(InsertItemPayload.ID, (payload, player, responseSender) -> {
            player.getServer().execute(() -> {
                ScreenHandler handler = player.currentScreenHandler;
                int slotId = payload.slotId();

                if (slotId >= 0 && slotId < handler.slots.size()) {
                    Slot slot = handler.slots.get(slotId);

                    if (slot.hasStack()) {
                        ItemStack stack = slot.getStack();
                        ItemKey key = ItemKey.of(stack);
                        try {
                            PlayerInfiniteInventory inv = InfiniteInventoryState.getPlayerState(player);

                            if (payload.single()) {
                                ItemStack toInsert = stack.copy();
                                toInsert.setCount(1);
                                stack.decrement(1);
                                if (stack.isEmpty()) {
                                    slot.setStack(ItemStack.EMPTY);
                                } else {
                                    slot.markDirty();
                                }
                                inv.addStack(toInsert);
                            } else {
                                inv.addStack(stack);
                                slot.setStack(ItemStack.EMPTY);
                            }

                            if (key != null) {
                                InfiniteInventoryState.updateTimestamp(player, key);
                            }

                            InfiniteInventoryState.getServerState(player.getServer()).markDirty();
                            syncInventoryToClient(player);
                        } catch (Exception e) {
                            System.err.println("Error at adding: " + e.getMessage());
                        }
                    }
                }
            });
        });

        // EXTRACT (CLICK ON ITEM IN UI)
        ServerPlayNetworking.registerGlobalReceiver(ExtractItemPayload.ID, (payload, player, responseSender) -> {
            player.getServer().execute(() -> {
                ItemStack requestedStack = payload.stackToMatch();
                PlayerInfiniteInventory inv = InfiniteInventoryState.getPlayerState(player);
                ItemKey key = ItemKey.of(requestedStack);

                if (key != null && inv.getAllItems().containsKey(key)) {
                    int maxTake = payload.single() ? 1 : requestedStack.getMaxCount();
                    ItemStack extracted = inv.removeStack(key, maxTake);

                    if (!extracted.isEmpty()) {
                        player.getInventory().insertStack(extracted);
                        if (extracted.getCount() > 0) {
                            player.dropItem(extracted, false);
                        }
                        InfiniteInventoryState.getServerState(player.getServer()).markDirty();
                        syncInventoryToClient(player);
                    }
                }
            });
        });

        // UI PREFS (panel visibility / sort mode / tooltip toggle)
        ServerPlayNetworking.registerGlobalReceiver(UpdateUiPrefsPayload.ID, (payload, player, responseSender) -> {
            player.getServer().execute(() -> {
                InfiniteInventoryState.setUiPrefs(player, new InfiniteInventoryState.UiPrefs(
                        payload.panelVisible(), payload.sortMode(), payload.showTooltips()));
            });
        });

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            syncInventoryToClient(handler.player);

            InfiniteInventoryState.UiPrefs prefs = InfiniteInventoryState.getUiPrefs(handler.player);
            ServerPlayNetworking.send(handler.player, new SyncUiPrefsPayload(
                    prefs.panelVisible(), prefs.sortMode(), prefs.showTooltips()));
        });
    }

    public static void syncInventoryToClient(ServerPlayerEntity player) {
        PlayerInfiniteInventory inv = InfiniteInventoryState.getPlayerState(player);
        InfiniteInventoryState state = InfiniteInventoryState.getServerState(player.getServer());
        Map<ItemKey, Long> timestamps = state.itemTimestamps.getOrDefault(player.getUuid(), new HashMap<>());

        List<SyncInventoryPayload.NetworkItemData> list = new ArrayList<>();
        for (Map.Entry<ItemKey, Long> entry : inv.getAllItems().entrySet()) {
            list.add(new SyncInventoryPayload.NetworkItemData(
                    entry.getKey().toStack(1),
                    entry.getValue(),
                    timestamps.getOrDefault(entry.getKey(), 0L)
            ));
        }
        ServerPlayNetworking.send(player, new SyncInventoryPayload(list));
    }
}
