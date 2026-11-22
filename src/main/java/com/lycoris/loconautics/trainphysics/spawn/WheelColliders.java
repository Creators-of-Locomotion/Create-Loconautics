package com.lycoris.loconautics.trainphysics.spawn;

import com.lycoris.loconautics.content.ghostwheel.GhostWheelBlock;
import com.lycoris.loconautics.registry.LoconauticsRegistries;
import com.lycoris.loconautics.trainphysics.SableTrain;
import com.lycoris.loconautics.trainphysics.SableTrain.Bogey;
import com.lycoris.loconautics.trainphysics.SableTrain.Car;
import com.lycoris.loconautics.trainphysics.state.CartEvent;
import com.lycoris.loconautics.trainphysics.state.CartLifecycle;
import com.lycoris.loconautics.trainphysics.wheels.GhostWheelProfile;
import com.simibubi.create.content.trains.bogey.AbstractBogeyBlock;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

public final class WheelColliders {

    private WheelColliders() {
    }

    private static final int MAX_REACH = GhostWheelProfile.MAX_ALONG;

    private static java.util.List<Vec3i> plateOffsets(int dy, Direction.Axis axis, int reach) {
        java.util.List<Vec3i> offsets = new java.util.ArrayList<>((2 * reach + 1) * 3);
        int xReach = axis == Direction.Axis.X ? reach : 1;
        int zReach = axis == Direction.Axis.X ? 1 : reach;
        for (int dx = -xReach; dx <= xReach; dx++) {
            for (int dz = -zReach; dz <= zReach; dz++) {
                offsets.add(new Vec3i(dx, dy, dz));
            }
        }
        return offsets;
    }

    public static boolean isMonorailBogey(BlockState bogey) {
        return com.lycoris.loconautics.common.track.TrackProfile.ofBogey(bogey).isMonorail();
    }

    public static boolean isUpsideDown(BlockState bogey) {
        return bogey.getBlock() instanceof AbstractBogeyBlock<?> block && block.isUpsideDown(bogey);
    }

    private static BlockState colliderBlock(Vec3i offset, boolean upsideDown, boolean monorail, Direction.Axis axis,
                                            int reach) {
        BlockState state = LoconauticsRegistries.GHOST_WHEEL.get().defaultBlockState()
                .setValue(GhostWheelBlock.UPSIDE_DOWN, upsideDown)
                .setValue(GhostWheelBlock.ON_MONORAIL, monorail)
                .setValue(GhostWheelBlock.AXIS, axis);
        int across = axis == Direction.Axis.X ? offset.getZ() : offset.getX();
        int along = axis == Direction.Axis.X ? offset.getX() : offset.getZ();
        if (Math.abs(along) == reach) {
            state = state.setValue(GhostWheelBlock.ALONG, along > 0 ? 1 : 2);
        }
        if (across == 0) {
            return state.setValue(GhostWheelBlock.MIDDLE, true);
        }
        return state.setValue(GhostWheelBlock.CORNER, across > 0 ? 1 : 2);
    }


    private static BlockPos bogeyPos(ServerSubLevel sub, Bogey bogey) {
        return sub.getPlot().getCenterBlock().offset(bogey.localPos()).immutable();
    }

    private static boolean bogeyAlive(ServerLevel level, BlockPos bogeyPos) {
        return level.getBlockState(bogeyPos).getBlock() instanceof AbstractBogeyBlock;
    }

    private static ServerSubLevel resolveSubLevel(SableTrain train) {
        Car car = train.car();
        if (car == null || car.subLevelId() == null) {
            return null;
        }
        ServerSubLevelContainer container = SubLevelContainer.getContainer(train.level());
        if (container == null
                || !(container.getSubLevel(car.subLevelId()) instanceof ServerSubLevel sub) || sub.isRemoved()) {
            return null;
        }
        return sub;
    }

    public static java.util.List<BlockPos> placePads(ServerLevel level, BlockPos bogeyPos, int reach) {
        BlockState bogeyState = level.getBlockState(bogeyPos);
        boolean upsideDown = isUpsideDown(bogeyState);
        boolean monorail = isMonorailBogey(bogeyState);
        Direction.Axis axis = bogeyState.hasProperty(AbstractBogeyBlock.AXIS)
                ? bogeyState.getValue(AbstractBogeyBlock.AXIS) : Direction.Axis.Z;
        java.util.List<Vec3i> offsets = plateOffsets(upsideDown ? 1 : -1, axis, reach);
        java.util.List<BlockPos> placed = new java.util.ArrayList<>(offsets.size());
        for (Vec3i offset : offsets) {
            BlockPos pos = bogeyPos.offset(offset).immutable();
            if (level.getBlockState(pos).isAir()) {
                level.setBlockAndUpdate(pos, colliderBlock(offset, upsideDown, monorail, axis, reach));
            }
            if (level.getBlockState(pos).is(LoconauticsRegistries.GHOST_WHEEL.get())) {
                placed.add(pos);
            }
        }
        return placed;
    }

    public static void reconcile(SableTrain train) {
        if (train.isPoseSettling()) {
            return;
        }
        ServerSubLevel sub = resolveSubLevel(train);
        Car car = train.car();
        if (sub == null || car == null) {
            return;
        }
        ServerLevel level = sub.getLevel();
        java.util.List<Bogey> alive = new java.util.ArrayList<>(car.bogeys().size());
        for (Bogey bogey : car.bogeys()) {
            BlockPos bogeyPos = bogeyPos(sub, bogey);
            if (bogeyAlive(level, bogeyPos)) {
                alive.add(bogey);
                continue;
            }
            clearPadsAround(level, bogeyPos);
        }
        if (alive.size() != car.bogeys().size()) {
            BogeyEditTrace.refuse(train, "reconcile dropped " + (car.bogeys().size() - alive.size()) + " bogey(s)");
            train.setBogeys(alive);
            SubLevelSplitWatcher.refitAfterBogeyDrop(train, sub, alive);
            CartLifecycle.fire(train, CartEvent.RESHAPED);
        }
    }

    public static boolean isOrphanPad(LevelAccessor level, BlockPos pad) {
        for (int dx = -MAX_REACH; dx <= MAX_REACH; dx++) {
            for (int dz = -MAX_REACH; dz <= MAX_REACH; dz++) {
                if (level.getBlockState(pad.offset(dx, 1, dz)).getBlock() instanceof AbstractBogeyBlock
                        || level.getBlockState(pad.offset(dx, -1, dz)).getBlock() instanceof AbstractBogeyBlock) {
                    return false;
                }
            }
        }
        return true;
    }

    public static boolean isPad(BlockState state) {
        return state.is(LoconauticsRegistries.GHOST_WHEEL.get());
    }

    public static void clearPadsAround(LevelAccessor level, BlockPos bogeyPos) {
        for (int dy = -1; dy <= 1; dy += 2) {
            for (int dx = -MAX_REACH; dx <= MAX_REACH; dx++) {
                for (int dz = -MAX_REACH; dz <= MAX_REACH; dz++) {
                    BlockPos pos = bogeyPos.offset(dx, dy, dz);
                    if (level.getBlockState(pos).is(LoconauticsRegistries.GHOST_WHEEL.get())) {
                        level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                    }
                }
            }
        }
    }
}
