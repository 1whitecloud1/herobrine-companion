package com.whitecloud233.modid.herobrine_companion.client.service;

import com.whitecloud233.modid.herobrine_companion.entity.ai.learning.SimpleNeuralNetwork.MindState;

import java.util.HashSet;
import java.util.Set;

/** 无模型调用，约束逐轮状态提示的体积与瞬时性。 */
public final class HeroSpeechStyleChecks {
    private static int checks;

    public static void main(String[] args) {
        check(HeroSpeechStyle.promptFor(null).isEmpty(), "An unavailable state must add no tokens");
        Set<String> prompts = new HashSet<>();
        int maxTokens = 0;
        for (MindState state : MindState.values()) {
            String prompt = HeroSpeechStyle.promptFor(state);
            check(!prompt.isBlank() && prompts.add(prompt), "Each mind state needs a distinct tone: " + state);
            int tokens = AIHistoryTokenSupport.estimateTextTokens(prompt);
            maxTokens = Math.max(maxTokens, tokens);
            check(tokens <= 64 && prompt.length() <= 256, "State prompt exceeds the small per-request budget: " + state);
            check(prompt.indexOf("[CURRENT TONE]") == prompt.lastIndexOf("[CURRENT TONE]"), "Only one active tone per request");
            check(prompt.contains("keep persona, language and tool rules") && prompt.contains("brief"),
                    "Tone must preserve identity and permissions without encouraging longer replies");
        }
        String before = HeroSpeechStyle.promptFor(MindState.PROTECTOR);
        HeroSpeechStyle.promptFor(MindState.JUDGE);
        check(HeroSpeechStyle.promptFor(MindState.PROTECTOR).equals(before), "State changes must not accumulate old instructions");
        check(!HeroSpeechStyle.promptFor(MindState.JUDGE).contains("reassuring"), "A new state replaces the previous tone");
        System.out.println("HeroSpeechStyleChecks: " + checks + " checks passed; max estimated added tokens=" + maxTokens);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        checks++;
    }
}
