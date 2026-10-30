package com.whitecloud233.herobrine_companion.client.render;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import org.joml.Matrix4f;

/** Screen-bounds contracts for the weapon outline scissor, with no GPU or game window. */
public final class PoemOutlineBoundsChecks {
    private static int checks;

    public static void main(String[] args) {
        // Two triangles of a 0.5 x 0.5 screen-space square centered in the viewport.
        // 0.25 is exact in binary floating point, so floor/ceil stay deterministic.
        ByteBuffer square = vertices(
                -0.25f, -0.25f, 0.5f, 0.25f, -0.25f, 0.5f, 0.25f, 0.25f, 0.5f,
                -0.25f, -0.25f, 0.5f, 0.25f, 0.25f, 0.5f, -0.25f, 0.25f, 0.5f);
        Matrix4f identity = new Matrix4f();
        PoemOutlineBounds bounds = PoemOutlineBounds.project(square, 6, 12, identity,
                0, 0, 100, 100, 2);
        check("centered square bounds", bounds != null && bounds.x() == 35 && bounds.y() == 35
                && bounds.width() == 30 && bounds.height() == 30);

        // A weapon clipped off the left edge is skipped entirely: no fullscreen work, no scissor.
        ByteBuffer offscreen = vertices(
                -3f, -0.2f, 0.5f, -2f, -0.2f, 0.5f, -2f, 0.2f, 0.5f,
                -3f, -0.2f, 0.5f, -2f, 0.2f, 0.5f, -3f, 0.2f, 0.5f);
        check("fully offscreen skipped", PoemOutlineBounds.project(offscreen, 6, 12, identity,
                0, 0, 100, 100, 2) == null);

        // Behind the camera: projection still yields a conservative fullscreen scissor.
        ByteBuffer near = vertices(
                -0.25f, -0.25f, -2f, 0.25f, -0.25f, -2f, 0.25f, 0.25f, -2f,
                -0.25f, -0.25f, -2f, 0.25f, 0.25f, -2f, -0.25f, 0.25f, -2f);
        bounds = PoemOutlineBounds.project(near, 6, 12, identity, 0, 0, 100, 100, 2);
        check("near-plane clipped becomes fullscreen", bounds != null
                && bounds.x() == 0 && bounds.y() == 0 && bounds.width() == 100 && bounds.height() == 100);

        // Viewport offsets keep bounds in window coordinates even when the viewport is a subregion.
        bounds = PoemOutlineBounds.project(square, 6, 12, identity, 50, 25, 100, 100, 2);
        check("viewport offset applied", bounds != null && bounds.x() == 85 && bounds.y() == 60
                && bounds.width() == 30 && bounds.height() == 30);

        // A tiny or empty buffer never reaches the GPU path.
        check("empty buffer skipped", PoemOutlineBounds.project(square, 0, 12, identity,
                0, 0, 100, 100, 2) == null);
        System.out.println("PoemOutlineBoundsChecks: " + checks + " checks passed");
    }

    private static ByteBuffer vertices(float... values) {
        ByteBuffer buffer = ByteBuffer.allocateDirect(values.length * 4).order(ByteOrder.nativeOrder());
        for (float value : values) buffer.putFloat(value);
        buffer.flip();
        return buffer;
    }

    private static void check(String label, boolean condition) { if (!condition) throw new AssertionError(label); checks++; }
}
