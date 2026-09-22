package com.whitecloud233.modid.herobrine_companion.client.render;

import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector4f;

/** Camera projection and lifetime envelope shared by the rift renderer and its verifier. */
public final class VoidRiftProjection {
    public static final int MAX_RIFTS = 8;
    public static final float HALF_LENGTH = 1.65F;
    public static final float SUPPORT_RADIUS = 1.6F;

    private VoidRiftProjection() { }

    /** Radius is measured in screen heights, so aspect ratio does not stretch the lens. */
    public record Projected(float u, float v, float radius, float angle, float depth,
                            float strength, float seed, float seconds) { }

    public static float envelope(float age, float lifetime) {
        if (!Float.isFinite(age) || !Float.isFinite(lifetime) || lifetime <= 0) return 0;
        return smooth(age / 2.0F) * smooth((lifetime - age) / 6.0F);
    }

    private static float smooth(float x) {
        x = Math.max(0, Math.min(1, x));
        return x * x * (3 - 2 * x);
    }

    /** viewPose includes the entity's billboard transform, but not the scene projection. */
    public static Projected project(Matrix4fc projection, Matrix4fc viewPose, int width, int height,
                                    float age, float lifetime, float seed) {
        if (width <= 0 || height <= 0 || !Float.isFinite(seed)) return null;
        float strength = envelope(age, lifetime);
        if (strength <= 0) return null;
        Vector4f viewCenter = viewPose.transform(new Vector4f(0, 0, 0, 1));
        float depth = -viewCenter.z / viewCenter.w;
        if (!viewCenter.isFinite() || !Float.isFinite(depth) || depth <= 0.05F) return null;
        Matrix4f clip = new Matrix4f(projection).mul(viewPose);
        Vector4f center = clip.transform(new Vector4f(0, 0, 0, 1));
        Vector4f top = clip.transform(new Vector4f(0, HALF_LENGTH, 0, 1));
        if (!center.isFinite() || !top.isFinite() || center.w <= 0.0001F || top.w <= 0.0001F) return null;
        float ndcDepth = center.z / center.w;
        if (ndcDepth <= -1 || ndcDepth >= 1) return null;
        float u = center.x / center.w * 0.5F + 0.5F;
        float v = center.y / center.w * 0.5F + 0.5F;
        float aspect = (float) width / height;
        float dx = (top.x / top.w * 0.5F + 0.5F - u) * aspect;
        float dy = top.y / top.w * 0.5F + 0.5F - v;
        float radius = (float) Math.hypot(dx, dy);
        if (!Float.isFinite(radius) || radius * height < 0.75F) return null;
        // Walking through a short-lived rift must not turn the whole display into a black plane.
        radius = Math.min(radius, 0.42F);
        strength *= smooth((depth - 0.05F) / 0.30F);
        if (strength <= 0) return null;
        float reach = radius * SUPPORT_RADIUS;
        if (u + reach / aspect < 0 || u - reach / aspect > 1 || v + reach < 0 || v - reach > 1) return null;
        float angle = (float) Math.atan2(dy, dx) - (float) Math.PI / 2;
        return new Projected(u, v, radius, angle, depth, strength, seed, age / 20.0F);
    }
}
