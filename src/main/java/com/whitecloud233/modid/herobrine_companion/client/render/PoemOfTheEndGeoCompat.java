package com.whitecloud233.modid.herobrine_companion.client.render;

import com.mojang.logging.LogUtils;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.loading.FMLEnvironment;
import org.slf4j.Logger;

/**
 * 终末之诗渲染器的工厂：按是否安装 GeckoLib 选择 3D 渲染器或回退渲染器。
 *
 * <p>本类不引用任何 GeckoLib 类，可被物品类安全加载；
 * 只有 {@code geckolib} 已加载时才会触达 {@link GeckoLibPoemOfTheEndRenderer}，
 * 从而避免没装 GeckoLib 时出现 {@code NoClassDefFoundError}。</p>
 */
public final class PoemOfTheEndGeoCompat {

    private static final Logger LOGGER = LogUtils.getLogger();

    private PoemOfTheEndGeoCompat() {
    }

    public static BlockEntityWithoutLevelRenderer createRenderer() {
        if (!FMLEnvironment.dist.isClient()) {
            // 防御：仅客户端会查询自定义渲染器
            return null;
        }
        if (ModList.get().isLoaded("geckolib")) {
            LOGGER.info("[poem] geckolib 已加载 -> 使用 GeckoLib 3D 渲染器");
            try {
                return GeckoLibPoemOfTheEndRenderer.create();
            } catch (LinkageError e) {
                // 防御：运行时的 GeckoLib 与编译期 API 不匹配时（例如精简版/旧版
                // geckolib 缺失本模组引用的类，或未来版本移除了旧 API），
                // 回退到手写模型而不是崩溃。NoClassDefFoundError、
                // ExceptionInInitializerError 均继承自 LinkageError。
                LOGGER.warn("[poem] geckolib 缺少所需 API（{}），回退到 2D 渲染器: {}", e.getClass().getSimpleName(), e.getMessage());
                return new PoemOfTheEndFallbackRenderer();
            }
        }
        LOGGER.info("[poem] geckolib 未加载 -> 使用回退渲染器");
        return new PoemOfTheEndFallbackRenderer();
    }
}
