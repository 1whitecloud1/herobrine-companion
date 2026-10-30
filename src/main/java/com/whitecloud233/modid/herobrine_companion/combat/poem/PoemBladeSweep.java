package com.whitecloud233.modid.herobrine_companion.combat.poem;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** Server animation sampling; neither fading trail history nor recovery poses deal damage. */
public final class PoemBladeSweep {
    /** Thickness/length tolerance around the visible blade edge. */
    public static final double BLADE_PADDING = .14;
    /**
     * Vertical reach of a cut, from the target's point of view. Small, low entities (silverfish,
     * small slimes, baby mobs) sit far below a chest-height arc, so the target box may extend
     * this far up to meet the blade. The smaller downward value still lets low sweeps catch
     * targets standing on a ledge or slab without turning every cut into a full-height column.
     */
    public static final double HIT_REACH_UP = 1.05;
    public static final double HIT_REACH_DOWN = .45;
    /** Broad-phase margin on the horizontal axes so the vertical expansion is actually queried. */
    public static final double HIT_QUERY_MARGIN = .10;
    private static final int SAMPLE_RATE = PoemBladeTrail.SAMPLES_PER_SECOND * 2;
    private final PoemMotionLibrary.Clip clip;
    private final int toolJoint;
    private final double start;
    private final List<Set<UUID>> hitTargets;
    private double lastTick;
    private Actor previous;
    private boolean cancelled;

    public PoemBladeSweep(int mode, int step, double start, Actor actor) {
        clip = PoemMotionLibrary.get().clip(mode, step);
        toolJoint = PoemMotionLibrary.get().joint("Tool_R");
        hitTargets = new ArrayList<>();
        for (var ignored : clip.timing.contacts()) hitTargets.add(new HashSet<>());
        this.start = lastTick = start;
        previous = actor;
    }

    public List<Sweep> poll(double tick, Actor actor) {
        if (cancelled) return List.of();
        // Teleporting or changing handedness must not sweep a blade across the intervening space.
        if (!Double.isFinite(tick) || tick < lastTick || !actor.valid() || !previous.valid()
                || previous.position.distanceToSqr(actor.position) > 16 || previous.leftHanded != actor.leftHanded) {
            cancelled = true;
            return List.of();
        }
        if (tick == lastTick) return List.of();
        double before = (lastTick - start) / 20, after = (tick - start) / 20;
        List<Sweep> sweeps = new ArrayList<>();
        List<PoemMotionLibrary.Contact> contacts = clip.timing.contacts();
        for (int contact = 0; contact < contacts.size(); contact++) {
            var window = contacts.get(contact);
            if (before >= window.end() - 1e-6 || after < window.start() - 1e-6) continue;
            double from = Math.max(before, window.start()), to = Math.max(from, Math.min(after, window.end()));
            double fraction = (to - from) / (after - before);
            int count = Math.max(1, (int) Math.ceil((to - from) * SAMPLE_RATE));
            count = Math.max(count, (int) Math.ceil(Math.abs(yawDelta(previous.yaw, actor.yaw)) * fraction / 4));
            count = Math.max(count, (int) Math.ceil(previous.position.distanceTo(actor.position) * fraction / .15));
            List<PoemOBB> boxes = new ArrayList<>(count);
            Edge edge = edge(from, previous.interpolate(actor, (from - before) / (after - before)));
            AABB bounds = null;
            for (int i = 1; i <= count; i++) {
                double time = from + (to - from) * i / count;
                Edge next = edge(time, previous.interpolate(actor, (time - before) / (after - before)));
                PoemOBB box = PoemOBB.sweep(edge.inner, edge.outer, next.inner, next.outer, BLADE_PADDING);
                boxes.add(box);
                bounds = bounds == null ? box.bounds() : bounds.minmax(box.bounds());
                edge = next;
            }
            sweeps.add(new Sweep(contact, List.copyOf(boxes), bounds));
        }
        previous = actor;
        lastTick = tick;
        return List.copyOf(sweeps);
    }

    public boolean cancelled() { return cancelled; }

    public boolean claim(Sweep sweep, UUID target, AABB bounds) {
        return !cancelled && sweep.intersects(bounds) && hitTargets.get(sweep.contact()).add(target);
    }

    private Edge edge(double time, Actor actor) {
        Matrix4f transform = clip.sampleJoint(toolJoint, (float) time, true);
        return new Edge(actor.toWorld(transform.transformPosition(PoemBladeTrail.inner())),
                actor.toWorld(transform.transformPosition(PoemBladeTrail.outer())));
    }

    private static float yawDelta(float from, float to) {
        float delta = (to - from) % 360;
        return delta >= 180 ? delta - 360 : delta < -180 ? delta + 360 : delta;
    }

    /** Position stays in doubles; only the small, local animation coordinates use floats. */
    public record Actor(Vec3 position, float yaw, boolean leftHanded, double renderOffsetY) {
        boolean valid() {
            return Double.isFinite(position.x) && Double.isFinite(position.y) && Double.isFinite(position.z)
                    && Float.isFinite(yaw) && Double.isFinite(renderOffsetY);
        }

        Actor interpolate(Actor to, double alpha) {
            alpha = Math.max(0, Math.min(1, alpha));
            return new Actor(position.lerp(to.position, alpha), yaw + yawDelta(yaw, to.yaw) * (float) alpha,
                    leftHanded, renderOffsetY + (to.renderOffsetY - renderOffsetY) * alpha);
        }

        public Vec3 toWorld(Vector3f point) {
            double radians = Math.toRadians(yaw), sin = Math.sin(radians), cos = Math.cos(radians);
            double x = leftHanded ? -point.x : point.x;
            // PlayerRenderer: Y(180-yaw), scale(-1,-1,1), scale(.9375), translate(0,-1.501,0), DCC_TO_MODEL.
            return position.add((-x * cos - point.y * sin) * PoemBladeTrail.PLAYER_SCALE,
                    (point.z + .001) * PoemBladeTrail.PLAYER_SCALE + renderOffsetY,
                    (-x * sin + point.y * cos) * PoemBladeTrail.PLAYER_SCALE);
        }
    }

    private record Edge(Vec3 inner, Vec3 outer) { }

    public record Sweep(int contact, List<PoemOBB> boxes, AABB bounds) {
        /** Broad-phase volume including the vertical reach; also used for the entity query. */
        public AABB queryBounds() {
            return new AABB(bounds.minX - HIT_QUERY_MARGIN, bounds.minY - HIT_REACH_DOWN, bounds.minZ - HIT_QUERY_MARGIN,
                    bounds.maxX + HIT_QUERY_MARGIN, bounds.maxY + HIT_REACH_UP, bounds.maxZ + HIT_QUERY_MARGIN);
        }

        public boolean intersects(AABB target) {
            // Expanding the target is the Minkowski equivalent of a taller cut volume and keeps the
            // exact blade SAT untouched, so the sampled sweep stays a few small OBBs.
            AABB reached = new AABB(target.minX, target.minY - HIT_REACH_DOWN, target.minZ,
                    target.maxX, target.maxY + HIT_REACH_UP, target.maxZ);
            if (!bounds.inflate(1e-7).intersects(reached)) return false;
            for (PoemOBB box : boxes) if (box.intersects(reached)) return true;
            return false;
        }
    }
}
