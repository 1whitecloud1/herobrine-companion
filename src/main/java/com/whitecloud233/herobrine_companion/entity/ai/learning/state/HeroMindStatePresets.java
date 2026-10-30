package com.whitecloud233.herobrine_companion.entity.ai.learning.state;

import com.whitecloud233.herobrine_companion.entity.ai.learning.SimpleNeuralNetwork;
import com.whitecloud233.herobrine_companion.entity.ai.learning.SimpleNeuralNetwork.MindState;
import net.minecraft.nbt.CompoundTag;

/** Command presets seed the existing brain without deleting learned action feedback. */
public final class HeroMindStatePresets {
    private static final String[] FIELDS = {"Violence", "DirectAttack", "Creativity", "Exploration",
            "Failure", "Entropy", "Meta", "Nostalgia", "MonsterEmpathy", "Respect", "Annoyance",
            "Curiosity", "Sorrow"};

    private HeroMindStatePresets() {}

    public static void apply(SimpleNeuralNetwork network, MindState state, long gameTime) {
        float[] values = switch (state) {
            case OBSERVER -> new float[]{0, 0, .25f, .25f, 0, 0, 0, 0, 0, .5f, 0, .45f, .1f};
            case PROTECTOR -> new float[]{0, 0, .95f, .95f, .05f, .1f, .1f, .05f, .05f, .85f, .05f, .65f, .1f};
            case JUDGE -> new float[]{.3f, .9f, .1f, .1f, .2f, .4f, .05f, .05f, .05f, .35f, .6f, .3f, .1f};
            case PRANKSTER -> new float[]{0, 0, .35f, .75f, .05f, .1f, .1f, .05f, .1f, .5f, .1f, .75f, .1f};
            case MAINTAINER -> new float[]{.05f, 0, .2f, .3f, .05f, .6f, .05f, .1f, .1f, .45f, .2f, .45f, .1f};
            case GLITCH_LORD -> new float[]{.05f, 0, .25f, .25f, .1f, .1f, .5f, .05f, .05f, .4f, .15f, .45f, .1f};
            case MONSTER_KING -> new float[]{.25f, 0, .1f, .15f, .05f, .05f, .05f, .05f, .7f, .35f, .3f, .3f, .1f};
            case REMINISCING -> new float[]{0, 0, .2f, .2f, .1f, .05f, .05f, .6f, .05f, .5f, .05f, .35f, .5f};
        };
        CompoundTag data = new CompoundTag();
        network.save(data);
        for (int i = 0; i < FIELDS.length; i++) data.putFloat(FIELDS[i], values[i]);
        network.load(data);
        network.forceState(state, gameTime);
    }
}
