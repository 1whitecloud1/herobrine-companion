package com.whitecloud233.modid.herobrine_companion.client.render;

/** Keep model baking and renderer registration on the same optional-mod decision. */
public enum PoemRenderBackend {
    FLAT, MESH, GECKOLIB;

    public static PoemRenderBackend choose(boolean epicFight, boolean geckoLib) {
        return geckoLib ? GECKOLIB : epicFight ? MESH : FLAT;
    }
}
