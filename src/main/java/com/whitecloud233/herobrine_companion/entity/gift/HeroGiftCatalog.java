package com.whitecloud233.herobrine_companion.entity.gift;

import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 赠礼物品静态分类目录 —— Bedrock hero_player_offer_catalog.py 的直接移植。
 *
 * <p>单一职责:纯分类与数据表。输入 ItemStack(等价 Bedrock item dict),输出
 * 分类结果与各项基础分;不感知存档/网络/表现。物品名统一为
 * "namespace:path"(minecraft:diamond / herobrine_companion:glitch_fragment),
 * 与 Bedrock 表逐字一致,便于对照维护。
 */
public final class HeroGiftCatalog {

    private HeroGiftCatalog() {
    }

    // ---- 等级表 ----
    /** 至珍:第一次递来会让祂改口索取,而不是收下。 */
    public static final Set<String> VILE_TIER_NAMES = Set.of(
            "minecraft:nether_star", "minecraft:enchanted_golden_apple",
            "minecraft:totem_of_undying", "minecraft:beacon", "minecraft:dragon_breath",
            "minecraft:elytra", "minecraft:heart_of_the_sea");

    /** 贵重:不天然加分,但会换来祂的态度与怀疑。 */
    public static final Set<String> PREMIUM_TIER_NAMES = Set.of(
            "minecraft:diamond", "minecraft:emerald", "minecraft:amethyst_shard",
            "minecraft:lapis_lazuli", "minecraft:netherite_ingot", "minecraft:netherite_scrap",
            "minecraft:iron_ingot", "minecraft:gold_ingot", "minecraft:copper_ingot",
            "minecraft:golden_apple", "minecraft:golden_carrot", "minecraft:echo_shard",
            "minecraft:lodestone", "minecraft:experience_bottle");

    private static final Set<String> PREMIUM_BLOCK_NAMES = Set.of(
            "minecraft:coal_block", "minecraft:iron_block", "minecraft:gold_block",
            "minecraft:diamond_block", "minecraft:emerald_block", "minecraft:lapis_block",
            "minecraft:copper_block", "minecraft:netherite_block", "minecraft:amethyst_block",
            "minecraft:quartz_block");

    // ---- 回礼池:谦卑、与礼物类别同主题,绝不发高价值物 ----
    public static final Map<String, String[]> RETURN_GIFT_POOLS = mapOf(
            "old", new String[]{"minecraft:bread", "minecraft:torch", "minecraft:book", "minecraft:iron_pickaxe"},
            "memory", new String[]{"minecraft:bread", "minecraft:torch", "minecraft:book", "minecraft:iron_pickaxe"},
            "repair", new String[]{"minecraft:oak_sapling", "minecraft:torch", "minecraft:redstone_torch", "minecraft:dandelion"},
            "night", new String[]{"minecraft:ender_pearl", "minecraft:gunpowder", "minecraft:bone"},
            "rift", new String[]{"minecraft:chorus_fruit", "minecraft:ender_pearl"},
            "cooked", new String[]{"minecraft:bread", "minecraft:cooked_porkchop", "minecraft:beetroot_seeds"},
            "raw", new String[]{"minecraft:bread", "minecraft:beetroot_seeds"},
            "rotten", new String[]{"minecraft:bone", "minecraft:gunpowder"},
            "golden", new String[]{"minecraft:bread"},
            "destructive", new String[]{"minecraft:torch", "minecraft:bread"},
            "default", new String[]{"minecraft:torch", "minecraft:bread"});

    /** 食物规则。 */
    public record FoodRule(String category, int warmth, int restraint, int rift, int pollution, int suspicion) {
        static FoodRule of(String category, int warmth) {
            return new FoodRule(category, warmth, 0, 0, 0, 0);
        }

        static FoodRule full(String category, int warmth, int restraint, int rift, int pollution, int suspicion) {
            return new FoodRule(category, warmth, restraint, rift, pollution, suspicion);
        }
    }

    public static final Map<String, FoodRule> FOOD_RULES = new LinkedHashMap<>();

    static {
        Map<String, FoodRule> m = FOOD_RULES;
        // 火边食物:经过加工或烹饪
        m.put("minecraft:bread", FoodRule.of("cooked", 6));
        m.put("minecraft:baked_potato", FoodRule.of("cooked", 6));
        m.put("minecraft:pumpkin_pie", FoodRule.of("cooked", 6));
        m.put("minecraft:cookie", FoodRule.of("cooked", 3));
        m.put("minecraft:cake", FoodRule.of("cooked", 5));
        m.put("minecraft:dried_kelp", FoodRule.of("cooked", 3));
        m.put("minecraft:mushroom_stew", FoodRule.of("cooked", 7));
        m.put("minecraft:beetroot_soup", FoodRule.of("cooked", 6));
        m.put("minecraft:rabbit_stew", FoodRule.of("cooked", 8));
        m.put("minecraft:cooked_beef", FoodRule.of("cooked", 7));
        m.put("minecraft:cooked_chicken", FoodRule.of("cooked", 7));
        m.put("minecraft:cooked_porkchop", FoodRule.of("cooked", 7));
        m.put("minecraft:cooked_mutton", FoodRule.of("cooked", 7));
        m.put("minecraft:cooked_rabbit", FoodRule.of("cooked", 7));
        m.put("minecraft:cooked_cod", FoodRule.of("cooked", 6));
        m.put("minecraft:cooked_salmon", FoodRule.of("cooked", 6));
        // 生冷食物
        m.put("minecraft:apple", FoodRule.of("raw", 4));
        m.put("minecraft:carrot", FoodRule.of("raw", 3));
        m.put("minecraft:potato", FoodRule.of("raw", 3));
        m.put("minecraft:beetroot", FoodRule.of("raw", 3));
        m.put("minecraft:melon_slice", FoodRule.of("raw", 3));
        m.put("minecraft:sweet_berries", FoodRule.of("raw", 3));
        m.put("minecraft:glow_berries", FoodRule.of("raw", 3));
        m.put("minecraft:honey_bottle", FoodRule.of("raw", 5));
        m.put("minecraft:milk_bucket", FoodRule.full("raw", 4, 2, 0, 0, 0));
        m.put("minecraft:beef", FoodRule.of("raw", 3));
        m.put("minecraft:chicken", FoodRule.of("raw", 3));
        m.put("minecraft:porkchop", FoodRule.of("raw", 3));
        m.put("minecraft:mutton", FoodRule.of("raw", 3));
        m.put("minecraft:rabbit", FoodRule.of("raw", 3));
        m.put("minecraft:cod", FoodRule.of("raw", 3));
        m.put("minecraft:salmon", FoodRule.of("raw", 3));
        m.put("minecraft:tropical_fish", FoodRule.of("raw", 3));
        // 紫颂果
        m.put("minecraft:chorus_fruit", FoodRule.full("raw", 2, 0, 8, 0, 0));
        // 腐坏食物
        m.put("minecraft:rotten_flesh", FoodRule.full("rotten", 0, 3, 0, 4, 0));
        m.put("minecraft:spider_eye", FoodRule.full("rotten", 0, 2, 0, 5, 0));
        m.put("minecraft:pufferfish", FoodRule.full("rotten", 0, 1, 0, 6, 0));
        m.put("minecraft:poisonous_potato", FoodRule.full("rotten", 0, 0, 0, 5, 0));
        m.put("minecraft:suspicious_stew", FoodRule.full("rotten", 0, 0, 0, 4, 0));
        // 金色食物:高价值不等于真诚
        m.put("minecraft:golden_apple", FoodRule.full("golden", 2, 0, 0, 0, 8));
        m.put("minecraft:enchanted_golden_apple", FoodRule.full("golden", 2, 0, 0, 0, 12));
        m.put("minecraft:golden_carrot", FoodRule.full("golden", 2, 0, 0, 0, 7));
    }

    public static final Set<String> REPAIR_GIFT_NAMES = Set.of(
            "minecraft:torch", "minecraft:redstone_torch", "minecraft:grass",
            "minecraft:grass_block", "minecraft:moss_block", "minecraft:glass");

    public static final Set<String> NIGHT_GIFT_NAMES = Set.of(
            "minecraft:bone", "minecraft:rotten_flesh", "minecraft:spider_eye",
            "minecraft:gunpowder", "minecraft:ender_pearl");

    public static final Set<String> RIFT_GIFT_NAMES = Set.of(
            "herobrine_companion:source_code_fragment", "herobrine_companion:glitch_fragment",
            "herobrine_companion:corrupted_code", "herobrine_companion:void_marrow");

    public static final Set<String> DESTRUCTIVE_GIFT_NAMES = Set.of(
            "minecraft:tnt", "minecraft:lava_bucket", "minecraft:fire_charge",
            "minecraft:flint_and_steel");

    private static final String[] TOOL_SUFFIXES = {
            "_sword", "_pickaxe", "_axe", "_shovel", "_hoe", "_bow",
            "_helmet", "_chestplate", "_leggings", "_boots",
    };

    private static final String[] REPAIR_SUFFIXES = {
            "_sapling", "_flower", "_tulip", "_leaves", "_glass",
    };

    // 原版物品级联分类:顺序即优先级,第一个命中即返回;在剥离命名空间的裸名上匹配
    private static final String[][] CATEGORY_RULES = {
            {"admin", "^(bedrock|command_block)$"},
            {"redstone", "(^|_)redstone|(piston|rail|hopper|lever|repeater|comparator|observer|target|dispenser|dropper|daylight_detector|tripwire_hook|note_block)$"},
            {"transport", "(_boat$|_raft$|minecart$)"},
            {"nether", "^(crimson|warped|soul_)|^(netherrack|nether_wart|nether_gold_ore|nether_quartz_ore|basalt|smooth_basalt|blackstone|polished_blackstone|polished_blackstone_bricks|gilded_blackstone|crying_obsidian|obsidian|magma_cream|ghast_tear|blaze_rod|blaze_powder|ancient_debris|respawn_anchor|quartz|quartz_block|quartz_bricks|glowstone_dust)$"},
            {"light", "(_candle$|^candle$|^torch$|^soul_torch$|^lantern$|^soul_lantern$|^campfire$|^soul_campfire$|^glowstone$|^sea_lantern$)"},
            {"weapon", "^(bow|crossbow|arrow|trident|shield)$"},
            {"armor", "^(elytra|.*_horse_armor)$"},
            {"mob_drop", "^(bone|rotten_flesh|spider_eye|gunpowder|slime_ball|string|feather|phantom_membrane|ghast_tear|blaze_rod|blaze_powder|magma_cream|ink_sac|glow_ink_sac|nautilus_shell|prismarine_shard|prismarine_crystals|scute|rabbit_foot|rabbit_hide|leather|honeycomb|fermented_spider_eye)$"},
            {"growth", "(_seeds$|^kelp$|^sugar_cane$|^cactus$|^wheat$|^cocoa_beans$|^hay_block$|^bone_meal$)"},
            {"rift", "^(end_stone|end_rod|purpur_block|purpur_pillar|popped_chorus_fruit|dragon_breath|sculk_catalyst|sculk_sensor|sculk_shrieker)"},
            {"water", "^(ice|packed_ice|blue_ice|snow_block|sponge|wet_sponge|prismarine|prismarine_bricks|dark_prismarine)$"},
            {"memory", "^(book|paper|name_tag|clock|compass|recovery_compass|brush|spyglass|fishing_rod|jukebox)$"},
            {"treasure", "^(nether_star|totem_of_undying|heart_of_the_sea|echo_shard|beacon|lodestone|experience_bottle)$"},
            {"gem", "^(diamond|emerald|amethyst_shard|lapis_lazuli|netherite_ingot|netherite_scrap|iron_ingot|gold_ingot|copper_ingot)$"},
            {"gem", "^(coal|iron|gold|diamond|emerald|lapis|copper|netherite|amethyst|quartz)_block$"},
            {"ore", "(_ore$|^raw_copper$|^raw_gold$|^raw_iron$|^coal$|^charcoal$|^flint$|^amethyst_block$|^budding_amethyst$)"},
            {"wood", "(_log$|_planks$|_door$|_trapdoor$|_fence$|_fence_gate$|_pressure_plate$|_button$|_slab$|_stairs$|_sign$|_hanging_sign$|^stripped_|^bamboo|^stick$)"},
            {"stone", "^(stone|cobblestone|cobbled_deepslate|deepslate|deepslate_bricks|deepslate_tiles|polished_deepslate|reinforced_deepslate|andesite|polished_andesite|diorite|polished_diorite|granite|polished_granite|tuff|calcite|mossy_cobblestone|sand|red_sand|gravel|dirt|coarse_dirt|rooted_dirt|podzol|mycelium|mud|packed_mud|mud_bricks|brick|clay|clay_ball)$"},
            {"animal", "^(saddle|shears|lead|bucket|water_bucket|lava_bucket|milk_bucket|powder_snow_bucket|honey_bottle|honeycomb|egg)$"},
            {"decor", "^(white|orange|magenta|light_blue|yellow|lime|pink|gray|light_gray|cyan|purple|blue|brown|green|red|black)_(wool|concrete|concrete_powder|terracotta|glazed_terracotta|stained_glass|stained_glass_pane|banner|carpet|dye|shulker_box|bed)$"},
            {"decor", "^(glass|glass_pane|scaffolding|terracotta|anvil|chipped_anvil|damaged_anvil|bell|chest|trapped_chest|ender_chest|barrel|furnace|blast_furnace|smoker|crafting_table|cartography_table|fletching_table|smithing_table|stonecutter_block|grindstone|loom|lectern|enchanting_table|brewing_stand|cauldron|composter|jukebox|shulker_box|bone_block|honeycomb_block|firework_rocket)$"},
    };

    private static final Pattern[] CASCADE_PATTERNS = compileRules(CATEGORY_RULES);

    private static Pattern[] compileRules(String[][] rules) {
        Pattern[] out = new Pattern[rules.length];
        for (int i = 0; i < rules.length; i++) {
            out[i] = Pattern.compile(rules[i][1]);
        }
        return out;
    }

    // ------------------------------------------------------------------
    // ItemStack 读取(Bedrock item dict 等价)
    // ------------------------------------------------------------------

    /** 物品注册名 "namespace:path";空手返回 "". */
    public static String itemName(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return "";
        }
        net.minecraft.resources.ResourceLocation key = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem());
        return key == null ? "" : key.toString();
    }

    public static int itemCount(ItemStack stack) {
        return stack == null ? 0 : stack.getCount();
    }

    public static int itemAux(ItemStack stack) {
        // Bedrock aux 对普通物品即 damage 值
        return stack == null ? 0 : stack.getDamageValue();
    }

    public static boolean hasCustomName(ItemStack stack) {
        return stack != null && !stack.isEmpty() && stack.has(net.minecraft.core.component.DataComponents.CUSTOM_NAME);
    }

    public static String customName(ItemStack stack) {
        if (!hasCustomName(stack)) {
            return "";
        }
        return stack.getHoverName().getString();
    }

    /** 裸名(去命名空间、小写)。 */
    public static String bareName(String itemName) {
        if (itemName == null) {
            return "";
        }
        int idx = itemName.indexOf(':');
        return (idx >= 0 ? itemName.substring(idx + 1) : itemName).toLowerCase();
    }

    // ------------------------------------------------------------------
    // 分类规则
    // ------------------------------------------------------------------

    public static boolean isFoodName(String itemName) {
        return FOOD_RULES.containsKey(itemName);
    }

    public static boolean isFoodStack(ItemStack stack) {
        return isFoodName(itemName(stack));
    }

    public static boolean isToolName(String itemName) {
        String bare = bareName(itemName);
        for (String suffix : TOOL_SUFFIXES) {
            if (bare.endsWith(suffix)) {
                return true;
            }
        }
        return false;
    }

    public static boolean isRepairItemName(String itemName) {
        String bare = bareName(itemName);
        if (REPAIR_GIFT_NAMES.contains(itemName)) {
            return true;
        }
        for (String suffix : REPAIR_SUFFIXES) {
            if (bare.endsWith(suffix)) {
                return true;
            }
        }
        return false;
    }

    public static boolean isLowDurability(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !stack.isDamageableItem()) {
            return false;
        }
        int max = stack.getMaxDamage();
        if (max <= 0) {
            return false;
        }
        return (float) stack.getDamageValue() / (float) max >= 0.75F;
    }

    /** 有序级联分类;返回类别名或 null。 */
    public static String cascadeCategory(String itemName) {
        String bare = bareName(itemName);
        for (int i = 0; i < CATEGORY_RULES.length; i++) {
            if (CASCADE_PATTERNS[i].matcher(bare).find()) {
                return CATEGORY_RULES[i][0];
            }
        }
        return null;
    }

    // ------------------------------------------------------------------
    // 主分类入口
    // ------------------------------------------------------------------

    /** 分类结果。 */
    public record Classified(String itemName, String kind, String category,
                             int memory, int warmth, int restraint, int rift,
                             int pollution, int suspicion, int valueTier) {
        boolean isFood() {
            return "food".equals(kind);
        }
    }

    public static Classified classify(ItemStack stack) {
        String itemName = itemName(stack);
        if (itemName.isEmpty() || itemCount(stack) <= 0) {
            return new Classified("", "empty", "empty", 0, 0, 0, 0, 0, 0, 0);
        }
        int memory = 1;
        int warmth = 0;
        int restraint = 0;
        int rift = 0;
        int pollution = 0;
        int suspicion = 0;
        String kind = "gift";
        String category = "ordinary";

        FoodRule food = FOOD_RULES.get(itemName);
        if (food != null) {
            kind = "food";
            category = food.category();
            memory = 0;
            warmth = food.warmth();
            restraint = food.restraint();
            rift = food.rift();
            pollution = food.pollution();
            suspicion = food.suspicion();
        } else if (RIFT_GIFT_NAMES.contains(itemName)) {
            category = "rift";
            memory = 3;
            rift = 12;
        } else if (NIGHT_GIFT_NAMES.contains(itemName)) {
            category = "night";
            memory = 2;
            restraint = 5;
        } else if (DESTRUCTIVE_GIFT_NAMES.contains(itemName)) {
            category = "destructive";
            memory = 0;
            pollution = 5;
            suspicion = 10;
        } else if (isRepairItemName(itemName)) {
            category = "repair";
            memory = 2;
            warmth = 6;
            restraint = 5;
        } else if (isToolName(itemName) || hasCustomName(stack)) {
            category = "old";
            memory = 6;
        }

        if (hasCustomName(stack)) {
            memory += 8;
        }
        if (kind.equals("gift") && isToolName(itemName) && isLowDurability(stack)) {
            memory += 10;
        }
        if ("ordinary".equals(category)) {
            String cascade = cascadeCategory(itemName);
            if (cascade != null) {
                category = cascade;
            }
        }
        return new Classified(itemName, kind, category, memory, warmth, restraint, rift,
                pollution, suspicion, valueTier(itemName));
    }

    /** 价值等级:3=至珍,2=贵重,0=其余;只改态度不改好感。 */
    public static int valueTier(String itemName) {
        if (VILE_TIER_NAMES.contains(itemName)) {
            return 3;
        }
        if (PREMIUM_TIER_NAMES.contains(itemName) || PREMIUM_BLOCK_NAMES.contains(itemName)) {
            return 2;
        }
        FoodRule food = FOOD_RULES.get(itemName);
        if (food != null && "golden".equals(food.category())) {
            return 2;
        }
        return 0;
    }

    /** 回礼池抽取:B常谦卑物品名。 */
    public static String pickReturnGift(String category, RandomSource random) {
        String[] pool = RETURN_GIFT_POOLS.getOrDefault(category, RETURN_GIFT_POOLS.get("default"));
        return pool[random.nextInt(pool.length)];
    }

    /** 物品展示标签:自定义名优先,否则裸名转空格。 */
    public static String formatItemLabel(ItemStack stack) {
        String itemName = itemName(stack);
        if (itemName.isEmpty()) {
            return "空手";
        }
        if (hasCustomName(stack)) {
            return customName(stack);
        }
        return bareName(itemName).replace('_', ' ');
    }

    /**
     * 物品指纹校验:用于"我交的就是刚才那件"。
     *
     * <p>1.21 适配:组件化之后不再有 ItemStack#getTag,改用
     * {@link ItemStack#isSameItemSameComponents}(同时覆盖 damage、自定义名等组件),
     * 语义与 Forge 版的 名称+aux+自定义名+tag 比较一致。
     */
    public static boolean sameItem(ItemStack a, ItemStack b) {
        if (a == b) {
            return true;
        }
        if (a == null || b == null || a.isEmpty() || b.isEmpty()) {
            return false;
        }
        return ItemStack.isSameItemSameComponents(a, b);
    }

    private static Map<String, String[]> mapOf(String k1, String[] v1, String k2, String[] v2,
                                               String k3, String[] v3, String k4, String[] v4,
                                               String k5, String[] v5, String k6, String[] v6,
                                               String k7, String[] v7, String k8, String[] v8,
                                               String k9, String[] v9, String k10, String[] v10,
                                               String k11, String[] v11) {
        Map<String, String[]> out = new LinkedHashMap<>();
        out.put(k1, v1);
        out.put(k2, v2);
        out.put(k3, v3);
        out.put(k4, v4);
        out.put(k5, v5);
        out.put(k6, v6);
        out.put(k7, v7);
        out.put(k8, v8);
        out.put(k9, v9);
        out.put(k10, v10);
        out.put(k11, v11);
        return out;
    }
}