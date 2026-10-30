package com.whitecloud233.modid.herobrine_companion.compat.safety;

import com.mojang.blaze3d.vertex.PoseStack;
import com.whitecloud233.modid.herobrine_companion.client.render.HeroRenderer;
import com.whitecloud233.modid.herobrine_companion.entity.HeroEntity;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;

/** A failed third-party layer cannot corrupt the caller's PoseStack or run again next frame. */
public final class GuardedHeroLayer extends RenderLayer<HeroEntity, PlayerModel<HeroEntity>> {
    private final String integration;
    private final RenderLayer<HeroEntity, PlayerModel<HeroEntity>> delegate;

    public GuardedHeroLayer(RenderLayerParent<HeroEntity, PlayerModel<HeroEntity>> parent, String integration,
                            RenderLayer<HeroEntity, PlayerModel<HeroEntity>> delegate) {
        super(parent);
        this.integration = integration;
        this.delegate = delegate;
    }

    @Override
    public void render(PoseStack poses, MultiBufferSource buffers, int light, HeroEntity hero,
                       float limbSwing, float limbAmount, float partial, float age, float yaw, float pitch) {
        if (!CompatSafety.allowed(integration)) return;
        PoseStack isolated = new PoseStack();
        isolated.last().pose().set(poses.last().pose());
        isolated.last().normal().set(poses.last().normal());
        CompatSafety.run(integration, "render layer", () ->
                delegate.render(isolated, buffers, light, hero, limbSwing, limbAmount, partial, age, yaw, pitch));
    }
}
