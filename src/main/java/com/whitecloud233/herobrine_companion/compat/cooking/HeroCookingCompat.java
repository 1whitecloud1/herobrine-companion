package com.whitecloud233.herobrine_companion.compat.cooking;

import com.whitecloud233.herobrine_companion.entity.HeroEntity;
import com.whitecloud233.herobrine_companion.entity.cooking.CookingRules;
import com.whitecloud233.herobrine_companion.entity.cooking.CookingService;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.List;
import org.jetbrains.annotations.Nullable;

/** @deprecated 烹饪玩法入口已迁入 CookingService；仅保留旧调用兼容。 */
@Deprecated
public final class HeroCookingCompat {
    public static final int INVITED_ACTION_COOK = CookingRules.INVITED_ACTION_COOK;
    public static final int MIN_COOK_REPEAT_COUNT = CookingRules.MIN_COOK_REPEAT_COUNT;
    public static final int MAX_COOK_REPEAT_COUNT = CookingRules.MAX_COOK_REPEAT_COUNT;

    private HeroCookingCompat() { }

    public record CookOptionView(ResourceLocation recipeId, ItemStack result, int maxRepeatCount) { }

    public static boolean isCookwareStation(Level level, BlockPos pos) { return CookingService.isCookwareStation(level, pos); }
    public static boolean tickCookware(HeroEntity hero, BlockPos pos) { return CookingService.tickCookware(hero, pos); }
    public static ItemStack getCookMainHandDisplay(HeroEntity hero) { return CookingService.getCookMainHandDisplay(hero); }
    public static ItemStack getCookOffhandDisplay(HeroEntity hero) { return CookingService.getCookOffhandDisplay(hero); }
    public static void resetCookSelection(HeroEntity hero, BlockPos pos) { CookingService.resetCookSelection(hero, pos); }
    public static List<CookOptionView> getCookOptions(HeroEntity hero, BlockPos pos) { return CookingService.getCookOptions(hero, pos).stream().map(option -> new CookOptionView(option.recipeId(), option.result(), option.maxRepeatCount())).toList(); }
    public static boolean selectCookOption(HeroEntity hero, BlockPos pos, ResourceLocation recipeId) { return CookingService.selectCookOption(hero, pos, recipeId); }
    public static int getCookOptionMaxRepeat(HeroEntity hero, BlockPos pos, ResourceLocation recipeId) { return CookingService.getCookOptionMaxRepeat(hero, pos, recipeId); }
    public static Component cycleCookSelection(HeroEntity hero, BlockPos pos) { return CookingService.cycleCookSelection(hero, pos); }
    public static void setCookRepeatCount(HeroEntity hero, BlockPos pos, int count) { CookingService.setCookRepeatCount(hero, pos, count); }
    public static void beginCookChunkTicket(HeroEntity hero, BlockPos pos) { CookingService.beginCookChunkTicket(hero, pos); }
    public static void endCookChunkTicket(HeroEntity hero) { CookingService.endCookChunkTicket(hero); }
    public static int clampCookRepeatCount(int count) { return CookingService.clampCookRepeatCount(count); }
    public static boolean hasAutonomousCookOptions(HeroEntity hero, BlockPos pos) { return CookingService.hasAutonomousCookOptions(hero, pos); }
    public static boolean beginAutonomousCooking(HeroEntity hero, BlockPos pos) { return CookingService.beginAutonomousCooking(hero, pos); }
    public static boolean isAutonomousCookingActive(HeroEntity hero, @Nullable BlockPos pos) { return CookingService.isAutonomousCookingActive(hero, pos); }
    public static void clearAutonomousCooking(HeroEntity hero) { CookingService.clearAutonomousCooking(hero); }
    public static void tickVisuals(HeroEntity hero) { CookingService.tickVisuals(hero); }
}
