package com.whitecloud233.modid.herobrine_companion.entity.companion;

import java.util.UUID;

import static com.whitecloud233.modid.herobrine_companion.entity.companion.CompanionModePolicy.Result;
import static com.whitecloud233.modid.herobrine_companion.entity.companion.CompanionModePolicy.State;

public final class CompanionModeChecks {
    private static final UUID OWNER = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID OTHER = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static int checks;

    public static void main(String[] args) {
        verify(Result.NO_REQUESTER, null, new State(null, 100, false, true, false, false), true);
        verify(Result.NOT_OWNER, OTHER, new State(OWNER, 100, false, true, false, false), true);
        verify(Result.NOT_OWNER, OTHER, new State(OWNER, 100, true, true, false, false), false);
        verify(Result.TRUST_TOO_LOW, OWNER, new State(OWNER, 49, false, true, false, false), true);
        verify(Result.CHANGED, OWNER, new State(OWNER, 50, false, true, false, false), true);
        verify(Result.CHANGED, OWNER, new State(null, 50, false, true, false, false), true);
        verify(Result.CHANGED, OWNER, new State(null, 50, true, true, false, false), true);
        verify(Result.UNCHANGED, OWNER, new State(OWNER, 100, true, true, false, false), true);
        verify(Result.UNCHANGED, OWNER, new State(OWNER, 0, false, true, false, false), false);
        verify(Result.UNCHANGED, OWNER, new State(null, 0, false, true, false, false), false);
        verify(Result.UNAVAILABLE, OWNER, new State(OWNER, 100, false, false, false, false), true);
        verify(Result.UNAVAILABLE, OWNER, new State(OWNER, 100, true, false, false, false), false);
        verify(Result.UNAVAILABLE, OWNER, new State(OWNER, 100, false, true, true, false), true);
        verify(Result.CONFLICTING_HERO, OWNER, new State(null, 100, false, true, false, true), true);

        for (int trust : new int[]{-10, 0, 49, 50, 100}) {
            for (boolean restricted : new boolean[]{false, true}) {
                for (boolean conflict : new boolean[]{false, true}) {
                    verify(Result.CHANGED, OWNER, new State(OWNER, trust, true, true, restricted, conflict), false);
                    verify(Result.NOT_OWNER, OTHER, new State(OWNER, trust, true, true, restricted, conflict), false);
                }
            }
        }
        System.out.println("CompanionModeChecks: " + checks + " checks passed");
    }

    private static void verify(Result expected, UUID actor, State state, boolean following) {
        Result actual = CompanionModePolicy.evaluate(actor, state, following);
        if (actual != expected) throw new AssertionError(state + ": expected " + expected + " but got " + actual);
        if (actual.successful() != (actual == Result.CHANGED || actual == Result.UNCHANGED)) {
            throw new AssertionError("Invalid success classification: " + actual);
        }
        checks++;
    }
}
