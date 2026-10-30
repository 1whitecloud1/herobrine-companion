package com.whitecloud233.modid.herobrine_companion.entity.cooking;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import com.whitecloud233.modid.herobrine_companion.entity.HeroEntity;

import javax.annotation.Nullable;
import java.util.List;
import java.util.function.Function;

/**
 * 厨具会话状态：选中项、重复份数、区块票据。
 *
 * <p><b>键前缀由构造参数决定，不在本类内固定。</b>森罗物语与农夫乐事原本各自实现了
 * 一份逐字符相同的会话状态逻辑，但使用了不同的 NBT 键前缀
 * （{@code HeroCookSelectionPos} 与 {@code HeroFdCookSelectionPos}）。
 * 这说明状态本来就是隔离的：方法体相同纯属复制，没有任何数据互通需求。</p>
 *
 * <p><b>为什么前缀必须是参数而不能统一：</b>如果两个模组共用同一批键，
 * 玩家从森罗的锅切到农夫乐事的锅时，旧选中项会残留到新锅上，
 * 表现为「刚放下农夫乐事的锅，Hero 却在按森罗的配方做菜」。
 * 前缀隔离是功能正确性的一部分，不是风格问题。</p>
 *
 * <p><b>新增厨具模组时：</b>为它分配一个未使用的前缀并在此类的调用方构造实例。
 * 不要复用已有前缀。</p>
 */
public final class CookSessionState {

    /** 区块票据的维护距离，与厨具模组本身的票据无关，只保证交互期间区块不卸载。 */
    private static final int TICKET_DISTANCE = 1;

    private final String keyPrefix;

    /**
     * @param keyPrefix NBT 键前缀，例如 {@link CookingRules#FD_STATE_PREFIX}。
     *                  必须与其他兼容层不同，且一旦发布不可更改（会丢旧存档状态）。
     */
    public CookSessionState(String keyPrefix) {
        if (keyPrefix == null || keyPrefix.isBlank()) {
            throw new IllegalArgumentException("Cook session key prefix must not be blank");
        }
        this.keyPrefix = keyPrefix;
    }

    private String selectionPosKey() { return keyPrefix + "CookSelectionPos"; }
    private String selectionIndexKey() { return keyPrefix + "CookSelectionIndex"; }
    private String selectionRecipeKey() { return keyPrefix + "CookSelectionRecipe"; }
    private String repeatPosKey() { return keyPrefix + "CookRepeatPos"; }
    private String repeatRemainingKey() { return keyPrefix + "CookRepeatRemaining"; }
    private String ticketLevelKey() { return keyPrefix + "CookTicketLevel"; }
    private String ticketChunkKey() { return keyPrefix + "CookTicketChunk"; }

    // ==========================================================================
    // 区块票据
    // ==========================================================================

    /** 钉住厨具所在区块，避免交互期间卸载。重复调用同一区块无副作用。 */
    public void beginChunkTicket(HeroEntity hero, BlockPos pos) {
        if (hero == null || pos == null || hero.level().isClientSide()
                || !(hero.level() instanceof ServerLevel serverLevel)) {
            return;
        }

        ChunkPos targetChunk = new ChunkPos(pos);
        String targetLevelId = serverLevel.dimension().location().toString();
        CompoundTag data = hero.getPersistentData();
        String currentLevelId = data.getString(ticketLevelKey());
        long currentChunkLong = data.getLong(ticketChunkKey());
        if (targetLevelId.equals(currentLevelId) && currentChunkLong == targetChunk.toLong()) {
            return;
        }

        endChunkTicket(hero);
        serverLevel.getChunkSource().addRegionTicket(TicketType.UNKNOWN, targetChunk, TICKET_DISTANCE, targetChunk);
        data.putString(ticketLevelKey(), targetLevelId);
        data.putLong(ticketChunkKey(), targetChunk.toLong());
    }

    /** 释放区块票据。跨维度也要能释放，因此按记录的维度找回对应 ServerLevel。 */
    public void endChunkTicket(HeroEntity hero) {
        if (hero == null || hero.level().isClientSide) {
            return;
        }

        CompoundTag data = hero.getPersistentData();
        String levelId = data.getString(ticketLevelKey());
        if (!levelId.isBlank() && hero.getServer() != null) {
            ResourceLocation dimensionId = ResourceLocation.tryParse(levelId);
            if (dimensionId != null) {
                ServerLevel ticketLevel = hero.getServer().getLevel(
                        ResourceKey.create(Registries.DIMENSION, dimensionId));
                long chunkLong = data.getLong(ticketChunkKey());
                if (ticketLevel != null) {
                    ChunkPos chunkPos = new ChunkPos(chunkLong);
                    ticketLevel.getChunkSource().removeRegionTicket(TicketType.UNKNOWN, chunkPos, TICKET_DISTANCE, chunkPos);
                }
            }
        }

        data.remove(ticketLevelKey());
        data.remove(ticketChunkKey());
    }

    // ==========================================================================
    // 选中项
    // ==========================================================================

    /** 重置选中状态并开启新的重复计数。 */
    public void resetSelection(HeroEntity hero, BlockPos pos) {
        if (hero == null || pos == null) {
            return;
        }
        CompoundTag data = hero.getPersistentData();
        data.putLong(selectionPosKey(), pos.asLong());
        data.putInt(selectionIndexKey(), 0);
        data.remove(selectionRecipeKey());
        data.putLong(repeatPosKey(), pos.asLong());
        data.putInt(repeatRemainingKey(), CookingRules.MIN_COOK_REPEAT_COUNT);
    }

    /** 记录当前选中的配方；{@code recipeId} 为 {@code null} 表示按序号选择（无固定配方）。 */
    public void setSelection(HeroEntity hero, BlockPos pos, int index, @Nullable ResourceLocation recipeId) {
        if (hero == null || pos == null) {
            return;
        }
        CompoundTag data = hero.getPersistentData();
        data.putLong(selectionPosKey(), pos.asLong());
        data.putInt(selectionIndexKey(), Math.max(0, index));
        if (recipeId == null) {
            data.remove(selectionRecipeKey());
        } else {
            data.putString(selectionRecipeKey(), recipeId.toString());
        }
    }

    /** 当前选中的配方 ID；未选或位置不匹配时返回 {@code null}。 */
    @Nullable
    public ResourceLocation selectedRecipe(HeroEntity hero, BlockPos pos) {
        if (hero == null || pos == null) {
            return null;
        }
        CompoundTag data = hero.getPersistentData();
        if (data.getLong(selectionPosKey()) != pos.asLong()) {
            return null;
        }
        String recipeId = data.getString(selectionRecipeKey());
        return recipeId.isBlank() ? null : ResourceLocation.tryParse(recipeId);
    }

    /** 按序号取当前选中项，越界时取模回绕。位置变化会先重置。 */
    public int selectionIndex(HeroEntity hero, BlockPos pos, int optionCount) {
        if (hero == null || pos == null || optionCount <= 0) {
            return 0;
        }

        CompoundTag data = hero.getPersistentData();
        if (data.getLong(selectionPosKey()) != pos.asLong()) {
            resetSelection(hero, pos);
            return 0;
        }

        int index = data.getInt(selectionIndexKey());
        if (index < 0 || index >= optionCount) {
            index = Math.floorMod(index, optionCount);
            data.putInt(selectionIndexKey(), index);
        }
        return index;
    }

    /**
     * 解析「当前选中的是第几项」：优先按配方 ID 精确匹配，否则退回序号。
     *
     * @param idOf 从选项取配方 ID；用 {@link Function} 而非固定接口，
     *             避免两个兼容层为了共用此方法而互相依赖对方的候选类型
     * @return 选项下标；未选中或选项为空时返回 -1
     */
    public <T> int resolveSelectedIndex(HeroEntity hero, BlockPos pos, List<T> options,
                                       Function<T, ResourceLocation> idOf) {
        if (options.isEmpty()) {
            return -1;
        }

        ResourceLocation selectedRecipeId = selectedRecipe(hero, pos);
        if (selectedRecipeId != null) {
            for (int i = 0; i < options.size(); i++) {
                if (selectedRecipeId.equals(idOf.apply(options.get(i)))) {
                    return i;
                }
            }
            return -1;
        }

        return selectionIndex(hero, pos, options.size());
    }

    // ==========================================================================
    // 重复份数
    // ==========================================================================

    public void setRepeatCount(HeroEntity hero, BlockPos pos, int count) {
        if (hero == null || pos == null) {
            return;
        }
        CompoundTag data = hero.getPersistentData();
        data.putLong(repeatPosKey(), pos.asLong());
        data.putInt(repeatRemainingKey(), CookingRules.clampRepeatCount(count));
    }

    public int repeatCount(HeroEntity hero, BlockPos pos) {
        if (hero == null || pos == null) {
            return CookingRules.MIN_COOK_REPEAT_COUNT;
        }
        CompoundTag data = hero.getPersistentData();
        if (data.getLong(repeatPosKey()) != pos.asLong()) {
            setRepeatCount(hero, pos, CookingRules.MIN_COOK_REPEAT_COUNT);
            return CookingRules.MIN_COOK_REPEAT_COUNT;
        }
        int remaining = CookingRules.clampRepeatCount(data.getInt(repeatRemainingKey()));
        data.putInt(repeatRemainingKey(), remaining);
        return remaining;
    }

    /**
     * 消耗一份重复计数。
     *
     * @return {@code true} 表示计数已用尽，调用方应当收工
     */
    public boolean consumeRepeat(HeroEntity hero, BlockPos pos) {
        if (hero == null || pos == null) {
            return false;
        }
        int remaining = repeatCount(hero, pos);
        if (remaining <= CookingRules.MIN_COOK_REPEAT_COUNT) {
            return true;
        }
        setRepeatCount(hero, pos, remaining - 1);
        return false;
    }
}
