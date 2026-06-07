package com.whitecloud233.modid.herobrine_companion.destructiongod.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.entity.DisplayRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Display;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.RenderTypeHelper;
import net.minecraftforge.client.model.data.ModelData;

public class FaultAwareBlockDisplayRenderer extends DisplayRenderer.BlockDisplayRenderer {
    private final BlockRenderDispatcher blockRenderer;

    public FaultAwareBlockDisplayRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.blockRenderer = context.getBlockRenderDispatcher();
    }

    @Override
    public void renderInner(Display.BlockDisplay display, Display.BlockDisplay.BlockRenderState renderState, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, float partialTick) {
        if (display.level() == null) {
            super.renderInner(display, renderState, poseStack, bufferSource, packedLight, partialTick);
            return;
        }

        BlockState blockState = renderState.blockState();
        if (blockState.getRenderShape() != RenderShape.MODEL) {
            super.renderInner(display, renderState, poseStack, bufferSource, packedLight, partialTick);
            return;
        }

        BlockPos blockPos = display.blockPosition();
        BakedModel bakedModel = this.blockRenderer.getBlockModel(blockState);
        long seed = blockState.getSeed(blockPos);
        RandomSource modelRandom = RandomSource.create(seed);

        for (RenderType renderType : bakedModel.getRenderTypes(blockState, modelRandom, ModelData.EMPTY)) {
            VertexConsumer consumer = bufferSource.getBuffer(RenderTypeHelper.getEntityRenderType(renderType, false));
            RandomSource quadRandom = RandomSource.create(seed);
            this.blockRenderer.renderBatched(
                    blockState,
                    blockPos,
                    display.level(),
                    poseStack,
                    consumer,
                    false,
                    quadRandom,
                    ModelData.EMPTY,
                    renderType
            );
        }
    }
}
