package com.lycoris.loconautics.trainphysics.spawn;

import com.lycoris.loconautics.LoconauticsConstants;
import com.lycoris.loconautics.common.BogeyGroundDrop;
import com.lycoris.loconautics.network.packets.train.SableTrainSyncPacket;
import com.lycoris.loconautics.trainphysics.SableTrain;
import com.lycoris.loconautics.common.sable.SubLevelData;
import com.lycoris.loconautics.trainphysics.portal.PortalTwins;
import com.lycoris.loconautics.trainphysics.state.SableTrainPersistence;
import com.lycoris.loconautics.trainphysics.state.SableTrainRegistry;
import com.lycoris.loconautics.trainphysics.wheels.GhostWheelBodies;
import com.simibubi.create.content.trains.bogey.AbstractBogeyBlockEntity;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class AssembledBogeyTrains {

    private static final Map<UUID, ServerLevel> PENDING = new LinkedHashMap<>();

    private AssembledBogeyTrains() {
    }

    public static void queue(ServerLevel level, ServerSubLevel sub) {
        synchronized (PENDING) {
            PENDING.put(sub.getUniqueId(), level);
        }
    }

    public static void processPending() {
        Map<UUID, ServerLevel> batch;
        synchronized (PENDING) {
            if (PENDING.isEmpty()) return;
            batch = new LinkedHashMap<>(PENDING);
            PENDING.clear();
        }
        for (Map.Entry<UUID, ServerLevel> pending : batch.entrySet()) {
            ServerSubLevel sub = SubLevelData.resolve(pending.getValue(), pending.getKey());
            if (sub == null || isSpokenFor(sub)) continue;
            List<BlockPos> bogeys = SubLevelSplitWatcher.scanBogeyPlotPositions(sub.getPlot());
            if (bogeys.isEmpty()) continue;
            if (toTrain(pending.getValue(), sub, bogeys.get(0))) {
                liftGroundDrop(sub, bogeys);
            }
        }
    }

    private static boolean isSpokenFor(ServerSubLevel sub) {
        UUID id = sub.getUniqueId();
        return SableTrainRegistry.bySubLevel(id) != null || GhostWheelBodies.isWheelBody(id) || PortalTwins.isTwin(sub);
    }

    private static void liftGroundDrop(ServerSubLevel sub, List<BlockPos> bogeys) {
        for (BlockPos pos : bogeys) {
            if (sub.getLevel().getBlockEntity(pos) instanceof AbstractBogeyBlockEntity bogey) {
                BogeyGroundDrop.set(bogey, false);
            }
        }
    }

    private static boolean toTrain(ServerLevel level, ServerSubLevel sub, BlockPos firstBogey) {
        SableTrain.Car car = SubLevelSplitWatcher.buildCarFromSubLevelScan(level, sub,
                TracklessCars.railDirection(sub, firstBogey), new ArrayList<>(), null, null, null, new ArrayList<>());
        boolean railed = car != null;
        if (!railed) {
            car = TracklessCars.build(sub);
        }
        if (car == null) {
            return false;
        }
        SableTrain train = new SableTrain(UUID.randomUUID(), level, car, false);
        if (!railed) {
            train.setDerailedQuiet(true);
        }
        SableTrainRegistry.register(train);
        SableTrainSpawner.nameCar(level, car, SableTrainSpawner.defaultTrainName());
        SubLevelSplitWatcher.ensureObserver(level);
        SableTrainPersistence.persist(train);
        SableTrainSyncPacket.broadcast(train);
        LoconauticsConstants.LOGGER.info("[sabletrain] assembled body {} became train {}, {} bogey(s), {}",
                sub.getUniqueId(), train.id(), car.bogeys().size(), railed ? "on rail" : "trackless and derailed");
        BogeyPlotChanges.traceTilt("became train", train);
        return true;
    }
}
