package com.whitecloud233.herobrine_companion.combat.poem;

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
    public static final double BLADE_PADDING = .10;
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
                PoemOBB box = PoemOBB.sweep(edge.inner, edge.outer, next.inner, next.outer,
                        BLADE_PADDING * Math.max(previous.scale, actor.scale));
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
    public record Actor(Vec3 position, float yaw, boolean leftHanded, double renderOffsetY, float scale) {
        boolean valid() {
            return Double.isFinite(position.x) && Double.isFinite(position.y) && Double.isFinite(position.z)
                    && Float.isFinite(yaw) && Double.isFinite(renderOffsetY) && Float.isFinite(scale) && scale > 0;
        }

        Actor interpolate(Actor to, double alpha) {
            alpha = Math.max(0, Math.min(1, alpha));
            return new Actor(position.lerp(to.position, alpha), yaw + yawDelta(yaw, to.yaw) * (float) alpha,
                    leftHanded, renderOffsetY + (to.renderOffsetY - renderOffsetY) * alpha,
                    scale + (to.scale - scale) * (float) alpha);
        }

        public Vec3 toWorld(Vector3f point) {
            double radians = Math.toRadians(yaw), sin = Math.sin(radians), cos = Math.cos(radians);
            double x = leftHanded ? -point.x : point.x;
            // LivingEntityRenderer applies the 1.21.1 scale attribute before the PlayerRenderer transforms.
            double modelScale = PoemBladeTrail.PLAYER_SCALE * scale;
            return position.add((-x * cos - point.y * sin) * modelScale,
                    (point.z + .001) * modelScale + renderOffsetY,
                    (-x * sin + point.y * cos) * modelScale);
        }
    }

    private record Edge(Vec3 inner, Vec3 outer) { }

    public record Sweep(int contact, List<PoemOBB> boxes, AABB bounds) {
        public boolean intersects(AABB target) {
            if (!bounds.inflate(1e-7).intersects(target)) return false;
            for (PoemOBB box : boxes) if (box.intersects(target)) return true;
            return false;
        }
    }
}
