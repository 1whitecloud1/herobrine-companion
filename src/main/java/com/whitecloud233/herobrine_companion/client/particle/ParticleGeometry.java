package com.whitecloud233.herobrine_companion.client.particle;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Shared mesh primitives. Camera subtraction, winding and float math match the original particles. */
public final class ParticleGeometry {
    private ParticleGeometry() { }

    public static void vertex(VertexConsumer vertex, Matrix4f matrix, Vec3 pos, ParticleColor color) {
        vertex.addVertex(matrix, (float) pos.x, (float) pos.y, (float) pos.z)
                .setColor(color.r(), color.g(), color.b(), color.a());
    }

    /** Arc historically submits camera-relative vertices without applying this matrix. */
    public static void vertexUntransformed(VertexConsumer vertex, Matrix4f matrix, Vec3 pos, ParticleColor color) {
        vertex.addVertex((float) pos.x, (float) pos.y, (float) pos.z)
                .setColor(color.r(), color.g(), color.b(), color.a());
    }

    /** Keep the Pillar's existing float-to-integer color conversion. */
    public static void vertexIntColor(VertexConsumer vertex, Matrix4f matrix, Vec3 pos, ParticleColor color) {
        vertex.addVertex(matrix, (float) pos.x, (float) pos.y, (float) pos.z)
                .setColor((int) (color.r() * 255), (int) (color.g() * 255),
                        (int) (color.b() * 255), (int) (color.a() * 255));
    }

    public static List<Vec3> ring(Vec3 center, Vec3 axis, int segments, float radius) {
        List<Vec3> ring = new ArrayList<>();
        Vector3f axis3f = vector(axis).normalize();
        Vector3f u = Math.abs(axis3f.y) < .999F
                ? axis3f.cross(new Vector3f(0F, 1F, 0F), new Vector3f()).normalize()
                : axis3f.cross(new Vector3f(1F, 0F, 0F), new Vector3f()).normalize();
        Vector3f v = axis3f.cross(u, new Vector3f()).normalize();
        for (int i = 0; i < segments; i++) {
            float angle = (float) i / segments * (float) Math.PI * 2F;
            Vector3f pos = vector(center);
            pos.add(u.mul((float) Math.cos(angle) * radius, new Vector3f()));
            pos.add(v.mul((float) Math.sin(angle) * radius, new Vector3f()));
            ring.add(new Vec3(pos.x, pos.y, pos.z));
        }
        return ring;
    }

    public static void cylinder(Matrix4f matrix, VertexConsumer vertex, Vec3 camera, List<Vec3> path,
                                int sides, float radius, ParticleColor color) {
        drawSegments(matrix, vertex, camera, path, sides, radius, radius, color, 1F, false, ParticleGeometry::vertex);
    }

    public static void cylinderUntransformed(Matrix4f matrix, VertexConsumer vertex, Vec3 camera, List<Vec3> path,
                                             int sides, float radius, ParticleColor color) {
        drawSegments(matrix, vertex, camera, path, sides, radius, radius, color, 1F, false, ParticleGeometry::vertexUntransformed);
    }

    /** Pillar policy: partial growth, tapered radii and collapse to the exact center for tiny tips. */
    public static void taperedCylinder(Matrix4f matrix, VertexConsumer vertex, Vec3 camera, List<Vec3> path,
                                       int sides, float baseRadius, float tipRadius, ParticleColor color, float progress) {
        drawSegments(matrix, vertex, camera, path, sides, baseRadius, tipRadius, color, progress, true, ParticleGeometry::vertex);
    }

    public static void taperedCylinderIntColor(Matrix4f matrix, VertexConsumer vertex, Vec3 camera, List<Vec3> path,
                                               int sides, float baseRadius, float tipRadius, ParticleColor color, float progress) {
        drawSegments(matrix, vertex, camera, path, sides, baseRadius, tipRadius, color, progress, true, ParticleGeometry::vertexIntColor);
    }

    @FunctionalInterface
    private interface VertexSink {
        void submit(VertexConsumer vertex, Matrix4f matrix, Vec3 pos, ParticleColor color);
    }

    private static void drawSegments(Matrix4f matrix, VertexConsumer vertex, Vec3 camera, List<Vec3> path,
                                     int sides, float baseRadius, float tipRadius, ParticleColor color,
                                     float progress, boolean collapseTinyRadius, VertexSink sink) {
        int total = path.size() - 1;
        if (total <= 0) return;
        int count = Math.min((int) Math.max(1, total * progress), total);
        for (int i = 0; i < count; i++) {
            Vec3 p1 = path.get(i).subtract(camera), p2 = path.get(i + 1).subtract(camera);
            Vec3 axis = p2.subtract(p1);
            if (axis.lengthSqr() < .001D) continue;
            float r1 = baseRadius + (tipRadius - baseRadius) * ((float) i / total);
            float r2 = baseRadius + (tipRadius - baseRadius) * ((float) (i + 1) / total);
            List<Vec3> a = collapseTinyRadius && r1 <= .001F ? Collections.nCopies(sides, p1) : ring(p1, axis, sides, r1);
            List<Vec3> b = collapseTinyRadius && r2 <= .001F ? Collections.nCopies(sides, p2) : ring(p2, axis, sides, r2);
            for (int j = 0; j < sides; j++) {
                sink.submit(vertex, matrix, a.get(j), color);
                sink.submit(vertex, matrix, a.get((j + 1) % sides), color);
                sink.submit(vertex, matrix, b.get((j + 1) % sides), color);
                sink.submit(vertex, matrix, b.get(j), color);
            }
        }
    }

    private static Vector3f vector(Vec3 vec) { return new Vector3f((float) vec.x, (float) vec.y, (float) vec.z); }
}
