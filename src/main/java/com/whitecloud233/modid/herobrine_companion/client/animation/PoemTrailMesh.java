package com.whitecloud233.modid.herobrine_companion.client.animation;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.whitecloud233.modid.herobrine_companion.client.animation.StandalonePoemAnimation.Frame;
import com.whitecloud233.modid.herobrine_companion.combat.poem.PoemBladeTrail;
import com.whitecloud233.modid.herobrine_companion.combat.poem.PoemMotionLibrary;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.LightTexture;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** Emits the same blade geometry for both views, without creating GPU render state. */
public final class PoemTrailMesh {
    /**
     * {截面放大倍数, 该层 alpha 系数, 贴图 V 覆盖上限}。
     *
     * <p>贴图换成基岩版终末之诗同款电弧 ramp 后，V=0 是白刃、V=1 已完全透明，
     * 不再是旧贴图那种"上下边缘淡、中间实"的对称结构。泛光层如果仍按旧习惯让
     * 截面顶点固定取 V=1，外扩出去的部分就全部落在透明区，等于白画一层。
     * 因此每层额外给一个 V 上限：核心层只吃到亮刃与青肩，泛光层吃得更深，
     * 靠"更宽更淡的尾部"而不是靠透明区来撑出柔光。</p>
     */
    private static final float[][] GLOW_PASSES = {{1.38f, .55f, .82f}, {1.08f, 1f, .58f}};
    private static final float[][] BODY_PASSES = {{1.14f, .82f, .64f}};

    private PoemTrailMesh() { }

    public static void render(Frame frame, HumanoidModel<?> model, PoseStack stack, VertexConsumer buffer, float scroll) {
        render(frame, model, stack, buffer, GLOW_PASSES, scroll);
    }

    /** Alpha-blended cyan body retains contrast against daylight and pale blocks. */
    public static void renderBody(Frame frame, HumanoidModel<?> model, PoseStack stack, VertexConsumer buffer, float scroll) {
        render(frame, model, stack, buffer, BODY_PASSES, scroll);
    }

    private static void render(Frame frame, HumanoidModel<?> model, PoseStack stack, VertexConsumer buffer,
                               float[][] passes, float scroll) {
        if (frame.trail().isEmpty()) return;
        Matrix4f toModel = new Matrix4f(PoemSkinMesh.DCC_TO_MODEL);
        if (frame.leftHanded()) toModel.scale(-1, 1, 1);
        ModelPart arm = frame.leftHanded() ? model.leftArm : model.rightArm;
        Matrix4f neutral = PoemSkinMesh.current(arm).mul(PoemSkinMesh.initial(arm).invert())
                .mul(toModel).mul(PoemMotionLibrary.get().bind(PoemMotionLibrary.get().joint("Tool_R")));
        Vector3f neutralInner = neutral.transformPosition(PoemBladeTrail.inner());
        Vector3f neutralOuter = neutral.transformPosition(PoemBladeTrail.outer());
        Matrix4f pose = stack.last().pose();
        for (PoemBladeTrail.Ribbon ribbon : frame.trail()) {
            int count = ribbon.edges().size();
            Vector3f[] inner = new Vector3f[count], outer = new Vector3f[count];
            for (int i = 0; i < count; i++) {
                PoemBladeTrail.Edge edge = ribbon.edges().get(i);
                inner[i] = new Vector3f(neutralInner).lerp(toModel.transformPosition(edge.inner(), new Vector3f()), edge.weight());
                outer[i] = new Vector3f(neutralOuter).lerp(toModel.transformPosition(edge.outer(), new Vector3f()), edge.weight());
            }
            for (float[] pass : passes) {
                float vSpan = pass.length > 2 ? pass[2] : 1f;
                for (int i = 0; i < count - 1; i++) {
                    PoemBladeTrail.Edge a = ribbon.edges().get(i), b = ribbon.edges().get(i + 1);
                    // U 整体偏移一段，电弧沿刀身流动；整条 ribbon 共用同一个偏移，段内不会错缝
                    vertex(buffer, pose, inner[i], outer[i], pass[0], a.u() + scroll, vSpan, a.alpha() * pass[1]);
                    vertex(buffer, pose, outer[i], inner[i], pass[0], a.u() + scroll, 0, a.alpha() * pass[1]);
                    vertex(buffer, pose, outer[i + 1], inner[i + 1], pass[0], b.u() + scroll, 0, b.alpha() * pass[1]);
                    vertex(buffer, pose, inner[i + 1], outer[i + 1], pass[0], b.u() + scroll, vSpan, b.alpha() * pass[1]);
                }
            }
        }
    }

    private static void vertex(VertexConsumer buffer, Matrix4f pose, Vector3f point, Vector3f opposite,
                               float width, float u, float v, float alpha) {
        Vector3f p = new Vector3f(point).sub(opposite).mul((width - 1) * .5f).add(point);
        pose.transformPosition(p);
        buffer.vertex(p.x, p.y, p.z).color(1f, 1f, 1f, alpha).uv(u, v).uv2(LightTexture.FULL_BRIGHT).endVertex();
    }
}
