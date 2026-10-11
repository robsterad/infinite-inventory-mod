package com.robsterad.infiniteinv.inventory;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import java.util.Objects;

public final class ItemKey {
    private final Item item;
    private final NbtCompound nbt;

    public ItemKey(Item item, NbtCompound nbt) {
        this.item = item;
        this.nbt = nbt;
    }

    public static ItemKey of(ItemStack stack) {
        if (stack.isEmpty()) return null;
        NbtCompound nbt = stack.getTag();
        return new ItemKey(stack.getItem(), nbt == null ? null : nbt.copy());
    }

    public ItemStack toStack(int count) {
        ItemStack stack = new ItemStack(item, count);
        if (nbt != null) stack.setTag(nbt.copy());
        return stack;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof ItemKey)) return false;
        ItemKey key = (ItemKey) other;
        return item == key.item && Objects.equals(nbt, key.nbt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(item, nbt);
    }
}

