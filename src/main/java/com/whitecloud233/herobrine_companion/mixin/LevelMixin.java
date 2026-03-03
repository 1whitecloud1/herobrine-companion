package com.whitecloud233.herobrine_companion.mixin;

import com.whitecloud233.herobrine_companion.entity.HeroEntity;
import com.whitecloud233.herobrine_companion.entity.ai.learning.HeroBrain;
import com.whitecloud233.herobrine_companion.event.WorldAnomalyEventHandler;
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

    @Inject(method = "setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z", at = @At("HEAD"))
    private void onSetBlock(BlockPos pos, BlockState newState, int flags, int maxUpdateDepth, CallbackInfoReturnable<Boolean> cir) {
        Level level = (Level) (Object) this;
        if (level.isClientSide) return;

        // 如果目标是将方块替换为空气（破坏方块）
        if (newState.isAir()) {
            // 1. 玩家主动挖掘的白名单拦截（结合你之前加的双格方块逻辑）
            if (WorldAnomalyEventHandler.PLAYER_BROKEN_BLOCKS.contains(pos)) {
                return;
            }

            BlockState oldState = level.getBlockState(pos);
            if (oldState.isAir() || oldState.is(Blocks.FIRE)) return;

            // 2. 【新增：附着物与合法物理掉落拦截】
            // 如果这个方块在当前的环境下已经无法存活（例如支撑它的方块刚刚被挖走），
            // 这属于原版世界的正常物理运转，不属于异常熵增，创世神无需修复。
            if (!oldState.canSurvive(level, pos)) {
                return;
            }

            // 3. 全局雷达扫描并触发神力重组
            for (HeroEntity hero : HeroBrain.ACTIVE_HEROES) {
                if (hero.level() == level && hero.distanceToSqr(pos.getX(), pos.getY(), pos.getZ()) < 4096) {
                    hero.getHeroBrain().rememberBrokenBlock(pos.immutable(), oldState);
                }
            }
        }
    }
}