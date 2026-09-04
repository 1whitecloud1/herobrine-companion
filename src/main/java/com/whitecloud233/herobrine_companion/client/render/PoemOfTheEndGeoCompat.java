package com.whitecloud233.herobrine_companion.client.render;

import com.mojang.logging.LogUtils;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.neoforged.fml.ModList;
import org.slf4j.Logger;

/**
 * 终末之诗渲染器的工厂：只在安装了 GeckoLib 时提供 3D 渲染器。
 *
 * <p>本类<b>不引用任何 GeckoLib 类</b>，因此可被客户端安全加载；
 * 只有 {@code geckolib} 已加载时才会触达 {@link GeckoLibPoemOfTheEndRenderer}，
 * 从而避免没装 GeckoLib 时出现 {@code NoClassDefFoundError}。</p>
 *
 * <p>没装 GeckoLib 时返回 {@code null}：此时不注册任何 {@code IClientItemExtensions}，
 * 物品模型也会被换成 2D 版本（见 {@code PoemOfTheEndModelSwapper}），
 * 走 vanilla 的普通物品渲染路径。</p>
 */
public final class PoemOfTheEndGeoCompat {

    private static final Logger LOGGER = LogUtils.getLogger();

    private PoemOfTheEndGeoCompat() {
    }

    /** GeckoLib 是否可用。注意 dev 环境因 runtimeOnly 恒为 true。 */
    public static boolean isGeckoLibLoaded() {
        return ModList.get().isLoaded("geckolib");
    }

    /**
     * @return 装了 GeckoLib 时返回 3D 渲染器；否则返回 {@code null}（表示走 vanilla 2D 路径）
     */
    public static BlockEntityWithoutLevelRenderer createRenderer() {
        if (isGeckoLibLoaded()) {
            LOGGER.info("[poem] geckolib 已加载 -> 终末之诗使用 GeckoLib 3D 渲染器");
            return GeckoLibPoemOfTheEndRenderer.create();
        }
        LOGGER.info("[poem] geckolib 未加载 -> 终末之诗回退到 2D 模型");
        return null;
    }
}