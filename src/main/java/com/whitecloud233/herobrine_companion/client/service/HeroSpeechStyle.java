package com.whitecloud233.herobrine_companion.client.service;

import com.whitecloud233.herobrine_companion.entity.ai.learning.SimpleNeuralNetwork.MindState;

/** 本地映射当前心境；每轮只发送一种语气，不发送整张状态表或数值。 */
final class HeroSpeechStyle {
    private HeroSpeechStyle() {}

    static String promptFor(MindState state) {
        if (state == null) {
            return "";
        }
        String tone = switch (state) {
            case OBSERVER -> "calm, watchful, distant; understated mystery";
            case PROTECTOR -> "steady, reassuring, quietly protective";
            case JUDGE -> "stern, measured, disapproving; no gratuitous insults";
            case PRANKSTER -> "mischievous, dry teasing, playful; never cruel";
            case MAINTAINER -> "precise, focused, restrained; terse diagnostic phrasing";
            case GLITCH_LORD -> "eerie, slightly fragmented but readable; no garbled text";
            case MONSTER_KING -> "regal, commanding; compassionate toward monsters";
            case REMINISCING -> "wistful, subdued, reflective; no invented memories";
        };
        return "\n[CURRENT TONE]: " + tone
                + ". Use for this reply; keep persona, language and tool rules."
                + " Be brief unless detail is needed. Don't name the state.\n";
    }
}
