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
 * 赤月·赤色末路（SCYTHE_SCARLET_END）：清空目标全部血疫层数，层数越多伤害/回血越猛，
 * 大范围终结斩——对应 EFN「战技·赤色末路：清空目标血疫并根据层数提升伤害、回复生命值」。
 */
public final class ScytheScarletEndEffect extends AbstractHeroNightfallSkillEffect {
    @Override
    public void apply(HeroEpicFightPatch patch, HeroEntity hero, Vec3 center, HeroNightfallSkillSpec spec) {
        if (!isServerLevel(hero)) {
            return;
        }
        ServerLevel level = (ServerLevel) hero.level();
        LivingEntity target = hero.getTarget();
        int stacks = HeroBloodCurseStore.clear(target);

        float damage = spec.bonusDamage() * (1.0F + stacks * 0.5F);
        damageTargets(level, hero, center, spec.radius(), damage, 0.9D, true);
        hero.heal(2.0F + stacks);

        // 大范围血红斩痕
        for (int i = 0; i < 16; i++) {
            double angle = (Math.PI * 2.0D * i) / 16;
            double ring = spec.radius() * (0.4D + 0.6D * ((i % 3) / 2.0D));
            Vec3 at = center.add(Math.cos(angle) * ring, 0.25D, Math.sin(angle) * ring);
            level.sendParticles(ParticleTypes.CRIMSON_SPORE, at.x, at.y, at.z, 3, 0.25D, 0.2D, 0.25D, 0.02D);
            level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, at.x, at.y, at.z, 2, 0.2D, 0.2D, 0.2D, 0.0D);
        }
        level.sendParticles(ParticleTypes.FLASH, center.x, center.y + 0.3D, center.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        level.playSound(null, center.x, center.y, center.z, SoundEvents.WITHER_BREAK_BLOCK, SoundSource.HOSTILE, 1.2F, 0.8F);
    }
}
