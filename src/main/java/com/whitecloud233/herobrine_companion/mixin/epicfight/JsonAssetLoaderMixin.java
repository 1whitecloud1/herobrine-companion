package com.whitecloud233.herobrine_companion.mixin.epicfight;

import net.minecraft.resources.ResourceLocation;
import org.apache.logging.log4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.lang.reflect.Method;

@Pseudo
@Mixin(targets = "yesman.epicfight.api.asset.JsonAssetLoader", remap = false)
public abstract class JsonAssetLoaderMixin {
    @Redirect(
            method = "loadClipForAnimation",
            at = @At(value = "INVOKE", target = "Lorg/apache/logging/log4j/Logger;debug(Ljava/lang/String;)V")
    )
    private void herobrineCompanion$suppressEfnKinematicsClipWarnings(Logger logger, String message, @Coerce Object animation) {
        if (!herobrineCompanion$shouldSuppress(animation, message)) {
            logger.debug(message);
        }
    }

    @Redirect(
            method = "loadAllJointsClipForAnimation",
            at = @At(value = "INVOKE", target = "Lorg/apache/logging/log4j/Logger;debug(Ljava/lang/String;)V")
    )
    private void herobrineCompanion$suppressEfnKinematicsAllJointWarnings(Logger logger, String message, @Coerce Object animation) {
        if (!herobrineCompanion$shouldSuppress(animation, message)) {
            logger.debug(message);
        }
    }

    @Unique
    private static boolean herobrineCompanion$shouldSuppress(Object animation, String message) {
        if (message == null || !message.contains(".KINEMATICS")) {
            return false;
        }

        ResourceLocation location = herobrineCompanion$getAnimationLocation(animation);
        if (location != null && "efn".equals(location.getNamespace())) {
            return true;
        }

        return message.contains(" efn:") || message.startsWith("efn:");
    }

    @Unique
    private static ResourceLocation herobrineCompanion$getAnimationLocation(Object animation) {
        if (animation == null) {
            return null;
        }

        try {
            Method getLocation = animation.getClass().getMethod("getLocation");
            Object value = getLocation.invoke(animation);
            if (value instanceof ResourceLocation resourceLocation) {
                return resourceLocation;
            }
        } catch (ReflectiveOperationException ignored) {
        }

        return null;
    }
}


