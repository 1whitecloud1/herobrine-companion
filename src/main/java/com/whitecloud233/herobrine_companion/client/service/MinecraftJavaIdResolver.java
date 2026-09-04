package com.whitecloud233.herobrine_companion.client.service;

import net.minecraft.client.Minecraft;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.locale.Language;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

final class MinecraftJavaIdResolver {
    private static final String VANILLA_NAMESPACE = "minecraft";

    private static Map<String, String> itemNames;
    private static Map<String, String> blockNames;
    private static Map<String, String> entityTypeNames;
    private static Map<String, String> effectNames;
    private static Language cachedLanguage;

    private MinecraftJavaIdResolver() {}

    static String itemId(String raw) {
        return normalizeRegisteredId(raw, BuiltInRegistries.ITEM, MinecraftJavaIdResolver::itemNameIndex);
    }

    static String blockId(String raw) {
        return normalizeRegisteredId(raw, BuiltInRegistries.BLOCK, MinecraftJavaIdResolver::blockNameIndex);
    }

    static String entityTypeId(String raw) {
        return normalizeRegisteredId(raw, BuiltInRegistries.ENTITY_TYPE, MinecraftJavaIdResolver::entityTypeNameIndex);
    }

    static String effectId(String raw) {
        return normalizeRegisteredId(raw, BuiltInRegistries.MOB_EFFECT, MinecraftJavaIdResolver::effectNameIndex);
    }

    static String enchantmentId(String raw) {
        return normalizeDynamicRegistryId(raw, Registries.ENCHANTMENT, null);
    }

    static String particleId(String raw) {
        return normalizeRegisteredId(raw, BuiltInRegistries.PARTICLE_TYPE, null);
    }

    static String soundId(String raw) {
        return normalizeRegisteredId(raw, BuiltInRegistries.SOUND_EVENT, null);
    }

    static String attributeId(String raw) {
        return normalizeRegisteredId(raw, BuiltInRegistries.ATTRIBUTE, null);
    }

    /** 维度：允许 "ns:path"；必须存在于当前连接服务器的维度列表（未连接时仅原版维度）。 */
    static String dimensionId(String raw) {
        String value = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
        if (value.isEmpty()) {
            return null;
        }
        if (!value.contains(":")) {
            value = VANILLA_NAMESPACE + ":" + value;
        }
        ResourceLocation id = ResourceLocation.tryParse(value);
        if (id == null) {
            return null;
        }
        return isRegisteredDimension(id) ? id.toString() : null;
    }

    /** 结构：允许 "ns:path"；必须存在于当前存档的结构注册表（worldgen/structure，未进存档时仅原版）。 */
    static String structureId(String raw) {
        String value = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
        if (value.isEmpty()) {
            return null;
        }
        if (!value.contains(":")) {
            value = VANILLA_NAMESPACE + ":" + value;
        }
        ResourceLocation id = ResourceLocation.tryParse(value);
        if (id == null) {
            return null;
        }
        return isRegisteredStructure(id) ? id.toString() : null;
    }

    /**
     * 维度的可读名：原版硬编码三个主维度；模组尝试 dimension.&lt;ns&gt;.&lt;path&gt; 语言键
     * （Twilight Forest 等模组会提供），取不到返回 null。
     */
    static String dimensionDisplayName(ResourceLocation id) {
        if (VANILLA_NAMESPACE.equals(id.getNamespace())) {
            return switch (id.getPath()) {
                case "overworld" -> "Overworld";
                case "the_nether" -> "The Nether";
                case "the_end" -> "The End";
                default -> null;
            };
        }
        String key = "dimension." + id.getNamespace() + "." + id.getPath();
        String localized = Language.getInstance().getOrDefault(key);
        return (localized != null && !localized.equals(key)) ? localized : null;
    }

    private static boolean isRegisteredDimension(ResourceLocation id) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.player == null || minecraft.player.connection == null) {
            return VANILLA_NAMESPACE.equals(id.getNamespace());
        }
        for (ResourceKey<Level> key : minecraft.player.connection.levels()) {
            if (key.location().equals(id)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isRegisteredStructure(ResourceLocation id) {
        // 客户端不同步结构注册表（1.20.2+ 起不再同步 worldgen 注册表）：
        // 原版结构由游戏保证存在；模组结构以服务器回填的本地索引为准，索引未就绪时拒绝。
        if (VANILLA_NAMESPACE.equals(id.getNamespace())) {
            return true;
        }
        return ModContentIndex.isComplete() && ModContentIndex.hasStructure(id.toString());
    }

    private static <T> String normalizeRegisteredId(String raw, Registry<T> registry, NameIndexSupplier nameIndexSupplier) {
        String direct = normalizeDirectId(raw, registry);
        if (direct != null) {
            return direct;
        }
        if (nameIndexSupplier == null) {
            return null;
        }
        return nameIndexSupplier.get().get(normalizeLookupText(raw));
    }

    private static <T> String normalizeDynamicRegistryId(String raw, ResourceKey<? extends Registry<T>> registryKey,
                                                         NameIndexSupplier nameIndexSupplier) {
        String direct = normalizeDirectDynamicId(raw, registryKey);
        if (direct != null) {
            return direct;
        }
        if (nameIndexSupplier == null) {
            return null;
        }
        return nameIndexSupplier.get().get(normalizeLookupText(raw));
    }

    private static <T> String normalizeDirectId(String raw, Registry<T> registry) {
        String value = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
        if (value.isEmpty()) {
            return null;
        }

        String candidate = value.contains(":") ? value : VANILLA_NAMESPACE + ":" + value;
        String found = registeredId(candidate, registry);
        if (found != null) {
            return found;
        }

        if (!value.contains(":")) {
            String underscoredCandidate = VANILLA_NAMESPACE + ":" + value.replace(' ', '_').replace('-', '_');
            return registeredId(underscoredCandidate, registry);
        }
        return null;
    }

    private static <T> String registeredId(String value, Registry<T> registry) {
        ResourceLocation id = ResourceLocation.tryParse(value);
        if (id == null || registry.getOptional(id).isEmpty()) {
            return null;
        }
        return id.toString();
    }

    private static <T> String normalizeDirectDynamicId(String raw, ResourceKey<? extends Registry<T>> registryKey) {
        String value = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
        if (value.isEmpty()) {
            return null;
        }

        String candidate = value.contains(":") ? value : VANILLA_NAMESPACE + ":" + value;
        String found = registeredDynamicId(candidate, registryKey);
        if (found != null) {
            return found;
        }

        if (!value.contains(":")) {
            String underscoredCandidate = VANILLA_NAMESPACE + ":" + value.replace(' ', '_').replace('-', '_');
            return registeredDynamicId(underscoredCandidate, registryKey);
        }
        return null;
    }

    private static <T> String registeredDynamicId(String value, ResourceKey<? extends Registry<T>> registryKey) {
        ResourceLocation id = ResourceLocation.tryParse(value);
        if (id == null) {
            return null;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.level == null) {
            return id.toString();
        }
        return minecraft.level.registryAccess().registry(registryKey)
                .filter(registry -> registry.getOptional(id).isPresent())
                .map(registry -> id.toString())
                .orElse(null);
    }

    /**
     * 判断一个 "namespace:path" 令牌是否指向当前客户端<b>已真实注册</b>的模组内容资源
     * （物品/方块/实体/效果/附魔/粒子/音效/属性）。用于 AI 命令安全校验：
     * 非原版命名空间只放行真实注册的资源，防止幻觉 ID 或命令注入。
     */
    static boolean isRegisteredModResource(String token) {
        ResourceLocation id = ResourceLocation.tryParse(token);
        if (id == null) {
            return false;
        }
        return BuiltInRegistries.ITEM.getOptional(id).isPresent()
                || BuiltInRegistries.BLOCK.getOptional(id).isPresent()
                || BuiltInRegistries.ENTITY_TYPE.getOptional(id).isPresent()
                || BuiltInRegistries.MOB_EFFECT.getOptional(id).isPresent()
                || BuiltInRegistries.PARTICLE_TYPE.getOptional(id).isPresent()
                || BuiltInRegistries.SOUND_EVENT.getOptional(id).isPresent()
                || BuiltInRegistries.ATTRIBUTE.getOptional(id).isPresent()
                || isRegisteredDynamicEnchantment(id)
                || isRegisteredDimension(id)
                || isRegisteredStructure(id);
    }

    /** 附魔在 1.21.1 是动态注册表：进存档后经 level.registryAccess() 查询（未进存档时按不存在处理）。 */
    private static boolean isRegisteredDynamicEnchantment(ResourceLocation id) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.level == null) {
            return false;
        }
        return minecraft.level.registryAccess().registry(Registries.ENCHANTMENT)
                .map(registry -> registry.getOptional(id).isPresent())
                .orElse(false);
    }

    private static Map<String, String> itemNameIndex() {
        resetNameIndexesIfLanguageChanged();
        if (itemNames == null) {
            itemNames = buildNameIndex(BuiltInRegistries.ITEM, Item::getDescriptionId);
        }
        return itemNames;
    }

    private static Map<String, String> blockNameIndex() {
        resetNameIndexesIfLanguageChanged();
        if (blockNames == null) {
            blockNames = buildNameIndex(BuiltInRegistries.BLOCK, Block::getDescriptionId);
        }
        return blockNames;
    }

    private static Map<String, String> entityTypeNameIndex() {
        resetNameIndexesIfLanguageChanged();
        if (entityTypeNames == null) {
            entityTypeNames = buildNameIndex(BuiltInRegistries.ENTITY_TYPE, EntityType::getDescriptionId);
        }
        return entityTypeNames;
    }

    private static Map<String, String> effectNameIndex() {
        resetNameIndexesIfLanguageChanged();
        if (effectNames == null) {
            effectNames = buildNameIndex(BuiltInRegistries.MOB_EFFECT, MobEffect::getDescriptionId);
        }
        return effectNames;
    }

    private static void resetNameIndexesIfLanguageChanged() {
        Language currentLanguage = Language.getInstance();
        if (cachedLanguage == currentLanguage) {
            return;
        }
        cachedLanguage = currentLanguage;
        itemNames = null;
        blockNames = null;
        entityTypeNames = null;
        effectNames = null;
    }

    private static <T> Map<String, String> buildNameIndex(Registry<T> registry, TranslationKeyReader<T> translationKeyReader) {
        Map<String, String> names = new HashMap<>();
        Language language = Language.getInstance();
        // 第一遍：原版条目，短名/本地化名直接写入（原版优先）。
        for (T value : registry) {
            ResourceLocation id = registry.getKey(value);
            if (id == null || !VANILLA_NAMESPACE.equals(id.getNamespace())) {
                continue;
            }
            String canonical = id.toString();
            names.put(normalizeLookupText(id.getPath()), canonical);
            names.put(normalizeLookupText(id.getPath().replace('_', ' ')), canonical);
            putLocalizedName(names, language, translationKeyReader, value, canonical);
        }
        // 第二遍：模组条目，仅在键不存在时写入（原版及先注册的模组优先，避免歧义覆盖）。
        for (T value : registry) {
            ResourceLocation id = registry.getKey(value);
            if (id == null || VANILLA_NAMESPACE.equals(id.getNamespace())) {
                continue;
            }
            String canonical = id.toString();
            names.putIfAbsent(normalizeLookupText(id.getPath()), canonical);
            names.putIfAbsent(normalizeLookupText(id.getPath().replace('_', ' ')), canonical);
            putLocalizedNameIfAbsent(names, language, translationKeyReader, value, canonical);
        }
        return names;
    }

    private static <T> void putLocalizedName(Map<String, String> names, Language language,
                                             TranslationKeyReader<T> translationKeyReader, T value, String canonical) {
        String localized = localize(language, translationKeyReader, value);
        if (!localized.isEmpty()) {
            names.put(localized, canonical);
        }
    }

    private static <T> void putLocalizedNameIfAbsent(Map<String, String> names, Language language,
                                                     TranslationKeyReader<T> translationKeyReader, T value, String canonical) {
        String localized = localize(language, translationKeyReader, value);
        if (!localized.isEmpty()) {
            names.putIfAbsent(localized, canonical);
        }
    }

    private static <T> String localize(Language language, TranslationKeyReader<T> translationKeyReader, T value) {
        String translationKey = translationKeyReader.get(value);
        if (translationKey == null || translationKey.isBlank()) {
            return "";
        }
        String localized = language.getOrDefault(translationKey);
        if (localized == null || localized.equals(translationKey)) {
            return "";
        }
        return normalizeLookupText(localized);
    }

    static String normalizeLookupText(String raw) {
        return raw == null ? "" : raw.trim()
                .replace("§", "")
                .replace('_', ' ')
                .replace('-', ' ')
                .replaceAll("\\s+", " ")
                .toLowerCase(Locale.ROOT);
    }

    private interface NameIndexSupplier {
        Map<String, String> get();
    }

    private interface TranslationKeyReader<T> {
        String get(T value);
    }
}

