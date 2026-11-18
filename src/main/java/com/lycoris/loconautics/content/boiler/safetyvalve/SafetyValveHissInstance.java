package com.lycoris.loconautics.content.boiler.safetyvalve;

import com.simibubi.create.AllSoundEvents;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class SafetyValveHissInstance extends AbstractTickableSoundInstance {

    private static final float QUIET = 0.25f;
    private static final float LOUD = 0.8f;
    private static final float LOW_PITCH = 0.75f;
    private static final float HIGH_PITCH = 1.1f;

    private final SafetyValveBlockEntity valve;

    public SafetyValveHissInstance(SafetyValveBlockEntity valve) {
        super(AllSoundEvents.STEAM.getMainEvent(), SoundSource.BLOCKS, SoundInstance.createUnseededRandom());
        this.valve = valve;
        this.looping = true;
        this.delay = 0;
        this.volume = 1.0e-4f;
        place();
    }

    @Override
    public void tick() {
        if (valve.isRemoved() || !valve.isVenting() || valve.opening() <= 0f) {
            stop();
            return;
        }
        float opening = valve.opening();
        volume = QUIET + (LOUD - QUIET) * opening;
        pitch = LOW_PITCH + (HIGH_PITCH - LOW_PITCH) * opening;
        place();
    }

    private void place() {
        Vec3 at = valve.hissPosition();
        this.x = at.x;
        this.y = at.y;
        this.z = at.z;
    }
}
