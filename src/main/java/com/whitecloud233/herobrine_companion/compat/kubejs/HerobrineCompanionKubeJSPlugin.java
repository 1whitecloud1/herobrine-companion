package com.whitecloud233.herobrine_companion.compat.kubejs;

import com.whitecloud233.herobrine_companion.entity.HeroEntity;
import com.whitecloud233.herobrine_companion.event.HeroRewards;
import com.whitecloud233.herobrine_companion.event.HeroContentLimits;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import dev.latvian.mods.kubejs.event.EventGroup;
import dev.latvian.mods.kubejs.event.EventGroupRegistry;
import dev.latvian.mods.kubejs.event.EventHandler;
import dev.latvian.mods.kubejs.event.KubeEvent;
import dev.latvian.mods.kubejs.plugin.KubeJSPlugin;
import dev.latvian.mods.kubejs.script.BindingRegistry;
import dev.latvian.mods.kubejs.script.ScriptType;
import net.minecraft.core.component.DataComponentPredicate;
import net.minecraft.world.item.trading.ItemCost;
import java.util.Optional;

public final class HerobrineCompanionKubeJSPlugin implements KubeJSPlugin {
    public static final EventGroup GROUP = EventGroup.of("HerobrineCompanionEvents");
    public static final EventHandler REGISTER_REWARDS = GROUP.server("registerRewards", () -> RegisterRewardsEvent.class);
    public static final EventHandler REGISTER_TRADES = GROUP.server("registerTrades", () -> HeroTradesEvent.class);
    private static final ThreadLocal<RegisterRewardsEvent> ACTIVE_REWARDS = new ThreadLocal<>();

    @Override
    public void registerEvents(EventGroupRegistry registry) { registry.register(GROUP); }

    @Override
    public void registerBindings(BindingRegistry bindings) {
        if (bindings.type() == ScriptType.SERVER) bindings.add("HeroRewards", new HeroRewardsJS());
    }

    public static void reloadRewards() {
        RegisterRewardsEvent event = new RegisterRewardsEvent(HeroRewards.defaults());
        ACTIVE_REWARDS.set(event);
        try {
            if (REGISTER_REWARDS.hasListeners() && REGISTER_REWARDS.post(ScriptType.SERVER, event).type() == dev.latvian.mods.kubejs.event.EventResult.Type.ERROR) {
                throw new IllegalStateException("Reward script failed; keeping the previous definition");
            }
            HeroRewards.replaceAll(event.rewards);
        } finally {
            ACTIVE_REWARDS.remove();
        }
    }

    public static void fireTradeEvent(MerchantOffers offers, HeroEntity hero) {
        if (hero == null || hero.level().isClientSide) return;
        if (REGISTER_TRADES.hasListeners()) {
            MerchantOffers edited = offers.copy();
            var result = REGISTER_TRADES.post(ScriptType.SERVER, new HeroTradesEvent(edited, hero));
            if (result.type() != dev.latvian.mods.kubejs.event.EventResult.Type.ERROR) { offers.clear(); offers.addAll(edited); }
        }
    }

    public static ItemStack parseItem(ItemStack value) {
        return HeroContentLimits.item(value);
    }

    public static ItemCost parseCost(ItemStack value) {
        ItemStack stack = HeroContentLimits.tradeItem(parseItem(value));
        return new ItemCost(stack.getItemHolder(), stack.getCount(),
                DataComponentPredicate.allOf(stack.getComponentsPatch().split().added()));
    }

    public static final class RewardJS {
        private final HeroRewards.Reward reward;
        private RewardJS(HeroRewards.Reward reward) { this.reward = reward; }

        public void setRequiredTrust(int value) { reward.requiredTrust = HeroContentLimits.trust(value); }
        public int getRequiredTrust() { return reward.requiredTrust; }
        public void add(ItemStack value) {
            if (reward.items.size() >= HeroContentLimits.MAX_ITEMS_PER_REWARD) throw new IllegalArgumentException("Too many reward items");
            reward.items.add(parseItem(value));
        }
        public void clearItems() { reward.items.clear(); }
        public void remove(int index) {
            if (index < 0 || index >= reward.items.size()) throw new IllegalArgumentException("Invalid reward item index: " + index);
            reward.items.remove(index);
        }
        public List<ItemStack> getItems() { return reward.items.stream().map(ItemStack::copy).toList(); }
    }

    public static final class RegisterRewardsEvent implements KubeEvent {
        private final List<HeroRewards.Reward> rewards;
        public RegisterRewardsEvent() { this(HeroRewards.snapshot()); }
        private RegisterRewardsEvent(List<HeroRewards.Reward> rewards) { this.rewards = new ArrayList<>(rewards); }

        public void add(int id, int requiredTrust, ItemStack... values) {
            HeroContentLimits.rewardId(id);
            HeroContentLimits.trust(requiredTrust);
            if (values == null || values.length > HeroContentLimits.MAX_ITEMS_PER_REWARD) throw new IllegalArgumentException("Too many reward items");
            List<ItemStack> items = new ArrayList<>();
            for (ItemStack value : values) items.add(parseItem(value));
            if (rewards.stream().noneMatch(r -> r.id == id) && rewards.size() >= HeroContentLimits.MAX_REWARDS) throw new IllegalArgumentException("Too many rewards");
            rewards.removeIf(r -> r.id == id);
            rewards.add(new HeroRewards.Reward(id, requiredTrust, items.toArray(ItemStack[]::new)));
        }
        public void remove(int id) { HeroContentLimits.rewardId(id); rewards.removeIf(r -> r.id == id); }
        public void removeAll() { rewards.clear(); }
        public void modify(int id, Consumer<RewardJS> modifier) {
            HeroRewards.Reward original = rewards.stream().filter(r -> r.id == id).findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Unknown reward ID: " + id));
            if (modifier == null) throw new IllegalArgumentException("Missing reward modifier");
            HeroRewards.Reward edited = HeroRewards.copy(original);
            modifier.accept(new RewardJS(edited));
            rewards.set(rewards.indexOf(original), edited);
        }
    }

    public static final class HeroTradesEvent implements KubeEvent {
        private final MerchantOffers offers;
        public final HeroEntity hero;
        public HeroTradesEvent(MerchantOffers offers, HeroEntity hero) { this.offers = offers; this.hero = hero; }
        public int getTrustLevel() { return hero.getTrustLevel(); }
        public void removeAll() { offers.clear(); }
        public void remove(int index) {
            if (index < 0 || index >= offers.size()) throw new IllegalArgumentException("Invalid trade index: " + index);
            offers.remove(index);
        }
        public void add(ItemStack input, ItemStack output) { add(input, null, output, 10, 5, 0.05f); }
        public void add(ItemStack input1, ItemStack input2, ItemStack output) { add(input1, input2, output, 10, 5, 0.05f); }
        public void add(ItemStack input1, ItemStack input2, ItemStack output, int maxUses, int xp, float priceMult) {
            HeroContentLimits.trade(maxUses, xp, priceMult);
            if (offers.size() >= HeroContentLimits.MAX_TRADES) throw new IllegalArgumentException("Too many trades");
            ItemCost first = parseCost(input1);
            Optional<ItemCost> second = input2 == null ? Optional.empty() : Optional.of(parseCost(input2));
            MerchantOffer offer = new MerchantOffer(first, second, HeroContentLimits.tradeItem(parseItem(output)), maxUses, xp, priceMult);
            offers.add(offer);
        }
    }

    public static final class HeroRewardsJS {
        private RegisterRewardsEvent active() {
            RegisterRewardsEvent event = ACTIVE_REWARDS.get();
            if (event == null) throw new IllegalStateException("Use HeroRewards inside HerobrineCompanionEvents.registerRewards");
            return event;
        }
        public void add(int id, int trust, ItemStack... items) { active().add(id, trust, items); }
        public void remove(int id) { active().remove(id); }
    }
}
