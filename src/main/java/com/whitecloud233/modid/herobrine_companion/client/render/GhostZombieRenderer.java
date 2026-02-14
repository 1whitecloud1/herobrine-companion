package com.whitecloud233.modid.herobrine_companion.client.render;

import com.whitecloud233.modid.herobrine_companion.HerobrineCompanion;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ZombieRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.monster.Zombie;

public class GhostZombieRenderer extends ZombieRenderer {
    private static final ResourceLocation TEXTURE = new ResourceLocation(HerobrineCompanion.MODID, "textures/entity/ghost_zombie.png");

    public GhostZombieRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public ResourceLocation getTextureLocation(Zombie entity) {
        return TEXTURE;
    }
}
