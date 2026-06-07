package com.whitecloud233.modid.herobrine_companion.entity.awakened;

public enum AwakenedMobRelationState {
    HOSTILE,
    WARY,
    CURIOUS,
    FAMILIAR,
    SUBMISSIVE;

    public static AwakenedMobRelationState fromScore(int score) {
        if (score <= -20) {
            return HOSTILE;
        }
        if (score < 10) {
            return WARY;
        }
        if (score < 30) {
            return CURIOUS;
        }
        if (score < 60) {
            return FAMILIAR;
        }
        return SUBMISSIVE;
    }

    public boolean allowsCeasefire() {
        return this != HOSTILE;
    }

    public boolean suppressesAggro() {
        return this == FAMILIAR || this == SUBMISSIVE;
    }
}
