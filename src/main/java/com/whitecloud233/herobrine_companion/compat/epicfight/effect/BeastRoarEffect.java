package com.whitecloud233.herobrine_companion.compat.epicfight.effect;

import com.whitecloud233.herobrine_companion.compat.epicfight.HeroEpicFightPatch;
import com.whitecloud233.herobrine_companion.compat.epicfight.HeroNightfallSkillSpec;
import com.whitecloud233.herobrine_companion.entity.HeroEntity;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.phys.Vec3;

/**
 * 狂野爪·狂野咆哮（BEAST_ROAR）：唤起野兽之血，短暂提升攻击与移速——
 * 对应 EFN 狂野爪的咆哮强化。
 */
public final class BeastRoarEffect extends AbstractHeroNightfallSkillEffect {
    @Override
    public void apply(HeroEpicFightPatch patch, HeroEntity hero, Vec3 center, HeroNightfallSkillSpec spec) {
        if (!isServerLevel(hero)) {
            return;
        }
        ServerLevel level = (ServerLevel) hero.level();
        hero.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 200, 0));
        hero.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 200, 0));

        Vec3 at = hero.position().add(0.0D, hero.getBbHeight() * 0.5D, 0.0D);
        level.sendParticles(ParticleTypes.SONIC_BOOM, at.x, at.y, at.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        level.sendParticles(ParticleTypes.SNEEZE, at.x, at.y, at.z, 16, 0.6D, 0.2D, 0.6D, 0.02D);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.WOLF_GROWL, SoundSource.HOSTILE, 1.6F, 0.8F);
    }
}
