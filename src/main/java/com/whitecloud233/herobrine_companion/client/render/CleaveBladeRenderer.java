package com.whitecloud233.herobrine_companion.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.whitecloud233.herobrine_companion.entity.projectile.CleaveBladeEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

public class CleaveBladeRenderer extends EntityRenderer<CleaveBladeEntity> {

    // 1.21.1 更新：使用 fromNamespaceAndPath 替代已废弃的 new ResourceLocation
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath("herobrine_companion", "textures/entity/cleave_blade.png");

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

    private void drawBladeFaces(PoseStack poseStack, VertexConsumer vertexconsumer, int light, float alpha) {
        // 1.21.1 更新：直接传递 PoseStack.Pose 更加方便
        PoseStack.Pose pose = poseStack.last();

        // 画正面
        vertex(vertexconsumer, pose, -0.5F, -0.5F, 0.0F, 0.0F, 1.0F, light, alpha);
        vertex(vertexconsumer, pose,  0.5F, -0.5F, 0.0F, 1.0F, 1.0F, light, alpha);
        vertex(vertexconsumer, pose,  0.5F,  0.5F, 0.0F, 1.0F, 0.0F, light, alpha);
        vertex(vertexconsumer, pose, -0.5F,  0.5F, 0.0F, 0.0F, 0.0F, light, alpha);

        // 画背面
        vertex(vertexconsumer, pose, -0.5F,  0.5F, 0.0F, 0.0F, 0.0F, light, alpha);
        vertex(vertexconsumer, pose,  0.5F,  0.5F, 0.0F, 1.0F, 0.0F, light, alpha);
        vertex(vertexconsumer, pose,  0.5F, -0.5F, 0.0F, 1.0F, 1.0F, light, alpha);
        vertex(vertexconsumer, pose, -0.5F, -0.5F, 0.0F, 0.0F, 1.0F, light, alpha);
    }

    private void vertex(VertexConsumer consumer, PoseStack.Pose pose, float x, float y, float z, float u, float v, int light, float alpha) {
        // 转换透明度为 0-255 的整数值
        int alphaInt = (int)(alpha * 255.0F);

        // 1.21.1 更新：方法名全面更换为 setXxx，并且不再需要 endVertex()
        consumer.addVertex(pose.pose(), x, y, z)
                .setColor(255, 255, 255, alphaInt)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light)
                .setNormal(pose, 0.0F, 1.0F, 0.0F); // 1.21.1 支持直接传 pose 算 Normal
    }

    @Override
    public ResourceLocation getTextureLocation(CleaveBladeEntity entity) {
        return TEXTURE;
    }
}
