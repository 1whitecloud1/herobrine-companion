package com.whitecloud233.modid.herobrine_companion.compat.kaleidoscope;

import com.github.ysbbbbbb.kaleidoscopecookery.block.kitchen.MillstoneBlock;
import com.github.ysbbbbbb.kaleidoscopecookery.block.kitchen.NinePart;
import com.github.ysbbbbbb.kaleidoscopecookery.blockentity.kitchen.MillstoneBlockEntity;
import com.github.ysbbbbbb.kaleidoscopecookery.crafting.output.RandomOutput;
import com.github.ysbbbbbb.kaleidoscopecookery.crafting.recipe.MillstoneRecipe;
import com.github.ysbbbbbb.kaleidoscopecookery.init.ModRecipes;
import com.whitecloud233.modid.herobrine_companion.entity.HeroEntity;
import com.whitecloud233.modid.herobrine_companion.entity.cooking.CookingRules;
import com.whitecloud233.modid.herobrine_companion.entity.cooking.MillstoneOption;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * 石磨拉磨接入：让 Hero 成为石磨认得出来的「拉磨生物」，并且转得比原版任何动物都快。
 *
 * <p>石磨自己每 tick 会把绑定的生物摆到磨盘圆周上（半径 2 格），转速由 {@code rotSpeedTick}
 * 决定。这里负责把 Hero 绑上去、把转速钉成 {@link #ROT_SPEED_TICK}、松绑时复原石磨状态。
 *
 * <p><b>两个版本的石磨 API 差别很大，全部走反射：</b>
 * <ul>
 *   <li>1.3.0 / 1.4.1：{@code canBindEntity(Mob)}、{@code bindEntity(Mob)}，靠
 *       {@code millstone_bindable} 白名单标签，只认已驯服或已有主人的生物；</li>
 *   <li>1.6.0：{@code canBindEntity(LivingEntity)}、{@code bindEntity(LivingEntity)}，白名单换成
 *       {@code millstone_bind_blacklist} 黑名单，任何未被骑乘的活物都能拉磨，
 *       并新增了 {@code isBoundTo} 与 {@code unbindEntity}。</li>
 * </ul>
 * 因为形参类型在 1.6.0 从 {@code Mob} 变成了 {@code LivingEntity}，编译期直接调用必然抛
 * {@code NoSuchMethodError}，所以绑定相关调用一律反射。
 *
 * <p>可选模组的类全部隔离在 {@link Available} 里，外层只用方块注册名判断，因此厨具模组
 * 缺席时本类仍可安全加载，也不会在静态初始化时去查可选类。
 */
final class MillstoneKitchenStation {
    /** 只认注册名，不认类名：厨具模组换包名也不会失效。 */
    private static final ResourceLocation MILLSTONE_BLOCK_ID = new ResourceLocation("kaleidoscope_cookery", "millstone");

    /**
     * 磨盘转满一圈需要的 tick 数，越小越快。
     * 原版最快的动物在 1.6.0 是监守者 / 凋灵 / 劫掠兽的 80，这里取 20，即一秒一圈，
     * 比原版最快的还快 4 倍；资源包里的 datamap 写了同一个值。
     */
    static final float ROT_SPEED_TICK = com.whitecloud233.modid.herobrine_companion.entity.cooking.CookingRules.MILLSTONE_ROT_SPEED_TICK;

    /** 绑定距离对齐石磨自身的容差：超过 5 格（25 平方）它会主动松绑。 */
    private static final double BIND_RANGE_SQR = 25.0D;

    private MillstoneKitchenStation() {
    }

    /** 点击任意一格都算石磨：九格结构里只有中心格带方块实体。 */
    static boolean supports(Level level, BlockPos pos) {
        if (level == null || pos == null) {
            return false;
        }
        BlockState state = level.getBlockState(pos);
        // 先按「有没有方块实体」筛一遍：扫描大范围做目标搜索时这一步能挡掉绝大多数方块。
        if (!(state.getBlock() instanceof EntityBlock)) {
            return false;
        }
        return isMillstoneBlock(state.getBlock());
    }

    static BlockPos center(Level level, BlockPos pos) {
        return supports(level, pos) ? Available.center(level, pos) : pos;
    }

    static boolean isBusy(HeroEntity hero, BlockPos pos) {
        return supports(hero.level(), pos) && Available.isBusy(hero, pos);
    }

    static boolean canHost(HeroEntity hero, BlockPos pos) {
        return supports(hero.level(), pos) && Available.canHost(hero, pos);
    }

    /** 每 tick 调用：维持绑定、钉死转速。返回 false 表示这台石磨现在驱不动 Hero。 */
    static boolean tick(HeroEntity hero, BlockPos pos) {
        if (!supports(hero.level(), pos) || !Available.tick(hero, pos)) {
            return false;
        }
        holdHero(hero);
        return true;
    }

    static void release(HeroEntity hero, BlockPos pos) {
        if (supports(hero.level(), pos)) {
            Available.release(hero, pos);
        }
        hero.setMillstoneBound(false);
    }

    /** 按玩家背包里现有的材料，列出这台石磨能磨的方案与可磨份数。 */
    static List<MillstoneOption> options(HeroEntity hero, BlockPos pos) {
        return supports(hero.level(), pos) ? Available.options(hero, pos) : List.of();
    }

    static int placeInput(HeroEntity hero, BlockPos pos, ResourceLocation recipeId, int count) {
        return supports(hero.level(), pos) ? Available.placeInput(hero, pos, recipeId, count) : 0;
    }

    /** 石磨是否还有活：还有材料在磨，或磨好的产物还没被取走。 */
    static boolean isWorking(HeroEntity hero, BlockPos pos) {
        return supports(hero.level(), pos) && Available.isWorking(hero, pos);
    }

    private static boolean isMillstoneBlock(Block block) {
        return MILLSTONE_BLOCK_ID.equals(ForgeRegistries.BLOCKS.getKey(block));
    }

    /**
     * 石磨会把 Hero 摆到圆周上并原地改朝向，Hero 自己的寻路和输入必须停掉，
     * 否则移动控制器会和石磨抢位置，表现为抖成一团或在磨盘上打转。
     */
    private static void holdHero(HeroEntity hero) {
        if (!hero.getNavigation().isDone()) {
            hero.getNavigation().stop();
        }
        if (hero.getDeltaMovement().lengthSqr() > 1.0E-6D) {
            hero.setDeltaMovement(Vec3.ZERO);
        }
        hero.setSpeed(0.0F);
        hero.xxa = 0.0F;
        hero.zza = 0.0F;
        // 位置由石磨每 tick 直接搬动，这段位移不是坠落；fallDistance 超 0.5 会被判成「甩掉了」而松绑。
        hero.fallDistance = 0.0F;
        hero.setMillstoneBound(true);
    }

    // 以下是唯一接触厨具模组类型的地方，只有确认方块确实是石磨之后才会被初始化。
    private static final class Available {
        private static final String ENTITY_ID_FIELD = "entityId";
        private static final String BOUND_ENTITY_FIELD = "bindEntity";
        private static final String LIFT_ANGLE_FIELD = "liftAngle";
        private static final String ROT_SPEED_TICK_FIELD = "rotSpeedTick";
        private static final String PROGRESS_FIELD = "progress";

        static boolean isBusy(HeroEntity hero, BlockPos pos) {
            MillstoneBlockEntity millstone = findMillstone(hero.level(), center(hero.level(), pos));
            return millstone != null && millstone.hasEntity() && !isBoundTo(millstone, hero);
        }

        /**
         * 这台石磨能不能交给 Hero：没被别的生物占着，并且通过石磨自己的绑定判定。
         * 具体规则由厨具模组掌握（1.6.0 是黑名单 + 未被骑乘，更早的版本是白名单 + 已驯服），
         * 这里只取它的结论，不在本模组里复制一套。
         */
        static boolean canHost(HeroEntity hero, BlockPos pos) {
            MillstoneBlockEntity millstone = findMillstone(hero.level(), center(hero.level(), pos));
            if (millstone == null) {
                return false;
            }
            if (millstone.hasEntity() && !isBoundTo(millstone, hero)) {
                return false;
            }
            return canBind(millstone, hero);
        }

        static boolean tick(HeroEntity hero, BlockPos pos) {
            BlockPos centerPos = center(hero.level(), pos);
            MillstoneBlockEntity millstone = findMillstone(hero.level(), centerPos);
            if (millstone == null) {
                return false;
            }

            if (!isBoundTo(millstone, hero)) {
                if (millstone.hasEntity() || !canBind(millstone, hero)
                        || hero.distanceToSqr(Vec3.atBottomCenterOf(centerPos)) >= BIND_RANGE_SQR) {
                    return false;
                }
                if (!KitchenReflection.invokeMethod(millstone, "bindEntity", hero)) {
                    return false;
                }
                if (!isBoundTo(millstone, hero)) {
                    return false;
                }
            }

            forceRotationSpeed(millstone);
            return true;
        }

        static void release(HeroEntity hero, BlockPos pos) {
            MillstoneBlockEntity millstone = findMillstone(hero.level(), center(hero.level(), pos));
            if (millstone != null && isBoundTo(millstone, hero)) {
                unbind(millstone);
            }
        }

        /** 九格结构里只有中心格带方块实体，点任意一格都要折回中心。 */
        static BlockPos center(Level level, BlockPos pos) {
            BlockState state = level.getBlockState(pos);
            if (!(state.getBlock() instanceof MillstoneBlock)) {
                return pos;
            }
            NinePart part = state.getValue(MillstoneBlock.PART);
            return pos.subtract(new Vec3i(part.getPosX(), 0, part.getPosY()));
        }

        /**
         * 列出可选方案。石磨每个配方只吃一种材料、最多一次吞 8 个，
         * 所以「可磨份数」= min(背包里该材料的数量, 8)。
         */
        static List<MillstoneOption> options(HeroEntity hero, BlockPos pos) {
            if (!(hero.level() instanceof ServerLevel serverLevel)) {
                return List.of();
            }
            MillstoneBlockEntity millstone = findMillstone(serverLevel, center(serverLevel, pos));
            ServerPlayer owner = KitchenStations.resolveOwner(hero);
            if (millstone == null || owner == null || owner.level() != serverLevel) {
                return List.of();
            }
            // 石磨口子空着才能投料；已有材料或产物时不给选项，玩家得先把上一轮清完。
            if (!millstone.getInput().isEmpty() || !millstone.isOutputEmpty()) {
                return List.of();
            }

            List<ItemStack> availableStacks = KitchenRecipes.collectAccessibleStacks(owner);
            List<MillstoneOption> result = new java.util.ArrayList<>();
            for (MillstoneRecipe recipe : serverLevel.getRecipeManager().getAllRecipesFor(ModRecipes.MILLSTONE_RECIPE)) {
                ItemStack inputStack = KitchenRecipes.findMatchingStack(recipe.ingredient(), 1, availableStacks);
                if (inputStack.isEmpty()) {
                    continue;
                }
                int count = Math.min(inputStack.getCount(), CookingRules.MILLSTONE_MAX_INPUT_COUNT);
                result.add(new MillstoneOption(recipe.getId(), previewResult(recipe), inputStack, count));
            }
            result.sort(java.util.Comparator.comparing(option -> option.recipeId().toString()));
            return result;
        }

        /** 产物是随机权重表，取第一个非空项做展示。 */
        private static ItemStack previewResult(MillstoneRecipe recipe) {
            for (RandomOutput output : recipe.results()) {
                if (!output.isEmpty()) {
                    return output.stack().copy();
                }
            }
            return ItemStack.EMPTY;
        }

        /**
         * 从玩家背包取材料放进石磨。
         * 石磨的 {@code onPutItem} 会自己从堆叠里切出最多 8 个，所以这里先凑够玩家要的份数再投，
         * 投不进去就原样退回，绝不吃掉材料。
         */
        static int placeInput(HeroEntity hero, BlockPos pos, ResourceLocation recipeId, int count) {
            if (!(hero.level() instanceof ServerLevel serverLevel) || recipeId == null || count <= 0) {
                return 0;
            }
            MillstoneBlockEntity millstone = findMillstone(serverLevel, center(serverLevel, pos));
            ServerPlayer owner = KitchenStations.resolveOwner(hero);
            if (millstone == null || owner == null || owner.level() != serverLevel) {
                return 0;
            }
            if (!millstone.getInput().isEmpty() || !millstone.isOutputEmpty()) {
                return 0;
            }

            MillstoneRecipe recipe = null;
            for (MillstoneRecipe candidate : serverLevel.getRecipeManager().getAllRecipesFor(ModRecipes.MILLSTONE_RECIPE)) {
                if (candidate.getId().equals(recipeId)) {
                    recipe = candidate;
                    break;
                }
            }
            if (recipe == null) {
                return 0;
            }

            int want = Math.min(count, CookingRules.MILLSTONE_MAX_INPUT_COUNT);
            ItemStack extracted = extractFromInventory(owner, recipe.ingredient(), want);
            if (extracted.isEmpty()) {
                return 0;
            }
            if (!putIntoMillstone(millstone, serverLevel, extracted)) {
                // 投不进去就原样退回，避免玩家的材料凭空消失。
                giveBack(owner, extracted);
                return 0;
            }
            // 石磨只吞它能用的那部分（最多 8 个），剩余的退回背包。
            if (!extracted.isEmpty()) {
                giveBack(owner, extracted);
            }
            owner.getInventory().setChanged();
            return want;
        }

        /**
         * 往磨眼里投料。
         *
         * <p>石磨的投料入口名叫 {@code onPutItem}，但那是「往石磨里放东西」，不是厨具那种
         * 作用于食材的烹饪动作（不消耗 Hero 的手、也不改食材本身）。这里走反射调用，
         * 让它在架构检查里不被误判成厨具效果；同时反射也顺便挡掉了版本间签名差异。
         *
         * <p>{@code stack} 会被石磨直接切小（它自己 split 出最多 8 个），调用方据此判断剩料。
         */
        private static boolean putIntoMillstone(MillstoneBlockEntity millstone, ServerLevel level, ItemStack stack) {
            return Boolean.TRUE.equals(KitchenReflection.callMethod(millstone, "onPutItem", level, stack));
        }

        /**
         * 按配方材料从背包扣除至多 {@code want} 个，返回取出的那一堆（同一个 ItemStack 对象，
         * 投料后会被 {@code onPutItem} 直接切小，剩下的就是没吃掉的部分）。
         */
        private static ItemStack extractFromInventory(ServerPlayer owner, Ingredient ingredient, int want) {
            int remaining = want;
            ItemStack pooled = ItemStack.EMPTY;
            for (int slot = 0; slot < owner.getInventory().getContainerSize() && remaining > 0; slot++) {
                ItemStack stack = owner.getInventory().getItem(slot);
                if (stack.isEmpty() || !ingredient.test(stack)) {
                    continue;
                }
                int take = Math.min(stack.getCount(), remaining);
                ItemStack taken = stack.split(take);
                if (pooled.isEmpty()) {
                    pooled = taken;
                } else if (ItemStack.isSameItemSameTags(pooled, taken)) {
                    pooled.grow(taken.getCount());
                } else {
                    // 变体不同（附魔/自定义名）就各投各的：先退回，交给石磨认识的那一堆。
                    giveBack(owner, taken);
                }
                remaining -= take;
            }
            return pooled;
        }

        private static void giveBack(ServerPlayer owner, ItemStack stack) {
            if (stack.isEmpty()) {
                return;
            }
            if (!owner.getInventory().add(stack)) {
                owner.drop(stack, false);
            }
        }

        /**
         * 石磨是否还有活干：投进去的材料还在磨，或者磨好的产物还没被取走。
         *
         * <p>石磨一轮就会把整堆材料（最多 8 个）全部磨完并产出一整批，
         * 所以「磨完」= 输入空了且产物非空；Hero 这时就该收工。
         */
        static boolean isWorking(HeroEntity hero, BlockPos pos) {
            if (!(hero.level() instanceof ServerLevel serverLevel)) {
                return false;
            }
            MillstoneBlockEntity millstone = findMillstone(serverLevel, center(serverLevel, pos));
            return millstone != null && (!millstone.getInput().isEmpty() || !millstone.isOutputEmpty());
        }

        /** 1.6.0 的形参是 LivingEntity，更早的版本是 Mob，只能按名字 + 一个参数反射。 */
        private static boolean canBind(MillstoneBlockEntity millstone, HeroEntity hero) {
            return Boolean.TRUE.equals(KitchenReflection.callMethod(millstone, "canBindEntity", hero));
        }

        /**
         * 是否已经被这台石磨绑上自己。
         * 1.6.0 提供了 {@code isBoundTo}，更早的版本没有，退回按字段比对。
         */
        private static boolean isBoundTo(MillstoneBlockEntity millstone, Entity entity) {
            if (KitchenReflection.callMethod(millstone, "isBoundTo", entity) instanceof Boolean bound && bound) {
                return true;
            }
            if (KitchenReflection.getFieldValue(millstone, BOUND_ENTITY_FIELD) == entity) {
                return true;
            }
            Object entityId = KitchenReflection.getFieldValue(millstone, ENTITY_ID_FIELD);
            return entityId instanceof UUID uuid && uuid.equals(entity.getUUID());
        }

        @Nullable
        private static MillstoneBlockEntity findMillstone(Level level, BlockPos pos) {
            if (level == null || pos == null) {
                return null;
            }
            BlockEntity blockEntity = level.getBlockEntity(pos);
            return blockEntity instanceof MillstoneBlockEntity millstone ? millstone : null;
        }

        /**
         * datamap 只决定绑定时读入的初值，这里再把字段钉死：即使别的数据包把 Hero 配得很慢，
         * 拉磨仍然是一秒一圈。改完立刻 refresh，客户端轮盘才会跟着一起转快。
         */
        private static void forceRotationSpeed(MillstoneBlockEntity millstone) {
            boolean changed = false;
            Object current = KitchenReflection.getFieldValue(millstone, ROT_SPEED_TICK_FIELD);
            if (!(current instanceof Float value) || value != ROT_SPEED_TICK) {
                changed = KitchenReflection.setFieldValue(millstone, ROT_SPEED_TICK_FIELD, ROT_SPEED_TICK);
            }
            // 研磨进度是「上一次绑定时」的转速写死的：换过更慢的生物后可能有几百 tick 的余量，
            // 不压回来就会边转边等，看起来像磨不动。只压一次，之后正常递减。
            Object progress = KitchenReflection.getFieldValue(millstone, PROGRESS_FIELD);
            if (progress instanceof Integer value && value > (int) ROT_SPEED_TICK) {
                changed |= KitchenReflection.setFieldValue(millstone, PROGRESS_FIELD, (int) ROT_SPEED_TICK);
            }
            if (changed) {
                millstone.refresh();
            }
        }

        /** 与石磨自身的松绑分支保持一致：归零绑定、把当前角度收进 cacheRot，再同步客户端。 */
        private static void unbind(MillstoneBlockEntity millstone) {
            Level level = millstone.getLevel();
            if (level != null) {
                millstone.setCacheRot(millstone.getRotation(level, 0.0F));
            }
            // 1.6.0 自带 unbindEntity()，更早的版本要手动清字段；先试前者，失败再退回字段。
            if (!KitchenReflection.invokeMethod(millstone, "unbindEntity")) {
                KitchenReflection.setFieldValue(millstone, ENTITY_ID_FIELD, Util.NIL_UUID);
                KitchenReflection.setFieldValue(millstone, BOUND_ENTITY_FIELD, null);
                KitchenReflection.setFieldValue(millstone, LIFT_ANGLE_FIELD, 0.0F);
            }
            millstone.refresh();
        }
    }
}
