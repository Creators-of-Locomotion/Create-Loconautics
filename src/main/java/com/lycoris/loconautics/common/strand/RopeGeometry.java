package com.lycoris.loconautics.common.strand;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;
import org.joml.Vector3dc;

import java.util.ArrayList;
import java.util.List;

public final class RopeGeometry {

    private RopeGeometry() {}

    public static List<Vector3d> tautPolyline(Vector3dc a, Vector3dc b, double restLength) {
        return polyline(a, b, restLength);
    }

    public static int linkCount(double restLength) {
        return Math.max(1, (int) Math.ceil(restLength - 1.0e-3)) + 1;
    }

    public static List<Vector3d> copyPoints(List<? extends Vector3dc> points) {
        List<Vector3d> copy = new ArrayList<>(points.size());
        for (Vector3dc p : points) {
            copy.add(new Vector3d(p));
        }
        return copy;
    }

    public static List<Vec3> redistributeEvenly(List<Vec3> points) {
        int n = points.size() - 1;
        if (n <= 0) {
            return points;
        }
        double[] segLen = new double[n];
        double total = 0.0;
        for (int i = 0; i < n; i++) {
            segLen[i] = points.get(i).distanceTo(points.get(i + 1));
            total += segLen[i];
        }
        if (total < 1.0e-6) {
            return points;
        }
        double target = total / n;
        List<Vec3> out = new ArrayList<>(points.size());
        out.add(points.get(0));
        double segStart = 0.0;
        int seg = 0;
        for (int k = 1; k <= n; k++) {
            double want = k * target;
            while (seg < n - 1 && segStart + segLen[seg] < want) {
                segStart += segLen[seg];
                seg++;
            }
            double into = segLen[seg] < 1.0e-9 ? 0.0 : Mth.clamp((want - segStart) / segLen[seg], 0.0, 1.0);
            out.add(points.get(seg).lerp(points.get(seg + 1), into));
        }
        return out;
    }

    private static List<Vector3d> polyline(Vector3dc a, Vector3dc b, double restLength) {
        int n = linkCount(restLength);
        Vector3d dir = new Vector3d(b).sub(a);
        double span = dir.length();
        if (span > 1.0e-6) {
            dir.div(span);
        } else {
            dir.set(0.0, -1.0, 0.0);
        }
        List<Vector3d> points = new ArrayList<>(n + 1);
        Vector3d cursor = new Vector3d(a);
        points.add(new Vector3d(cursor));
        for (int i = 0; i < n; i++) {
            cursor.add(dir);
            points.add(new Vector3d(cursor));
        }
        return points;
    }
}