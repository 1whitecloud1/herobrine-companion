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
 * 村雨·斩夺（MURASAMA_ZANDATSU）：目标血量低于 20% 时触发高额处决伤害，
 * 否则为普通突进斩——对应 EFN「斩切模式」的终结技语义。
 */
public final class MurasamaZandatsuEffect extends AbstractHeroNightfallSkillEffect {
    private static final float EXECUTE_HEALTH_RATIO = 0.2F;
    private static final float EXECUTE_MULTIPLIER = 2.5F;

    @Override
    public void apply(HeroEpicFightPatch patch, HeroEntity hero, Vec3 center, HeroNightfallSkillSpec spec) {
        if (!isServerLevel(hero)) {
            return;
        }
        ServerLevel level = (ServerLevel) hero.level();
        LivingEntity target = hero.getTarget();
        float multiplier = target != null && target.isAlive()
                && target.getHealth() / Math.max(1.0F, target.getMaxHealth()) < EXECUTE_HEALTH_RATIO
                ? EXECUTE_MULTIPLIER
                : 1.0F;

        // 高速十字斩痕
        Vec3 look = hero.getLookAngle();
        Vec3 side = new Vec3(-look.z, 0.0D, look.x).normalize();
        for (int i = -1; i <= 1; i += 2) {
            Vec3 from = center.add(side.scale(i * spec.radius() * 0.7D)).add(0.0D, -0.4D, 0.0D);
            Vec3 to = center.add(side.scale(i * spec.radius() * 0.7D)).add(0.0D, 0.4D, 0.0D);
            for (int step = 0; step <= 10; step++) {
                Vec3 point = from.lerp(to, step / 10.0D);
                level.sendParticles(ParticleTypes.END_ROD, point.x, point.y, point.z, 1, 0.04D, 0.04D, 0.04D, 0.0D);
            }
        }
        level.sendParticles(ParticleTypes.CRIT, center.x, center.y + 0.2D, center.z, 22, spec.radius() * 0.5D, 0.35D, spec.radius() * 0.5D, 0.05D);
        level.playSound(null, center.x, center.y, center.z, SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.HOSTILE, 1.3F, 0.5F);

        damageTargets(level, hero, center, spec.radius(), spec.bonusDamage() * multiplier, 0.85D, true);
    }
}
