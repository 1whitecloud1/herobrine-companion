package com.whitecloud233.herobrine_companion.compat.cooking;

import com.whitecloud233.herobrine_companion.compat.farmersdelight.HeroFarmersDelightCompat;
import com.whitecloud233.herobrine_companion.compat.kaleidoscope.HeroKaleidoscopeCompat;
import com.whitecloud233.herobrine_companion.entity.HeroEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.List;

public final class HeroCookingCompat {
    public static final int INVITED_ACTION_COOK = 4;
    public static final int MIN_COOK_REPEAT_COUNT = 1;
    public static final int MAX_COOK_REPEAT_COUNT = 64;

    private HeroCookingCompat() {
    }

    public static boolean isCookwareStation(Level level, BlockPos pos) {
        return HeroFarmersDelightCompat.isCookwareStation(level, pos)
                || HeroKaleidoscopeCompat.isCookwareStation(level, pos);
    }

    public static boolean tickCookware(HeroEntity hero, BlockPos pos) {
        if (HeroFarmersDelightCompat.isCookwareStation(hero.level(), pos)) {
            return HeroFarmersDelightCompat.tickCookware(hero, pos);
        }
        return HeroKaleidoscopeCompat.tickCookware(hero, pos);
    }

    public static ItemStack getCookMainHandDisplay(HeroEntity hero) {
        if (hero == null || hero.getInvitedPos() == null) {
            return ItemStack.EMPTY;
        }
        if (HeroFarmersDelightCompat.isCookwareStation(hero.level(), hero.getInvitedPos())) {
            return HeroFarmersDelightCompat.getCookMainHandDisplay(hero);
        }
        return HeroKaleidoscopeCompat.getCookMainHandDisplay(hero);
    }

    public static ItemStack getCookOffhandDisplay(HeroEntity hero) {
        if (hero == null || hero.getInvitedPos() == null) {
            return ItemStack.EMPTY;
        }
        if (HeroFarmersDelightCompat.isCookwareStation(hero.level(), hero.getInvitedPos())) {
            return HeroFarmersDelightCompat.getCookOffhandDisplay(hero);
        }
        return HeroKaleidoscopeCompat.getCookOffhandDisplay(hero);
    }

    public static void resetCookSelection(HeroEntity hero, BlockPos pos) {
        if (HeroFarmersDelightCompat.isCookwareStation(hero.level(), pos)) {
            HeroFarmersDelightCompat.resetCookSelection(hero, pos);
            return;
        }
        HeroKaleidoscopeCompat.resetCookSelection(hero, pos);
    }

    public static List<CookOptionView> getCookOptions(HeroEntity hero, BlockPos pos) {
        if (HeroFarmersDelightCompat.isCookwareStation(hero.level(), pos)) {
            return HeroFarmersDelightCompat.getCookOptions(hero, pos);
        }
        return HeroKaleidoscopeCompat.getCookOptions(hero, pos).stream()
                .map(option -> new CookOptionView(option.recipeId(), option.result(), option.maxRepeatCount()))
                .toList();
    }

    public static boolean selectCookOption(HeroEntity hero, BlockPos pos, ResourceLocation recipeId) {
        if (HeroFarmersDelightCompat.isCookwareStation(hero.level(), pos)) {
            return HeroFarmersDelightCompat.selectCookOption(hero, pos, recipeId);
        }
        return HeroKaleidoscopeCompat.selectCookOption(hero, pos, recipeId);
    }

    public static int getCookOptionMaxRepeat(HeroEntity hero, BlockPos pos, ResourceLocation recipeId) {
        if (HeroFarmersDelightCompat.isCookwareStation(hero.level(), pos)) {
            return HeroFarmersDelightCompat.getCookOptionMaxRepeat(hero, pos, recipeId);
        }
        return HeroKaleidoscopeCompat.getCookOptionMaxRepeat(hero, pos, recipeId);
    }

    public static Component cycleCookSelection(HeroEntity hero, BlockPos pos) {
        if (HeroFarmersDelightCompat.isCookwareStation(hero.level(), pos)) {
            return HeroFarmersDelightCompat.cycleCookSelection(hero, pos);
        }
        return HeroKaleidoscopeCompat.cycleCookSelection(hero, pos);
    }

    public static void setCookRepeatCount(HeroEntity hero, BlockPos pos, int count) {
        if (HeroFarmersDelightCompat.isCookwareStation(hero.level(), pos)) {
            HeroFarmersDelightCompat.setCookRepeatCount(hero, pos, count);
            return;
        }
        HeroKaleidoscopeCompat.setCookRepeatCount(hero, pos, count);
    }

    public static void beginCookChunkTicket(HeroEntity hero, BlockPos pos) {
        if (HeroFarmersDelightCompat.isCookwareStation(hero.level(), pos)) {
            HeroFarmersDelightCompat.beginCookChunkTicket(hero, pos);
            return;
        }
        HeroKaleidoscopeCompat.beginCookChunkTicket(hero, pos);
    }

    public static void endCookChunkTicket(HeroEntity hero) {
        HeroFarmersDelightCompat.endCookChunkTicket(hero);
        HeroKaleidoscopeCompat.endCookChunkTicket(hero);
    }

    public static int clampCookRepeatCount(int count) {
        return Math.max(MIN_COOK_REPEAT_COUNT, Math.min(MAX_COOK_REPEAT_COUNT, count));
    }

    public record CookOptionView(ResourceLocation recipeId, ItemStack result, int maxRepeatCount) {
    }
}
