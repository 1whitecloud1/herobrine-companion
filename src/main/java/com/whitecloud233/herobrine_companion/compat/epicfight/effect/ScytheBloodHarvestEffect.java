package com.whitecloud233.herobrine_companion.compat.epicfight.effect;

import com.whitecloud233.herobrine_companion.compat.epicfight.HeroEpicFightPatch;
import com.whitecloud233.herobrine_companion.compat.epicfight.HeroNightfallSkillSpec;
import com.whitecloud233.herobrine_companion.entity.HeroEntity;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * 赤月·收割（SCYTHE_BLOOD_HARVEST）：消耗目标 3 层血疫换取高额伤害并回复 Hero 生命——
 * 对应 EFN「战技·收割：命中吸收血疫转换为能量并获得霸体/攻击提升」。
 */
public final class ScytheBloodHarvestEffect extends AbstractHeroNightfallSkillEffect {
    private static final int CONSUME_PER_CAST = 3;

    @Override
    public void apply(HeroEpicFightPatch patch, HeroEntity hero, Vec3 center, HeroNightfallSkillSpec spec) {
        if (!isServerLevel(hero)) {
            return;
        }
        ServerLevel level = (ServerLevel) hero.level();
        LivingEntity target = hero.getTarget();
        int stacks = HeroBloodCurseStore.consumeLevels(target, CONSUME_PER_CAST);
        if (stacks <= 0) {
            stacks = 1;
        }

        float damage = spec.bonusDamage() * (0.6F + stacks);
        damageTargets(level, hero, center, spec.radius(), damage, 0.5D, false);
        hero.heal(1.5F * stacks);

        level.sendParticles(ParticleTypes.CRIMSON_SPORE, center.x, center.y + 0.2D, center.z, 18, spec.radius() * 0.4D, 0.3D, spec.radius() * 0.4D, 0.03D);
        level.sendParticles(ParticleTypes.HEART, hero.getX(), hero.getY() + hero.getBbHeight(), hero.getZ(), 3, 0.3D, 0.2D, 0.3D, 0.0D);
        level.playSound(null, center.x, center.y, center.z, SoundEvents.WITHER_SHOOT, SoundSource.HOSTILE, 1.0F, 0.7F);
    }
}
