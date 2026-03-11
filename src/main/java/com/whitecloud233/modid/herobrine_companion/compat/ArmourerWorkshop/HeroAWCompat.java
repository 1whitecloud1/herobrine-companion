package com.whitecloud233.modid.herobrine_companion.compat.ArmourerWorkshop;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.ForgeRegistries;

public class HeroAWCompat {

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
    public static void attachAW(EntityRenderer<?> heroRenderer) {
        if (isLoaded()) {
            AWSafeInvoker.attachAW(heroRenderer);
        }
    }

    // ==========================================
    // 【核心防御机制】安全隔离内部类！
    // 只要没有安装时装工坊，JVM 就绝对不会加载这个类，杜绝崩溃！
    // ==========================================
    private static class AWSafeInvoker {
        static void attachAW(EntityRenderer<?> heroRenderer) {
            try {
                var dispatcher = Minecraft.getInstance().getEntityRenderDispatcher();
                EntityRenderer<?> playerRenderer = dispatcher.getSkinMap().get("default");

                if (playerRenderer != null) {
                    var playerContext = moe.plushie.armourers_workshop.core.client.other.EntityRendererContext.of(playerRenderer);
                    var playerProfile = playerContext.entityProfile();

                    var heroContext = moe.plushie.armourers_workshop.core.client.other.EntityRendererContext.of(heroRenderer);
                    heroContext.setEntityType(EntityType.PLAYER);
                    heroContext.setEntityProfile(playerProfile);

                    System.out.println("✅ [Herobrine Companion] 成功为创世神注入 Armourer's Workshop 原生渲染核心！");
                }
            } catch (Throwable e) {
                System.err.println("❌ [Herobrine Companion] 注入 AW 渲染核心失败: " + e.getMessage());
            }
        }
    }
}