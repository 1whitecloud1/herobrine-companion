package com.whitecloud233.modid.herobrine_companion.mixin;

import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

public class HerobrineCompanionMixinPlugin implements IMixinConfigPlugin {
    @Override
    public void onLoad(String mixinPackage) {
        // Keep mixin bootstrap side-effect free. Any exception thrown here can make
        // the whole mixin config look unreadable to the launcher.
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    /**
     * 是否应用某个 mixin。
     *
     * <p>不再按「epicfight 是否在 classpath」做门控。原因：
     * <ol>
     *   <li>EpicFight / Avalon 已是 build.gradle 里的硬运行时依赖（enableEpicFightCompat
     *       机制已整体注释），门控失去意义；</li>
     *   <li>旧的 {@code Class.forName} 探测用的是本插件自身的 classloader，在 mixin 配置
     *       处理阶段（bootstrap，早于各 mod 容器构造）看不到第三方 mod 类，导致所有
     *       {@code *.mixin.epicfight.*} mixin 被静默跳过（2026-08 实测：三个 epicfight
     *       mixin 全部没被 applied，而 efn/invincible 等无插件配置的同类 Pseudo mixin 正常生效）；</li>
     *   <li>每个 epicfight mixin 已自带 {@code @Pseudo}，目标类缺失时 Mixin 会自行跳过，
     *       无需插件再探测一次。</li>
     * </ol></p>
     */
    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        return true;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }
}
