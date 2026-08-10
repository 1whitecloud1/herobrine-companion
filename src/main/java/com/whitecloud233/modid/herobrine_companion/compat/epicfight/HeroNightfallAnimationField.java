package com.whitecloud233.modid.herobrine_companion.compat.epicfight;

import yesman.epicfight.api.animation.AnimationManager;
import yesman.epicfight.api.animation.types.StaticAnimation;
import yesman.epicfight.api.asset.AssetAccessor;

/**
 * 动画字段引用：延迟解析 EFN 动画常量（反射读静态字段），并对比当前播放动画。
 */
record HeroNightfallAnimationField(String owner, String fieldName) {
    AnimationManager.AnimationAccessor<? extends StaticAnimation> resolve() {
        return HeroNightfallAnimationLookup.resolve(this.owner, this.fieldName);
    }

    AnimationManager.AnimationAccessor<? extends StaticAnimation> resolveOriginal() {
        return HeroNightfallAnimationLookup.resolveOriginal(this.owner, this.fieldName);
    }

    boolean matches(AssetAccessor<? extends StaticAnimation> currentAnimation) {
        AnimationManager.AnimationAccessor<? extends StaticAnimation> resolved = this.resolve();
        if (resolved != null && resolved.equals(currentAnimation)) {
            return true;
        }
        AnimationManager.AnimationAccessor<? extends StaticAnimation> original = this.resolveOriginal();
        return original != null && original.equals(currentAnimation);
    }
}
