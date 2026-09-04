package com.whitecloud233.herobrine_companion.entity.ai.learning.state;

import com.whitecloud233.herobrine_companion.entity.HeroEntity;
import com.whitecloud233.herobrine_companion.entity.ai.learning.SimpleNeuralNetwork;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public final class HeroMindStateRegistry {

    private static final String GLITCH_BOUNCE_LOCK_KEY = "MindGlitchBounceLock";
    private static final int GLITCH_BOUNCE_LOCK_TICKS = 2400;

    private static final List<HeroMindStateDefinition> DEFINITIONS = List.of(
            new ReminiscingStateDefinition(),
            new MaintainerStateDefinition(),
            new GlitchLordStateDefinition(),
            new MonsterKingStateDefinition(),
            new JudgeStateDefinition(),
            new ProtectorStateDefinition(),
            new PranksterStateDefinition(),
            new ObserverStateDefinition()
    );

    private static final Map<SimpleNeuralNetwork.MindState, HeroMindStateDefinition> BY_STATE =
            new EnumMap<>(SimpleNeuralNetwork.MindState.class);

    static {
        for (HeroMindStateDefinition definition : DEFINITIONS) {
            BY_STATE.put(definition.state(), definition);
        }
    }

    private HeroMindStateRegistry() {
    }

    public static void reconcileContextualExit(HeroEntity hero) {
        SimpleNeuralNetwork.MindState state = hero.getMindState();
        if (state == SimpleNeuralNetwork.MindState.MAINTAINER
                && hero.getPersistentData().getInt("MindMaintainerNoTaskTicks") >= 600) {
            hero.getHeroBrain().forceState(SimpleNeuralNetwork.MindState.OBSERVER);
            hero.setMindState(SimpleNeuralNetwork.MindState.OBSERVER);
            return;
        }
        if (state == SimpleNeuralNetwork.MindState.GLITCH_LORD
                && hero.getPersistentData().getInt("MindGlitchNoContactTicks") >= 4800) {
            hero.getHeroBrain().forceState(SimpleNeuralNetwork.MindState.OBSERVER);
            hero.setMindState(SimpleNeuralNetwork.MindState.OBSERVER);
            lockGlitchReentry(hero);
        }
    }

    public static SimpleNeuralNetwork.MindState resolve(
            HeroMindStateSnapshot snapshot,
            SimpleNeuralNetwork.MindState currentState,
            int stateAgeTicks,
            @Nullable HeroEntity hero
    ) {
        boolean glitchBlocked = isGlitchEntryLocked(hero);

        HeroMindStateDefinition currentDefinition = BY_STATE.get(currentState);
        if (currentDefinition != null && stateAgeTicks < currentDefinition.minDwellTicks()) {
            return currentState;
        }

        if (currentDefinition != null) {
            SimpleNeuralNetwork.MindState exitTarget = currentDefinition.shouldExit(snapshot, hero);
            if (exitTarget == null) {
                // 幽灵掉落物/故障感知会把 metaScore 抬高；高 meta 允许任何状态进入故障之主。
                if (snapshot.metaScore() >= 0.30f
                        && currentState != SimpleNeuralNetwork.MindState.GLITCH_LORD
                        && !glitchBlocked) {
                    return SimpleNeuralNetwork.MindState.GLITCH_LORD;
                }
                return currentState;
            }
            HeroMindStateDefinition targetDefinition = BY_STATE.get(exitTarget);
            if (targetDefinition != null && targetDefinition.shouldEnter(snapshot)
                    && !(exitTarget == SimpleNeuralNetwork.MindState.GLITCH_LORD && glitchBlocked)) {
                lockGlitchReentryIfLeaving(hero, currentState);
                return exitTarget;
            }
        }

        for (HeroMindStateDefinition definition : DEFINITIONS) {
            if (definition.state() != currentState && definition.shouldEnter(snapshot)
                    && !(definition.state() == SimpleNeuralNetwork.MindState.GLITCH_LORD && glitchBlocked)) {
                lockGlitchReentryIfLeaving(hero, currentState);
                return definition.state();
            }
        }
        return currentState;
    }

    private static boolean isGlitchEntryLocked(@Nullable HeroEntity hero) {
        return hero != null && hero.level().getGameTime() < hero.getPersistentData().getLong(GLITCH_BOUNCE_LOCK_KEY);
    }

    private static void lockGlitchReentryIfLeaving(HeroEntity hero, SimpleNeuralNetwork.MindState currentState) {
        if (hero != null && currentState == SimpleNeuralNetwork.MindState.GLITCH_LORD) {
            lockGlitchReentry(hero);
        }
    }

    private static void lockGlitchReentry(HeroEntity hero) {
        hero.getPersistentData().putLong(GLITCH_BOUNCE_LOCK_KEY, hero.level().getGameTime() + GLITCH_BOUNCE_LOCK_TICKS);
    }

    public static void tickServer(HeroEntity hero) {
        reconcileContextualExit(hero);
        HeroMindStateDefinition definition = BY_STATE.get(hero.getMindState());
        if (definition == null) {
            return;
        }

        if (!HeroStateBehaviorSupport.isRuntimeStateBlockingMindSupport(hero)) {
            definition.tickServerSupport(hero);
        }

        if (!HeroStateBehaviorSupport.isRuntimeStateBlockingMindMovement(hero)) {
            definition.tickServerMovement(hero);
        }
    }

    /**
     * 每 tick 续瞄状态系统最近记录的注视目标（10°/tick，与原版 LookControl 回摆速度一致），
     * 消除"状态 tick 一次性 setLookAt（2 tick 生效）→ 之后 8 tick 摆回身体"的锯齿摆动。
     * <p>
     * 战斗/陪伴/邀请/骑乘等专用系统运行时跳过，避免与其自身的头部控制互相覆盖；
     * 目标超过 15 tick 未被状态刷新则视为过期，让头部平滑回正。
     */
    public static void tickServerLook(HeroEntity hero) {
        if (HeroStateBehaviorSupport.isRuntimeStateBlockingMindMovement(hero)) {
            return;
        }
        Vec3 target = hero.mindLookTarget;
        if (target == null) {
            return;
        }
        if (hero.tickCount - hero.mindLookTargetTick > 15) {
            hero.mindLookTarget = null;
            hero.mindLookTargetTick = 0;
            return;
        }
        hero.getLookControl().setLookAt(target.x, target.y, target.z, 10.0F, 10.0F);
    }

    public static void tickClientAmbient(HeroEntity hero) {
        HeroMindStateDefinition definition = BY_STATE.get(hero.getMindState());
        if (definition != null) {
            definition.tickClientAmbient(hero);
        }
    }
}
