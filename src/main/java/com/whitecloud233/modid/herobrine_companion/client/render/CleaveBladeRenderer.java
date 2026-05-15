package com.whitecloud233.modid.herobrine_companion.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.whitecloud233.modid.herobrine_companion.entity.projectile.CleaveBladeEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

public class CleaveBladeRenderer extends EntityRenderer<CleaveBladeEntity> {

    @SuppressWarnings("removal")
    private static final ResourceLocation TEXTURE = new ResourceLocation("herobrine_companion", "textures/entity/cleave_blade.png");

    public CleaveBladeRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public boolean shouldRender(CleaveBladeEntity entity, net.minecraft.client.renderer.culling.Frustum camera, double camX, double camY, double camZ) {
        return true;
    }

    @Override
    public void render(CleaveBladeEntity entity, float entityYaw, float partialTicks, PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        poseStack.pushPose();

        poseStack.translate(0.0D, -15.0D, 0.0D);

        float yaw = Mth.lerp(partialTicks, entity.yRotO, entity.getYRot());
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F - yaw));
        poseStack.mulPose(Axis.ZP.rotationDegrees(90.0F));
        // slashRollDegrees uses terrain-cut semantics: 0° = horizontal forward cut, ±90° = vertical cut.
        // The textured quad's neutral pose is vertical, so convert the roll before rendering.
        poseStack.mulPose(Axis.XP.rotationDegrees(90.0F - entity.getSlashRollDegrees()));

        // 强行赋予环境最高光照级别 (无视黑夜和阴影)
        int light = 15728880;
        // 使用支持自定义透明度的发光渲染通道
        VertexConsumer vertexconsumer = buffer.getBuffer(RenderType.entityTranslucentEmissive(this.getTextureLocation(entity)));
        float pulse = 1.0F + Mth.sin((entity.tickCount + partialTicks) * 1.6F) * 0.05F;

        poseStack.pushPose();
        poseStack.scale(240.0F * pulse, 10.0F, 1.0F);
        drawBladeFaces(poseStack, vertexconsumer, light, 1.0F);
        poseStack.popPose();

        poseStack.pushPose();
        poseStack.scale(205.0F * pulse, 34.0F, 1.0F);
        drawBladeFaces(poseStack, vertexconsumer, light, 0.78F);
        poseStack.popPose();

        poseStack.pushPose();
        poseStack.scale(285.0F * pulse, 70.0F, 1.0F);
        drawBladeFaces(poseStack, vertexconsumer, light, 0.30F);
        poseStack.popPose();

        poseStack.popPose();
        super.render(entity, entityYaw, partialTicks, poseStack, buffer, packedLight);
    }

    // 把画正反面的代码提出来，方便重复调用
    private void drawBladeFaces(PoseStack poseStack, VertexConsumer vertexconsumer, int light, float alpha) {
        PoseStack.Pose posestack$pose = poseStack.last();
        Matrix4f matrix4f = posestack$pose.pose();
        Matrix3f matrix3f = posestack$pose.normal();

        // 画正面
        vertex(vertexconsumer, matrix4f, matrix3f, -0.5F, -0.5F, 0.0F, 0.0F, 1.0F, light, alpha);
        vertex(vertexconsumer, matrix4f, matrix3f,  0.5F, -0.5F, 0.0F, 1.0F, 1.0F, light, alpha);
        vertex(vertexconsumer, matrix4f, matrix3f,  0.5F,  0.5F, 0.0F, 1.0F, 0.0F, light, alpha);
        vertex(vertexconsumer, matrix4f, matrix3f, -0.5F,  0.5F, 0.0F, 0.0F, 0.0F, light, alpha);

        // 画背面
        vertex(vertexconsumer, matrix4f, matrix3f, -0.5F,  0.5F, 0.0F, 0.0F, 0.0F, light, alpha);
        vertex(vertexconsumer, matrix4f, matrix3f,  0.5F,  0.5F, 0.0F, 1.0F, 0.0F, light, alpha);
        vertex(vertexconsumer, matrix4f, matrix3f,  0.5F, -0.5F, 0.0F, 1.0F, 1.0F, light, alpha);
        vertex(vertexconsumer, matrix4f, matrix3f, -0.5F, -0.5F, 0.0F, 0.0F, 1.0F, light, alpha);
    }

    // 修改了 vertex 方法，允许传入 alpha (透明度)
    private void vertex(VertexConsumer consumer, Matrix4f matrix4f, Matrix3f matrix3f, float x, float y, float z, float u, float v, int light, float alpha) {
        consumer.vertex(matrix4f, x, y, z)
                // 这里的颜色为纯白，最后一位控制透明度
                .color(1.0F, 1.0F, 1.0F, alpha)
                .uv(u, v)
                .overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(light)
                .normal(matrix3f, 0.0F, 1.0F, 0.0F)
                .endVertex();
    }

    @Override
    public ResourceLocation getTextureLocation(CleaveBladeEntity entity) {
        return TEXTURE;
    }
}