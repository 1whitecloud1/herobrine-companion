package com.whitecloud233.herobrine_companion.compat.epicfight;

import com.mojang.logging.LogUtils;
import com.whitecloud233.herobrine_companion.entity.HeroEntity;
import com.whitecloud233.herobrine_companion.item.PoemOfTheEndItem;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import yesman.epicfight.api.animation.AnimationManager;
import yesman.epicfight.api.animation.AnimationPlayer;
import yesman.epicfight.api.animation.Animator;
import yesman.epicfight.api.animation.LivingMotions;
import yesman.epicfight.api.animation.types.AttackAnimation;
import yesman.epicfight.api.animation.types.StaticAnimation;
import yesman.epicfight.api.asset.AssetAccessor;
import yesman.epicfight.world.capabilities.entitypatch.HumanoidMobPatch;
import yesman.epicfight.world.entity.ai.goal.CombatBehaviors;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Mode-specific native scythe combos, sprint attacks and air attacks for Hero. */
public final class HeroScytheComboBehaviors {

    private static final Logger LOGGER = LogUtils.getLogger();

    public static final double ATTACK_RADIUS = 3.6D;
    private static final int COMBO_COOLDOWN = 20;
    private static volatile List<UnityScytheAnimations.MotionSet> motionSets = List.of();
    private static volatile MediaPipeScytheAnimations.MotionSet legacyMotionSet;

    /** 一次性诊断用:每个原因只打一次,避免热路径刷屏。 */
    private static final Set<String> LOGGED = ConcurrentHashMap.newKeySet();
    private static volatile long lastStartLog;
    private static volatile long lastDiagLog;

    private HeroScytheComboBehaviors() {
    }

    /** Register through the mod's existing builder, independently of Nightfall. */
    static void registerAnimation(AnimationManager.AnimationBuilder builder) {
        if (builder == null) {
            LOGGER.warn("[HeroScythe] animation builder was null, scythe combo not registered");
            return;
        }
        try {
            legacyMotionSet = MediaPipeScytheAnimations.registerHero(builder);
            motionSets = UnityScytheAnimations.registerHero(builder);
            LOGGER.info("[HeroScythe] registered {} native scythe modes, each with combo, dash and air attacks", motionSets.size());
        } catch (Throwable throwable) {
            LOGGER.error("[HeroScythe] failed to register the native scythe modes", throwable);
        }
    }

    static void applyLivingAnimations(ItemStack stack,
            java.util.Map<yesman.epicfight.api.animation.LivingMotion, AssetAccessor<? extends StaticAnimation>> motions) {
        UnityScytheAnimations.MotionSet set = motionSetFor(stack);
        if (!isSupported(stack) || set == null) {
            return;
        }
        motions.put(LivingMotions.IDLE, set.ready());
        motions.put(LivingMotions.WALK, set.hold());
        motions.put(LivingMotions.RUN, set.hold());
        motions.put(LivingMotions.CHASE, set.hold());
        motions.put(LivingMotions.SNEAK, set.hold());
    }

    static UnityScytheAnimations.MotionSet motionSetFor(@Nullable ItemStack stack) {
        if (!isSupported(stack) || motionSets.size() != UnityScytheAnimations.MODE_COUNT) return null;
        int mode = ((PoemOfTheEndItem) stack.getItem()).getMode(stack);
        return motionSetForMode(mode);
    }

    static UnityScytheAnimations.MotionSet motionSetForMode(int mode) {
        return motionSets.get(mode >= 0 && mode < motionSets.size() ? mode : 0);
    }

    /** 是否为本模组镰刀(终末之诗)。 */
    public static boolean isSupported(@Nullable ItemStack stack) {
        return stack != null && !stack.isEmpty() && stack.getItem() instanceof PoemOfTheEndItem;
    }

    /** 连段攻击半径;非本模组镰刀返回 fallback。 */
    public static double getAttackRadius(@Nullable ItemStack stack, double fallback) {
        return isSupported(stack) ? ATTACK_RADIUS : fallback;
    }

    /**
     * 为战斗模式、手持终末之诗的英雄构建动捕连斩行为。
     *
     * <p>各段位于同一个 {@link CombatBehaviors.BehaviorSeries},由 Epic Fight 的
     * {@code AnimatedAttackGoal} 逐段衔接;每段的动作状态/连段步数走本模组与 WOM 共用的
     * {@link HeroEpicFightPatch#createScytheComboAttackBehavior} 跟踪管线。</p>
     */
    @Nullable
    static CombatBehaviors.Builder<HumanoidMobPatch<?>> build(@Nullable HeroEpicFightPatch patch, @Nullable ItemStack stack) {
        if (patch == null || !isSupported(stack)) {
            return null;
        }
        UnityScytheAnimations.MotionSet set = motionSetFor(stack);
        if (set == null) {
            onceLog("no-animation", "[HeroScythe] held {} but no combo segment is registered - falling back", stack.getItem());
            return null;
        }
        List<AnimationManager.AnimationAccessor<? extends AttackAnimation>> combo = set.combo();

        onceLog("built", "[HeroScythe] combo behaviors built for {} ({} segments, radius={})",
                stack.getItem(), combo.size(), ATTACK_RADIUS);

        CombatBehaviors.BehaviorSeries.Builder<HumanoidMobPatch<?>> series =
                CombatBehaviors.BehaviorSeries.<HumanoidMobPatch<?>>builder()
                        .weight(100.0F)
                        .cooldown(COMBO_COOLDOWN)
                        .canBeInterrupted(false)
                        .looping(false);
        int size = combo.size();
        for (int i = 0; i < size; i++) {
            CombatBehaviors.Behavior.Builder<HumanoidMobPatch<?>> behavior =
                    patch.createScytheComboAttackBehavior(combo.get(i), i, size);
            behavior.custom(i == 0 ? HeroScytheComboBehaviors::canStart : HeroScytheComboBehaviors::canContinue);
            series.nextBehavior(behavior);
        }

        CombatBehaviors.Builder<HumanoidMobPatch<?>> builder = CombatBehaviors.builder();
        builder.newBehaviorSeries(series);
        builder.newBehaviorSeries(CombatBehaviors.BehaviorSeries.<HumanoidMobPatch<?>>builder()
                .weight(180F).cooldown(40).canBeInterrupted(false).looping(false)
                .nextBehavior(patch.createScytheSpecialAttackBehavior(set.dash()).custom(HeroScytheComboBehaviors::canDash)));
        builder.newBehaviorSeries(CombatBehaviors.BehaviorSeries.<HumanoidMobPatch<?>>builder()
                .weight(240F).cooldown(20).canBeInterrupted(false).looping(false)
                .nextBehavior(patch.createScytheSpecialAttackBehavior(set.air()).custom(HeroScytheComboBehaviors::canAir)));
        return builder;
    }

    private static boolean canStart(HumanoidMobPatch<?> mobPatch) {
        if (!gate(mobPatch)) {
            return false;
        }
        HeroEntity hero = ((HeroEpicFightPatch) mobPatch).getOriginal();
        if (!hero.onGround() || hero.isFloating() || canDash(mobPatch)) return false;
        LivingEntity target = hero.getTarget();
        double reach = ATTACK_RADIUS + 2.0D;
        if (hero.distanceToSqr(target) > reach * reach) {
            onceLog("gate-range", "[HeroScythe] gate: target out of reach (reach={})", String.format("%.2f", reach));
            return false;
        }
        long now = System.currentTimeMillis();
        if (now - lastStartLog > 2000L) {
            lastStartLog = now;
            LOGGER.info("[HeroScythe] combo start");
        }
        return true;
    }

    private static boolean canDash(HumanoidMobPatch<?> mobPatch) {
        if (!gate(mobPatch)) return false;
        HeroEntity hero = ((HeroEpicFightPatch) mobPatch).getOriginal();
        double distance = hero.distanceToSqr(hero.getTarget());
        return hero.onGround() && !hero.isFloating()
                && (hero.isSprinting() || hero.getDeltaMovement().horizontalDistanceSqr() > .015D)
                && distance >= 2.2D * 2.2D && distance <= 11D * 11D;
    }

    private static boolean canAir(HumanoidMobPatch<?> mobPatch) {
        if (!gate(mobPatch)) return false;
        HeroEntity hero = ((HeroEpicFightPatch) mobPatch).getOriginal();
        double reach = ATTACK_RADIUS + 2D;
        return !hero.onGround() && !hero.isFloating() && hero.distanceToSqr(hero.getTarget()) <= reach * reach;
    }

    /** 后续段:目标还在、视线还在就继续连,不再要求距离回到起手范围(动画自带前冲位移)。 */
    private static boolean canContinue(HumanoidMobPatch<?> mobPatch) {
        return gate(mobPatch);
    }

    private static boolean gate(HumanoidMobPatch<?> mobPatch) {
        if (!(mobPatch instanceof HeroEpicFightPatch heroPatch)) {
            onceLog("gate-patch", "[HeroScythe] gate: patch is {}", mobPatch);
            return false;
        }
        HeroEntity hero = heroPatch.getOriginal();
        if (hero == null) {
            return false;
        }
        if (!hero.isBattleModeActive()) {
            onceLog("gate-battle", "[HeroScythe] gate: hero {} is NOT in battle mode", hero.getId());
            return false;
        }
        if (hero.isBattleHoldAction() || hero.isBattleReleaseAction()) {
            onceLog("gate-hold", "[HeroScythe] gate: hero in heavy hold/release action");
            return false;
        }
        LivingEntity target = hero.getTarget();
        if (target == null || !target.isAlive()) {
            onceLog("gate-target", "[HeroScythe] gate: no living target");
            return false;
        }
        if (!hero.hasLineOfSight(target)) {
            onceLog("gate-los", "[HeroScythe] gate: no line of sight to {}", target);
            return false;
        }
        return true;
    }

    /**
     * 客户端排障(每秒最多一条):说明"这段动画在客户端到底播没播、EF 认为它多长"。
     */
    static void clientDiag(@Nullable HeroEpicFightPatch patch, @Nullable HeroEntity hero) {
        if (patch == null || hero == null || !isSupported(hero.getMainHandItem())) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastDiagLog < 1000L) {
            return;
        }
        lastDiagLog = now;

        Animator animator = patch.getAnimator();
        UnityScytheAnimations.MotionSet set = motionSetFor(hero.getMainHandItem());
        List<AnimationManager.AnimationAccessor<? extends AttackAnimation>> segments = set == null ? List.of() : set.attacks();
        String state = "no-animator";
        if (animator != null) {
            state = "idle-playing";     // 动画器在播(但可能不是我们的段)
            for (AnimationManager.AnimationAccessor<? extends AttackAnimation> accessor : segments) {
                AnimationPlayer player = animator.getPlayerFor(accessor);
                if (player != null && !player.isEmpty()) {
                    AttackAnimation animation = accessor.get();
                    state = String.format("SEG elapsed=%.2f total=%.2f", player.getElapsedTime(),
                            animation != null ? animation.getTotalTime() : -1.0F);
                    break;
                }
            }
        }
        LOGGER.info("[HeroScythe-diag] battle={} efPose={} animator={} {} registeredSegments={}",
                hero.isBattleModeActive(),
                HeroEpicFightCompat.shouldUseEpicFightPose(hero),
                animator != null,
                state,
                segments.size());
    }

    private static void onceLog(String key, String format, Object... args) {
        if (LOGGED.add(key)) {
            LOGGER.info(format, args);
        }
    }
}
