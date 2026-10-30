package com.whitecloud233.herobrine_companion.compat.mixin.epicfight;

import net.neoforged.neoforge.registries.DeferredHolder;

import com.whitecloud233.herobrine_companion.compat.epicfight.WomAntitheusLapseCompat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import yesman.epicfight.api.animation.property.AnimationParameters;
import yesman.epicfight.api.animation.types.StaticAnimation;
import yesman.epicfight.api.asset.AssetAccessor;
import yesman.epicfight.skill.SkillDataKey;
import yesman.epicfight.skill.SkillDataManager;
import yesman.epicfight.world.capabilities.entitypatch.LivingEntityPatch;

/** Fix WOM 2.0.176's ANTITHEUS_LAPSE state reset at 1.75 seconds. */
@Pseudo
@Mixin(targets = "reascer.wom.gameasset.WOMAnimations", remap = false)
public abstract class WomAntitheusLapseMixin {
    @Redirect(
            method = "lambda$build$196",
            at = @At(value = "INVOKE", target = "Lyesman/epicfight/skill/SkillDataManager;setDataSync(Lnet/neoforged/neoforge/registries/DeferredHolder;Ljava/lang/Object;)V"),
            // Optional for other WOM builds; the pinned 2.0.176 bytecode is checked
            // by verifyWomLapseOwnership and contains exactly these three writes.
            require = 0, expect = 3
    )
    private static <T> void herobrineCompanion$resetOnlyOwnedSkillData(
            SkillDataManager manager, DeferredHolder<SkillDataKey<?>, ? extends SkillDataKey<T>> key, T value,
            LivingEntityPatch<?> entitypatch, AssetAccessor<? extends StaticAnimation> animation,
            AnimationParameters<?, ?, ?, ?, ?, ?, ?, ?, ?, ?> params) {
        WomAntitheusLapseCompat.resetOnlyOwnedSkillData(entitypatch, manager, key, value);
    }
}
