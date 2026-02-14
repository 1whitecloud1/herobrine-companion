package com.whitecloud233.modid.herobrine_companion.entity.logic;

import com.whitecloud233.modid.herobrine_companion.HerobrineCompanion;
import com.whitecloud233.modid.herobrine_companion.item.LoreFragmentItem;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class HeroRewards {
    private static final Logger LOGGER = LoggerFactory.getLogger("HeroRewards");

    // 奖励类保持不变，但建议 items 字段保持 public 以便高级访问
    public static class Reward {
        public final int id;
        public int requiredTrust;
        public List<ItemStack> items;

        public Reward(int id, int requiredTrust, ItemStack... items) {
            this.id = id;
            this.requiredTrust = requiredTrust;
            this.items = new ArrayList<>(Arrays.asList(items));
        }

        // 添加一个辅助方法，供 Java 内部使用
        public void addItem(ItemStack stack) {
            this.items.add(stack);
        }

        @Override
        public String toString() {
            return "Reward{id=" + id + ", trust=" + requiredTrust + ", items=" + items + "}";
        }
    }

    public static final List<Reward> REWARDS = new ArrayList<>();

    // 静态块只在类加载时运行一次，我们改为调用 reset
    static {
        reset();
    }

    /**
     * 重置奖励列表为默认值。
     * KubeJS 插件将在每次脚本重载前调用此方法，防止奖励重复叠加。
     */
    public static void reset() {
        REWARDS.clear();
        // --- 信任度 2 奖励 (合并) ---
        ItemStack handbook = new ItemStack(HerobrineCompanion.LORE_HANDBOOK.get());
        ItemStack fragment1 = new ItemStack(HerobrineCompanion.LORE_FRAGMENT.get());
        CompoundTag tag = new CompoundTag();
        tag.putString(LoreFragmentItem.LORE_ID_KEY, "fragment_1");
        fragment1.setTag(tag); // 1.20.1 使用 setTag
        REWARDS.add(new Reward(6, 2, handbook, fragment1)); // 使用 ID 6
        // 示例奖励，可以根据需求修改
        REWARDS.add(new Reward(0, 10, new ItemStack(Items.TOTEM_OF_UNDYING, 2)));
        REWARDS.add(new Reward(1, 20, new ItemStack(HerobrineCompanion.ABYSSAL_GAZE.get(), 1)));
        REWARDS.add(new Reward(2, 30, new ItemStack(Items.DIAMOND, 32)));
        REWARDS.add(new Reward(3, 50, new ItemStack(HerobrineCompanion.SOUL_BOUND_PACT.get(), 1)));
        REWARDS.add(new Reward(4, 75, new ItemStack(HerobrineCompanion.TRANSCENDENCE_PERMIT.get(), 1)));
        REWARDS.add(new Reward(5, 100, new ItemStack(HerobrineCompanion.POEM_OF_THE_END.get(), 1)));
    }
    // [修改] 信任度 100 的奖励改为 终末之诗
    public static Reward getReward(int id) {
        return REWARDS.stream().filter(r -> r.id == id).findFirst().orElse(null);
    }
}
