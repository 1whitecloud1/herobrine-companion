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

        if (newState.isAir()) {
            if (WorldAnomalyEventHandler.PLAYER_BROKEN_BLOCKS.contains(pos)) {
                return; 
            }

            BlockState oldState = level.getBlockState(pos);
            if (oldState.isAir() || oldState.is(Blocks.FIRE)) return;

            // 【终极性能优化】：不再扫描区块实体！
            // 直接遍历全局注册的 Herobrine（通常只有 1 个），然后计算纯数学距离。
            // 将爆炸时的 O(N) 几何级卡顿，瞬间化为 O(1) 的极速响应。
            for (HeroEntity hero : HeroBrain.ACTIVE_HEROES) {
                // 确保他们在同一个维度，并且距离在 64 格之内 (64的平方是4096)
                if (hero.level() == level && hero.distanceToSqr(pos.getX(), pos.getY(), pos.getZ()) < 4096) {
                    hero.getHeroBrain().rememberBrokenBlock(pos.immutable(), oldState);
                }
            }
        }
    }
}