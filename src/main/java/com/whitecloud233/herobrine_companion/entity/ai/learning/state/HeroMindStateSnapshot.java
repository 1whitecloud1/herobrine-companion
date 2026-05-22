package com.whitecloud233.herobrine_companion.entity.ai.learning.state;

public record HeroMindStateSnapshot(
        float violenceScore,
        float creativityScore,
        float explorationScore,
        float failureScore,
        float directAttackScore,
        float entropyScore,
        float metaScore,
        float nostalgiaScore,
        float monsterEmpathyScore,
        float respectWeight,
        float annoyanceWeight,
        float curiosityWeight,
        float arroganceWeight,
        float sorrowWeight,
        float stabilityObsession,
        float prankFeedback
) {
}
