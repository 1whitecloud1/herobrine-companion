package com.whitecloud233.herobrine_companion.entity.ai.learning;

import com.whitecloud233.herobrine_companion.entity.ai.learning.state.HeroMindStateRegistry;
import com.whitecloud233.herobrine_companion.entity.ai.learning.state.HeroMindStateSnapshot;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;

import java.util.HashMap;
import java.util.Map;

/**
 * 模拟 Herobrine 的核心逻辑处理单元。
 * 基于“深度学习”概念的模拟，包含输入层、隐藏层（性格权重）、输出层（状态）以及反馈机制。
 */
public class SimpleNeuralNetwork {

    private float violenceScore = 0.0f;
    private float creativityScore = 0.0f;
    private float explorationScore = 0.0f;
    private float failureScore = 0.0f;
    private float directAttackScore = 0.0f;

    private float entropyScore = 0.0f;
    private float metaScore = 0.0f;
    private float nostalgiaScore = 0.0f;
    private float monsterEmpathyScore = 0.0f;

    private float respectWeight = 0.5f;
    private float annoyanceWeight = 0.0f;
    private float curiosityWeight = 0.5f;

    private float arroganceWeight = 0.8f;
    private float sorrowWeight = 0.1f;
    private float stabilityObsession = 0.9f;

    private final Map<String, Float> actionFeedback = new HashMap<>();

    public enum MindState {
        OBSERVER,
        PROTECTOR,
        JUDGE,
        PRANKSTER,
        MAINTAINER,
        GLITCH_LORD,
        MONSTER_KING,
        REMINISCING
    }

    private MindState currentState = MindState.OBSERVER;
    private long lastUpdateTick = 0;
    private long stateEnteredTick = 0;

    // --- 输入处理 (Feed Forward) ---

    public void input(String inputType, float intensity) {
        switch (inputType) {
            case "VIOLENCE" -> {
                this.violenceScore = clamp(this.violenceScore + intensity);
            }
            case "DIRECT_ATTACK" -> {
                this.violenceScore = clamp(this.violenceScore + intensity);
                this.directAttackScore = clamp(this.directAttackScore + intensity * 1.5f);
            }
            case "CREATIVITY" -> this.creativityScore = clamp(this.creativityScore + intensity);
            case "EXPLORATION" -> this.explorationScore = clamp(this.explorationScore + intensity);
            case "FAILURE" -> {
                this.failureScore = clamp(this.failureScore + intensity);
                this.arroganceWeight = clamp(this.arroganceWeight + 0.05f);
            }
            case "ENTROPY" -> this.entropyScore = clamp(this.entropyScore + intensity);
            case "META" -> this.metaScore = clamp(this.metaScore + intensity);
            case "NOSTALGIA" -> {
                this.nostalgiaScore = clamp(this.nostalgiaScore + intensity);
                this.sorrowWeight = clamp(this.sorrowWeight + intensity * 0.5f);
            }
            case "MONSTER_INTEREST" -> this.monsterEmpathyScore = clamp(this.monsterEmpathyScore + intensity);
        }
    }
    
    // [新增] 专门用于 LoreFragment 的输入接口
    public void inputLoreFragment(String fragmentId) {
        this.respectWeight = clamp(this.respectWeight + 0.05f);
        this.nostalgiaScore = clamp(this.nostalgiaScore + 0.05f);

        try {
            int id = Integer.parseInt(fragmentId.replace("fragment_", ""));
            if (id <= 3) {
                this.sorrowWeight = clamp(this.sorrowWeight + 0.1f);
            } else if (id <= 7) {
                this.stabilityObsession = clamp(this.stabilityObsession + 0.1f);
                this.entropyScore = clamp(this.entropyScore - 0.1f);
            } else {
                this.metaScore = clamp(this.metaScore + 0.1f);
                this.arroganceWeight = clamp(this.arroganceWeight - 0.1f);
            }
        } catch (NumberFormatException e) {
            this.sorrowWeight = clamp(this.sorrowWeight + 0.05f);
        }
    }

    public void feedback(String action, float reward) {
        float current = actionFeedback.getOrDefault(action, 0.0f);
        actionFeedback.put(action, clamp(current + reward * 0.1f));
    }

    public void tick(long gameTime) {
        if (gameTime - lastUpdateTick < 100) return;
        lastUpdateTick = gameTime;

        decayInputs();
        updateWeights();
        determineState();
    }

    private void decayInputs() {
        // [修改] 记忆衰减：大幅降低衰减速度，让状态更持久
        // 之前的 0.02f 太快了，50次 tick (250秒) 就会完全归零
        // 现在改为 0.001f，需要 200次 tick (1000秒 = 16分钟) 才会归零
        float decayRate = 0.001f;

        this.violenceScore = Math.max(0.0f, this.violenceScore - decayRate);
        this.creativityScore = Math.max(0.0f, this.creativityScore - decayRate);
        this.explorationScore = Math.max(0.0f, this.explorationScore - decayRate);
        this.failureScore = Math.max(0.0f, this.failureScore - decayRate);
        this.directAttackScore = Math.max(0.0f, this.directAttackScore - 0.003f);

        this.entropyScore = Math.max(0.0f, this.entropyScore - 0.02f);
        this.metaScore = Math.max(0.0f, this.metaScore - 0.0001f);
        this.nostalgiaScore = Math.max(0.0f, this.nostalgiaScore - 0.005f);
        this.monsterEmpathyScore = Math.max(0.0f, this.monsterEmpathyScore - decayRate);
    }

    private void updateWeights() {
        float respectImpact = (this.creativityScore * 0.6f) + (this.explorationScore * 0.3f) + (this.metaScore * 0.4f);
        if (this.violenceScore > 0.7f) {
            respectImpact -= 0.1f;
        }
        if (this.failureScore > 0.2f && this.violenceScore < 0.4f) {
            respectImpact += 0.08f;
        }
        this.respectWeight = clamp(this.respectWeight * 0.8f + respectImpact * 0.2f);

        float violenceImpact = this.violenceScore * 1.2f;
        if (this.failureScore > 0.5f) {
            violenceImpact *= 0.5f;
        }

        float entropyImpact = this.entropyScore * 1.5f;
        float directAttackImpact = this.directAttackScore * 2.0f;
        float mitigation = this.respectWeight * 0.5f;
        this.annoyanceWeight = clamp(this.annoyanceWeight * 0.7f
                + (violenceImpact + entropyImpact + directAttackImpact - mitigation) * 0.3f);

        float curiosityImpact = this.explorationScore + (this.metaScore * 0.5f);
        if (this.violenceScore > 0.9f && this.explorationScore < 0.1f) {
            curiosityImpact -= 0.3f;
        }
        this.curiosityWeight = clamp(this.curiosityWeight * 0.9f + curiosityImpact * 0.1f);

        this.arroganceWeight = clamp(0.8f - (this.respectWeight * 0.5f) + (this.failureScore * 0.3f));
    }

    private void determineState() {
        determineState(this.lastUpdateTick);
    }

    private void determineState(long gameTime) {
        int stateAgeTicks = (int) Math.max(0L, gameTime - this.stateEnteredTick);
        MindState resolved = HeroMindStateRegistry.resolve(buildSnapshot(), this.currentState, stateAgeTicks);
        if (resolved != this.currentState) {
            this.currentState = resolved;
            this.stateEnteredTick = gameTime;
        }
    }

    private HeroMindStateSnapshot buildSnapshot() {
        return new HeroMindStateSnapshot(
                violenceScore,
                creativityScore,
                explorationScore,
                failureScore,
                directAttackScore,
                entropyScore,
                metaScore,
                nostalgiaScore,
                monsterEmpathyScore,
                respectWeight,
                annoyanceWeight,
                curiosityWeight,
                arroganceWeight,
                sorrowWeight,
                stabilityObsession,
                actionFeedback.getOrDefault("PRANK", 0.0f)
        );
    }

    private float clamp(float val) {
        return Mth.clamp(val, 0.0f, 1.0f);
    }

    public MindState getCurrentState() {
        return this.currentState;
    }

    public void forceState(MindState state, long gameTime) {
        if (state == null) {
            return;
        }
        this.currentState = state;
        this.stateEnteredTick = gameTime;
    }
    // --- NBT Persistence ---

    public void save(CompoundTag tag) {
        CompoundTag brain = new CompoundTag();
        brain.putFloat("Violence", violenceScore);
        brain.putFloat("Creativity", creativityScore);
        brain.putFloat("Exploration", explorationScore);
        brain.putFloat("Failure", failureScore);
        tag.putFloat("DirectAttack", directAttackScore);

        brain.putFloat("Entropy", entropyScore);
        brain.putFloat("Meta", metaScore);
        brain.putFloat("Nostalgia", nostalgiaScore);
        brain.putFloat("MonsterEmpathy", monsterEmpathyScore);
        tag.putFloat("StabilityObsession", stabilityObsession);

        brain.putFloat("Respect", respectWeight);
        brain.putFloat("Annoyance", annoyanceWeight);
        brain.putFloat("Curiosity", curiosityWeight);
        brain.putFloat("Arrogance", arroganceWeight);
        brain.putFloat("Sorrow", sorrowWeight);
        
        brain.putString("State", currentState.name());
        tag.putLong("StateEnteredTick", stateEnteredTick);

        // Save Memory
        CompoundTag memory = new CompoundTag();
        actionFeedback.forEach(memory::putFloat);
        brain.put("Memory", memory);
        
        tag.put("HeroNeuralNetwork", brain);
    }

    public void load(CompoundTag tag) {
        if (!tag.contains("Violence")) return;

        this.violenceScore = tag.getFloat("Violence");
        this.creativityScore = tag.getFloat("Creativity");
        this.explorationScore = tag.getFloat("Exploration");
        this.failureScore = tag.getFloat("Failure");
        this.directAttackScore = tag.getFloat("DirectAttack");
        this.entropyScore = tag.getFloat("Entropy");
        this.metaScore = tag.getFloat("Meta");
        this.nostalgiaScore = tag.getFloat("Nostalgia");
        this.monsterEmpathyScore = tag.getFloat("MonsterEmpathy");

        this.respectWeight = tag.getFloat("Respect");
        this.annoyanceWeight = tag.getFloat("Annoyance");
        this.curiosityWeight = tag.getFloat("Curiosity");
        this.arroganceWeight = tag.getFloat("Arrogance");
        this.sorrowWeight = tag.getFloat("Sorrow");
        if (tag.contains("StabilityObsession")) {
            this.stabilityObsession = tag.getFloat("StabilityObsession");
        }

        try {
            this.currentState = MindState.valueOf(tag.getString("State"));
        } catch (Exception e) {
            this.currentState = MindState.OBSERVER;
        }
        this.stateEnteredTick = tag.contains("StateEnteredTick") ? tag.getLong("StateEnteredTick") : 0L;

        actionFeedback.clear();
        if (tag.contains("Memory")) {
            CompoundTag memory = tag.getCompound("Memory");
            for (String key : memory.getAllKeys()) {
                actionFeedback.put(key, memory.getFloat(key));
            }
        }
    }
    
    // Debug info
    public String getDebugInfo() {
        return String.format(
                "St:%s | V:%.2f E:%.2f M:%.2f N:%.2f | R:%.2f A:%.2f Arr:%.2f",
                currentState.name().substring(0, 3),
                violenceScore,
                entropyScore,
                metaScore,
                nostalgiaScore,
                respectWeight,
                annoyanceWeight,
                arroganceWeight
        );
    }
}
