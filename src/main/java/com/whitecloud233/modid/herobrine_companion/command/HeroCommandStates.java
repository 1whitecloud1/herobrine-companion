package com.whitecloud233.modid.herobrine_companion.command;

import com.whitecloud233.modid.herobrine_companion.entity.ai.learning.SimpleNeuralNetwork.MindState;
import java.util.List;
import java.util.Locale;

/** Names accepted by the Bedrock-compatible state command. */
public final class HeroCommandStates {
    private static final List<String> CHINESE = List.of("观察者", "守护者", "审判者", "恶作剧者",
            "维护者", "故障之主", "怪物之王", "追忆者");

    private HeroCommandStates() {}

    public static MindState parse(String input) {
        String name = input.trim().toLowerCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
        int index = CHINESE.indexOf(name);
        if (index >= 0) return MindState.values()[index];
        if (name.length() == 1 && name.charAt(0) >= '0' && name.charAt(0) <= '7') {
            return MindState.values()[name.charAt(0) - '0'];
        }
        name = switch (name) {
            case "glitchlord", "glitch", "代码之神", "故障主" -> "glitch_lord";
            case "monsterking", "monster" -> "monster_king";
            case "reminisce" -> "reminiscing";
            default -> name;
        };
        try {
            return MindState.valueOf(name.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    public static List<String> chineseNames() { return CHINESE; }
}
