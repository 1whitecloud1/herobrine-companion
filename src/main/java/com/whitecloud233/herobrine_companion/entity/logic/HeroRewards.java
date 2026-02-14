package com.whitecloud233.herobrine_companion.entity.logic;

import com.whitecloud233.herobrine_companion.HerobrineCompanion;
import com.whitecloud233.herobrine_companion.item.LoreFragmentItem;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
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
        
        // --- 信任度 2 奖励 ---
        ItemStack handbook = new ItemStack(HerobrineCompanion.LORE_HANDBOOK.get());
        ItemStack fragment1 = new ItemStack(HerobrineCompanion.LORE_FRAGMENT.get());
        CompoundTag tag = new CompoundTag();
        tag.putString(LoreFragmentItem.LORE_ID_KEY, "fragment_1");
        fragment1.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        REWARDS.add(new Reward(6, 2, handbook, fragment1));

        // --- 其他奖励 ---
        REWARDS.add(new Reward(0, 10, new ItemStack(Items.TOTEM_OF_UNDYING, 2)));
        REWARDS.add(new Reward(1, 20, new ItemStack(HerobrineCompanion.ABYSSAL_GAZE.get(), 1)));
        REWARDS.add(new Reward(2, 30, new ItemStack(Items.DIAMOND, 32)));
        REWARDS.add(new Reward(3, 50, new ItemStack(HerobrineCompanion.SOUL_BOUND_PACT.get(), 1)));
        REWARDS.add(new Reward(4, 75, new ItemStack(HerobrineCompanion.TRANSCENDENCE_PERMIT.get(), 1)));
        REWARDS.add(new Reward(5, 100, new ItemStack(HerobrineCompanion.POEM_OF_THE_END.get(), 1)));
        
        LOGGER.info("HeroRewards reset to defaults. Count: {}", REWARDS.size());
    }

    public static Reward getReward(int id) {
        return REWARDS.stream().filter(r -> r.id == id).findFirst().orElse(null);
    }
}
