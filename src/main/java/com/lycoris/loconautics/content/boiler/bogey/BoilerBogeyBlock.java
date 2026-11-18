package com.lycoris.loconautics.content.boiler.bogey;

import com.lycoris.loconautics.content.boiler.BoilerBlocks;
import com.simibubi.create.content.trains.bogey.AbstractBogeyBlock;
import com.simibubi.create.content.trains.bogey.BogeySizes;
import com.simibubi.create.content.trains.bogey.BogeyStyle;
import com.simibubi.create.content.trains.track.TrackMaterial;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public class BoilerBogeyBlock extends AbstractBogeyBlock<BoilerBogeyBlockEntity> {

    public BoilerBogeyBlock(Properties properties) {
        super(properties, BogeySizes.SMALL);
        registerDefaultState(defaultBlockState().setValue(WATERLOGGED, false));
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState placed = defaultBlockState().setValue(AXIS, context.getHorizontalDirection().getAxis());
        return withWater(placed, context);
    }

    @Override
    public BlockState getRotatedBlockState(BlockState state, Direction targetedFace) {
        return state.cycle(AXIS);
    }

    @Override
    public InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                            BlockHitResult hit) {
        if (player.isShiftKeyDown() || !player.getMainHandItem().isEmpty() || !player.getOffhandItem().isEmpty()) {
            return super.useWithoutItem(state, level, pos, player, hit);
        }
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (level.getBlockEntity(pos) instanceof BoilerBogeyBlockEntity bogey) {
            player.openMenu(bogey, bogey::sendToMenu);
        }
        return InteractionResult.CONSUME;
    }

    @Override
    public TrackMaterial.TrackType getTrackType(BogeyStyle style) {
        return TrackMaterial.TrackType.STANDARD;
    }

    @Override
    public double getWheelPointSpacing() {
        return 2;
    }

    @Override
    public double getWheelRadius() {
        return 6.5 / 16d;
    }

    @Override
    public Vec3 getConnectorAnchorOffset() {
        return new Vec3(0, 7 / 32f, 1);
    }

    @Override
    public BogeyStyle getDefaultStyle() {
        return BoilerBogeyStyles.BOILER;
    }

    @Override
    public ItemStack getCloneItemStack(BlockState state, HitResult target, LevelReader level, BlockPos pos,
                                       Player player) {
        return new ItemStack(BoilerBlocks.BOILER_BOGEY_ITEM.get());
    }

    @Override
    public Class<BoilerBogeyBlockEntity> getBlockEntityClass() {
        return BoilerBogeyBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends BoilerBogeyBlockEntity> getBlockEntityType() {
        return BoilerBlocks.BOILER_BOGEY_BE.get();
    }
}
