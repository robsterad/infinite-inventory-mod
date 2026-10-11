package com.robsterad.infiniteinv.inventory;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

public record ItemKey(Item item, NbtCompound nbt) {

    public static ItemKey of(ItemStack stack) {
        if (stack.isEmpty()) {
            return null;
        }
        NbtCompound nbt = stack.getNbt();
        return new ItemKey(stack.getItem(), nbt == null ? null : nbt.copy());
    }

    public ItemStack toStack(int count) {
        ItemStack stack = new ItemStack(this.item, count);
        if (this.nbt != null) stack.setNbt(this.nbt.copy());
        return stack;
    }
}
