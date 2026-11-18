package com.lycoris.loconautics.foundation;

import net.minecraft.world.item.DyeColor;

import java.util.Arrays;
import java.util.List;

public final class ColorOrder {
    //remember when I said that depot adds its custom colors to the vanilla list? this is what I meant.
    //you can straight up(not that I am) use the vanilla list :D
    public static final List<DyeColor> ORDER = Arrays.stream(DyeColor.values()).toList();

    public static final List<String> EXTRA = List.of("industrial", "andesite", "brass", "copper", "zinc");

    public static final int EXTRA_START = ORDER.size();

    public static final int COUNT = EXTRA_START + EXTRA.size();

    private ColorOrder() {
    }

    public static int indexOf(DyeColor color) {
        return ORDER.indexOf(color);
    }

    public static int extraIndex(String name) {
        int at = EXTRA.indexOf(name);
        return at < 0 ? -1 : EXTRA_START + at;
    }

    public static DyeColor at(int index) {
        return ORDER.get(index);
    }
}
