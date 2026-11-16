package com.lycoris.loconautics.content.boiler;

public record BoilerPool(float max, float bogeyDemand, float outputUsed) {

    public float remainder() {
        return Math.max(0f, max - bogeyDemand);
    }

    public float used() {
        return bogeyDemand + outputUsed;
    }

    public float usedFraction() {
        return max <= 0f ? 0f : Math.min(1f, used() / max);
    }

    public boolean bogeysOverdrawn() {
        return bogeyDemand > max;
    }
}
