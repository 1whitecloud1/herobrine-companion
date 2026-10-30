package com.whitecloud233.modid.herobrine_companion.compat.kaleidoscope;

import com.whitecloud233.modid.herobrine_companion.entity.HeroEntity;
import com.whitecloud233.modid.herobrine_companion.entity.cooking.CookOption;
import com.whitecloud233.modid.herobrine_companion.entity.cooking.CookingRules;
import com.whitecloud233.modid.herobrine_companion.entity.cooking.MillstoneOption;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.fml.ModList;

/** Stable guarded entry point for optional Kaleidoscope Cookery integration. */
public final class HeroKaleidoscopeCompat {
    public static final int INVITED_ACTION_COOK = CookingRules.INVITED_ACTION_COOK;

    public static final int INVITED_ACTION_MILLSTONE = CookingRules.INVITED_ACTION_MILLSTONE;

    private static final String MOD_ID = "kaleidoscope_cookery";

    public static final int MIN_COOK_REPEAT_COUNT = CookingRules.MIN_COOK_REPEAT_COUNT;

    public static final int MAX_COOK_REPEAT_COUNT = CookingRules.MAX_COOK_REPEAT_COUNT;

    private HeroKaleidoscopeCompat() {
    }

    public static boolean isLoaded() {
        return ModList.get().isLoaded(MOD_ID);
    }

    public static boolean isCookwareStation(Level level, BlockPos pos) {
        if (!isLoaded() || level == null || pos == null) {
            return false;
        }
        return KitchenStations.isCookwareStation(level, pos);
    }

    public static boolean tickCookware(HeroEntity hero, BlockPos pos) {
        if (!isLoaded() || hero == null || pos == null || hero.level().isClientSide) {
            return false;
        }
        beginCookChunkTicket(hero, pos);
        return KitchenStations.tickCookware(hero, pos);
    }

    public static ItemStack getCookMainHandDisplay(HeroEntity hero) {
        if (!isLoaded() || hero == null || hero.getInvitedAction() != INVITED_ACTION_COOK || hero.getInvitedPos() == null) {
            return ItemStack.EMPTY;
        }
        return KitchenStations.getCookMainHandDisplay(hero.level(), hero.getInvitedPos());
    }

    public static ItemStack getCookOffhandDisplay(HeroEntity hero) {
        if (!isLoaded() || hero == null || hero.getInvitedAction() != INVITED_ACTION_COOK || hero.getInvitedPos() == null) {
            return ItemStack.EMPTY;
        }
        return KitchenStations.getCookOffhandDisplay(hero.level(), hero.getInvitedPos());
    }

    public static void resetCookSelection(HeroEntity hero, BlockPos pos) {
        KitchenSession.resetCookSelection(hero, pos);
    }

    /** 石磨不是锅具，做菜路由不会命中它，这里单独开一组入口。 */
    public static boolean isMillstoneStation(Level level, BlockPos pos) {
        if (!isLoaded() || level == null || pos == null) {
            return false;
        }
        return MillstoneKitchenStation.supports(level, pos);
    }

    public static BlockPos getMillstoneCenter(Level level, BlockPos pos) {
        if (!isLoaded() || level == null || pos == null) {
            return pos;
        }
        return MillstoneKitchenStation.center(level, pos);
    }

    /** 已被别的生物占用的石磨不能再分配 Hero，否则会把别人挤下磨。 */
    public static boolean isMillstoneBusy(HeroEntity hero, BlockPos pos) {
        if (!isLoaded() || hero == null || pos == null || hero.level().isClientSide) {
            return false;
        }
        return MillstoneKitchenStation.isBusy(hero, pos);
    }

    /** 邀请前的可用性判定：能不能让 Hero 上这台磨。 */
    public static boolean canHostMillstone(HeroEntity hero, BlockPos pos) {
        if (!isLoaded() || hero == null || pos == null || hero.level().isClientSide) {
            return false;
        }
        return MillstoneKitchenStation.canHost(hero, pos);
    }

    public static boolean tickMillstone(HeroEntity hero, BlockPos pos) {
        if (!isLoaded() || hero == null || pos == null || hero.level().isClientSide) {
            return false;
        }
        return MillstoneKitchenStation.tick(hero, pos);
    }
    public static void releaseMillstone(HeroEntity hero, BlockPos pos) {
        if (!isLoaded() || hero == null || pos == null || hero.level().isClientSide) {
            return;
        }
        MillstoneKitchenStation.release(hero, pos);
    }

    /** 石磨可选的研磨方案（按玩家背包现有材料算）。 */
    public static List<MillstoneOption> getMillstoneOptions(HeroEntity hero, BlockPos pos) {
        if (!isLoaded() || hero == null || pos == null || hero.level().isClientSide) {
            return List.of();
        }
        return MillstoneKitchenStation.options(hero, pos);
    }

    public static int placeMillstoneInput(HeroEntity hero, BlockPos pos, ResourceLocation recipeId, int count) {
        if (!isLoaded() || hero == null || pos == null || recipeId == null || hero.level().isClientSide) {
            return 0;
        }
        return MillstoneKitchenStation.placeInput(hero, pos, recipeId, count);
    }

    /** 材料还在磨、或产物未取走 → 还没干完。 */
    public static boolean isMillstoneWorking(HeroEntity hero, BlockPos pos) {
        if (!isLoaded() || hero == null || pos == null || hero.level().isClientSide) {
            return false;
        }
        return MillstoneKitchenStation.isWorking(hero, pos);
    }
    public static List<CookOption> getCookOptions(HeroEntity hero, BlockPos pos) {
        if (!isLoaded() || hero == null || pos == null || hero.level().isClientSide) {
            return List.of();
        }
        return KitchenStations.getCookOptions(hero, pos);
    }

    public static List<ItemStack> getAutonomousCookResults(Level level, BlockPos pos) {
        if (!isLoaded() || level == null || pos == null || level.isClientSide) {
            return List.of();
        }
        return KitchenRecipeCatalog.getAutonomousCookResults(level, pos);
    }

    public static List<AutonomousCookOption> getAutonomousCookOptions(Level level, BlockPos pos) {
        if (!isLoaded() || level == null || pos == null || level.isClientSide) {
            return List.of();
        }
        return KitchenRecipeCatalog.getAutonomousCookOptions(level, pos);
    }

    public static CompoundTag captureAutonomousCookwareSnapshot(Level level, BlockPos pos) {
        if (!isLoaded() || level == null || pos == null) {
            return new CompoundTag();
        }
        return KitchenAutonomousDisplay.captureAutonomousCookwareSnapshot(level, pos);
    }

    public static boolean applyAutonomousCookwareDisplay(Level level, BlockPos pos, ResourceLocation recipeId, ItemStack result) {
        if (!isLoaded() || level == null || pos == null || recipeId == null || result == null || result.isEmpty() || level.isClientSide) {
            return false;
        }
        return KitchenAutonomousDisplay.applyAutonomousCookwareDisplay(level, pos, recipeId, result);
    }

    public static void restoreAutonomousCookwareSnapshot(Level level, BlockPos pos, CompoundTag snapshot) {
        if (!isLoaded() || level == null || pos == null || snapshot == null || snapshot.isEmpty()) {
            return;
        }
        KitchenAutonomousDisplay.restoreAutonomousCookwareSnapshot(level, pos, snapshot);
    }

    public static boolean selectCookOption(HeroEntity hero, BlockPos pos, ResourceLocation recipeId) {
        if (!isLoaded() || hero == null || pos == null || recipeId == null || hero.level().isClientSide) {
            return false;
        }
        return KitchenStations.selectCookOption(hero, pos, recipeId);
    }

    public static int getCookOptionMaxRepeat(HeroEntity hero, BlockPos pos, ResourceLocation recipeId) {
        if (!isLoaded() || hero == null || pos == null || recipeId == null || hero.level().isClientSide) {
            return 0;
        }
        return KitchenStations.getCookOptionMaxRepeat(hero, pos, recipeId);
    }

    public static Component cycleCookSelection(HeroEntity hero, BlockPos pos) {
        if (!isLoaded() || hero == null || pos == null || hero.level().isClientSide) {
            return Component.translatable("message.herobrine_companion.invite_cook_none");
        }
        return KitchenStations.cycleCookSelection(hero, pos);
    }

    public static void setCookRepeatCount(HeroEntity hero, BlockPos pos, int count) {
        KitchenSession.setCookRepeatCount(hero, pos, count);
    }

    public static void beginCookChunkTicket(HeroEntity hero, BlockPos pos) {
        KitchenSession.beginCookChunkTicket(hero, pos);
    }

    public static void endCookChunkTicket(HeroEntity hero) {
        KitchenSession.endCookChunkTicket(hero);
    }

    public static int clampCookRepeatCount(int count) {
        return KitchenSession.clampCookRepeatCount(count);
    }

    public record AutonomousCookOption(ResourceLocation recipeId, ItemStack result) {
    }
}
