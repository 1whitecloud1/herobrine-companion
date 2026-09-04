package com.whitecloud233.modid.herobrine_companion.client.render;

import software.bernie.geckolib.animatable.SingletonGeoAnimatable;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * 终末之诗的无状态 GeckoLib 动画对象。
 *
 * <p><b>兼容 GeckoLib 4.7 与 4.8+：</b>本类实现 {@link SingletonGeoAnimatable}
 * 这一 4.7 / 4.8+ 均存在的接口（4.8 起新增的 {@code StatelessGeoSingletonAnimatable}
 * 只是在其上叠加了 4.8 独有的 {@code StatelessAnimatable} 动画指令方法；就本模组
 * 用到的抽象方法 {@code registerControllers / getAnimatableInstanceCache /
 * getTick} 而言，两个版本的签名完全一致，已用 javap 逐版本比对确认）。
 * 因此同一份编译产物无需任何运行时区分，即可同时运行在 GeckoLib 4.7 与 4.8+ 上。</p>
 *
 * <p>注意：本类只存在于 GeckoLib 已加载时的渲染路径里（由
 * {@link GeckoLibPoemOfTheEndRenderer} 引用），物品本体 {@code PoemOfTheEndItem}
 * 并不实现任何 GeckoLib 接口，因此没装 GeckoLib 时服务端/客户端都不会加载
 * 这些类，可安全回退。</p>
 */
public final class PoemOfTheEndAnimatable implements SingletonGeoAnimatable {

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        // 静态 3D 模型：设置一段最小空闲动画（与 Celestisynth 的 weapon.none 一致），
        // GeckoLib 4.8 的动画处理器在没有任何动画时会产出空模型导致不可见。
        controllers.add(new AnimationController<>(this, "poem_of_the_end_controller", 0, state ->
                state.setAndContinue(RawAnimation.begin().thenLoop("animation.poem_of_the_end.none"))));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    @Override
    public double getTick(Object object) {
        return 0D;
    }
}