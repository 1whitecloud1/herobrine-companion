package com.whitecloud233.herobrine_companion.compat.epicfight;

import com.whitecloud233.herobrine_companion.entity.HeroEntity;
import com.whitecloud233.herobrine_companion.init.ModItems;
import com.whitecloud233.herobrine_companion.item.PoemOfTheEndItem;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
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
import yesman.epicfight.world.capabilities.item.CapabilityItem.Styles;
import yesman.epicfight.world.capabilities.item.CapabilityItem.WeaponCategories;

public final class HeroEpicFightWeaponProfiles {
   private static final String LEGACY_BUILDERS_CLASS = "yesman.epicfight.api.ex_cap.modules.assets.Builders";
   private static final String LEGACY_BUILDER_MANAGER_CLASS = "yesman.epicfight.api.ex_cap.modules.core.managers.BuilderManager";
   private static final Map<String, WeaponCapability> FALLBACK_PROFILES = new ConcurrentHashMap();
   private static volatile boolean initialized;
   private static WeaponCapability normalProfile;
   private static WeaponCapability realmBreakerProfile;
   private static WeaponCapability thunderProfile;
   private static WeaponCapability voidShatterProfile;

   private HeroEpicFightWeaponProfiles() {
   }

   public static synchronized void bootstrap() {
      if (!initialized && HeroEpicFightCompat.isLoaded()) {
         Item poem = (Item)ModItems.POEM_OF_THE_END.get();
         normalProfile = buildProfile("SWORD", poem, Items.DIAMOND_SWORD, WeaponCategories.SWORD, Styles.ONE_HAND);
         realmBreakerProfile = buildProfile("GREATSWORD", poem, (Item)null, WeaponCategories.GREATSWORD, Styles.TWO_HAND);
         thunderProfile = buildProfile("SPEAR", poem, (Item)null, WeaponCategories.SPEAR, Styles.TWO_HAND);
         voidShatterProfile = buildProfile("DAGGER", poem, (Item)null, WeaponCategories.DAGGER, Styles.ONE_HAND);
         initialized = true;
      }
   }

   public static CapabilityItem resolveCapability(HeroEntity hero) {
      if (hero != null && hero.isAddedToLevel()) {
         try {
            return resolveCapability(hero.getMainHandItem());
         } catch (NullPointerException var2) {
            return CapabilityItem.EMPTY;
         }
      } else {
         return CapabilityItem.EMPTY;
      }
   }

   public static CapabilityItem resolveCapability(ItemStack stack) {
      if (stack != null && !stack.isEmpty()) {
         Item var2 = stack.getItem();
         if (var2 instanceof PoemOfTheEndItem) {
            PoemOfTheEndItem poem = (PoemOfTheEndItem)var2;
            bootstrap();
            WeaponCapability var10000;
            switch (poem.getMode(stack)) {
               case 1 -> var10000 = realmBreakerProfile;
               case 2 -> var10000 = thunderProfile;
               case 3 -> var10000 = voidShatterProfile;
               default -> var10000 = normalProfile;
            }

            return var10000;
         } else {
            CapabilityItem capability = EpicFightCapabilities.getItemStackCapability(stack);
            if (capability != null && !capability.isEmpty()) {
               return capability;
            } else {
               CapabilityItem inferredCapability = inferCapability(stack);
               return inferredCapability != null ? inferredCapability : CapabilityItem.EMPTY;
            }
         }
      } else {
         return CapabilityItem.EMPTY;
      }
   }

   public static boolean isRangedLoadout(HeroEntity hero) {
      if (hero != null && hero.isAddedToLevel()) {
         try {
            return isRangedLoadout(hero.getMainHandItem());
         } catch (NullPointerException var2) {
            return false;
         }
      } else {
         return false;
      }
   }

   public static boolean isRangedLoadout(ItemStack stack) {
      if (stack != null && !stack.isEmpty()) {
         if (stack.getItem() instanceof PoemOfTheEndItem) {
            return false;
         } else if (stack.getItem() instanceof ProjectileWeaponItem) {
            return true;
         } else {
            UseAnim useAnim = stack.getUseAnimation();
            if (useAnim != UseAnim.BOW && useAnim != UseAnim.CROSSBOW) {
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
            } else {
               return true;
            }
         }
      } else {
         return false;
      }
   }

   public static boolean hasHeroControlledCombatAnimations(HeroEntity hero) {
      if (hero != null && hero.isAddedToLevel()) {
         try {
            return hasHeroControlledCombatAnimations(hero.getMainHandItem());
         } catch (NullPointerException var2) {
            return false;
         }
      } else {
         return false;
      }
   }

   public static boolean hasHeroControlledCombatAnimations(ItemStack stack) {
      if (stack != null && !stack.isEmpty()) {
         if (!isRangedLoadout(stack) && !(stack.getItem() instanceof PoemOfTheEndItem)) {
            CapabilityItem capability = resolveCapability(stack);
            return capability != null && !capability.isEmpty();
         } else {
            return true;
         }
      } else {
         return false;
      }
   }

   public static String getProfileKey(HeroEntity hero, LivingEntityPatch<?> patch) {
      if (hero == null) {
         return "missing";
      } else {
         ItemStack stack = hero.getMainHandItem();
         ResourceLocation itemId = stack.isEmpty() ? ResourceLocation.withDefaultNamespace("empty") : BuiltInRegistries.ITEM.getKey(stack.getItem());
         if (itemId == null) {
            itemId = ResourceLocation.withDefaultNamespace("unknown");
         }

         CapabilityItem capability = resolveCapability(hero);
         if (capability != null && !capability.isEmpty()) {
            String extra = "";
            Item var7 = stack.getItem();
            if (var7 instanceof PoemOfTheEndItem) {
               PoemOfTheEndItem poem = (PoemOfTheEndItem)var7;
               int var8 = poem.getMode(stack);
               extra = "|poemMode=" + var8;
            }

            String var9 = String.valueOf(itemId);
            return var9 + "|" + String.valueOf(capability.getWeaponCategory()) + ":" + String.valueOf(capability.getStyle(patch)) + extra + "|ranged=" + isRangedLoadout(stack);
         } else {
            String var10000 = String.valueOf(itemId);
            return var10000 + "|empty|ranged=" + isRangedLoadout(stack);
         }
      }
   }

   private static boolean containsRangedKeyword(String value) {
      if (value != null && !value.isEmpty()) {
         return value.contains("bow") || value.contains("crossbow") || value.contains("ranged") || value.contains("gun") || value.contains("firearm") || value.contains("rifle") || value.contains("pistol") || value.contains("revolver") || value.contains("musket") || value.contains("shotgun") || value.contains("sniper") || value.contains("launcher") || value.contains("cannon") || value.contains("blaster");
      } else {
         return false;
      }
   }

   private static WeaponCapability buildProfile(String legacyBuilderField, Item item, Item sampleItem, WeaponCategory fallbackCategory, Style forcedStyle) {
      WeaponCapability profile = tryBuildLegacyPreset(legacyBuilderField, item, forcedStyle);
      if (profile != null) {
         return profile;
      } else {
         profile = tryCopySampleCapability(sampleItem);
         if (profile != null) {
            return profile;
         } else {
            WeaponCapability.Builder builder = WeaponCapability.builder();
            builder.category(fallbackCategory);
            if (forcedStyle != null) {
               builder.styleProvider((patch) -> forcedStyle);
            }

            return (WeaponCapability)builder.build();
         }
      }
   }

   private static WeaponCapability tryBuildLegacyPreset(String legacyBuilderField, Item item, Style forcedStyle) {
      try {
         ClassLoader classLoader = HeroEpicFightWeaponProfiles.class.getClassLoader();
         Class<?> buildersClass = Class.forName("yesman.epicfight.api.ex_cap.modules.assets.Builders", false, classLoader);
         Class<?> builderManagerClass = Class.forName("yesman.epicfight.api.ex_cap.modules.core.managers.BuilderManager", false, classLoader);
         Field builderField = buildersClass.getField(legacyBuilderField);
         Object builderEntry = builderField.get((Object)null);
         if (builderEntry == null) {
            return null;
         } else {
            Method idMethod = builderEntry.getClass().getMethod("id");
            Object builderId = idMethod.invoke(builderEntry);
            if (builderId instanceof ResourceLocation) {
               ResourceLocation id = (ResourceLocation)builderId;
               Method getEntryMethod = builderManagerClass.getMethod("getEntry", ResourceLocation.class);
               Object entry = getEntryMethod.invoke((Object)null, id);
               if (entry == null) {
                  return null;
               } else {
                  Method exCapRegistrationMethod = findMethod("yesman.epicfight.world.capabilities.item.WeaponCapabilityPresets", "exCapRegistration", Map.Entry.class, Item.class);
                  if (exCapRegistrationMethod == null) {
                     return null;
                  } else {
                     Object builderObject = exCapRegistrationMethod.invoke((Object)null, entry, item);
                     if (builderObject instanceof WeaponCapability.Builder) {
                        WeaponCapability.Builder builder = (WeaponCapability.Builder)builderObject;
                        if (forcedStyle != null) {
                           builder.styleProvider((patch) -> forcedStyle);
                        }

                        return (WeaponCapability)builder.build();
                     } else {
                        return null;
                     }
                  }
               }
            } else {
               return null;
            }
         }
      } catch (LinkageError | ReflectiveOperationException var16) {
         return null;
      }
   }

   private static WeaponCapability tryCopySampleCapability(Item sampleItem) {
      if (sampleItem == null) {
         return null;
      } else {
         CapabilityItem capability = EpicFightCapabilities.getItemStackCapability(new ItemStack(sampleItem));
         WeaponCapability var10000;
         if (capability instanceof WeaponCapability) {
            WeaponCapability weaponCapability = (WeaponCapability)capability;
            var10000 = weaponCapability;
         } else {
            var10000 = null;
         }

         return var10000;
      }
   }

   private static CapabilityItem inferCapability(ItemStack stack) {
      if (stack != null && !stack.isEmpty()) {
         Item item = stack.getItem();
         ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(item);
         String path = itemId != null ? itemId.getPath().toLowerCase(Locale.ROOT) : "";
         if (!(item instanceof TridentItem) && !containsAny(path, "trident")) {
            if (item instanceof SwordItem) {
               return getOrCreateFallbackProfile("SWORD", WeaponCategories.SWORD, Styles.ONE_HAND);
            } else if (item instanceof AxeItem) {
               return getOrCreateFallbackProfile("AXE", WeaponCategories.AXE, Styles.ONE_HAND);
            } else if (item instanceof PickaxeItem) {
               return getOrCreateFallbackProfile("PICKAXE", WeaponCategories.PICKAXE, Styles.ONE_HAND);
            } else if (item instanceof ShovelItem) {
               return getOrCreateFallbackProfile("SHOVEL", WeaponCategories.SHOVEL, Styles.ONE_HAND);
            } else if (item instanceof HoeItem) {
               return getOrCreateFallbackProfile("HOE", WeaponCategories.HOE, Styles.ONE_HAND);
            } else if (containsAny(path, "greatsword", "claymore", "zweihander")) {
               return getOrCreateFallbackProfile("GREATSWORD", WeaponCategories.GREATSWORD, Styles.TWO_HAND);
            } else if (containsAny(path, "longsword")) {
               return getOrCreateFallbackProfile("LONGSWORD", WeaponCategories.LONGSWORD, Styles.TWO_HAND);
            } else if (containsAny(path, "spear", "pike", "halberd", "glaive", "lance")) {
               return getOrCreateFallbackProfile("SPEAR", WeaponCategories.SPEAR, Styles.TWO_HAND);
            } else if (containsAny(path, "dagger", "knife")) {
               return getOrCreateFallbackProfile("DAGGER", WeaponCategories.DAGGER, Styles.ONE_HAND);
            } else if (containsAny(path, "tachi")) {
               return getOrCreateFallbackProfile("TACHI", WeaponCategories.TACHI, Styles.TWO_HAND);
            } else if (containsAny(path, "uchigatana", "katana")) {
               return getOrCreateFallbackProfile("UCHIGATANA", WeaponCategories.UCHIGATANA, Styles.ONE_HAND);
            } else {
               return containsAny(path, "fist", "gauntlet", "claw") ? getOrCreateFallbackProfile("FIST", WeaponCategories.FIST, Styles.ONE_HAND) : null;
            }
         } else {
            return getOrCreateFallbackProfile("TRIDENT", WeaponCategories.TRIDENT, Styles.TWO_HAND);
         }
      } else {
         return null;
      }
   }

   private static WeaponCapability getOrCreateFallbackProfile(String key, WeaponCategory category, Style style) {
      return (WeaponCapability)FALLBACK_PROFILES.computeIfAbsent(key + "|" + String.valueOf(style), (ignored) -> {
         WeaponCapability.Builder builder = WeaponCapability.builder();
         builder.category(category);
         builder.styleProvider((patch) -> style);
         return (WeaponCapability)builder.build();
      });
   }

   private static boolean containsAny(String value, String... tokens) {
      for(String token : tokens) {
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
      } catch (LinkageError | ReflectiveOperationException var4) {
         return null;
      }
   }
}
