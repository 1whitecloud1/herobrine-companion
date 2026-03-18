package com.whitecloud233.modid.herobrine_companion.client.event;

import com.whitecloud233.modid.herobrine_companion.item.PoemOfTheEndItem;
import com.whitecloud233.modid.herobrine_companion.network.CleaveSkillPacket;
import com.whitecloud233.modid.herobrine_companion.network.PacketHandler;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

@Mod.EventBusSubscriber(modid = "herobrine_companion", value = Dist.CLIENT)
public class KeyBindingHandler {

    // 注册 5 键
    public static final KeyMapping SKILL_KEY = new KeyMapping(
            "key.herobrine_companion.cleave_skill", // 语言文件中的本地化键值
            GLFW.GLFW_KEY_KP_5,                        // 默认按键 5
            "key.categories.herobrine_companion"    // 按键设置里的分类名
    );

    // 客户端蓄力计时器
    private static int chargeTicks = 0;

    // 必须在 Mod 事件总线上注册按键
    @Mod.EventBusSubscriber(modid = "herobrine_companion", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static class ModBusEvents {
        @SubscribeEvent
        public static void onKeyRegister(RegisterKeyMappingsEvent event) {
            event.register(SKILL_KEY);
        }
    }

    // 监听每一帧的客户端输入
    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null) return;

        // 判断：按下了R键，且主手里拿着镰刀
        if (SKILL_KEY.isDown() && player.getMainHandItem().getItem() instanceof PoemOfTheEndItem) {
            chargeTicks++;
            
            // 可以加一点视觉/听觉反馈，比如蓄力时播放滋滋的雷电声
            if (chargeTicks == 1) {
                // 刚按下时触发一点声音...
            }
            
            // 20 Ticks = 1 秒
            if (chargeTicks == 20) {
                // 【加入 Debug 提示】
                player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§e[Debug] 客户端：R键蓄力1秒完成，正在向服务端发送数据包..."));
                // 蓄力完成！发送数据包给服务端触发技能
                PacketHandler.sendToServer(new CleaveSkillPacket());
                
                // 为了防止一直按着狂发包，可以把计时器推到一个不会再触发的值
                chargeTicks = 999; 
            }
        } else {
            // 如果松开按键，或者切了别的物品，蓄力清零
            chargeTicks = 0;
        }
    }
}