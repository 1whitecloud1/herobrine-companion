package com.whitecloud233.herobrine_companion.combat.poem;

import java.util.ArrayList;
import java.util.List;
import java.util.function.DoubleFunction;
import java.util.function.DoubleUnaryOperator;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** Short ribbons sampled from the same Tool_R socket as the standalone weapon. */
public final class PoemBladeTrail {
    public static final float LIFETIME = .22f;
    public static final int SAMPLES_PER_SECOND = 120;
    public static final float PLAYER_SCALE = .9375f;

    private PoemBladeTrail() { }

    // Blade extremities in Tool_R space, after rig.weapon_to_tool (not the handle).
    public static Vector3f inner() { return new Vector3f(-.026746f, -1.835568f, -1.454998f); }
    public static Vector3f outer() { return new Vector3f(-.026746f, .888342f, -2.335641f); }

    public static Matrix4f toModel() {
        return new Matrix4f().m00(-1).m11(0).m22(0).m12(-1).m21(-1).m31(1.5f);
    }

    /** Finish the vanilla-pose transition before the first damaging blade sample. */
    public static float blendTime(PoemMotionLibrary.Timing timing, boolean continuing) {
        return Math.max(.00001f, Math.min(continuing ? .035f : .1f, timing.contacts().get(0).start()));
    }

    public static List<Ribbon> sample(PoemMotionLibrary.Timing timing, float age,
                                      DoubleFunction<Matrix4f> tool, DoubleUnaryOperator poseWeight) {
        if (!Float.isFinite(age) || age < 0) return List.of();
        List<Ribbon> ribbons = new ArrayList<>();
        for (PoemMotionLibrary.Contact contact : timing.contacts()) {
            float from = Math.max(contact.start(), age - LIFETIME);
            float to = Math.min(contact.end(), age);
            if (to - from < .00001f) continue;
            int segments = Math.max(1, (int) Math.ceil((to - from) * SAMPLES_PER_SECOND));
            List<Edge> edges = new ArrayList<>(segments + 1);
            for (int i = 0; i <= segments; i++) {
                float u = (float) i / segments;
                float time = from + (to - from) * u;
                float remaining = Math.max(0, Math.min(1, 1 - (age - time) / LIFETIME));
                Matrix4f transform = tool.apply(time);
                edges.add(new Edge(transform.transformPosition(inner()), transform.transformPosition(outer()),
                        (float) poseWeight.applyAsDouble(time), remaining * remaining * (3 - 2 * remaining), u));
            }
            // Separate contact windows must never be bridged by a strip across the recovery pose.
            ribbons.add(new Ribbon(List.copyOf(edges)));
        }
        return List.copyOf(ribbons);
    }

    public record Edge(Vector3f inner, Vector3f outer, float weight, float alpha, float u) { }
    public record Ribbon(List<Edge> edges) { }
}
