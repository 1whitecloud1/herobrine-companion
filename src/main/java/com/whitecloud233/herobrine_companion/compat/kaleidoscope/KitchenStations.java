package com.whitecloud233.herobrine_companion.compat.kaleidoscope;

import com.github.ysbbbbbb.kaleidoscopecookery.api.blockentity.IChoppingBoard;
import com.whitecloud233.herobrine_companion.entity.cooking.CookSessionFlow;
import com.github.ysbbbbbb.kaleidoscopecookery.api.blockentity.ISteamer;
import com.github.ysbbbbbb.kaleidoscopecookery.blockentity.kitchen.ChoppingBoardBlockEntity;
import com.github.ysbbbbbb.kaleidoscopecookery.blockentity.kitchen.PotBlockEntity;
import com.github.ysbbbbbb.kaleidoscopecookery.blockentity.kitchen.StockpotBlockEntity;
import com.github.ysbbbbbb.kaleidoscopecookery.init.ModItems;
import com.whitecloud233.herobrine_companion.util.DebugLog;
import com.whitecloud233.herobrine_companion.compat.kaleidoscope.KitchenRecipes.BackendCookOption;
import com.whitecloud233.herobrine_companion.entity.HeroEntity;
import com.whitecloud233.herobrine_companion.entity.cooking.CookOption;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

/** Routes loaded Cookery stations and translates recipe choices into the public cooking API. */
final class KitchenStations {
    private KitchenStations() {
    }

    static boolean isCookwareStation(Level level, BlockPos pos) {
        BlockEntity blockEntity = level.getBlockEntity(pos);
        return blockEntity instanceof PotBlockEntity
                || blockEntity instanceof StockpotBlockEntity
                || TeapotKitchenStation.isTeapotBlockEntity(blockEntity)
                || blockEntity instanceof IChoppingBoard
                || blockEntity instanceof ISteamer;
    }

    static ItemStack getCookMainHandDisplay(Level level, BlockPos pos) {
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity instanceof PotBlockEntity) {
            return ModItems.KITCHEN_SHOVEL.get().getDefaultInstance();
        }
        if (blockEntity instanceof StockpotBlockEntity) {
            return new ItemStack(Items.WOODEN_SHOVEL);
        }
        if (TeapotKitchenStation.isTeapotBlockEntity(blockEntity)) {
            return new ItemStack(Items.WOODEN_SHOVEL);
        }
        if (blockEntity instanceof IChoppingBoard) {
            return ModItems.IRON_KITCHEN_KNIFE.get().getDefaultInstance();
        }
        return ItemStack.EMPTY;
    }

    static ItemStack getCookOffhandDisplay(Level level, BlockPos pos) {
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (TeapotKitchenStation.isTeapotBlockEntity(blockEntity)) {
            return TeapotKitchenStation.getCookOffhandDisplay();
        }
        return ItemStack.EMPTY;
    }

    static boolean tickCookware(HeroEntity hero, BlockPos pos) {
        if (!(hero.level() instanceof ServerLevel serverLevel)) {
            return false;
        }

        ServerPlayer owner = resolveOwner(hero);
        if (owner == null || owner.level() != serverLevel) {
            DebugLog.info(KitchenDiagnostics.LOGGER, "[CookDiag] kaleidoscope.tick :: OWNER_INVALID ownerNull={} sameLevel={}",
                    owner == null, owner != null && owner.level() == serverLevel);
            return false;
        }

        BlockEntity blockEntity = serverLevel.getBlockEntity(pos);
        KitchenDiagnostics.tick(pos, blockEntity);
        return KitchenActorInteraction.perform(hero, owner, () -> {
            if (blockEntity instanceof PotBlockEntity pot) {
                return PotKitchenStation.tickPot(hero, serverLevel, owner, pot);
            }
            if (blockEntity instanceof StockpotBlockEntity stockpot) {
                return StockpotKitchenStation.tickStockpot(hero, serverLevel, owner, stockpot);
            }
            if (TeapotKitchenStation.isTeapotBlockEntity(blockEntity)) {
                return TeapotKitchenStation.tickCookware(hero, serverLevel, owner, blockEntity);
            }
            if (blockEntity instanceof ChoppingBoardBlockEntity choppingBoard) {
                return ChoppingBoardKitchenStation.tickChoppingBoard(hero, serverLevel, owner, choppingBoard);
            }
            if (blockEntity instanceof ISteamer steamer) {
                return SteamerKitchenStation.tickSteamer(hero, serverLevel, owner, steamer);
            }
            return false;
        });
    }

    /** 会话状态入口：森罗侧走 KitchenSession 的静态方法。 */
    private static final CookSessionFlow.SessionAccess SESSION = new CookSessionFlow.SessionAccess() {
        @Override
        public void reset(HeroEntity hero, BlockPos pos) {
            KitchenSession.resetCookSelection(hero, pos);
        }

        @Override
        public void select(HeroEntity hero, BlockPos pos, int index, ResourceLocation recipeId) {
            KitchenSession.setCookSelection(hero, pos, index, recipeId);
        }

        @Override
        public int selectedIndex(HeroEntity hero, BlockPos pos, int optionCount) {
            return KitchenSession.getCookSelectionIndex(hero, pos, optionCount);
        }
    };

    /** 轮换选中项：通用流程见 {@link CookSessionFlow}；森罗要求主人在同一维度。 */
    static Component cycleCookSelection(HeroEntity hero, BlockPos pos) {
        return CookSessionFlow.cycle(hero, pos, SESSION, resolveOwner(hero), true,
                (level, owner, blockEntity) -> mapOptions(getCookOptions(level, owner, blockEntity)));
    }

    /** 后端选项 → 通用选项的包装（两个兼容层各自的结构性适配）。 */
    private static List<CookOption> mapOptions(List<BackendCookOption> options) {
        if (options.isEmpty()) {
            return List.of();
        }
        List<CookOption> result = new ArrayList<>(options.size());
        for (BackendCookOption option : options) {
            result.add(new CookOption(option.recipeId(), option.result().copy(), option.maxRepeatCount()));
        }
        return result;
    }

    static List<CookOption> getCookOptions(HeroEntity hero, BlockPos pos) {
        if (!(hero.level() instanceof ServerLevel serverLevel)) {
            return List.of();
        }

        ServerPlayer owner = resolveOwner(hero);
        if (owner == null || owner.level() != serverLevel) {
            return List.of();
        }

        BlockEntity blockEntity = serverLevel.getBlockEntity(pos);
        List<BackendCookOption> options = getCookOptions(serverLevel, owner, blockEntity);
        if (options.isEmpty()) {
            return List.of();
        }

        List<CookOption> result = new ArrayList<>(options.size());
        for (BackendCookOption option : options) {
            result.add(new CookOption(option.recipeId(), option.result().copy(), option.maxRepeatCount()));
        }
        return result;
    }

    static boolean selectCookOption(HeroEntity hero, BlockPos pos, ResourceLocation recipeId) {
        if (!(hero.level() instanceof ServerLevel serverLevel)) {
            return false;
        }

        ServerPlayer owner = resolveOwner(hero);
        if (owner == null || owner.level() != serverLevel) {
            return false;
        }

        BlockEntity blockEntity = serverLevel.getBlockEntity(pos);
        List<BackendCookOption> options = getCookOptions(serverLevel, owner, blockEntity);
        for (int i = 0; i < options.size(); i++) {
            if (options.get(i).recipeId().equals(recipeId)) {
                KitchenSession.setCookSelection(hero, pos, i, recipeId);
                return true;
            }
        }
        return false;
    }

    static int getCookOptionMaxRepeat(HeroEntity hero, BlockPos pos, ResourceLocation recipeId) {
        if (!(hero.level() instanceof ServerLevel serverLevel)) {
            return 0;
        }

        ServerPlayer owner = resolveOwner(hero);
        if (owner == null || owner.level() != serverLevel) {
            return 0;
        }

        BlockEntity blockEntity = serverLevel.getBlockEntity(pos);
        List<BackendCookOption> options = getCookOptions(serverLevel, owner, blockEntity);
        for (BackendCookOption option : options) {
            if (option.recipeId().equals(recipeId)) {
                return option.maxRepeatCount();
            }
        }
        return 0;
    }

    @Nullable
    static ServerPlayer resolveOwner(HeroEntity hero) {
        if (hero.getOwnerUUID() == null) {
            return null;
        }
        return hero.level() instanceof ServerLevel serverLevel
                ? serverLevel.getServer().getPlayerList().getPlayer(hero.getOwnerUUID())
                : null;
    }

    static List<BackendCookOption> getCookOptions(ServerLevel level, ServerPlayer owner, @Nullable BlockEntity blockEntity) {
        if (blockEntity instanceof PotBlockEntity pot) {
            return PotKitchenStation.mapPotOptions(PotKitchenStation.collectPotPlans(level, owner, pot));
        }
        if (blockEntity instanceof StockpotBlockEntity stockpot) {
            return StockpotKitchenStation.mapStockpotOptions(StockpotKitchenStation.collectStockpotPlans(level, owner, stockpot));
        }
        if (TeapotKitchenStation.isTeapotBlockEntity(blockEntity)) {
            return TeapotKitchenStation.getCookOptions(level, owner, blockEntity);
        }
        if (blockEntity instanceof ChoppingBoardBlockEntity choppingBoard) {
            return ChoppingBoardKitchenStation.mapChoppingBoardOptions(ChoppingBoardKitchenStation.collectChoppingBoardPlans(level, owner, choppingBoard));
        }
        if (blockEntity instanceof ISteamer steamer) {
            return SteamerKitchenStation.mapSteamerOptions(SteamerKitchenStation.collectSteamerPlans(level, owner, steamer));
        }
        return List.of();
    }
}
