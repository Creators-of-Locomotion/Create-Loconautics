package com.lycoris.loconautics.client.connected;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonObject;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.Material;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.ModelState;
import net.minecraft.client.resources.model.UnbakedModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.neoforged.neoforge.client.model.geometry.IGeometryBakingContext;
import net.neoforged.neoforge.client.model.geometry.IGeometryLoader;
import net.neoforged.neoforge.client.model.geometry.IUnbakedGeometry;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

public class ConnectedVariantModelLoader implements IGeometryLoader<ConnectedVariantModelLoader.Geometry> {

    private static final String[] KEYS = {"single", "start", "middle", "end"};

    @Override
    public Geometry read(JsonObject json, JsonDeserializationContext context) {
        List<ResourceLocation> parts = new ArrayList<>(KEYS.length + 4);
        ResourceLocation fallback = ResourceLocation.parse(GsonHelper.getAsString(json, "single"));
        for (String key : KEYS) {
            parts.add(json.has(key) ? ResourceLocation.parse(GsonHelper.getAsString(json, key)) : fallback);
        }
        ResourceLocation end = parts.get(3);
        ResourceLocation endLong = json.has("end_long")
                ? ResourceLocation.parse(GsonHelper.getAsString(json, "end_long")) : end;
        parts.add(endLong);
        ResourceLocation singleOpen = openPart(json, "single_open", parts.get(0));
        ResourceLocation endOpen = openPart(json, "end_open", end);
        parts.add(singleOpen);
        parts.add(endOpen);
        parts.add(json.has("end_long_open") ? openPart(json, "end_long_open", endLong) : endOpen);
        return new Geometry(List.copyOf(parts), GsonHelper.getAsBoolean(json, "along", false));
    }

    private static ResourceLocation openPart(JsonObject json, String key, ResourceLocation closed) {
        return json.has(key) ? ResourceLocation.parse(GsonHelper.getAsString(json, key)) : closed;
    }

    public record Geometry(List<ResourceLocation> parts, boolean along) implements IUnbakedGeometry<Geometry> {

        @Override
        public void resolveParents(Function<ResourceLocation, UnbakedModel> modelGetter,
                                   IGeometryBakingContext context) {
            for (ResourceLocation part : parts) {
                modelGetter.apply(part).resolveParents(modelGetter);
            }
        }

        @Override
        public BakedModel bake(IGeometryBakingContext context, ModelBaker baker,
                               Function<Material, TextureAtlasSprite> spriteGetter,
                               ModelState modelState, ItemOverrides overrides) {
            List<BakedModel> baked = new ArrayList<>(parts.size());
            for (ResourceLocation part : parts) {
                baked.add(baker.bake(part, modelState, spriteGetter));
            }
            return new ConnectedVariantModel(baked.get(0), baked.get(1), baked.get(2), baked.get(3),
                    baked.get(4), baked.get(5), baked.get(6), baked.get(7), along);
        }
    }
}
