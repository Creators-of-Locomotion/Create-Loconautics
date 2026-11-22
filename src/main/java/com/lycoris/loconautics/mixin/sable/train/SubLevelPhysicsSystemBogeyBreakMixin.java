package com.lycoris.loconautics.mixin.sable.train;

import com.lycoris.loconautics.trainphysics.spawn.BogeyPlotChanges;
import com.simibubi.create.content.trains.bogey.AbstractBogeyBlock;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = SubLevelPhysicsSystem.class, remap = false)
public abstract class SubLevelPhysicsSystemBogeyBreakMixin {

    @Shadow
    @Final
    private ServerLevel level;

    @Inject(method = "handleBlockChange", at = @At("HEAD"))
    private void loconautics$bogeyChanged(SectionPos sectionPos,
            net.minecraft.world.level.chunk.LevelChunkSection section, int x, int y, int z,
            BlockState oldState, BlockState newState, CallbackInfo ci) {
        boolean was = oldState.getBlock() instanceof AbstractBogeyBlock;
        boolean is = newState.getBlock() instanceof AbstractBogeyBlock;
        if (was == is) {
            return;
        }
        BlockPos bogeyPos = new BlockPos((sectionPos.x() << 4) + x, (sectionPos.y() << 4) + y,
                (sectionPos.z() << 4) + z);
        if (is) {
            BogeyPlotChanges.placed(level, bogeyPos);
        } else {
            BogeyPlotChanges.removed(level, bogeyPos);
        }
    }
}
