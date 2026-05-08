package com.whitecloud233.modid.herobrine_companion.compat.epicfight;

import com.whitecloud233.modid.herobrine_companion.client.render.HeroEyesLayer;
import com.whitecloud233.modid.herobrine_companion.client.render.HeroHeldItemLayer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.world.entity.EntityType;
import yesman.epicfight.api.client.model.Meshes;
import yesman.epicfight.client.mesh.HumanoidMesh;
import yesman.epicfight.client.renderer.patched.entity.PCustomHumanoidEntityRenderer;

public class HeroPatchedHumanoidRenderer extends PCustomHumanoidEntityRenderer<HumanoidMesh> {
    @SuppressWarnings({"rawtypes", "unchecked"})
    public HeroPatchedHumanoidRenderer(EntityRendererProvider.Context context, EntityType<?> entityType) {
        super(Meshes.BIPED, context, entityType);
        this.addPatchedLayer((Class) HeroEyesLayer.class, (HeroPatchedEyesLayer) new HeroPatchedEyesLayer(Meshes.BIPED));
        this.addPatchedLayer(HeroHeldItemLayer.class, new HeroPatchedItemInHandLayer<>());
    }
}

