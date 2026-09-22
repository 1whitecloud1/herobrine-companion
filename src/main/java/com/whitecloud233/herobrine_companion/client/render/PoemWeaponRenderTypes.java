package com.whitecloud233.herobrine_companion.client.render;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

/** The same opaque base and unlit blade/gem material for both native weapon renderers. */
public final class PoemWeaponRenderTypes extends RenderType {
    private static final RenderType BASE = entityCutoutNoCull(ResourceLocation.fromNamespaceAndPath(
            "herobrine_companion", "textures/item/poem_of_the_end_native.png"));
    // The entity-translucent-emissive shader still applies minecraft_mix_light.
    // The eyes shader samples only texture/color, so glow does not dim with the
    // surface normal or the world's lightmap. Alpha blending retains its colors.
    private static final RenderType GLOW = create("herobrine_companion:poem_weapon_glow",
            DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS, 4096, false, true,
            CompositeState.builder().setShaderState(RENDERTYPE_EYES_SHADER)
                    .setTextureState(new TextureStateShard(ResourceLocation.fromNamespaceAndPath(
                            "herobrine_companion", "textures/item/poem_of_the_end_native_glow.png"), false, false))
                    .setTransparencyState(TRANSLUCENT_TRANSPARENCY).setCullState(NO_CULL)
                    .setDepthTestState(LEQUAL_DEPTH_TEST).setWriteMaskState(COLOR_WRITE).createCompositeState(false));

    private PoemWeaponRenderTypes() {
        super("poem_weapon_material", DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS,
                4096, false, true, () -> { }, () -> { });
    }

    public static RenderType base() { return BASE; }
    public static RenderType glow() { return GLOW; }
}
