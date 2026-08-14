package com.whitecloud233.herobrine_companion.util;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/**
 * 把物品描述成 AI 可读的形式："展示名(注册ID)"。
 *
 * <p>供 Omniscient Eye 与 agent 工具做<b>装备感知</b>共用：展示名跟随客户端语言
 * （AI 需按玩家语言回复），注册 ID 兜底消除同名歧义。空物品统一为 "empty"。</p>
 */
public final class AiItemNaming {

    private AiItemNaming() {
    }

    public static String describe(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return "empty";
        }
        ResourceLocation key = BuiltInRegistries.ITEM.getKey(stack.getItem());
        String id = key != null ? key.toString() : String.valueOf(stack.getItem());
        return stack.getHoverName().getString() + " (" + id + ")";
    }
}
