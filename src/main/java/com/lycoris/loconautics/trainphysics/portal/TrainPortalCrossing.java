package com.lycoris.loconautics.trainphysics.portal;

import com.lycoris.loconautics.LoconauticsConstants;
import com.lycoris.loconautics.common.advancement.TrainAdvancements;
import com.lycoris.loconautics.common.sable.SableTeleportation;
import com.lycoris.loconautics.trainphysics.SableTrain;
import com.lycoris.loconautics.trainphysics.TrainConsist;
import com.lycoris.loconautics.trainphysics.TrainLength;
import com.lycoris.loconautics.common.sable.SubLevelData;
import com.lycoris.loconautics.config.ExperimentalConfig;
import com.lycoris.loconautics.config.LoconauticsConfig;
import com.lycoris.loconautics.trainphysics.drive.TrainRailAdvance;
import com.lycoris.loconautics.trainphysics.rail.RailCarriage;
import com.lycoris.loconautics.trainphysics.rail.RailFollower;
import com.lycoris.loconautics.trainphysics.spawn.SableTrainSpawner;
import com.lycoris.loconautics.trainphysics.state.CartEvent;
import com.lycoris.loconautics.trainphysics.state.CartStateStore;
import com.lycoris.loconautics.trainphysics.state.SableTrainRegistry;
import com.lycoris.loconautics.trainphysics.teleport.TrainTeleporter;
import com.lycoris.loconautics.trainphysics.wheels.GhostWheelBodies;
import com.simibubi.create.content.trains.entity.TravellingPoint;
import com.simibubi.create.content.trains.graph.TrackGraphLocation;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3ic;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class TrainPortalCrossing {

    private static final double HANDOVER_DEPTH = 0.25;

    private static final double CLEAR_MARGIN = 1.0;

    private static final long RETRY_TICKS = 40;

    private static final int CLEAR_HOLD = 20;

    private static final Map<UUID, Long> ISSUE278_UNTIL = new HashMap<>();

    private static final long REFUSAL_RETRY_TICKS = 20;

    private static final long POP_HOLD_TICKS = 40;

    private static final double POP_MARGIN = 2.0;

    private static final double POP_FAR_STEP = 40.0;

    private static final double POP_APPROACH = 1.0e-3;

    private static final double IDLE_SPEED = 0.005;

    private static final Vec3 POP_UP = new Vec3(0.0, 1.0, 0.0);

    private static final Map<UUID, Long> POP_RETRY = new HashMap<>();

    private static final Map<UUID, Double> POP_DEPTH = new HashMap<>();

    private static final class Straddle {
        PortalFrame frame;
        int clearTicks;
        boolean ready;

        Straddle(PortalFrame frame) {
            this.frame = frame;
        }
    }

    private static final class Crossing {
        final List<SableTrain> cars;
        final long builtAt;
        long decidedAt = -1;
        long retryAt;

        Crossing(List<SableTrain> cars, long builtAt, long retryAt) {
            this.cars = cars;
            this.builtAt = builtAt;
            this.retryAt = retryAt;
        }
    }

    private static final Map<UUID, Straddle> CARS = new HashMap<>();

    private static final Map<UUID, Crossing> CROSSING_OF = new HashMap<>();

    public static final CartStateStore STORE = new CartStateStore() {
        @Override
        public void forget(SableTrain train, UUID subId, CartEvent event) {
            if (clears(event)) {
                POP_DEPTH.remove(subId);
                end(train, false);
            }
        }

        @Override
        public void retain(Set<UUID> liveTrains, Set<UUID> liveSubs) {
            CARS.keySet().retainAll(liveSubs);
            CROSSING_OF.keySet().retainAll(liveSubs);
            ISSUE278_UNTIL.keySet().retainAll(liveSubs);
            POP_RETRY.keySet().retainAll(liveSubs);
            POP_DEPTH.keySet().retainAll(liveSubs);
        }

        @Override
        public String describe(UUID trainId, UUID subId) {
            Straddle straddle = CARS.get(subId);
            Crossing crossing = CROSSING_OF.get(subId);
            if (straddle == null && crossing == null) {
                return "";
            }
            String text = straddle == null ? "in a crossing chain" : "straddling " + straddle.frame.nearDim().location()
                    + " to " + straddle.frame.farDim().location() + " ready=" + straddle.ready + " clear="
                    + straddle.clearTicks;
            return crossing == null ? text : text + " chain=" + crossing.cars.size() + " retryAt=" + crossing.retryAt;
        }

        // a handover keeps the straddle, the car goes on straddling from the far side
        @Override
        public boolean clears(CartEvent event) {
            return event == CartEvent.REMOVED || event == CartEvent.RELOCATED || event == CartEvent.TELEPORTED
                    || event == CartEvent.REERAILED || event == CartEvent.EMBODIED || event == CartEvent.GHOSTED;
        }
    };

    private TrainPortalCrossing() {
    }

    public static Set<UUID> entering() {
        return Set.copyOf(CARS.keySet());
    }

    public static boolean isCrossing(UUID subLevelId) {
        return SableTeleportation.isInFlight(subLevelId);
    }

    public static PortalFrame straddle(UUID carSubLevelId) {
        Straddle s = CARS.get(carSubLevelId);
        return s == null ? null : s.frame;
    }

    public static void tick(SableTrain train) {
        SableTrain.Car car = train.car();
        if (car == null) {
            return;
        }
        if (!ExperimentalConfig.portalTwins()) {
            pop(train, car);
            return;
        }
        issue278After(train, car);
        PortalFrame frame = frameOf(car, train);
        if (frame == null) {
            end(train, false);
            return;
        }
        ServerLevel home = train.level();
        ServerSubLevel master = SubLevelData.resolve(home, car.subLevelId());
        if (master == null) {
            return;
        }
        double[] span = span(master, frame);
        if (span == null) {
            return;
        }
        Straddle straddle = CARS.computeIfAbsent(car.subLevelId(), id -> new Straddle(frame));
        straddle.frame = frame;
        if (!TrainRailAdvance.anyPointFar(car) && clear(train, span)) {
            straddle.clearTicks++;
            if (straddle.clearTicks >= CLEAR_HOLD) {
                end(train, true);
                return;
            }
        } else {
            straddle.clearTicks = 0;
        }
        PortalHold.hold(home, car.subLevelId());
        // twins first, a handover on the first straddle tick would otherwise skip the standby and pop the whole car over
        long straddleStart = System.nanoTime();
        straddle.ready = straddle(train, car, master, frame);
        long straddleMicros = (System.nanoTime() - straddleStart) / 1_000L;
        if (LoconauticsConfig.DEBUG && PortalTrace.dueAlways("longtrain" + car.subLevelId(),
                train.level().getGameTime(), 20)) {
            LoconauticsConstants.TRACE.info("[longtrain] tick car={} chain={} straddling={} straddleMicros={} {}",
                    PortalTrace.id(car.subLevelId()), chain(train).size(), CARS.size(), straddleMicros,
                    PortalTwins.debugCounts());
        }
        if (LoconauticsConfig.DEBUG) {
            traceJam(train, car, master, frame);
        }
        if (!straddle.ready) {
            PortalEntry.renew(car.subLevelId());
            PortalTwins.renew(car.subLevelId());
            return;
        }
        long now = home.getGameTime();
        Crossing crossing = crossingOf(train, now);
        PortalTwins.syncContacts(home.getServer(), ids(crossing.cars));
        if (crossing.decidedAt == now) {
            return;
        }
        crossing.decidedAt = now;
        handOver(train, crossing, frame);
    }

    private static void pop(SableTrain train, SableTrain.Car car) {
        UUID id = car.subLevelId();
        ServerLevel home = train.level();
        long now = home.getGameTime();
        PortalFrame frame = train.isDerailed() ? null : lookAhead(car, train);
        ServerSubLevel master = frame == null ? null : SubLevelData.resolve(home, id);
        if (master == null) {
            POP_DEPTH.remove(id);
            return;
        }
        double depth = frame.depth(master.logicalPose().position());
        Double before = POP_DEPTH.put(id, depth);
        boolean approaching = before != null && depth - before > POP_APPROACH;
        Long retryAt = POP_RETRY.get(id);
        if (retryAt != null && now < retryAt || !approaching && !TrainRailAdvance.anyPendingPortal(car)) {
            return;
        }
        ServerLevel far = home.getServer().getLevel(frame.farDim());
        TrackGraphLocation start = far == null ? null : frame.farStart(car.carriage().graph());
        Vec3 out = frame.exitDirection();
        double length = TrainLength.consistLength(home, id) + POP_MARGIN;
        TrackGraphLocation nose = start == null ? null
                : SableTrainSpawner.locationBack(start, POP_UP, out, -length, true);
        if (nose == null && start != null) {
            nose = SableTrainSpawner.locationBack(start, POP_UP, out, -trackPast(start, out, length), true);
        }
        List<SableTrain> chain = chain(train);
        if (nose == null) {
            refusePop(chain, now);
            return;
        }
        if (!holdFarChunks(far, frame, nose)) {
            return;
        }
        List<ServerPlayer> riders = new ArrayList<>();
        for (SableTrain member : chain) {
            ServerSubLevel sub = SubLevelData.resolve(member.level(), member.car().subLevelId());
            if (sub != null) {
                riders.addAll(TrainAdvancements.riders(sub));
            }
        }
        TrainTeleporter.Result result = TrainTeleporter.teleportThroughPortal(train, far, nose, POP_UP, out, frame,
                line -> PortalTrace.log("{}", line));
        if (!result.ok()) {
            refusePop(chain, now);
            return;
        }
        for (SableTrain member : result.cars()) {
            POP_RETRY.put(member.car().subLevelId(), now + POP_HOLD_TICKS);
            POP_DEPTH.remove(member.car().subLevelId());
        }
        TrainAdvancements.portal(riders);
    }

    private static double trackPast(TrackGraphLocation start, Vec3 out, double length) {
        for (double reach = length; reach > 0.0; reach -= 1.0) {
            if (SableTrainSpawner.locationBack(start, POP_UP, out, -reach, true) != null) {
                return reach;
            }
        }
        return 0.0;
    }

    private static void refusePop(List<SableTrain> chain, long now) {
        for (SableTrain member : chain) {
            POP_RETRY.put(member.car().subLevelId(), now + REFUSAL_RETRY_TICKS);
        }
    }

    private static boolean holdFarChunks(ServerLevel far, PortalFrame frame, TrackGraphLocation nose) {
        TravellingPoint tip = RailFollower.pointAt(nose);
        Vec3 end = tip == null ? null : tip.getPosition(nose.graph);
        Vec3 from = frame.farPoint();
        int steps = end == null ? 0 : (int) Math.ceil(from.distanceTo(end) / POP_FAR_STEP);
        boolean ready = true;
        for (int i = 0; i <= steps; i++) {
            Vec3 at = steps == 0 ? from : from.lerp(end, (double) i / steps);
            ready &= PortalHold.holdFarChunks(far, at.x, at.y, at.z);
        }
        return ready;
    }

    private static boolean clear(SableTrain train, double[] span) {
        return span[1] <= -CLEAR_MARGIN || span[1] <= 0.0 && Math.abs(train.speed()) < IDLE_SPEED;
    }

    private static PortalFrame frameOf(SableTrain.Car car, SableTrain train) {
        PortalFrame frame = unifyFrame(car, train);
        return frame != null ? frame : chainFrame(car, train);
    }

    // a trackless car has no rails to carry a frame, so it straddles with the chain it is coupled into
    private static PortalFrame chainFrame(SableTrain.Car car, SableTrain train) {
        if (CARS.isEmpty()) {
            return null;
        }
        Straddle own = CARS.get(car.subLevelId());
        if (own != null && own.frame.nearDim().equals(train.level().dimension())) {
            return own.frame;
        }
        ServerSubLevel master = SubLevelData.resolve(train.level(), car.subLevelId());
        if (master == null) {
            return null;
        }
        for (SableTrain member : chain(train)) {
            Straddle other = CARS.get(member.car().subLevelId());
            if (other == null || !other.frame.nearDim().equals(train.level().dimension())) {
                continue;
            }
            double[] span = span(master, other.frame);
            if (span != null && !clear(train, span)) {
                return other.frame;
            }
        }
        return null;
    }

    private static Crossing crossingOf(SableTrain train, long now) {
        Crossing known = CROSSING_OF.get(train.car().subLevelId());
        if (known != null && known.builtAt == now) {
            return known;
        }
        List<SableTrain> cars = chain(train);
        long retryAt = 0;
        for (SableTrain member : cars) {
            Crossing old = CROSSING_OF.get(member.car().subLevelId());
            if (old != null) {
                retryAt = Math.max(retryAt, old.retryAt);
            }
        }
        Crossing fresh = new Crossing(cars, now, retryAt);
        for (SableTrain member : cars) {
            CROSSING_OF.put(member.car().subLevelId(), fresh);
        }
        return fresh;
    }

    private static List<UUID> ids(List<SableTrain> cars) {
        List<UUID> ids = new ArrayList<>();
        for (SableTrain member : cars) {
            ids.add(member.car().subLevelId());
        }
        return ids;
    }

    private static void traceJam(SableTrain train, SableTrain.Car car, ServerSubLevel master, PortalFrame frame) {
        long now = train.level().getGameTime();
        if (!PortalTrace.dueAlways("xjam" + car.subLevelId(), now, 2)) {
            return;
        }
        var container = SubLevelContainer.getContainer(train.level());
        Vector3d velocity = container == null ? new Vector3d()
                : container.physicsSystem().getPipeline().getLinearVelocity(master, new Vector3d());
        StringBuilder points = new StringBuilder();
        for (RailCarriage rail : rails(car)) {
            points.append(rail.isFar(rail.leading()) ? 'F' : 'n').append(rail.isFar(rail.trailing()) ? 'F' : 'n')
                    .append(rail.reachedEnd() ? 'E' : '-').append(' ');
        }
        LoconauticsConstants.TRACE.info("[xjam] car={} tick={} depth={} trainV={} target={} "
                        + "bodyV={} derailed={} settle={} rails={}", PortalTrace.id(car.subLevelId()), now,
                PortalTrace.f(frame.depth(master.logicalPose().position())), PortalTrace.f(train.speed()),
                PortalTrace.f(train.targetSpeed()), PortalTrace.f(velocity.length()), train.isDerailed(),
                train.isPoseSettling(), points);
    }

    private static boolean straddle(SableTrain train, SableTrain.Car car, ServerSubLevel master, PortalFrame frame) {
        MinecraftServer server = train.level().getServer();
        ServerLevel farLevel = server.getLevel(frame.farDim());
        Vector3d farCenter = frame.toFar(master.logicalPose().position());
        if (farLevel == null || !PortalHold.holdFarChunks(farLevel, farCenter.x, farCenter.y, farCenter.z)) {
            return false;
        }
        List<UUID> homeBodies = new ArrayList<>();
        List<UUID> twinBodies = new ArrayList<>();
        List<UUID> standbyBodies = new ArrayList<>();
        homeBodies.add(car.subLevelId());
        ServerSubLevel twin = PortalTwins.ensure(master, frame, car.subLevelId());
        if (twin != null) {
            twinBodies.add(twin.getUniqueId());
        }
        for (UUID plateId : GhostWheelBodies.wheelBodiesOf(car.subLevelId())) {
            ServerSubLevel plate = SubLevelData.resolve(train.level(), plateId);
            if (plate == null) {
                continue;
            }
            homeBodies.add(plateId);
            ServerSubLevel plateTwin = PortalTwins.ensure(plate, frame, car.subLevelId());
            if (plateTwin != null) {
                twinBodies.add(plateTwin.getUniqueId());
            }
        }
        PortalTwins.retain(server, car.subLevelId(), homeBodies);
        UUID standby = PortalTwins.ensureStandby(master, car.subLevelId());
        if (standby != null) {
            standbyBodies.add(standby);
        }
        PortalEntry.sync(train.level(), car.subLevelId(), frame, homeBodies, standbyBodies,
                server.getLevel(frame.farDim()), twinBodies);
        if (PortalTrace.due(car.subLevelId(), train.level().getGameTime(), 20)) {
            double[] span = span(master, frame);
            PortalTrace.log("straddle car={} {} to {} span={} to {} home={} plates={} twins={} standby={} "
                            + "twinOfCar={} anyPointFar={}", PortalTrace.id(car.subLevelId()),
                    frame.nearDim().location(), frame.farDim().location(),
                    span == null ? "n/a" : PortalTrace.f(span[0]), span == null ? "n/a" : PortalTrace.f(span[1]),
                    homeBodies.size(), homeBodies.size() - 1, twinBodies.size(), standbyBodies.size(),
                    twin != null, TrainRailAdvance.anyPointFar(car));
        }
        return true;
    }

    private static void issue286(SableTrain train, String kind, String why) {
        if (PortalTrace.dueAlways("issue286" + train.car().subLevelId() + kind, train.level().getGameTime(), 40)) {
            LoconauticsConstants.TRACE.info("[issue286] car={} dim={} speed={} physics={} {}",
                    PortalTrace.id(train.car().subLevelId()), train.level().dimension().location(),
                    PortalTrace.f(train.speed()), train.isPhysics(), why);
        }
    }

    private static void handOver(SableTrain train, Crossing crossing, PortalFrame frame) {
        List<SableTrain> chain = crossing.cars;
        List<SableTrain> past = new ArrayList<>();
        List<ServerPlayer> riders = new ArrayList<>();
        for (SableTrain member : chain) {
            ServerSubLevel sub = member.level() == train.level()
                    ? SubLevelData.resolve(member.level(), member.car().subLevelId()) : null;
            if (sub == null) {
                issue286(train, "nobody", "no live body for car " + PortalTrace.id(member.car().subLevelId()) + " on this side");
                return;
            }
            riders.addAll(TrainAdvancements.riders(sub));
            if (frame.depth(sub.logicalPose().position()) > HANDOVER_DEPTH) {
                past.add(member);
            }
        }
        long now = train.level().getGameTime();
        if (past.size() * 2 <= chain.size()) {
            issue286(train, "past", "waiting, only " + past.size() + " of " + chain.size() + " cars are past the plane");
            return;
        }
        long retryAt = crossing.retryAt;
        if (now < retryAt) {
            issue286(train, "backoff", "backing off after a failed handover for " + (retryAt - now) + " more ticks");
            return;
        }
        for (SableTrain member : chain) {
            Straddle waitingOn = CARS.get(member.car().subLevelId());
            if (waitingOn != null && !waitingOn.ready) {
                issue286(train, "chunks", "waiting on the far chunks of car " + PortalTrace.id(member.car().subLevelId()));
                return;
            }
        }
        String refusal = TrainTeleporter.handOverRefusal(chain);
        if (refusal != null) {
            issue286(train, "refused", "refused: " + refusal);
            if (PortalTrace.dueAlways("teleportfix" + train.car().subLevelId(), now, 20)) {
                ServerSubLevel own = SubLevelData.resolve(train.level(), train.car().subLevelId());
                LoconauticsConstants.TRACE.warn("[teleportfix] refused car={} why={} speed={} target={} depth={} "
                                + "derailed={}", PortalTrace.id(train.car().subLevelId()), refusal,
                        PortalTrace.f(train.speed()), PortalTrace.f(train.targetSpeed()),
                        own == null ? "n/a" : PortalTrace.f(frame.depth(own.logicalPose().position())),
                        train.isDerailed());
            }
            if (PortalTrace.due("refusal" + train.car().subLevelId(), now, 20)) {
                PortalTrace.log("handover refused car={} why={}", PortalTrace.id(train.car().subLevelId()), refusal);
            }
            crossing.retryAt = now + REFUSAL_RETRY_TICKS;
            return;
        }
        MinecraftServer server = train.level().getServer();
        boolean waiting = false;
        for (SableTrain member : chain) {
            UUID id = member.car().subLevelId();
            waiting |= PortalTwins.standbyPending(id);
        }
        if (waiting) {
            issue286(train, "standby", "waiting on a twin standby body");
            if (PortalTrace.due("waiting" + train.car().subLevelId(), now, 5)) {
                PortalTrace.log("handover waiting on standby car={}", PortalTrace.id(train.car().subLevelId()));
            }
            return;
        }
        for (SableTrain member : chain) {
            UUID id = member.car().subLevelId();
            PortalTwins.softenTwin(server, id);
            PortalHold.release(server, id);
        }
        issue278Before(chain, frame, past);
        long handStarted = System.nanoTime();
        TrainTeleporter.Result result = TrainTeleporter.handOver(chain, frame,
                ignored -> { });
        long handMs = (System.nanoTime() - handStarted) / 1_000_000L;
        if (handMs >= 40) {
            LoconauticsConstants.LOGGER.warn("[portal] slow handover of {} took {}ms", PortalTrace.id(train.car().subLevelId()),
                    handMs);
        }
        PortalTrace.log("handover result car={} ok={} moved={}", PortalTrace.id(train.car().subLevelId()),
                result.ok(), result.moved());
        LoconauticsConstants.TRACE.info("[longtrain] handover car={} chain={} past={} ok={} moved={} ms={} {}",
                PortalTrace.id(train.car().subLevelId()), chain.size(), past.size(), result.ok(), result.moved(),
                handMs, PortalTwins.debugCounts());
        if (!result.ok()) {
            LoconauticsConstants.LOGGER.warn("[issue286] [issue281] handover failed car={} chain={} moved={} why={}",
                    PortalTrace.id(train.car().subLevelId()), chain.size(), result.moved(), result.failure());
            crossing.retryAt = now + RETRY_TICKS;
            return;
        }
        PortalFrame turned = frame.inverted();
        for (SableTrain member : chain) {
            ISSUE278_UNTIL.put(member.car().subLevelId(), now + 100);
        }
        for (SableTrain member : chain) {
            UUID id = member.car().subLevelId();
            PortalEntry.clear(id, PortalTwins.handedOver(server, id, turned));
            Straddle landed = CARS.get(id);
            if (landed != null) {
                landed.frame = turned;
                landed.clearTicks = 0;
            }
        }
        crossing.retryAt = 0;
        TrainAdvancements.portal(riders);
        LoconauticsConstants.TRACE.info("[portal] handed {} car(s) from {} to {}", result.moved(),
                frame.nearDim().location(), frame.farDim().location());
    }

    private static void issue278Before(List<SableTrain> chain, PortalFrame frame, List<SableTrain> past) {
        StringBuilder out = new StringBuilder();
        for (SableTrain member : chain) {
            ServerSubLevel sub = SubLevelData.resolve(member.level(), member.car().subLevelId());
            out.append(PortalTrace.id(member.car().subLevelId())).append(" depth=")
                    .append(sub == null ? "none" : PortalTrace.f(frame.depth(sub.logicalPose().position())))
                    .append(" past=").append(past.contains(member)).append(" far=")
                    .append(TrainRailAdvance.anyPointFar(member.car())).append(" physics=").append(member.isPhysics())
                    .append(" derailed=").append(member.isDerailed()).append(" speed=")
                    .append(PortalTrace.f(member.speed())).append(" | ");
        }
        LoconauticsConstants.TRACE.info("[issue278] handover chain={} past={} cars: {}", chain.size(), past.size(), out);
    }

    private static void issue278After(SableTrain train, SableTrain.Car car) {
        Long until = ISSUE278_UNTIL.get(car.subLevelId());
        if (until == null) {
            return;
        }
        long now = train.level().getGameTime();
        if (now > until) {
            ISSUE278_UNTIL.remove(car.subLevelId());
            return;
        }
        if (!PortalTrace.dueAlways("issue278" + car.subLevelId(), now, 10)) {
            return;
        }
        ServerSubLevel sub = SubLevelData.resolve(train.level(), car.subLevelId());
        PortalFrame own = rails(car).stream().map(RailCarriage::portal).filter(f -> f != null).findFirst().orElse(null);
        StringBuilder rails = new StringBuilder();
        for (RailCarriage rail : rails(car)) {
            rails.append(rail.isFar(rail.leading()) ? 'F' : 'n').append(rail.isFar(rail.trailing()) ? 'F' : 'n')
                    .append(rail.reachedEnd() ? 'E' : '-').append(' ');
        }
        LoconauticsConstants.TRACE.info("[issue278] after car={} level={} body={} depthInOwnFrame={} frame={} rails={} "
                        + "derailed={} physics={} settle={} speed={} partners={}", PortalTrace.id(car.subLevelId()),
                train.level().dimension().location(), sub != null,
                sub == null || own == null ? "n/a" : PortalTrace.f(own.depth(sub.logicalPose().position())),
                own != null, rails, train.isDerailed(), train.isPhysics(), train.isPoseSettling(),
                PortalTrace.f(train.speed()), chain(train).size());
    }

    // a body overhangs its bogeys, so the straddle has to start when its front nears the plane, not when a bogey does
    private static PortalFrame lookAhead(SableTrain.Car car, SableTrain train) {
        ServerSubLevel master = SubLevelData.resolve(train.level(), car.subLevelId());
        BoundingBox3ic bounds = master == null ? null : master.getPlot().getBoundingBox();
        if (car.carriage() == null || bounds == null || bounds.volume() <= 0) {
            return null;
        }
        double reach = Math.max(bounds.maxX() - bounds.minX(), bounds.maxZ() - bounds.minZ()) + 1.0;
        PortalFrame ahead = car.carriage().portalWithin(reach);
        double[] span = ahead == null ? null : span(master, ahead);
        if (span == null || clear(train, span)) {
            return null;
        }
        return ahead;
    }

    private static PortalFrame unifyFrame(SableTrain.Car car, SableTrain train) {
        PortalFrame frame = rails(car).stream().map(RailCarriage::portal)
                .filter(own -> own != null && own.nearDim().equals(train.level().dimension()))
                .findFirst().orElse(null);
        if (frame == null) {
            frame = lookAhead(car, train);
        }
        if (frame == null) {
            return null;
        }
        for (RailCarriage rail : rails(car)) {
            if (rail.portal() != frame) {
                rail.setPortal(frame);
            }
        }
        return frame;
    }

    public static void cancel(SableTrain train) {
        end(train, true);
    }

    private static void end(SableTrain train, boolean clearRails) {
        SableTrain.Car car = train.car();
        if (car == null) {
            return;
        }
        UUID id = car.subLevelId();
        if (clearRails) {
            for (RailCarriage rail : rails(car)) {
                rail.clearPortal();
            }
        }
        CROSSING_OF.remove(id);
        if (CARS.remove(id) != null) {
            PortalTrace.log("end straddle car={} clearRails={}", PortalTrace.id(id), clearRails);
        }
        MinecraftServer server = train.level().getServer();
        PortalTwins.endStraddle(server, id);
        PortalEntry.clear(id);
        PortalHold.release(server, id);
    }

    public static void clearAll(MinecraftServer server) {
        PortalTwins.dropAll(server);
        PortalTwins.clearAll();
        PortalHold.releaseAll(server);
        PortalEntry.clearAll();
        PortalColliders.clearAll();
        PortalTrace.clearAll();
        CARS.clear();
        CROSSING_OF.clear();
        ISSUE278_UNTIL.clear();
        POP_RETRY.clear();
        POP_DEPTH.clear();
    }

    private static List<RailCarriage> rails(SableTrain.Car car) {
        List<RailCarriage> rails = new ArrayList<>();
        if (car.carriage() != null) {
            rails.add(car.carriage());
        }
        for (SableTrain.Bogey bogey : car.bogeys()) {
            if (bogey.rail() != null) {
                rails.add(bogey.rail());
            }
        }
        return rails;
    }

    private static List<SableTrain> chain(SableTrain train) {
        List<SableTrain> cars = new ArrayList<>();
        for (UUID id : TrainConsist.orderedChainThisTick(train.level(), train.car().subLevelId())) {
            SableTrain member = SableTrainRegistry.bySubLevel(id);
            if (member != null && member.level() == train.level() && member.car() != null) {
                cars.add(member);
            }
        }
        return cars.isEmpty() ? List.of(train) : cars;
    }

    private static double[] span(ServerSubLevel sub, PortalFrame frame) {
        BoundingBox3ic bounds = sub.getPlot().getBoundingBox();
        if (bounds == null || bounds.volume() <= 0) {
            return null;
        }
        Pose3dc pose = sub.logicalPose();
        double min = Double.MAX_VALUE;
        double max = -Double.MAX_VALUE;
        Vector3d corner = new Vector3d();
        for (int i = 0; i < 8; i++) {
            corner.set((i & 1) == 0 ? bounds.minX() : bounds.maxX() + 1.0,
                    (i & 2) == 0 ? bounds.minY() : bounds.maxY() + 1.0,
                    (i & 4) == 0 ? bounds.minZ() : bounds.maxZ() + 1.0);
            pose.transformPosition(corner);
            double depth = frame.depth(corner);
            min = Math.min(min, depth);
            max = Math.max(max, depth);
        }
        return new double[] {min, max};
    }
}
