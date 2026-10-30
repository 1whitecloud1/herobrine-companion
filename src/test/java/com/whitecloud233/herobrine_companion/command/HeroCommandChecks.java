package com.whitecloud233.herobrine_companion.command;

import com.whitecloud233.herobrine_companion.entity.ai.learning.SimpleNeuralNetwork;
import com.whitecloud233.herobrine_companion.entity.ai.learning.SimpleNeuralNetwork.MindState;
import com.whitecloud233.herobrine_companion.entity.ai.learning.state.HeroMindStatePresets;
import net.minecraft.nbt.CompoundTag;

/** Regression coverage for command state names, learned memory and saved state stability. */
public final class HeroCommandChecks {
    public static void main(String[] args) {
        for (MindState state : MindState.values()) {
            require(HeroCommandStates.parse(state.name()) == state, "English state: " + state);
            require(HeroCommandStates.parse(Integer.toString(state.ordinal())) == state, "Numeric state: " + state);
            require(HeroCommandStates.parse(HeroCommandStates.chineseNames().get(state.ordinal())) == state,
                    "Chinese state: " + state);
            SimpleNeuralNetwork network = new SimpleNeuralNetwork();
            network.feedback("repair", .7f);
            CompoundTag before = new CompoundTag();
            network.save(before);
            HeroMindStatePresets.apply(network, state, 1000);
            CompoundTag saved = new CompoundTag();
            network.save(saved);
            require(saved.getCompound("Memory").equals(before.getCompound("Memory")), "Lost learned feedback");
            require(saved.getLong("StateEnteredTick") == 1000, "State dwell timer not reset");
            SimpleNeuralNetwork restored = new SimpleNeuralNetwork();
            restored.load(saved);
            require(restored.getCurrentState() == state, "State did not survive serialization");
        }
        require(HeroCommandStates.parse(" GLITCH-LORD ") == MindState.GLITCH_LORD, "Normalization");
        require(HeroCommandStates.parse("代码之神") == MindState.GLITCH_LORD, "Bedrock alias");
        require(HeroCommandStates.parse("monsterking") == MindState.MONSTER_KING, "Compact state alias");
        for (String invalid : new String[]{"-1", "8", "protector extra", "", "unknown"})
            require(HeroCommandStates.parse(invalid) == null, "Invalid state accepted: " + invalid);
        System.out.println("HeroCommandChecks: eight state presets, names, learned memory and persistence passed");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
