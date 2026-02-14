package com.whitecloud233.modid.herobrine_companion.mixin;

import com.whitecloud233.modid.herobrine_companion.entity.HeroEntity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {

    @Inject(method = "canAttack(Lnet/minecraft/world/entity/LivingEntity;)Z", at = @At("HEAD"), cancellable = true)
    private void canAttack(LivingEntity target, CallbackInfoReturnable<Boolean> cir) {
        if (target instanceof Player player && player.getTags().contains("herobrine_companion_peaceful")) {
            cir.setReturnValue(false);
        } else if (target instanceof HeroEntity) {
            cir.setReturnValue(false);
        }
    }
}
