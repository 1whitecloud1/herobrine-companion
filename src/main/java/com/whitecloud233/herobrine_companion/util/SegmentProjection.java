package com.whitecloud233.herobrine_companion.util;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/** Projection onto a finite segment, with data cached for block/entity scans. */
public final class SegmentProjection {
    private final Vec3 start;
    private final Vec3 delta;
    private final double lengthSquared;

    public SegmentProjection(Vec3 start, Vec3 end) {
        this.start = start;
        this.delta = end.subtract(start);
        this.lengthSquared = this.delta.lengthSqr();
    }

    public boolean isDegenerate() { return this.lengthSquared < 1.0E-4D; }
    public Vec3 delta() { return this.delta; }

    public Vec3 closestPoint(Vec3 point) {
        if (isDegenerate()) return this.start;
        double t = Mth.clamp(point.subtract(this.start).dot(this.delta) / this.lengthSquared, 0.0D, 1.0D);
        return this.start.add(this.delta.scale(t));
    }
}
