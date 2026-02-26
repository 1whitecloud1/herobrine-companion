package com.whitecloud233.herobrine_companion.client;

import com.whitecloud233.herobrine_companion.HerobrineCompanion;
import com.whitecloud233.herobrine_companion.client.service.AIService;
import com.whitecloud233.herobrine_companion.client.service.LocalChatService;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientChatEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;

import java.util.Set;

@EventBusSubscriber(modid = HerobrineCompanion.MODID, value = Dist.CLIENT)
public class ClientChatHandler {

    // 定义退出聊天的关键词
    private static final Set<String> EXIT_COMMANDS = Set.of("bye", "exit", "quit", "再见", "退出", "拜拜");

    // 本地模式默认回复的翻译键和兜底文本
    private static final String DEFAULT_RESPONSE_KEY = "chat.herobrine_companion.default_silence";
    private static final String DEFAULT_RESPONSE_FALLBACK = "§7[Hero stares at you silently...]";

    @SubscribeEvent
    public static void onClientChat(ClientChatEvent event) {
        ClientHooks.ChatMode mode = ClientHooks.getChatMode();

        // 只在开启了“与 Herobrine 聊天”模式时拦截消息
        if (mode == ClientHooks.ChatMode.CHAT) {
            String message = event.getMessage();

            // 检查是否是退出指令
            if (EXIT_COMMANDS.contains(message.toLowerCase())) {
                exitChat();
                event.setCanceled(true); // 阻止消息发送到服务器
                return;
            }

            // 只有当不是以 "/" 开头的指令时才处理
            if (!message.startsWith("/")) {
                event.setCanceled(true); // 拦截消息，不发给服务器，改为本地处理

                Minecraft mc = Minecraft.getInstance();

                // 在本地显示玩家自己输入的文字
                if (mc.player != null) {
                    Component playerMessage = Component.literal("<" + mc.player.getName().getString() + "> " + message);
                    mc.gui.getChat().addMessage(playerMessage);
                }

                // === 核心分支：判断当前是云端模式还是本地模式 ===
                if (ClientHooks.isApiEnabled()) {
                    // -----------------------------
                    // 【云端模式】走 AI 大模型 API
                    // -----------------------------
                    if (mc.player != null) {
                        AIService.chat(message, mc.player.getUUID()).thenAccept(reply -> {
                            // 拿到大模型的回复后，切回主线程将其显示在聊天框
                            mc.tell(() -> {
                                mc.gui.getChat().addMessage(
                                        Component.translatable("message.herobrine_companion.chat_hero", Component.literal(reply))
                                );
                            });
                        });
                    }
                } else {
                    // -----------------------------
                    // 【本地模式】走 H2 数据库正则匹配
                    // -----------------------------
                    LocalChatService.CachedRule rule = LocalChatService.getInstance().getChatResponse(message);
                    Component heroMessage;

                    if (rule != null) {
                        // 1. 生成翻译键: chat.herobrine_companion.rule.123
                        String translationKey = "chat.herobrine_companion.rule." + rule.id();

                        // 2. 获取原始文本作为兜底
                        String originalText = rule.response();

                        // 3. 替换占位符 {player}
                        if (mc.getUser() != null) {
                            originalText = originalText.replace("{player}", mc.getUser().getName());
                        }

                        // 4. 构建消息组件
                        heroMessage = Component.translatableWithFallback(translationKey, originalText);
                    } else {
                        // 没有匹配到规则，使用默认回复
                        heroMessage = Component.translatableWithFallback(DEFAULT_RESPONSE_KEY, DEFAULT_RESPONSE_FALLBACK);
                    }

                    // 立即在聊天栏显示 Herobrine 的本地回复
                    mc.gui.getChat().addMessage(
                            Component.translatable("message.herobrine_companion.chat_hero", heroMessage)
                    );
                }

                // 显示退出提示
                showExitHint();

                // 保持聊天栏不退出
                // 利用 mc.tell 提交一个主线程任务，在下一帧立刻再把 ChatScreen 弹出来
                mc.tell(() -> {
                    mc.setScreen(new ChatScreen(""));
                });
            }
        }
    }

    private static void showExitHint() {
        // 在 Action Bar (物品栏上方) 显示“输入 bye 退出”的提示
        Minecraft.getInstance().gui.setOverlayMessage(
                Component.translatable("message.herobrine_companion.chat_hint_exit"),
                false
        );
    }

    private static void exitChat() {
        ClientHooks.disableChat();

        // 【新增】：在退出聊天时，清空大模型对当前玩家的短期记忆上下文
        if (Minecraft.getInstance().player != null) {
            AIService.clearHistory(Minecraft.getInstance().player.getUUID());
        }

        Minecraft.getInstance().gui.getChat().addMessage(
                Component.translatable("message.herobrine_companion.chat_exit")
        );
    }
    // 【新增】：当玩家退出当前存档或断开服务器时，强制重置所有聊天状态与大模型记忆
    // 当玩家退出当前存档或断开服务器时，强制重置所有聊天状态与大模型记忆
    @SubscribeEvent
    public static void onPlayerLogOut(ClientPlayerNetworkEvent.LoggingOut event) {
        // 【修改】：使用我们刚写的新方法，彻底重置聊天状态和 API 开启状态
        ClientHooks.resetAll();

        // 2. 清理大模型的记忆上下文
        if (event.getPlayer() != null) {
            AIService.clearHistory(event.getPlayer().getUUID());
        }
    }
}