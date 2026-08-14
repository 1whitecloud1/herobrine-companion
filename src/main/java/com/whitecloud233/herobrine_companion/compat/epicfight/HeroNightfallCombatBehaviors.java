package com.whitecloud233.herobrine_companion.compat.epicfight;

import com.whitecloud233.herobrine_companion.compat.epicfight.HeroNightfallSkillSeries.Trigger;
import com.whitecloud233.herobrine_companion.entity.HeroEntity;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;
import org.jetbrains.annotations.Nullable;
import yesman.epicfight.api.animation.AnimationManager.AnimationAccessor;
import yesman.epicfight.api.animation.types.StaticAnimation;
import yesman.epicfight.world.capabilities.entitypatch.HumanoidMobPatch;
import yesman.epicfight.world.entity.ai.goal.CombatBehaviors;

/**
 * 行为构建层（单一职责）：把 {@link HeroNightfallProfile} 翻译成 Epic Fight 怪物侧
 * {@code CombatBehaviors} 行为图（连段系列 + 技能系列）。只做"组装"，
 * 触发条件全部委托给 {@link HeroNightfallBehaviorGates}。
 */
final class HeroNightfallCombatBehaviors {
    private static final String IMPACTFUL_MOD_ID = "impactful";
    private static final double AIR_ATTACK_JUMP_Y = 0.62D;
    private static final double AIR_ATTACK_FORWARD_SPEED = 0.36D;

    private HeroNightfallCombatBehaviors() {
    }

    @Nullable
    static CombatBehaviors.Builder<HumanoidMobPatch<?>> build(HeroNightfallProfile profile) {
        if (profile == null || !profile.combatSafe()) {
            return null;
        }
        return isCrimsonMoonProfile(profile) ? buildCrimsonMoon(profile) : buildDefault(profile);
    }

    private static boolean isCrimsonMoonProfile(HeroNightfallProfile profile) {
        return profile.itemPaths().contains("crimson_moon");
    }

    private static CombatBehaviors.Builder<HumanoidMobPatch<?>> buildDefault(HeroNightfallProfile profile) {
        CombatBehaviors.Builder<HumanoidMobPatch<?>> builder = CombatBehaviors.builder();
        boolean addedAnySeries = false;
        double comboRange = Math.max(3.6D, profile.attackRadius() + 0.6D);

        CombatBehaviors.BehaviorSeries.Builder<HumanoidMobPatch<?>> comboSeries = CombatBehaviors.BehaviorSeries.<HumanoidMobPatch<?>>builder()
                .weight(100.0F)
                .cooldown(3)
                .canBeInterrupted(false)
                .looping(false);
        boolean hasComboAnimation = false;

        for (int comboIndex = 0; comboIndex < profile.comboAnimations().size(); comboIndex++) {
            final int behaviorComboIndex = comboIndex;
            HeroNightfallAnimationField animationField = profile.comboAnimations().get(comboIndex);
            AnimationAccessor<? extends StaticAnimation> animation = animationField.resolve();
            if (!isPlayableAttackAnimation(animation)) {
                continue;
            }

            comboSeries.nextBehavior(createAnimationBehavior(animation, resolveComboActionState(behaviorComboIndex), true)
                    .custom(mobPatch -> isPlayableAttackAnimation(animation)
                            && (behaviorComboIndex == 0
                            ? HeroNightfallBehaviorGates.canStartDefaultCombo(mobPatch, profile, behaviorComboIndex, comboRange)
                            : HeroNightfallBehaviorGates.canContinueDefaultCombo(mobPatch, profile, behaviorComboIndex, comboRange))));
            hasComboAnimation = true;
        }

        if (hasComboAnimation) {
            builder.newBehaviorSeries(comboSeries);
            addedAnySeries = true;
        } else {
            HeroEpicFightDebugLog.event(null, "nightfallBuild",
                    "combo series SKIPPED for " + profile.itemPaths() + " (no playable combo animation)");
        }

        for (HeroNightfallSkillSeries skillSeries : profile.skillSeries()) {
            if (appendSkillSeries(builder, skillSeries)) {
                addedAnySeries = true;
            }
        }

        if (!addedAnySeries) {
            HeroEpicFightDebugLog.event(null, "nightfallBuild",
                    "NO behavior series built for " + profile.itemPaths());
        }

        return addedAnySeries ? builder : null;
    }

    private static boolean appendSkillSeries(CombatBehaviors.Builder<HumanoidMobPatch<?>> builder, HeroNightfallSkillSeries skillSeries) {
        CombatBehaviors.BehaviorSeries.Builder<HumanoidMobPatch<?>> behaviorSeries = CombatBehaviors.BehaviorSeries.<HumanoidMobPatch<?>>builder()
                .weight(skillSeries.weight())
                .cooldown(skillSeries.cooldown())
                .canBeInterrupted(false)
                .looping(false);
        boolean hasSkillAnimation = false;
        boolean firstBehavior = true;

        for (HeroNightfallAnimationField animationField : skillSeries.animations()) {
            AnimationAccessor<? extends StaticAnimation> animation = animationField.resolve();
            if (!isPlayableAttackAnimation(animation)) {
                continue;
            }

            CombatBehaviors.Behavior.Builder<HumanoidMobPatch<?>> behavior =
                    createAnimationBehavior(animation, skillSeries.actionState(), false, skillSeries.airAttack());
            if (firstBehavior) {
                behavior.randomChance(skillSeries.chance())
                        .custom(mobPatch -> isPlayableAttackAnimation(animation) && canStartByTrigger(mobPatch, skillSeries));
                firstBehavior = false;
            } else {
                behavior.custom(HeroNightfallBehaviorGates::canContinueSkillSeries);
            }
            behaviorSeries.nextBehavior(behavior);
            hasSkillAnimation = true;
        }

        if (hasSkillAnimation) {
            builder.newBehaviorSeries(behaviorSeries);
        }
        return hasSkillAnimation;
    }

    private static boolean canStartByTrigger(HumanoidMobPatch<?> mobPatch, HeroNightfallSkillSeries series) {
        return switch (series.trigger()) {
            case COUNTER -> HeroNightfallBehaviorGates.canStartCounter(mobPatch, series);
            case BLOOD_HARVEST -> HeroNightfallBehaviorGates.canStartBloodHarvest(mobPatch, series);
            default -> HeroNightfallBehaviorGates.canStartSkillSeries(mobPatch, series);
        };
    }

    @Nullable
    private static CombatBehaviors.Builder<HumanoidMobPatch<?>> buildCrimsonMoon(HeroNightfallProfile profile) {
        if (profile.skillSeries().size() < 2) {
            return buildDefault(profile);
        }

        CombatBehaviors.Builder<HumanoidMobPatch<?>> builder = CombatBehaviors.builder();
        double comboRange = Math.max(3.8D, profile.attackRadius() + 0.6D);
        HeroNightfallSkillSeries harvest = profile.skillSeries().get(0);
        HeroNightfallSkillSeries scarletEnd = profile.skillSeries().get(1);

        CombatBehaviors.BehaviorSeries.Builder<HumanoidMobPatch<?>> comboSeries = CombatBehaviors.BehaviorSeries.<HumanoidMobPatch<?>>builder()
                .weight(100.0F)
                .cooldown(3)
                .canBeInterrupted(false)
                .looping(false);
        boolean hasComboAnimation = false;

        for (int comboIndex = 0; comboIndex < profile.comboAnimations().size(); comboIndex++) {
            final int behaviorComboIndex = comboIndex;
            HeroNightfallAnimationField animationField = profile.comboAnimations().get(comboIndex);
            AnimationAccessor<? extends StaticAnimation> animation = animationField.resolve();
            if (!isPlayableAttackAnimation(animation)) {
                continue;
            }

            comboSeries.nextBehavior(createAnimationBehavior(animation, resolveComboActionState(behaviorComboIndex), true)
                    .custom(mobPatch -> isPlayableAttackAnimation(animation)
                            && (behaviorComboIndex == 0
                            ? HeroNightfallBehaviorGates.canStartCrimsonMoonCombo(mobPatch, profile, behaviorComboIndex, comboRange)
                            : HeroNightfallBehaviorGates.canContinueCrimsonMoonCombo(mobPatch, profile, behaviorComboIndex, comboRange))));
            hasComboAnimation = true;
        }

        if (!hasComboAnimation) {
            return buildDefault(profile);
        }

        builder.newBehaviorSeries(comboSeries);

        if (isImpactfulCrimsonMoonSkillUnsafe()) {
            for (int skillIndex = 2; skillIndex < profile.skillSeries().size(); skillIndex++) {
                appendSkillSeries(builder, profile.skillSeries().get(skillIndex));
            }
            return builder;
        }

        CombatBehaviors.BehaviorSeries.Builder<HumanoidMobPatch<?>> harvestSeries = CombatBehaviors.BehaviorSeries.<HumanoidMobPatch<?>>builder()
                .weight(harvest.weight())
                .cooldown(harvest.cooldown())
                .canBeInterrupted(false)
                .looping(false);
        boolean hasHarvestAnimation = false;

        for (HeroNightfallAnimationField animationField : harvest.animations()) {
            AnimationAccessor<? extends StaticAnimation> animation = animationField.resolve();
            if (!isPlayableAttackAnimation(animation)) {
                continue;
            }

            harvestSeries.nextBehavior(createAnimationBehavior(animation, harvest.actionState(), false)
                    .custom(mobPatch -> isPlayableAttackAnimation(animation) && HeroNightfallBehaviorGates.canStartCrimsonMoonHarvest(mobPatch, harvest))
                    .randomChance(harvest.chance()));
            hasHarvestAnimation = true;
        }

        if (!hasHarvestAnimation) {
            return buildDefault(profile);
        }

        builder.newBehaviorSeries(harvestSeries);

        CombatBehaviors.BehaviorSeries.Builder<HumanoidMobPatch<?>> releaseSeries = CombatBehaviors.BehaviorSeries.<HumanoidMobPatch<?>>builder()
                .weight(scarletEnd.weight())
                .cooldown(scarletEnd.cooldown())
                .canBeInterrupted(false)
                .looping(false);
        boolean hasReleaseAnimation = false;

        for (HeroNightfallAnimationField animationField : scarletEnd.animations()) {
            AnimationAccessor<? extends StaticAnimation> animation = animationField.resolve();
            if (!isPlayableAttackAnimation(animation)) {
                continue;
            }

            releaseSeries.nextBehavior(createAnimationBehavior(animation, scarletEnd.actionState(), false)
                    .custom(mobPatch -> isPlayableAttackAnimation(animation) && HeroNightfallBehaviorGates.canStartCrimsonMoonRelease(mobPatch, scarletEnd)));
            hasReleaseAnimation = true;
        }

        if (!hasReleaseAnimation) {
            return buildDefault(profile);
        }

        builder.newBehaviorSeries(releaseSeries);
        for (int skillIndex = 2; skillIndex < profile.skillSeries().size(); skillIndex++) {
            appendSkillSeries(builder, profile.skillSeries().get(skillIndex));
        }
        return builder;
    }

    private static boolean isImpactfulCrimsonMoonSkillUnsafe() {
        return ModList.get().isLoaded(IMPACTFUL_MOD_ID);
    }

    private static CombatBehaviors.Behavior.Builder<HumanoidMobPatch<?>> createAnimationBehavior(AnimationAccessor<? extends StaticAnimation> animation,
                                                                                                 int actionState,
                                                                                                 boolean swingMainHand) {
        return createAnimationBehavior(animation, actionState, swingMainHand, false);
    }

    private static CombatBehaviors.Behavior.Builder<HumanoidMobPatch<?>> createAnimationBehavior(AnimationAccessor<? extends StaticAnimation> animation,
                                                                                                 int actionState,
                                                                                                 boolean swingMainHand,
                                                                                                 boolean launchAirAttack) {
        return CombatBehaviors.Behavior.<HumanoidMobPatch<?>>builder().behavior(mobPatch -> {
            if (!isPlayableAttackAnimation(animation)) {
                return;
            }

            if (mobPatch instanceof HeroEpicFightPatch heroPatch) {
                HeroEntity hero = heroPatch.getOriginal();
                if (hero != null && launchAirAttack) {
                    launchHeroAirAttack(hero);
                }
                if (hero != null && actionState >= 0) {
                    hero.beginBattleAction(actionState);
                }
                if (hero != null && swingMainHand) {
                    hero.swing(InteractionHand.MAIN_HAND);
                }
            }

            mobPatch.playAnimationSynchronized(animation, 0.0F);
        });
    }

    private static boolean isPlayableAttackAnimation(@Nullable AnimationAccessor<? extends StaticAnimation> animation) {
        return animation != null && !animation.isEmpty();
    }

    private static void launchHeroAirAttack(HeroEntity hero) {
        if (hero.isFloating()) {
            // 起飞后的空中攻击：保持浮空悬停中向目标前压，让空中连段在悬浮中衔接
            // （落回地面由 HeroEpicFightChaseGoal 的飞行追击回退负责，不在空中攻击里强制落地）
            Vec3 currentMovement = hero.getDeltaMovement();
            LivingEntity target = hero.getTarget();
            if (target != null) {
                double dx = target.getX() - hero.getX();
                double dz = target.getZ() - hero.getZ();
                double horizontalDistance = Math.sqrt(dx * dx + dz * dz);
                if (horizontalDistance > 1.0E-4D) {
                    hero.setDeltaMovement(dx / horizontalDistance * AIR_ATTACK_FORWARD_SPEED,
                            currentMovement.y,
                            dz / horizontalDistance * AIR_ATTACK_FORWARD_SPEED);
                    hero.hasImpulse = true;
                }
            }
            return;
        }

        hero.setFloating(false);
        hero.setNoGravity(false);

        Vec3 currentMovement = hero.getDeltaMovement();
        double forwardX = currentMovement.x;
        double forwardZ = currentMovement.z;
        LivingEntity target = hero.getTarget();
        if (target != null) {
            double dx = target.getX() - hero.getX();
            double dz = target.getZ() - hero.getZ();
            double horizontalDistance = Math.sqrt(dx * dx + dz * dz);
            if (horizontalDistance > 1.0E-4D) {
                forwardX = dx / horizontalDistance * AIR_ATTACK_FORWARD_SPEED;
                forwardZ = dz / horizontalDistance * AIR_ATTACK_FORWARD_SPEED;
            }
        }

        double jumpY = hero.onGround() ? AIR_ATTACK_JUMP_Y : Math.max(currentMovement.y, AIR_ATTACK_JUMP_Y * 0.55D);
        hero.setDeltaMovement(forwardX, jumpY, forwardZ);
        hero.hasImpulse = true;
    }

    private static int resolveComboActionState(int comboIndex) {
        return comboIndex % 2 == 0 ? HeroEntity.BATTLE_ACTION_LIGHT_COMBO_1 : HeroEntity.BATTLE_ACTION_LIGHT_COMBO_2;
    }
}
