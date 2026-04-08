package com.whitecloud233.modid.herobrine_companion.mixin;

import com.whitecloud233.modid.herobrine_companion.config.Config;
import com.whitecloud233.modid.herobrine_companion.entity.HeroEntity;
import com.whitecloud233.modid.herobrine_companion.entity.ai.learning.HeroBrain;
import com.whitecloud233.modid.herobrine_companion.event.WorldAnomalyEventHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Level.class)
public abstract class LevelMixin {

    // 1. 新增一个防重入标记，用于解决与其他模组的死循环兼容性问题
    private static final ThreadLocal<Boolean> IS_CHECKING_SURVIVAL = ThreadLocal.withInitial(() -> false);

    @Inject(method = "setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z", at = @At("HEAD"))
    private void onSetBlock(BlockPos pos, BlockState newState, int flags, int maxUpdateDepth, CallbackInfoReturnable<Boolean> cir) {
        Level level = (Level) (Object) this;
        if (level.isClientSide) return;

        // 2. 如果当前正在检测 canSurvive，说明是其他模组导致的嵌套调用，直接放行，打破死循环
        if (IS_CHECKING_SURVIVAL.get()) {
            return;
        }

        // 如果目标是将方块替换为空气（破坏方块）
        if (newState.isAir()) {
            if (WorldAnomalyEventHandler.PLAYER_BROKEN_BLOCKS.contains(pos)) {
                return;
            }

            BlockState oldState = level.getBlockState(pos);
            if (oldState.isAir() || oldState.is(Blocks.FIRE)) return;

            // 3. 在调用 canSurvive 之前上锁，调用结束后解锁
            IS_CHECKING_SURVIVAL.set(true);
            try {
                // 如果这个方块在当前的环境下已经无法存活，放行
                if (!oldState.canSurvive(level, pos)) {
                    return;
                }
            } finally {
                // 务必在 finally 块中释放标记，防止内存泄漏或永久锁死
                IS_CHECKING_SURVIVAL.set(false);
            }

            // 【新增】4. 配置判断：如果未开启方块修复，直接 return 结束，不再进行后续的遍历扫描
            if (!Config.heroBlockRestoration) {
                return;
            }

            // 5. 全局雷达扫描并触发神力重组
            for (HeroEntity hero : HeroBrain.ACTIVE_HEROES) {
                if (hero.level() == level && hero.distanceToSqr(pos.getX(), pos.getY(), pos.getZ()) < 4096) {
                    hero.getHeroBrain().rememberBrokenBlock(pos.immutable(), oldState);
                }
            }
        }
    }
}