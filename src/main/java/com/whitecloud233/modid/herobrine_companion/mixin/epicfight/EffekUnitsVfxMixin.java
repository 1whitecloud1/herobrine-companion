package com.whitecloud233.modid.herobrine_companion.mixin.epicfight;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.loading.FMLEnvironment;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * EFN(史诗战斗：夜幕) 3.4.0 服务端崩溃守卫。
 *
 * <p>根因：EFN 把 {@code AddTriggerEvent}（位于 {@code com.hm.efn.client.events}）注册到
 * <b>公共</b>事件总线上，服务端也会触发。其 {@code onEffectAdded} 在方法开头无条件调用
 * {@code EffekUnits.VFXENABLE()}，而该方法读取的是 <b>客户端专属配置</b>
 * {@code EFNClientConfig.VFX_PLUS}。专用服务器从不加载客户端配置，dev 环境下
 * {@code ForgeConfigSpec.ConfigValue.get()} 直接抛
 * {@code IllegalStateException: Cannot get config value before config is loaded}，
 * EventBus 把该异常重新抛出 → 任何 {@code addEffect}（金苹果、信标、药水等）都会让
 * 服务端整条玩家 tick 崩溃。</p>
 *
 * <p>守卫：专用服务器上本就没有客户端视觉效果，直接把 {@code VFXENABLE} 视为关闭
 * （返回 false），EFN 各触发逻辑随即提前返回，不再触碰客户端配置。客户端行为不受影响。</p>
 *
 * <p>实测三版缓存(8003764/8082392/8366962=3.4.0)均有此缺陷；3.2.6 尚无 {@code AddTriggerEvent}。</p>
 */
@Pseudo
@Mixin(targets = "com.hm.efn.util.EffekUnits", remap = false)
public abstract class EffekUnitsVfxMixin {

    @Inject(method = "VFXENABLE", at = @At("HEAD"), cancellable = true, require = 0)
    private static void herobrineCompanion$guardServerVfx(CallbackInfoReturnable<Boolean> cir) {
        if (FMLEnvironment.dist != Dist.CLIENT) {
            cir.setReturnValue(false);
        }
    }
}
