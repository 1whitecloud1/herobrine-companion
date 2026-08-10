package com.whitecloud233.modid.herobrine_companion.compat.epicfight.effect;

import com.whitecloud233.modid.herobrine_companion.compat.epicfight.HeroEpicFightPatch;
import com.whitecloud233.modid.herobrine_companion.compat.epicfight.HeroNightfallSkillSpec;
import com.whitecloud233.modid.herobrine_companion.entity.HeroEntity;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.phys.Vec3;

/**
 * 血癫狂·血之狂暴（BLOOD_LUST）：以血唤醒刀刃，短暂强化攻击——
 * 对应 EFN 血癫狂模式的「每次斩击附血之力」的怪物侧近似（攻击力 + 攻速）。
 */
public final class BloodLustEffect extends AbstractHeroNightfallSkillEffect {
    @Override
    public void apply(HeroEpicFightPatch patch, HeroEntity hero, Vec3 center, HeroNightfallSkillSpec spec) {
        if (!isServerLevel(hero)) {
            return;
        }
        ServerLevel level = (ServerLevel) hero.level();
        hero.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 200, 1));
        hero.addEffect(new MobEffectInstance(MobEffects.DIG_SPEED, 200, 0));

        Vec3 at = hero.position().add(0.0D, hero.getBbHeight() * 0.6D, 0.0D);
        level.sendParticles(ParticleTypes.CRIMSON_SPORE, at.x, at.y, at.z, 20, 0.5D, 0.2D, 0.5D, 0.03D);
        level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, at.x, at.y, at.z, 10, 0.4D, 0.2D, 0.4D, 0.01D);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.BLAZE_SHOOT, SoundSource.HOSTILE, 1.0F, 0.6F);
    }
}
