package com.lycoris.loconautics.content.combustion;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

public final class CombustionFuel {

    private static volatile Map<Fluid, CombustionFuel> byFluid = Map.of();
    private static volatile List<CombustionFuel> byTag = List.of();

    private final String key;
    private final @Nullable TagKey<Fluid> tag;
    private final float topRpm;
    private final float burnPerTick;

    CombustionFuel(String key, @Nullable ResourceLocation tagId, float topRpm, float ticksPerMb) {
        this.key = key;
        this.tag = tagId == null ? null : TagKey.create(Registries.FLUID, tagId);
        this.topRpm = topRpm;
        this.burnPerTick = 1.0f / ticksPerMb;
    }

    public String key() {
        return key;
    }

    public float topRpm() {
        return topRpm;
    }

    public float burnPerTick() {
        return burnPerTick;
    }

    static void replace(Map<Fluid, CombustionFuel> fluids, List<CombustionFuel> tags) {
        byFluid = Map.copyOf(fluids);
        byTag = List.copyOf(tags);
    }

    @Nullable
    public static CombustionFuel of(FluidStack stack) {
        return stack.isEmpty() ? null : of(stack.getFluid());
    }

    @Nullable
    public static CombustionFuel of(Fluid fluid) {
        CombustionFuel exact = byFluid.get(fluid);
        if (exact != null) return exact;
        if (fluid instanceof FlowingFluid flowing) {
            exact = byFluid.get(flowing.getSource());
            if (exact != null) return exact;
        }
        for (CombustionFuel fuel : byTag) {
            if (fluid.defaultFluidState().is(fuel.tag)) return fuel;
        }
        return null;
    }

    public static boolean accepts(FluidStack stack) {
        return of(stack) != null;
    }
}
