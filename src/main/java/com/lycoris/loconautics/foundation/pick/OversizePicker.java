package com.lycoris.loconautics.foundation.pick;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

public final class OversizePicker {

    private static final double SAMPLE_STEP = 0.2;
    private static final double MAX_LENGTH = 16.0;

    private OversizePicker() {
    }

    // a ray only tests the cells it crosses, so shapes that leave their cell are tested from the neighbors
    public static BlockHitResult extend(BlockGetter level, ClipContext context, BlockHitResult vanilla) {
        Vec3 from = context.getFrom();
        Vec3 to = context.getTo();
        Vec3 delta = to.subtract(from);
        double length = delta.length();
        if (length < 1.0e-6 || length > MAX_LENGTH) return vanilla;

        BlockHitResult best = vanilla;
        double bestDist = vanilla.getType() == HitResult.Type.MISS
                ? Double.MAX_VALUE : from.distanceToSqr(vanilla.getLocation());

        LongOpenHashSet cells = new LongOpenHashSet();
        LongOpenHashSet owners = new LongOpenHashSet();
        BlockPos.MutableBlockPos cell = new BlockPos.MutableBlockPos();
        BlockPos.MutableBlockPos scan = new BlockPos.MutableBlockPos();
        int steps = (int) Math.ceil(length / SAMPLE_STEP);
        for (int i = 0; i <= steps; i++) {
            double t = (double) i / steps;
            cell.set(from.x + delta.x * t, from.y + delta.y * t, from.z + delta.z * t);
            if (!cells.add(cell.asLong())) continue;
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        scan.set(cell.getX() + dx, cell.getY() + dy, cell.getZ() + dz);
                        if (!owners.add(scan.asLong())) continue;
                        BlockState state = level.getBlockState(scan);
                        if (!(state.getBlock() instanceof OversizeShape)) continue;
                        BlockPos owner = scan.immutable();
                        BlockHitResult hit = clipOwner(level, context, owner, state, from, to);
                        if (hit == null) continue;
                        double dist = from.distanceToSqr(hit.getLocation());
                        if (dist < bestDist) {
                            best = hit;
                            bestDist = dist;
                        }
                    }
                }
            }
        }
        return best;
    }

    @Nullable
    private static BlockHitResult clipOwner(BlockGetter level, ClipContext context, BlockPos owner,
                                            BlockState state, Vec3 from, Vec3 to) {
        VoxelShape shape = context.getBlockShape(state, level, owner);
        if (shape.isEmpty()) return null;
        return level.clipWithInteractionOverride(from, to, owner, shape, state);
    }
}
