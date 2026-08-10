package com.whitecloud233.modid.herobrine_companion.compat.epicfight.effect;

import com.whitecloud233.modid.herobrine_companion.compat.epicfight.HeroEpicFightPatch;
import com.whitecloud233.modid.herobrine_companion.compat.epicfight.HeroNightfallSkillSpec;
import com.whitecloud233.modid.herobrine_companion.entity.HeroEntity;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * 赤月·血疫叠层（SCYTHE_BLOOD_STACK）：每次命中为目标叠加 1 层血疫（上限 10），
 * 层数越高附加的流血伤害越高——对应 EFN 赤月核心机制。
 */
public final class ScytheBloodStackEffect extends AbstractHeroNightfallSkillEffect {
    @Override
    public void apply(HeroEpicFightPatch patch, HeroEntity hero, Vec3 center, HeroNightfallSkillSpec spec) {
        if (!isServerLevel(hero)) {
            return;
        }
        ServerLevel level = (ServerLevel) hero.level();
        LivingEntity target = hero.getTarget();
        if (target == null || !target.isAlive() || !canHeroSkillDamage(hero, target)) {
            return;
        }

        int levelCount = HeroBloodCurseStore.addLevel(target, 1);
        float damage = spec.bonusDamage() * levelCount;
        if (damage <= 0.0F) {
            damage = 0.5F;
        }
        target.hurt(level.damageSources().magic(), damage);

        Vec3 at = target.position().add(0.0D, target.getBbHeight() * 0.6D, 0.0D);
        level.sendParticles(ParticleTypes.CRIMSON_SPORE, at.x, at.y, at.z, 4, 0.2D, 0.2D, 0.2D, 0.01D);
        level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, at.x, at.y, at.z, 2, 0.12D, 0.12D, 0.12D, 0.0D);
    }
}
