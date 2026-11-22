package com.lycoris.loconautics.content.boiler.firebox;

import com.lycoris.loconautics.content.boiler.BoilerAttach;
import com.lycoris.loconautics.client.LoconauticsPartialModels;
import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.foundation.blockEntity.renderer.SafeBlockEntityRenderer;
import dev.engine_room.flywheel.api.visualization.VisualizationManager;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

public class FireboxRenderer extends SafeBlockEntityRenderer<FireboxBlockEntity> {

    static final float LEVER_DEGREES = 82.1f;

    static final float DOOR_DEGREES = 58.8f;

    static final float[] LEVER_PIVOT = pivot(8.0f, 12.0f, -1.0f);
    static final float[] DOOR_PIVOT = pivot(8.0f, 13.0f, -0.5f);

    public FireboxRenderer(BlockEntityRendererProvider.Context context) {
    }

    private static float[] pivot(float x, float y, float z) {
        return new float[]{x / 16.0f, y / 16.0f, z / 16.0f};
    }

    static float openProgress(float swing) {
        float left = 1.0f - Mth.clamp(swing, 0.0f, 1.0f);
        return 1.0f - left * left * left * left;
    }

    @Override
    protected void renderSafe(FireboxBlockEntity be, float partialTicks, PoseStack ms,
                              MultiBufferSource buffer, int light, int overlay) {
        if (VisualizationManager.supportsVisualization(be.getLevel())) return;

        BlockState state = be.getBlockState();
        if (state.getValue(FireboxBlock.EXTENSION)) return;

        Direction facing = state.getValue(BlockStateProperties.HORIZONTAL_FACING);
        float yaw = 180.0f - facing.toYRot();
        float swing = be.getDoorOpenTime(partialTicks) / FireboxBlockEntity.DOOR_SWING_TICKS;
        float progress = openProgress(swing);

        swing(ms, buffer, state, light, LoconauticsPartialModels.FIREBOX_LEVER_ARM,
                yaw, LEVER_PIVOT, LEVER_DEGREES * progress);
        swing(ms, buffer, state, light, LoconauticsPartialModels.FIREBOX_DOOR_LEFT,
                yaw, DOOR_PIVOT, -DOOR_DEGREES * progress);
        swing(ms, buffer, state, light, LoconauticsPartialModels.FIREBOX_DOOR_RIGHT,
                yaw, DOOR_PIVOT, DOOR_DEGREES * progress);
    }

    private static void swing(PoseStack ms, MultiBufferSource buffer, BlockState state, int light,
                              PartialModel model, float yaw, float[] hinge, float degrees) {
        SuperByteBuffer part = CachedBuffers.partial(model, state);
        float lift = state.getValue(BoilerAttach.RAISED) ? (float) BoilerAttach.BULGE : 0.0f;
        part.translate(0.0f, lift, 0.0f).center().rotateYDegrees(yaw).uncenter()
                .translate(hinge[0], hinge[1], hinge[2])
                .rotateZDegrees(degrees)
                .translate(-hinge[0], -hinge[1], -hinge[2]);
        part.light(light).renderInto(ms, buffer.getBuffer(RenderType.cutout()));
    }
}
