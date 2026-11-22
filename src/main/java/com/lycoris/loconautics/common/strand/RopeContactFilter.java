package com.lycoris.loconautics.common.strand;

import com.lycoris.loconautics.common.sable.BodyRef;
import com.lycoris.loconautics.common.strand.AttachedRopePhysicsObject.Attachment;
import dev.ryanhcode.sable.api.physics.callback.BlockSubLevelCollisionCallback;
import dev.ryanhcode.sable.api.physics.object.rope.RopeHandle;
import dev.ryanhcode.sable.companion.math.JOMLConversion;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;
import org.joml.Vector3dc;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class RopeContactFilter implements BlockSubLevelCollisionCallback {

    public static final RopeContactFilter INSTANCE = new RopeContactFilter();

    private static final double MARGIN = 0.0625;
    private static final CollisionResult REMOVE = new CollisionResult(JOMLConversion.ZERO, true);

    private record Pass(ResourceKey<Level> dimension, RopeHandle.AttachmentPoint end, int segments) {}

    private static final Map<AttachedRopePhysicsObject, Pass> PASSES = new ConcurrentHashMap<>();

    private RopeContactFilter() {
    }

    public static void allow(ServerLevel level, AttachedRopePhysicsObject rope, RopeHandle.AttachmentPoint end, int segments) {
        PASSES.put(rope, new Pass(level.dimension(), end, Math.max(1, segments)));
    }

    public static void clear(AttachedRopePhysicsObject rope) {
        PASSES.remove(rope);
    }

    @Override
    public CollisionResult sable$onCollision(BlockPos pos, @Nullable BlockPos otherPos, Vector3d hit, double impactVelocity) {
        SubLevelPhysicsSystem system = SubLevelPhysicsSystem.getCurrentlySteppingSystem();
        if (otherPos != null || system == null) {
            return CollisionResult.NONE;
        }
        ServerLevel level = system.getLevel();
        for (Map.Entry<AttachedRopePhysicsObject, Pass> e : PASSES.entrySet()) {
            AttachedRopePhysicsObject rope = e.getKey();
            Pass pass = e.getValue();
            if (!rope.isActive()) {
                PASSES.remove(rope);
                continue;
            }
            if (!pass.dimension().equals(level.dimension())) {
                continue;
            }
            StrandEnd end = rope.endOf(pass.end());
            if (end == null || !end.local().equals(pos)) {
                continue;
            }
            if (passes(level, rope, pass, end, hit)) {
                return REMOVE;
            }
        }
        return CollisionResult.NONE;
    }

    private static boolean passes(ServerLevel level, AttachedRopePhysicsObject rope, Pass pass, StrandEnd end,
                                  Vector3d hit) {
        List<Vector3d> points = rope.getPoints();
        int n = points.size();
        Attachment att = rope.attachments.get(pass.end());
        if (n < 2 || att == null) {
            return false;
        }
        ServerSubLevel sub = end.subLevelId() == null ? null : BodyRef.of(level, end.subLevelId()).sub();
        Vector3d[] chain = new Vector3d[Math.min(pass.segments(), n - 1) + 1];
        chain[0] = rope.holderPoint(level, att, sub);
        for (int k = 1; k < chain.length; k++) {
            Vector3d p = points.get(pass.end() == RopeHandle.AttachmentPoint.END ? n - 1 - k : k);
            chain[k] = sub == null ? new Vector3d(p) : sub.logicalPose().transformPositionInverse(p, new Vector3d());
        }
        double reach = rope.getCollisionRadius() + MARGIN;
        double nearest = chain.length == 1 ? hit.distance(chain[0]) : Double.MAX_VALUE;
        for (int k = 1; k < chain.length; k++) {
            nearest = Math.min(nearest, segmentDistance(hit, chain[k - 1], chain[k]));
        }
        return nearest <= reach;
    }

    private static double segmentDistance(Vector3dc p, Vector3dc a, Vector3dc b) {
        double abx = b.x() - a.x();
        double aby = b.y() - a.y();
        double abz = b.z() - a.z();
        double lenSq = abx * abx + aby * aby + abz * abz;
        double t = lenSq < 1.0e-12 ? 0.0
                : ((p.x() - a.x()) * abx + (p.y() - a.y()) * aby + (p.z() - a.z()) * abz) / lenSq;
        t = Math.max(0.0, Math.min(1.0, t));
        double dx = a.x() + abx * t - p.x();
        double dy = a.y() + aby * t - p.y();
        double dz = a.z() + abz * t - p.z();
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }
}
