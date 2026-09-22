package com.whitecloud233.herobrine_companion.entity.gift;

import net.minecraft.util.RandomSource;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 秘密喜好谜题 —— Bedrock hero_player_offer_secret.py 的纯逻辑移植。
 *
 * <p>每个英雄每桶(plain/care/premium/vile 四轴)秘密偏爱一个槽位(单件物品或一类物品);
 * 玩家凭游戏常识把物品归入轴,递交命中 = 心照。提示收敛式分级:
 * 轴揭示(首条保底)→ 异轴冷提示 → 同轴二分位提示 → 暖提示。
 * 单一职责:纯谜题逻辑;存储(secretFavorites/discoveredFavorites/hintedFavorites/
 * hintProgress)在档案层,编排在 HeroOfferService。
 */
public final class HeroGiftSecret {

    private HeroGiftSecret() {
    }

    public static final List<String> BUCKETS = List.of("plain", "care", "premium", "vile");

    public static final Map<String, String> BUCKET_AXIS = Map.of(
            "plain", "foraged",
            "care", "made",
            "premium", "mineral",
            "vile", "relic");

    private static final List<String> FLOWER_NAMES = List.of(
            "minecraft:dandelion", "minecraft:poppy", "minecraft:blue_orchid",
            "minecraft:allium", "minecraft:azure_bluet", "minecraft:red_tulip",
            "minecraft:orange_tulip", "minecraft:white_tulip", "minecraft:pink_tulip",
            "minecraft:oxeye_daisy", "minecraft:cornflower",
            "minecraft:lily_of_the_valley", "minecraft:wither_rose",
            "minecraft:sunflower", "minecraft:lilac", "minecraft:rose_bush",
            "minecraft:peony");

    private static final List<String> TOOL_MATERIALS = List.of("wooden", "stone", "iron", "golden", "diamond", "netherite");
    private static final List<String> TOOL_KINDS = List.of("sword", "pickaxe", "axe", "shovel", "hoe");

    private static final Set<String> ALL_CRAFTED_TOOLS = buildCraftedTools();

    private static Set<String> buildCraftedTools() {
        Set<String> out = new HashSet<>();
        for (String material : TOOL_MATERIALS) {
            for (String kind : TOOL_KINDS) {
                out.add("minecraft:" + material + "_" + kind);
            }
        }
        out.addAll(Set.of("minecraft:bow", "minecraft:crossbow", "minecraft:arrow",
                "minecraft:shield", "minecraft:shears", "minecraft:fishing_rod"));
        return out;
    }

    /** 槽位:谜底单位(单件物品或一类物品)+ 二分位描述符。 */
    public record Slot(String id, Set<String> items, List<String> bits) {
    }

    public static final Map<String, Map<String, Slot>> SLOTS = buildSlots();

    private static Map<String, Map<String, Slot>> buildSlots() {
        Map<String, Map<String, Slot>> out = new HashMap<>();
        Map<String, Slot> plain = new HashMap<>();
        plain.put("berries", new Slot("berries", Set.of("minecraft:sweet_berries"), List.of("sweet", "plant")));
        plain.put("apple", new Slot("apple", Set.of("minecraft:apple"), List.of("sweet", "plant")));
        plain.put("melon", new Slot("melon", Set.of("minecraft:melon_slice"), List.of("sweet", "plant")));
        plain.put("glow", new Slot("glow", Set.of("minecraft:glow_berries"), List.of("sweet", "plant")));
        plain.put("sugar", new Slot("sugar", Set.of("minecraft:sugar_cane"), List.of("plant")));
        plain.put("flower", new Slot("flower", new HashSet<>(FLOWER_NAMES), List.of("plant", "flower")));
        plain.put("clay", new Slot("clay", Set.of("minecraft:clay_ball"), List.of("soil")));
        plain.put("flint", new Slot("flint", Set.of("minecraft:flint"), List.of("soil")));
        out.put("plain", plain);

        Map<String, Slot> care = new HashMap<>();
        care.put("torch", new Slot("torch", Set.of("minecraft:torch"), List.of("light")));
        care.put("rtorch", new Slot("rtorch", Set.of("minecraft:redstone_torch"), List.of("light")));
        care.put("bread", new Slot("bread", Set.of("minecraft:bread"), List.of("food", "plant_food")));
        care.put("bpotato", new Slot("bpotato", Set.of("minecraft:baked_potato"), List.of("food", "plant_food")));
        care.put("pork", new Slot("pork", Set.of("minecraft:cooked_porkchop"), List.of("food", "meat")));
        care.put("book", new Slot("book", Set.of("minecraft:book"), List.of("paper")));
        care.put("tools", new Slot("tools", ALL_CRAFTED_TOOLS, List.of("tool")));
        out.put("care", care);

        Map<String, Slot> premium = new HashMap<>();
        premium.put("diamond", new Slot("diamond", Set.of("minecraft:diamond"), List.of("gem")));
        premium.put("emerald", new Slot("emerald", Set.of("minecraft:emerald"), List.of("gem")));
        premium.put("iron", new Slot("iron", Set.of("minecraft:iron_ingot"), List.of("ingot")));
        premium.put("gold", new Slot("gold", Set.of("minecraft:gold_ingot"), List.of("ingot")));
        premium.put("copper", new Slot("copper", Set.of("minecraft:copper_ingot"), List.of("ingot")));
        premium.put("lapis", new Slot("lapis", Set.of("minecraft:lapis_lazuli"), List.of("dye")));
        premium.put("amethyst", new Slot("amethyst", Set.of("minecraft:amethyst_shard"), List.of("crystal")));
        premium.put("redstone", new Slot("redstone", Set.of("minecraft:redstone"), List.of("dust")));
        out.put("premium", premium);

        Map<String, Slot> vile = new HashMap<>();
        vile.put("star", new Slot("star", Set.of("minecraft:nether_star"), List.of("craft")));
        vile.put("elytra", new Slot("elytra", Set.of("minecraft:elytra"), List.of("flight")));
        vile.put("totem", new Slot("totem", Set.of("minecraft:totem_of_undying"), List.of("guard")));
        vile.put("breath", new Slot("breath", Set.of("minecraft:dragon_breath"), List.of("craft")));
        vile.put("heart", new Slot("heart", Set.of("minecraft:heart_of_the_sea"), List.of("deep")));
        vile.put("beacon", new Slot("beacon", Set.of("minecraft:beacon"), List.of("guard", "gleam")));
        vile.put("shulker", new Slot("shulker", Set.of("minecraft:shulker_shell"), List.of("craft")));
        vile.put("echo", new Slot("echo", Set.of("minecraft:echo_shard"), List.of("craft", "dark")));
        out.put("vile", vile);
        return out;
    }

    public static final int HINT_COOLDOWN_TICKS = 300;
    public static final double HINT_CHANCE = 0.25;
    public static final int MEMORY_LIMIT = 4;
    public static final int BIT_LIMIT = 8;
    public static final int MISS_LIMIT = 12;

    /** 心照恩典:按已识破桶数(含本次命中)递增的当场 buff 方案(每档仅一次)。 */
    public record GraceBuff(String effect, int duration, int amplifier) {
    }

    private static final List<List<GraceBuff>> GRACE_BUFF_TIERS = List.of(
            List.of(new GraceBuff("absorption", 600, 0)),
            List.of(new GraceBuff("absorption", 720, 0), new GraceBuff("speed", 720, 0)),
            List.of(new GraceBuff("absorption", 840, 1), new GraceBuff("speed", 840, 0),
                    new GraceBuff("fire_resistance", 840, 0)),
            List.of(new GraceBuff("absorption", 960, 1), new GraceBuff("speed", 960, 1),
                    new GraceBuff("fire_resistance", 960, 0), new GraceBuff("night_vision", 960, 0)));

    public static List<GraceBuff> graceBuffPlan(int discoveredCount) {
        int tier = Math.max(1, Math.min(4, Math.max(1, discoveredCount))) - 1;
        return GRACE_BUFF_TIERS.get(tier);
    }

    /** 「心照眷顾」终局被动效果套装。 */
    public static final List<String> NIGHT_GRACE_EFFECTS = List.of(
            "speed", "haste", "strength", "resistance", "fire_resistance",
            "water_breathing", "health_boost", "saturation", "luck");
    public static final int NIGHT_GRACE_DURATION_SECONDS = 36000;
    public static final int NIGHT_GRACE_REAPPLY_SECONDS = 60;

    // ------------------------------------------------------------------
    // 查询
    // ------------------------------------------------------------------

    public static Map<String, Slot> slotsOf(String bucket) {
        return SLOTS.getOrDefault(bucket, Map.of());
    }

    /** 物品在指定桶中的槽位 id;不在该桶返回 null。 */
    public static String slotOfItem(String bucket, String itemName) {
        if (itemName == null || itemName.isEmpty()) {
            return null;
        }
        for (Slot slot : slotsOf(bucket).values()) {
            if (slot.items().contains(itemName)) {
                return slot.id();
            }
        }
        return null;
    }

    /** 物品所属的轴(foraged/made/mineral/relic);不在候选池返回 null。 */
    public static String axisOfItem(String itemName) {
        if (itemName == null || itemName.isEmpty()) {
            return null;
        }
        for (String bucket : BUCKETS) {
            if (slotOfItem(bucket, itemName) != null) {
                return BUCKET_AXIS.get(bucket);
            }
        }
        return null;
    }

    // ------------------------------------------------------------------
    // 谜底生成与归一
    // ------------------------------------------------------------------

    /** 每桶随机一个槽位 id。 */
    public static Map<String, String> generateFavorites(RandomSource random) {
        Map<String, String> out = new HashMap<>();
        for (String bucket : BUCKETS) {
            List<Slot> slots = new ArrayList<>(slotsOf(bucket).values());
            out.put(bucket, slots.get(random.nextInt(slots.size())).id());
        }
        return out;
    }

    /** 槽位 id 白名单;旧档 itemName 若仍属新槽位则迁移为槽位 id。 */
    public static Map<String, String> normalizeFavorites(Object raw) {
        Map<String, String> out = new HashMap<>();
        if (!(raw instanceof Map<?, ?> map)) {
            return out;
        }
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            String bucket = String.valueOf(entry.getKey());
            if (!BUCKETS.contains(bucket)) {
                continue;
            }
            String slotId = String.valueOf(entry.getValue());
            if (slotsOf(bucket).containsKey(slotId)) {
                out.put(bucket, slotId);
                continue;
            }
            for (Slot slot : slotsOf(bucket).values()) {
                if (slot.items().contains(slotId)) {
                    out.put(bucket, slot.id());
                    break;
                }
            }
        }
        return out;
    }

    /** 惰性生成并定型;缺失/失效的桶单独补生成,已有槽位保持不变(每 Hero 一次定型)。 */
    public static Map<String, String> ensureFavorites(Map<String, String> favorites, RandomSource random) {
        Map<String, String> out = normalizeFavorites(favorites);
        boolean missing = false;
        for (String bucket : BUCKETS) {
            if (!out.containsKey(bucket)) {
                missing = true;
                break;
            }
        }
        if (missing) {
            Map<String, String> generated = generateFavorites(random);
            for (String bucket : BUCKETS) {
                out.putIfAbsent(bucket, generated.get(bucket));
            }
        }
        return out;
    }

    /** 递交物命中谜底 → 返回对应的桶;否则 null。 */
    public static String isFavorite(Map<String, String> favorites, String itemName) {
        if (itemName == null || itemName.isEmpty()) {
            return null;
        }
        for (Map.Entry<String, String> entry : normalizeFavorites(favorites).entrySet()) {
            Slot slot = slotsOf(entry.getKey()).get(entry.getValue());
            if (slot != null && slot.items().contains(itemName)) {
                return entry.getKey();
            }
        }
        return null;
    }

    public static String favoriteItem(Map<String, String> favorites, String bucket) {
        Map<String, String> f = normalizeFavorites(favorites);
        Slot slot = slotsOf(bucket).get(f.get(bucket));
        if (slot == null) {
            return "";
        }
        return slot.items().stream().sorted().findFirst().orElse("");
    }

    public static boolean hasUndiscovered(Map<String, String> favorites, List<String> discovered) {
        Set<String> d = new HashSet<>(discovered == null ? List.of() : discovered);
        Map<String, String> f = normalizeFavorites(favorites);
        for (String bucket : BUCKETS) {
            if (f.containsKey(bucket) && !d.contains(bucket)) {
                return true;
            }
        }
        return false;
    }

    public static boolean hasUnhintedBucket(Map<String, String> favorites, List<String> discovered, List<String> hinted) {
        Set<String> d = new HashSet<>(discovered == null ? List.of() : discovered);
        Set<String> h = new HashSet<>(hinted == null ? List.of() : hinted);
        Map<String, String> f = normalizeFavorites(favorites);
        for (String bucket : BUCKETS) {
            if (f.containsKey(bucket) && !d.contains(bucket) && !h.contains(bucket)) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------
    // 提示进度
    // ------------------------------------------------------------------

    /** 每桶提示状态:已揭示 bit、已剔除槽位、miss 连胜。 */
    public record HintState(List<String> bits, List<String> missedSlots, int streak) {
        public static HintState fresh() {
            return new HintState(new ArrayList<>(), new ArrayList<>(), 0);
        }
    }

    public static Map<String, HintState> normalizeHintProgress(Object raw) {
        Map<String, HintState> out = new HashMap<>();
        if (!(raw instanceof Map<?, ?> map)) {
            return out;
        }
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            String bucket = String.valueOf(entry.getKey());
            if (!BUCKETS.contains(bucket) || !(entry.getValue() instanceof Map<?, ?> state)) {
                continue;
            }
            Set<String> validBits = new HashSet<>();
            for (Slot slot : slotsOf(bucket).values()) {
                validBits.addAll(slot.bits());
            }
            List<String> bits = new ArrayList<>();
            for (Object bit : asList(state.get("bits"))) {
                String b = String.valueOf(bit);
                if (validBits.contains(b) && !bits.contains(b)) {
                    bits.add(b);
                }
            }
            List<String> missed = new ArrayList<>();
            for (Object sid : asList(state.get("missedSlots"))) {
                String s = String.valueOf(sid);
                if (slotsOf(bucket).containsKey(s) && !missed.contains(s)) {
                    missed.add(s);
                }
            }
            int streak = Math.max(0, Math.min(16, toInt(state.get("streak"))));
            out.put(bucket, new HintState(
                    bits.subList(0, Math.min(BIT_LIMIT, bits.size())),
                    missed.subList(0, Math.min(MISS_LIMIT, missed.size())),
                    streak));
        }
        return out;
    }

    /** 记一次未命中:递交物槽位 ≠ 谜底槽位 → 从候选集剔除。 */
    public static void recordMiss(Map<String, HintState> progress, Map<String, String> favorites,
                                  String itemName) {
        if (itemName == null || itemName.isEmpty()) {
            return;
        }
        Map<String, String> f = normalizeFavorites(favorites);
        for (String bucket : BUCKETS) {
            String secretSlot = f.get(bucket);
            String sid = slotOfItem(bucket, itemName);
            if (sid == null || sid.equals(secretSlot)) {
                continue;
            }
            HintState state = progress.computeIfAbsent(bucket, k -> HintState.fresh());
            List<String> missed = new ArrayList<>(state.missedSlots());
            if (!missed.contains(sid)) {
                missed.add(sid);
                progress.put(bucket, new HintState(state.bits(),
                        missed.subList(0, Math.min(MISS_LIMIT, missed.size())),
                        Math.min(16, state.streak() + 1)));
            }
        }
    }

    public static boolean aliveFocus(Map<String, String> favorites, List<String> discovered, String focus) {
        if (focus == null || !BUCKETS.contains(focus) || !normalizeFavorites(favorites).containsKey(focus)) {
            return false;
        }
        return !(discovered != null && discovered.contains(focus));
    }

    private static String focusPick(String offerItemName, Map<String, String> favorites,
                                    List<String> discovered, List<String> hinted, RandomSource random) {
        Map<String, String> f = normalizeFavorites(favorites);
        Set<String> d = new HashSet<>(discovered == null ? List.of() : discovered);
        List<String> undiscovered = new ArrayList<>();
        for (String bucket : BUCKETS) {
            if (f.containsKey(bucket) && !d.contains(bucket)) {
                undiscovered.add(bucket);
            }
        }
        if (undiscovered.isEmpty()) {
            return null;
        }
        Set<String> h = new HashSet<>(hinted == null ? List.of() : hinted);
        String offerAxis = axisOfItem(offerItemName);
        List<String> matchedUnhinted = new ArrayList<>();
        if (offerAxis != null) {
            for (String bucket : undiscovered) {
                if (!h.contains(bucket) && offerAxis.equals(BUCKET_AXIS.get(bucket))) {
                    matchedUnhinted.add(bucket);
                }
            }
        }
        if (!matchedUnhinted.isEmpty()) {
            return matchedUnhinted.get(random.nextInt(matchedUnhinted.size()));
        }
        List<String> unhinted = new ArrayList<>();
        for (String bucket : undiscovered) {
            if (!h.contains(bucket)) {
                unhinted.add(bucket);
            }
        }
        if (!unhinted.isEmpty()) {
            return unhinted.get(random.nextInt(unhinted.size()));
        }
        List<String> matched = new ArrayList<>();
        if (offerAxis != null) {
            for (String bucket : undiscovered) {
                if (offerAxis.equals(BUCKET_AXIS.get(bucket))) {
                    matched.add(bucket);
                }
            }
        }
        if (!matched.isEmpty()) {
            return matched.get(random.nextInt(matched.size()));
        }
        return undiscovered.get(random.nextInt(undiscovered.size()));
    }

    /** 本次 miss 是否应保底给一条线索(不受冷却/掷骰影响)。 */
    public static boolean guaranteePending(String offerItemName, Map<String, String> favorites,
                                           List<String> discovered, List<String> hinted,
                                           String focus, Map<String, HintState> progress) {
        if (!hasUndiscovered(favorites, discovered)) {
            return false;
        }
        Set<String> h = new HashSet<>(hinted == null ? List.of() : hinted);
        if (aliveFocus(favorites, discovered, focus)) {
            if (!h.contains(focus)) {
                return true;
            }
            HintState state = progress == null ? null : progress.get(focus);
            return state != null && state.streak() >= 2;
        }
        Map<String, String> f = normalizeFavorites(favorites);
        for (String bucket : BUCKETS) {
            if (f.containsKey(bucket) && !h.contains(bucket)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 为一次 miss 选择线索池。
     *
     * @return (poolKey, targetBucket, revealAxis);全识破时 (null,null,false)
     */
    public static HintPick pickHint(String offerItemName, Map<String, String> favorites,
                                    List<String> discovered, RandomSource random,
                                    List<String> hinted, Map<String, HintState> progress,
                                    String focus) {
        if (offerItemName == null || offerItemName.isEmpty() || !hasUndiscovered(favorites, discovered)) {
            return new HintPick(null, null, false);
        }
        Set<String> h = new HashSet<>(hinted == null ? List.of() : hinted);
        String bucket;
        if (aliveFocus(favorites, discovered, focus)) {
            bucket = focus;
        } else {
            bucket = focusPick(offerItemName, favorites, discovered, hinted, random);
        }
        if (bucket == null) {
            return new HintPick(null, null, false);
        }
        Map<String, HintState> prog = progress == null ? new HashMap<>() : progress;
        if (!h.contains(bucket)) {
            if (offerIsOnAxis(offerItemName, bucket)) {
                return new HintPick("hint_axis_" + BUCKET_AXIS.get(bucket), bucket, true);
            }
            return new HintPick("hint_axis_else_" + BUCKET_AXIS.get(bucket), bucket, true);
        }
        if (!offerIsOnAxis(offerItemName, bucket)) {
            return new HintPick("hint_cold", bucket, false);
        }
        Map<String, String> f = normalizeFavorites(favorites);
        Slot target = slotsOf(bucket).get(f.get(bucket));
        if (target == null) {
            return new HintPick("hint_warm", bucket, false);
        }
        HintState state = prog.computeIfAbsent(bucket, k -> HintState.fresh());
        Set<String> missed = new HashSet<>(state.missedSlots());
        List<Slot> remaining = new ArrayList<>();
        for (Slot slot : slotsOf(bucket).values()) {
            if (!missed.contains(slot.id())) {
                remaining.add(slot);
            }
        }
        if (remaining.size() <= 1) {
            return new HintPick("hint_warm", bucket, false);
        }
        Set<String> revealed = new HashSet<>(state.bits());
        String best = null;
        int bestScore = -1;
        for (String bit : target.bits()) {
            if (revealed.contains(bit)) {
                continue;
            }
            int withBit = 0;
            for (Slot slot : remaining) {
                if (slot.bits().contains(bit)) {
                    withBit++;
                }
            }
            int without = remaining.size() - withBit;
            if (withBit == 0 || without == 0) {
                continue;
            }
            int score = Math.min(withBit, without);
            if (score > bestScore) {
                best = bit;
                bestScore = score;
            }
        }
        if (best == null) {
            return new HintPick("hint_warm", bucket, false);
        }
        List<String> bits = new ArrayList<>(state.bits());
        bits.add(best);
        progress.put(bucket, new HintState(bits, state.missedSlots(), state.streak()));
        return new HintPick("hint_bit_" + best, bucket, false);
    }

    /** pickHint 的返回值。 */
    public record HintPick(String poolKey, String targetBucket, boolean revealAxis) {
    }

    private static boolean offerIsOnAxis(String offerItemName, String bucket) {
        String axis = axisOfItem(offerItemName);
        return axis != null && axis.equals(BUCKET_AXIS.get(bucket));
    }

    private static List<?> asList(Object value) {
        if (value instanceof List<?> list) {
            return list;
        }
        return List.of();
    }

    private static int toInt(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (Exception e) {
            return 0;
        }
    }
}