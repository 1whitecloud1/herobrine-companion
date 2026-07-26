package com.whitecloud233.modid.herobrine_companion.compat.epicfight;

import com.mojang.blaze3d.vertex.PoseStack;
import com.whitecloud233.modid.herobrine_companion.client.render.HeroRenderer;
import com.whitecloud233.modid.herobrine_companion.entity.HeroEntity;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import yesman.epicfight.api.asset.AssetAccessor;
import yesman.epicfight.api.client.model.SkinnedMesh;
import yesman.epicfight.api.utils.math.OpenMatrix4f;
import yesman.epicfight.client.renderer.patched.layer.PatchedLayer;
import yesman.epicfight.world.capabilities.entitypatch.LivingEntityPatch;

public class HeroPatchedEyesLayer extends PatchedLayer<LivingEntity, LivingEntityPatch<LivingEntity>, HumanoidModel<LivingEntity>, RenderLayer<LivingEntity, HumanoidModel<LivingEntity>>> {
    private final AssetAccessor<? extends SkinnedMesh> mesh;

    public HeroPatchedEyesLayer(AssetAccessor<? extends SkinnedMesh> mesh) {
        this.mesh = mesh;
    }

    @Override
    protected void renderLayer(LivingEntityPatch<LivingEntity> entitypatch, LivingEntity entityliving, RenderLayer<LivingEntity, HumanoidModel<LivingEntity>> vanillaLayer, PoseStack postStack, MultiBufferSource buffer, int packedLight, OpenMatrix4f[] poses, float bob, float yRot, float xRot, float partialTicks) {
        // 与原版 HeroEyesLayer 一致:自定义皮肤用自动识别的对齐眼睛图,无白眼则不渲染
        ResourceLocation eyes = (entityliving instanceof HeroEntity hero)
                ? HeroRenderer.getEyesTexture(hero)
                : HeroRenderer.DEFAULT_EYES;
        if (eyes == null) {
            return;
        }
        this.mesh.get().draw(postStack, buffer, RenderType.eyes(eyes), 15728640, 1.0F, 1.0F, 1.0F, 1.0F, OverlayTexture.NO_OVERLAY, entitypatch.getArmature(), poses);
    }
}



