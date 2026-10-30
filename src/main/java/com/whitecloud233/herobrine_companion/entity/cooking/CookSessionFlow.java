package com.whitecloud233.herobrine_companion.entity.cooking;

import com.whitecloud233.herobrine_companion.entity.HeroEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

import javax.annotation.Nullable;
import java.util.List;
import java.util.function.Function;

/**
 * 厨具会话的<b>通用流程</b>：轮换选中项、读取选项、按配方设定份数。
 *
 * <p><b>为什么要有它</b>：农夫乐事与森罗物语两个兼容层各自实现了这一整套流程，
 * 相似度 95–97%（{@code cycleCookSelection}、{@code getCookOptions}、
 * {@code selectCookOption}、{@code getCookOptionMaxRepeat} 四组），
 * 差异只有两处：
 * <ol>
 *   <li>森罗额外要求 {@code owner.level() == serverLevel}（跨维度时主人不在场，不能远程做菜）；</li>
 *   <li>会话状态的存放入口不同——森罗走 {@code KitchenSession} 静态方法，
 *       农夫乐事走后端持有的 {@link CookSessionState} 实例。</li>
 * </ol>
 * 这两处都用参数表达，<b>两侧的会话键前缀与状态存储仍完全隔离</b>
 * （见 {@link CookSessionState} 的类注释）。</p>
 *
 * <p><b>不合并的东西</b>：「哪个 block entity 算厨具」「怎么把厨具状态翻译成
 * {@link CookOption}」仍留在各自兼容层——那是真正对接不同模组 API 的部分。</p>
 */
public final class CookSessionFlow {

    private CookSessionFlow() {
    }

    /** 会话状态的读写入口；两个兼容层各自提供实现。 */
    public interface SessionAccess {
        void reset(HeroEntity hero, BlockPos pos);

        void select(HeroEntity hero, BlockPos pos, int index, ResourceLocation recipeId);

        int selectedIndex(HeroEntity hero, BlockPos pos, int optionCount);
    }

    /**
     * 轮换选中项（Hero 对着厨具按交互键时调用）。
     *
     * @param session     会话状态入口
     * @param owner       主人；由调用方解析，null 表示不在场
     * @param requireSameLevel 是否要求主人在同一维度（森罗为 true）
     * @param resolver    由 (level, owner, blockEntity) 解析出候选选项
     */
    public static Component cycle(HeroEntity hero, BlockPos pos, SessionAccess session,
                                  @Nullable ServerPlayer owner, boolean requireSameLevel,
                                  TriFunction<ServerLevel, ServerPlayer, BlockEntity, List<CookOption>> resolver) {
        if (!(hero.level() instanceof ServerLevel serverLevel)) {
            return noneMessage();
        }
        if (owner == null || (requireSameLevel && owner.level() != serverLevel)) {
            return noneMessage();
        }

        BlockEntity blockEntity = serverLevel.getBlockEntity(pos);
        List<CookOption> options = resolver.apply(serverLevel, owner, blockEntity);
        if (options.isEmpty()) {
            session.reset(hero, pos);
            return noneMessage();
        }

        if (options.size() == 1) {
            session.select(hero, pos, 0, options.get(0).recipeId());
            return Component.translatable("message.herobrine_companion.invite_cook_single",
                    options.get(0).result().getHoverName());
        }

        int nextIndex = (session.selectedIndex(hero, pos, options.size()) + 1) % options.size();
        session.select(hero, pos, nextIndex, options.get(nextIndex).recipeId());
        return Component.translatable("message.herobrine_companion.invite_cook_selected",
                options.get(nextIndex).result().getHoverName());
    }

    /**
     * 读取当前可做选项；主人不在场 / 维度不符时返回空列表。
     */
    public static List<CookOption> options(HeroEntity hero, BlockPos pos,
                                           @Nullable ServerPlayer owner, boolean requireSameLevel,
                                           TriFunction<ServerLevel, ServerPlayer, BlockEntity, List<CookOption>> resolver) {
        if (!(hero.level() instanceof ServerLevel serverLevel)) {
            return List.of();
        }
        if (owner == null || (requireSameLevel && owner.level() != serverLevel)) {
            return List.of();
        }
        return resolver.apply(serverLevel, owner, serverLevel.getBlockEntity(pos));
    }

    /**
     * 设定选中配方对应的份数；配方不在候选里时返回 {@code 0}。
     */
    public static int maxRepeat(List<CookOption> options, @Nullable ResourceLocation recipeId) {
        if (options == null || recipeId == null) {
            return 0;
        }
        for (CookOption option : options) {
            if (recipeId.equals(option.recipeId())) {
                return option.maxRepeatCount();
            }
        }
        return 0;
    }

    private static Component noneMessage() {
        return Component.translatable("message.herobrine_companion.invite_cook_none");
    }

    /** 三参数函数（JDK 没有内置）。 */
    @FunctionalInterface
    public interface TriFunction<A, B, C, R> {
        R apply(A a, B b, C c);
    }
}
