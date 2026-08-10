package com.whitecloud233.modid.herobrine_companion.compat.epicfight.effect;

import com.whitecloud233.modid.herobrine_companion.compat.epicfight.HeroEpicFightPatch;
import com.whitecloud233.modid.herobrine_companion.compat.epicfight.HeroNightfallSkillSpec;
import com.whitecloud233.modid.herobrine_companion.entity.HeroEntity;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

/** 阎魔刀·达摩克利斯之剑（YAMATO_DAMOCLES）：巨剑从 4 格高空垂直贯穿中心。 */
public final class YamatoDamoclesEffect extends AbstractHeroNightfallSkillEffect {
    @Override
    public void apply(HeroEpicFightPatch patch, HeroEntity hero, Vec3 center, HeroNightfallSkillSpec spec) {
        if (!isServerLevel(hero)) {
            return;
        }
        ServerLevel level = (ServerLevel) hero.level();
        spawnSculkSwordParticles(level, center, spec.radius(), 24);
        if (invokeEfnSummon(EFN_DAMOCLES_SWORD, level, hero, hero.getTarget())) {
            return;
        }

        Vec3 top = center.add(0.0D, 4.0D, 0.0D);
        for (int step = 0; step <= 18; step++) {
            Vec3 point = top.lerp(center, step / 18.0D);
            double spread = step < 10 ? 0.05D : 0.18D;
            level.sendParticles(ParticleTypes.END_ROD, point.x, point.y, point.z, 2, spread, spread, spread, 0.0D);
            if (step % 3 == 0) {
                level.sendParticles(ParticleTypes.PORTAL, point.x, point.y, point.z, 2, 0.12D, 0.12D, 0.12D, 0.02D);
            }
        }

        level.playSound(null, center.x, center.y, center.z, SoundEvents.TRIDENT_HIT, SoundSource.HOSTILE, 1.3F, 0.75F);
        level.playSound(null, center.x, center.y, center.z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.HOSTILE, 0.9F, 0.8F);
        damageTargets(level, hero, center, spec.radius(), spec.bonusDamage(), 0.95D, true);
    }
}
