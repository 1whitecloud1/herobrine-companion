package com.whitecloud233.modid.herobrine_companion.mixin;

import com.whitecloud233.modid.herobrine_companion.entity.awakened.AwakenedMobBrain;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EnderDragon.class)
public abstract class AwakenedEnderDragonBrainMixin extends Mob {
    protected AwakenedEnderDragonBrainMixin(EntityType<? extends Mob> entityType, Level level) {
        super(entityType, level);
    }

    @Inject(method = "aiStep", at = @At("TAIL"))
    private void herobrineCompanion$tickAwakenedDragonBrain(CallbackInfo ci) {
        AwakenedMobBrain.serverTick((Mob) (Object) this);
    }
}
