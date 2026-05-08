package com.whitecloud233.modid.herobrine_companion.client.event;

import com.whitecloud233.modid.herobrine_companion.HerobrineCompanion;
import com.whitecloud233.modid.herobrine_companion.client.service.AIService;
import com.whitecloud233.modid.herobrine_companion.client.service.ConversationStore;
import com.whitecloud233.modid.herobrine_companion.client.service.LLMConfig;
import com.whitecloud233.modid.herobrine_companion.client.service.LocalChatService;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientChatEvent;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

@Mod.EventBusSubscriber(modid = HerobrineCompanion.MODID, value = Dist.CLIENT)
public class ClientChatHandler {
    private static final int STREAM_PREVIEW_MAX_CHARS = 96;

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
                    if (LLMConfig.isKeyMissingOrInvalid()) {
                        // 【核心防御】：发现没填 Key，直接强制弹出 UI 引导，不发网络请求
                        mc.tell(() -> {
                            mc.setScreen(new com.whitecloud233.modid.herobrine_companion.config.ApiKeyInputScreen(new ChatScreen("")));
                        });
                        return;
                    }

                    if (mc.player != null) {
                        if (LLMConfig.isStreamingEnabled()) {
                            AtomicLong lastOverlayUpdate = new AtomicLong(0L);
                            AtomicInteger lastOverlayLength = new AtomicInteger(0);

                            AIService.chat(message, mc.player.getUUID(), partial ->
                                    updateStreamingOverlay(mc, partial, lastOverlayUpdate, lastOverlayLength)
                            ).thenAccept(reply -> mc.tell(() -> {
                                showExitHint();
                                mc.gui.getChat().addMessage(
                                        Component.translatable("message.herobrine_companion.chat_hero", Component.literal(reply))
                                );
                            }));
                        } else {
                            AIService.chat(message, mc.player.getUUID()).thenAccept(reply -> {
                                // 拿到大模型的回复后，切回主线程将其显示在聊天框
                                mc.tell(() -> {
                                    mc.gui.getChat().addMessage(
                                            Component.translatable("message.herobrine_companion.chat_hero", Component.literal(reply))
                                    );
                                });
                            });
                        }
                    }
                } else {
                    // -----------------------------
                    // 【本地模式】走本地 JSON 词库正则匹配
                    // -----------------------------
                    LocalChatService.CachedRule rule = LocalChatService.getInstance().getChatResponse(message);
                    Component heroMessage;

                    if (rule != null) {
                        // 1. 生成翻译键: chat.herobrine_companion.rule.123
                        String translationKey = "chat.herobrine_companion.rule." + rule.id();

                        // 2. 获取原始文本作为兜底
                        String originalText = rule.response();

                        // 必须先判断 originalText 是否为 null！
                        if (originalText != null) {
                            // 3. 替换占位符 {player}
                            if (mc.getUser() != null && mc.getUser().getName() != null) {
                                originalText = originalText.replace("{player}", mc.getUser().getName());
                            }
                        } else {
                            originalText = "..."; // 防御性赋值，防止 Component 再次崩溃
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
                // 显示退出提示
                if (!ClientHooks.isApiEnabled() || !LLMConfig.isStreamingEnabled()) {
                    showExitHint();
                }

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
    private static void updateStreamingOverlay(Minecraft mc, String partialText, AtomicLong lastOverlayUpdate, AtomicInteger lastOverlayLength) {
        if (partialText == null || partialText.isBlank()) {
            return;
        }

        long now = System.currentTimeMillis();
        int currentLength = partialText.length();
        long lastUpdate = lastOverlayUpdate.get();
        int lastLength = lastOverlayLength.get();
        if ((now - lastUpdate) < 45L && (currentLength - lastLength) < 3) {
            return;
        }

        lastOverlayUpdate.set(now);
        lastOverlayLength.set(currentLength);
        String preview = partialText.length() > STREAM_PREVIEW_MAX_CHARS
                ? partialText.substring(partialText.length() - STREAM_PREVIEW_MAX_CHARS)
                : partialText;

        mc.tell(() -> mc.gui.setOverlayMessage(Component.literal("§e<Herobrine> §f" + preview + "▌"), false));
    }
    private static void exitChat() {
        ClientHooks.disableChat();
        // 在退出聊天时，仅清理运行期的短期去重缓存；已保存对话记录继续保留
        if (Minecraft.getInstance().player != null) {
            AIService.clearTransientHistory(Minecraft.getInstance().player.getUUID());
        }
        Minecraft.getInstance().gui.setOverlayMessage(Component.empty(), false);

        Minecraft.getInstance().gui.getChat().addMessage(
                Component.translatable("message.herobrine_companion.chat_exit")
        );
    }

    // 当玩家退出当前存档或断开服务器时，重置聊天状态并保存会话；不要清空已存档的聊天记录
    @SubscribeEvent
    public static void onPlayerLogOut(ClientPlayerNetworkEvent.LoggingOut event) {
        // 使用我们刚写的新方法，彻底重置聊天状态和 API 开启状态
        ClientHooks.resetAll();
        // 仅清理运行期记忆上下文，不删除已落盘的会话消息
        if (event.getPlayer() != null) {
            AIService.clearTransientHistory(event.getPlayer().getUUID());
            ConversationStore.getInstance().saveAndClearSession();
        }
        Minecraft.getInstance().gui.setOverlayMessage(Component.empty(), false);
    }
}