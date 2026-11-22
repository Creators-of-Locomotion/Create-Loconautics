package com.lycoris.loconautics.trainphysics.spawn;

import com.lycoris.loconautics.LoconauticsConstants;
import com.lycoris.loconautics.common.sable.SubLevelData;
import com.lycoris.loconautics.trainphysics.SableTrain;
import com.lycoris.loconautics.trainphysics.state.SableTrainRegistry;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class BogeyPlotChanges {

    private static final Set<UUID> MARKED = ConcurrentHashMap.newKeySet();

    private BogeyPlotChanges() {
    }

    public static void placed(ServerLevel level, BlockPos plotPos) {
        if (!(Sable.HELPER.getContaining(level, plotPos) instanceof ServerSubLevel sub)) {
            return;
        }
        SableTrain train = SableTrainRegistry.bySubLevel(sub.getUniqueId());
        LoconauticsConstants.TRACE.info("[bogeyplace] bogey placed at {} in body {}, train {}", plotPos,
                sub.getUniqueId(), train == null ? "none, queued to become one" : train.id());
        if (train == null) {
            AssembledBogeyTrains.queue(level, sub);
            return;
        }
        SubLevelSplitWatcher.forgetIgnored(train.id(), plotPos);
        MARKED.add(train.id());
    }

    public static void removed(ServerLevel level, BlockPos plotPos) {
        WheelColliders.clearPadsAround(level, plotPos);
    }

    public static void traceTilt(String when, SableTrain train) {
        if (train.car() == null) {
            return;
        }
        ServerSubLevel sub = SubLevelData.resolve(train.level(), train.car().subLevelId());
        if (sub == null) {
            return;
        }
        double tilt = Math.toDegrees(Math.acos(Math.max(-1.0, Math.min(1.0, SubLevelData.up(sub).y))));
        LoconauticsConstants.TRACE.info("[bogeyplace] {} train {} body {} tilt {} deg, {} bogey(s)", when, train.id(),
                sub.getUniqueId(), String.format(Locale.ROOT, "%.2f", tilt), train.car().bogeys().size());
    }

    static List<UUID> takeMarked() {
        List<UUID> taken = new ArrayList<>(MARKED);
        MARKED.removeAll(taken);
        return taken;
    }
}
