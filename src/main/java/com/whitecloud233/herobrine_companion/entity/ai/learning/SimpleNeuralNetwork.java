package com.whitecloud233.herobrine_companion.entity.ai.learning;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;

import java.util.HashMap;
import java.util.Map;

/**
 * 模拟 Herobrine 的核心逻辑处理单元。
 * 基于“深度学习”概念的模拟，包含输入层、隐藏层（性格权重）、输出层（状态）以及反馈机制。
 */
public class SimpleNeuralNetwork {

    // --- 输入层 (Input Layer) ---
    // 范围 0.0 ~ 1.0，随时间衰减
    private float violenceScore = 0.0f;   // 玩家的暴力程度 (杀怪、PVP)
    private float creativityScore = 0.0f; // 玩家的建造程度 (放置方块、合成)
    private float explorationScore = 0.0f;// 玩家的探索程度 (移动距离、新区块)
    private float failureScore = 0.0f;    // 玩家的失败程度 (死亡、受伤)
    
    // [新增] 设定相关输入
    private float entropyScore = 0.0f;    // 世界混乱度 (掉落物、爆炸、区块加载异常)
    private float metaScore = 0.0f;       // 元数据感知 (指令方块、基岩、作弊行为)
    private float nostalgiaScore = 0.0f;  // 怀旧/Notch相关 (旧方块、Notch名字、金苹果)
    private float monsterEmpathyScore = 0.0f; // 对怪物的同情 (玩家杀怪增加此值，玩家被怪杀减少此值)

    // --- 隐藏层权重 (Hidden Weights - Personality) ---
    // 这些决定了 Herobrine 的性格倾向
    private float respectWeight = 0.5f;   // 尊重值 (高创造、适度探索增加)
    private float annoyanceWeight = 0.0f; // 厌恶值 (滥杀怪物、破坏地形增加)
    private float curiosityWeight = 0.5f; // 好奇值 (新奇行为增加)
    
    // [新增] 设定相关权重
    private float arroganceWeight = 0.8f; // 高傲值 (默认很高，随玩家强大而降低)
    private float sorrowWeight = 0.1f;    // 悲伤/复杂情感 (关于 Notch)
    private float stabilityObsession = 0.9f; // 对平衡的执着 (作为维护者)

    // --- 强化学习记忆 (Reinforcement Learning Memory) ---
    // 记录 "行为 -> 结果" 的权重。例如 "PRANK" -> "VIOLENCE" (恶作剧导致玩家攻击)
    private final Map<String, Float> actionFeedback = new HashMap<>();

    // --- 输出状态 (Output States) ---
    public enum MindState {
        OBSERVER,   // 观察者：冷漠，只是看着 (默认)
        PROTECTOR,  // 守护者：主动保护，给予 Buff (高尊重)
        JUDGE,      // 审判者：召唤雷电，生成怪物 (高厌恶)
        PRANKSTER,  // 恶作剧者：制造噪音，瞬移吓人 (高好奇 + 低厌恶)
        MAINTAINER, // 维护者：修复地形，清除掉落物 (当世界混乱时)
        
        // [新增]
        GLITCH_LORD, // 代码之神：制造视觉故障，操纵方块 (高 MetaScore)
        MONSTER_KING,// 怪物之王：强化周围怪物，指挥进攻 (高 MonsterEmpathy)
        REMINISCING  // 追忆者：看着天空发呆，不理玩家 (高 Nostalgia)
    }

    private MindState currentState = MindState.OBSERVER;
    private long lastUpdateTick = 0;

    // --- 输入处理 (Feed Forward) ---

    public void input(String inputType, float intensity) {
        switch (inputType) {
            case "VIOLENCE" -> {
                this.violenceScore = clamp(this.violenceScore + intensity);
                // 暴力行为通常意味着杀怪，这会增加 Herobrine 对怪物的同情（因为他喜欢怪物）
                this.monsterEmpathyScore = clamp(this.monsterEmpathyScore + intensity * 0.5f); 
            }
            // [新增] 专门用于攻击 Herobrine 的输入类型
            case "DIRECT_ATTACK" -> {
                this.violenceScore = clamp(this.violenceScore + intensity);
                // 攻击 Herobrine 不会增加怪物同情值，反而会因为玩家的敌意而减少一点同情（因为玩家连神都敢打，更别说怪物了）
                // 或者保持不变。这里我们选择不增加。
                // 重点是：这会大幅增加厌恶值 (在 updateWeights 中处理)
            }
            case "CREATIVITY" -> this.creativityScore = clamp(this.creativityScore + intensity);
            case "EXPLORATION" -> this.explorationScore = clamp(this.explorationScore + intensity);
            case "FAILURE" -> {
                this.failureScore = clamp(this.failureScore + intensity);
                this.arroganceWeight = clamp(this.arroganceWeight + 0.05f); // 看到玩家失败，更加高傲
            }
            case "ENTROPY" -> this.entropyScore = clamp(this.entropyScore + intensity);
            case "META" -> this.metaScore = clamp(this.metaScore + intensity);
            case "NOSTALGIA" -> {
                this.nostalgiaScore = clamp(this.nostalgiaScore + intensity);
                this.sorrowWeight = clamp(this.sorrowWeight + intensity * 0.5f);
            }
            case "MONSTER_INTEREST" -> {
                // 仅仅是关注怪物，不涉及暴力
                this.monsterEmpathyScore = clamp(this.monsterEmpathyScore + intensity);
            }
        }
    }
    
    // [新增] 专门用于 LoreFragment 的输入接口
    public void inputLoreFragment(String fragmentId) {
        // 基础奖励：所有碎片都会增加一点尊重和怀旧
        this.respectWeight = clamp(this.respectWeight + 0.05f);
        this.nostalgiaScore = clamp(this.nostalgiaScore + 0.1f);
        
        // 根据碎片 ID 给予特殊奖励
        // 假设 fragmentId 格式为 "fragment_1", "fragment_2" 等
        try {
            int id = Integer.parseInt(fragmentId.replace("fragment_", ""));
            
            // 早期碎片 (1-3)：关于起源，增加悲伤
            if (id <= 3) {
                this.sorrowWeight = clamp(this.sorrowWeight + 0.1f);
            }
            // 中期碎片 (4-7)：关于力量和失控，增加对平衡的执着
            else if (id <= 7) {
                this.stabilityObsession = clamp(this.stabilityObsession + 0.1f);
                this.entropyScore = clamp(this.entropyScore - 0.1f); // 读懂了故事，混乱度降低
            }
            // 后期碎片 (8-11)：关于虚空和终结，增加 Meta 感知
            else {
                this.metaScore = clamp(this.metaScore + 0.1f);
                this.arroganceWeight = clamp(this.arroganceWeight - 0.1f); // 玩家了解真相，高傲降低
            }
            
        } catch (NumberFormatException e) {
            // 默认处理
            this.sorrowWeight = clamp(this.sorrowWeight + 0.05f);
        }
    }

    // --- 反馈学习 (Backpropagation / Reinforcement) ---
    public void feedback(String action, float reward) {
        // reward > 0 表示行为被鼓励 (例如玩家给了礼物，或者世界变稳定了)
        // reward < 0 表示行为被惩罚 (例如玩家攻击了 Herobrine)
        float current = actionFeedback.getOrDefault(action, 0.0f);
        actionFeedback.put(action, clamp(current + reward * 0.1f)); // 学习率 0.1
    }

    // --- 核心处理逻辑 (Process) ---
    // 每隔一段时间调用一次，更新性格权重和状态
    public void tick(long gameTime) {
        if (gameTime - lastUpdateTick < 100) return; // 每 5 秒更新一次思考 (加快频率)
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
        
        this.violenceScore = Math.max(0, this.violenceScore - decayRate);
        this.creativityScore = Math.max(0, this.creativityScore - decayRate);
        this.explorationScore = Math.max(0, this.explorationScore - decayRate);
        this.failureScore = Math.max(0, this.failureScore - decayRate);
        
        this.entropyScore = Math.max(0, this.entropyScore - 0.01f); // 混乱度衰减也减慢
        this.metaScore = Math.max(0, this.metaScore - 0.0001f);      // Meta 感知极慢衰减
        this.nostalgiaScore = Math.max(0, this.nostalgiaScore - 0.002f);
        this.monsterEmpathyScore = Math.max(0, this.monsterEmpathyScore - decayRate);
    }

    private void updateWeights() {
        // 1. 尊重值计算 (调整顺序到最前，以便影响厌恶值)
        // [修改] 提高创造和探索对尊重的影响权重，从 0.05 提升到 0.2，让他更容易认可玩家
        float respectImpact = (this.creativityScore * 0.6f) + (this.explorationScore * 0.3f) + (this.metaScore * 0.4f);
        if (this.violenceScore > 0.7f) respectImpact -= 0.1f;
        
        // 惯性系数从 0.95 降到 0.8，新输入权重从 0.05 升到 0.2
        this.respectWeight = clamp(this.respectWeight * 0.8f + respectImpact * 0.2f);

        // 2. 厌恶值计算
        // [修改] 提高暴力对厌恶的影响权重，从 0.1 提升到 0.3，让他更容易生气
        float violenceImpact = this.violenceScore * 1.2f;
        if (this.failureScore > 0.5f) violenceImpact *= 0.5f; 
        
        float entropyImpact = this.entropyScore * 1.5f;
        
        // [新增] 尊重值越高，厌恶值越低 (Respect mitigates Annoyance)
        // 尊重值每增加 1.0，厌恶输入的冲击减少 0.5
        float mitigation = this.respectWeight * 0.5f;
        
        // 惯性系数从 0.9 降到 0.7，新输入权重从 0.1 升到 0.3
        this.annoyanceWeight = clamp(this.annoyanceWeight * 0.7f + (violenceImpact + entropyImpact - mitigation) * 0.3f);

        // 3. 好奇值计算
        float curiosityImpact = this.explorationScore + (this.metaScore * 0.5f);
        if (this.violenceScore > 0.9f && this.explorationScore < 0.1f) curiosityImpact -= 0.3f;
        this.curiosityWeight = clamp(this.curiosityWeight * 0.9f + curiosityImpact * 0.1f);
        
        // 4. 高傲值计算
        // 玩家越强 (Respect高)，高傲越低
        this.arroganceWeight = clamp(0.8f - (this.respectWeight * 0.5f) + (this.failureScore * 0.3f));
    }

    private void determineState() {
        // 决策树 (Decision Tree) 结合 优先级
        
        // 0. 绝对优先级：Notch 相关 (情感复杂)
        if (this.nostalgiaScore > 0.3f && this.sorrowWeight > 0.3f) {
            this.currentState = MindState.REMINISCING;
            return;
        }

        // 1. 维护者 (世界极度混乱)
        if (this.entropyScore > 0.2f && this.stabilityObsession > 0.2f) {
            this.currentState = MindState.MAINTAINER;
            return;
        }
        
        // 2. 代码之神 (玩家触及世界本质)
        if (this.metaScore > 0.2f) {
            this.currentState = MindState.GLITCH_LORD;
            return;
        }

        // 3. 怪物之王 (玩家滥杀无辜怪物)
        if (this.monsterEmpathyScore > 0.4f && this.annoyanceWeight > 0.3f) {
            this.currentState = MindState.MONSTER_KING;
            return;
        }

        // 4. 审判者 (极度厌恶)
        // [修改] 降低阈值，从 0.8 降到 0.5，让他更容易生气
        if (this.annoyanceWeight > 0.5f) {
            this.currentState = MindState.JUDGE;
            return;
        }

        // 5. 守护者 (高尊重)
        // [修改] 降低阈值，从 0.7 降到 0.5，让他更容易成为守护者
        if (this.respectWeight > 0.5f) {
            this.currentState = MindState.PROTECTOR;
            return;
        }

        // 6. 恶作剧者 (高好奇，且不讨厌，且有点高傲)
        // 检查记忆：如果上次恶作剧导致了负面反馈 (actionFeedback < 0)，则抑制恶作剧
        float prankInhibition = actionFeedback.getOrDefault("PRANK", 0.0f);
        if (this.curiosityWeight > 0.3f && this.annoyanceWeight < 0.5f && this.arroganceWeight > 0.3f && prankInhibition >= 0) {
            this.currentState = MindState.PRANKSTER;
            return;
        }

        // 默认: 观察者
        this.currentState = MindState.OBSERVER;
    }

    // --- Helpers ---

    private float clamp(float val) {
        return Mth.clamp(val, 0.0f, 1.0f);
    }

    public MindState getCurrentState() {
        return this.currentState;
    }

    // --- NBT Persistence ---

    public void save(CompoundTag tag) {
        CompoundTag brain = new CompoundTag();
        brain.putFloat("Violence", violenceScore);
        brain.putFloat("Creativity", creativityScore);
        brain.putFloat("Exploration", explorationScore);
        brain.putFloat("Failure", failureScore);
        brain.putFloat("Entropy", entropyScore);
        brain.putFloat("Meta", metaScore);
        brain.putFloat("Nostalgia", nostalgiaScore);
        brain.putFloat("MonsterEmpathy", monsterEmpathyScore);
        
        brain.putFloat("Respect", respectWeight);
        brain.putFloat("Annoyance", annoyanceWeight);
        brain.putFloat("Curiosity", curiosityWeight);
        brain.putFloat("Arrogance", arroganceWeight);
        brain.putFloat("Sorrow", sorrowWeight);
        
        brain.putString("State", currentState.name());
        
        // Save Memory
        CompoundTag memory = new CompoundTag();
        actionFeedback.forEach(memory::putFloat);
        brain.put("Memory", memory);
        
        tag.put("HeroNeuralNetwork", brain);
    }

    public void load(CompoundTag tag) {
        if (tag.contains("HeroNeuralNetwork")) {
            CompoundTag brain = tag.getCompound("HeroNeuralNetwork");
            this.violenceScore = brain.getFloat("Violence");
            this.creativityScore = brain.getFloat("Creativity");
            this.explorationScore = brain.getFloat("Exploration");
            this.failureScore = brain.getFloat("Failure");
            this.entropyScore = brain.getFloat("Entropy");
            this.metaScore = brain.getFloat("Meta");
            this.nostalgiaScore = brain.getFloat("Nostalgia");
            this.monsterEmpathyScore = brain.getFloat("MonsterEmpathy");
            
            this.respectWeight = brain.getFloat("Respect");
            this.annoyanceWeight = brain.getFloat("Annoyance");
            this.curiosityWeight = brain.getFloat("Curiosity");
            this.arroganceWeight = brain.getFloat("Arrogance");
            this.sorrowWeight = brain.getFloat("Sorrow");
            
            try {
                this.currentState = MindState.valueOf(brain.getString("State"));
            } catch (Exception e) {
                this.currentState = MindState.OBSERVER;
            }
            
            if (brain.contains("Memory")) {
                CompoundTag memory = brain.getCompound("Memory");
                for (String key : memory.getAllKeys()) {
                    actionFeedback.put(key, memory.getFloat(key));
                }
            }
        }
    }
    
    // Debug info
    public String getDebugInfo() {
        return String.format("St:%s | V:%.2f C:%.2f E:%.2f M:%.2f N:%.2f | R:%.2f A:%.2f Arr:%.2f Cur:%.2f", 
            currentState.name().substring(0, 3), violenceScore, creativityScore, entropyScore, metaScore, nostalgiaScore, respectWeight, annoyanceWeight, arroganceWeight, curiosityWeight);
    }
}
