package com.whitecloud233.modid.herobrine_companion.compat.ArmourerWorkshop;

import com.mojang.logging.LogUtils;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.ForgeRegistries;
import org.slf4j.Logger;

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

public class HeroAWCompat {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Set<EntityRenderer<?>> ATTACHED_RENDERERS = Collections.newSetFromMap(new WeakHashMap<>());

    public static boolean isLoaded() {
        return ModList.get().isLoaded("armourers_workshop");
    }

    // 判断是不是时装工坊的物品 (这里只用到了原版类，所以很安全)
    public static boolean isAwItem(ItemStack stack) {
        if (stack.isEmpty()) return false;
        var registryName = ForgeRegistries.ITEMS.getKey(stack.getItem());
        return registryName != null && registryName.getNamespace().equals("armourers_workshop");
    }

    // 外部调用入口
    public static synchronized boolean attachAW(EntityRenderer<?> heroRenderer, EntityRenderer<?> playerRenderer) {
        if (!isLoaded() || heroRenderer == null || playerRenderer == null) {
            return false;
        }
        if (ATTACHED_RENDERERS.contains(heroRenderer)) {
            return true;
        }

        boolean attached = AWSafeInvoker.attachAW(heroRenderer, playerRenderer);
        if (attached) {
            ATTACHED_RENDERERS.add(heroRenderer);
        }
        return attached;
    }

    public static synchronized boolean isAttached(EntityRenderer<?> heroRenderer) {
        return heroRenderer != null && ATTACHED_RENDERERS.contains(heroRenderer);
    }

    // ==========================================
    // 【核心防御机制】安全隔离内部类！
    // 只要没有安装时装工坊，JVM 就绝对不会加载这个类，杜绝崩溃！
    // ==========================================
    private static class AWSafeInvoker {
        static boolean attachAW(EntityRenderer<?> heroRenderer, EntityRenderer<?> playerRenderer) {
            try {
                var playerContext = moe.plushie.armourers_workshop.core.client.other.EntityRendererContext.of(playerRenderer);
                var playerProfile = playerContext.entityProfile();
                if (playerProfile == null) {
                    return false;
                }

                var heroContext = moe.plushie.armourers_workshop.core.client.other.EntityRendererContext.of(heroRenderer);
                heroContext.setEntityType(EntityType.PLAYER);
                heroContext.setEntityProfile(playerProfile);

                LOGGER.info("[Herobrine Companion] Armourer's Workshop renderer attached to Hero renderer.");
                return true;
            } catch (Throwable e) {
                LOGGER.warn("[Herobrine Companion] Failed to attach Armourer's Workshop renderer context to Hero renderer.", e);
                return false;
            }
        }
    }
}