package com.whitecloud233.modid.herobrine_companion.compat.epicfight;

import com.whitecloud233.modid.herobrine_companion.HerobrineCompanion;
import com.whitecloud233.modid.herobrine_companion.entity.HeroEntity;
import com.whitecloud233.modid.herobrine_companion.item.PoemOfTheEndItem;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.item.UseAnim;
import net.minecraftforge.registries.ForgeRegistries;
import yesman.epicfight.world.capabilities.EpicFightCapabilities;
import yesman.epicfight.world.capabilities.entitypatch.LivingEntityPatch;
import yesman.epicfight.world.capabilities.item.CapabilityItem;
import yesman.epicfight.world.capabilities.item.WeaponCapability;
import yesman.epicfight.world.capabilities.item.WeaponCapabilityPresets;

import java.util.Locale;
import java.util.function.Function;

public final class HeroEpicFightWeaponProfiles {
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

        Item poem = HerobrineCompanion.POEM_OF_THE_END.get();
        normalProfile = buildPreset(WeaponCapabilityPresets.SWORD, poem, CapabilityItem.Styles.ONE_HAND);
        realmBreakerProfile = buildPreset(WeaponCapabilityPresets.SWORD, poem, CapabilityItem.Styles.ONE_HAND);
        thunderProfile = buildPreset(WeaponCapabilityPresets.SWORD, poem, CapabilityItem.Styles.ONE_HAND);
        voidShatterProfile = buildPreset(WeaponCapabilityPresets.SWORD, poem, CapabilityItem.Styles.ONE_HAND);
        initialized = true;
    }

    public static CapabilityItem resolveCapability(HeroEntity hero) {
        if (hero == null || !hero.isAddedToWorld()) {
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

        return EpicFightCapabilities.getItemStackCapabilityOr(stack, CapabilityItem.EMPTY);
    }

    public static boolean isRangedLoadout(HeroEntity hero) {
        if (hero == null || !hero.isAddedToWorld()) {
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

        ResourceLocation itemId = ForgeRegistries.ITEMS.getKey(stack.getItem());
        String itemPath = itemId != null ? itemId.toString().toLowerCase(Locale.ROOT) : "";
        return containsRangedKeyword(itemPath);
    }

    public static boolean hasHeroControlledCombatAnimations(HeroEntity hero) {
        if (hero == null || !hero.isAddedToWorld()) {
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

        if (isRangedLoadout(stack) || stack.getItem() instanceof PoemOfTheEndItem || HeroNightfallMovesets.isSupported(stack)) {
            return true;
        }

        CapabilityItem capability = resolveCapability(stack);
        if (capability == null || capability.isEmpty()) {
            return false;
        }

        var category = capability.getWeaponCategory();
        return category == CapabilityItem.WeaponCategories.SWORD
                || category == CapabilityItem.WeaponCategories.AXE
                || category == CapabilityItem.WeaponCategories.LONGSWORD
                || category == CapabilityItem.WeaponCategories.SPEAR
                || category == CapabilityItem.WeaponCategories.GREATSWORD
                || category == CapabilityItem.WeaponCategories.UCHIGATANA
                || category == CapabilityItem.WeaponCategories.TACHI
                || category == CapabilityItem.WeaponCategories.DAGGER;
    }

    public static String getProfileKey(HeroEntity hero, LivingEntityPatch<?> patch) {
        if (hero == null) {
            return "missing";
        }

        ItemStack stack = hero.getMainHandItem();
        ResourceLocation itemId = stack.isEmpty() ? ResourceLocation.withDefaultNamespace("empty") : ForgeRegistries.ITEMS.getKey(stack.getItem());
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

    private static WeaponCapability buildPreset(Function<Item, CapabilityItem.Builder> presetFactory, Item item, CapabilityItem.Styles forcedStyle) {
        WeaponCapability.Builder builder = (WeaponCapability.Builder) presetFactory.apply(item);
        if (forcedStyle != null) {
            builder.styleProvider(patch -> forcedStyle);
        }
        return (WeaponCapability) builder.build();
    }
}

