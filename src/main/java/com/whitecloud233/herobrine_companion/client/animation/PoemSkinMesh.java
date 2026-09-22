package com.whitecloud233.herobrine_companion.client.animation;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.whitecloud233.herobrine_companion.combat.poem.PoemMotionLibrary;
import com.whitecloud233.herobrine_companion.combat.poem.PoemBladeTrail;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Subdivides the real model's cubes, preserving skin UVs, slim arms and armor inflation. */
public final class PoemSkinMesh {
    private static final Map<ModelPart, PoemSkinMesh> CACHE = new WeakHashMap<>();
    public static final Matrix4f DCC_TO_MODEL = PoemBladeTrail.toModel();
    public static final Matrix4f MODEL_TO_DCC = new Matrix4f(DCC_TO_MODEL).invert();
    public final List<Face> faces = new ArrayList<>();
    public final Matrix4f initial;
    private final Matrix4f inverseInitial;
    private final int upper, lower;
    private final float hinge;

    public enum Part {
        HEAD("Head", "Head"), BODY("Chest", "Torso"),
        RIGHT_ARM("Arm_R", "Hand_R"), LEFT_ARM("Arm_L", "Hand_L"),
        RIGHT_LEG("Thigh_R", "Leg_R"), LEFT_LEG("Thigh_L", "Leg_L");
        final String upper, lower;
        Part(String upper, String lower) { this.upper = upper; this.lower = lower; }
    }

    public static PoemSkinMesh of(ModelPart part, Part type) {
        return CACHE.computeIfAbsent(part, ignored -> new PoemSkinMesh(part, type));
    }

    private PoemSkinMesh(ModelPart part, Part type) {
        PoemMotionLibrary library = PoemMotionLibrary.get();
        upper = library.joint(type.upper); lower = library.joint(type.lower);
        hinge = library.bind(type == Part.BODY ? upper : lower).m32();
        initial = initial(part); inverseInitial = new Matrix4f(initial).invert();
        Capture capture = new Capture();
        part.visit(new PoseStack(), (pose, path, index, cube) -> {
            ModelPart owner = part;
            List<WeakReference<ModelPart>> ancestry = new ArrayList<>(); ancestry.add(new WeakReference<>(part));
            Matrix4f rest = new Matrix4f(initial);
            if (!path.isEmpty()) for (String child : path.substring(1).split("/")) {
                owner = owner.getChild(child); ancestry.add(new WeakReference<>(owner)); rest.mul(initial(owner));
            }
            capture.vertices.clear();
            cube.compile(new PoseStack().last(), capture, 0, 0, -1);
            for (int i = 0; i < capture.vertices.size(); i += 4) {
                List<Vertex> polygon = new ArrayList<>();
                for (int k = 0; k < 4; k++) {
                    Vertex v = capture.vertices.get(i + k);
                    polygon.add(new Vertex(rest.transformPosition(v.point, new Vector3f()), v.u, v.v));
                }
                List<List<Vertex>> pieces = new ArrayList<>(); pieces.add(polygon);
                if (upper != lower) {
                    for (float y : new float[]{1.5f - hinge - .018f, 1.5f - hinge, 1.5f - hinge + .018f}) {
                        List<List<Vertex>> next = new ArrayList<>();
                        for (List<Vertex> piece : pieces) {
                            List<Vertex> above = clip(piece, y, true), below = clip(piece, y, false);
                            if (above.size() >= 3) next.add(above);
                            if (below.size() >= 3) next.add(below);
                        }
                        pieces = next;
                    }
                }
                for (List<Vertex> piece : pieces) {
                    if (piece.size() == 4) faces.add(new Face(piece.toArray(Vertex[]::new), List.copyOf(ancestry)));
                    else for (int k = 1; k < piece.size() - 1; k++) {
                        faces.add(new Face(new Vertex[]{piece.get(0), piece.get(k), piece.get(k + 1), piece.get(k + 1)}, List.copyOf(ancestry)));
                    }
                }
            }
        });
    }

    public static Matrix4f initial(ModelPart part) {
        PartPose p = part.getInitialPose();
        return new Matrix4f().translation(p.x / 16, p.y / 16, p.z / 16)
                .rotate(new Quaternionf().rotationZYX(p.zRot, p.yRot, p.xRot));
    }

    public static Matrix4f current(ModelPart part) {
        return new Matrix4f().translation(part.x / 16, part.y / 16, part.z / 16)
                .rotate(new Quaternionf().rotationZYX(part.zRot, part.yRot, part.xRot)).scale(part.xScale, part.yScale, part.zScale);
    }

    private static List<Vertex> clip(List<Vertex> polygon, float y, boolean above) {
        // Coplanar faces belong to one side only, otherwise a seam duplicates a surface.
        float min = Float.POSITIVE_INFINITY, max = Float.NEGATIVE_INFINITY;
        for (Vertex v : polygon) { min = Math.min(min, v.point.y); max = Math.max(max, v.point.y); }
        if (Math.abs(max - min) < 1e-7f && Math.abs(min - y) < 1e-7f) return above ? polygon : List.of();
        List<Vertex> result = new ArrayList<>();
        Vertex a = polygon.get(polygon.size() - 1);
        boolean insideA = above ? a.point.y <= y : a.point.y >= y;
        for (Vertex b : polygon) {
            boolean insideB = above ? b.point.y <= y : b.point.y >= y;
            if (insideA != insideB) {
                float t = (y - a.point.y) / (b.point.y - a.point.y);
                result.add(new Vertex(new Vector3f(a.point).lerp(b.point, t), a.u + (b.u - a.u) * t, a.v + (b.v - a.v) * t));
            }
            if (insideB) result.add(b);
            a = b; insideA = insideB;
        }
        // Clipping through an existing vertex can repeat it; keep a usable polygon.
        for (int i = result.size() - 1; i >= 0 && result.size() > 1; i--) {
            if (result.get(i).point.distanceSquared(result.get((i + 1) % result.size()).point) < 1e-12f) result.remove(i);
        }
        return result;
    }

    public Vector3f position(Vertex vertex, Matrix4f[] skin, Matrix4f vanilla, float weight) {
        Vector3f dcc = MODEL_TO_DCC.transformPosition(vertex.point, new Vector3f());
        float lowerWeight = upper == lower ? 0 : Math.max(0, Math.min(1, (hinge + .018f - dcc.z) / .036f));
        Vector3f animated = skin[upper].transformPosition(dcc, new Vector3f());
        if (lowerWeight > 0) animated.lerp(skin[lower].transformPosition(dcc, new Vector3f()), lowerWeight);
        DCC_TO_MODEL.transformPosition(animated);
        if (weight < 1) {
            Vector3f rest = inverseInitial.transformPosition(vertex.point, new Vector3f());
            vanilla.transformPosition(rest);
            return rest.lerp(animated, weight);
        }
        return animated;
    }

    public void render(ModelPart part, Matrix4f[] skin, float weight, PoseStack stack, VertexConsumer buffer,
                       int light, int overlay, int color) {
        Matrix4f vanilla = current(part);
        for (Face face : faces) {
            if (!face.visible()) continue;
            Vector3f[] points = new Vector3f[4];
            for (int i = 0; i < 4; i++) points[i] = position(face.vertices[i], skin, vanilla, weight);
            Vector3f normal = new Vector3f(points[1]).sub(points[0]).cross(new Vector3f(points[2]).sub(points[0]));
            if (normal.lengthSquared() < 1e-14f) continue;
            normal.normalize(); stack.last().normal().transform(normal).normalize();
            for (int i = 0; i < 4; i++) {
                Vector3f p = stack.last().pose().transformPosition(points[i]);
                emit(buffer, p, normal, face.vertices[i].u, face.vertices[i].v, light, overlay, color);
            }
        }
    }

    public static void emit(VertexConsumer buffer, Vector3f p, Vector3f normal, float u, float v, int light, int overlay, int color) {
        buffer.addVertex(p.x, p.y, p.z, color, u, v, overlay, light, normal.x, normal.y, normal.z);
    }

    public record Vertex(Vector3f point, float u, float v) { }
    public record Face(Vertex[] vertices, List<WeakReference<ModelPart>> ancestry) {
        boolean visible() {
            for (WeakReference<ModelPart> reference : ancestry) {
                ModelPart part = reference.get();
                if (part == null || !part.visible) return false;
            }
            ModelPart owner = ancestry.get(ancestry.size() - 1).get();
            return owner != null && !owner.skipDraw;
        }
    }

    /** Cube.compile is public in both versions; no private polygon accessors or reflection. */
    private static final class Capture implements VertexConsumer {
        final List<Vertex> vertices = new ArrayList<>();
        Vector3f point;
        float u, v;
        @Override public VertexConsumer addVertex(float x, float y, float z) { point = new Vector3f(x, y, z); return this; }
        @Override public VertexConsumer setColor(int r, int g, int b, int a) { return this; }
        @Override public VertexConsumer setUv(float u, float v) { this.u = u; this.v = v; return this; }
        @Override public VertexConsumer setUv1(int u, int v) { return this; }
        @Override public VertexConsumer setUv2(int u, int v) { return this; }
        @Override public VertexConsumer setNormal(float x, float y, float z) { vertices.add(new Vertex(point, u, v)); return this; }
    }
}
