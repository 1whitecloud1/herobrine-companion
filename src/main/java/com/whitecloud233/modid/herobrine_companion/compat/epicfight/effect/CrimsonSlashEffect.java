package com.whitecloud233.modid.herobrine_companion.compat.epicfight.effect;

import com.whitecloud233.modid.herobrine_companion.compat.epicfight.HeroEpicFightPatch;
import com.whitecloud233.modid.herobrine_companion.compat.epicfight.HeroNightfallSkillSpec;
import com.whitecloud233.modid.herobrine_companion.entity.HeroEntity;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

/** 赤红斩击特效（CRIMSON_SLASH）：火焰 + 电火花。 */
public final class CrimsonSlashEffect extends AbstractHeroNightfallSkillEffect {
    @Override
    public void apply(HeroEpicFightPatch patch, HeroEntity hero, Vec3 center, HeroNightfallSkillSpec spec) {
        if (!isServerLevel(hero)) {
            return;
        }
        ServerLevel level = (ServerLevel) hero.level();
        level.sendParticles(ParticleTypes.SWEEP_ATTACK, center.x, center.y, center.z, 8, 0.55D, 0.2D, 0.55D, 0.0D);
        level.sendParticles(ParticleTypes.FLAME, center.x, center.y, center.z, 18, 0.7D, 0.25D, 0.7D, 0.01D);
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, center.x, center.y, center.z, 12, 0.6D, 0.25D, 0.6D, 0.02D);
        level.playSound(null, center.x, center.y, center.z, SoundEvents.BLAZE_SHOOT, SoundSource.HOSTILE, 1.0F, 0.7F);
        damageTargets(level, hero, center, spec.radius(), spec.bonusDamage(), 0.65D, false);
    }
}
