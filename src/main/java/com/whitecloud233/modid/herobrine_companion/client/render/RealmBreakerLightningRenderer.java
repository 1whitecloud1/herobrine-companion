package com.whitecloud233.modid.herobrine_companion.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.whitecloud233.modid.herobrine_companion.HerobrineCompanion;
import com.whitecloud233.modid.herobrine_companion.entity.projectile.RealmBreakerLightningEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

public class RealmBreakerLightningRenderer extends EntityRenderer<RealmBreakerLightningEntity> {
    private static final ResourceLocation TEXTURE = new ResourceLocation(HerobrineCompanion.MODID, "textures/entity/projectile/realm_breaker_lightning.png");

    public RealmBreakerLightningRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(RealmBreakerLightningEntity entity, float entityYaw, float partialTicks, PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        poseStack.pushPose();
        
        // 旋转以匹配运动方向
        // yRot - 90.0F 使本地 X 轴对齐运动方向
        poseStack.mulPose(com.mojang.math.Axis.YP.rotationDegrees(Mth.lerp(partialTicks, entity.yRotO, entity.getYRot()) - 90.0F));
        poseStack.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(Mth.lerp(partialTicks, entity.xRotO, entity.getXRot())));
        
        // 调整大小
        poseStack.scale(3.0F, 3.0F, 3.0F);
        
        PoseStack.Pose last = poseStack.last();
        Matrix4f pose = last.pose();
        Matrix3f normal = last.normal();
        VertexConsumer vertexConsumer = buffer.getBuffer(RenderType.entityCutoutNoCull(this.getTextureLocation(entity)));

        // 绘制十字交叉的两个平面，确保从任何角度都能看到
        // 平面 1: XY 平面 (垂直)
        vertex(vertexConsumer, pose, normal, packedLight, -0.5f, -0.5f, 0.0f, 0, 1);
        vertex(vertexConsumer, pose, normal, packedLight, 0.5f, -0.5f, 0.0f, 1, 1);
        vertex(vertexConsumer, pose, normal, packedLight, 0.5f, 0.5f, 0.0f, 1, 0);
        vertex(vertexConsumer, pose, normal, packedLight, -0.5f, 0.5f, 0.0f, 0, 0);

        // 平面 2: XZ 平面 (水平)
        vertex(vertexConsumer, pose, normal, packedLight, -0.5f, 0.0f, -0.5f, 0, 1);
        vertex(vertexConsumer, pose, normal, packedLight, 0.5f, 0.0f, -0.5f, 1, 1);
        vertex(vertexConsumer, pose, normal, packedLight, 0.5f, 0.0f, 0.5f, 1, 0);
        vertex(vertexConsumer, pose, normal, packedLight, -0.5f, 0.0f, 0.5f, 0, 0);

        poseStack.popPose();
        super.render(entity, entityYaw, partialTicks, poseStack, buffer, packedLight);
    }

    private static void vertex(VertexConsumer consumer, Matrix4f pose, Matrix3f normal, int lightmapUV, float x, float y, float z, int u, int v) {
        consumer.vertex(pose, x, y, z)
                .color(255, 255, 255, 255)
                .uv((float)u, (float)v)
                .overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(lightmapUV)
                .normal(normal, 0.0F, 1.0F, 0.0F)
                .endVertex();
    }

    @Override
    public ResourceLocation getTextureLocation(RealmBreakerLightningEntity entity) {
        return TEXTURE;
    }
}
