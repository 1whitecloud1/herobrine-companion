package com.whitecloud233.modid.herobrine_companion.client.service;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.locale.Language;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * 已安装模组内容<b>本地索引</b>：游戏加载时把客户端注册表（物品/方块/实体/效果/附魔/粒子/音效）
 * 的条目与模组清单固化成本地 JSON 文件，之后 `registry_lookup` 直接查内存索引，
 * 不注入系统提示词、也不在每次查询时全量扫注册表。
 *
 * <p>生命周期：{@link #initialize()} 在客户端启动时只读磁盘（指纹一致则直接加载）；
 * 文件缺失或模组列表变化时，在进存档后（客户端语言已加载，本地化名才准确）由
 * {@link #ensureReady()} 重建并写盘。构建失败只影响检索精度，不影响游戏。</p>
 *
 * <p>安全：只读注册表与 ModList，绝不执行任何内容；条目为真实注册的资源。</p>
 */
public final class ModContentIndex {

    /** 文件结构版本：条目格式变化时 +1，强制重建。 */
    private static final int SCHEMA_VERSION = 2;
    private static final String INDEX_FILE_NAME = "mod_content_index.json";

    /** 索引文件大小上限：超过视为损坏（防御磁盘上被改坏的巨型文件导致启动 OOM）。 */
    private static final long MAX_INDEX_FILE_BYTES = 64L * 1024L * 1024L;

    private static final String[] COUNT_LABELS = {"items", "blocks", "entities", "effects", "enchantments", "particles", "sounds", "dimensions", "structures"};

    private static volatile IndexData data;
    private static volatile boolean busy;
    /** 结构清单是否已从服务器回填（1.20.1 客户端无结构注册表，结构数据以服务端响应为准）。 */
    private static volatile boolean structuresReady;

    private ModContentIndex() {
    }

    /** 客户端启动钩子：只读本地文件，指纹一致则加载进内存（不构建、不写盘）。 */
    public static void initialize() {
        loadFromDiskIfValid();
    }

    /**
     * 确保索引可用：磁盘有效则加载，否则重建并写盘。可安全地从任意线程调用。
     * 若已加载的索引是"存档未就绪时构建"的残缺版（缺维度/结构），且现在 level 已加载，
     * 则自动补建为完整索引。
     */
    public static void ensureReady() {
        IndexData current = data;
        if (current != null && current.builtWithLevel()) {
            return;
        }
        synchronized (ModContentIndex.class) {
            if (busy) {
                // 另一线程正在加载/构建：本次调用走实时注册表兜底，不阻塞。
                return;
            }
            busy = true;
        }
        try {
            if (data == null) {
                loadFromDiskIfValid();
            }
            if (data == null) {
                buildAndSave();
            } else if (!data.builtWithLevel() && hasLoadedLevel()) {
                // 上次构建发生在进存档瞬间（level 未就绪 → 维度/结构缺失），现在补建。
                buildAndSave();
            }
        } finally {
            synchronized (ModContentIndex.class) {
                busy = false;
            }
        }
    }

    public static boolean isReady() {
        return data != null;
    }

    /** 索引是否"完整"（构建时存档已就绪，且结构清单已从服务器回填）。未就绪时查询应走实时注册表兜底。 */
    public static boolean isComplete() {
        IndexData current = data;
        return current != null && current.builtWithLevel() && structuresReady;
    }

    /**
     * 服务器结构清单回填（服务端权威）：替换/补入 structure 条目、更新模组计数、
     * 标记就绪并写盘。由 {@code StructureIndexPacket} 在客户端线程调用。
     */
    public static synchronized void ingestStructures(List<String> structureIds) {
        try {
            if (data == null) {
                ensureReady();
            }
            IndexData current = data;
            if (current == null) {
                return;
            }
            Map<String, int[]> counts = new HashMap<>();
            for (ModMeta meta : current.mods().values()) {
                counts.put(meta.id(), meta.counts().clone());
            }
            List<RuntimeEntry> entries = new ArrayList<>();
            for (RuntimeEntry entry : current.entries()) {
                if ("structure".equals(entry.category())) {
                    continue;
                }
                entries.add(entry);
                int[] perMod = counts.computeIfAbsent(entry.namespace(), key -> new int[COUNT_LABELS.length]);
                if (perMod.length > categoryIndex(entry.category())) {
                    perMod[categoryIndex(entry.category())]++;
                }
            }
            for (String id : structureIds) {
                ResourceLocation rl = ResourceLocation.tryParse(id);
                if (rl == null) {
                    continue;
                }
                entries.add(new RuntimeEntry(id, rl.getNamespace(), rl.getPath(), "structure", null,
                        MinecraftJavaIdResolver.normalizeLookupText(rl.getPath()), ""));
                int[] perMod = counts.computeIfAbsent(rl.getNamespace(), key -> new int[COUNT_LABELS.length]);
                perMod[categoryIndex("structure")]++;
            }
            Map<String, ModMeta> mods = new HashMap<>();
            for (Map.Entry<String, int[]> entry : counts.entrySet()) {
                if (total(entry.getValue()) == 0) {
                    continue;
                }
                mods.put(entry.getKey(), new ModMeta(entry.getKey(), displayName(entry.getKey()), entry.getValue()));
            }
            IndexData updated = new IndexData(current.fingerprint(), mods, entries, current.builtWithLevel(), true);
            data = updated;
            structuresReady = true;
            saveToDisk(updated);
        } catch (RuntimeException ignored) {
            // 回填失败：保持原索引，结构查询走实时注册表兜底。
        }
    }

    private static boolean hasLoadedLevel() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft != null && minecraft.level != null;
    }

    /** 结构 ID 是否存在于本地索引（结构数据来自服务器回填，见 {@code StructureIndexPacket}）。 */
    public static boolean hasStructure(String structureId) {
        IndexData d = data;
        if (d == null || structureId == null) {
            return false;
        }
        for (RuntimeEntry entry : d.entries()) {
            if ("structure".equals(entry.category()) && entry.id().equals(structureId)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 模糊检索内存索引。queryNorm 为已归一化的查询词（见
     * {@link MinecraftJavaIdResolver#normalizeLookupText}）。categoryFilter 为
     * item/block/entity/effect/enchantment/particle/sound 之一，空串 = 全部。
     * 返回按相关度排序的前 limit 条（同 id 跨类别去重，取高分）。
     */
    public static SearchResult search(String queryNorm, String categoryFilter, int limit) {
        IndexData d = data;
        if (d == null || queryNorm.isEmpty()) {
            return SearchResult.empty();
        }
        Map<String, Scored> best = new LinkedHashMap<>();
        for (RuntimeEntry entry : d.entries()) {
            if (!categoryFilter.isEmpty() && !entry.category().equals(categoryFilter)) {
                continue;
            }
            int score = 0;
            score = Math.max(score, score(entry.id(), queryNorm));
            score = Math.max(score, score(entry.pathNorm(), queryNorm));
            if (!entry.nameNorm().isEmpty()) {
                score = Math.max(score, score(entry.nameNorm(), queryNorm));
            }
            if (score <= 0) {
                continue;
            }
            Scored existing = best.get(entry.id());
            if (existing == null || score > existing.score()) {
                best.put(entry.id(), new Scored(new SearchHit(entry.id(), entry.name()), score));
            }
        }
        List<Scored> sorted = new ArrayList<>(best.values());
        sorted.sort(Comparator.comparingInt((Scored s) -> s.score()).reversed()
                .thenComparing(s -> s.hit().id()));
        int total = sorted.size();
        int cap = Math.min(Math.max(limit, 0), sorted.size());
        List<SearchHit> hits = new ArrayList<>(cap);
        for (int i = 0; i < cap; i++) {
            hits.add(sorted.get(i).hit());
        }
        return new SearchResult(hits, total);
    }

    /** 已安装模组清单摘要（含各类注册数量），供 `registry_lookup` 的 "mods" 查询使用。 */
    public static String modsSummary(int maxLines, int maxChars) {
        IndexData d = data;
        if (d == null || d.mods().isEmpty()) {
            return "(本地索引尚未就绪或没有任何已注册内容的模组。)";
        }
        List<ModMeta> mods = new ArrayList<>(d.mods().values());
        mods.sort((a, b) -> Integer.compare(total(b.counts()), total(a.counts())));
        StringBuilder sb = new StringBuilder(512);
        sb.append("[INSTALLED MODS (local index, ").append(mods.size()).append(" mods with registered content)]:\n");
        int shown = 0;
        for (ModMeta mod : mods) {
            if (shown >= maxLines || sb.length() >= maxChars) {
                break;
            }
            sb.append("- ").append(mod.id()).append(": ").append(mod.name())
                    .append(" (").append(formatCounts(mod.counts())).append(")\n");
            shown++;
        }
        if (mods.size() > shown) {
            sb.append("- ...").append(mods.size() - shown).append(" more mods\n");
        }
        sb.append("To list a mod's content, call registry_lookup with its namespace as the query; to resolve an exact id, search by name or id fragment.");
        return sb.toString();
    }

    // ------------------------------------------------------------------
    // 磁盘加载 / 构建
    // ------------------------------------------------------------------

    private static boolean loadFromDiskIfValid() {
        IndexData loaded = null;
        try {
            Path file = indexFile();
            if (Files.exists(file)) {
                String json = Files.readString(file, StandardCharsets.UTF_8);
                loaded = IndexData.fromJson(json);
            }
        } catch (Exception ignored) {
        }
        if (loaded != null && loaded.valid() && loaded.fingerprint().equals(computeFingerprint())) {
            data = loaded;
            structuresReady = loaded.structuresReady();
            return true;
        }
        return false;
    }

    private static void buildAndSave() {
        try {
            Map<String, int[]> counts = new HashMap<>();
            List<RuntimeEntry> entries = new ArrayList<>();
            Language language = Language.getInstance();
            scan(BuiltInRegistries.ITEM, "item", Item::getDescriptionId, language, entries, counts);
            scan(BuiltInRegistries.BLOCK, "block", Block::getDescriptionId, language, entries, counts);
            scan(BuiltInRegistries.ENTITY_TYPE, "entity", EntityType::getDescriptionId, language, entries, counts);
            scan(BuiltInRegistries.MOB_EFFECT, "effect", MobEffect::getDescriptionId, language, entries, counts);
            scan(BuiltInRegistries.ENCHANTMENT, "enchantment", Enchantment::getDescriptionId, language, entries, counts);
            scan(BuiltInRegistries.PARTICLE_TYPE, "particle", null, language, entries, counts);
            scan(BuiltInRegistries.SOUND_EVENT, "sound", null, language, entries, counts);
            // 维度/结构依赖连接与存档：进存档后才有数据（未连接时跳过，下次构建补齐）。
            scanDimensions(entries, counts);
            scanStructures(entries, counts);
            // 补建时保留已回填的结构条目（避免补建丢失服务器结构清单）。
            if (structuresReady && data != null) {
                for (RuntimeEntry entry : data.entries()) {
                    if ("structure".equals(entry.category())) {
                        entries.add(entry);
                        counts.computeIfAbsent(entry.namespace(), k -> new int[COUNT_LABELS.length])[categoryIndex("structure")]++;
                    }
                }
            }

            Map<String, ModMeta> mods = new HashMap<>();
            for (Map.Entry<String, int[]> entry : counts.entrySet()) {
                if (total(entry.getValue()) == 0) {
                    continue;
                }
                mods.put(entry.getKey(), new ModMeta(entry.getKey(), displayName(entry.getKey()), entry.getValue()));
            }

            IndexData built = new IndexData(computeFingerprint(), mods, entries, hasLoadedLevel(), structuresReady);
            data = built;
            saveToDisk(built);
        } catch (RuntimeException ignored) {
            // 构建失败：data 保持 null，查询走实时注册表兜底。
        }
    }

    private static <T> void scan(Registry<T> registry, String category, Function<T, String> translationKeyReader,
                                 Language language, List<RuntimeEntry> entries, Map<String, int[]> counts) {
        int categoryIndex = categoryIndex(category);
        for (T value : registry) {
            ResourceLocation id = registry.getKey(value);
            if (id == null) {
                continue;
            }
            String namespace = id.getNamespace();
            String path = id.getPath();
            counts.computeIfAbsent(namespace, key -> new int[COUNT_LABELS.length])[categoryIndex]++;
            String name = null;
            if (translationKeyReader != null) {
                String translationKey = translationKeyReader.apply(value);
                if (translationKey != null && !translationKey.isBlank()) {
                    String localized = language.getOrDefault(translationKey);
                    if (localized != null && !localized.equals(translationKey)) {
                        name = localized;
                    }
                }
            }
            entries.add(new RuntimeEntry(id.toString(), namespace, path, category, name,
                    MinecraftJavaIdResolver.normalizeLookupText(path),
                    name == null ? "" : MinecraftJavaIdResolver.normalizeLookupText(name)));
        }
    }

    /** 维度条目：来自当前连接服务器的维度列表；模组维度可经 dimension.&lt;ns&gt;.&lt;path&gt; 语言键取显示名。 */
    private static void scanDimensions(List<RuntimeEntry> entries, Map<String, int[]> counts) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.player == null || minecraft.player.connection == null) {
            return;
        }
        for (ResourceKey<Level> key : minecraft.player.connection.levels()) {
            ResourceLocation id = key.location();
            counts.computeIfAbsent(id.getNamespace(), k -> new int[COUNT_LABELS.length])[categoryIndex("dimension")]++;
            String name = MinecraftJavaIdResolver.dimensionDisplayName(id);
            entries.add(new RuntimeEntry(id.toString(), id.getNamespace(), id.getPath(), "dimension", name,
                    MinecraftJavaIdResolver.normalizeLookupText(id.getPath()),
                    name == null ? "" : MinecraftJavaIdResolver.normalizeLookupText(name)));
        }
    }

    /** 结构条目：来自连接/存档的结构注册表（worldgen/structure，含模组结构）。 */
    private static void scanStructures(List<RuntimeEntry> entries, Map<String, int[]> counts) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) {
            return;
        }
        net.minecraft.core.RegistryAccess registryAccess = null;
        if (minecraft.player != null && minecraft.player.connection != null) {
            registryAccess = minecraft.player.connection.registryAccess();
        } else if (minecraft.level != null) {
            registryAccess = minecraft.level.registryAccess();
        }
        if (registryAccess == null) {
            return;
        }
        registryAccess.registry(Registries.STRUCTURE).ifPresent(registry -> {
            for (var value : registry) {
                ResourceLocation id = registry.getKey(value);
                if (id == null) {
                    continue;
                }
                counts.computeIfAbsent(id.getNamespace(), k -> new int[COUNT_LABELS.length])[categoryIndex("structure")]++;
                entries.add(new RuntimeEntry(id.toString(), id.getNamespace(), id.getPath(), "structure", null,
                        MinecraftJavaIdResolver.normalizeLookupText(id.getPath()), ""));
            }
        });
    }

    private static void saveToDisk(IndexData toSave) {
        try {
            Path file = indexFile();
            Files.createDirectories(file.getParent());
            Files.writeString(file, toSave.toJson(), StandardCharsets.UTF_8);
        } catch (IOException ignored) {
            // 非关键：索引已在内存中可用，仅影响下次启动的加载速度。
        }
    }

    private static Path indexFile() {
        return FMLPaths.CONFIGDIR.get()
                .resolve("herobrine_companion")
                .resolve(INDEX_FILE_NAME);
    }

    /** 指纹 = 结构版本 + MC 版本 + 全部模组 id@version（排序）。变化即重建索引。 */
    private static String computeFingerprint() {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            List<String> lines = new ArrayList<>();
            lines.add("schema:" + SCHEMA_VERSION);
            try {
                lines.add("mc:" + net.minecraft.SharedConstants.getCurrentVersion().getName());
            } catch (RuntimeException ignored) {
            }
            for (var info : ModList.get().getMods()) {
                lines.add(info.getModId() + "@" + info.getVersion());
            }
            lines.sort(String::compareTo);
            for (String line : lines) {
                digest.update((line + "\n").getBytes(StandardCharsets.UTF_8));
            }
            byte[] hash = digest.digest();
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (Exception e) {
            // 指纹计算失败：返回空串，永不匹配 → 每次启动重建（正确性优先）。
            return "";
        }
    }

    private static String displayName(String namespace) {
        if (namespace.equals("minecraft")) {
            return "Vanilla";
        }
        try {
            var container = ModList.get().getModContainerById(namespace);
            if (container.isPresent() && container.get().getModInfo().getDisplayName() != null) {
                return container.get().getModInfo().getDisplayName();
            }
        } catch (RuntimeException ignored) {
        }
        return namespace;
    }

    private static int categoryIndex(String category) {
        return switch (category) {
            case "item" -> 0;
            case "block" -> 1;
            case "entity" -> 2;
            case "effect" -> 3;
            case "enchantment" -> 4;
            case "particle" -> 5;
            case "sound" -> 6;
            case "dimension" -> 7;
            case "structure" -> 8;
            default -> 0;
        };
    }

    private static int total(int[] values) {
        int sum = 0;
        for (int value : values) {
            sum += value;
        }
        return sum;
    }

    private static String formatCounts(int[] values) {
        StringBuilder sb = new StringBuilder(48);
        for (int i = 0; i < values.length; i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(values[i]).append(' ').append(COUNT_LABELS[i]);
        }
        return sb.toString();
    }

    /** 与 {@code RegistryLookupSupport} 相同的评分：相等 > 前缀 > 包含。 */
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
    // 数据结构
    // ------------------------------------------------------------------

    /** 内存中的运行条目（pathNorm/nameNorm 加载时预计算，避免每次查询重复归一化）。 */
    record RuntimeEntry(String id, String namespace, String path, String category, String name,
                        String pathNorm, String nameNorm) {
    }

    record ModMeta(String id, String name, int[] counts) {
    }

    record SearchHit(String id, String name) {
    }

    record SearchResult(List<SearchHit> hits, int total) {
        static SearchResult empty() {
            return new SearchResult(List.of(), 0);
        }
    }

    private record Scored(SearchHit hit, int score) {
    }

    /** 磁盘格式的解析/序列化载体。 */
    static final class IndexData {
        private final String fingerprint;
        private final Map<String, ModMeta> mods;
        private final List<RuntimeEntry> entries;
        /** 构建时存档 level 是否已就绪：false = 残缺版（维度可能缺失），进存档后应补建。 */
        private final boolean builtWithLevel;
        /** 结构清单是否已由服务器回填（1.20.1 客户端无结构注册表，结构数据来自服务端响应）。 */
        private final boolean structuresReady;

        IndexData(String fingerprint, Map<String, ModMeta> mods, List<RuntimeEntry> entries,
                  boolean builtWithLevel, boolean structuresReady) {
            this.fingerprint = fingerprint;
            this.mods = mods;
            this.entries = entries;
            this.builtWithLevel = builtWithLevel;
            this.structuresReady = structuresReady;
        }

        String fingerprint() {
            return this.fingerprint;
        }

        Map<String, ModMeta> mods() {
            return this.mods;
        }

        List<RuntimeEntry> entries() {
            return this.entries;
        }

        boolean builtWithLevel() {
            return this.builtWithLevel;
        }

        boolean structuresReady() {
            return this.structuresReady;
        }

        boolean valid() {
            return this.fingerprint != null && this.entries != null;
        }

        String toJson() {
            JsonObject root = new JsonObject();
            root.addProperty("schema", SCHEMA_VERSION);
            root.addProperty("fingerprint", this.fingerprint);
            root.addProperty("builtAt", System.currentTimeMillis());
            root.addProperty("levelReady", this.builtWithLevel);
            root.addProperty("structuresReady", this.structuresReady);

            JsonArray modsJson = new JsonArray();
            for (ModMeta mod : this.mods.values()) {
                JsonObject modJson = new JsonObject();
                modJson.addProperty("id", mod.id());
                modJson.addProperty("name", mod.name());
                JsonArray countsJson = new JsonArray();
                for (int count : mod.counts()) {
                    countsJson.add(count);
                }
                modJson.add("counts", countsJson);
                modsJson.add(modJson);
            }
            root.add("mods", modsJson);

            JsonArray entriesJson = new JsonArray();
            for (RuntimeEntry entry : this.entries) {
                JsonObject entryJson = new JsonObject();
                entryJson.addProperty("id", entry.id());
                entryJson.addProperty("ns", entry.namespace());
                entryJson.addProperty("path", entry.path());
                entryJson.addProperty("cat", entry.category());
                if (entry.name() != null) {
                    entryJson.addProperty("name", entry.name());
                }
                entriesJson.add(entryJson);
            }
            root.add("entries", entriesJson);
            return root.toString();
        }

        static IndexData fromJson(String json) {
            try {
                JsonObject root = JsonParser.parseString(json).getAsJsonObject();
                if (root == null || root.get("schema") == null
                        || root.get("schema").getAsInt() != SCHEMA_VERSION) {
                    return null;
                }
                String fingerprint = root.has("fingerprint") ? root.get("fingerprint").getAsString() : null;
                // levelReady 缺失（旧文件）按 false 处理：进存档后触发一次补建，修复早期残缺索引。
                boolean levelReady = root.has("levelReady") && root.get("levelReady").isJsonPrimitive()
                        && root.get("levelReady").getAsBoolean();
                boolean structureReady = root.has("structuresReady") && root.get("structuresReady").isJsonPrimitive()
                        && root.get("structuresReady").getAsBoolean();

                Map<String, ModMeta> mods = new HashMap<>();
                JsonArray modsJson = root.getAsJsonArray("mods");
                if (modsJson != null) {
                    for (JsonElement element : modsJson) {
                        JsonObject modJson = element.getAsJsonObject();
                        String id = modJson.get("id").getAsString();
                        String name = modJson.has("name") ? modJson.get("name").getAsString() : id;
                        int[] counts = new int[COUNT_LABELS.length];
                        JsonArray countsJson = modJson.getAsJsonArray("counts");
                        if (countsJson != null) {
                            int i = 0;
                            for (JsonElement countElement : countsJson) {
                                if (i < counts.length) {
                                    counts[i++] = countElement.getAsInt();
                                }
                            }
                        }
                        mods.put(id, new ModMeta(id, name, counts));
                    }
                }

                List<RuntimeEntry> entries = new ArrayList<>();
                JsonArray entriesJson = root.getAsJsonArray("entries");
                if (entriesJson != null) {
                    for (JsonElement element : entriesJson) {
                        JsonObject entryJson = element.getAsJsonObject();
                        String id = entryJson.get("id").getAsString();
                        String namespace = entryJson.has("ns") ? entryJson.get("ns").getAsString() : "";
                        String path = entryJson.has("path") ? entryJson.get("path").getAsString() : "";
                        String category = entryJson.has("cat") ? entryJson.get("cat").getAsString() : "";
                        String name = entryJson.has("name") && !entryJson.get("name").isJsonNull()
                                ? entryJson.get("name").getAsString()
                                : null;
                        entries.add(new RuntimeEntry(id, namespace, path, category, name,
                                MinecraftJavaIdResolver.normalizeLookupText(path),
                                name == null ? "" : MinecraftJavaIdResolver.normalizeLookupText(name)));
                    }
                }
                return new IndexData(fingerprint, mods, entries, levelReady, structureReady);
            } catch (RuntimeException ignored) {
                return null;
            }
        }
    }
}
