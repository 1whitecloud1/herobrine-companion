package com.whitecloud233.modid.herobrine_companion.compat.epicfight.effect;

import com.whitecloud233.modid.herobrine_companion.compat.epicfight.HeroEpicFightPatch;
import com.whitecloud233.modid.herobrine_companion.compat.epicfight.HeroNightfallSkillSpec;
import com.whitecloud233.modid.herobrine_companion.entity.HeroEntity;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

/** 次元斩特效（JUDGEMENT_CUT）：传送门 + 末地棒斩痕。 */
public final class JudgementCutEffect extends AbstractHeroNightfallSkillEffect {
    @Override
    public void apply(HeroEpicFightPatch patch, HeroEntity hero, Vec3 center, HeroNightfallSkillSpec spec) {
        if (!isServerLevel(hero)) {
            return;
        }
        ServerLevel level = (ServerLevel) hero.level();
        level.sendParticles(ParticleTypes.END_ROD, center.x, center.y, center.z, 18, 0.8D, 0.35D, 0.8D, 0.05D);
        level.sendParticles(ParticleTypes.PORTAL, center.x, center.y, center.z, 22, 0.85D, 0.35D, 0.85D, 0.1D);
        level.sendParticles(ParticleTypes.CRIT, center.x, center.y, center.z, 10, 0.55D, 0.2D, 0.55D, 0.02D);
        level.playSound(null, center.x, center.y, center.z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.HOSTILE, 1.0F, 1.2F);
        level.playSound(null, center.x, center.y, center.z, SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.HOSTILE, 1.0F, 0.55F);
        damageTargets(level, hero, center, spec.radius(), spec.bonusDamage(), 0.45D, true);
    }
}
