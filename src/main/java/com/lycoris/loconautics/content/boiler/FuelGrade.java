package com.lycoris.loconautics.content.boiler;

import com.lycoris.loconautics.config.LoconauticsConfig;

public enum FuelGrade {
    POOR, NORMAL, PREMIUM;

    public int levelsPerFirebox() {
        return switch (this) {
            case POOR -> LoconauticsConfig.FIRE_LEVELS_POOR.get();
            case NORMAL -> LoconauticsConfig.FIRE_LEVELS_NORMAL.get();
            case PREMIUM -> LoconauticsConfig.FIRE_LEVELS_PREMIUM.get();
        };
    }
}
