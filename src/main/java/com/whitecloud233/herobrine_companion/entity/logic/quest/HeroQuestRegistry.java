package com.whitecloud233.herobrine_companion.entity.logic.quest;

import com.whitecloud233.herobrine_companion.entity.logic.quest.impl.CandleRitualQuest;
import com.whitecloud233.herobrine_companion.entity.logic.quest.impl.CaptainBannerQuest;
import com.whitecloud233.herobrine_companion.entity.logic.quest.impl.ClearUnstableZoneQuest;
import com.whitecloud233.herobrine_companion.entity.logic.quest.impl.FeedWolfQuest;
import com.whitecloud233.herobrine_companion.entity.logic.quest.impl.MirrorQuest;
import com.whitecloud233.herobrine_companion.entity.logic.quest.impl.MusicDiscQuest;
import com.whitecloud233.herobrine_companion.entity.logic.quest.impl.PacifyEndermanQuest;
import com.whitecloud233.herobrine_companion.entity.logic.quest.impl.StormVigilQuest;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 单一职责：委托的注册表。持有两条只读映射——
 * <ul>
 *   <li>{@link Definition}：展示数据（名称 / 描述 / 奖励文本的翻译键），仅供 UI 读取；</li>
 *   <li>{@link HeroQuest} 实现：行为逻辑，仅供 {@link HeroQuestManager} 分发。</li>
 * </ul>
 * 新增委托 = 在 impl 包写一个实现类 + 在这里登记两行，其余系统自动生效。
 */
public final class HeroQuestRegistry {

    /** 重复接取冷却：完成委托后需等待 3 个游戏日（≈1 真实小时）才能再次接取，防止刷信任。 */
    public static final long COOLDOWN_TICKS = 3L * 24000L;

    /** 展示数据：与行为解耦，UI 只读这里。 */
    public record Definition(int id, String nameKey, String descKey, String rewardKey) {
    }

    private static final List<Definition> DEFINITIONS = List.of(
            new Definition(1, "gui.herobrine_companion.request_name_1", "gui.herobrine_companion.request_desc_1", "gui.herobrine_companion.request_reward_1"),
            new Definition(2, "gui.herobrine_companion.request_name_2", "gui.herobrine_companion.request_desc_2", "gui.herobrine_companion.request_reward_2"),
            new Definition(3, "gui.herobrine_companion.request_name_3", "gui.herobrine_companion.request_desc_3", "gui.herobrine_companion.request_reward_3"),
            new Definition(4, "gui.herobrine_companion.request_name_4", "gui.herobrine_companion.request_desc_4", "gui.herobrine_companion.request_reward_4"),
            new Definition(5, "gui.herobrine_companion.request_name_5", "gui.herobrine_companion.request_desc_5", "gui.herobrine_companion.request_reward_5"),
            new Definition(6, "gui.herobrine_companion.request_name_6", "gui.herobrine_companion.request_desc_6", "gui.herobrine_companion.request_reward_6"),
            new Definition(7, "gui.herobrine_companion.request_name_7", "gui.herobrine_companion.request_desc_7", "gui.herobrine_companion.request_reward_7"),
            new Definition(8, "gui.herobrine_companion.request_name_8", "gui.herobrine_companion.request_desc_8", "gui.herobrine_companion.request_reward_8")
    );

    private static final Map<Integer, HeroQuest> IMPLEMENTATIONS = new HashMap<>();

    static {
        IMPLEMENTATIONS.put(1, new ClearUnstableZoneQuest());
        IMPLEMENTATIONS.put(2, new PacifyEndermanQuest());
        IMPLEMENTATIONS.put(3, new MusicDiscQuest());
        IMPLEMENTATIONS.put(4, new FeedWolfQuest());
        IMPLEMENTATIONS.put(5, new CandleRitualQuest());
        IMPLEMENTATIONS.put(6, new CaptainBannerQuest());
        IMPLEMENTATIONS.put(7, new MirrorQuest());
        IMPLEMENTATIONS.put(8, new StormVigilQuest());
    }

    private HeroQuestRegistry() {
    }

    /** 全部展示定义，顺序即界面翻页顺序。 */
    public static List<Definition> definitions() {
        return DEFINITIONS;
    }

    public static int count() {
        return DEFINITIONS.size();
    }

    public static Definition definition(int questId) {
        for (Definition definition : DEFINITIONS) {
            if (definition.id() == questId) return definition;
        }
        return null;
    }

    /** 按 ID 取行为实现；未知 ID 返回 null（调用方应静默忽略）。 */
    public static HeroQuest byId(int questId) {
        return IMPLEMENTATIONS.get(questId);
    }

    /**
     * 客户端右键 Hero 时的开屏拦截规则：当前委托是“交付类”委托且主手拿的是交付物时，
     * 不打开 Hero 界面——交付由服务端 {@code EntityInteract} 事件权威消费。
     */
    public static boolean isDeliveryQuestInHand(int questId, ItemStack stack) {
        HeroQuest quest = byId(questId);
        if (quest instanceof MusicDiscQuest musicDiscQuest) {
            return musicDiscQuest.isDeliveryItem(stack);
        }
        if (quest instanceof CaptainBannerQuest captainBannerQuest) {
            return captainBannerQuest.isDeliveryItem(stack);
        }
        return false;
    }
}