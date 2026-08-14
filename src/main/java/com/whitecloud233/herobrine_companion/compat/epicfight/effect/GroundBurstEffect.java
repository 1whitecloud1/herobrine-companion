package com.whitecloud233.herobrine_companion.compat.epicfight.effect;

import com.whitecloud233.herobrine_companion.compat.epicfight.HeroEpicFightPatch;
import com.whitecloud233.herobrine_companion.compat.epicfight.HeroNightfallSkillSpec;
import com.whitecloud233.herobrine_companion.entity.HeroEntity;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

/** 大地爆裂特效（GROUND_BURST）：爆炸 + 浓烟。 */
public final class GroundBurstEffect extends AbstractHeroNightfallSkillEffect {
    @Override
    public void apply(HeroEpicFightPatch patch, HeroEntity hero, Vec3 center, HeroNightfallSkillSpec spec) {
        if (!isServerLevel(hero)) {
            return;
        }
        ServerLevel level = (ServerLevel) hero.level();
        level.sendParticles(ParticleTypes.EXPLOSION, center.x, center.y, center.z, 3, 0.2D, 0.05D, 0.2D, 0.0D);
        level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, center.x, center.y, center.z, 10, 0.7D, 0.15D, 0.7D, 0.01D);
        level.sendParticles(ParticleTypes.CRIT, center.x, center.y, center.z, 16, 0.9D, 0.25D, 0.9D, 0.03D);
        level.playSound(null, center.x, center.y, center.z, SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 0.9F, 1.15F);
        damageTargets(level, hero, center, spec.radius(), spec.bonusDamage(), 0.85D, false);
    }
}
