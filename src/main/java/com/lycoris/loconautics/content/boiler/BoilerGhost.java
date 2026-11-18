package com.lycoris.loconautics.content.boiler;

import com.lycoris.loconautics.LoconauticsConstants;
import com.lycoris.loconautics.trainphysics.SableTrain;

import java.util.List;

// server only, an unloaded boiler burns its fuel and water
public final class BoilerGhost {

    private BoilerGhost() {
    }

    // the boiler can be on any car of the rake, the leader is the one that gets stopped
    public static void step(SableTrain leader, List<SableTrain> group) {
        BoilerSummary summary = null;
        for (SableTrain car : group) {
            if (car.boilerSummary() != null) {
                summary = car.boilerSummary();
                break;
            }
        }
        boolean log = (leader.level().getGameTime() + leader.id().hashCode()) % 100 == 0;
        boolean scheduled = BoilerStopAlert.hasWorkingSchedule(group);
        if (summary == null || !scheduled) {
            if (log) LoconauticsConstants.LOGGER.warn("[boilerghost] skip train={} summary={} scheduled={}",
                    leader.id(), summary != null, scheduled);
            return;
        }
        summary.spendTick();
        String problem = summary.problem();
        if (log) LoconauticsConstants.LOGGER.warn("[boilerghost] step train={} {} problem={} target={} speed={} cruise={}",
                leader.id(), summary.describe(), problem, leader.targetSpeed(), leader.speed(), leader.ghostCruise());
        if (problem == null) return;
        leader.setTargetSpeed(0.0);
        BoilerStopAlert.check(leader, group, summary, null);
    }
}
