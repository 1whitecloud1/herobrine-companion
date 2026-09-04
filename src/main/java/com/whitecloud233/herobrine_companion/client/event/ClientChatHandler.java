package com.whitecloud233.herobrine_companion.client.event;

import com.whitecloud233.herobrine_companion.HerobrineCompanion;
import com.whitecloud233.herobrine_companion.client.gui.HeroChatScreen;
import com.whitecloud233.herobrine_companion.client.gui.crosschat.CrossChatScreen;
import com.whitecloud233.herobrine_companion.client.service.AIReplyGuard;
import com.whitecloud233.herobrine_companion.client.service.AIService;
import com.whitecloud233.herobrine_companion.client.service.ConversationStore;
import com.whitecloud233.herobrine_companion.client.service.LLMConfig;
import com.whitecloud233.herobrine_companion.client.service.LocalChatService;
import com.whitecloud233.herobrine_companion.network.PacketHandler;
import com.whitecloud233.herobrine_companion.network.ai.SendCrossChatHbMessagePacket;
import com.whitecloud233.herobrine_companion.network.ai.SendCrossChatMessagePacket;
import com.whitecloud233.herobrine_companion.util.LegacyFormattingComponents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientChatEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;

import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

@EventBusSubscriber(modid = HerobrineCompanion.MODID, value = Dist.CLIENT)
public class ClientChatHandler {
    private static final int STREAM_PREVIEW_MAX_CHARS = 96;
    private static final String CROSS_PLAYER_PREFIX = "@cross ";
    private static final String CROSS_PLAYER_ALT_PREFIX = "@peer ";
    private static final String CROSS_HB_PREFIX = "@hb ";
    private static final String CROSS_HERO_PREFIX = "@hero ";

    // 定义退出聊天的关键词
    private static final Set<String> EXIT_COMMANDS = Set.of("bye", "exit", "quit", "再见", "退出", "拜拜");

    // 本地模式默认回复的翻译键和兜底文本
    private static final String DEFAULT_RESPONSE_KEY = "chat.herobrine_companion.default_silence";
    private static final String DEFAULT_RESPONSE_FALLBACK = "§7[Hero stares at you silently...]";

    @SubscribeEvent
    public static void onClientChat(ClientChatEvent event) {
        ClientHooks.ChatMode mode = ClientHooks.getChatMode();

        if (mode == ClientHooks.ChatMode.CROSS_CHAT) {
            handleCrossChat(event);
            return;
        }

        // 只在开启了“与 Herobrine 聊天”模式时拦截消息
        if (mode == ClientHooks.ChatMode.CHAT) {
            String message = event.getMessage();
            String trimmed = message == null ? "" : message.trim();
            String normalized = normalizeCommandText(trimmed);

            // 检查是否是退出指令
            if (isExitCommand(normalized)) {
                exitChat();
                event.setCanceled(true); // 阻止消息发送到服务器
                return;
            }

            // 只有当不是以 "/" 开头的指令时才处理
            if (!trimmed.startsWith("/")) {
                event.setCanceled(true); // 拦截消息，不发给服务器，改为本地处理

                Minecraft mc = Minecraft.getInstance();

                // 在本地显示玩家自己输入的文字
                if (mc.player != null) {
                    Component playerMessage = Component.translatable("chat.type.text", mc.player.getDisplayName(), Component.literal(message));
                    mc.gui.getChat().addMessage(playerMessage);
                }

                if (ClientHooks.hasActiveCrossChatSession()) {
                    if (isCrossPlayerCommand(normalized)) {
                        String crossMessage = extractPrefixedMessage(trimmed);
                        if (!crossMessage.isEmpty()) {
                            PacketHandler.sendToServer(new SendCrossChatMessagePacket(crossMessage));
                            showHeroChatHint();
                            reopenCurrentChat(mc);
                        }
                        return;
                    }

                    if (isCrossHbCommand(normalized)) {
                        String hbMessage = extractPrefixedMessage(trimmed);
                        if (!hbMessage.isEmpty()) {
                            PacketHandler.sendToServer(new SendCrossChatHbMessagePacket(hbMessage));
                            showHeroChatHint();
                            reopenCurrentChat(mc);
                        }
                        return;
                    }
                }

                // === 核心分支：判断当前是云端模式还是本地模式 ===
                if (ClientHooks.isApiEnabled()) {
                    // -----------------------------
                    // 【云端模式】走 AI 大模型 API
                    // -----------------------------
                    if (LLMConfig.isSetupIncompleteOrInvalid()) {
                        // 【核心防御】：发现没填 Key，直接强制弹出 UI 引导，不发网络请求
                        mc.tell(() -> {
                            mc.setScreen(new com.whitecloud233.herobrine_companion.config.ApiKeyInputScreen(new HeroChatScreen("")));
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
                                        Component.translatable("message.herobrine_companion.chat_hero", LegacyFormattingComponents.parse(reply))
                                );
                            }));
                        } else {
                            AIService.chat(message, mc.player.getUUID()).thenAccept(reply -> {
                                // 拿到大模型的回复后，切回主线程将其显示在聊天框
                                mc.tell(() -> {
                                    mc.gui.getChat().addMessage(
                                            Component.translatable("message.herobrine_companion.chat_hero", LegacyFormattingComponents.parse(reply))
                                    );
                                });
                            });
                        }
                    }
                } else {
                    // -----------------------------
                    // 【本地模式】已接入本地模型 → 走本地 LLM 完整管线（会话历史/指令/工具），
                    // 且不要求任何云端 Key；未接入 → 走本地 JSON 词库正则匹配兜底。
                    // -----------------------------
                    if (LLMConfig.isLocalModelConnected() && mc.player != null) {
                        AIService.chatLocal(message, mc.player.getUUID(), null).thenAccept(reply -> {
                            mc.tell(() -> {
                                showExitHint();
                                mc.gui.getChat().addMessage(
                                        Component.translatable("message.herobrine_companion.chat_hero", LegacyFormattingComponents.parse(reply))
                                );
                            });
                        });
                    } else {
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
                }

                // 显示退出提示
                if (!ClientHooks.isApiEnabled() || !LLMConfig.isStreamingEnabled()) {
                    showHeroChatHint();
                }

                // 保持聊天栏不退出
                // 利用 mc.tell 提交一个主线程任务，在下一帧立刻再把 ChatScreen 弹出来
                reopenCurrentChat(mc);
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

    private static void showHeroChatHint() {
        Minecraft mc = Minecraft.getInstance();
        if (ClientHooks.hasActiveCrossChatSession()) {
            mc.gui.setOverlayMessage(
                    Component.translatable(
                            "message.herobrine_companion.chat_hint_cross",
                            ClientHooks.getCrossChatPeerName().isBlank() ? "-" : ClientHooks.getCrossChatPeerName()
                    ),
                    false
            );
            return;
        }
        showExitHint();
    }

    private static void showCrossChatHint() {
        String peerName = ClientHooks.getCrossChatPeerName();
        Component target = Component.translatable(ClientHooks.isCrossChatHbInputMode()
                ? "gui.herobrine_companion.cross_chat.archive_mode_hb"
                : "gui.herobrine_companion.cross_chat.screen_target_peer_hb");
        Component auto = Component.translatable(ClientHooks.isCrossChatAutoChatEnabled()
                ? "gui.herobrine_companion.cross_chat.screen_status_on"
                : "gui.herobrine_companion.cross_chat.screen_status_off");
        Minecraft.getInstance().gui.setOverlayMessage(
                Component.translatable(
                        "message.herobrine_companion.cross_chat.overlay_compact",
                        peerName == null || peerName.isBlank() ? "-" : peerName,
                        target,
                        auto
                ),
                false
        );
    }

    private static void handleCrossChat(ClientChatEvent event) {
        String message = event.getMessage();
        if (message == null) {
            return;
        }
        String normalized = normalizeCommandText(message == null ? "" : message.trim());
        if (isExitCommand(normalized)) {
            exitChat();
            event.setCanceled(true);
            return;
        }
        if (message.startsWith("/")) {
            return;
        }

        event.setCanceled(true);
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.gui.getChat().addMessage(Component.translatable("chat.type.text", mc.player.getDisplayName(), Component.literal(message)));
        }

        String trimmed = message.trim();
        if (isCrossPlayerCommand(normalized)) {
            String crossMessage = extractPrefixedMessage(trimmed);
            if (!crossMessage.isEmpty()) {
                PacketHandler.sendToServer(new SendCrossChatMessagePacket(crossMessage));
            }
        } else if (isCrossHbCommand(normalized)) {
            String hbMessage = extractPrefixedMessage(trimmed);
            if (!hbMessage.isEmpty()) {
                PacketHandler.sendToServer(new SendCrossChatHbMessagePacket(hbMessage));
            }
        } else if (ClientHooks.isCrossChatHbInputMode()) {
            PacketHandler.sendToServer(new SendCrossChatHbMessagePacket(message));
        } else {
            PacketHandler.sendToServer(new SendCrossChatMessagePacket(message));
        }

        showCrossChatHint();
        mc.tell(() -> mc.setScreen(new CrossChatScreen("")));
    }

    private static void updateStreamingOverlay(Minecraft mc, String partialText, AtomicLong lastOverlayUpdate, AtomicInteger lastOverlayLength) {
        if (partialText == null || partialText.isBlank()) {
            return;
        }
        // 本地 Qwen3 等模型可能带思考块：预览只显示剥离后的正文，避免思考过程闪屏
        String visibleText = AIReplyGuard.stripThinkingBlocks(partialText);
        if (visibleText.isBlank()) {
            return;
        }

        long now = System.currentTimeMillis();
        int currentLength = visibleText.length();
        long lastUpdate = lastOverlayUpdate.get();
        int lastLength = lastOverlayLength.get();
        if ((now - lastUpdate) < 45L && (currentLength - lastLength) < 3) {
            return;
        }

        lastOverlayUpdate.set(now);
        lastOverlayLength.set(currentLength);
        String preview = visibleText.length() > STREAM_PREVIEW_MAX_CHARS
                ? visibleText.substring(visibleText.length() - STREAM_PREVIEW_MAX_CHARS)
                : visibleText;

        mc.tell(() -> mc.gui.setOverlayMessage(Component.translatable("message.herobrine_companion.streaming_preview", preview + "▌"), false));
    }

    private static boolean isExitCommand(String normalized) {
        return !normalized.isEmpty() && EXIT_COMMANDS.contains(normalized);
    }

    private static String normalizeCommandText(String message) {
        if (message == null) {
            return "";
        }
        String normalized = message.trim().toLowerCase();
        if (normalized.startsWith("/")) {
            normalized = normalized.substring(1).trim();
        }
        return normalized;
    }

    private static boolean isCrossPlayerCommand(String normalized) {
        return normalized.startsWith(CROSS_PLAYER_PREFIX) || normalized.startsWith(CROSS_PLAYER_ALT_PREFIX);
    }

    private static boolean isCrossHbCommand(String normalized) {
        return normalized.startsWith(CROSS_HB_PREFIX) || normalized.startsWith(CROSS_HERO_PREFIX);
    }

    private static String extractPrefixedMessage(String message) {
        if (message == null) {
            return "";
        }
        int spaceIndex = message.indexOf(' ');
        return spaceIndex < 0 ? "" : message.substring(spaceIndex + 1).trim();
    }

    private static void reopenCurrentChat(Minecraft mc) {
        mc.tell(() -> mc.setScreen(ClientHooks.getChatMode() == ClientHooks.ChatMode.CROSS_CHAT
                ? new CrossChatScreen("")
                : new HeroChatScreen("")));
    }

    private static void exitChat() {
        ClientHooks.ChatMode currentMode = ClientHooks.getChatMode();
        ClientHooks.disableChat();
        // 仅在退出普通 hb 对话时清理运行期短期记忆；跨 HB 会话只退出界面，不影响本地对话记忆
        if (currentMode == ClientHooks.ChatMode.CHAT && Minecraft.getInstance().player != null) {
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
            com.whitecloud233.herobrine_companion.client.service.CrossChatHistoryStore.getInstance().saveAndClearSession();
        }
        Minecraft.getInstance().gui.setOverlayMessage(Component.empty(), false);
    }
}
