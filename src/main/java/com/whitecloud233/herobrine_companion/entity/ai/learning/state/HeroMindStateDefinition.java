package com.whitecloud233.herobrine_companion.entity.ai.learning.state;

import com.whitecloud233.herobrine_companion.entity.HeroEntity;
import com.whitecloud233.herobrine_companion.entity.ai.learning.SimpleNeuralNetwork;

public interface HeroMindStateDefinition {

    SimpleNeuralNetwork.MindState state();

    boolean shouldEnter(HeroMindStateSnapshot snapshot);

    default SimpleNeuralNetwork.MindState shouldExit(HeroMindStateSnapshot snapshot, HeroEntity hero) {
        return null;
    }

    default int minDwellTicks() {
        return 0;
    }
    default void tickServerSupport(HeroEntity hero) {
    }

    default void tickServerMovement(HeroEntity hero) {
        tickServer(hero);
    }

    default void tickServer(HeroEntity hero) {
    }

    default void tickClientAmbient(HeroEntity hero) {
    }
}
