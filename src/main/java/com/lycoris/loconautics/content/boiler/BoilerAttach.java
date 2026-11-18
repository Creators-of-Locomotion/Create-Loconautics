package com.lycoris.loconautics.content.boiler;

import com.lycoris.loconautics.LoconauticsConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

public final class BoilerAttach {
    public static final TagKey<Block> HOSTS = TagKey.create(net.minecraft.core.registries.Registries.BLOCK,
            LoconauticsConstants.id("boiler_hosts"));

    public static final BooleanProperty RAISED = BooleanProperty.create("raised");

    // how far a part sits out from its host, in blocks
    public static final double BULGE = 0.5;

    private BoilerAttach() {
    }

    public static boolean isHost(BlockState state) {
        return state.is(HOSTS);
    }

    public static boolean isHostAt(BlockGetter level, BlockPos pos) {
        return isHost(level.getBlockState(pos));
    }

    // face is the side of the part that touches the host
    public static boolean raisedOn(BlockGetter level, BlockPos part, Direction face) {
        return isHostAt(level, part.relative(face));
    }
}
