package com.whitecloud233.herobrine_companion.entity.cooking;

import com.whitecloud233.herobrine_companion.entity.HeroEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.List;

/** 只暴露本模组需要的厨具能力，外部模组类型和反射细节不得穿过接口。 */
public interface CookingBackend {
    String id();
    boolean supports(Level level, BlockPos pos);
    boolean tick(HeroEntity hero, BlockPos pos);
    ItemStack mainHandDisplay(HeroEntity hero);
    ItemStack offHandDisplay(HeroEntity hero);
    void resetSelection(HeroEntity hero, BlockPos pos);
    List<CookOption> options(HeroEntity hero, BlockPos pos);
    boolean select(HeroEntity hero, BlockPos pos, ResourceLocation recipeId);
    int maxRepeat(HeroEntity hero, BlockPos pos, ResourceLocation recipeId);
    Component cycleSelection(HeroEntity hero, BlockPos pos);
    void setRepeatCount(HeroEntity hero, BlockPos pos, int count);
    void beginChunkTicket(HeroEntity hero, BlockPos pos);
    void endChunkTicket(HeroEntity hero);
    List<CookOption> autonomousOptions(Level level, BlockPos pos);

    default CompoundTag beginAutonomousDisplay(Level level, BlockPos pos, CookOption choice) { return new CompoundTag(); }
    default void restoreAutonomousDisplay(Level level, BlockPos pos, CompoundTag snapshot) { }

    /** 石磨不是锅具，不参与做菜路由，需要单独的能力位。 */
    default boolean supportsMillstone(Level level, BlockPos pos) { return false; }

    /** 返回石磨占用的中心格；非石磨原样返回，调用方无需感知九格结构。 */
    default BlockPos millstoneCenter(Level level, BlockPos pos) { return pos; }

    /** 每 tick 维持「Hero 被这台石磨绑着」，返回 false 表示这台石磨现在驱不动它。 */
    default boolean tickMillstone(HeroEntity hero, BlockPos pos) { return false; }

    /** 是否已被别的生物占用；已绑自己视为空闲。 */
    default boolean isMillstoneBusy(HeroEntity hero, BlockPos pos) { return false; }

    /** 这台石磨当前是否愿意接受该 Hero（类型在标签里、未被骑乘等由厨具模组判定）。 */
    default boolean canHostMillstone(HeroEntity hero, BlockPos pos) { return false; }

    /** 石磨可选的研磨方案：按玩家背包里现有材料算出来的「能磨什么、最多磨几份」。 */
    default List<MillstoneOption> millstoneOptions(HeroEntity hero, BlockPos pos) { return List.of(); }

    /** 把选好的材料从玩家背包放进石磨。返回实际放上去的数量，0 表示失败。 */
    default int placeMillstoneInput(HeroEntity hero, BlockPos pos, ResourceLocation recipeId, int count) { return 0; }

    /** 石磨是否还有活：材料还在磨，或产物还没被取走。 */
    default boolean isMillstoneWorking(HeroEntity hero, BlockPos pos) { return false; }

    default void releaseMillstone(HeroEntity hero, BlockPos pos) { }

    /** 旧存档的后端私有快照由各适配器识别，业务层不维护模组专属键名。 */
    default boolean restoreLegacyDisplay(Level level, BlockPos pos, CompoundTag savedState) { return false; }
}
