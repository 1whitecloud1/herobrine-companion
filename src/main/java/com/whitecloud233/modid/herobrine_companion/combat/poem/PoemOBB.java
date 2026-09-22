package com.whitecloud233.modid.herobrine_companion.combat.poem;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** A small oriented box enclosing one swept blade edge, with a full 15-axis SAT test. */
public final class PoemOBB {
    private static final Vec3[] WORLD_AXES = {new Vec3(1, 0, 0), new Vec3(0, 1, 0), new Vec3(0, 0, 1)};
    private final Vec3 center;
    private final Vec3[] axes;
    private final double[] half;
    private final AABB bounds;

    private PoemOBB(Vec3 center, Vec3[] axes, double[] half) {
        this.center = center;
        this.axes = axes;
        this.half = half;
        double x = radius(WORLD_AXES[0]), y = radius(WORLD_AXES[1]), z = radius(WORLD_AXES[2]);
        bounds = new AABB(center.x - x, center.y - y, center.z - z, center.x + x, center.y + y, center.z + z);
    }

    public static PoemOBB sweep(Vec3 innerBefore, Vec3 outerBefore, Vec3 innerAfter, Vec3 outerAfter, double padding) {
        Vec3 along = outerBefore.subtract(innerBefore).add(outerAfter.subtract(innerAfter));
        if (along.lengthSqr() < 1e-12) along = outerBefore.subtract(innerBefore);
        if (along.lengthSqr() < 1e-12) along = WORLD_AXES[0];
        along = along.normalize();
        Vec3 across = innerAfter.subtract(innerBefore).add(outerAfter.subtract(outerBefore));
        across = across.subtract(along.scale(across.dot(along)));
        if (across.lengthSqr() < 1e-12) {
            Vec3 guide = Math.abs(along.y) < .9 ? WORLD_AXES[1] : WORLD_AXES[0];
            across = guide.subtract(along.scale(guide.dot(along)));
        }
        across = across.normalize();
        Vec3[] axes = {along, across, along.cross(across).normalize()};
        // Project near the blade, so world-border coordinates retain their precision.
        Vec3[] points = {Vec3.ZERO, outerBefore.subtract(innerBefore),
                innerAfter.subtract(innerBefore), outerAfter.subtract(innerBefore)};
        Vec3 center = innerBefore;
        double[] half = new double[3];
        for (int i = 0; i < 3; i++) {
            double min = 0, max = 0;
            for (Vec3 point : points) {
                double projection = point.dot(axes[i]);
                min = Math.min(min, projection); max = Math.max(max, projection);
            }
            center = center.add(axes[i].scale((min + max) * .5));
            half[i] = (max - min) * .5 + padding;
        }
        return new PoemOBB(center, axes, half);
    }

    public AABB bounds() { return bounds; }

    public boolean intersects(AABB target) {
        if (!bounds.inflate(1e-7).intersects(target)) return false;
        Vec3 offset = target.getCenter().subtract(center);
        Vec3 targetHalf = new Vec3(target.getXsize() * .5, target.getYsize() * .5, target.getZsize() * .5);
        for (Vec3 axis : axes) if (separated(axis, offset, targetHalf)) return false;
        for (Vec3 world : WORLD_AXES) {
            if (separated(world, offset, targetHalf)) return false;
            for (Vec3 axis : axes) if (separated(axis.cross(world), offset, targetHalf)) return false;
        }
        return true;
    }

    private double radius(Vec3 axis) {
        return half[0] * Math.abs(axes[0].dot(axis)) + half[1] * Math.abs(axes[1].dot(axis))
                + half[2] * Math.abs(axes[2].dot(axis));
    }

    private boolean separated(Vec3 axis, Vec3 offset, Vec3 targetHalf) {
        if (axis.lengthSqr() < 1e-14) return false;
        double targetRadius = Math.abs(axis.x) * targetHalf.x + Math.abs(axis.y) * targetHalf.y
                + Math.abs(axis.z) * targetHalf.z;
        return Math.abs(offset.dot(axis)) > radius(axis) + targetRadius + 1e-7;
    }
}
