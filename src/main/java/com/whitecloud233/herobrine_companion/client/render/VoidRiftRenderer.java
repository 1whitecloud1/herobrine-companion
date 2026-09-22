package com.whitecloud233.herobrine_companion.client.render;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Axis;
import com.whitecloud233.herobrine_companion.entity.projectile.VoidRiftEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;

/** The normal path submits a procedural screen-space lens, without a textured entity quad. */
public class VoidRiftRenderer extends EntityRenderer<VoidRiftEntity> {
    public VoidRiftRenderer(EntityRendererProvider.Context context) { super(context); }

    @Override
    public void render(VoidRiftEntity entity, float entityYaw, float partialTicks, PoseStack poseStack,
                       MultiBufferSource buffer, int packedLight) {
        poseStack.pushPose();
        poseStack.mulPose(this.entityRenderDispatcher.cameraOrientation());
        poseStack.mulPose(Axis.ZP.rotationDegrees(entity.getRotation()));
        if (!VoidRiftPostEffect.submit(entity, poseStack.last().pose(), partialTicks)) {
            fallback(entity, partialTicks, poseStack.last().pose(), buffer);
        }
        poseStack.popPose();
        super.render(entity, entityYaw, partialTicks, poseStack, buffer, packedLight);
    }

    private static void fallback(VoidRiftEntity entity, float partialTick, Matrix4f pose, MultiBufferSource buffers) {
        float strength = VoidRiftProjection.envelope(entity.tickCount + partialTick, entity.getVisualLifetime());
        if (strength <= 0) return;
        VertexConsumer out = buffers.getBuffer(FallbackMaterial.TYPE);
        int alpha = (int) (255 * strength);
        for (int i = 0; i < 16; i++) {
            float y0 = (i / 8.0F - 1) * VoidRiftProjection.HALF_LENGTH;
            float y1 = ((i + 1) / 8.0F - 1) * VoidRiftProjection.HALF_LENGTH;
            float w0 = 0.19F * (float) Math.sin(Math.PI * i / 16) * strength;
            float w1 = 0.19F * (float) Math.sin(Math.PI * (i + 1) / 16) * strength;
            quad(out, pose, -w0, w0, y0, -w1, w1, y1, 4, 1, 9, alpha);
            quad(out, pose, -w0 - 0.025F, -w0, y0, -w1 - 0.025F, -w1, y1, 186, 129, 255, alpha);
            quad(out, pose, w0, w0 + 0.025F, y0, w1, w1 + 0.025F, y1, 186, 129, 255, alpha);
        }
    }

    private static void quad(VertexConsumer out, Matrix4f pose, float left0, float right0, float y0,
                             float left1, float right1, float y1, int r, int g, int b, int a) {
        out.addVertex(pose, left0, y0, 0).setColor(r, g, b, a);
        out.addVertex(pose, right0, y0, 0).setColor(r, g, b, a);
        out.addVertex(pose, right1, y1, 0).setColor(r, g, b, a);
        out.addVertex(pose, left1, y1, 0).setColor(r, g, b, a);
    }

    @Override
    public ResourceLocation getTextureLocation(VoidRiftEntity entity) {
        // EntityRenderer requires a location; neither the lens nor fallback samples a texture.
        return TextureAtlas.LOCATION_BLOCKS;
    }

    private static final class FallbackMaterial extends RenderType {
        private static final RenderType TYPE = create("herobrine_companion:rift_fallback",
                DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS, 4096, false, true,
                CompositeState.builder().setShaderState(POSITION_COLOR_SHADER)
                        .setTransparencyState(TRANSLUCENT_TRANSPARENCY).setCullState(NO_CULL)
                        .setDepthTestState(LEQUAL_DEPTH_TEST).setWriteMaskState(COLOR_WRITE).createCompositeState(false));

        private FallbackMaterial() {
            super("rift_fallback", DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS,
                    4096, false, true, () -> { }, () -> { });
        }
    }
}
