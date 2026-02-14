package com.whitecloud233.herobrine_companion.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.whitecloud233.herobrine_companion.HerobrineCompanion;
import com.whitecloud233.herobrine_companion.block.entity.EndRingPortalBlockEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

public class EndRingPortalRenderer implements BlockEntityRenderer<EndRingPortalBlockEntity> {
    public static final ResourceLocation PORTAL_TEXTURE = ResourceLocation.fromNamespaceAndPath(HerobrineCompanion.MODID, "textures/entity/end_ring_portal.png");

    public EndRingPortalRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(EndRingPortalBlockEntity blockEntity, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        // Render single layer with no rotation, relying on texture animation
        // Use lower alpha to avoid "milk" look if texture is too opaque
        renderLayer(blockEntity, poseStack, bufferSource, PORTAL_TEXTURE, 0.0f, 0.0f, 0.6f, 1.0f);
    }

    private void renderLayer(EndRingPortalBlockEntity blockEntity, PoseStack poseStack, MultiBufferSource bufferSource, ResourceLocation texture, float rotation, float offset, float alpha, float scale) {
        VertexConsumer consumer = bufferSource.getBuffer(RenderType.entityTranslucent(texture));
        
        poseStack.pushPose();
        
        // Center the rotation (even if 0)
        poseStack.translate(0.5, 0.75, 0.5); // Top face center
        if (rotation != 0.0f) {
            poseStack.mulPose(new Quaternionf().rotateY((float) Math.toRadians(rotation)));
        }
        poseStack.scale(scale, 1.0f, scale);
        poseStack.translate(-0.5, -0.75, -0.5);

        Matrix4f matrix4f = poseStack.last().pose();

        // Render Top Face (at y=0.75 + offset to prevent z-fighting)
        float yTop = 0.75f + offset * 0.01f;
        renderFaceTop(consumer, matrix4f, 0, 1, yTop, 0, 1, alpha);

        // Render Bottom Face (at y=0.25 - offset)
        float yBottom = 0.25f - offset * 0.01f;
        renderFaceBottom(consumer, matrix4f, 0, 1, yBottom, 0, 1, alpha);
        
        poseStack.popPose();
    }
    
    private void renderFaceTop(VertexConsumer consumer, Matrix4f matrix, float x1, float x2, float y, float z1, float z2, float alpha) {
        // Top face
        consumer.addVertex(matrix, x1, y, z1).setColor(1.0F, 1.0F, 1.0F, alpha).setUv(0, 0).setOverlay(655360).setLight(15728880).setNormal(0, 1, 0);
        consumer.addVertex(matrix, x1, y, z2).setColor(1.0F, 1.0F, 1.0F, alpha).setUv(0, 1).setOverlay(655360).setLight(15728880).setNormal(0, 1, 0);
        consumer.addVertex(matrix, x2, y, z2).setColor(1.0F, 1.0F, 1.0F, alpha).setUv(1, 1).setOverlay(655360).setLight(15728880).setNormal(0, 1, 0);
        consumer.addVertex(matrix, x2, y, z1).setColor(1.0F, 1.0F, 1.0F, alpha).setUv(1, 0).setOverlay(655360).setLight(15728880).setNormal(0, 1, 0);
    }

    private void renderFaceBottom(VertexConsumer consumer, Matrix4f matrix, float x1, float x2, float y, float z1, float z2, float alpha) {
        // Bottom face (Reverse order)
        consumer.addVertex(matrix, x2, y, z1).setColor(1.0F, 1.0F, 1.0F, alpha).setUv(1, 0).setOverlay(655360).setLight(15728880).setNormal(0, -1, 0);
        consumer.addVertex(matrix, x2, y, z2).setColor(1.0F, 1.0F, 1.0F, alpha).setUv(1, 1).setOverlay(655360).setLight(15728880).setNormal(0, -1, 0);
        consumer.addVertex(matrix, x1, y, z2).setColor(1.0F, 1.0F, 1.0F, alpha).setUv(0, 1).setOverlay(655360).setLight(15728880).setNormal(0, -1, 0);
        consumer.addVertex(matrix, x1, y, z1).setColor(1.0F, 1.0F, 1.0F, alpha).setUv(0, 0).setOverlay(655360).setLight(15728880).setNormal(0, -1, 0);
    }
}
