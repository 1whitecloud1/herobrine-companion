package com.whitecloud233.modid.herobrine_companion.mixin.client;

import com.mojang.blaze3d.shaders.BlendMode;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(BlendMode.class)
public interface BlendModeAccessor {
    @Accessor("lastApplied")
    static BlendMode poem$getLastApplied() {
        throw new AssertionError();
    }

    @Accessor("lastApplied")
    static void poem$setLastApplied(BlendMode mode) {
        throw new AssertionError();
    }
}
