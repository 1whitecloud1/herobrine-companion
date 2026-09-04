package com.whitecloud233.modid.herobrine_companion.client.service;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.whitecloud233.modid.herobrine_companion.client.llm.LlmToolSpec;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.locale.Language;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * 注册表检索工具的<b>LLM 面</b>：让模型在调用命令工具前，按模糊名称/ID 片段/模组命名空间
 * 查出<b>精确且真实注册</b>的资源 ID（物品/方块/实体/效果/附魔/粒子/音效）。
 *
 * <p>单一职责：只做"面向模型的那一层"——工具描述/JSON schema、参数校验、
 * 只读检索（优先查游戏加载时固化的本地索引 {@link ModContentIndex}，未就绪时直接扫注册表兜底）、
 * 结果格式化。不执行任何游戏动作、不写任何状态。</p>
 *
 * <p>安全定位：只读（绝不执行返回内容）；只返回真实注册的 ID（命令侧校验同源，
 * 见 {@link MinecraftJavaIdResolver#isRegisteredModResource}）；结果按条数/字符数截断。</p>
 */
public final class RegistryLookupSupport {

    static final String TOOL_REGISTRY_LOOKUP = "registry_lookup";

    private static final int MAX_QUERY_LENGTH = 80;
    private static final int DEFAULT_LIMIT = 8;
    private static final int MAX_LIMIT = 20;
    private static final int FALLBACK_SCAN_CAP = 60;

    /** 查询词命中这些即视为"列出已安装模组"。 */
    private static final Set<String> MOD_LIST_QUERIES = Set.of(
            "mods", "list mods", "mod list", "mods list", "installed mods",
            "已安装模组", "模组列表", "模组清单", "装了哪些模组", "有哪些模组");

    private RegistryLookupSupport() {
    }

    static boolean isSupportedToolName(String toolName) {
        return TOOL_REGISTRY_LOOKUP.equals(toolName);
    }

    static LlmToolSpec toolSpec() {
        return new LlmToolSpec(TOOL_REGISTRY_LOOKUP, buildDescription(), createInputSchema());
    }

    /** 解析并校验模型给的参数；不合法返回带原因的 error。 */
    static ParseResult parseAction(JsonObject args) {
        String query = getString(args, "query").trim();
        if (query.isEmpty()) {
            return ParseResult.error("query 不能为空");
        }
        if (query.length() > MAX_QUERY_LENGTH) {
            return ParseResult.error("query 过长（最多 " + MAX_QUERY_LENGTH + " 字符）");
        }
        // category 是可选的：未传/为空时默认 any（与 schema 声明一致，勿误判为非法参数）。
        String categoryId = getString(args, "category");
        Category category = categoryId.isEmpty() ? Category.ANY : Category.fromId(categoryId);
        if (category == null) {
            return ParseResult.error("category 只能是 " + Category.ids());
        }
        int limit = Math.max(1, Math.min(getInt(args, "limit", DEFAULT_LIMIT), MAX_LIMIT));
        return ParseResult.success(new RegistryLookupRequest(query, category, limit));
    }

    /**
     * 只读检索，返回已格式化的结果文本（直接回填 LLM 对话，调用方不需再加工）。
     * 优先查本地索引 {@link ModContentIndex}；索引未就绪时直接扫注册表兜底。
     */
    static String search(RegistryLookupRequest request) {
        String queryNorm = MinecraftJavaIdResolver.normalizeLookupText(request.query());
        if (MOD_LIST_QUERIES.contains(queryNorm)) {
            return ModContentIndex.modsSummary(40, 2000);
        }

        ModContentIndex.ensureReady();
        if (ModContentIndex.isComplete()) {
            return formatIndexResults(request, ModContentIndex.search(
                    queryNorm,
                    request.category() == Category.ANY ? "" : request.category().id(),
                    Math.max(request.limit(), FALLBACK_SCAN_CAP)));
        }
        // 索引未就绪或不完整（构建时存档未加载，缺维度/结构）：直接扫注册表，保证可用性。
        return scanRegistriesFallback(request, queryNorm);
    }

    private static String formatIndexResults(RegistryLookupRequest request, ModContentIndex.SearchResult result) {
        if (result.hits().isEmpty()) {
            return noMatchText(request.query());
        }
        int shown = Math.min(request.limit(), result.hits().size());
        StringBuilder sb = new StringBuilder(256);
        sb.append("[registry_lookup: query=\"").append(request.query())
                .append("\" category=").append(request.category().id())
                .append(", ").append(result.total()).append(" matches, showing top ").append(shown).append("]\n");
        for (int i = 0; i < shown; i++) {
            ModContentIndex.SearchHit hit = result.hits().get(i);
            sb.append("- ").append(hit.id());
            if (hit.name() != null && !hit.name().isBlank()) {
                sb.append(" | ").append(hit.name().replace("\n", " "));
            }
            sb.append("\n");
        }
        if (result.total() > shown) {
            sb.append("(").append(result.total() - shown).append(" more matches; narrow the query or raise limit)\n");
        }
        sb.append("Only these exact ids are accepted by command tools; use them verbatim in item_id/entity_id/block_id/effect_id/enchantment_id.");
        return sb.toString();
    }

    private static String noMatchText(String query) {
        return "(registry_lookup 没有找到与 \"" + query + "\" 匹配的已注册内容。"
                + "它可能不存在于当前整合包/服务端；不要编造 ID。"
                + "可换用其他关键词、模组命名空间，或用 query=\"mods\" 查看已安装模组清单。)";
    }

    /** 索引未就绪时的实时注册表兜底扫描。 */
    private static String scanRegistriesFallback(RegistryLookupRequest request, String queryNorm) {
        Map<String, Hit> hits = new LinkedHashMap<>();
        List<Category> categories = request.category() == Category.ANY ? Category.contentCategories() : List.of(request.category());
        for (Category category : categories) {
            scan(category, queryNorm, hits);
        }
        if (hits.isEmpty()) {
            return noMatchText(request.query());
        }
        List<Hit> sorted = new ArrayList<>(hits.values());
        sorted.sort(Comparator.comparingInt((Hit h) -> h.score).reversed().thenComparing(h -> h.id));
        int shown = Math.min(request.limit(), sorted.size());
        StringBuilder sb = new StringBuilder(256);
        sb.append("[registry_lookup: query=\"").append(request.query())
                .append("\" category=").append(request.category().id())
                .append(", ").append(sorted.size()).append(" matches, showing top ").append(shown).append("]\n");
        for (int i = 0; i < shown; i++) {
            Hit hit = sorted.get(i);
            sb.append("- ").append(hit.id);
            if (hit.displayName != null && !hit.displayName.isBlank()) {
                sb.append(" | ").append(hit.displayName.replace("\n", " "));
            }
            sb.append("\n");
        }
        if (sorted.size() > shown) {
            sb.append("(").append(sorted.size() - shown).append(" more matches; narrow the query or raise limit)\n");
        }
        sb.append("Only these exact ids are accepted by command tools; use them verbatim in item_id/entity_id/block_id/effect_id/enchantment_id.");
        return sb.toString();
    }

    // ------------------------------------------------------------------
    // 检索实现
    // ------------------------------------------------------------------

    private static <T> void scan(Category category, String queryNorm, Map<String, Hit> hits) {
        if (category == Category.DIMENSION) {
            scanDimensionKeys(queryNorm, hits);
            return;
        }
        Registry<T> registry = category.registry();
        if (registry == null) {
            return;
        }
        Function<T, String> keyReader = category.translationKeyReader();
        Language language = Language.getInstance();
        for (T value : registry) {
            ResourceLocation id = registry.getKey(value);
            if (id == null) {
                continue;
            }
            int score = 0;
            String displayName = null;
            score = Math.max(score, score(id.toString(), queryNorm));
            score = Math.max(score, score(MinecraftJavaIdResolver.normalizeLookupText(id.getPath()), queryNorm));
            if (keyReader != null) {
                String translationKey = keyReader.apply(value);
                if (translationKey != null && !translationKey.isBlank()) {
                    String localized = language.getOrDefault(translationKey);
                    if (localized != null && !localized.equals(translationKey)) {
                        displayName = localized;
                        score = Math.max(score, score(MinecraftJavaIdResolver.normalizeLookupText(localized), queryNorm));
                    }
                }
            }
            if (score <= 0) {
                continue;
            }
            String full = id.toString();
            Hit existing = hits.get(full);
            if (existing == null || score > existing.score) {
                hits.put(full, new Hit(full, displayName, score));
            }
        }
    }

    /** 维度不是注册表而是连接服务器的维度键列表，单独扫描。后台线程读取，竞态/断开一律吞掉。 */
    private static void scanDimensionKeys(String queryNorm, Map<String, Hit> hits) {
        try {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft == null || minecraft.player == null || minecraft.player.connection == null) {
                return;
            }
            for (ResourceKey<Level> key : minecraft.player.connection.levels()) {
                ResourceLocation id = key.location();
                int score = Math.max(score(id.toString(), queryNorm),
                        score(MinecraftJavaIdResolver.normalizeLookupText(id.getPath()), queryNorm));
                String displayName = MinecraftJavaIdResolver.dimensionDisplayName(id);
                if (displayName != null) {
                    score = Math.max(score, score(MinecraftJavaIdResolver.normalizeLookupText(displayName), queryNorm));
                }
                if (score > 0) {
                    hits.put(id.toString(), new Hit(id.toString(), displayName, score));
                }
            }
        } catch (RuntimeException ignored) {
            // 进/出存档瞬间的竞态：本次查询无维度结果即可，不影响游戏。
        }
    }

    private static int score(String candidate, String query) {
        if (query.isEmpty() || candidate.isEmpty()) {
            return 0;
        }
        if (candidate.equals(query)) {
            return 100;
        }
        if (candidate.startsWith(query)) {
            return 60;
        }
        if (candidate.contains(query)) {
            return 25;
        }
        return 0;
    }

    // ------------------------------------------------------------------
    // 工具描述 / schema
    // ------------------------------------------------------------------

    private static String buildDescription() {
        return "A read-only, local registry search over a local index of ALL installed mod content built at game load "
                + "(items, blocks, entity types, mob effects, enchantments, particles, sounds, DIMENSIONS and STRUCTURES with exact registered ids — vanilla and installed mods alike). "
                + "Query 'mods' to list installed mods, a mod namespace (e.g. 'tacz') to list that mod's content, "
                + "or a fuzzy name/id fragment (e.g. 'ak', 'copper ingot', 'twilight', '钻石') to resolve the EXACT registered id. "
                + "ALWAYS call this BEFORE filling item_id / entity_id / block_id / effect_id / enchantment_id / dimension_id / structure_id "
                + "when you are not 100% certain of the exact id, especially for modded content. "
                + "Never guess or invent a mod id — unregistered ids are rejected by the command validator. "
                + "It never changes the game and never executes anything.";
    }

    private static JsonObject createInputSchema() {
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");
        schema.addProperty("additionalProperties", false);

        JsonObject properties = new JsonObject();

        JsonObject query = new JsonObject();
        query.addProperty("type", "string");
        query.addProperty("description", "Fuzzy search text: name, id fragment, or mod namespace (e.g. 'ak47', 'copper ingot', 'tacz', '钻石'); use 'mods' to list installed mods.");
        query.addProperty("maxLength", MAX_QUERY_LENGTH);
        properties.add("query", query);

        JsonObject category = new JsonObject();
        category.addProperty("type", "string");
        category.addProperty("description", "Optional category filter; default 'any'.");
        JsonArray categoryEnum = new JsonArray();
        for (Category value : Category.values()) {
            categoryEnum.add(value.id());
        }
        category.add("enum", categoryEnum);
        properties.add("category", category);

        JsonObject limit = new JsonObject();
        limit.addProperty("type", "integer");
        limit.addProperty("description", "Max results to return, 1-" + MAX_LIMIT + ", default " + DEFAULT_LIMIT + ".");
        properties.add("limit", limit);

        schema.add("properties", properties);
        JsonArray required = new JsonArray();
        required.add("query");
        schema.add("required", required);
        return schema;
    }

    private static String getString(JsonObject args, String name) {
        if (args == null || !args.has(name) || args.get(name).isJsonNull() || !args.get(name).isJsonPrimitive()) {
            return "";
        }
        try {
            return args.get(name).getAsString();
        } catch (RuntimeException ignored) {
            return "";
        }
    }

    private static int getInt(JsonObject args, String name, int fallback) {
        if (args == null || !args.has(name) || args.get(name).isJsonNull() || !args.get(name).isJsonPrimitive()) {
            return fallback;
        }
        try {
            return args.get(name).getAsInt();
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    /** 检索范围（any = 全部内容类别）。结构是 worldgen 注册表，经 level.registryAccess() 解析。 */
    enum Category {
        ANY("any", null, null),
        ITEM("item", BuiltInRegistries.ITEM, ItemTranslation.KEY),
        BLOCK("block", BuiltInRegistries.BLOCK, BlockTranslation.KEY),
        ENTITY("entity", BuiltInRegistries.ENTITY_TYPE, EntityTranslation.KEY),
        EFFECT("effect", BuiltInRegistries.MOB_EFFECT, EffectTranslation.KEY),
        ENCHANTMENT("enchantment", BuiltInRegistries.ENCHANTMENT, EnchantmentTranslation.KEY),
        PARTICLE("particle", BuiltInRegistries.PARTICLE_TYPE, null),
        SOUND("sound", BuiltInRegistries.SOUND_EVENT, null),
        DIMENSION("dimension", null, null),
        STRUCTURE("structure", null, null);

        private final String id;
        private final Registry<?> registry;
        private final Function<Object, String> translationKeyReader;

        Category(String id, Registry<?> registry, Function<Object, String> translationKeyReader) {
            this.id = id;
            this.registry = registry;
            this.translationKeyReader = translationKeyReader;
        }

        String id() {
            return this.id;
        }

        @SuppressWarnings("unchecked")
        <T> Registry<T> registry() {
            if (this == STRUCTURE) {
                // 结构是 worldgen 动态注册表：优先连接同步的 registryAccess，其次 level 的。
                Minecraft minecraft = Minecraft.getInstance();
                if (minecraft == null) {
                    return null;
                }
                net.minecraft.core.RegistryAccess registryAccess = null;
                if (minecraft.player != null && minecraft.player.connection != null) {
                    registryAccess = minecraft.player.connection.registryAccess();
                } else if (minecraft.level != null) {
                    registryAccess = minecraft.level.registryAccess();
                }
                if (registryAccess == null) {
                    return null;
                }
                return (Registry<T>) registryAccess.registry(Registries.STRUCTURE).orElse(null);
            }
            return (Registry<T>) this.registry;
        }

        @SuppressWarnings("unchecked")
        <T> Function<T, String> translationKeyReader() {
            return (Function<T, String>) this.translationKeyReader;
        }

        static Category fromId(String id) {
            for (Category category : values()) {
                if (category.id.equals(id)) {
                    return category;
                }
            }
            return null;
        }

        static List<Category> contentCategories() {
            return List.of(ITEM, BLOCK, ENTITY, EFFECT, ENCHANTMENT, PARTICLE, SOUND, DIMENSION, STRUCTURE);
        }

        static String ids() {
            StringBuilder sb = new StringBuilder();
            for (Category category : values()) {
                if (sb.length() > 0) {
                    sb.append(" / ");
                }
                sb.append(category.id);
            }
            return sb.toString();
        }
    }

    // 各注册表条目的翻译键读取器（泛型擦除后用 Object 适配）。
    private static final class ItemTranslation implements Function<Object, String> {
        static final ItemTranslation KEY = new ItemTranslation();

        @Override
        public String apply(Object value) {
            return value instanceof net.minecraft.world.item.Item item ? item.getDescriptionId() : null;
        }
    }

    private static final class BlockTranslation implements Function<Object, String> {
        static final BlockTranslation KEY = new BlockTranslation();

        @Override
        public String apply(Object value) {
            return value instanceof net.minecraft.world.level.block.Block block ? block.getDescriptionId() : null;
        }
    }

    private static final class EntityTranslation implements Function<Object, String> {
        static final EntityTranslation KEY = new EntityTranslation();

        @Override
        public String apply(Object value) {
            return value instanceof net.minecraft.world.entity.EntityType<?> type ? type.getDescriptionId() : null;
        }
    }

    private static final class EffectTranslation implements Function<Object, String> {
        static final EffectTranslation KEY = new EffectTranslation();

        @Override
        public String apply(Object value) {
            return value instanceof net.minecraft.world.effect.MobEffect effect ? effect.getDescriptionId() : null;
        }
    }

    private static final class EnchantmentTranslation implements Function<Object, String> {
        static final EnchantmentTranslation KEY = new EnchantmentTranslation();

        @Override
        public String apply(Object value) {
            return value instanceof net.minecraft.world.item.enchantment.Enchantment enchantment ? enchantment.getDescriptionId() : null;
        }
    }

    /** 一次已校验的检索请求（不可变）。 */
    record RegistryLookupRequest(String query, Category category, int limit) {
    }

    private record Hit(String id, String displayName, int score) {
    }

    record ParseResult(RegistryLookupRequest request, String error) {
        static ParseResult success(RegistryLookupRequest request) {
            return new ParseResult(request, "");
        }

        static ParseResult error(String error) {
            return new ParseResult(null, error);
        }

        boolean isValid() {
            return this.request != null;
        }
    }
}
