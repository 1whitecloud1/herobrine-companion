package com.whitecloud233.herobrine_companion.client.render;

import software.bernie.geckolib.animatable.SingletonGeoAnimatable;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * 终末之诗的 GeckoLib 动画对象。
 *
 * <p>物品本体 {@code PoemOfTheEndItem} 故意<b>不</b>实现任何 GeckoLib 接口，
 * 否则没装 GeckoLib 时物品类加载即 {@code NoClassDefFoundError}（物品注册在两端都发生，
 * 服务端也会崩）。动画状态因此挪到这个独立的单例里，它只被
 * {@link GeckoLibPoemOfTheEndRenderer} 引用，而后者只在 {@code geckolib} 已加载时
 * 才会被 {@link PoemOfTheEndGeoCompat} 触达。</p>
 */
public final class PoemOfTheEndAnimatable implements SingletonGeoAnimatable {

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        // 静态 3D 模型：仍需一段循环空闲动画。GeckoLib 的动画处理器在没有任何
        // 动画时会产出空姿态，模型会不可见（与 Celestisynth 的 weapon.none 同理）。
        controllers.add(new AnimationController<>(this, "poem_of_the_end_controller", 0, state ->
                state.setAndContinue(RawAnimation.begin()
                        .thenLoop("animation.herobrine_companion.poem_of_the_end.idle"))));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    @Override
    public double getTick(Object object) {
        // 静态模型，不需要随时间推进
        return 0D;
    }
}
