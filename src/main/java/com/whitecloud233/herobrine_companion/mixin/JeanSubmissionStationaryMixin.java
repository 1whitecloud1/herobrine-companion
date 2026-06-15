package com.whitecloud233.herobrine_companion.mixin;

import com.whitecloud233.herobrine_companion.entity.awakened.containment.JeanSubmissionAnchor;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EnderDragon.class)
public abstract class JeanSubmissionStationaryMixin extends Mob {
    protected JeanSubmissionStationaryMixin(EntityType<? extends Mob> entityType, Level level) {
        super(entityType, level);
    }

    @Inject(method = "aiStep", at = @At("HEAD"))
    private void herobrineCompanion$freezeSubmissionStart(CallbackInfo ci) {
        JeanSubmissionAnchor.enforce((EnderDragon) (Object) this);
    }

    @Inject(method = "aiStep", at = @At("TAIL"))
    private void herobrineCompanion$freezeSubmissionEnd(CallbackInfo ci) {
        JeanSubmissionAnchor.enforce((EnderDragon) (Object) this);
    }
}
