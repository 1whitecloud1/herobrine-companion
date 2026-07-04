package com.whitecloud233.modid.herobrine_companion.client.service;

import net.minecraft.core.Registry;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.locale.Language;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.enchantment.Enchantment;
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
    private static Map<String, String> enchantmentNames;
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
        return normalizeRegisteredId(raw, BuiltInRegistries.ENCHANTMENT, MinecraftJavaIdResolver::enchantmentNameIndex);
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
        if (id == null || !VANILLA_NAMESPACE.equals(id.getNamespace()) || registry.getOptional(id).isEmpty()) {
            return null;
        }
        return id.toString();
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

    private static Map<String, String> enchantmentNameIndex() {
        resetNameIndexesIfLanguageChanged();
        if (enchantmentNames == null) {
            enchantmentNames = buildNameIndex(BuiltInRegistries.ENCHANTMENT, Enchantment::getDescriptionId);
        }
        return enchantmentNames;
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
        enchantmentNames = null;
    }

    private static <T> Map<String, String> buildNameIndex(Registry<T> registry, TranslationKeyReader<T> translationKeyReader) {
        Map<String, String> names = new HashMap<>();
        Language language = Language.getInstance();
        for (T value : registry) {
            ResourceLocation id = registry.getKey(value);
            if (id == null || !VANILLA_NAMESPACE.equals(id.getNamespace())) {
                continue;
            }
            String canonical = id.toString();
            names.put(normalizeLookupText(id.getPath()), canonical);
            names.put(normalizeLookupText(id.getPath().replace('_', ' ')), canonical);

            String translationKey = translationKeyReader.get(value);
            if (translationKey == null || translationKey.isBlank()) {
                continue;
            }
            String localized = language.getOrDefault(translationKey);
            if (localized != null && !localized.equals(translationKey)) {
                names.put(normalizeLookupText(localized), canonical);
            }
        }
        return names;
    }

    private static String normalizeLookupText(String raw) {
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
