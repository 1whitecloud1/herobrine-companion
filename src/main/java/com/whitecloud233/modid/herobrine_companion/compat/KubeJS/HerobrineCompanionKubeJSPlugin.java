package com.whitecloud233.modid.herobrine_companion.compat.KubeJS;

import com.whitecloud233.modid.herobrine_companion.entity.HeroEntity;
import com.whitecloud233.modid.herobrine_companion.event.HeroRewards;
import com.whitecloud233.modid.herobrine_companion.event.HeroContentLimits;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import dev.latvian.mods.kubejs.KubeJSPlugin;
import dev.latvian.mods.kubejs.event.EventGroup;
import dev.latvian.mods.kubejs.event.EventHandler;
import dev.latvian.mods.kubejs.event.EventJS;
import dev.latvian.mods.kubejs.item.ItemStackJS;
import dev.latvian.mods.kubejs.script.BindingsEvent;
import dev.latvian.mods.kubejs.script.ScriptType;

public final class HerobrineCompanionKubeJSPlugin extends KubeJSPlugin {
    public static final EventGroup GROUP = EventGroup.of("HerobrineCompanionEvents");
    public static final EventHandler REGISTER_REWARDS = GROUP.server("registerRewards", () -> RegisterRewardsEvent.class);
    public static final EventHandler REGISTER_TRADES = GROUP.server("registerTrades", () -> HeroTradesEvent.class);
    private static final ThreadLocal<RegisterRewardsEvent> ACTIVE_REWARDS = new ThreadLocal<>();

    @Override
    public void registerEvents() { GROUP.register(); }

    @Override
    public void registerBindings(BindingsEvent event) {
        if (event.getType() == ScriptType.SERVER) event.add("HeroRewards", new HeroRewardsJS());
    }

    public static void reloadRewards() {
        RegisterRewardsEvent event = new RegisterRewardsEvent(HeroRewards.defaults());
        ACTIVE_REWARDS.set(event);
        try {
            if (REGISTER_REWARDS.hasListeners() && REGISTER_REWARDS.post(ScriptType.SERVER, event).error()) {
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
            MerchantOffers edited = new MerchantOffers(offers.createTag());
            var result = REGISTER_TRADES.post(ScriptType.SERVER, new HeroTradesEvent(edited, hero));
            if (!result.error()) { offers.clear(); offers.addAll(edited); }
        }
    }

    public static ItemStack parseItem(Object value) {
        return HeroContentLimits.item(ItemStackJS.of(value));
    }

    public static ItemStack parseCost(Object value) { return HeroContentLimits.tradeItem(parseItem(value)); }

    public static final class RewardJS {
        private final HeroRewards.Reward reward;
        private RewardJS(HeroRewards.Reward reward) { this.reward = reward; }

        public void setRequiredTrust(int value) { reward.requiredTrust = HeroContentLimits.trust(value); }
        public int getRequiredTrust() { return reward.requiredTrust; }
        public void add(Object value) {
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

    public static final class RegisterRewardsEvent extends EventJS {
        private final List<HeroRewards.Reward> rewards;
        public RegisterRewardsEvent() { this(HeroRewards.snapshot()); }
        private RegisterRewardsEvent(List<HeroRewards.Reward> rewards) { this.rewards = new ArrayList<>(rewards); }

        public void add(int id, int requiredTrust, Object... values) {
            HeroContentLimits.rewardId(id);
            HeroContentLimits.trust(requiredTrust);
            if (values == null || values.length > HeroContentLimits.MAX_ITEMS_PER_REWARD) throw new IllegalArgumentException("Too many reward items");
            List<ItemStack> items = new ArrayList<>();
            for (Object value : values) items.add(parseItem(value));
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

    public static final class HeroTradesEvent extends EventJS {
        private final MerchantOffers offers;
        public final HeroEntity hero;
        public HeroTradesEvent(MerchantOffers offers, HeroEntity hero) { this.offers = offers; this.hero = hero; }
        public int getTrustLevel() { return hero.getTrustLevel(); }
        public void removeAll() { offers.clear(); }
        public void remove(int index) {
            if (index < 0 || index >= offers.size()) throw new IllegalArgumentException("Invalid trade index: " + index);
            offers.remove(index);
        }
        public void add(Object input, Object output) { add(input, null, output, 10, 5, 0.05f); }
        public void add(Object input1, Object input2, Object output) { add(input1, input2, output, 10, 5, 0.05f); }
        public void add(Object input1, Object input2, Object output, int maxUses, int xp, float priceMult) {
            HeroContentLimits.trade(maxUses, xp, priceMult);
            if (offers.size() >= HeroContentLimits.MAX_TRADES) throw new IllegalArgumentException("Too many trades");
            ItemStack first = parseCost(input1);
            ItemStack second = input2 == null ? ItemStack.EMPTY : parseCost(input2);
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
        public void add(int id, int trust, Object... items) { active().add(id, trust, items); }
        public void remove(int id) { active().remove(id); }
    }
}
