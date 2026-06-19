package com.whitecloud233.herobrine_companion.compat.epicfight;

import com.whitecloud233.herobrine_companion.init.*;

import com.whitecloud233.herobrine_companion.entity.HeroEntity;
import com.whitecloud233.herobrine_companion.item.PoemOfTheEndItem;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.PickaxeItem;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.item.ShovelItem;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.item.UseAnim;
import yesman.epicfight.world.capabilities.EpicFightCapabilities;
import yesman.epicfight.world.capabilities.entitypatch.LivingEntityPatch;
import yesman.epicfight.world.capabilities.item.CapabilityItem;
import yesman.epicfight.world.capabilities.item.Style;
import yesman.epicfight.world.capabilities.item.WeaponCapability;
import yesman.epicfight.world.capabilities.item.WeaponCategory;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class HeroEpicFightWeaponProfiles {
    private static final String LEGACY_BUILDERS_CLASS = "yesman.epicfight.api.ex_cap.modules.assets.Builders";
    private static final String LEGACY_BUILDER_MANAGER_CLASS = "yesman.epicfight.api.ex_cap.modules.core.managers.BuilderManager";
    private static final Map<String, WeaponCapability> FALLBACK_PROFILES = new ConcurrentHashMap<>();
    private static volatile boolean initialized;
    private static WeaponCapability normalProfile;
    private static WeaponCapability realmBreakerProfile;
    private static WeaponCapability thunderProfile;
    private static WeaponCapability voidShatterProfile;

    private HeroEpicFightWeaponProfiles() {
    }

    public static synchronized void bootstrap() {
        if (initialized || !HeroEpicFightCompat.isLoaded()) {
            return;
        }

        Item poem = ModItems.POEM_OF_THE_END.get();
        normalProfile = buildProfile("SWORD", poem, Items.DIAMOND_SWORD, CapabilityItem.WeaponCategories.SWORD, CapabilityItem.Styles.ONE_HAND);
        realmBreakerProfile = buildProfile("GREATSWORD", poem, null, CapabilityItem.WeaponCategories.GREATSWORD, CapabilityItem.Styles.TWO_HAND);
        thunderProfile = buildProfile("SPEAR", poem, null, CapabilityItem.WeaponCategories.SPEAR, CapabilityItem.Styles.TWO_HAND);
        voidShatterProfile = buildProfile("DAGGER", poem, null, CapabilityItem.WeaponCategories.DAGGER, CapabilityItem.Styles.ONE_HAND);
        initialized = true;
    }

    public static CapabilityItem resolveCapability(HeroEntity hero) {
        if (hero == null || !hero.isAddedToLevel()) {
            return CapabilityItem.EMPTY;
        }

        try {
            return resolveCapability(hero.getMainHandItem());
        } catch (NullPointerException exception) {
            return CapabilityItem.EMPTY;
        }
    }

    public static CapabilityItem resolveCapability(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return CapabilityItem.EMPTY;
        }

        if (stack.getItem() instanceof PoemOfTheEndItem poem) {
            bootstrap();
            return switch (poem.getMode(stack)) {
                case PoemOfTheEndItem.MODE_REALM_BREAKER -> realmBreakerProfile;
                case PoemOfTheEndItem.MODE_THUNDER_CALL -> thunderProfile;
                case PoemOfTheEndItem.MODE_VOID_SHATTER -> voidShatterProfile;
                default -> normalProfile;
            };
        }

        CapabilityItem capability = EpicFightCapabilities.getItemStackCapability(stack);
        if (capability != null && !capability.isEmpty()) {
            return capability;
        }

        CapabilityItem inferredCapability = inferCapability(stack);
        return inferredCapability != null ? inferredCapability : CapabilityItem.EMPTY;
    }

    public static boolean isRangedLoadout(HeroEntity hero) {
        if (hero == null || !hero.isAddedToLevel()) {
            return false;
        }

        try {
            return isRangedLoadout(hero.getMainHandItem());
        } catch (NullPointerException exception) {
            return false;
        }
    }

    public static boolean isRangedLoadout(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }

        if (stack.getItem() instanceof PoemOfTheEndItem) {
            return false;
        }

        if (stack.getItem() instanceof ProjectileWeaponItem) {
            return true;
        }

        UseAnim useAnim = stack.getUseAnimation();
        if (useAnim == UseAnim.BOW || useAnim == UseAnim.CROSSBOW) {
            return true;
        }

        CapabilityItem capability = resolveCapability(stack);
        if (capability != null && !capability.isEmpty()) {
            String categoryName = String.valueOf(capability.getWeaponCategory()).toLowerCase(Locale.ROOT);
            if (containsRangedKeyword(categoryName)) {
                return true;
            }
        }

        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
        String itemPath = itemId != null ? itemId.toString().toLowerCase(Locale.ROOT) : "";
        return containsRangedKeyword(itemPath);
    }

    public static boolean hasHeroControlledCombatAnimations(HeroEntity hero) {
        if (hero == null || !hero.isAddedToLevel()) {
            return false;
        }

        try {
            return hasHeroControlledCombatAnimations(hero.getMainHandItem());
        } catch (NullPointerException exception) {
            return false;
        }
    }

    public static boolean hasHeroControlledCombatAnimations(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }

        if (isRangedLoadout(stack) || stack.getItem() instanceof PoemOfTheEndItem) {
            return true;
        }

        CapabilityItem capability = resolveCapability(stack);
        if (capability == null || capability.isEmpty()) {
            return false;
        }

        return true;
    }

    public static String getProfileKey(HeroEntity hero, LivingEntityPatch<?> patch) {
        if (hero == null) {
            return "missing";
        }

        ItemStack stack = hero.getMainHandItem();
        ResourceLocation itemId = stack.isEmpty() ? ResourceLocation.withDefaultNamespace("empty") : BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (itemId == null) {
            itemId = ResourceLocation.withDefaultNamespace("unknown");
        }

        CapabilityItem capability = resolveCapability(hero);
        if (capability == null || capability.isEmpty()) {
            return itemId + "|empty|ranged=" + isRangedLoadout(stack);
        }

        String extra = "";
        if (stack.getItem() instanceof PoemOfTheEndItem poem) {
            extra = "|poemMode=" + poem.getMode(stack);
        }

        return itemId + "|" + capability.getWeaponCategory() + ":" + capability.getStyle(patch) + extra + "|ranged=" + isRangedLoadout(stack);
    }

    private static boolean containsRangedKeyword(String value) {
        if (value == null || value.isEmpty()) {
            return false;
        }

        return value.contains("bow")
                || value.contains("crossbow")
                || value.contains("ranged")
                || value.contains("gun")
                || value.contains("firearm")
                || value.contains("rifle")
                || value.contains("pistol")
                || value.contains("revolver")
                || value.contains("musket")
                || value.contains("shotgun")
                || value.contains("sniper")
                || value.contains("launcher")
                || value.contains("cannon")
                || value.contains("blaster");
    }

    private static WeaponCapability buildProfile(String legacyBuilderField,
                                                 Item item,
                                                 Item sampleItem,
                                                 WeaponCategory fallbackCategory,
                                                 Style forcedStyle) {
        WeaponCapability profile = tryBuildLegacyPreset(legacyBuilderField, item, forcedStyle);
        if (profile != null) {
            return profile;
        }

        profile = tryCopySampleCapability(sampleItem);
        if (profile != null) {
            return profile;
        }

        WeaponCapability.Builder builder = WeaponCapability.builder();
        builder.category(fallbackCategory);
        if (forcedStyle != null) {
            builder.styleProvider(patch -> forcedStyle);
        }
        return (WeaponCapability) builder.build();
    }

    private static WeaponCapability tryBuildLegacyPreset(String legacyBuilderField, Item item, Style forcedStyle) {
        try {
            ClassLoader classLoader = HeroEpicFightWeaponProfiles.class.getClassLoader();
            Class<?> buildersClass = Class.forName(LEGACY_BUILDERS_CLASS, false, classLoader);
            Class<?> builderManagerClass = Class.forName(LEGACY_BUILDER_MANAGER_CLASS, false, classLoader);

            Field builderField = buildersClass.getField(legacyBuilderField);
            Object builderEntry = builderField.get(null);
            if (builderEntry == null) {
                return null;
            }

            Method idMethod = builderEntry.getClass().getMethod("id");
            Object builderId = idMethod.invoke(builderEntry);
            if (!(builderId instanceof ResourceLocation id)) {
                return null;
            }

            Method getEntryMethod = builderManagerClass.getMethod("getEntry", ResourceLocation.class);
            Object entry = getEntryMethod.invoke(null, id);
            if (entry == null) {
                return null;
            }

            Method exCapRegistrationMethod = findMethod(
                    "yesman.epicfight.world.capabilities.item.WeaponCapabilityPresets",
                    "exCapRegistration",
                    java.util.Map.Entry.class,
                    Item.class
            );
            if (exCapRegistrationMethod == null) {
                return null;
            }

            Object builderObject = exCapRegistrationMethod.invoke(null, entry, item);
            if (!(builderObject instanceof WeaponCapability.Builder builder)) {
                return null;
            }

            if (forcedStyle != null) {
                builder.styleProvider(patch -> forcedStyle);
            }
            return (WeaponCapability) builder.build();
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return null;
        }
    }

    private static WeaponCapability tryCopySampleCapability(Item sampleItem) {
        if (sampleItem == null) {
            return null;
        }

        CapabilityItem capability = EpicFightCapabilities.getItemStackCapability(new ItemStack(sampleItem));
        return capability instanceof WeaponCapability weaponCapability ? weaponCapability : null;
    }

    private static CapabilityItem inferCapability(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }

        Item item = stack.getItem();
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(item);
        String path = itemId != null ? itemId.getPath().toLowerCase(Locale.ROOT) : "";

        if (item instanceof TridentItem || containsAny(path, "trident")) {
            return getOrCreateFallbackProfile("TRIDENT", CapabilityItem.WeaponCategories.TRIDENT, CapabilityItem.Styles.TWO_HAND);
        }
        if (item instanceof SwordItem) {
            return getOrCreateFallbackProfile("SWORD", CapabilityItem.WeaponCategories.SWORD, CapabilityItem.Styles.ONE_HAND);
        }
        if (item instanceof AxeItem) {
            return getOrCreateFallbackProfile("AXE", CapabilityItem.WeaponCategories.AXE, CapabilityItem.Styles.ONE_HAND);
        }
        if (item instanceof PickaxeItem) {
            return getOrCreateFallbackProfile("PICKAXE", CapabilityItem.WeaponCategories.PICKAXE, CapabilityItem.Styles.ONE_HAND);
        }
        if (item instanceof ShovelItem) {
            return getOrCreateFallbackProfile("SHOVEL", CapabilityItem.WeaponCategories.SHOVEL, CapabilityItem.Styles.ONE_HAND);
        }
        if (item instanceof HoeItem) {
            return getOrCreateFallbackProfile("HOE", CapabilityItem.WeaponCategories.HOE, CapabilityItem.Styles.ONE_HAND);
        }
        if (containsAny(path, "greatsword", "claymore", "zweihander")) {
            return getOrCreateFallbackProfile("GREATSWORD", CapabilityItem.WeaponCategories.GREATSWORD, CapabilityItem.Styles.TWO_HAND);
        }
        if (containsAny(path, "longsword")) {
            return getOrCreateFallbackProfile("LONGSWORD", CapabilityItem.WeaponCategories.LONGSWORD, CapabilityItem.Styles.TWO_HAND);
        }
        if (containsAny(path, "spear", "pike", "halberd", "glaive", "lance")) {
            return getOrCreateFallbackProfile("SPEAR", CapabilityItem.WeaponCategories.SPEAR, CapabilityItem.Styles.TWO_HAND);
        }
        if (containsAny(path, "dagger", "knife")) {
            return getOrCreateFallbackProfile("DAGGER", CapabilityItem.WeaponCategories.DAGGER, CapabilityItem.Styles.ONE_HAND);
        }
        if (containsAny(path, "tachi")) {
            return getOrCreateFallbackProfile("TACHI", CapabilityItem.WeaponCategories.TACHI, CapabilityItem.Styles.TWO_HAND);
        }
        if (containsAny(path, "uchigatana", "katana")) {
            return getOrCreateFallbackProfile("UCHIGATANA", CapabilityItem.WeaponCategories.UCHIGATANA, CapabilityItem.Styles.ONE_HAND);
        }
        if (containsAny(path, "fist", "gauntlet", "claw")) {
            return getOrCreateFallbackProfile("FIST", CapabilityItem.WeaponCategories.FIST, CapabilityItem.Styles.ONE_HAND);
        }

        return null;
    }

    private static WeaponCapability getOrCreateFallbackProfile(String key, WeaponCategory category, Style style) {
        return FALLBACK_PROFILES.computeIfAbsent(key + "|" + style, ignored -> {
            WeaponCapability.Builder builder = WeaponCapability.builder();
            builder.category(category);
            builder.styleProvider(patch -> style);
            return (WeaponCapability) builder.build();
        });
    }

    private static boolean containsAny(String value, String... tokens) {
        for (String token : tokens) {
            if (value.contains(token)) {
                return true;
            }
        }
        return false;
    }

    private static Method findMethod(String className, String methodName, Class<?>... parameterTypes) {
        try {
            Class<?> type = Class.forName(className, false, HeroEpicFightWeaponProfiles.class.getClassLoader());
            return type.getMethod(methodName, parameterTypes);
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return null;
        }
    }
}

