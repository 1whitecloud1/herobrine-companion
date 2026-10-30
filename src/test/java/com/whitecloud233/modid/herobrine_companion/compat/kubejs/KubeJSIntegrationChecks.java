package com.whitecloud233.modid.herobrine_companion.compat.kubejs;

import com.whitecloud233.modid.herobrine_companion.regression.SourceFixturesChecks;
import java.nio.file.Files;
import java.nio.file.Path;

public final class KubeJSIntegrationChecks {
    public static void main(String[] args) throws Exception {
        Path root = Path.of(args[0]);
        String base = "src/main/java/com/whitecloud233/modid/herobrine_companion/";
        String limits = Files.readString(root.resolve(base + "event/HeroContentLimits.java"));
        String rewards = Files.readString(root.resolve(base + "event/HeroRewards.java"));
        String plugin = Files.readString(root.resolve(base + "compat/KubeJS/HerobrineCompanionKubeJSPlugin.java"));
        limits = nested(limits);
        rewards = nested(rewards);
        int start = rewards.indexOf("    public static List<Reward> defaults() {");
        int end = rewards.indexOf("    public static Reward copy(", start);
        rewards = rewards.substring(0, start) + "public static List<Reward> defaults() { return List.of(new Reward(2, 30, new ItemStack(32))); }\n" + rewards.substring(end);
        plugin = nested(plugin);
        String source = "import java.util.*; import java.util.function.*; public class KubeFixture {" + STUBS + limits + rewards + plugin + TESTS + "}";
        try (var compiled = SourceFixturesChecks.compile("KubeFixture", source)) {
            int count = (int) compiled.type().getMethod("run").invoke(null);
            System.out.println("KUBEJS_INTEGRATION_OK: " + count + " production-method checks");
        }
        String main = Files.readString(root.resolve(base + "HerobrineCompanion.java"));
        check(!main.contains("kubejs.plugin.KubeJSPlugin") && !main.contains("EVENT_BUS.register(HerobrineCompanionKubeJSPlugin"), "Main must not guess KubeJS class paths");
        String hook = Files.readString(root.resolve(base + "event/HeroContentReload.java"));
        check(hook.contains("SERVER_DATA_LOAD") && hook.contains("HeroTrades.invalidate()") && hook.contains("OnDatapackSyncEvent"), "Reload and datapack sync hooks missing");
        String cache = Files.readString(root.resolve(base + "entity/logic/data/HeroTradeHandler.java"));
        check(cache.contains("revision != HeroTrades.revision()"), "Existing entities must invalidate trade caches");
        String screen = Files.readString(root.resolve(base + "client/gui/HeroRewardScreen.java"));
        check(screen.contains("ClientHeroContent.rewards()") && !screen.contains("hero.claimReward(reward.id)"), "Reward screen must use server content and acknowledgement");
        String jei = Files.readString(root.resolve(base + "compat/jei/HerobrineJeiPlugin.java"));
        check(!jei.contains("HeroRewards.reset()") && !jei.contains("HeroTrades.getOffers(dummyHero)") && jei.contains("hideRecipes"), "JEI must not reset rewards or execute server scripts");
        String manifest = Files.readString(root.resolve("src/main/resources/kubejs.plugins.txt"));
        check(manifest.trim().equals("com.whitecloud233.modid.herobrine_companion.compat.KubeJS.HerobrineCompanionKubeJSPlugin"), "Plugin discovery mismatch");
        System.out.println("KUBEJS_ARCHITECTURE_OK: 6 checks");
    }
    private static String nested(String source) {
        return source.replaceFirst("(?m)^package [^;]+;", "")
                .replaceAll("(?m)^import [^;]+;", "")
                .replace("public final class ", "public static final class ")
                .replace("public class HeroRewards", "public static class HeroRewards");
    }
    private static void check(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
    private static final String STUBS = """
        static class Logger { void info(Object... args) {} }
        static class LoggerFactory { static Logger getLogger(String name) { return new Logger(); } }
        static class ItemStack {
            static final ItemStack EMPTY = new ItemStack(0);
            int count; String metadata;
            ItemStack(int count) { this.count = count; }
            boolean isEmpty() { return count < 1; }
            int getCount() { return count; }
            int getMaxStackSize() { return 64; }
            ItemStack copy() { ItemStack result = new ItemStack(count); result.metadata = metadata; return result; }
            Object getItemHolder() { return "item"; }
            Patch getComponentsPatch() { return new Patch(metadata); }
        }
        record Patch(String value) { Patch split() { return this; } String added() { return value; } }
        static class DataComponentPredicate { static String allOf(String value) { return value; } }
        record ItemCost(Object item, int count, String components) {}
        static class ItemStackJS { static ItemStack of(Object value) { return value instanceof ItemStack item ? item : ItemStack.EMPTY; } }
        static class MerchantOffer {
            ItemStack result;
            Object first;
            MerchantOffer(Object first, Object second, ItemStack result, int uses, int xp, float multiplier) { this.first = first; this.result = result; }
        }
        static class MerchantOffers extends ArrayList<MerchantOffer> {
            MerchantOffers() {}
            MerchantOffers(MerchantOffers original) { addAll(original); }
            MerchantOffers createTag() { return this; }
            MerchantOffers copy() { return new MerchantOffers(this); }
        }
        static class HeroEntity { Level level = new Level(); Level level() { return level; } int getTrustLevel() { return 100; } }
        static class Level { boolean isClientSide; }
        enum ScriptType { SERVER, CLIENT }
        static class BindingRegistry { ScriptType type() { return ScriptType.SERVER; } void add(String name, Object value) {} }
        static class BindingsEvent { ScriptType getType() { return ScriptType.SERVER; } void add(String name, Object value) {} }
        static class EventGroupRegistry { void register(EventGroup value) {} }
        static class KubeJSPlugin { public void registerEvents() {} public void registerBindings(BindingsEvent event) {} }
        interface NeoKubeJSPlugin { default void registerEvents(EventGroupRegistry registry) {} default void registerBindings(BindingRegistry bindings) {} }
        interface KubeEvent {}
        static class EventJS {}
        static class EventResult {
            enum Type { PASS, ERROR }
            boolean failed;
            EventResult(boolean failed) { this.failed = failed; }
            boolean error() { return failed; }
            Type type() { return failed ? Type.ERROR : Type.PASS; }
        }
        static class EventGroup {
            static EventGroup of(String name) { return new EventGroup(); }
            EventHandler server(String name, Supplier<?> type) { return new EventHandler(); }
            void register() {}
        }
        static class EventHandler {
            Consumer<Object> callback;
            boolean hasListeners() { return callback != null; }
            EventResult post(ScriptType type, Object event) {
                try { callback.accept(event); return new EventResult(false); }
                catch (RuntimeException e) { return new EventResult(true); }
            }
        }
        """;
    private static final String TESTS = """
        static int checks;
        static void check(boolean ok) { if (!ok) throw new AssertionError("check " + checks); checks++; }
        static void rejects(Runnable call) { try { call.run(); throw new AssertionError("Expected validation failure"); } catch (IllegalArgumentException | IllegalStateException expected) { checks++; } }
        public static int run() {
            HeroRewards.reset();
            check(HeroRewards.getReward(2).requiredTrust == 30);
            HerobrineCompanionKubeJSPlugin.REGISTER_REWARDS.callback = raw -> {
                var event = (HerobrineCompanionKubeJSPlugin.RegisterRewardsEvent) raw;
                event.modify(2, reward -> { reward.setRequiredTrust(5); reward.add(new ItemStack(3)); });
                event.add(100, 10, new ItemStack(1));
            };
            HerobrineCompanionKubeJSPlugin.reloadRewards();
            HerobrineCompanionKubeJSPlugin.reloadRewards();
            check(HeroRewards.REWARDS.size() == 2 && HeroRewards.getReward(2).items.size() == 2);
            check(HeroRewards.getReward(2).requiredTrust == 5);
            var snapshot = HeroRewards.snapshot();
            snapshot.get(0).items.get(0).count = 1;
            check(HeroRewards.getReward(2).items.get(0).count == 32);
            HerobrineCompanionKubeJSPlugin.REGISTER_REWARDS.callback = raw -> {
                var event = (HerobrineCompanionKubeJSPlugin.RegisterRewardsEvent) raw;
                event.removeAll(); throw new IllegalArgumentException("broken script");
            };
            rejects(HerobrineCompanionKubeJSPlugin::reloadRewards);
            check(HeroRewards.REWARDS.size() == 2);
            HerobrineCompanionKubeJSPlugin.REGISTER_REWARDS.callback = raw -> ((HerobrineCompanionKubeJSPlugin.RegisterRewardsEvent) raw).removeAll();
            HerobrineCompanionKubeJSPlugin.reloadRewards();
            check(HeroRewards.REWARDS.isEmpty());
            HerobrineCompanionKubeJSPlugin.REGISTER_REWARDS.callback = null;
            HerobrineCompanionKubeJSPlugin.reloadRewards();
            check(HeroRewards.REWARDS.size() == 1);
            rejects(() -> new HerobrineCompanionKubeJSPlugin.HeroRewardsJS().add(1, 0, new ItemStack(1)));
            var event = new HerobrineCompanionKubeJSPlugin.RegisterRewardsEvent();
            rejects(() -> event.add(-1, 0, new ItemStack(1)));
            rejects(() -> event.add(1, -1, new ItemStack(1)));
            rejects(() -> event.add(1, 0, ItemStack.EMPTY));
            rejects(() -> event.modify(999, reward -> {}));
            rejects(() -> event.modify(2, reward -> reward.setRequiredTrust(-1)));
            rejects(() -> HeroContentLimits.trade(0, 1, .05f));
            rejects(() -> HeroContentLimits.trade(1, -1, .05f));
            rejects(() -> HeroContentLimits.trade(1, 0, Float.NaN));
            rejects(() -> HeroContentLimits.trade(1, 0, Float.POSITIVE_INFINITY));
            rejects(() -> HeroContentLimits.item(new ItemStack(65)));
            ItemStack named = new ItemStack(1); named.metadata = "enchanted";
            ItemStack copy = HerobrineCompanionKubeJSPlugin.parseItem(named);
            check(copy != named && copy.metadata.equals("enchanted"));
            check(HerobrineCompanionKubeJSPlugin.parseCost(named).metadata.equals("enchanted"));
            MerchantOffers offers = new MerchantOffers();
            HeroEntity hero = new HeroEntity();
            HerobrineCompanionKubeJSPlugin.REGISTER_TRADES.callback = raw -> {
                var trade = (HerobrineCompanionKubeJSPlugin.HeroTradesEvent) raw;
                trade.add(new ItemStack(2), new ItemStack(1));
            };
            HerobrineCompanionKubeJSPlugin.fireTradeEvent(offers, hero);
            check(offers.size() == 1);
            hero.level.isClientSide = true;
            HerobrineCompanionKubeJSPlugin.fireTradeEvent(offers, hero);
            check(offers.size() == 1);
            hero.level.isClientSide = false;
            HerobrineCompanionKubeJSPlugin.REGISTER_TRADES.callback = raw -> {
                var trade = (HerobrineCompanionKubeJSPlugin.HeroTradesEvent) raw;
                trade.removeAll(); throw new IllegalStateException("broken trade");
            };
            HerobrineCompanionKubeJSPlugin.fireTradeEvent(offers, hero);
            check(offers.size() == 1);
            rejects(() -> new HerobrineCompanionKubeJSPlugin.HeroTradesEvent(offers, hero).add(new ItemStack(1), null, new ItemStack(1), 0, 0, .1f));
            return checks;
        }
        """;
}
