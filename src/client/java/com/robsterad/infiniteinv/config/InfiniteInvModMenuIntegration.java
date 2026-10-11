package com.robsterad.infiniteinv.config;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import me.shedaniel.clothconfig2.gui.ClothConfigScreen;
import me.shedaniel.clothconfig2.gui.widget.SearchFieldEntry;
import net.minecraft.text.Text;

public class InfiniteInvModMenuIntegration implements ModMenuApi {

    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return parent -> {
            ConfigBuilder builder = ConfigBuilder.create()
                    .setParentScreen(parent)
                    .setTitle(Text.literal("Infinite Inventory"));

            ConfigEntryBuilder entryBuilder = builder.entryBuilder();
            ConfigCategory keybinds = builder.getOrCreateCategory(Text.literal("Keybinds"));

            keybinds.addEntry(entryBuilder.startModifierKeyCodeField(
                            Text.literal("Take one item"), InfiniteInvConfig.INSTANCE.takeOne)
                    .setDefaultValue(InfiniteInvConfig.DEFAULT_TAKE_ONE)
                    .setModifierSaveConsumer(code -> InfiniteInvConfig.INSTANCE.takeOne = code)
                    .build());

            keybinds.addEntry(entryBuilder.startModifierKeyCodeField(
                            Text.literal("Take entire stack"), InfiniteInvConfig.INSTANCE.takeStack)
                    .setDefaultValue(InfiniteInvConfig.DEFAULT_TAKE_STACK)
                    .setModifierSaveConsumer(code -> InfiniteInvConfig.INSTANCE.takeStack = code)
                    .build());

            keybinds.addEntry(entryBuilder.startModifierKeyCodeField(
                            Text.literal("Send one item"), InfiniteInvConfig.INSTANCE.sendOne)
                    .setDefaultValue(InfiniteInvConfig.DEFAULT_SEND_ONE)
                    .setModifierSaveConsumer(code -> InfiniteInvConfig.INSTANCE.sendOne = code)
                    .build());

            keybinds.addEntry(entryBuilder.startModifierKeyCodeField(
                            Text.literal("Send entire stack"), InfiniteInvConfig.INSTANCE.sendStack)
                    .setDefaultValue(InfiniteInvConfig.DEFAULT_SEND_STACK)
                    .setModifierSaveConsumer(code -> InfiniteInvConfig.INSTANCE.sendStack = code)
                    .build());

            builder.setSavingRunnable(InfiniteInvConfig::save);

            // Cloth Config always injects a search box as the first row of a category's entry
            // list; with only 4 keybinds it's pure clutter, and there's no public toggle for it,
            // so strip it out post-init (both the type and the listWidget field are public API).
            builder.setAfterInitConsumer(screen -> {
                if (screen instanceof ClothConfigScreen ccs) {
                    java.util.List<?> children = ccs.listWidget.children();
                    children.removeIf(entry -> entry instanceof SearchFieldEntry);
                }
            });

            return builder.build();
        };
    }
}
