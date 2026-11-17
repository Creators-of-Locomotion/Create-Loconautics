package com.lycoris.loconautics.regression;

import com.lycoris.loconautics.compat.create.psi.SablePsiDriver;
import com.lycoris.loconautics.content.bearingaxle.BogeyShaft;
import com.lycoris.loconautics.content.controls.panel.PanelBlockEntity;
import com.lycoris.loconautics.content.controls.panel.PanelSlot;
import com.lycoris.loconautics.content.controls.panelgauge.GaugeBlockEntity;
import com.lycoris.loconautics.content.controls.panelgauge.GaugeMetric;
import com.lycoris.loconautics.content.dashboard.DashboardBlocks;
import com.lycoris.loconautics.content.transmission.TransmissionBlockEntity;
import com.lycoris.loconautics.registry.LoconauticsRegistries;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.kinetics.RotationPropagator;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.transmission.SplitShaftBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.simulated_team.simulated.index.SimBlocks;
import dev.simulated_team.simulated.content.blocks.velocity_sensor.VelocitySensorBlockEntity;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

@GameTestHolder("loconautics")
@PrefixGameTestTemplate(false)
public final class IssueRegressionTests {
    @GameTest(template = "issue_empty")
    public static void transmissionCachesStress(GameTestHelper helper) throws Exception {
        BlockPos pos = new BlockPos(1, 1, 1);
        helper.setBlock(pos, LoconauticsRegistries.TRANSMISSION.get());
        TransmissionBlockEntity transmission = (TransmissionBlockEntity) helper.getBlockEntity(pos);
        transmission.setSpeed(64.0f);
        transmission.setRedstonePower(15);
        float calculated = transmission.calculateStressApplied();
        Field cached = KineticBlockEntity.class.getDeclaredField("lastStressApplied");
        cached.setAccessible(true);
        helper.assertTrue(calculated > 0.0f, "Powered transmission must consume stress");
        helper.assertTrue(cached.getFloat(transmission) == calculated, "Create must retain the calculated stress");
        helper.succeed();
    }

    @GameTest(template = "issue_empty")
    public static void transmissionUpdatesCachedStressWhenStopped(GameTestHelper helper) throws Exception {
        BlockPos pos = new BlockPos(1, 1, 1);
        helper.setBlock(pos, LoconauticsRegistries.TRANSMISSION.get());
        TransmissionBlockEntity transmission = (TransmissionBlockEntity) helper.getBlockEntity(pos);
        transmission.setSpeed(64.0f);
        transmission.setRedstonePower(15);
        transmission.calculateStressApplied();
        transmission.setRedstonePower(0);
        float calculated = transmission.calculateStressApplied();
        Field cached = KineticBlockEntity.class.getDeclaredField("lastStressApplied");
        cached.setAccessible(true);
        helper.assertTrue(calculated == 0.0f && cached.getFloat(transmission) == 0.0f,
                "Stopping the output must clear the cached load");
        helper.succeed();
    }

    @GameTest(template = "issue_empty")
    public static void poweredClutchBlocksBogeyShaft(GameTestHelper helper) throws Exception {
        verifyShaft(helper, AllBlocks.CLUTCH.getDefaultState().setValue(BlockStateProperties.POWERED, true), 0.0f);
    }

    @GameTest(template = "issue_empty")
    public static void openClutchPassesBogeyShaft(GameTestHelper helper) throws Exception {
        verifyShaft(helper, AllBlocks.CLUTCH.getDefaultState().setValue(BlockStateProperties.POWERED, false), 1.0f);
    }

    @GameTest(template = "issue_empty")
    public static void gearshiftReversesBogeyShaft(GameTestHelper helper) throws Exception {
        verifyShaft(helper, AllBlocks.GEARSHIFT.getDefaultState().setValue(BlockStateProperties.POWERED, true), -1.0f);
    }

    private static void verifyShaft(GameTestHelper helper, BlockState sourceState, float expected) throws Exception {
        BlockPos start = new BlockPos(1, 1, 1);
        BlockPos middle = start.south();
        BlockPos end = middle.south();
        helper.setBlock(start, sourceState.setValue(BlockStateProperties.AXIS, Direction.Axis.Z));
        helper.setBlock(middle, AllBlocks.SMALL_BOGEY.getDefaultState()
                .setValue(BlockStateProperties.HORIZONTAL_AXIS, Direction.Axis.Z)
                .setValue(BogeyShaft.SHAFTED, true));
        helper.setBlock(end, AllBlocks.SHAFT.getDefaultState().setValue(BlockStateProperties.AXIS, Direction.Axis.Z));
        if (sourceState.getValue(BlockStateProperties.POWERED)) {
            helper.setBlock(start.above(), Blocks.REDSTONE_BLOCK);
        }
        KineticBlockEntity from = (KineticBlockEntity) helper.getBlockEntity(start);
        KineticBlockEntity to = (KineticBlockEntity) helper.getBlockEntity(end);
        from.source = helper.absolutePos(start.north());
        from.setSpeed(64.0f);
        helper.assertTrue(((SplitShaftBlockEntity) from).getRotationSpeedModifier(Direction.SOUTH) == expected,
                "Source must expose the expected modifier before testing the bogey connection");
        Method method = RotationPropagator.class.getDeclaredMethod("getRotationSpeedModifier", KineticBlockEntity.class, KineticBlockEntity.class);
        method.setAccessible(true);
        float modifier = (float) method.invoke(null, from, to);
        helper.assertTrue(modifier == expected, "Bogey shaft modifier must be " + expected + ", was " + modifier);
        helper.succeed();
    }

    @GameTest(template = "issue_empty")
    public static void portableInterfaceReachesTwoBlockGap(GameTestHelper helper) throws Exception {
        helper.assertTrue(inReach(new Vec3(3, 0, 0), true), "Two free blocks must be within reach");
        helper.assertTrue(inReach(new Vec3(3, 0, 0), false), "Stationary interfaces must use the same reach");
        helper.succeed();
    }

    @GameTest(template = "issue_empty")
    public static void portableInterfaceRejectsThirdBlockGap(GameTestHelper helper) throws Exception {
        helper.assertTrue(!inReach(new Vec3(4, 0, 0), true), "Three free blocks must be out of reach");
        helper.succeed();
    }

    @GameTest(template = "issue_empty")
    public static void portableInterfaceRejectsLateralMismatch(GameTestHelper helper) throws Exception {
        helper.assertTrue(!inReach(new Vec3(2, 0.8, 0), true), "Carriage interfaces must reject an offset over their tolerance");
        helper.assertTrue(!inReach(new Vec3(2, 0.4, 0), false), "Stationary interfaces must use the strict tolerance");
        helper.succeed();
    }

    @GameTest(template = "issue_empty")
    public static void portableInterfaceRejectsTargetBehindIt(GameTestHelper helper) throws Exception {
        helper.assertTrue(!inReach(new Vec3(-2, 0, 0), true), "A target behind the interface must not connect");
        helper.succeed();
    }

    private static boolean inReach(Vec3 delta, boolean carriage) throws Exception {
        Method method = SablePsiDriver.class.getDeclaredMethod("stillInReach", Vec3.class, Vec3.class, boolean.class);
        method.setAccessible(true);
        return (boolean) method.invoke(null, delta, new Vec3(1, 0, 0), carriage);
    }

    @GameTest(template = "issue_empty")
    public static void panelPreservesBindingsWhenMoved(GameTestHelper helper) {
        verifyPanelMove(helper, Rotation.NONE, 0, true);
    }

    @GameTest(template = "issue_empty")
    public static void panelPreservesBindingsWhenRotatedClockwise(GameTestHelper helper) {
        verifyPanelMove(helper, Rotation.CLOCKWISE_90, 3, true);
    }

    @GameTest(template = "issue_empty")
    public static void panelPreservesBindingsWhenRotatedCounterclockwise(GameTestHelper helper) {
        verifyPanelMove(helper, Rotation.COUNTERCLOCKWISE_90, 1, true);
    }

    @GameTest(template = "issue_empty")
    public static void panelPreservesBindingsWhenRotatedHalfTurn(GameTestHelper helper) {
        verifyPanelMove(helper, Rotation.CLOCKWISE_180, 2, true);
    }

    @GameTest(template = "issue_empty")
    public static void panelKeepsStationaryExternalBinding(GameTestHelper helper) {
        verifyPanelMove(helper, Rotation.CLOCKWISE_90, 3, false);
    }

    private static void verifyPanelMove(GameTestHelper helper, Rotation rotation, int angle, boolean moveTarget) {
        BlockPos source = helper.absolutePos(new BlockPos(1, 1, 1));
        BlockPos target = source.east();
        BlockPos destination = helper.absolutePos(new BlockPos(4, 1, 4));
        helper.getLevel().setBlockAndUpdate(source, DashboardBlocks.DASHBOARDS.get(DyeColor.BLACK)
                .get().defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH));
        helper.getLevel().setBlockAndUpdate(target, Blocks.STONE.defaultBlockState());
        PanelBlockEntity panel = (PanelBlockEntity) helper.getLevel().getBlockEntity(source);
        PanelSlot slot = PanelSlot.TOP_LEFT;
        panel.putPart(slot, new GaugeBlockEntity.Gauge(target, GaugeMetric.PRESSURE, 0, 0, 0, 0));
        panel.setLabel(slot, Component.literal("Boiler"));
        panel.setInverted(slot, true);
        panel.setLinkFrequency(slot, new ItemStack(Items.GOLD_INGOT), new ItemStack(Items.REDSTONE));
        var transform = new SubLevelAssemblyHelper.AssemblyTransform(source, destination, angle, rotation, helper.getLevel());
        SubLevelAssemblyHelper.moveBlocks(helper.getLevel(), transform,
                moveTarget ? List.of(source, target) : List.of(source));
        helper.runAfterDelay(2, () -> {
            PanelBlockEntity moved = (PanelBlockEntity) helper.getLevel().getBlockEntity(destination);
            helper.assertTrue(moved != null, "Panel must survive the move");
            helper.assertTrue(helper.getLevel().getBlockState(source).isAir(), "Assembly must remove the source panel");
            helper.assertTrue(!moveTarget || helper.getLevel().getBlockState(target).isAir(),
                    "Assembly must remove the moved target");
            helper.assertTrue(helper.getLevel().isLoaded(target), "Source target chunk must be loaded for the move test");
            helper.assertTrue(helper.getLevel().isLoaded(destination), "Destination chunk must be loaded for the move test");
            moved.tick();
            var gauge = (GaugeBlockEntity.Gauge) moved.getPart(slot);
            BlockPos expected = moveTarget ? transform.apply(target) : target;
            helper.assertTrue(gauge != null && gauge.firebox().equals(expected),
                    "Gauge binding must follow only its moved target; expected " + expected + ", got " + gauge);
            helper.assertTrue(moved.label(slot) != null && moved.label(slot).getString().equals("Boiler"),
                    "Panel label must survive assembly");
            helper.assertTrue(moved.isInverted(slot), "Panel options must survive assembly");
            helper.assertTrue(moved.transmitter(slot) != null
                    && moved.transmitter(slot).first().is(Items.GOLD_INGOT)
                    && moved.transmitter(slot).second().is(Items.REDSTONE),
                    "Both redstone frequencies must survive assembly");
            helper.succeed();
        });
    }

    @GameTest(template = "issue_empty")
    public static void speedGaugeUsesMagnitudeOfAwayRange(GameTestHelper helper) throws Exception {
        verifySpeedGauge(helper, false, 5.0f);
    }

    @GameTest(template = "issue_empty")
    public static void speedGaugeHandlesReverseMotion(GameTestHelper helper) throws Exception {
        verifySpeedGauge(helper, false, -5.0f);
    }

    @GameTest(template = "issue_empty")
    public static void speedGaugeStillHandlesTowardsRange(GameTestHelper helper) throws Exception {
        verifySpeedGauge(helper, true, 5.0f);
    }

    private static void verifySpeedGauge(GameTestHelper helper, boolean towards, float speed) throws Exception {
        BlockPos pos = new BlockPos(1, 1, 1);
        helper.setBlock(pos, SimBlocks.VELOCITY_SENSOR.getDefaultState());
        var sensor = (VelocitySensorBlockEntity) helper.getBlockEntity(pos);
        sensor.initialize();
        Field direction = sensor.getMaxSpeed().getClass().getDeclaredField("towards");
        direction.setAccessible(true);
        direction.setBoolean(sensor.getMaxSpeed(), towards);
        Field velocity = VelocitySensorBlockEntity.class.getDeclaredField("adjustedVelocity");
        velocity.setAccessible(true);
        velocity.setFloat(sensor, speed);
        helper.assertTrue(Math.abs(sensor.getMaxSpeed().getValue()) == 10, "Sensor range must be 10 m/s");
        for (GaugeMetric metric : List.of(GaugeMetric.SUB_LEVEL_SPEED, GaugeMetric.SUB_LEVEL_SPEED_FPS,
                GaugeMetric.SUB_LEVEL_SPEED_KMH, GaugeMetric.SUB_LEVEL_SPEED_MPH)) {
            helper.assertTrue(metric.normalized(sensor) == 0.5f,
                    metric + " must show half scale for 5 m/s in a 10 m/s range");
        }
        helper.succeed();
    }
}
