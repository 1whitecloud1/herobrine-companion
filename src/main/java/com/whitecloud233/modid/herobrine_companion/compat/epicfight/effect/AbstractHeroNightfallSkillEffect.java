package com.whitecloud233.modid.herobrine_companion.compat.epicfight.effect;

import com.whitecloud233.modid.herobrine_companion.compat.epicfight.HeroEpicFightPatch;
import com.whitecloud233.modid.herobrine_companion.compat.epicfight.HeroNightfallSkillSpec;
import com.whitecloud233.modid.herobrine_companion.entity.HeroEntity;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import yesman.epicfight.api.animation.Joint;
import yesman.epicfight.api.animation.Pose;
import yesman.epicfight.api.utils.math.OpenMatrix4f;
import yesman.epicfight.api.utils.math.Vec3f;

import java.util.List;

/**
 * 特效实现基类：集中所有特效共享的"外部依赖隔离"——伤害结算、目标过滤、粒子、EFN 实体反射召唤、
 * 武器关节中心解析。具体特效只实现各自的 {@link #apply}。
 */
public abstract class AbstractHeroNightfallSkillEffect implements HeroNightfallSkillEffect {

    protected static final String EFN_BLAST_SUMMONED_SWORD = "com.hm.efn.entity.effect.BlastSummonedSwordEntity";
    protected static final String EFN_HEAVY_RAIN_SWORD = "com.hm.efn.entity.effect.HeavyRainSwordEntity";
    protected static final String EFN_DAMOCLES_SWORD = "com.hm.efn.entity.effect.DamoclesSwordEntity";

    protected static boolean isServerLevel(HeroEntity hero) {
        return hero.level() instanceof ServerLevel;
    }

    /**
     * 解析特效中心：优先武器关节，其次目标身体中心，兜底为 Hero 视线前方。
     */
    public static Vec3 resolveCenter(HeroEpicFightPatch patch, HeroEntity hero, LivingEntity target, boolean alignToWeaponJoint) {
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

    /**
     * 对特效范围内可伤目标结算魔法伤害 + 击退。
     */
    protected static void damageTargets(ServerLevel level, HeroEntity hero, Vec3 center, double radius,
                                        float damage, double pushStrength, boolean resetInvulnerability) {
        AABB bounds = new AABB(center.x - radius, center.y - radius, center.z - radius,
                center.x + radius, center.y + radius, center.z + radius);
        List<LivingEntity> targets = level.getEntitiesOfClass(LivingEntity.class, bounds,
                entity -> entity.isAlive() && canHeroSkillDamage(hero, entity));

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

    protected static boolean canHeroSkillDamage(HeroEntity hero, LivingEntity target) {
        if (target == hero || target instanceof HeroEntity) {
            return false;
        }
        if (target instanceof net.minecraft.world.entity.player.Player player) {
            return hero.getOwnerUUID() == null || !hero.getOwnerUUID().equals(player.getUUID());
        }
        return true;
    }

    protected static void spawnSculkSwordParticles(ServerLevel level, Vec3 center, double radius, int count) {
        level.sendParticles(ParticleTypes.SCULK_SOUL, center.x, center.y + 0.15D, center.z, count,
                radius * 0.18D, 0.35D, radius * 0.18D, 0.02D);
        level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, center.x, center.y + 0.2D, center.z,
                Math.max(4, count / 2), radius * 0.22D, 0.22D, radius * 0.22D, 0.01D);
        level.sendParticles(ParticleTypes.PORTAL, center.x, center.y + 0.2D, center.z,
                Math.max(6, count / 2), radius * 0.28D, 0.25D, radius * 0.28D, 0.04D);
    }

    /**
     * 尝试反射调用 EFN 自身的召唤剑实体；失败则返回 false 由调用方走粒子/伤害兜底。
     */
    protected static boolean invokeEfnSummon(String className, ServerLevel level, HeroEntity hero, LivingEntity target) {
        try {
            Class<?> effectClass = Class.forName(className, false, AbstractHeroNightfallSkillEffect.class.getClassLoader());
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
}
