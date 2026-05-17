package com.whitecloud233.herobrine_companion.destructiongod.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.entity.DisplayRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.Display;
import net.minecraft.world.level.block.state.BlockState;

public class FaultAwareBlockDisplayRenderer extends DisplayRenderer.BlockDisplayRenderer {
    private final BlockRenderDispatcher blockRenderer;

    public FaultAwareBlockDisplayRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.blockRenderer = context.getBlockRenderDispatcher();
    }

    @Override
    public void renderInner(Display.BlockDisplay display, Display.BlockDisplay.BlockRenderState renderState, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, float partialTick) {
        BlockState blockState = renderState.blockState();
        if (display.level() == null || blockState == null) {
            super.renderInner(display, renderState, poseStack, bufferSource, packedLight, partialTick);
            return;
        }
        this.blockRenderer.renderSingleBlock(blockState, poseStack, bufferSource, packedLight, OverlayTexture.NO_OVERLAY);
    }
}
