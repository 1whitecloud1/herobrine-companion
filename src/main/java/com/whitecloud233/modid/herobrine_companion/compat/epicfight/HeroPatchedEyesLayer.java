package com.whitecloud233.modid.herobrine_companion.compat.epicfight;

import com.mojang.blaze3d.vertex.PoseStack;
import com.whitecloud233.modid.herobrine_companion.HerobrineCompanion;
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
    private static final ResourceLocation EYES = ResourceLocation.fromNamespaceAndPath(HerobrineCompanion.MODID, "textures/entity/hero_eyes.png");
    private final RenderType renderType;
    private final AssetAccessor<? extends SkinnedMesh> mesh;

    public HeroPatchedEyesLayer(AssetAccessor<? extends SkinnedMesh> mesh) {
        this.mesh = mesh;
        this.renderType = RenderType.eyes(EYES);
    }

    @Override
    protected void renderLayer(LivingEntityPatch<LivingEntity> entitypatch, LivingEntity entityliving, RenderLayer<LivingEntity, HumanoidModel<LivingEntity>> vanillaLayer, PoseStack postStack, MultiBufferSource buffer, int packedLight, OpenMatrix4f[] poses, float bob, float yRot, float xRot, float partialTicks) {
        this.mesh.get().draw(postStack, buffer, this.renderType, 15728640, 1.0F, 1.0F, 1.0F, 1.0F, OverlayTexture.NO_OVERLAY, entitypatch.getArmature(), poses);
    }
}



