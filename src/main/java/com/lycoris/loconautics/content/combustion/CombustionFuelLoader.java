package com.lycoris.loconautics.content.combustion;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.lycoris.loconautics.LoconauticsConstants;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.level.material.Fluid;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class CombustionFuelLoader extends SimpleJsonResourceReloadListener {

    private static final Gson GSON = new Gson();
    public static final String DIRECTORY = "combustion_fuel";

    public CombustionFuelLoader() {
        super(GSON, DIRECTORY);
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager manager,
                         ProfilerFiller profiler) {
        Map<Fluid, CombustionFuel> fluids = new LinkedHashMap<>();
        List<CombustionFuel> tags = new ArrayList<>();

        for (var entry : files.entrySet()) {
            try {
                parse(GSON.fromJson(entry.getValue(), JsonObject.class), fluids, tags);
            } catch (IllegalArgumentException | IllegalStateException | ClassCastException | NullPointerException ex) {
                LoconauticsConstants.LOGGER.error("[Fuel] bad combustion fuel file {}", entry.getKey(), ex);
            }
        }

        CombustionFuel.replace(fluids, tags);
        LoconauticsConstants.LOGGER.info("[Fuel] loaded {} fluid and {} tag combustion fuels",
                fluids.size(), tags.size());
    }

    private static void parse(JsonObject o, Map<Fluid, CombustionFuel> fluids, List<CombustionFuel> tags) {
        String name = o.get("fluid").getAsString();
        float rpm = o.get("rpm").getAsFloat();
        float ticksPerMb = o.get("ticks_per_mb").getAsFloat();
        if (rpm <= 0f || ticksPerMb <= 0f) {
            throw new IllegalArgumentException("rpm and ticks_per_mb must be above zero for " + name);
        }
        if (name.startsWith("#")) {
            tags.add(new CombustionFuel(name, ResourceLocation.parse(name.substring(1)), rpm, ticksPerMb));
            return;
        }
        ResourceLocation id = ResourceLocation.parse(name);
        Fluid fluid = BuiltInRegistries.FLUID.getOptional(id).orElse(null);
        if (fluid == null) {
            LoconauticsConstants.LOGGER.debug("[Fuel] fuel for absent fluid {}", id);
            return;
        }
        fluids.put(fluid, new CombustionFuel(id.toString(), null, rpm, ticksPerMb));
    }
}
