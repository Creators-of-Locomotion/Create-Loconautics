package com.lycoris.loconautics.client.ponder;

import com.lycoris.loconautics.content.boiler.BoilerBlocks;
import com.lycoris.loconautics.content.boiler.barrel.BoilerBarrelBlock;
import com.simibubi.create.AllItems;
import com.simibubi.create.foundation.ponder.CreateSceneBuilder;
import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.state.BlockState;

public class BoilerSetupScenes {

    private static final BlockPos FIREBOX = new BlockPos(1, 1, 2);
    private static final BlockPos BODY_NEAR = new BlockPos(2, 1, 2);
    private static final BlockPos BODY_MID = new BlockPos(3, 1, 2);
    private static final BlockPos BODY_FAR = new BlockPos(4, 1, 2);

    public static void assembly(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = open(builder, "boiler_assembly", "Building a Boiler");

        scene.world().showSection(util.select().position(FIREBOX), Direction.DOWN);
        scene.idle(10);
        barrelEnd(scene, true);
        scene.world().showSection(util.select().position(BODY_NEAR), Direction.DOWN);
        scene.idle(4);
        scene.world().showSection(util.select().position(BODY_MID), Direction.DOWN);
        scene.idle(15);
        scene.overlay().showOutline(PonderPalette.GREEN, "boiler", util.select().fromTo(FIREBOX, BODY_MID), 60);
        scene.overlay().showText(60)
                .text("A Firebox with Boiler Body blocks beside it forms a Boiler")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(util.vector().topOf(BODY_NEAR));
        scene.idle(70);

        scene.world().setBlock(BODY_FAR, crossways(), false);
        scene.world().showSection(util.select().position(BODY_FAR), Direction.DOWN);
        scene.idle(15);
        scene.overlay().showOutline(PonderPalette.RED, "crossways", util.select().position(BODY_FAR), 70);
        scene.overlay().showText(70)
                .text("Every Boiler Body has to run along the same line")
                .attachKeyFrame()
                .colored(PonderPalette.RED)
                .placeNearTarget()
                .pointAt(util.vector().topOf(BODY_FAR));
        scene.idle(80);

        scene.world().destroyBlock(BODY_FAR);
        scene.idle(10);
        scene.world().restoreBlocks(util.select().position(BODY_FAR));
        barrelEnd(scene, false);
        scene.idle(10);
        scene.overlay().showOutline(PonderPalette.GREEN, "barrel", util.select().fromTo(FIREBOX, BODY_FAR), 60);
        scene.overlay().showText(60)
                .text("Lined up, they join into one barrel")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(util.vector().topOf(BODY_MID));
        scene.idle(70);

        scene.overlay().showControls(util.vector().topOf(FIREBOX), Pointing.DOWN, 50)
                .withItem(AllItems.GOGGLES.asStack());
        scene.idle(10);
        scene.overlay().showText(70)
                .text("Engineer's Goggles on the Firebox name the next missing piece")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(util.vector().topOf(FIREBOX));
        scene.idle(80);
    }

    private static void barrelEnd(CreateSceneBuilder scene, boolean midIsEnd) {
        scene.world().modifyBlock(BODY_MID, state -> state
                .setValue(BoilerBarrelBlock.CONNECTED_POS, !midIsEnd)
                .setValue(BoilerBarrelBlock.SMOKE_POS, midIsEnd), false);
    }

    private static BlockState crossways() {
        return BoilerBlocks.DYED_BODIES.get(BoilerBlocks.BODY_TAB_KIND).get(DyeColor.RED).get().defaultBlockState()
                .setValue(BoilerBarrelBlock.FACING, Direction.NORTH);
    }

    private static CreateSceneBuilder open(SceneBuilder builder, String id, String title) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        scene.title(id, title);
        scene.configureBasePlate(0, 0, 5);
        scene.showBasePlate();
        scene.idle(5);
        return scene;
    }
}
