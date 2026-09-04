package com.whitecloud233.herobrine_companion.mixin;

import com.whitecloud233.herobrine_companion.entity.awakened.AwakenedMobAccessor;
import com.whitecloud233.herobrine_companion.entity.awakened.AwakenedMobWorldData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 觉醒怪物世界名额注销钩子。
 *
 * <p>必须直接混入 {@link Entity} 而不是 {@link net.minecraft.world.entity.Mob}：
 * {@code remove(RemovalReason)} 声明在 Entity 上，若从 Mob 混入注入父类方法，
 * refmap 会把它映射到 Mob 名下，运行期找不到目标直接导致加载崩溃
 * （InvalidInjectionException: could not find any targets ... in Mob）。
 *
 * <p>死亡、消失（discard）、跨维度离开时注销世界注册表中的名额；
 * 区块卸载两种原因保留名额（觉醒怪仍存在于未加载区块中）。
 */
@Mixin(Entity.class)
public abstract class AwakenedEntityRemovalMixin {
    @Inject(method = "remove(Lnet/minecraft/world/entity/Entity$RemovalReason;)V", at = @At("TAIL"))
    private void herobrineCompanion$onAwakenedMobRemoved(Entity.RemovalReason reason, CallbackInfo ci) {
        Entity self = (Entity) (Object) this;
        if (reason == Entity.RemovalReason.UNLOADED_TO_CHUNK || reason == Entity.RemovalReason.UNLOADED_WITH_PLAYER) {
            return;
        }
        if (!(self instanceof AwakenedMobAccessor accessor) || !accessor.herobrineCompanion$isAwakenedMob()) {
            return;
        }
        if (self.level() instanceof ServerLevel serverLevel) {
            AwakenedMobWorldData.get(serverLevel).unregister(self.getUUID());
        }
    }
}