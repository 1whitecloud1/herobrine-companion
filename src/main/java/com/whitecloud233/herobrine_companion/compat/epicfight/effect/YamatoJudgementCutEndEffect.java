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
 * 阎魔刀·次元斩绝（YAMATO_JUDGEMENT_CUT_END）：范围巨大的次元斩终焉，
 * 目标血量越低伤害越高（对 <20% 目标翻倍，对应 EFN 的处决语义）。
 */
public final class YamatoJudgementCutEndEffect extends AbstractHeroNightfallSkillEffect {
    private static final float EXECUTE_HEALTH_RATIO = 0.2F;
    private static final float EXECUTE_MULTIPLIER = 2.0F;

    @Override
    public void apply(HeroEpicFightPatch patch, HeroEntity hero, Vec3 center, HeroNightfallSkillSpec spec) {
        if (!isServerLevel(hero)) {
            return;
        }
        ServerLevel level = (ServerLevel) hero.level();

        // 满屏次元斩痕
        for (int i = 0; i < 24; i++) {
            double angle = (Math.PI * 2.0D * i) / 24;
            double ring = spec.radius() * (0.35D + 0.65D * ((i % 4) / 3.0D));
            Vec3 from = center.add(Math.cos(angle) * ring, (i % 3) * 0.5D, Math.sin(angle) * ring);
            Vec3 to = center.add(Math.cos(angle + 0.9D) * ring * 0.7D, (i % 3) * 0.5D + 0.3D, Math.sin(angle + 0.9D) * ring * 0.7D);
            for (int step = 0; step <= 8; step++) {
                Vec3 point = from.lerp(to, step / 8.0D);
                level.sendParticles(ParticleTypes.END_ROD, point.x, point.y, point.z, 1, 0.03D, 0.03D, 0.03D, 0.0D);
            }
        }
        level.sendParticles(ParticleTypes.PORTAL, center.x, center.y + 0.3D, center.z, 40, spec.radius() * 0.7D, 0.5D, spec.radius() * 0.7D, 0.08D);
        level.sendParticles(ParticleTypes.FLASH, center.x, center.y, center.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        level.playSound(null, center.x, center.y, center.z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.HOSTILE, 1.2F, 0.5F);
        level.playSound(null, center.x, center.y, center.z, SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.HOSTILE, 1.3F, 0.4F);

        LivingEntity target = hero.getTarget();
        float multiplier = target != null && target.isAlive()
                && target.getHealth() / Math.max(1.0F, target.getMaxHealth()) < EXECUTE_HEALTH_RATIO
                ? EXECUTE_MULTIPLIER
                : 1.0F;
        damageTargets(level, hero, center, spec.radius(), spec.bonusDamage() * multiplier, 1.05D, true);
    }
}
