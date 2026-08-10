package com.whitecloud233.modid.herobrine_companion.compat.epicfight.effect;

import com.whitecloud233.modid.herobrine_companion.compat.epicfight.HeroEpicFightPatch;
import com.whitecloud233.modid.herobrine_companion.compat.epicfight.HeroNightfallSkillSpec;
import com.whitecloud233.modid.herobrine_companion.entity.HeroEntity;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/** 阎魔刀·五月雨（YAMATO_HEAVY_RAIN）：12 柄幻影剑从高处坠入中心区域。 */
public final class YamatoHeavyRainEffect extends AbstractHeroNightfallSkillEffect {
    @Override
    public void apply(HeroEpicFightPatch patch, HeroEntity hero, Vec3 center, HeroNightfallSkillSpec spec) {
        if (!isServerLevel(hero)) {
            return;
        }
        ServerLevel level = (ServerLevel) hero.level();
        spawnSculkSwordParticles(level, center, spec.radius(), 18);
        if (invokeEfnSummon(EFN_HEAVY_RAIN_SWORD, level, hero, hero.getTarget())) {
            return;
        }

        level.playSound(null, center.x, center.y, center.z, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.HOSTILE, 1.1F, 1.45F);
        int swordCount = 12;
        for (int i = 0; i < swordCount; i++) {
            double angle = (Math.PI * 2.0D * i) / swordCount;
            double ring = spec.radius() * (0.25D + 0.55D * ((i % 3) / 2.0D));
            Vec3 top = center.add(Mth.cos((float) angle) * ring, 2.4D + (i % 4) * 0.18D, Mth.sin((float) angle) * ring);
            Vec3 bottom = top.add(0.0D, -2.1D, 0.0D);
            for (int step = 0; step <= 6; step++) {
                Vec3 point = top.lerp(bottom, step / 6.0D);
                level.sendParticles(ParticleTypes.END_ROD, point.x, point.y, point.z, 1, 0.01D, 0.01D, 0.01D, 0.0D);
            }
        }

        level.sendParticles(ParticleTypes.CRIT, center.x, center.y + 0.35D, center.z, 18,
                spec.radius() * 0.35D, 0.35D, spec.radius() * 0.35D, 0.05D);
        damageTargets(level, hero, center, spec.radius(), spec.bonusDamage(), 0.25D, true);
    }
}
