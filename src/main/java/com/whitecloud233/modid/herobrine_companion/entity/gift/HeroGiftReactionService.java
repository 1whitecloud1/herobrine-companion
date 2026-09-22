package com.whitecloud233.modid.herobrine_companion.entity.gift;

import com.whitecloud233.modid.herobrine_companion.entity.HeroEntity;
import com.whitecloud233.modid.herobrine_companion.entity.dialogue.ActorDialogueManager;
import com.whitecloud233.modid.herobrine_companion.entity.dialogue.ActorDialoguePersona;
import com.whitecloud233.modid.herobrine_companion.entity.dialogue.ActorDialogueSpec;
import com.whitecloud233.modid.herobrine_companion.entity.dialogue.DialogueChannel;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.UUID;

/**
 * 赠礼回应台词 —— LLM 为主、本地池兜底。
 *
 * <p>单一职责:把一次赠礼判定的"应说点东西"翻译成一句英雄台词并发出。
 * 主回应走 {@link DialogueChannel#GIFT} 通道的 ActorDialogue 闭环
 * (服务端发包 → 客户端 LLM 生成 → 气泡回发);该通道独立于觉醒对话的限流,
 * 默认不设最小间隔,因此托付的台词不会因为觉醒生物在说话而被挤成本地文本。
 * LLM 未配置/请求失败时,同样回落到 {@link HeroGiftLines} 本地池。
 *
 * <p>同一次交互里的引导句与附加句(冷却提示、模糊线索、生日第二句)请用
 * {@link #sendLocalLine}:直接上气泡,不发起 LLM 请求。
 */
public final class HeroGiftReactionService {

    private HeroGiftReactionService() {
    }

    private static final ActorDialoguePersona PERSONA = new ActorDialoguePersona(
            "Herobrine（守夜人）",
            "你是 Minecraft 世界里的 Herobrine：世界的守夜人、裂缝的维护者、黑夜的共主。"
                    + "玩家把物品交给你——那不是贡品，是一段世界痕迹，你要按它的含义回应。",
            "低沉、简短、不带感叹号；像对自己说话又刚好让玩家听见；不解释系统机制。",
            List.of(
                    "玩家近期的行为（击杀、修复、伤害村庄）会改变你的态度，把它融进话里但不复述数字。",
                    "记住：记忆之礼你铭记；修复之礼你认可；黑夜之礼你告诫；裂痕之礼你接住；"
                            + "高价值物品只会换来怀疑；污染行为换来审判。",
                    "绝不说'礼物'、'系统'、'任务'这类玩家向词汇；称它为'痕迹'、'证据'或直接以物相称。"));

    private static final String[] CODE_MEANINGS = {
            "remembered|你愿意铭记这件托付",
            "accepted|你平淡地收下",
            "food_accepted|你收下这份食物",
            "warning|你收下但带着告诫",
            "judged|你拒绝并审判",
            "rejected_repeat|你拒绝——同样的东西递太多次了",
            "rejected_suspicious|你拒收并起疑",
            "rejected_taboo|你认得这件东西，也知道它连着什么旧账",
            "request_asked|你不收，改口索取一件别的",
            "request_fulfilled|玩家达成了你的请求，你收得踏实",
            "returned_last_bite|你把食物退回——这一口该留给玩家自己",
            "secret_hit|玩家的递交命中了你的心头好，你心照不宣",
            "birthday|今天是你的生日，玩家记起了它",
            "request_expired|他提出的请求过期了",
    };

    /**
     * 发送一条**主**赠礼回应(走 GIFT 通道,独立限流)。
     *
     * @return 服务端记录的 fallback 文本(存入 LastReaction)
     */
    public static String sendReaction(HeroEntity hero, ServerPlayer player, String code,
                                      String category, int tasteLevel, String mood,
                                      String itemName, String extraHint) {
        HeroGiftLines.Line line = HeroGiftLines.pick(code, category, tasteLevel, mood,
                hero.getRandom(), player.getUUID());
        String userPrompt = buildUserPrompt(code, category, tasteLevel, mood, itemName, extraHint, hero, player);
        ActorDialogueManager.INSTANCE.requestDialogue(new ActorDialogueSpec(
                hero,
                UUID.randomUUID(),
                PERSONA,
                userPrompt,
                line.text(),
                line.key(),
                List.of(),
                player,
                null,
                DialogueChannel.GIFT));
        return line.text();
    }

    /**
     * 发送一条**仅本地**赠礼台词(同一次交互的引导句/附加句)。
     *
     * <p>直接上气泡,不经过 LLM 链路:不消耗 API 配额、不受任何频率闸影响,
     * 也不会与主回应争抢同一次交互的"发言权"。
     *
     * @return 实际显示的文本
     */
    public static String sendLocalLine(HeroEntity hero, ServerPlayer player, String code,
                                       String category, int tasteLevel, String mood) {
        HeroGiftLines.Line line = HeroGiftLines.pick(code, category, tasteLevel, mood,
                hero.getRandom(), player.getUUID());
        hero.showGiftLine(line.text());
        return line.text();
    }

    private static String buildUserPrompt(String code, String category, int tasteLevel,
                                          String mood, String itemName, String extraHint,
                                          HeroEntity hero, ServerPlayer player) {
        String meaning = meaningOf(code);
        StringBuilder sb = new StringBuilder();
        sb.append("玩家「").append(player.getName().getString()).append("」刚把一件物品交付给你。\n");
        if (itemName != null && !itemName.isBlank()) {
            sb.append("物品：").append(itemName).append("（类别：").append(category == null ? "?" : category)
                    .append("）\n");
        }
        sb.append("你对此事的姿态：").append(meaning).append("。\n");
        if (tasteLevel != 0) {
            sb.append("你对这件物品的口味记忆等级（-4 厌恶 ~ +4 知心）：").append(tasteLevel).append("。\n");
        }
        if (mood != null) {
            sb.append("今天你的情绪基调：").append("grumpy".equals(mood) ? "冷淡" : "难得地愉悦").append("。\n");
        }
        if (extraHint != null && !extraHint.isBlank()) {
            sb.append("补充：").append(extraHint).append("\n");
        }
        sb.append("用一句话回应他，不要复述以上数据。");
        return sb.toString();
    }

    private static String meaningOf(String code) {
        if (code == null) {
            return "你收下了";
        }
        for (String pair : CODE_MEANINGS) {
            String[] parts = pair.split("\\|", 2);
            if (parts[0].equals(code)) {
                return parts[1];
            }
        }
        return "你收下了";
    }
}