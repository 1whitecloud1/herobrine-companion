package com.whitecloud233.modid.herobrine_companion.compat.epicfight.effect;

import com.whitecloud233.modid.herobrine_companion.compat.epicfight.HeroEpicFightPatch;
import com.whitecloud233.modid.herobrine_companion.compat.epicfight.HeroNightfallSkillSpec;
import com.whitecloud233.modid.herobrine_companion.entity.HeroEntity;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * 阎魔刀·幻影剑连射（YAMATO_SUMMONED_SWORD）：朝目标打出一串小型幻影剑刃，
 * 优先调用 EFN 爆破剑刃实体，失败则粒子 + 沿线伤害兜底。
 */
public final class YamatoSummonedSwordEffect extends AbstractHeroNightfallSkillEffect {
    @Override
    public void apply(HeroEpicFightPatch patch, HeroEntity hero, Vec3 center, HeroNightfallSkillSpec spec) {
        if (!isServerLevel(hero)) {
            return;
        }
        ServerLevel level = (ServerLevel) hero.level();
        spawnSculkSwordParticles(level, center, spec.radius(), 14);
        if (invokeEfnSummon(EFN_BLAST_SUMMONED_SWORD, level, hero, hero.getTarget())) {
            return;
        }

        LivingEntity target = hero.getTarget();
        Vec3 start = hero.position().add(0.0D, hero.getBbHeight() * 0.75D, 0.0D).add(hero.getLookAngle().scale(0.8D));
        Vec3 end = target != null && target.isAlive()
                ? target.position().add(0.0D, target.getBbHeight() * 0.5D, 0.0D)
                : center;
        Vec3 direction = end.subtract(start);
        if (direction.lengthSqr() < 1.0E-4D) {
            direction = hero.getLookAngle().scale(Math.max(2.0D, spec.radius()));
            end = start.add(direction);
        }
        Vec3 unit = direction.normalize();

        int volley = 6;
        for (int i = 0; i < volley; i++) {
            Vec3 point = start.add(unit.scale(i * 0.9D));
            level.sendParticles(ParticleTypes.END_ROD, point.x, point.y, point.z, 2, 0.08D, 0.08D, 0.08D, 0.0D);
            level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, point.x, point.y, point.z, 1, 0.04D, 0.04D, 0.04D, 0.0D);
        }

        level.playSound(null, start.x, start.y, start.z, SoundEvents.TRIDENT_THROW, SoundSource.HOSTILE, 1.0F, 1.8F);
        damageTargets(level, hero, end, spec.radius() * 0.8D, spec.bonusDamage(), 0.5D, true);
    }
}
