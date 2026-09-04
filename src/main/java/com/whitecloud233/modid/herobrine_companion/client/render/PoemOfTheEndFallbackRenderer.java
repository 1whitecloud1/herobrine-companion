package com.whitecloud233.modid.herobrine_companion.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelManager;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import com.whitecloud233.modid.herobrine_companion.HerobrineCompanion;

/**
 * 终末之诗的回退渲染器（玩家未安装 GeckoLib 时使用）。
 *
 * <p>直接渲染原来的手写模型 {@code models/item/poem_of_the_end_base.json}
 * （该模型已通过 {@code ModelEvent.RegisterAdditional} 注册烘焙），
 * 显示变换取自该模型自身的 display，表现与改造前完全一致。</p>
 */
public final class PoemOfTheEndFallbackRenderer extends BlockEntityWithoutLevelRenderer {

    // 与 ClientModSetup 中 RegisterAdditional 注册的路径一致（不带 "item/" 前缀）
    private static final ModelResourceLocation BASE_MODEL =
            new ModelResourceLocation(HerobrineCompanion.MODID, "poem_of_the_end_base", "inventory");

    public PoemOfTheEndFallbackRenderer() {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext context, PoseStack poseStack,
                             MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        ModelManager modelManager = Minecraft.getInstance().getItemRenderer().getItemModelShaper().getModelManager();
        BakedModel baseModel = modelManager.getModel(BASE_MODEL);

        poseStack.pushPose();
        // 入口矩阵 = translate(-0.5)。抵消后施加原模型 display，再补回 -0.5，
        // 最终组合与 vanilla 对普通模型的处理完全一致：display ∘ translate(-0.5)。
        poseStack.translate(0.5F, 0.5F, 0.5F);
        baseModel.applyTransform(context, poseStack, false);
        poseStack.translate(-0.5F, -0.5F, -0.5F);

        ItemRenderer itemRenderer = Minecraft.getInstance().getItemRenderer();
        for (BakedModel pass : baseModel.getRenderPasses(stack, false)) {
            for (RenderType renderType : pass.getRenderTypes(stack, false)) {
                VertexConsumer vertexConsumer = ItemRenderer.getFoilBufferDirect(bufferSource, renderType, true, stack.hasFoil());
                itemRenderer.renderModelLists(pass, stack, packedLight, packedOverlay, poseStack, vertexConsumer);
            }
        }
        poseStack.popPose();
    }
}
