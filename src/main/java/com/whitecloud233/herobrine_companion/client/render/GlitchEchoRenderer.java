package com.whitecloud233.herobrine_companion.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.whitecloud233.herobrine_companion.HerobrineCompanion;
import com.whitecloud233.herobrine_companion.entity.GlitchEchoEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import org.joml.Matrix4f;

public class GlitchEchoRenderer extends EntityRenderer<GlitchEchoEntity> {

    // [关键] 指向新的抽象核心贴图
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(HerobrineCompanion.MODID, "textures/entity/glitch_echo_core.png");
    private final RandomSource random = RandomSource.create();

    public GlitchEchoRenderer(EntityRendererProvider.Context context) {
        super(context);
        // 数据回响是一个发光体，不需要实体阴影
        this.shadowRadius = 0.0f;
    }

    @Override
    public ResourceLocation getTextureLocation(GlitchEchoEntity entity) {
        return TEXTURE;
    }

    @Override
    public void render(GlitchEchoEntity entity, float entityYaw, float partialTick, PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        // 不调用 super.render，因为我们不需要原版的命名牌等渲染

        poseStack.pushPose();
        float time = entity.tickCount + partialTick;

        // ---------------------------------------------------------
        // 1. 基础位移与抖动 (位置控制)
        // ---------------------------------------------------------
        // 将中心点抬高到实体中间 (假设实体高 0.3，抬高 0.15)
        poseStack.translate(0.0, 0.15, 0.0);

        // 强烈的随机抖动，模拟不稳定数据
        float jitterScale = 0.05f; // 抖动范围
        poseStack.translate(
            (random.nextFloat() - 0.5f) * jitterScale,
            (random.nextFloat() - 0.5f) * jitterScale + Mth.sin(time * 0.1f) * 0.05f, // 加上缓慢的上下浮动
            (random.nextFloat() - 0.5f) * jitterScale
        );

        // ---------------------------------------------------------
        // 2. 混沌旋转 (形态控制)
        // ---------------------------------------------------------
        // 让方块在三个轴上疯狂自转，看起来像失控的核心
        poseStack.mulPose(Axis.YP.rotationDegrees(time * 15.0f + random.nextFloat() * 10.0f));
        poseStack.mulPose(Axis.XP.rotationDegrees(time * 20.0f + random.nextFloat() * 10.0f));
        poseStack.mulPose(Axis.ZP.rotationDegrees(time * 10.0f));

        // ---------------------------------------------------------
        // 3. 脉冲缩放 (大小控制)
        // ---------------------------------------------------------
        // 基础大小：适应 0.3 的碰撞箱，方块大概 0.25 大小
        float baseScale = 0.25f;
        // 呼吸效果：在 0.8倍 到 1.2倍 之间波动
        float pulse = 1.0f + Mth.sin(time * 0.2f) * 0.2f;

        // 偶尔出现的“故障尖刺”放大效果
        if (random.nextFloat() < 0.03f) {
            pulse *= (1.5f + random.nextFloat() * 0.5f);
        }
        poseStack.scale(baseScale * pulse, baseScale * pulse, baseScale * pulse);

        // ---------------------------------------------------------
        // 4. 绘制几何体 (核心实现)
        // ---------------------------------------------------------
        // 使用 entityTranslucentEmissive：半透明 + 自发光 (无视环境光变黑)
        VertexConsumer consumer = buffer.getBuffer(RenderType.entityTranslucentEmissive(this.getTextureLocation(entity)));
        PoseStack.Pose lastPose = poseStack.last();
        Matrix4f poseMatrix = lastPose.pose();

        // 绘制一个中心在 (0,0,0)，边长为 1 的标准立方体
        drawCube(poseMatrix, lastPose, consumer, packedLight);

        poseStack.popPose();
    }

    /**
     * 辅助方法：绘制一个简单的立方体
     */
    private void drawCube(Matrix4f pose, PoseStack.Pose lastPose, VertexConsumer consumer, int light) {
        float s = 0.5f; // 半边长

        // 前面 (Z+)
        vertex(pose, lastPose, consumer, -s, -s, s, 0, 1, light, 0, 0, 1);
        vertex(pose, lastPose, consumer, s, -s, s, 1, 1, light, 0, 0, 1);
        vertex(pose, lastPose, consumer, s, s, s, 1, 0, light, 0, 0, 1);
        vertex(pose, lastPose, consumer, -s, s, s, 0, 0, light, 0, 0, 1);
        // 后面 (Z-)
        vertex(pose, lastPose, consumer, s, -s, -s, 0, 1, light, 0, 0, -1);
        vertex(pose, lastPose, consumer, -s, -s, -s, 1, 1, light, 0, 0, -1);
        vertex(pose, lastPose, consumer, -s, s, -s, 1, 0, light, 0, 0, -1);
        vertex(pose, lastPose, consumer, s, s, -s, 0, 0, light, 0, 0, -1);
        // 上面 (Y+)
        vertex(pose, lastPose, consumer, -s, s, -s, 0, 1, light, 0, 1, 0);
        vertex(pose, lastPose, consumer, -s, s, s, 0, 0, light, 0, 1, 0);
        vertex(pose, lastPose, consumer, s, s, s, 1, 0, light, 0, 1, 0);
        vertex(pose, lastPose, consumer, s, s, -s, 1, 1, light, 0, 1, 0);
        // 下面 (Y-)
        vertex(pose, lastPose, consumer, -s, -s, s, 0, 0, light, 0, -1, 0);
        vertex(pose, lastPose, consumer, -s, -s, -s, 0, 1, light, 0, -1, 0);
        vertex(pose, lastPose, consumer, s, -s, -s, 1, 1, light, 0, -1, 0);
        vertex(pose, lastPose, consumer, s, -s, s, 1, 0, light, 0, -1, 0);
        // 右面 (X+)
        vertex(pose, lastPose, consumer, s, -s, s, 0, 1, light, 1, 0, 0);
        vertex(pose, lastPose, consumer, s, -s, -s, 1, 1, light, 1, 0, 0);
        vertex(pose, lastPose, consumer, s, s, -s, 1, 0, light, 1, 0, 0);
        vertex(pose, lastPose, consumer, s, s, s, 0, 0, light, 1, 0, 0);
        // 左面 (X-)
        vertex(pose, lastPose, consumer, -s, -s, -s, 0, 1, light, -1, 0, 0);
        vertex(pose, lastPose, consumer, -s, -s, s, 1, 1, light, -1, 0, 0);
        vertex(pose, lastPose, consumer, -s, s, s, 1, 0, light, -1, 0, 0);
        vertex(pose, lastPose, consumer, -s, s, -s, 0, 0, light, -1, 0, 0);
    }

    /**
     * 辅助方法：构建单个顶点
     */
    private void vertex(Matrix4f pose, PoseStack.Pose lastPose, VertexConsumer consumer, float x, float y, float z, float u, float v, int light, float nx, float ny, float nz) {
        // 设置为白色带透明度 (Alpha=200)，配合 Emissive 材质实现发光半透明效果
        consumer.addVertex(pose, x, y, z)
                .setColor(255, 255, 255, 200)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light) // Emissive 会忽略这个光照值，自发光
                .setNormal(lastPose, nx, ny, nz);
    }

    // 确保实体不被错误剔除
    @Override
    public boolean shouldRender(GlitchEchoEntity livingEntity, net.minecraft.client.renderer.culling.Frustum camera, double camX, double camY, double camZ) {
        return true;
    }
}
