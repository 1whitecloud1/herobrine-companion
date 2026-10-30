package com.whitecloud233.herobrine_companion.client.render;

import java.nio.ByteBuffer;
import org.joml.Matrix4fc;
import org.joml.Vector4f;

/** Conservative screen bounds for a buffered weapon, including near-plane crossings. */
public record PoemOutlineBounds(int x, int y, int width, int height) {
    public static PoemOutlineBounds project(ByteBuffer vertices, int count, int stride, Matrix4fc transform,
                                             int viewportX, int viewportY, int viewportWidth, int viewportHeight,
                                             int padding) {
        if (count == 0 || viewportWidth <= 0 || viewportHeight <= 0) return null;
        float minX = Float.POSITIVE_INFINITY, minY = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY, maxY = Float.NEGATIVE_INFINITY;
        int outside = 63;
        boolean crossesNear = false;
        Vector4f point = new Vector4f();
        for (int i = 0; i < count; i++) {
            int offset = vertices.position() + i * stride;
            point.set(vertices.getFloat(offset), vertices.getFloat(offset + 4), vertices.getFloat(offset + 8), 1).mul(transform);
            if (!point.isFinite()) return null;
            int clip = 0;
            if (point.x < -point.w) clip |= 1;
            if (point.x > point.w) clip |= 2;
            if (point.y < -point.w) clip |= 4;
            if (point.y > point.w) clip |= 8;
            if (point.z < -point.w) clip |= 16;
            if (point.z > point.w) clip |= 32;
            outside &= clip;
            if (point.w <= 0.00001f || point.z < -point.w) {
                crossesNear = true;
                continue;
            }
            float x = point.x / point.w, y = point.y / point.w;
            minX = Math.min(minX, x); maxX = Math.max(maxX, x);
            minY = Math.min(minY, y); maxY = Math.max(maxY, y);
        }
        // A shared near-plane bit means the weapon straddles the camera, not that it is offscreen.
        if ((outside & ~16) != 0) return null;
        int left = Math.max(0, viewportX), bottom = Math.max(0, viewportY);
        int right = viewportX + viewportWidth, top = viewportY + viewportHeight;
        // A clipped edge can cover more of the screen than either projected endpoint.
        if (!crossesNear) {
            left = Math.max(left, viewportX + (int) Math.floor((clamp(minX) + 1) * .5 * viewportWidth) - padding);
            bottom = Math.max(bottom, viewportY + (int) Math.floor((clamp(minY) + 1) * .5 * viewportHeight) - padding);
            right = Math.min(right, viewportX + (int) Math.ceil((clamp(maxX) + 1) * .5 * viewportWidth) + padding);
            top = Math.min(top, viewportY + (int) Math.ceil((clamp(maxY) + 1) * .5 * viewportHeight) + padding);
        }
        return right > left && top > bottom ? new PoemOutlineBounds(left, bottom, right - left, top - bottom) : null;
    }

    private static float clamp(float value) { return Math.max(-1, Math.min(1, value)); }
}
