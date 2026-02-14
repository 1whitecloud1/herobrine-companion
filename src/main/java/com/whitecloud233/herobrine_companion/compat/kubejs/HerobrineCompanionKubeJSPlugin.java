package com.whitecloud233.herobrine_companion.compat.kubejs;

import com.whitecloud233.herobrine_companion.entity.HeroEntity;
import com.whitecloud233.herobrine_companion.entity.logic.HeroRewards;
import dev.latvian.mods.kubejs.event.EventGroup;
import dev.latvian.mods.kubejs.event.EventHandler;
import dev.latvian.mods.kubejs.event.EventGroupRegistry;
import dev.latvian.mods.kubejs.event.KubeEvent;
import dev.latvian.mods.kubejs.plugin.KubeJSPlugin;
import dev.latvian.mods.kubejs.script.BindingRegistry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.TagsUpdatedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// 1. 注册为 NeoForge 事件监听者，以便捕获重载事件
// 1.21.1: EventBusSubscriber.Bus.GAME 已被移除，默认就是 GAME 总线，或者使用 NeoForge.EVENT_BUS.register()
@EventBusSubscriber
public class HerobrineCompanionKubeJSPlugin implements KubeJSPlugin {
    private static final Logger LOGGER = LoggerFactory.getLogger("HerobrineCompanionKubeJS");
    public static final EventGroup GROUP = EventGroup.of("HerobrineCompanionEvents");
    public static final EventHandler REGISTER_REWARDS = GROUP.server("registerRewards", () -> RegisterRewardsEvent.class);
    
    // [新增] 交易事件
    public static final EventHandler REGISTER_TRADES = GROUP.server("registerTrades", () -> HeroTradesEvent.class);

    private static final Pattern COUNT_REGEX = Pattern.compile("^(\\d+)x\\s+(.+)$");

    @Override
    public void registerEvents(EventGroupRegistry registry) {
        registry.register(GROUP);
    }

    @Override
    public void registerBindings(BindingRegistry bindings) {
        // 注册全局对象，方便快速测试，但推荐用事件
        bindings.add("HeroRewards", new HeroRewardsJS());
    }

    /**
     * 关键步骤：在数据包/标签重载时触发。
     * 这通常发生在服务器启动完成或执行 /reload 命令时。
     * 这是触发 KubeJS 修改奖励的最佳时机。
     */
    @SubscribeEvent
    public static void onTagsUpdated(TagsUpdatedEvent event) {
        // 1.21.1: TagsUpdatedEvent.UpdateCause 可能有所不同，或者直接检查是否是服务器端
        // 检查是否是从注册表加载的 (通常意味着数据包重载)
        if (event.getUpdateCause() == TagsUpdatedEvent.UpdateCause.SERVER_DATA_LOAD) {
            LOGGER.info("Data reloaded, firing HerobrineCompanion reward registration...");
            
            // 1. 先重置为默认值，防止重复添加
            HeroRewards.reset();
            
            // 2. 如果 KubeJS 系统已就绪，触发事件让脚本修改列表
            if (REGISTER_REWARDS.hasListeners()) {
                REGISTER_REWARDS.post(new RegisterRewardsEvent());
            }
        }
    }

    // [新增] 供 HeroTrades.java 调用的静态钩子
    public static void fireTradeEvent(MerchantOffers offers, HeroEntity hero) {
        if (REGISTER_TRADES.hasListeners()) {
            // 将当前的交易列表和实体传给事件
            REGISTER_TRADES.post(new HeroTradesEvent(offers, hero));
        }
    }

    // --- 工具方法：解析对象为 ItemStack ---
    public static ItemStack parseItem(Object obj) {
        if (obj == null) return ItemStack.EMPTY;
        if (obj instanceof ItemStack) return (ItemStack) obj;
        
        // 处理 "3x minecraft:apple" 或 "minecraft:dirt"
        if (obj instanceof String str) {
            int count = 1;
            String itemId = str;
            Matcher matcher = COUNT_REGEX.matcher(str);
            if (matcher.find()) {
                count = Integer.parseInt(matcher.group(1));
                itemId = matcher.group(2);
            }
            ResourceLocation rl = ResourceLocation.tryParse(itemId);
            if (rl != null) {
                Item item = BuiltInRegistries.ITEM.get(rl);
                if (item != Items.AIR) return new ItemStack(item, count);
            }
            return ItemStack.EMPTY;
        }

        // 处理 KubeJS ItemWrapper (Item.of(...))
        try {
            Method getItemStackMethod = obj.getClass().getMethod("getItemStack");
            Object result = getItemStackMethod.invoke(obj);
            if (result instanceof ItemStack) return (ItemStack) result;
        } catch (Exception ignored) {}
        
        return ItemStack.EMPTY;
    }

    // [新增] 辅助方法：解析 ItemCost (输入物品)
    // Minecraft 1.21 使用 ItemCost 而不是 ItemStack 作为输入
    public static ItemCost parseCost(Object obj) {
        ItemStack stack = parseItem(obj);
        if (stack.isEmpty()) return new ItemCost(Items.AIR);
        return new ItemCost(stack.getItem(), stack.getCount());
    }

    // --- 包装类：让 Modify 更安全 ---
    // 这个类包装了原始的 Java Reward 对象，提供给 JS 使用
    public static class RewardJS {
        private final HeroRewards.Reward original;

        public RewardJS(HeroRewards.Reward original) {
            this.original = original;
        }

        // 允许 JS 修改信任值: reward.requiredTrust = 50
        public void setRequiredTrust(int t) { this.original.requiredTrust = t; }
        public int getRequiredTrust() { return this.original.requiredTrust; }

        // 允许 JS 添加物品: reward.add('minecraft:apple')
        public void add(Object item) {
            ItemStack stack = parseItem(item);
            if (!stack.isEmpty()) {
                this.original.items.add(stack);
            } else {
                LOGGER.warn("Failed to add invalid item to reward ID {}", original.id);
            }
        }

        // 允许 JS 清空物品: reward.clearItems()
        public void clearItems() {
            this.original.items.clear();
        }

        // 允许 JS 移除指定索引的物品: reward.remove(0)
        public void remove(int index) {
            if (index >= 0 && index < this.original.items.size()) {
                this.original.items.remove(index);
            }
        }
        
        // 获取只读列表 (可选)
        public List<ItemStack> getItems() { return new ArrayList<>(this.original.items); }
    }

    // --- 事件类 ---
    public static class RegisterRewardsEvent implements KubeEvent {
        public void add(int id, int requiredTrust, Object... itemsIn) {
            List<ItemStack> stackList = new ArrayList<>();
            for (Object obj : itemsIn) {
                ItemStack stack = parseItem(obj);
                if (!stack.isEmpty()) stackList.add(stack);
            }
            HeroRewards.REWARDS.removeIf(r -> r.id == id);
            HeroRewards.REWARDS.add(new HeroRewards.Reward(id, requiredTrust, stackList.toArray(new ItemStack[0])));
        }
        
        public void remove(int id) {
            HeroRewards.REWARDS.removeIf(r -> r.id == id);
        }
        
        // 这里使用了 RewardJS 包装器！
        public void modify(int id, Consumer<RewardJS> modifier) {
            HeroRewards.Reward reward = HeroRewards.getReward(id);
            if (reward != null) {
                // 将包装后的对象传给 JS
                modifier.accept(new RewardJS(reward));
            }
        }
    }

    // [新增] 交易事件类
    public static class HeroTradesEvent implements KubeEvent {
        private final MerchantOffers offers;
        public final HeroEntity hero; // 公开给 JS，可以读取 hero.trustLevel

        public HeroTradesEvent(MerchantOffers offers, HeroEntity hero) {
            this.offers = offers;
            this.hero = hero;
        }

        // 让 JS 获取信任等级: event.getTrustLevel()
        public int getTrustLevel() {
            return hero.getTrustLevel();
        }

        // 移除所有交易
        public void removeAll() {
            offers.clear();
        }
        
        // 简单添加交易: add('5x apple', 'diamond')
        public void add(Object input, Object output) {
            add(input, null, output, 10, 5, 0.05f);
        }

        // 双输入交易: add('5x apple', 'gold_ingot', 'diamond')
        public void add(Object input1, Object input2, Object output) {
            add(input1, input2, output, 10, 5, 0.05f);
        }

        // 完整版添加交易
        public void add(Object input1, Object input2, Object output, int maxUses, int xp, float priceMult) {
            ItemCost costA = parseCost(input1);
            ItemStack result = parseItem(output);
            
            Optional<ItemCost> costB = Optional.empty();
            if (input2 != null) {
                ItemCost c2 = parseCost(input2);
                if (c2.item() != Items.AIR) {
                    costB = Optional.of(c2);
                }
            }

            if (costA.item() != Items.AIR && !result.isEmpty()) {
                MerchantOffer offer = new MerchantOffer(costA, costB, result, maxUses, xp, priceMult);
                offers.add(offer);
            } else {
                LOGGER.warn("Invalid trade skipped: {} + {} -> {}", input1, input2, output);
            }
        }
    }

    // --- 全局对象 (可选，用于调试) ---
    public static class HeroRewardsJS {
        public void add(int id, int trust, Object... items) { new RegisterRewardsEvent().add(id, trust, items); }
        public void remove(int id) { new RegisterRewardsEvent().remove(id); }
    }
}
