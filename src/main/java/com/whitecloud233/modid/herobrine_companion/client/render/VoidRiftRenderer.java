package com.whitecloud233.modid.herobrine_companion.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.whitecloud233.modid.herobrine_companion.HerobrineCompanion;
import com.whitecloud233.modid.herobrine_companion.entity.VoidRiftEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

public class VoidRiftRenderer extends EntityRenderer<VoidRiftEntity> {
    private static final ResourceLocation TEXTURE = new ResourceLocation(HerobrineCompanion.MODID, "textures/entity/void_rift.png");

    public VoidRiftRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(VoidRiftEntity entity, float entityYaw, float partialTicks, PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        poseStack.pushPose();
        // [新增] 应用随机旋转 (绕 Z 轴)
        poseStack.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(entity.getRotation()));

        // 始终面向玩家
        poseStack.mulPose(this.entityRenderDispatcher.cameraOrientation());
        poseStack.mulPose(com.mojang.math.Axis.YP.rotationDegrees(180.0F));

        // 缩放
        poseStack.scale(3.0F, 3.0F, 3.0F);

        PoseStack.Pose last = poseStack.last();
        Matrix4f pose = last.pose();
        Matrix3f normal = last.normal();

        // 使用半透明发光材质
        VertexConsumer vertexConsumer = buffer.getBuffer(RenderType.entityTranslucentEmissive(this.getTextureLocation(entity)));

        // 绘制一个简单的平面
        addVertex(vertexConsumer, pose, normal, packedLight, -0.5F, -0.5F, 0, 1);
        addVertex(vertexConsumer, pose, normal, packedLight, 0.5F, -0.5F, 1, 1);
        addVertex(vertexConsumer, pose, normal, packedLight, 0.5F, 0.5F, 1, 0);
        addVertex(vertexConsumer, pose, normal, packedLight, -0.5F, 0.5F, 0, 0);

        poseStack.popPose();
        super.render(entity, entityYaw, partialTicks, poseStack, buffer, packedLight);
    }

    private static void addVertex(VertexConsumer consumer, Matrix4f pose, Matrix3f normal, int lightmapUV, float x, float y, int u, int v) {
        consumer.vertex(pose, x, y, 0.0F)
                .color(255, 255, 255, 255)
                .uv((float)u, (float)v)
                .overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(lightmapUV)
                .normal(normal, 0.0F, 1.0F, 0.0F)
                .endVertex();
    }

    @Override
    public ResourceLocation getTextureLocation(VoidRiftEntity entity) {
        return TEXTURE;
    }
}
