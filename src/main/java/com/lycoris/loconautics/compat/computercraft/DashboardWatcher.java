package com.lycoris.loconautics.compat.computercraft;

import com.lycoris.loconautics.LoconauticsConstants;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Set;

@EventBusSubscriber(modid = LoconauticsConstants.MOD_ID)
public final class DashboardWatcher {

    private static final Set<Pollable> WATCHED = new LinkedHashSet<>();

    private DashboardWatcher() {
    }

    public static void watch(Pollable pollable) {
        WATCHED.add(pollable);
    }

    public static void unwatch(Pollable pollable) {
        WATCHED.remove(pollable);
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (WATCHED.isEmpty()) return;
        for (Pollable pollable : new ArrayList<>(WATCHED)) pollable.pollDashboard();
    }

    public interface Pollable {
        void pollDashboard();
    }
}
