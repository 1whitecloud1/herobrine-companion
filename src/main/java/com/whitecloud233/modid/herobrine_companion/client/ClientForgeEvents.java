package com.whitecloud233.modid.herobrine_companion.client;

import com.whitecloud233.modid.herobrine_companion.HerobrineCompanion;
import com.whitecloud233.modid.herobrine_companion.client.service.LocalChatService;
import com.whitecloud233.modid.herobrine_companion.item.PoemOfTheEndItem;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;

import java.lang.reflect.Method;

@Mod.EventBusSubscriber(modid = HerobrineCompanion.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ClientForgeEvents {

    private static Method startAttackMethod;

    @SubscribeEvent
    public static void onClientPlayerLogin(ClientPlayerNetworkEvent.LoggingIn event) {
        // 修改说明：LocalChatService 在第一次 getInstance() 时会自动连接数据库。
        // 这里调用 loadChatRules() 是为了确保每次进游戏都重新读取一遍规则。
        LocalChatService.getInstance().loadChatRules();
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null && mc.level != null && !mc.isPaused()) {
                handlePoemRapidFire(mc);
            }
        }
    }

    private static void handlePoemRapidFire(Minecraft mc) {
        Player player = mc.player;
        ItemStack stack = player.getMainHandItem();

        if (stack.getItem() instanceof PoemOfTheEndItem && mc.options.keyAttack.isDown()) {
            if (stack.getOrCreateTag().getInt("PoemMode") == PoemOfTheEndItem.MODE_VOID_SHATTER) {
                // 碎空模式：极速连击
                try {
                    if (startAttackMethod == null) {
                        // 尝试使用 startAttack 的 SRG 名称
                        startAttackMethod = ObfuscationReflectionHelper.findMethod(Minecraft.class, "startAttack");
                        startAttackMethod.setAccessible(true);
                    }
                    startAttackMethod.invoke(mc);
                    // 显式调用挥手动作，确保有动画
                    player.swing(InteractionHand.MAIN_HAND);
                } catch (Exception e) {
                    // 如果找不到方法，尝试使用混淆名（如果需要）或者记录错误但不崩溃
                    // 在开发环境中通常是 startAttack，在生产环境中可能是 m_91317_
                    try {
                         if (startAttackMethod == null) {
                            startAttackMethod = ObfuscationReflectionHelper.findMethod(Minecraft.class, "m_91317_");
                            startAttackMethod.setAccessible(true);
                            startAttackMethod.invoke(mc);
                            player.swing(InteractionHand.MAIN_HAND);
                         }
                    } catch (Exception ex) {
                        // 忽略错误，避免刷屏
                    }
                }
            }
        }
    }
}
