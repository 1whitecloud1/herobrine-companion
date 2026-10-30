package com.whitecloud233.modid.herobrine_companion.event;

import com.whitecloud233.modid.herobrine_companion.init.ModItems;
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

    public static volatile List<Reward> REWARDS = List.of();

    // 静态块只在类加载时运行一次，我们改为调用 reset
    static {
        reset();
    }

    /**
     * 重置奖励列表为默认值。
     * 无 KubeJS 时在资源重载后恢复默认定义。
     */
    public static void reset() { replaceAll(defaults()); }

    public static List<Reward> defaults() {
        List<Reward> rewards = new ArrayList<>();

        // --- 信任度 2 奖励 (合并) ---
        ItemStack handbook = new ItemStack(ModItems.LORE_HANDBOOK.get());
        ItemStack fragment1 = new ItemStack(ModItems.LORE_FRAGMENT.get());
        CompoundTag tag = new CompoundTag();
        tag.putString(LoreFragmentItem.LORE_ID_KEY, "fragment_1");
        fragment1.setTag(tag); // 1.20.1 使用 setTag
        rewards.add(new Reward(6, 2, handbook, fragment1)); // 使用 ID 6
        // 示例奖励，可以根据需求修改
        rewards.add(new Reward(0, 10, new ItemStack(Items.TOTEM_OF_UNDYING, 2)));
        rewards.add(new Reward(1, 20, new ItemStack(ModItems.ABYSSAL_GAZE.get(), 1)));
        rewards.add(new Reward(2, 30, new ItemStack(Items.DIAMOND, 32)));
        rewards.add(new Reward(3, 50, new ItemStack(ModItems.SOUL_BOUND_PACT.get(), 1)));
        rewards.add(new Reward(4, 75, new ItemStack(ModItems.TRANSCENDENCE_PERMIT.get(), 1)));
        rewards.add(new Reward(5, 100, new ItemStack(ModItems.POEM_OF_THE_END.get(), 1)));
        return rewards;
    }

    public static Reward copy(Reward reward) {
        return new Reward(reward.id, reward.requiredTrust, reward.items.stream().map(ItemStack::copy).toArray(ItemStack[]::new));
    }

    public static List<Reward> snapshot() { return REWARDS.stream().map(HeroRewards::copy).toList(); }

    public static void replaceAll(List<Reward> rewards) {
        if (rewards.size() > HeroContentLimits.MAX_REWARDS) throw new IllegalArgumentException("Too many rewards");
        java.util.Set<Integer> ids = new java.util.HashSet<>();
        List<Reward> checked = new ArrayList<>();
        for (Reward reward : rewards) {
            HeroContentLimits.rewardId(reward.id);
            HeroContentLimits.trust(reward.requiredTrust);
            if (!ids.add(reward.id)) throw new IllegalArgumentException("Duplicate reward ID: " + reward.id);
            if (reward.items.size() > HeroContentLimits.MAX_ITEMS_PER_REWARD) throw new IllegalArgumentException("Too many reward items");
            checked.add(new Reward(reward.id, reward.requiredTrust, reward.items.stream().map(HeroContentLimits::item).toArray(ItemStack[]::new)));
        }
        REWARDS = List.copyOf(checked);
    }

    public static Reward getReward(int id) {
        return REWARDS.stream().filter(r -> r.id == id).findFirst().orElse(null);
    }
}
