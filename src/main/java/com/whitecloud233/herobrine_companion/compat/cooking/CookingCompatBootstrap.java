package com.whitecloud233.herobrine_companion.compat.cooking;

import com.whitecloud233.herobrine_companion.entity.cooking.CookingBackendRegistry;
import com.whitecloud233.herobrine_companion.entity.cooking.CookingService;

import java.util.List;

public final class CookingCompatBootstrap {
    private CookingCompatBootstrap() {
    }

    public static void registerBackends() {
        // 保留现有重叠厨具的优先级，不通过扫描或排序隐式改变路由。
        CookingService.installBackends(new CookingBackendRegistry(List.of(
                new FarmersDelightCookingBackend(), new KaleidoscopeCookingBackend())));
    }
}
