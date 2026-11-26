package com.lycoris.loconautics.content.boiler.firebox;

import com.lycoris.loconautics.content.boiler.BoilerTags;
import com.lycoris.loconautics.content.boiler.barrel.BoilerBarrelBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

public final class FireboxSeams {
    public static final double END_BAND = 5.0 / 16.0;

    private FireboxSeams() {
    }

    public static boolean hasPartner(LevelAccessor level, BlockPos pos, BlockState state, Direction end) {
        Direction facing = state.getValue(BlockStateProperties.HORIZONTAL_FACING);
        if (end.getAxis() != facing.getAxis()) {
            return false;
        }
        BlockPos otherPos = pos.relative(end);
        BlockState other = level.getBlockState(otherPos);
        if (other.is(BoilerTags.BOILER_FIREBOX)) {
            BlockPos anchor = FireboxGroups.renderAnchor(level, pos);
            return anchor != null && anchor.equals(FireboxGroups.renderAnchor(level, otherPos));
        }
        return end == facing.getOpposite() && other.getBlock() instanceof BoilerBarrelBlock barrel
                && barrel.hasPartner(level, otherPos, facing);
    }

    public static List<Direction> partners(LevelAccessor level, BlockPos pos, BlockState state) {
        List<Direction> ends = new ArrayList<>(2);
        Direction facing = state.getValue(BlockStateProperties.HORIZONTAL_FACING);
        for (Direction end : new Direction[]{facing, facing.getOpposite()}) {
            if (hasPartner(level, pos, state, end)) {
                ends.add(end);
            }
        }
        return ends;
    }

    public static boolean isJoined(LevelAccessor level, BlockPos pos, BlockState state, Direction end) {
        BlockState other = level.getBlockState(pos.relative(end));
        if (other.getBlock() instanceof BoilerBarrelBlock) {
            return BoilerBarrelBlock.joinsToward(other, end.getOpposite());
        }
        return !state.getValue(FireboxBlock.severedFor(end));
    }

    public static Direction aimedEnd(BlockState state, BlockPos pos, Vec3 click) {
        Direction facing = state.getValue(BlockStateProperties.HORIZONTAL_FACING);
        Vec3 rel = click.subtract(Vec3.atCenterOf(pos));
        double along = facing.getAxis().choose(rel.x, rel.y, rel.z);
        if (Math.abs(along) < 0.5 - END_BAND) {
            return null;
        }
        Direction positive = Direction.fromAxisAndDirection(facing.getAxis(), Direction.AxisDirection.POSITIVE);
        return along > 0 ? positive : positive.getOpposite();
    }

    public static void toggle(Level level, BlockPos pos, Direction end) {
        BlockPos otherPos = pos.relative(end);
        BlockState other = level.getBlockState(otherPos);
        if (other.getBlock() instanceof BoilerBarrelBlock barrel) {
            barrel.toggleEnd(level, otherPos, end.getOpposite());
            return;
        }
        BlockState here = level.getBlockState(pos);
        boolean sever = !here.getValue(FireboxBlock.severedFor(end));
        level.setBlock(pos, here.setValue(FireboxBlock.severedFor(end), sever), Block.UPDATE_CLIENTS);
        level.setBlock(otherPos, other.setValue(FireboxBlock.severedFor(end.getOpposite()), sever),
                Block.UPDATE_CLIENTS);
    }
}
