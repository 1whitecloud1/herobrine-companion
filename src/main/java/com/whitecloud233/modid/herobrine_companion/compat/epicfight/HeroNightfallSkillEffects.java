package com.whitecloud233.modid.herobrine_companion.compat.epicfight;

import com.whitecloud233.modid.herobrine_companion.entity.HeroEntity;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import yesman.epicfight.api.animation.Joint;
import yesman.epicfight.api.animation.Pose;
import yesman.epicfight.api.utils.math.OpenMatrix4f;
import yesman.epicfight.api.utils.math.Vec3f;

import java.util.List;

public final class HeroNightfallSkillEffects {
    private static final String EFN_BLAST_SUMMONED_SWORD = "com.hm.efn.entity.effect.BlastSummonedSwordEntity";
    private static final String EFN_HEAVY_RAIN_SWORD = "com.hm.efn.entity.effect.HeavyRainSwordEntity";
    private static final String EFN_DAMOCLES_SWORD = "com.hm.efn.entity.effect.DamoclesSwordEntity";

    private HeroNightfallSkillEffects() {
    }

    public static void apply(HeroEntity hero, HeroNightfallSkillSpec effect) {
        apply(null, hero, effect, false);
    }

    public static void apply(HeroEpicFightPatch patch, HeroEntity hero, HeroNightfallSkillSpec effect, boolean alignToWeaponJoint) {
        if (!(hero.level() instanceof ServerLevel level) || effect == null) {
            return;
        }

        LivingEntity target = hero.getTarget();
        Vec3 center = resolveCenter(patch, hero, target, alignToWeaponJoint);

        switch (effect.effectType()) {
            case ARC_SLASH -> applyArcSlash(level, hero, center, effect.radius(), effect.bonusDamage());
            case CRIMSON_SLASH -> applyCrimsonSlash(level, hero, center, effect.radius(), effect.bonusDamage());
            case JUDGEMENT_CUT -> applyJudgementCut(level, hero, center, effect.radius(), effect.bonusDamage());
            case GROUND_BURST -> applyGroundBurst(level, hero, center, effect.radius(), effect.bonusDamage());
            case LIGHTNING_CALL -> applyLightningCall(level, hero, center, effect.radius(), effect.bonusDamage());
            case YAMATO_BLAST_SWORD -> applyYamatoBlastSword(level, hero, center, effect.radius(), effect.bonusDamage());
            case YAMATO_HEAVY_RAIN -> applyYamatoHeavyRain(level, hero, center, effect.radius(), effect.bonusDamage());
            case YAMATO_DAMOCLES -> applyYamatoDamocles(level, hero, center, effect.radius(), effect.bonusDamage());
        }
    }

    private static Vec3 resolveCenter(HeroEpicFightPatch patch, HeroEntity hero, LivingEntity target, boolean alignToWeaponJoint) {
        if (alignToWeaponJoint) {
            Vec3 weaponJointCenter = resolveWeaponJointCenter(patch, hero);
            if (weaponJointCenter != null) {
                return weaponJointCenter;
            }
        }

        if (target != null && target.isAlive()) {
            return target.position().add(0.0D, target.getBbHeight() * 0.5D, 0.0D);
        }

        return hero.position().add(0.0D, hero.getBbHeight() * 0.55D, 0.0D).add(hero.getLookAngle().scale(2.25D));
    }

    private static Vec3 resolveWeaponJointCenter(HeroEpicFightPatch patch, HeroEntity hero) {
        if (patch == null || patch.getAnimator() == null || patch.getArmature() == null) {
            return null;
        }

        Joint joint = patch.getParentJointOfHand(InteractionHand.MAIN_HAND);
        if (joint == null) {
            return null;
        }

        try {
            Pose pose = patch.getAnimator().getPose(1.0F);
            OpenMatrix4f jointTransform = patch.getArmature().getBoundTransformFor(pose, joint)
                    .mulFront(OpenMatrix4f.createTranslation((float) hero.getX(), (float) hero.getY(), (float) hero.getZ())
                            .mulBack(OpenMatrix4f.createRotatorDeg(180.0F, Vec3f.Y_AXIS)
                                    .mulBack(patch.getModelMatrix(1.0F))));
            return OpenMatrix4f.transform(jointTransform, Vec3.ZERO)
                    .add(hero.getLookAngle().scale(0.65D))
                    .add(0.0D, hero.getBbHeight() * 0.05D, 0.0D);
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private static void applyArcSlash(ServerLevel level, HeroEntity hero, Vec3 center, double radius, float damage) {
        level.sendParticles(ParticleTypes.SWEEP_ATTACK, center.x, center.y, center.z, 6, 0.45D, 0.15D, 0.45D, 0.0D);
        level.sendParticles(ParticleTypes.CRIT, center.x, center.y, center.z, 14, 0.6D, 0.3D, 0.6D, 0.02D);
        level.playSound(null, center.x, center.y, center.z, SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.HOSTILE, 1.1F, 0.8F);
        damageTargets(level, hero, center, radius, damage, 0.55D, false);
    }

    private static void applyCrimsonSlash(ServerLevel level, HeroEntity hero, Vec3 center, double radius, float damage) {
        level.sendParticles(ParticleTypes.SWEEP_ATTACK, center.x, center.y, center.z, 8, 0.55D, 0.2D, 0.55D, 0.0D);
        level.sendParticles(ParticleTypes.FLAME, center.x, center.y, center.z, 18, 0.7D, 0.25D, 0.7D, 0.01D);
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, center.x, center.y, center.z, 12, 0.6D, 0.25D, 0.6D, 0.02D);
        level.playSound(null, center.x, center.y, center.z, SoundEvents.BLAZE_SHOOT, SoundSource.HOSTILE, 1.0F, 0.7F);
        damageTargets(level, hero, center, radius, damage, 0.65D, false);
    }

    private static void applyJudgementCut(ServerLevel level, HeroEntity hero, Vec3 center, double radius, float damage) {
        level.sendParticles(ParticleTypes.END_ROD, center.x, center.y, center.z, 18, 0.8D, 0.35D, 0.8D, 0.05D);
        level.sendParticles(ParticleTypes.PORTAL, center.x, center.y, center.z, 22, 0.85D, 0.35D, 0.85D, 0.1D);
        level.sendParticles(ParticleTypes.CRIT, center.x, center.y, center.z, 10, 0.55D, 0.2D, 0.55D, 0.02D);
        level.playSound(null, center.x, center.y, center.z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.HOSTILE, 1.0F, 1.2F);
        level.playSound(null, center.x, center.y, center.z, SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.HOSTILE, 1.0F, 0.55F);
        damageTargets(level, hero, center, radius, damage, 0.45D, true);
    }

    private static void applyGroundBurst(ServerLevel level, HeroEntity hero, Vec3 center, double radius, float damage) {
        level.sendParticles(ParticleTypes.EXPLOSION, center.x, center.y, center.z, 3, 0.2D, 0.05D, 0.2D, 0.0D);
        level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, center.x, center.y, center.z, 10, 0.7D, 0.15D, 0.7D, 0.01D);
        level.sendParticles(ParticleTypes.CRIT, center.x, center.y, center.z, 16, 0.9D, 0.25D, 0.9D, 0.03D);
        level.playSound(null, center.x, center.y, center.z, SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 0.9F, 1.15F);
        damageTargets(level, hero, center, radius, damage, 0.85D, false);
    }

    private static void applyLightningCall(ServerLevel level, HeroEntity hero, Vec3 center, double radius, float damage) {
        LightningBolt lightning = EntityType.LIGHTNING_BOLT.create(level);
        if (lightning != null) {
            lightning.moveTo(center);
            lightning.setVisualOnly(true);
            level.addFreshEntity(lightning);
        }

        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, center.x, center.y, center.z, 20, 0.75D, 0.3D, 0.75D, 0.04D);
        level.sendParticles(ParticleTypes.CRIT, center.x, center.y, center.z, 10, 0.55D, 0.2D, 0.55D, 0.03D);
        level.playSound(null, center.x, center.y, center.z, SoundEvents.TRIDENT_THUNDER, SoundSource.HOSTILE, 1.4F, 1.05F);
        damageTargets(level, hero, center, radius, damage, 0.7D, true);
    }

    private static void applyYamatoBlastSword(ServerLevel level, HeroEntity hero, Vec3 center, double radius, float damage) {
        spawnSculkSwordParticles(level, center, radius, 10);
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
            direction = hero.getLookAngle().scale(Math.max(2.0D, radius));
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
        damageTargets(level, hero, end, radius, damage, 0.45D, true);
    }

    private static void applyYamatoHeavyRain(ServerLevel level, HeroEntity hero, Vec3 center, double radius, float damage) {
        spawnSculkSwordParticles(level, center, radius, 18);
        if (invokeEfnSummon(EFN_HEAVY_RAIN_SWORD, level, hero, hero.getTarget())) {
            return;
        }

        level.playSound(null, center.x, center.y, center.z, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.HOSTILE, 1.1F, 1.45F);
        int swordCount = 12;
        for (int i = 0; i < swordCount; i++) {
            double angle = (Math.PI * 2.0D * i) / swordCount;
            double ring = radius * (0.25D + 0.55D * ((i % 3) / 2.0D));
            Vec3 top = center.add(Mth.cos((float) angle) * ring, 2.4D + (i % 4) * 0.18D, Mth.sin((float) angle) * ring);
            Vec3 bottom = top.add(0.0D, -2.1D, 0.0D);
            for (int step = 0; step <= 6; step++) {
                Vec3 point = top.lerp(bottom, step / 6.0D);
                level.sendParticles(ParticleTypes.END_ROD, point.x, point.y, point.z, 1, 0.01D, 0.01D, 0.01D, 0.0D);
            }
        }

        level.sendParticles(ParticleTypes.CRIT, center.x, center.y + 0.35D, center.z, 18, radius * 0.35D, 0.35D, radius * 0.35D, 0.05D);
        damageTargets(level, hero, center, radius, damage, 0.25D, true);
    }

    private static void applyYamatoDamocles(ServerLevel level, HeroEntity hero, Vec3 center, double radius, float damage) {
        spawnSculkSwordParticles(level, center, radius, 24);
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
        damageTargets(level, hero, center, radius, damage, 0.95D, true);
    }

    private static boolean invokeEfnSummon(String className, ServerLevel level, HeroEntity hero, LivingEntity target) {
        try {
            Class<?> effectClass = Class.forName(className, false, HeroNightfallSkillEffects.class.getClassLoader());
            if (target != null && target.isAlive()) {
                try {
                    effectClass.getMethod("summon", net.minecraft.world.level.Level.class, LivingEntity.class, LivingEntity.class)
                            .invoke(null, level, hero, target);
                    return true;
                } catch (NoSuchMethodException ignored) {
                }
            }

            effectClass.getMethod("summon", net.minecraft.world.level.Level.class, LivingEntity.class)
                    .invoke(null, level, hero);
            return true;
        } catch (ReflectiveOperationException | LinkageError | RuntimeException exception) {
            return false;
        }
    }

    private static void spawnSculkSwordParticles(ServerLevel level, Vec3 center, double radius, int count) {
        level.sendParticles(ParticleTypes.SCULK_SOUL, center.x, center.y + 0.15D, center.z, count, radius * 0.18D, 0.35D, radius * 0.18D, 0.02D);
        level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, center.x, center.y + 0.2D, center.z, Math.max(4, count / 2), radius * 0.22D, 0.22D, radius * 0.22D, 0.01D);
        level.sendParticles(ParticleTypes.PORTAL, center.x, center.y + 0.2D, center.z, Math.max(6, count / 2), radius * 0.28D, 0.25D, radius * 0.28D, 0.04D);
    }

    private static void damageTargets(ServerLevel level, HeroEntity hero, Vec3 center, double radius, float damage, double pushStrength, boolean resetInvulnerability) {
        AABB bounds = new AABB(center.x - radius, center.y - radius, center.z - radius, center.x + radius, center.y + radius, center.z + radius);
        List<LivingEntity> targets = level.getEntitiesOfClass(LivingEntity.class, bounds, entity -> entity.isAlive() && canHeroSkillDamage(hero, entity));

        for (LivingEntity target : targets) {
            if (resetInvulnerability) {
                target.invulnerableTime = 0;
            }

            target.hurt(level.damageSources().magic(), damage);
            Vec3 push = target.position().subtract(center);
            if (push.lengthSqr() < 1.0E-4D) {
                push = hero.getLookAngle();
            }
            push = push.normalize().scale(pushStrength);
            target.push(push.x, 0.22D + pushStrength * 0.18D, push.z);
        }
    }

    private static boolean canHeroSkillDamage(HeroEntity hero, LivingEntity target) {
        if (target == hero || target instanceof HeroEntity) {
            return false;
        }
        if (target instanceof net.minecraft.world.entity.player.Player player) {
            return hero.getOwnerUUID() == null || !hero.getOwnerUUID().equals(player.getUUID());
        }
        return true;
    }

    public enum EffectType {
        ARC_SLASH,
        CRIMSON_SLASH,
        JUDGEMENT_CUT,
        GROUND_BURST,
        LIGHTNING_CALL,
        YAMATO_BLAST_SWORD,
        YAMATO_HEAVY_RAIN,
        YAMATO_DAMOCLES
    }
}


