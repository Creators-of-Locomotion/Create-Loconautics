package com.lycoris.loconautics.common.track;

import com.lycoris.loconautics.LoconauticsConstants;
import com.simibubi.create.content.trains.bogey.AbstractBogeyBlock;
import com.simibubi.create.content.trains.track.TrackMaterial;
import net.minecraft.world.level.block.state.BlockState;

public record TrackProfile(Kind kind, double tractionBoost, double padFloor, double collisionHeight) {

    public enum Kind {
        STANDARD, MONORAIL
    }

    public static final TrackProfile STANDARD = new TrackProfile(Kind.STANDARD, 1.0,
            LoconauticsConstants.RAIL_HEAD_HEIGHT, LoconauticsConstants.TRACK_COLLISION_HEIGHT);

    public static final TrackProfile MONORAIL = new TrackProfile(Kind.MONORAIL, 3.0,
            LoconauticsConstants.MONORAIL_PAD_FLOOR, LoconauticsConstants.MONORAIL_COLLISION_HEIGHT);

    public static TrackProfile of(TrackMaterial material) {
        if (material == null) {
            return STANDARD;
        }
        if (LoconauticsConstants.RAILWAYS_MONORAIL_MATERIAL.equals(material.id)) {
            return MONORAIL;
        }
        return STANDARD;
    }

    public static TrackProfile ofBogey(BlockState bogey) {
        if (bogey.getBlock() instanceof AbstractBogeyBlock<?> block) {
            TrackMaterial.TrackType type = block.getTrackType(block.getDefaultStyle());
            if (type != null && LoconauticsConstants.RAILWAYS_MONORAIL_MATERIAL.equals(type.id)) {
                return MONORAIL;
            }
        }
        return STANDARD;
    }

    public boolean isMonorail() {
        return kind == Kind.MONORAIL;
    }

    public double settleSink() {
        return padFloor - collisionHeight;
    }
}
