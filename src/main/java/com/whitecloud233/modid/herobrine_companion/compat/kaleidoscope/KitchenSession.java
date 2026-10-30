package com.whitecloud233.modid.herobrine_companion.compat.kaleidoscope;

import com.whitecloud233.modid.herobrine_companion.compat.kaleidoscope.KitchenRecipes.RecipeCandidate;
import com.whitecloud233.modid.herobrine_companion.entity.HeroEntity;
import com.whitecloud233.modid.herobrine_companion.entity.cooking.CookSessionState;
import com.whitecloud233.modid.herobrine_companion.entity.cooking.CookingRules;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

/**
 * 森罗物语的会话状态入口。
 *
 * <p>通用逻辑已收敛到 {@link CookSessionState}，这里只负责三件事：
 * 固定本模组的状态键前缀、桥接森罗特有的 {@link KitchenActionCooldown}、
 * 以及保持包内原有静态方法签名不变（站点类无需改动调用方式）。</p>
 *
 * <p><b>前缀必须是 {@link CookingRules#KALEIDOSCOPE_STATE_PREFIX}：</b>
 * 与农夫乐事的 {@code HeroFd} 前缀不同，这是旧存档兼容与跨厨具隔离的保证。</p>
 */
final class KitchenSession {

    private static final CookSessionState STATE =
            new CookSessionState(CookingRules.KALEIDOSCOPE_STATE_PREFIX);

    private KitchenSession() {
    }

    static void resetCookSelection(HeroEntity hero, BlockPos pos) {
        if (hero == null || pos == null) {
            return;
        }
        KitchenActionCooldown.clear(hero.getPersistentData());
        STATE.resetSelection(hero, pos);
    }

    static void setCookRepeatCount(HeroEntity hero, BlockPos pos, int count) {
        STATE.setRepeatCount(hero, pos, count);
    }

    static void beginCookChunkTicket(HeroEntity hero, BlockPos pos) {
        STATE.beginChunkTicket(hero, pos);
    }

    static void endCookChunkTicket(HeroEntity hero) {
        STATE.endChunkTicket(hero);
        if (hero != null && !hero.level().isClientSide) {
            KitchenActionCooldown.clear(hero.getPersistentData());
        }
    }

    static int clampCookRepeatCount(int count) {
        return CookingRules.clampRepeatCount(count);
    }

    static int getCookRepeatCount(HeroEntity hero, BlockPos pos) {
        return STATE.repeatCount(hero, pos);
    }

    /** 消耗一份重复计数；用尽时收工并清理会话。 */
    static boolean consumeCookRepeat(HeroEntity hero, BlockPos pos) {
        if (hero == null || pos == null) {
            return false;
        }

        if (STATE.consumeRepeat(hero, pos)) {
            endCookChunkTicket(hero);
            hero.setInvitedPos(null);
            hero.setInvitedAction(CookingRules.ACTION_NONE);
            STATE.setRepeatCount(hero, pos, CookingRules.MIN_COOK_REPEAT_COUNT);
            return true;
        }
        return true;
    }

    @Nullable
    static ResourceLocation getCookSelectionRecipeId(HeroEntity hero, BlockPos pos) {
        return STATE.selectedRecipe(hero, pos);
    }

    static int getCookSelectionIndex(HeroEntity hero, BlockPos pos, int optionCount) {
        return STATE.selectionIndex(hero, pos, optionCount);
    }

    static void setCookSelection(HeroEntity hero, BlockPos pos, int index, @Nullable ResourceLocation recipeId) {
        STATE.setSelection(hero, pos, index, recipeId);
    }

    static <T extends RecipeCandidate> int resolveSelectedRecipeIndex(HeroEntity hero, BlockPos pos, List<T> options) {
        return STATE.resolveSelectedIndex(hero, pos, options, RecipeCandidate::recipeId);
    }
}
