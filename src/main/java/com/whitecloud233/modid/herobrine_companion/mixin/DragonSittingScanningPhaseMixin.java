package com.whitecloud233.modid.herobrine_companion.mixin;

import com.whitecloud233.modid.herobrine_companion.config.Config;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.enderdragon.phases.AbstractDragonPhaseInstance;
import net.minecraft.world.entity.boss.enderdragon.phases.DragonSittingScanningPhase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(DragonSittingScanningPhase.class)
public abstract class DragonSittingScanningPhaseMixin extends AbstractDragonPhaseInstance {

    public DragonSittingScanningPhaseMixin(EnderDragon dragon) {
        super(dragon);
    }

    @Inject(method = "doServerTick", at = @At("HEAD"), cancellable = true)
    private void onServerTick(CallbackInfo ci) {
        // 1. 首先检查配置是否启用
        if (!Config.heroKingAuraEnabled) {
            return;
        }

        // 2. 检查我们在 Goal 里设置的 NBT 标记
        if (this.dragon.getPersistentData().getBoolean("HeroSubmission")) {
            // 如果处于臣服状态，直接取消原版的“寻找传送门”逻辑
            // 这样龙就会乖乖保持 SITTING 状态，且保留转头、呼吸等动画
            ci.cancel();
        }
    }
}