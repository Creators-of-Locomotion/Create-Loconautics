package com.lycoris.loconautics.content.boiler;

import com.lycoris.loconautics.LoconauticsConstants;
import com.lycoris.loconautics.content.boiler.barrel.BoilerBarrelBlock;
import com.lycoris.loconautics.content.boiler.firebox.FireboxBlock;
import com.simibubi.create.content.equipment.extendoGrip.ExtendoGripItem;
import com.simibubi.create.infrastructure.config.AllConfigs;
import net.createmod.catnip.placement.IPlacementHelper;
import net.createmod.catnip.placement.PlacementHelpers;
import net.createmod.catnip.placement.PlacementOffset;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

import java.util.List;
import java.util.function.Predicate;

@EventBusSubscriber(modid = LoconauticsConstants.MOD_ID)
public final class BoilerLinePlacement implements IPlacementHelper {

    private static final BoilerLinePlacement INSTANCE = new BoilerLinePlacement();
    private static final int ID = PlacementHelpers.register(INSTANCE);

    private BoilerLinePlacement() {
    }

    public static IPlacementHelper get() {
        return PlacementHelpers.get(ID);
    }

    private static boolean isLinePart(Block block) {
        return block instanceof FireboxBlock || block instanceof BoilerBarrelBlock barrel && !barrel.big;
    }

    private static Direction.Axis axisOf(BlockState state) {
        if (state.getBlock() instanceof FireboxBlock) {
            return state.getValue(BlockStateProperties.HORIZONTAL_FACING).getAxis();
        }
        return state.getValue(DirectionalBlock.FACING).getAxis();
    }

    private static boolean onAxis(BlockState state, Direction.Axis axis) {
        return isLinePart(state.getBlock()) && axisOf(state) == axis;
    }

    private static Block heldBlock(Player player) {
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack stack = player.getItemInHand(hand);
            if (stack.getItem() instanceof BlockItem item && isLinePart(item.getBlock())) {
                return item.getBlock();
            }
        }
        return null;
    }

    @Override
    public Predicate<ItemStack> getItemPredicate() {
        return stack -> stack.getItem() instanceof BlockItem item && isLinePart(item.getBlock());
    }

    @Override
    public Predicate<BlockState> getStatePredicate() {
        return state -> isLinePart(state.getBlock());
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        Player player = event.getEntity();
        ItemStack stack = event.getItemStack();
        if (!player.isShiftKeyDown() || !(stack.getItem() instanceof BlockItem item)
                || !isLinePart(item.getBlock())) {
            return;
        }
        Level level = event.getLevel();
        BlockState state = level.getBlockState(event.getPos());
        if (!isLinePart(state.getBlock())) {
            return;
        }
        BlockHitResult ray = event.getHitVec();
        PlacementOffset offset = INSTANCE.getOffset(player, level, state, event.getPos(), ray);
        if (!offset.isSuccessful()) {
            return;
        }
        event.setCancellationResult(offset.placeInWorld(level, item, player, event.getHand(), ray).result());
        event.setCanceled(true);
    }

    @Override
    public PlacementOffset getOffset(Player player, Level level, BlockState state, BlockPos pos,
                                     BlockHitResult ray) {
        Direction.Axis axis = axisOf(state);
        Block held = player == null ? null : heldBlock(player);
        if (held instanceof FireboxBlock && axis == Direction.Axis.Y) {
            return PlacementOffset.fail();
        }
        int range = AllConfigs.server().equipment.placementAssistRange.get();
        if (player != null) {
            AttributeInstance reach = player.getAttribute(Attributes.BLOCK_INTERACTION_RANGE);
            if (reach != null && reach.hasModifier(ExtendoGripItem.singleRangeAttributeModifier.id())) {
                range += 4;
            }
        }
        List<Direction> directions = IPlacementHelper.orderedByDistance(pos, ray.getLocation(),
                dir -> dir.getAxis() == axis);
        for (Direction dir : directions) {
            int count = 0;
            BlockPos check = pos.relative(dir);
            while (onAxis(level.getBlockState(check), axis)) {
                count++;
                check = check.relative(dir);
            }
            if (count >= range) {
                continue;
            }
            BlockPos target = pos.relative(dir, count + 1);
            if (level.getBlockState(target).canBeReplaced()) {
                return PlacementOffset.success(target, placed -> align(placed, state));
            }
        }
        return PlacementOffset.fail();
    }

    private static BlockState align(BlockState placed, BlockState source) {
        Direction facing = source.getBlock() instanceof FireboxBlock
                ? source.getValue(BlockStateProperties.HORIZONTAL_FACING)
                : source.getValue(DirectionalBlock.FACING);
        BlockState aligned = placed.setValue(BoilerAttach.RAISED,
                placed.getValue(BoilerAttach.RAISED) || source.getValue(BoilerAttach.RAISED));
        if (placed.getBlock() instanceof FireboxBlock) {
            return aligned.setValue(BlockStateProperties.HORIZONTAL_FACING, facing);
        }
        return aligned.setValue(DirectionalBlock.FACING, facing)
                .setValue(BoilerBarrelBlock.SMOKE_POS, false)
                .setValue(BoilerBarrelBlock.SMOKE_NEG, false);
    }
}
