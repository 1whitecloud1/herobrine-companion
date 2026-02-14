package com.whitecloud233.herobrine_companion.mixin;

import com.whitecloud233.herobrine_companion.config.Config;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.enderdragon.phases.EnderDragonPhase;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EnderDragon.class)
public abstract class EnderDragonMixin extends Mob {

    // 用来记录龙原本是不是静音的
    @Unique
    private boolean herobrine_companion$wasSilent;

    protected EnderDragonMixin(EntityType<? extends Mob> entityType, Level level) {
        super(entityType, level);
    }

    // 1. 在 aiStep 执行之前：检查状态并“封嘴”
    @Inject(method = "aiStep", at = @At("HEAD"))
    private void silenceStart(CallbackInfo ci) {
        // 保存原始状态
        this.herobrine_companion$wasSilent = this.isSilent();

        // 0. 检查配置是否启用
        // 注意：这里直接读取静态字段，如果配置热重载可能需要确保 Config 类已更新
        if (!Config.heroKingAuraEnabled) {
            return;
        }

        EnderDragon dragon = (EnderDragon)(Object)this;

        // 1. 必须同时满足：处于坐下状态 AND 拥有臣服标记
        // 这样就不会误伤原版正在回血的龙
        boolean isSitting = dragon.getPhaseManager().getCurrentPhase().getPhase() == EnderDragonPhase.SITTING_SCANNING;
        boolean isSubmission = dragon.getPersistentData().getBoolean("HeroSubmission");

        if (isSitting && isSubmission) {
            // 临时开启静音！
            // 这样 aiStep 里的 playSound 逻辑执行时，会被系统自动拦截
            this.setSilent(true);
        }
    }

    // 2. 在 aiStep 执行之后：立刻“解封”
    @Inject(method = "aiStep", at = @At("TAIL"))
    private void silenceEnd(CallbackInfo ci) {
        // 如果我们刚才强制开启了静音，现在必须还原
        // 否则龙会变成永久哑巴，连受伤都不会叫
        if (!this.herobrine_companion$wasSilent) {
            this.setSilent(false);
        }
    }
}