package com.whitecloud233.modid.herobrine_companion.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.whitecloud233.modid.herobrine_companion.client.animation.PoemItemMesh;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/** Native 3D item rendering for Epic Fight installations without GeckoLib. */
public final class PoemOfTheEndMeshRenderer extends BlockEntityWithoutLevelRenderer {
    private final PoemOfTheEndFallbackRenderer flatRenderer = new PoemOfTheEndFallbackRenderer();
    public PoemOfTheEndMeshRenderer() {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext context, PoseStack poseStack,
                             MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        if (context == ItemDisplayContext.GUI) {
            flatRenderer.renderByItem(stack, context, poseStack, bufferSource, packedLight, packedOverlay);
            return;
        }
        poseStack.pushPose();
        try {
            // Forge keeps the builtin/entity display empty; apply it exactly once.
            poseStack.translate(.5, .5, .5);
            PoemItemMesh.display(context).apply(false, poseStack);
            poseStack.translate(-.5, -.5, -.5);
            // Match GeoObjectRenderer's model origin so Epic Fight's sockets still line up.
            poseStack.translate(.5, .51, .5);
            VertexConsumer base = ItemRenderer.getFoilBufferDirect(bufferSource,
                    PoemWeaponRenderTypes.base(), false, stack.hasFoil());
            PoemItemMesh.render(poseStack, base, packedLight, packedOverlay);
            PoemItemMesh.render(poseStack, bufferSource.getBuffer(PoemWeaponRenderTypes.glow()),
                    LightTexture.FULL_BRIGHT, packedOverlay);
        } finally { poseStack.popPose(); }
    }
}
