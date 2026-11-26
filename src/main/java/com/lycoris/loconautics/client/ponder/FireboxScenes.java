package com.lycoris.loconautics.client.ponder;

import com.simibubi.create.AllItems;
import com.simibubi.create.foundation.ponder.CreateSceneBuilder;
import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;

public class FireboxScenes {

    private static final BlockPos FIREBOX = new BlockPos(1, 2, 2);
    private static final BlockPos BODY_FROM = new BlockPos(2, 2, 2);
    private static final BlockPos BODY_TO = new BlockPos(4, 2, 2);
    private static final BlockPos LEGS_FROM = new BlockPos(1, 1, 2);
    private static final BlockPos LEGS_TO = new BlockPos(4, 1, 2);

    public static void fire(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        scene.title("firebox_fire", "Lighting Fireboxes");
        scene.configureBasePlate(0, 0, 5);
        scene.showBasePlate();
        scene.idle(5);

        scene.world().showSection(util.select().fromTo(LEGS_FROM, LEGS_TO), Direction.DOWN);
        scene.world().showSection(util.select().position(FIREBOX), Direction.DOWN);
        scene.idle(5);
        scene.world().showSection(util.select().fromTo(BODY_FROM, BODY_TO), Direction.DOWN);
        scene.idle(20);
        Vec3 door = util.vector().blockSurface(FIREBOX, Direction.WEST);
        scene.overlay().showText(60)
                .text("A Firebox burns fuel to heat the Boiler it is part of")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(door);
        scene.idle(70);

        scene.overlay().showControls(door, Pointing.RIGHT, 30)
                .rightClick()
                .withItem(new ItemStack(Items.COAL));
        scene.idle(10);
        scene.overlay().showText(60)
                .text("Right click with fuel to load it into the Firebox")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(door);
        scene.idle(70);

        scene.overlay().showControls(door, Pointing.RIGHT, 30)
                .rightClick()
                .withItem(new ItemStack(Items.FLINT_AND_STEEL));
        scene.idle(10);
        scene.world().modifyBlocks(util.select().position(FIREBOX),
                state -> state.setValue(BlockStateProperties.LIT, true), false);
        scene.overlay().showText(70)
                .text("An Igniter such as Flint and Steel lights it once the Boiler is built")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(door);
        scene.idle(80);

        scene.overlay().showControls(door, Pointing.RIGHT, 30)
                .rightClick()
                .withItem(new ItemStack(Items.IRON_SHOVEL));
        scene.idle(10);
        scene.world().modifyBlocks(util.select().position(FIREBOX),
                state -> state.setValue(BlockStateProperties.LIT, false), false);
        scene.overlay().showText(60)
                .text("Using a Shovel puts the fire out")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(door);
        scene.idle(70);

        scene.overlay().showControls(door, Pointing.RIGHT, 30)
                .rightClick();
        scene.idle(10);
        scene.overlay().showText(70)
                .text("An empty hand swings its doors, open doors let the fumes out")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(door);
        scene.idle(80);
    }

    public static void row(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        scene.title("firebox_row", "Joining Fireboxes");
        scene.configureBasePlate(0, 0, 5);
        scene.showBasePlate();
        scene.idle(5);

        BlockPos first = new BlockPos(2, 2, 1);
        BlockPos second = new BlockPos(2, 2, 2);
        BlockPos third = new BlockPos(2, 2, 3);
        Vec3 door = util.vector().blockSurface(second, Direction.WEST);
        scene.world().showSection(util.select().fromTo(new BlockPos(2, 1, 1), first), Direction.DOWN);
        scene.idle(15);
        scene.world().showSection(util.select().fromTo(new BlockPos(2, 1, 2), second), Direction.DOWN);
        scene.idle(5);
        scene.world().showSection(util.select().fromTo(new BlockPos(2, 1, 3), third), Direction.DOWN);
        scene.idle(15);
        scene.overlay().showText(60)
                .text("Fireboxes placed side by side join into one big Firebox")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(door);
        scene.idle(70);

        scene.overlay().showText(60)
                .text("Every Firebox in the group adds a fuel slot")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(door);
        scene.idle(70);

        scene.overlay().showControls(door, Pointing.RIGHT, 40)
                .rightClick()
                .withItem(AllItems.WRENCH.asStack());
        scene.idle(10);
        scene.overlay().showText(60)
                .text("Using a Wrench on a side turns the door to face it")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(door);
        scene.idle(70);

        scene.overlay().showOutline(PonderPalette.GREEN, "group", util.select().fromTo(first, third), 70);
        scene.overlay().showText(70)
                .text("A group holds up to 3 Fireboxes in a straight line")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(util.vector().topOf(second));
        scene.idle(80);
    }

    public static void stoker(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        scene.title("firebox_stoker", "Feeding Fireboxes with Stokers");
        scene.configureBasePlate(0, 0, 5);
        scene.showBasePlate();
        scene.idle(5);

        BlockPos stoker = new BlockPos(2, 1, 2);
        BlockPos firebox = new BlockPos(2, 2, 2);
        Vec3 side = util.vector().blockSurface(stoker, Direction.WEST);
        scene.world().showSection(util.select().position(stoker), Direction.DOWN);
        scene.idle(5);
        scene.world().showSection(util.select().position(firebox), Direction.DOWN);
        scene.idle(20);
        scene.overlay().showText(60)
                .text("A Firebox Stoker under a Firebox feeds it fuel automatically")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(side);
        scene.idle(70);

        scene.overlay().showControls(side, Pointing.RIGHT, 30)
                .rightClick()
                .withItem(new ItemStack(Items.COAL));
        scene.idle(10);
        scene.overlay().showText(60)
                .text("Right click it with fuel to fill its 9 slots")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(side);
        scene.idle(70);

        scene.overlay().showOutline(PonderPalette.GREEN, "feed", util.select().position(firebox), 60);
        scene.overlay().showText(60)
                .text("It passes one item up to the Firebox every half second")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(util.vector().blockSurface(firebox, Direction.WEST));
        scene.idle(70);

        scene.overlay().showText(60)
                .text("Automation can insert fuel but never take it out")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(side);
        scene.idle(70);
    }
}
