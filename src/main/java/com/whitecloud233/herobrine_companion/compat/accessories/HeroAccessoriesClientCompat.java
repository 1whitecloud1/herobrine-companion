package com.whitecloud233.herobrine_companion.compat.accessories;

import com.whitecloud233.herobrine_companion.client.render.HeroRenderer;
import com.whitecloud233.herobrine_companion.entity.HeroEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;

public final class HeroAccessoriesClientCompat {

    private HeroAccessoriesClientCompat() {
    }

    public static void attachRenderLayer(HeroRenderer renderer) {
        if (!HeroAccessoriesCompat.isLoaded() || renderer == null) {
            return;
        }
        AccessoriesClientSafeInvoker.attachRenderLayer(renderer);
    }

    /**
     * 判断给定的饰品界面是否属于 Hero。mixin 用它决定是否展示空槽位。
     *
     * <p>用反射读界面上的目标实体，避免本类依赖 Accessories 界面的具体类型。</p>
     */
    public static boolean isHeroAccessoriesScreen(Object screen) {
        if (!HeroAccessoriesCompat.isLoaded() || screen == null) {
            return false;
        }
        try {
            java.lang.reflect.Method accessor = screen.getClass().getMethod("targetEntityDefaulted");
            accessor.setAccessible(true);
            return accessor.invoke(screen) instanceof HeroEntity;
        } catch (Throwable ignored) {
            com.whitecloud233.herobrine_companion.compat.safety.CompatSafety.rethrowFatal(ignored);
            return false;
        }
    }

    /**
     * 打开指定 Herobrine 的原生饰品栏。实体失效时不回退到玩家自己的装备。
     * 原生菜单的护甲和副手绑定由 AccessoriesHeroEquipmentMixin 在两端修正。
     *
     * @param target 装扮页绑定的 Herobrine
     * @return 是否成功发出打开请求
     */
    public static boolean openAccessoriesScreen(HeroEntity target) {
        if (!HeroAccessoriesCompat.isLoaded() || target == null || !target.isAlive()) {
            return false;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null || target.level() != mc.player.level()) {
            return false;
        }
        // 先关掉本模组自己的界面：饰品栏是独立菜单，留着旧界面会被覆盖或导致打开失败
        if (mc.screen != null) {
            mc.setScreen(null);
        }
        return AccessoriesClientSafeInvoker.openScreenFor(target);
    }

    private static final class AccessoriesClientSafeInvoker {

        private AccessoriesClientSafeInvoker() {
        }

        static void attachRenderLayer(LivingEntityRenderer<HeroEntity, PlayerModel<HeroEntity>> renderer) {
            renderer.addLayer(new com.whitecloud233.herobrine_companion.compat.safety.GuardedHeroLayer(renderer, "accessories",
                    new io.wispforest.accessories.client.AccessoriesRenderLayer<>(renderer)));
        }

        static boolean openScreenFor(HeroEntity target) {
            io.wispforest.accessories.networking.AccessoriesNetworking.sendToServer(
                    io.wispforest.accessories.networking.server.ScreenOpen.of(target,
                            io.wispforest.accessories.menu.AccessoriesMenuVariant.ORIGINAL));
            return true;
        }
    }
}
