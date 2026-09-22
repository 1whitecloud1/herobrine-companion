package com.whitecloud233.modid.herobrine_companion.client.animation;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.whitecloud233.modid.herobrine_companion.combat.poem.PoemMotionLibrary;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.client.renderer.block.model.ItemTransform;
import net.minecraft.world.item.ItemDisplayContext;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** The existing scythe mesh in the same model space as the GeckoLib item. */
public final class PoemItemMesh {
    private static final Setup SETUP = PoemMotionLibrary.read(PoemMotionLibrary.DATA_ROOT + "item_model.json", Setup.class);
    private static final List<Face> FACES = bake();

    private PoemItemMesh() { }

    private static List<Face> bake() {
        if (SETUP.format() != 1) throw new IllegalStateException("Invalid Poem item mesh setup");
        Mesh mesh = PoemMotionLibrary.read(PoemMotionLibrary.DATA_ROOT + "weapon.json", Mesh.class);
        Matrix4f toModel = PoemMotionLibrary.matrix(SETUP.mesh_to_model());
        List<Face> faces = new ArrayList<>(mesh.faces().length);
        for (int[] indices : mesh.faces()) {
            Vector3f[] points = new Vector3f[4]; float[][] uv = new float[4][2];
            for (int i = 0; i < 4; i++) {
                float[] raw = mesh.vertices()[indices[i]];
                points[i] = toModel.transformPosition(new Vector3f(raw[0], raw[1], raw[2]));
                uv[i][0] = mesh.uv()[indices[i]][0]; uv[i][1] = 1 - mesh.uv()[indices[i]][1];
            }
            Vector3f normal = new Vector3f(points[1]).sub(points[0]).cross(new Vector3f(points[2]).sub(points[0]));
            if (normal.lengthSquared() > 1e-14f) faces.add(new Face(points, uv, normal.normalize()));
        }
        return List.copyOf(faces);
    }

    public static void render(PoseStack stack, VertexConsumer buffer, int light, int overlay) {
        Vector3f point = new Vector3f(), normal = new Vector3f();
        for (Face face : FACES) {
            stack.last().normal().transform(face.normal(), normal).normalize();
            for (int i = 0; i < 4; i++) {
                stack.last().pose().transformPosition(face.points()[i], point);
                PoemSkinMesh.emit(buffer, point, normal, face.uv()[i][0], face.uv()[i][1], light, overlay, -1);
            }
        }
    }

    /** Forge's builtin/entity model has no display block, so its renderer applies this once. */
    public static ItemTransform display(ItemDisplayContext context) {
        Display display = SETUP.display().get(context.getSerializedName());
        if (display == null && context == ItemDisplayContext.FIRST_PERSON_LEFT_HAND) {
            display = SETUP.display().get(ItemDisplayContext.FIRST_PERSON_RIGHT_HAND.getSerializedName());
        }
        return display == null ? ItemTransform.NO_TRANSFORM : new ItemTransform(
                vector(display.rotation(), 0), vector(display.translation(), 0).mul(1f / 16), vector(display.scale(), 1));
    }

    private static Vector3f vector(float[] values, float fallback) {
        return values == null ? new Vector3f(fallback) : new Vector3f(values[0], values[1], values[2]);
    }

    private record Setup(int format, float[] mesh_to_model, Map<String, Display> display) { }
    private record Display(float[] rotation, float[] translation, float[] scale) { }
    private record Mesh(float[][] vertices, int[][] faces, float[][] uv) { }
    private record Face(Vector3f[] points, float[][] uv, Vector3f normal) { }
}
