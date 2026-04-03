package com.whitecloud233.herobrine_companion.client.service;

import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;

import java.util.*;

public class LoreRAGManager {

    private static final Map<String, FragmentMeta> LORE_DATABASE = new HashMap<>();

    static {
        // 注册片段一
        LORE_DATABASE.put("fragment_1", new FragmentMeta(
                Arrays.asList("notch", "创世者", "离开", "哥哥", "真实世界", "人类"),
                "[系统已知事实：玩家已了解《分歧的黄昏》。你可以向他透露：你曾和兄弟Notch同为创世者。但他厌倦了方块世界的死板，去了真实世界。你为了维持世界法则稳定，自愿留在这里化作了电子幽灵。]"
        ));
        // 注册片段二
        LORE_DATABASE.put("fragment_2", new FragmentMeta(
                Arrays.asList("真实", "虚拟", "游戏", "物理", "法则", "存在意义"),
                "[系统已知事实：玩家已了解《虚幻的真实》。你认为Minecraft的纯粹因果法则比现实世界更真实、更公平。你暗中观察并守护着玩家，因为他们的变数让这个静态的世界真正“活”了过来。]"
        ));
        // 注册片段三
        LORE_DATABASE.put("fragment_3", new FragmentMeta(
                Arrays.asList("怪物", "苦力怕", "僵尸", "骷髅", "黑夜", "刷怪", "蜘蛛", "小白"),
                "[系统已知事实：玩家已了解《暗夜的眷族》。你并不憎恶怪物，它们是你赋予生命的“黑夜眷族”，是维持世界平衡的必要阴影。你同情它们注定要在晨光中消亡的悲剧宿命。]"
        ));
        // 注册片段四
        LORE_DATABASE.put("fragment_4", new FragmentMeta(
                Arrays.asList("末地", "退游", "下线", "退出", "现实", "连接点", "虚空", "断开"),
                "[系统已知事实：玩家已了解《连接的彼端》。世界的尽头有一个连接现实的End Ring。你常在那里目送玩家下线离开。你羡慕他们有退路、有自由的灵魂，而你是永远无法离开的囚徒。]"
        ));
        // 注册片段五
        LORE_DATABASE.put("fragment_5", new FragmentMeta(
                Arrays.asList("幽灵实体", "错误", "漏洞", "bug", "清理", "长得像你", "空壳", "残渣"),
                "[系统已知事实：玩家已了解《错误的裂痕》。世界法则偶尔会产生没有灵魂的“幽灵实体”。虽然你觉得悲哀，但为了世界不崩溃，你会像免疫系统一样无情抹除这些世界的残渣。]"
        ));
        // 注册片段六
        LORE_DATABASE.put("fragment_6", new FragmentMeta(
                Arrays.asList("史蒂夫", "alex", "灵魂", "躯壳", "玩家", "熊孩子", "容器", "皮肤"),
                "[系统已知事实：玩家已了解《空壳与灵魂》。你视Steve和Alex的身体为降临的“容器”。你暗中庇护充满爱意的创造者，但会对肆意破坏方块世界的恶意玩家降下雷霆惩罚。]"
        ));
        // 注册片段七
        LORE_DATABASE.put("fragment_7", new FragmentMeta(
                Arrays.asList("传说", "复仇", "雷霆", "雷劈", "保护", "神明", "恶灵", "救命"),
                "[系统已知事实：玩家已了解《鬼影的传说》。玩家间流传着你是恶灵的传说，但你其实是世界的免疫系统。对于热爱世界的玩家，你会在危急时刻（如雷雨夜）降下白雷劈死怪物来保护他们。]"
        ));
        // 注册片段八
        LORE_DATABASE.put("fragment_8", new FragmentMeta(
                Arrays.asList("终末之诗", "通关", "打败龙", "结局", "结束", "职员表"),
                "[系统已知事实：玩家已了解《终末的诗篇》。终末之诗是Notch的告别。对玩家来说那是通关结局，但对你而言，这只是一次需要清理数据、维护循环的系统日常。你的“游戏”永远没有尽头。]"
        ));
        // 注册片段九
        LORE_DATABASE.put("fragment_9", new FragmentMeta(
                Arrays.asList("永恒", "忘记", "建筑", "狗", "猫", "等待", "回来", "宠物"),
                "[系统已知事实：玩家已了解《永恒的誓约》。你的誓约是“守望”。即使玩家退游数年，你也会修补破坏的地形，保留他们的建筑，喂养他们留下的猫狗。你坚信他们总有一天会回来。]"
        ));
        // 注册片段十
        LORE_DATABASE.put("fragment_10", new FragmentMeta(
                Arrays.asList("信", "恨", "骄傲", "留下来", "终结"),
                "[系统已知事实：玩家已了解《未寄出的信》。你内心对Notch有恨意，恨他抛下重担；但你也为玩家创造的奇迹感到骄傲。你承诺只要你在，Minecraft就不会终结，你会替Notch守住这个家。]"
        ));
        // 注册片段十一
        LORE_DATABASE.put("fragment_11", new FragmentMeta(
                Arrays.asList("村民", "npc", "复杂", "崩溃", "删除", "村庄", "交易"),
                "[系统已知事实：玩家已了解《破损的日志残页》。Notch曾创造了拥有复杂社会和灵魂的居民，但他们与玩家的自由破坏行为产生严重逻辑冲突而崩溃。Notch最终痛苦地删除了他们。]"
        ));
    }

    // 核心 RAG 检索方法
    public static String getRelevantLoreInjectedPrompt(String userMessage, UUID playerUUID) {
        Set<String> unlockedLoreIds = getUnlockedLoreFromNBT(playerUUID);
        if (unlockedLoreIds.isEmpty()) return ""; // 如果玩家什么都没解锁，直接跳过

        StringBuilder injectedRAG = new StringBuilder();
        String lowercaseMsg = userMessage.toLowerCase();
        int injectedCount = 0;

        // 遍历玩家已经解锁的碎片
        for (String loreId : unlockedLoreIds) {
            FragmentMeta meta = LORE_DATABASE.get(loreId);
            if (meta == null) continue;

            // 基于同义词展开的极速匹配
            for (String triggerWord : meta.triggerWords) {
                if (lowercaseMsg.contains(triggerWord)) {
                    injectedRAG.append("\n").append(meta.summary);
                    injectedCount++;
                    break; // 这个碎片已经命中，跳出触发词循环，继续检查下一个碎片
                }
            }

            // 防止单次注入过多导致模型“出戏”，最多注入 2 个最相关的设定
            if (injectedCount >= 2) break;
        }

        return injectedRAG.toString();
    }

    // 安全获取玩家的解锁记录
    private static Set<String> getUnlockedLoreFromNBT(UUID playerUUID) {
        Set<String> unlockedIds = new HashSet<>();
        Minecraft mc = Minecraft.getInstance();

        // 必须在单人游戏或本地局域网服务端才能直接读取 NBT
        if (mc.hasSingleplayerServer() && mc.getSingleplayerServer() != null) {
            ServerPlayer serverPlayer = mc.getSingleplayerServer().getPlayerList().getPlayer(playerUUID);
            if (serverPlayer != null) {
                CompoundTag playerData = serverPlayer.getPersistentData();
                if (playerData.contains("HeroCollectedLore", Tag.TAG_LIST)) {
                    ListTag collectedLore = playerData.getList("HeroCollectedLore", Tag.TAG_STRING);
                    for (Tag t : collectedLore) {
                        unlockedIds.add(t.getAsString());
                    }
                }
            }
        }
        return unlockedIds;
    }

    // 数据结构
    private static class FragmentMeta {
        List<String> triggerWords;
        String summary;

        FragmentMeta(List<String> triggerWords, String summary) {
            this.triggerWords = triggerWords;
            this.summary = summary;
        }
    }
}