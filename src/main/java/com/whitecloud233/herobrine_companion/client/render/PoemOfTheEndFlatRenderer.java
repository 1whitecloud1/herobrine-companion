package com.whitecloud233.herobrine_companion.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/**
 * 用普通烘焙模型渲染终末之诗的 2D 外观。
 *
 * <p>被 {@link GeckoLibPoemOfTheEndRenderer} 用于 <b>物品栏图标</b>——即使装了
 * GeckoLib，物品栏也沿用 2D 贴图，只有手持/地面/展示框等场景才用 3D 模型。</p>
 *
 * <p>模型 {@code poem_of_the_end_base#standalone} 由
 * {@link PoemOfTheEndModelSwapper} 通过 {@code ModelEvent.RegisterAdditional} 烘焙。</p>
 */
public final class PoemOfTheEndFlatRenderer {

    private PoemOfTheEndFlatRenderer() {
    }

    public static void render(ItemStack stack, ItemDisplayContext context, PoseStack poseStack,
                              MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        BakedModel model = Minecraft.getInstance().getModelManager()
                .getModel(PoemOfTheEndModelSwapper.flatModelLocation());

        poseStack.pushPose();
        // 入口矩阵 = translate(-0.5)。抵消后施加该模型自己的 display，再补回 -0.5，
        // 最终组合与 vanilla 对普通物品模型的处理完全一致：display ∘ translate(-0.5)。
        poseStack.translate(0.5F, 0.5F, 0.5F);
        model.applyTransform(context, poseStack, false);
        poseStack.translate(-0.5F, -0.5F, -0.5F);

        ItemRenderer itemRenderer = Minecraft.getInstance().getItemRenderer();
        for (BakedModel pass : model.getRenderPasses(stack, false)) {
            for (RenderType renderType : pass.getRenderTypes(stack, false)) {
                VertexConsumer buffer =
                        ItemRenderer.getFoilBufferDirect(bufferSource, renderType, true, stack.hasFoil());
                itemRenderer.renderModelLists(pass, stack, packedLight, packedOverlay, poseStack, buffer);
            }
        }
        poseStack.popPose();
    }
}
