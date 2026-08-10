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

/** 阎魔刀·爆破剑刃（YAMATO_BLAST_SWORD）：4 柄幻影剑沿视线钉射目标。 */
public final class YamatoBlastSwordEffect extends AbstractHeroNightfallSkillEffect {
    @Override
    public void apply(HeroEpicFightPatch patch, HeroEntity hero, Vec3 center, HeroNightfallSkillSpec spec) {
        if (!isServerLevel(hero)) {
            return;
        }
        ServerLevel level = (ServerLevel) hero.level();
        spawnSculkSwordParticles(level, center, spec.radius(), 10);
        if (invokeEfnSummon(EFN_BLAST_SUMMONED_SWORD, level, hero, hero.getTarget())) {
            return;
        }

        LivingEntity target = hero.getTarget();
        Vec3 start = hero.position().add(0.0D, hero.getBbHeight() * 0.7D, 0.0D).add(hero.getLookAngle().scale(0.9D));
        Vec3 end = target != null && target.isAlive()
                ? target.position().add(0.0D, target.getBbHeight() * 0.55D, 0.0D)
                : center;
        Vec3 direction = end.subtract(start);
        if (direction.lengthSqr() < 1.0E-4D) {
            direction = hero.getLookAngle().scale(Math.max(2.0D, spec.radius()));
            end = start.add(direction);
        }

        for (int swordIndex = 0; swordIndex < 4; swordIndex++) {
            double sideOffset = (swordIndex - 1.5D) * 0.28D;
            Vec3 side = new Vec3(-direction.z, 0.0D, direction.x).normalize().scale(sideOffset);
            for (int step = 0; step <= 10; step++) {
                Vec3 point = start.add(side).lerp(end.add(side), step / 10.0D);
                level.sendParticles(ParticleTypes.END_ROD, point.x, point.y, point.z, 1, 0.015D, 0.015D, 0.015D, 0.0D);
                if ((step & 1) == 0) {
                    level.sendParticles(ParticleTypes.CRIT, point.x, point.y, point.z, 1, 0.025D, 0.025D, 0.025D, 0.0D);
                }
            }
        }

        level.playSound(null, start.x, start.y, start.z, SoundEvents.TRIDENT_THROW, SoundSource.HOSTILE, 1.0F, 1.65F);
        damageTargets(level, hero, end, spec.radius(), spec.bonusDamage(), 0.45D, true);
    }
}
