package com.whitecloud233.modid.herobrine_companion.entity.ai.learning.state;

import com.whitecloud233.modid.herobrine_companion.entity.HeroEntity;
import com.whitecloud233.modid.herobrine_companion.entity.ai.learning.SimpleNeuralNetwork;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public final class HeroMindStateRegistry {

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
        }
    }

    public static SimpleNeuralNetwork.MindState resolve(
            HeroMindStateSnapshot snapshot,
            SimpleNeuralNetwork.MindState currentState,
            int stateAgeTicks
    ) {
        HeroMindStateDefinition currentDefinition = BY_STATE.get(currentState);
        if (currentDefinition != null && stateAgeTicks < currentDefinition.minDwellTicks()) {
            return currentState;
        }

        if (currentDefinition != null) {
            SimpleNeuralNetwork.MindState exitTarget = currentDefinition.shouldExit(snapshot, null);
            if (exitTarget == null) {
                return currentState;
            }
            HeroMindStateDefinition targetDefinition = BY_STATE.get(exitTarget);
            if (targetDefinition != null && targetDefinition.shouldEnter(snapshot)) {
                return exitTarget;
            }
        }

        for (HeroMindStateDefinition definition : DEFINITIONS) {
            if (definition.state() != currentState && definition.shouldEnter(snapshot)) {
                return definition.state();
            }
        }
        return currentState;
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

    public static void tickClientAmbient(HeroEntity hero) {
        HeroMindStateDefinition definition = BY_STATE.get(hero.getMindState());
        if (definition != null) {
            definition.tickClientAmbient(hero);
        }
    }
}
