package com.whitecloud233.modid.herobrine_companion.mixin.epicfight;

import com.whitecloud233.modid.herobrine_companion.compat.epicfight.WomAntitheusLapseCompat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import reascer.wom.gameasset.WOMSkills;
import reascer.wom.skill.WOMSkillDataKeys;
import yesman.epicfight.api.animation.property.AnimationParameters;
import yesman.epicfight.api.animation.types.StaticAnimation;
import yesman.epicfight.api.asset.AssetAccessor;
import yesman.epicfight.skill.Skill;
import yesman.epicfight.skill.SkillDataKey;
import yesman.epicfight.skill.SkillDataManager;
import yesman.epicfight.world.capabilities.entitypatch.LivingEntityPatch;
import yesman.epicfight.world.capabilities.entitypatch.player.ServerPlayerPatch;

/** Fix WOM 2.0.171's ANTITHEUS_LAPSE state reset at 1.75 seconds. */
@Pseudo
@Mixin(targets = "reascer.wom.gameasset.WOMAnimations", remap = false)
public abstract class WomAntitheusLapseMixin {
    @Redirect(
            method = "lambda$build$196",
            at = @At(value = "INVOKE", target = "Lyesman/epicfight/skill/SkillDataManager;setDataSync(Lyesman/epicfight/skill/SkillDataKey;Ljava/lang/Object;)V"),
            // Optional for other WOM builds; the pinned 2.0.171 bytecode is checked
            // by verifyWomLapseOwnership and contains exactly these three writes.
            require = 0, expect = 3
    )
    private static <T> void herobrineCompanion$resetOnlyOwnedSkillData(
            SkillDataManager manager, SkillDataKey<T> key, T value,
            LivingEntityPatch<?> entitypatch, AssetAccessor<? extends StaticAnimation> animation,
            AnimationParameters<?, ?, ?, ?, ?, ?, ?, ?, ?, ?> params) {
        Skill expected;
        if (key == WOMSkillDataKeys.LAPSE.get() || key == WOMSkillDataKeys.PARTICLE.get()) {
            expected = WOMSkills.DEMON_MARK_PASSIVE;
        } else if (key == WOMSkillDataKeys.ACTIVE.get()) {
            expected = WOMSkills.DEMONIC_ASCENSION;
        } else {
            manager.setDataSync(key, value);
            return;
        }
        if (entitypatch instanceof ServerPlayerPatch player) {
            WomAntitheusLapseCompat.setDataSyncIfOwned(player.getSkill(expected), manager, key, value);
        }
    }
}
