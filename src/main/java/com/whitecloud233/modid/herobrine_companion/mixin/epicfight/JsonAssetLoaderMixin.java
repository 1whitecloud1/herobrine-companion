package com.whitecloud233.modid.herobrine_companion.mixin.epicfight;

import net.minecraft.resources.ResourceLocation;
import org.apache.logging.log4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import yesman.epicfight.api.animation.types.StaticAnimation;
import yesman.epicfight.api.asset.JsonAssetLoader;

@Mixin(value = JsonAssetLoader.class, remap = false)
public abstract class JsonAssetLoaderMixin {
    @Redirect(
            method = "loadClipForAnimation",
            at = @At(value = "INVOKE", target = "Lorg/apache/logging/log4j/Logger;debug(Ljava/lang/String;)V")
    )
    private void herobrineCompanion$suppressEfnKinematicsClipWarnings(Logger logger, String message, StaticAnimation animation) {
        if (!herobrineCompanion$shouldSuppress(animation, message)) {
            logger.debug(message);
        }
    }

    @Redirect(
            method = "loadAllJointsClipForAnimation",
            at = @At(value = "INVOKE", target = "Lorg/apache/logging/log4j/Logger;debug(Ljava/lang/String;)V")
    )
    private void herobrineCompanion$suppressEfnKinematicsAllJointWarnings(Logger logger, String message, StaticAnimation animation) {
        if (!herobrineCompanion$shouldSuppress(animation, message)) {
            logger.debug(message);
        }
    }

    @Unique
    private static boolean herobrineCompanion$shouldSuppress(StaticAnimation animation, String message) {
        if (message == null || !message.contains(".KINEMATICS")) {
            return false;
        }

        if (animation != null) {
            ResourceLocation location = animation.getLocation();
            if (location != null && "efn".equals(location.getNamespace())) {
                return true;
            }
        }

        return message.contains(" efn:") || message.startsWith("efn:");
    }
}


