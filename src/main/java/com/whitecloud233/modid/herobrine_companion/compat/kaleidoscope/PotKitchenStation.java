package com.whitecloud233.modid.herobrine_companion.compat.kaleidoscope;

import com.github.ysbbbbbb.kaleidoscopecookery.api.blockentity.IPot;
import com.github.ysbbbbbb.kaleidoscopecookery.blockentity.kitchen.PotBlockEntity;
import com.github.ysbbbbbb.kaleidoscopecookery.crafting.recipe.PotRecipe;
import com.github.ysbbbbbb.kaleidoscopecookery.init.ModRecipes;
import com.github.ysbbbbbb.kaleidoscopecookery.init.tag.TagMod;
import com.whitecloud233.modid.herobrine_companion.compat.kaleidoscope.KitchenRecipes.BackendCookOption;
import com.whitecloud233.modid.herobrine_companion.compat.kaleidoscope.KitchenRecipes.IngredientPlan;
import com.whitecloud233.modid.herobrine_companion.compat.kaleidoscope.KitchenRecipes.PotPlan;
import com.whitecloud233.modid.herobrine_companion.entity.HeroEntity;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.BooleanSupplier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.entity.BlockEntity;

/** Plans and executes frying-pot interactions. */
final class PotKitchenStation {
    private PotKitchenStation() {
    }

    static List<BackendCookOption> mapPotOptions(List<PotPlan> plans) {
        List<BackendCookOption> options = new ArrayList<>(plans.size());
        for (PotPlan plan : plans) {
            options.add(new BackendCookOption(plan.recipeId(), plan.result().copy(), plan.maxRepeatCount()));
        }
        return options;
    }

    static boolean tickPot(HeroEntity hero, ServerLevel level, ServerPlayer owner,
                                   PotBlockEntity pot) {
        int status = pot.getStatus();
        if (KitchenDiagnostics.LOGGER.isDebugEnabled() && level.getGameTime() % 20L == 0L) {
            KitchenDiagnostics.LOGGER.debug("[CookDebug] pot status={} currentTick={} inputs={}", status, pot.getCurrentTick(),
                    KitchenRecipes.collectNonEmptyStacks(pot.getInputs()).size());
        }
        if (status == IPot.FINISHED
                || status == IPot.BURNT) {
            return tryTakeOutPot(hero, level, owner, pot);
        }

        if (status == IPot.COOKING) {
            if (KitchenActionCooldown.tryCookAction(hero, level, pot.getBlockPos(), 20)) {
                pot.onShovelHit(level, hero, ItemStack.EMPTY);
                KitchenDiagnostics.cookDiag("pot.cooking", "stir tick=" + pot.getCurrentTick());
                owner.getInventory().setChanged();
                return true;
            }
            return false;
        }

        // 只处理「等待加配菜」状态：其余未知状态一律不动，避免对着别的状态空转放油/加菜。
        if (status != IPot.PUT_INGREDIENT) {
            return false;
        }

        List<PotPlan> plans = collectPotPlans(level, owner, pot);
        int selectedIndex = resolvePotPlanIndex(hero, pot, plans);
        KitchenDiagnostics.cookDiag("pot.state", "status=" + status + " tick=" + pot.getCurrentTick()
                + " empty=" + pot.isEmpty() + " inputs=" + KitchenRecipes.collectNonEmptyStacks(pot.getInputs()).size()
                + " plans=" + plans.size() + " sel=" + selectedIndex
                + " selRecipe=" + KitchenSession.getCookSelectionRecipeId(hero, pot.getBlockPos()));

        // ① 缺油时先补油。补不上也不再直接 return false —— 旧实现在这里直接放弃，
        //    于是「没油」= 永久卡在“加入配菜中……”，而油其实并不是开炒的必要条件。
        if (pot.getCurrentTick() <= 0) {
            if (KitchenRecipes.tryAccessibleStacks(owner, stack -> pot.onPlaceOil(level, hero, stack))) {
                KitchenDiagnostics.cookDiag("pot.oil", "OK");
                owner.getInventory().setChanged();
                return true;
            }
            KitchenDiagnostics.cookDiag("pot.oil", "FAIL noOil");
        }

        // ② 缺料就补料（addIngredient 只要求 status == PUT_INGREDIENT，与锅里有没油无关）。
        if (selectedIndex >= 0) {
            ItemStack nextIngredient = plans.get(selectedIndex).ingredientPlan().nextIngredient();
            if (nextIngredient != null && !nextIngredient.isEmpty()) {
                if (pot.addIngredient(level, hero, nextIngredient)) {
                    KitchenDiagnostics.cookDiag("pot.add", "OK " + nextIngredient.getItem());
                    owner.getInventory().setChanged();
                    return true;
                }
                KitchenDiagnostics.cookDiag("pot.add", "FAIL " + nextIngredient.getItem() + " empty=" + pot.isEmpty()
                        + " status=" + pot.getStatus() + " stackBlocked="
                        + nextIngredient.is(TagMod.INGREDIENT_BLOCKLIST));
                // 加不进去（锅已满 / 该材料被模组黑名单拦截）也不能放弃：
                // 否则锅内材料已经定了、配方却还要更多，Hero 会永远卡在“加入配菜中……”。
                // 下面照常用现有材料开炒（不成菜时模组会给出“谜之炒菜”）。
            }
        }

        // ③ 料齐了（或加不进去 / 压根没有方案）→ 开锅。
        //    关键：1.6.0 的 onShovelHit 在 PUT_INGREDIENT 且锅内非空时直接 startCooking()，
        //    完全不看 currentTick、也不要求锅里有油，所以断油不再是开炒的拦路虎。
        if (pot.isEmpty()) {
            // 空锅且没料可放：确实无事可做（等油熬干后模组自己会 reset）。
            KitchenDiagnostics.cookDiag("pot.idle", "empty plans=" + plans.size() + " sel=" + selectedIndex
                    + " tick=" + pot.getCurrentTick());
            return false;
        }
        if (!KitchenActionCooldown.tryCookAction(hero, level, pot.getBlockPos(), 20)) {
            return false;
        }
        KitchenDiagnostics.cookDiag("pot.stirFry", "OK inputs=" + KitchenRecipes.collectNonEmptyStacks(pot.getInputs()).size()
                + " sel=" + selectedIndex);
        pot.onShovelHit(level, hero, ItemStack.EMPTY);
        owner.getInventory().setChanged();
        return true;
    }

    /**
     * 挑选炒锅配方。候选列表每 tick 按「锅内现有材料 + 主人背包」重算，玩家选中的那一条会因为
     * 其它配方变得可做、背包变化而漂移甚至消失。此时不能直接放弃（放弃 = 永久卡在“加入配菜中……”），
     * 改为在锅内已有材料时选「缺得最少」的那条，让 Hero 顺着锅内已放的材料把这一锅炒完。
     */
    static int resolvePotPlanIndex(HeroEntity hero,
                                           PotBlockEntity pot,
                                           List<PotPlan> plans) {
        if (plans.isEmpty()) {
            return -1;
        }

        int selectedIndex = KitchenSession.resolveSelectedRecipeIndex(hero, pot.getBlockPos(), plans);
        boolean potEmpty = pot.isEmpty();
        if (selectedIndex >= 0 && (potEmpty || plans.get(selectedIndex).ingredientPlan().matchedCount() > 0)) {
            return selectedIndex;
        }

        if (potEmpty) {
            return selectedIndex;
        }

        int bestIndex = -1;
        for (int i = 0; i < plans.size(); i++) {
            if (bestIndex < 0 || isBetterPotPlan(plans.get(i), plans.get(bestIndex))) {
                bestIndex = i;
            }
        }
        return bestIndex;
    }

    static boolean isBetterPotPlan(PotPlan candidate, PotPlan currentBest) {
        return KitchenRecipes.isBetterIngredientChoice(candidate, candidate.ingredientPlan(),
                currentBest, currentBest.ingredientPlan());
    }

    /**
     * 出锅。1.6.0 的 {@code takeOutProduct} 只有两条成功路径：
     * <ol>
     *   <li>配方带 carrier（碗/盘…）：必须手持 carrier 物品且数量够装成品；</li>
     *   <li>否则：必须手持厨房锅铲（tag {@code kaleidoscope_cookery:kitchen_shovel}）。</li>
     * </ol>
     * 两条都不满足时，模组除了返回 false 还会 <b>{@code user.hurt(inFire, 1)}</b>。
     * 操作者必须是 Hero，不能传作为材料来源/收货人的 owner，否则烫伤会扣玩家的血。
     * 只尝试 carrier 和厨房锅铲，避免逐格试错反复触发厨具副作用。
     */
    static boolean tryTakeOutPot(HeroEntity hero, ServerLevel level, ServerPlayer owner,
                                         IPot pot) {
        ItemStack carrier = findPotCarrierStack(owner, pot);
        if (!carrier.isEmpty() && takeOutPotWith(hero, level, owner, pot, carrier)) {
            return true;
        }
        ItemStack shovel = findKitchenShovelStack(owner);
        if (!shovel.isEmpty() && takeOutPotWith(hero, level, owner, pot, shovel)) {
            return true;
        }
        KitchenDiagnostics.cookDiag("pot.takeout", "FAIL status=" + pot.getStatus()
                + " carrier=" + (carrier.isEmpty() ? "none" : carrier.getItem())
                + " shovel=" + (shovel.isEmpty() ? "none" : shovel.getItem()));
        return false;
    }

    static boolean takeOutPotWith(HeroEntity hero, ServerLevel level, ServerPlayer owner,
                                          IPot pot,
                                          ItemStack held) {
        // 兼容要求潜行的版本；只临时改变实际操作者 Hero 的状态。
        if (!withTemporaryShift(hero, () -> pot.takeOutProduct(level, hero, held))) {
            return false;
        }
        owner.getInventory().setChanged();
        if (pot instanceof BlockEntity blockEntity) {
            KitchenSession.consumeCookRepeat(hero, blockEntity.getBlockPos());
        }
        KitchenDiagnostics.cookDiag("pot.takeout", "OK with=" + held.getItem());
        return true;
    }

    /** 找配方 carrier（碗/盘…）对应的背包物品，数量必须够装下成品。 */
    static ItemStack findPotCarrierStack(ServerPlayer owner,
                                                 IPot pot) {
        Object rawCarrier = KitchenReflection.getFieldValue(pot, "carrier");
        if (!(rawCarrier instanceof Ingredient carrier) || carrier.isEmpty()) {
            return ItemStack.EMPTY;
        }
        int need = 1;
        Object rawResult = KitchenReflection.getFieldValue(pot, "result");
        if (rawResult instanceof ItemStack result && !result.isEmpty()) {
            need = Math.max(1, result.getCount());
        }
        for (ItemStack stack : owner.getInventory().items) {
            if (!stack.isEmpty() && stack.getCount() >= need && carrier.test(stack)) {
                return stack;
            }
        }
        for (ItemStack stack : owner.getInventory().offhand) {
            if (!stack.isEmpty() && stack.getCount() >= need && carrier.test(stack)) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }

    /** 找背包里的厨房锅铲（tag kaleidoscope_cookery:kitchen_shovel）。 */
    static ItemStack findKitchenShovelStack(ServerPlayer owner) {
        for (ItemStack stack : owner.getInventory().items) {
            if (!stack.isEmpty()
                    && stack.is(TagMod.KITCHEN_SHOVEL)) {
                return stack;
            }
        }
        for (ItemStack stack : owner.getInventory().offhand) {
            if (!stack.isEmpty()
                    && stack.is(TagMod.KITCHEN_SHOVEL)) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }

    static List<PotPlan> collectPotPlans(ServerLevel level, ServerPlayer owner,
                                                 PotBlockEntity pot) {
        List<ItemStack> currentInputs = KitchenRecipes.collectNonEmptyStacks(pot.getInputs());
        List<ItemStack> availableStacks = KitchenRecipes.collectAccessibleStacks(owner);
        List<PotPlan> plans = new ArrayList<>();

        for (PotRecipe recipe : level.getRecipeManager().getAllRecipesFor(ModRecipes.POT_RECIPE)) {
            IngredientPlan ingredientPlan = KitchenRecipes.buildIngredientPlan(recipe.ingredients(), currentInputs, availableStacks);
            int maxRepeatCount = KitchenRecipes.computeIngredientCraftCount(recipe.ingredients(), currentInputs, availableStacks);
            if (ingredientPlan != null && maxRepeatCount > 0) {
                plans.add(new PotPlan(recipe.getId(), recipe.getResultItem(level.registryAccess()).copy(),
                        ingredientPlan, maxRepeatCount));
            }
        }

        plans.sort(Comparator.comparing(plan -> plan.recipeId().toString()));
        return plans;
    }

    static boolean withTemporaryShift(HeroEntity hero, BooleanSupplier action) {
        boolean wasShiftDown = hero.isShiftKeyDown();
        if (!wasShiftDown) {
            hero.setShiftKeyDown(true);
        }
        try {
            return action.getAsBoolean();
        } finally {
            if (!wasShiftDown) {
                hero.setShiftKeyDown(false);
            }
        }
    }
}
