package com.whitecloud233.herobrine_companion.client.render;

import com.whitecloud233.herobrine_companion.entity.GlitchVillagerEntity;
import net.minecraft.client.model.VillagerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

public class GlitchVillagerRenderer extends MobRenderer<GlitchVillagerEntity, VillagerModel<GlitchVillagerEntity>> {
    // Use a missing texture or a corrupted one
    private static final ResourceLocation TEXTURE = ResourceLocation.withDefaultNamespace("textures/entity/villager/villager.png");

    public GlitchVillagerRenderer(EntityRendererProvider.Context context) {
        super(context, new VillagerModel<>(context.bakeLayer(ModelLayers.VILLAGER)), 0.5F);
    }

    @Override
    public ResourceLocation getTextureLocation(GlitchVillagerEntity entity) {
        // Return a texture that might look glitchy or just the standard one
        // The prompt mentions "missing texture" (purple/black checkerboard) or corrupted appearance.
        // We can't easily generate a missing texture resource location that works in code without a custom texture file.
        // But we can use a texture that doesn't exist to force the missing texture, or use a custom one if we had it.
        // For now, let's use the standard villager texture but maybe we can do something in the model or layers.
        // Or, if we want the purple/black square, we can return a path that definitely doesn't exist.
        return ResourceLocation.fromNamespaceAndPath("herobrine_companion", "textures/entity/glitch_villager.png");
    }
}
