package com.whitecloud233.modid.herobrine_companion.compat.epicfight.effect;

import com.whitecloud233.modid.herobrine_companion.compat.epicfight.HeroEpicFightPatch;
import com.whitecloud233.modid.herobrine_companion.compat.epicfight.HeroNightfallSkillSpec;
import com.whitecloud233.modid.herobrine_companion.entity.HeroEntity;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

/** 通用横扫斩击特效（ARC_SLASH）。 */
public final class ArcSlashEffect extends AbstractHeroNightfallSkillEffect {
    @Override
    public void apply(HeroEpicFightPatch patch, HeroEntity hero, Vec3 center, HeroNightfallSkillSpec spec) {
        if (!isServerLevel(hero)) {
            return;
        }
        ServerLevel level = (ServerLevel) hero.level();
        level.sendParticles(ParticleTypes.SWEEP_ATTACK, center.x, center.y, center.z, 6, 0.45D, 0.15D, 0.45D, 0.0D);
        level.sendParticles(ParticleTypes.CRIT, center.x, center.y, center.z, 14, 0.6D, 0.3D, 0.6D, 0.02D);
        level.playSound(null, center.x, center.y, center.z, SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.HOSTILE, 1.1F, 0.8F);
        damageTargets(level, hero, center, spec.radius(), spec.bonusDamage(), 0.55D, false);
    }
}
