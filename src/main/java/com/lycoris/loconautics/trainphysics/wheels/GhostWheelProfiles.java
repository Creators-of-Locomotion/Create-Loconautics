package com.lycoris.loconautics.trainphysics.wheels;

import com.lycoris.loconautics.LoconauticsConstants;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.registries.datamaps.DataMapType;
import net.neoforged.neoforge.registries.datamaps.RegisterDataMapTypesEvent;

public final class GhostWheelProfiles {

    public static final DataMapType<Block, GhostWheelProfile> TYPE = DataMapType
            .builder(LoconauticsConstants.id("ghost_wheel"), Registries.BLOCK, GhostWheelProfile.CODEC)
            .build();

    private GhostWheelProfiles() {
    }

    public static void register(RegisterDataMapTypesEvent event) {
        event.register(TYPE);
    }

    public static GhostWheelProfile.Limits limitsOf(BlockState bogeyState, CompoundTag bogeyData) {
        GhostWheelProfile profile = bogeyState.getBlockHolder().getData(TYPE);
        if (profile == null) {
            return GhostWheelProfile.DEFAULT_LIMITS;
        }
        if (profile.sizeKey().isEmpty() || bogeyData == null || !bogeyData.contains(profile.sizeKey().get())) {
            return profile.limits();
        }
        return profile.limitsFor(bogeyData.getInt(profile.sizeKey().get()));
    }

    public static double floor(boolean monorail) {
        return monorail ? LoconauticsConstants.MONORAIL_PAD_FLOOR : LoconauticsConstants.RAIL_HEAD_HEIGHT;
    }

    public static double floorOf(BlockState bogeyState) {
        return com.lycoris.loconautics.common.track.TrackProfile.ofBogey(bogeyState).padFloor();
    }
}
