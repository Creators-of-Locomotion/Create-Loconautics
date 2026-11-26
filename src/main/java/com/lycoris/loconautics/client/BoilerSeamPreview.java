package com.lycoris.loconautics.client;

import com.lycoris.loconautics.content.boiler.BoilerAttach;
import com.lycoris.loconautics.content.boiler.BoilerShapes;
import com.lycoris.loconautics.content.boiler.barrel.BoilerBarrelBlock;
import com.lycoris.loconautics.content.boiler.firebox.FireboxBlock;
import com.lycoris.loconautics.content.boiler.firebox.FireboxSeams;
import com.simibubi.create.AllItems;
import com.simibubi.create.CreateClient;
import net.createmod.catnip.outliner.Outliner;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

public final class BoilerSeamPreview {
    private static final int ACTIVE_COLOR = 0xF4C25B;
    private static final int PASSIVE_COLOR = 0x8C8C8C;
    private static final float ACTIVE_WIDTH = 1 / 16f;
    private static final float PASSIVE_WIDTH = 1 / 32f;

    private BoilerSeamPreview() {
    }

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        ClientLevel level = mc.level;
        if (player == null || level == null || player.isShiftKeyDown() || mc.screen != null) {
            return;
        }
        if (!(mc.hitResult instanceof BlockHitResult hit)) {
            return;
        }
        if (!AllItems.WRENCH.isIn(player.getMainHandItem())) {
            return;
        }
        BlockPos pos = hit.getBlockPos();
        BlockState state = level.getBlockState(pos);
        boolean firebox = state.getBlock() instanceof FireboxBlock;
        if (!firebox && !(state.getBlock() instanceof BoilerBarrelBlock)) {
            return;
        }
        Direction.Axis axis = lengthAxis(state, firebox);
        if (hit.getDirection().getAxis() == axis || firebox && hit.getDirection().getAxis().isVertical()) {
            return;
        }
        List<Direction> ends = firebox ? FireboxSeams.partners(level, pos, state) : barrelEnds(level, pos, state);
        if (ends.isEmpty()) {
            return;
        }
        Direction aimed = firebox
                ? FireboxSeams.aimedEnd(state, pos, hit.getLocation())
                : nearerEnd(axis, pos, hit.getLocation());
        double lift = state.getValue(BoilerAttach.RAISED) ? BoilerAttach.BULGE : 0.0;
        for (Direction end : ends) {
            drawRing(pos, axis, firebox, end, lift, end == aimed);
        }
        if (aimed == null || !ends.contains(aimed)) {
            return;
        }
        boolean joined = firebox
                ? FireboxSeams.isJoined(level, pos, state, aimed)
                : BoilerBarrelBlock.joinsToward(state, aimed);
        List<MutableComponent> tip = new ArrayList<>(2);
        tip.add(Component.translatable("loconautics.boiler_seam.title"));
        tip.add(Component.translatable(joined
                ? "loconautics.boiler_seam.sever"
                : "loconautics.boiler_seam.rejoin"));
        CreateClient.VALUE_SETTINGS_HANDLER.showHoverTip(tip);
    }

    private static Direction.Axis lengthAxis(BlockState state, boolean firebox) {
        if (firebox) {
            return state.getValue(BlockStateProperties.HORIZONTAL_FACING).getAxis();
        }
        return state.getValue(BoilerBarrelBlock.FACING).getAxis();
    }

    private static List<Direction> barrelEnds(ClientLevel level, BlockPos pos, BlockState state) {
        List<Direction> ends = new ArrayList<>(2);
        BoilerBarrelBlock barrel = (BoilerBarrelBlock) state.getBlock();
        Direction.Axis axis = state.getValue(BoilerBarrelBlock.FACING).getAxis();
        for (Direction.AxisDirection sign : Direction.AxisDirection.values()) {
            Direction end = Direction.fromAxisAndDirection(axis, sign);
            if (barrel.hasPartner(level, pos, end)) {
                ends.add(end);
            }
        }
        return ends;
    }

    private static Direction nearerEnd(Direction.Axis axis, BlockPos pos, Vec3 click) {
        Vec3 rel = click.subtract(Vec3.atCenterOf(pos));
        Direction positive = Direction.fromAxisAndDirection(axis, Direction.AxisDirection.POSITIVE);
        return axis.choose(rel.x, rel.y, rel.z) >= 0 ? positive : positive.getOpposite();
    }

    private static void drawRing(BlockPos pos, Direction.Axis axis, boolean firebox, Direction end,
                                 double lift, boolean active) {
        double along = end.getAxisDirection() == Direction.AxisDirection.POSITIVE ? 1.0 : 0.0;
        Vec3 origin = Vec3.atLowerCornerOf(pos).add(0.0, lift, 0.0);
        List<Vec3> ring = BoilerShapes.seamRing(axis, firebox, along);
        Outliner outliner = Outliner.getInstance();
        for (int i = 0; i < ring.size(); i++) {
            Vec3 from = origin.add(ring.get(i));
            Vec3 to = origin.add(ring.get((i + 1) % ring.size()));
            outliner.showLine("loconautics:boiler_seam_" + end.ordinal() + "_" + i, from, to)
                    .colored(active ? ACTIVE_COLOR : PASSIVE_COLOR)
                    .lineWidth(active ? ACTIVE_WIDTH : PASSIVE_WIDTH);
        }
    }
}
