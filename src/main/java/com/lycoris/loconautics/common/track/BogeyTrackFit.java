package com.lycoris.loconautics.common.track;

import com.lycoris.loconautics.LoconauticsConstants;
import com.simibubi.create.content.trains.bogey.AbstractBogeyBlock;
import com.simibubi.create.content.trains.bogey.AbstractBogeyBlockEntity;
import com.simibubi.create.content.trains.bogey.BogeyStyle;
import com.simibubi.create.content.trains.track.TrackMaterial;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Set;
import java.util.function.Predicate;

public final class BogeyTrackFit {

    public static final Predicate<TrackMaterial> ANY = material -> true;

    private BogeyTrackFit() {
    }

    public static Predicate<TrackMaterial> forBogey(Level level, BlockPos bogeyPos) {
        BlockState state = level.getBlockState(bogeyPos);
        if (!(state.getBlock() instanceof AbstractBogeyBlock<?> block)) {
            return ANY;
        }
        BogeyStyle style = level.getBlockEntity(bogeyPos) instanceof AbstractBogeyBlockEntity be
                ? be.getStyle() : block.getDefaultStyle();
        if (isUniversal(block.getTrackType(style))) {
            return ANY;
        }
        Set<TrackMaterial.TrackType> valid = block.getValidPathfindingTypes(style);
        return material -> material == null || material.trackType == null
                || isUniversal(material.trackType) || valid.contains(material.trackType);
    }

    private static boolean isUniversal(TrackMaterial.TrackType type) {
        return type != null && LoconauticsConstants.RAILWAYS_UNIVERSAL_TRACK.equals(type.id);
    }
}
