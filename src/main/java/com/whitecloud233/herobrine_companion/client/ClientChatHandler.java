package com.whitecloud233.herobrine_companion.client;

import com.whitecloud233.herobrine_companion.HerobrineCompanion;
import com.whitecloud233.herobrine_companion.client.service.LocalChatService;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientChatEvent;

import java.util.Set;

@EventBusSubscriber(modid = HerobrineCompanion.MODID, value = Dist.CLIENT)
public class ClientChatHandler {

    // 定义退出聊天的关键词
    private static final Set<String> EXIT_COMMANDS = Set.of("bye", "exit", "quit", "再见", "退出", "拜拜");

    // 默认回复的翻译键和兜底文本
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

                // 显示玩家自己说的
                Component heroMessage;

                // === 核心修改：调用 LocalChatService 获取规则 ===
                // 这里的 CachedRule 是我们在 LocalChatService 里定义的 record
                LocalChatService.CachedRule rule = LocalChatService.getInstance().getChatResponse(message);

                if (rule != null) {
                    // 1. 生成翻译键: chat.herobrine_companion.rule.123
                    String translationKey = "chat.herobrine_companion.rule." + rule.id();

                    // 2. 获取原始文本作为兜底
                    String originalText = rule.response();

                    // 3. 替换占位符 {player}
                    if (Minecraft.getInstance().getUser() != null) {
                        originalText = originalText.replace("{player}", Minecraft.getInstance().getUser().getName());
                    }

                    // 4. 构建消息组件：尝试使用翻译键，如果语言文件里没有，就用 originalText
                    heroMessage = Component.translatableWithFallback(translationKey, originalText);
                } else {
                    // 没有匹配到规则，使用默认回复
                    heroMessage = Component.translatableWithFallback(DEFAULT_RESPONSE_KEY, DEFAULT_RESPONSE_FALLBACK);
                }

                // 在聊天栏显示 Herobrine 的回复
                Minecraft.getInstance().gui.getChat().addMessage(
                        Component.translatable("message.herobrine_companion.chat_hero", heroMessage)
                );

                showExitHint();
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
        // AIService.clearHistory(); // 如果需要清除上下文可解开注释
        Minecraft.getInstance().gui.getChat().addMessage(
                Component.translatable("message.herobrine_companion.chat_exit")
        );
    }
}