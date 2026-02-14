package com.whitecloud233.modid.herobrine_companion.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.whitecloud233.modid.herobrine_companion.HerobrineCompanion;
import com.whitecloud233.modid.herobrine_companion.block.entity.EndRingPortalBlockEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

public class EndRingPortalRenderer implements BlockEntityRenderer<EndRingPortalBlockEntity> {
    public static final ResourceLocation PORTAL_TEXTURE = new ResourceLocation(HerobrineCompanion.MODID, "textures/entity/end_ring_portal.png");

    public EndRingPortalRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(EndRingPortalBlockEntity blockEntity, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        renderLayer(blockEntity, poseStack, bufferSource, PORTAL_TEXTURE, 0.0f, 0.0f, 0.6f, 1.0f);
    }

    private void renderLayer(EndRingPortalBlockEntity blockEntity, PoseStack poseStack, MultiBufferSource bufferSource, ResourceLocation texture, float rotation, float offset, float alpha, float scale) {
        VertexConsumer consumer = bufferSource.getBuffer(RenderType.entityTranslucent(texture));
        
        poseStack.pushPose();
        
        poseStack.translate(0.5, 0.75, 0.5); 
        if (rotation != 0.0f) {
            poseStack.mulPose(new Quaternionf().rotateY((float) Math.toRadians(rotation)));
        }
        poseStack.scale(scale, 1.0f, scale);
        poseStack.translate(-0.5, -0.75, -0.5);

        Matrix4f matrix4f = poseStack.last().pose();

        float yTop = 0.75f + offset * 0.01f;
        renderFaceTop(consumer, matrix4f, 0, 1, yTop, 0, 1, alpha);

        float yBottom = 0.25f - offset * 0.01f;
        renderFaceBottom(consumer, matrix4f, 0, 1, yBottom, 0, 1, alpha);
        
        poseStack.popPose();
    }
    
    private void renderFaceTop(VertexConsumer consumer, Matrix4f matrix, float x1, float x2, float y, float z1, float z2, float alpha) {
        consumer.vertex(matrix, x1, y, z1).color(1.0F, 1.0F, 1.0F, alpha).uv(0, 0).overlayCoords(655360).uv2(15728880).normal(0, 1, 0).endVertex();
        consumer.vertex(matrix, x1, y, z2).color(1.0F, 1.0F, 1.0F, alpha).uv(0, 1).overlayCoords(655360).uv2(15728880).normal(0, 1, 0).endVertex();
        consumer.vertex(matrix, x2, y, z2).color(1.0F, 1.0F, 1.0F, alpha).uv(1, 1).overlayCoords(655360).uv2(15728880).normal(0, 1, 0).endVertex();
        consumer.vertex(matrix, x2, y, z1).color(1.0F, 1.0F, 1.0F, alpha).uv(1, 0).overlayCoords(655360).uv2(15728880).normal(0, 1, 0).endVertex();
    }

    private void renderFaceBottom(VertexConsumer consumer, Matrix4f matrix, float x1, float x2, float y, float z1, float z2, float alpha) {
        consumer.vertex(matrix, x2, y, z1).color(1.0F, 1.0F, 1.0F, alpha).uv(1, 0).overlayCoords(655360).uv2(15728880).normal(0, -1, 0).endVertex();
        consumer.vertex(matrix, x2, y, z2).color(1.0F, 1.0F, 1.0F, alpha).uv(1, 1).overlayCoords(655360).uv2(15728880).normal(0, -1, 0).endVertex();
        consumer.vertex(matrix, x1, y, z2).color(1.0F, 1.0F, 1.0F, alpha).uv(0, 1).overlayCoords(655360).uv2(15728880).normal(0, -1, 0).endVertex();
        consumer.vertex(matrix, x1, y, z1).color(1.0F, 1.0F, 1.0F, alpha).uv(0, 0).overlayCoords(655360).uv2(15728880).normal(0, -1, 0).endVertex();
    }
}
