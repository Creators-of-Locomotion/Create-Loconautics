package com.lycoris.loconautics.trainphysics.wheels;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.Map;
import java.util.Optional;

public record GhostWheelProfile(Limits limits, Optional<String> sizeKey, Map<Integer, Limits> sizes) {

    public static final double MAX_SWING_DEGREES = 25.0;

    public static final int MAX_ALONG = 6;

    public record Limits(double yaw, double pitch, double roll, double damping, int along) {

        public static final Codec<Limits> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.doubleRange(0.0, MAX_SWING_DEGREES).fieldOf("yaw").forGetter(Limits::yaw),
                Codec.doubleRange(0.0, MAX_SWING_DEGREES).fieldOf("pitch").forGetter(Limits::pitch),
                Codec.doubleRange(0.0, MAX_SWING_DEGREES).fieldOf("roll").forGetter(Limits::roll),
                Codec.doubleRange(0.0, 64.0).fieldOf("damping").forGetter(Limits::damping),
                Codec.intRange(1, MAX_ALONG).optionalFieldOf("along", 1).forGetter(Limits::along)
        ).apply(instance, Limits::new));

        public boolean sameSwing(Limits other) {
            return yaw == other.yaw && pitch == other.pitch && roll == other.roll && damping == other.damping;
        }

        public double yawRadians() {
            return Math.toRadians(yaw);
        }

        public double pitchRadians() {
            return Math.toRadians(pitch);
        }

        public double rollRadians() {
            return Math.toRadians(roll);
        }
    }

    private static final Codec<Integer> SIZE_KEY = Codec.STRING.comapFlatMap(text -> {
        try {
            return DataResult.success(Integer.parseInt(text));
        } catch (NumberFormatException e) {
            return DataResult.error(() -> "not a whole number: " + text);
        }
    }, String::valueOf);

    public static final Codec<GhostWheelProfile> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Limits.CODEC.fieldOf("limits").forGetter(GhostWheelProfile::limits),
            Codec.STRING.optionalFieldOf("size_key").forGetter(GhostWheelProfile::sizeKey),
            Codec.unboundedMap(SIZE_KEY, Limits.CODEC).optionalFieldOf("sizes", Map.of())
                    .forGetter(GhostWheelProfile::sizes)
    ).apply(instance, GhostWheelProfile::new));

    public static final Limits DEFAULT_LIMITS = new Limits(12.5, 12.5, 5.0, 4.0, 1);

    public static final GhostWheelProfile DEFAULT = new GhostWheelProfile(DEFAULT_LIMITS, Optional.empty(), Map.of());

    public Limits limitsFor(int size) {
        return sizes.getOrDefault(size, limits);
    }
}
