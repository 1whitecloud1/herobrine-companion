package com.whitecloud233.herobrine_companion.entity.cooking;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;

import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import java.util.function.Predicate;

/**
 * 背包材料与配方的匹配计算。
 *
 * <p><b>为什么不放在任一兼容层里：</b>森罗物语与农夫乐事各自实现了一份逐字符相同的
 * 「统计可用材料、展开配方所需材料、回溯分配」逻辑。这些计算只依赖
 * {@link ItemStack}、{@link Ingredient} 与 {@link Predicate}，不涉及任何模组的 API，
 * 把它们留在兼容层里会造成两套等价代码各自演化。</p>
 *
 * <p><b>这不构成两个模组的耦合：</b>本类没有任何状态，也不持有任何模组类型，
 * 两个兼容层只是各自调用同一组纯函数，彼此不知道对方存在。
 * 真正需要避免的耦合是共享可变状态（例如同一个 NBT 键），那由
 * {@link CookSessionState} 用键前缀隔离解决。</p>
 *
 * <p>回溯部分使用泛型 {@code <T>} 而不是具体 {@link Ingredient}，因为
 * {@code Ingredient} 本身实现了 {@code Predicate<ItemStack>}，调用方可以直接传入；
 * 而石磨之类只需要"某个物品能匹配某条件"的场景也能复用同一套算法。</p>
 */
public final class IngredientMatcher {

    private IngredientMatcher() {
    }

    // ==========================================================================
    // 容量计算
    // ==========================================================================

    /** 复制每格材料的剩余数量，供回溯分配时原地扣减而不影响真实背包。 */
    public static int[] copyStackCounts(List<ItemStack> availableStacks) {
        int[] counts = new int[availableStacks.size()];
        for (int i = 0; i < availableStacks.size(); i++) {
            counts[i] = availableStacks.get(i).getCount();
        }
        return counts;
    }

    /** 统计背包中满足该配方的材料总数。 */
    public static int countMatchingItems(Predicate<ItemStack> ingredient, List<ItemStack> availableStacks) {
        int total = 0;
        for (ItemStack stack : availableStacks) {
            if (!stack.isEmpty() && ingredient.test(stack)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    /** 统计背包中与目标物品同种（忽略 NBT）的总数。 */
    public static int countSameItem(ItemStack target, List<ItemStack> availableStacks) {
        int total = 0;
        for (ItemStack stack : availableStacks) {
            if (!stack.isEmpty() && ItemStack.isSameItem(target, stack)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    // ==========================================================================
    // 配方展开
    // ==========================================================================

    /** 去掉配方里的空条目（某些模组会用空 Ingredient 占位）。 */
    public static List<Ingredient> normalize(List<Ingredient> rawIngredients) {
        List<Ingredient> ingredients = new ArrayList<>();
        for (Ingredient ingredient : rawIngredients) {
            if (ingredient != null && !ingredient.isEmpty()) {
                ingredients.add(ingredient);
            }
        }
        return ingredients;
    }

    /** 找出背包里第一个满足条件且数量足够的材料格。 */
    public static ItemStack findMatchingStack(Predicate<ItemStack> ingredient, int minCount, List<ItemStack> availableStacks) {
        for (ItemStack stack : availableStacks) {
            if (!stack.isEmpty() && stack.getCount() >= minCount && ingredient.test(stack)) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }

    /** 收集玩家背包主副手里的非空材料。 */
    public static List<ItemStack> collectAccessibleStacks(net.minecraft.world.entity.player.Player owner) {
        List<ItemStack> stacks = new ArrayList<>(
                owner.getInventory().items.size() + owner.getInventory().offhand.size());
        for (ItemStack stack : owner.getInventory().items) {
            if (!stack.isEmpty()) {
                stacks.add(stack);
            }
        }
        for (ItemStack stack : owner.getInventory().offhand) {
            if (!stack.isEmpty()) {
                stacks.add(stack);
            }
        }
        return stacks;
    }

    // ==========================================================================
    // 去重
    // ==========================================================================

    /**
     * 把候选产物加进结果列表，已存在（同物品同 NBT）则跳过。原地修改 {@code results}。
     *
     * <p>两个兼容层原本各写一份，语义一致：空栈忽略、按 {@code isSameItemSameComponents} 去重。</p>
     */
    public static void addUniqueResult(List<ItemStack> results, ItemStack candidate) {
        if (candidate.isEmpty()) {
            return;
        }
        for (ItemStack existing : results) {
            if (ItemStack.isSameItemSameComponents(existing, candidate)) {
                return;
            }
        }
        results.add(candidate);
    }

    // ==========================================================================
    // 回溯分配
    // ==========================================================================

    /**
     * 判断「锅/砧板上已有的输入」能否与配方条目一一对应。
     *
     * @param currentInputs 当前已放入的材料
     * @param ingredients   配方条目（可为任意谓词，不限于 {@link Ingredient}）
     * @param used          长度不小于 {@code ingredients.size()} 的标记数组，会被修改
     */
    public static <T> boolean matchExistingInputs(List<T> currentInputs, List<? extends Predicate<T>> ingredients,
                                                  boolean[] used, int index) {
        if (index >= currentInputs.size()) {
            return true;
        }

        T input = currentInputs.get(index);
        for (int i = 0; i < ingredients.size(); i++) {
            if (used[i]) {
                continue;
            }
            Predicate<T> ingredient = ingredients.get(i);
            if (!ingredient.test(input)) {
                continue;
            }
            used[i] = true;
            if (matchExistingInputs(currentInputs, ingredients, used, index + 1)) {
                return true;
            }
            used[i] = false;
        }
        return false;
    }

    /**
     * 判断「剩余所需配方条目」能否从背包里凑齐，并把选中的材料按顺序追加到 {@code chosenStacks}。
     *
     * @param counts        每格材料可用数量，匹配过程中会被临时扣减并回溯还原
     * @param chosenStacks  输出：本次匹配选中的材料，失败时恢复原样
     */
    public static <T> boolean matchRemainingIngredients(List<? extends Predicate<T>> remainingIngredients,
                                                        List<T> availableStacks, int[] counts,
                                                        List<T> chosenStacks, int index) {
        if (index >= remainingIngredients.size()) {
            return true;
        }

        Predicate<T> ingredient = remainingIngredients.get(index);
        for (int i = 0; i < availableStacks.size(); i++) {
            if (counts[i] <= 0) {
                continue;
            }
            T candidate = availableStacks.get(i);
            if (!ingredient.test(candidate)) {
                continue;
            }

            counts[i]--;
            chosenStacks.add(candidate);
            if (matchRemainingIngredients(remainingIngredients, availableStacks, counts, chosenStacks, index + 1)) {
                return true;
            }
            chosenStacks.remove(chosenStacks.size() - 1);
            counts[i]++;
        }
        return false;
    }

    // ==========================================================================
    // 可制作份数
    // ==========================================================================

    /**
     * 计算在当前背包与已放入材料的前提下，这份配方还能做多少份。
     *
     * <p>先扣除「锅/砧板上已经放了的输入」，再用剩余配方条目做回溯分配；
     * 之后每一轮完整配方算一份，直到材料不足或达到上限。</p>
     *
     * <p>两个厨具模组原本各写一份逐字符相同的实现（缩进不同导致此前未被识别为重复）。
     * 结果直接决定 Hero 一次接单能做几份，两侧算法若漂移会出现
     * 「森罗能做 5 份、农夫乐事只能做 2 份」的不一致。</p>
     *
     * @param rawIngredients 配方原始材料（内部会过滤空条目）
     * @param currentInputs  已经放进厨具的材料
     * @param availableStacks 玩家背包可用材料
     * @return 可制作份数；0 表示材料不足，{@link CookingRules#MAX_COOK_REPEAT_COUNT} 表示配方为空
     */
    public static int computeCraftCount(List<Ingredient> rawIngredients, List<ItemStack> currentInputs,
                                        List<ItemStack> availableStacks) {
        List<Ingredient> ingredients = normalize(rawIngredients);
        if (ingredients.isEmpty()) {
            return CookingRules.MAX_COOK_REPEAT_COUNT;
        }

        boolean[] usedByCurrent = new boolean[ingredients.size()];
        if (!matchExistingInputs(currentInputs, ingredients, usedByCurrent, 0)) {
            return 0;
        }

        List<Ingredient> firstCycleRemaining = new ArrayList<>();
        for (int i = 0; i < ingredients.size(); i++) {
            if (!usedByCurrent[i]) {
                firstCycleRemaining.add(ingredients.get(i));
            }
        }

        int[] counts = copyStackCounts(availableStacks);
        if (!firstCycleRemaining.isEmpty() && !consumeIngredientGroup(firstCycleRemaining, availableStacks, counts)) {
            return 0;
        }

        int craftCount = 1;
        while (craftCount < CookingRules.MAX_COOK_REPEAT_COUNT
                && consumeIngredientGroup(ingredients, availableStacks, counts)) {
            craftCount++;
        }
        return craftCount;
    }

    /** 尝试消耗一整组配方材料；空配方视为成功。 */
    public static boolean consumeIngredientGroup(List<Ingredient> ingredients, List<ItemStack> availableStacks, int[] counts) {
        return ingredients.isEmpty()
                || matchRemainingIngredients(ingredients, availableStacks, counts, new ArrayList<>(), 0);
    }

    // ==========================================================================
    // 材料规划
    // ==========================================================================

    /**
     * 计算「还差哪些材料」：先看已有输入占用了多少配方条目，再从背包里凑剩下的。
     *
     * <p>返回的 {@link IngredientPlanResult} 是纯数据，两个兼容层各自包装成自己的
     * record 类型（字段语义相同），因此不需要共享 record 定义。</p>
     *
     * @return 材料不足时返回 {@code null}
     */
    @Nullable
    public static IngredientPlanResult planIngredient(List<Ingredient> rawIngredients, List<ItemStack> currentInputs,
                                                      List<ItemStack> availableStacks) {
        List<Ingredient> ingredients = normalize(rawIngredients);

        boolean[] usedByCurrent = new boolean[ingredients.size()];
        if (!matchExistingInputs(currentInputs, ingredients, usedByCurrent, 0)) {
            return null;
        }

        List<Ingredient> remainingIngredients = new ArrayList<>();
        for (int i = 0; i < ingredients.size(); i++) {
            if (!usedByCurrent[i]) {
                remainingIngredients.add(ingredients.get(i));
            }
        }

        if (remainingIngredients.isEmpty()) {
            return new IngredientPlanResult(null, currentInputs.size(), 0);
        }

        int[] counts = copyStackCounts(availableStacks);
        List<ItemStack> chosenStacks = new ArrayList<>();
        if (!matchRemainingIngredients(remainingIngredients, availableStacks, counts, chosenStacks, 0)) {
            return null;
        }

        ItemStack nextIngredient = chosenStacks.isEmpty() ? null : chosenStacks.get(0);
        return new IngredientPlanResult(nextIngredient, currentInputs.size(), remainingIngredients.size());
    }

    /**
     * {@link #planIngredient} 的结果。
     *
     * @param nextIngredient 下一份该放什么；null 表示已有输入已满足整个配方
     * @param matchedCount   已被已有输入满足的条目数
     * @param missingCount   还需要从背包补的条目数
     */
    public record IngredientPlanResult(@Nullable ItemStack nextIngredient, int matchedCount, int missingCount) {
    }
}
