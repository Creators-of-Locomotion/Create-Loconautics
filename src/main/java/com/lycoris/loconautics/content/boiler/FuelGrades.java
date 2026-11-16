package com.lycoris.loconautics.content.boiler;

import com.lycoris.loconautics.LoconauticsConstants;
import com.lycoris.loconautics.config.LoconauticsConfig;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;

public final class FuelGrades {

    public static final TagKey<Item> POOR_FUEL = TagKey.create(Registries.ITEM, LoconauticsConstants.id("boiler_fuel/poor"));
    public static final TagKey<Item> PREMIUM_FUEL = TagKey.create(Registries.ITEM, LoconauticsConstants.id("boiler_fuel/premium"));

    private FuelGrades() {
    }

    // tags win, anything else is graded by how long one item burns
    public static FuelGrade of(ItemStack stack) {
        if (stack.is(PREMIUM_FUEL)) return FuelGrade.PREMIUM;
        if (stack.is(POOR_FUEL)) return FuelGrade.POOR;
        int burn = stack.getBurnTime(RecipeType.SMELTING);
        if (burn <= LoconauticsConfig.POOR_FUEL_BURN_LIMIT.get()) return FuelGrade.POOR;
        if (burn > LoconauticsConfig.PREMIUM_FUEL_BURN_LIMIT.get()) return FuelGrade.PREMIUM;
        return FuelGrade.NORMAL;
    }
}
