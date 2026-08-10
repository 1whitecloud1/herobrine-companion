package com.whitecloud233.modid.herobrine_companion.client.service;

import com.whitecloud233.modid.herobrine_companion.HerobrineCompanion;
import com.whitecloud233.modid.herobrine_companion.item.SourceFlowItem;
import com.whitecloud233.modid.herobrine_companion.network.HeroAIActionPacket;
import com.whitecloud233.modid.herobrine_companion.network.HeroPunishmentPacket;
import com.whitecloud233.modid.herobrine_companion.network.PacketHandler;
import com.whitecloud233.modid.herobrine_companion.network.TeleportToHeroPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * 世界命令执行器：把一条<b>已归一化</b>的命令串执行到游戏里（单机直改 / 联机发包），
 * 并负责权限校验与安全校验。
 *
 * <p>单一职责：只做"执行 + 把关"。这是 {@code AIService} 曾经最大的单块职责
 * （{@code executeCommandWithFeedback} 约 190 行 if/else），也是所有游戏/网络依赖的汇聚点——
 * 所有 {@code Minecraft}、{@code ServerPlayer}、{@code PacketHandler}、
 * {@code HeroSummonItem}、{@code SourceFlowItem} 的调用都隔离在这里，LLM 编排层不直接碰它们。</p>
 *
 * <p>本类只负责"给命令串就执行"，不负责判断 AI 该不该发这条命令（见 {@link AIActionIntentInference}）。</p>
 */
public final class AIGameCommandExecutor {

    public static final String ACTION_MASSIVE_LIGHTNING = "action:massive_lightning";
    public static final String ACTION_TELEPORT_TO_HERO = "action:teleport_to_hero";

    private static final String VANILLA_NAMESPACE = "minecraft";

    private AIGameCommandExecutor() {
    }

    /**
     * 执行一条命令串。返回 {@code CompletableFuture<Boolean>}，{@code true} 表示已提交/成功执行。
     * 会先做权限与安全校验（禁开作弊 / 引用非原版内容时拒绝）。
     */
    public static CompletableFuture<Boolean> executeCommandWithFeedback(String command, UUID targetPlayerUUID) {
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            future.complete(false);
            return future;
        }

        mc.tell(() -> {
            if (!isPermissionBypassAction(command) && !mc.player.hasPermissions(2)) {
                mc.gui.getChat().addMessage(Component.translatable("message.herobrine_companion.ai_game.cheats_disabled"));
                future.complete(false);
                return;
            }
            if (!isSafeGeneratedCommand(command)) {
                mc.gui.getChat().addMessage(Component.translatable("message.herobrine_companion.ai_game.modded_blocked"));
                future.complete(false);
                return;
            }

            if (ACTION_MASSIVE_LIGHTNING.equals(command)) {
                if (mc.hasSingleplayerServer() && mc.getSingleplayerServer() != null) {
                    mc.getSingleplayerServer().execute(() -> {
                        try {
                            ServerPlayer serverPlayer = mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
                            if (serverPlayer != null) {
                                CommandSourceStack godSource = serverPlayer.createCommandSourceStack().withPermission(4);
                                for (int i = 0; i < 20; i++) {
                                    int offsetX = (int) (Math.random() * 30 - 15), offsetZ = (int) (Math.random() * 30 - 15);
                                    mc.getSingleplayerServer().getCommands().performPrefixedCommand(godSource, String.format("execute at @s run summon lightning_bolt ~%d ~ ~%d", offsetX, offsetZ));
                                }
                                future.complete(true);
                            } else {
                                future.complete(false);
                            }
                        } catch (Exception e) {
                            future.complete(false);
                        }
                    });
                } else {
                    future.complete(false);
                }

            } else if (HeroPunishmentPacket.ACTION_KILL_PLAYER.equals(command)) {
                if (mc.hasSingleplayerServer() && mc.getSingleplayerServer() != null) {
                    var server = mc.getSingleplayerServer();
                    server.execute(() -> {
                        try {
                            ServerPlayer serverPlayer = server.getPlayerList().getPlayer(targetPlayerUUID);
                            if (serverPlayer == null) {
                                future.complete(false);
                                return;
                            }

                            if (serverPlayer.isAlive()) {
                                serverPlayer.kill();
                            }
                            future.complete(true);
                        } catch (Exception e) {
                            future.complete(false);
                        }
                    });
                } else {
                    PacketHandler.sendToServer(new HeroPunishmentPacket(command));
                    future.complete(true);
                }

            } else if (HeroPunishmentPacket.ACTION_KICK_PLAYER.equals(command)) {
                if (mc.hasSingleplayerServer() && mc.getSingleplayerServer() != null) {
                    var server = mc.getSingleplayerServer();
                    server.execute(() -> {
                        try {
                            ServerPlayer serverPlayer = server.getPlayerList().getPlayer(targetPlayerUUID);
                            if (serverPlayer == null) {
                                future.complete(false);
                                return;
                            }

                            serverPlayer.connection.disconnect(Component.literal("Herobrine has cast you out."));
                            future.complete(true);
                        } catch (Exception e) {
                            future.complete(false);
                        }
                    });
                } else {
                    PacketHandler.sendToServer(new HeroPunishmentPacket(command));
                    future.complete(true);
                }

            } else if (ACTION_TELEPORT_TO_HERO.equals(command) || command.startsWith("tp @s @e[type=herobrine_companion:hero")) {
                // 【行为2：玩家传送到 AI 身边】
                if (mc.hasSingleplayerServer() && mc.getSingleplayerServer() != null) {
                    var server = mc.getSingleplayerServer();
                    server.execute(() -> {
                        try {
                            ServerPlayer serverPlayer = server.getPlayerList().getPlayer(mc.player.getUUID());
                            if (serverPlayer == null) {
                                future.complete(false);
                                return;
                            }

                            // 调用 SourceFlowItem 的传送代码
                            boolean success = SourceFlowItem.performTeleportToHero(serverPlayer);
                            future.complete(success);
                        } catch (Exception e) {
                            future.complete(false);
                        }
                    });
                } else {
                    // 多人游戏下发送传送到Hero身边的数据包
                    PacketHandler.sendToServer(new TeleportToHeroPacket());
                    future.complete(true);
                }

            } else if (HeroAIActionPacket.isSupportedAction(command)) {
                if (mc.hasSingleplayerServer() && mc.getSingleplayerServer() != null) {
                    var server = mc.getSingleplayerServer();
                    server.execute(() -> {
                        try {
                            ServerPlayer serverPlayer = server.getPlayerList().getPlayer(targetPlayerUUID);
                            future.complete(serverPlayer != null && HeroAIActionPacket.performAction(serverPlayer, command));
                        } catch (Exception e) {
                            future.complete(false);
                        }
                    });
                } else {
                    PacketHandler.sendToServer(new HeroAIActionPacket(command));
                    future.complete(true);
                }

            } else if (command.contains("gamemode creative") || command.contains("gamemode 1")) {
                if (mc.hasSingleplayerServer() && mc.getSingleplayerServer() != null) {
                    mc.getSingleplayerServer().execute(() -> {
                        ServerPlayer serverPlayer = mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
                        if (serverPlayer != null) {
                            serverPlayer.setGameMode(GameType.CREATIVE);
                            future.complete(true);
                        } else {
                            future.complete(false);
                        }
                    });
                } else {
                    future.complete(false);
                }
            } else if (command.contains("gamemode survival") || command.contains("gamemode 0")) {
                if (mc.hasSingleplayerServer() && mc.getSingleplayerServer() != null) {
                    mc.getSingleplayerServer().execute(() -> {
                        ServerPlayer serverPlayer = mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
                        if (serverPlayer != null) {
                            serverPlayer.setGameMode(GameType.SURVIVAL);
                            future.complete(true);
                        } else {
                            future.complete(false);
                        }
                    });
                } else {
                    future.complete(false);
                }
            } else if (command.contains("gamemode spectator") || command.contains("gamemode 3")) {
                if (mc.hasSingleplayerServer() && mc.getSingleplayerServer() != null) {
                    mc.getSingleplayerServer().execute(() -> {
                        ServerPlayer serverPlayer = mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
                        if (serverPlayer != null) {
                            serverPlayer.setGameMode(GameType.SPECTATOR);
                            future.complete(true);
                        } else {
                            future.complete(false);
                        }
                    });
                } else {
                    future.complete(false);
                }
            } else {
                if (mc.hasSingleplayerServer() && mc.getSingleplayerServer() != null) {
                    mc.getSingleplayerServer().execute(() -> {
                        try {
                            ServerPlayer serverPlayer = mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
                            if (serverPlayer != null) {
                                CommandSourceStack godSource = serverPlayer.createCommandSourceStack().withPermission(4);
                                mc.getSingleplayerServer().getCommands().performPrefixedCommand(godSource, command);
                                future.complete(true);
                            } else {
                                future.complete(false);
                            }
                        } catch (Exception e) {
                            future.complete(false);
                        }
                    });
                } else {
                    mc.player.connection.sendCommand(command);
                    future.complete(true);
                }
            }
        });
        return future;
    }

    private static boolean isPermissionBypassAction(String command) {
        return HeroAIActionPacket.isSupportedAction(command)
                || isExtremePunishmentAction(command);
    }

    private static boolean isSafeGeneratedCommand(String command) {
        if (command == null || command.isBlank()) {
            return false;
        }
        String normalized = AIActionIntentInference.normalizeActionInferenceText(command);
        if (ACTION_TELEPORT_TO_HERO.equals(normalized)
                || ACTION_MASSIVE_LIGHTNING.equals(normalized)
                || HeroAIActionPacket.isSupportedAction(normalized)
                || isExtremePunishmentAction(normalized)) {
            return true;
        }
        if (normalized.startsWith("tp @e[type=" + HerobrineCompanion.MODID + ":hero")
                || normalized.startsWith("tp @s @e[type=" + HerobrineCompanion.MODID + ":hero")) {
            return true;
        }

        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("(?<![a-z0-9_.-])([a-z0-9_.-]+):[a-z0-9_/.-]+")
                .matcher(normalized);
        while (matcher.find()) {
            if (!VANILLA_NAMESPACE.equals(matcher.group(1))) {
                return false;
            }
        }
        return true;
    }

    private static boolean isExtremePunishmentAction(String command) {
        return HeroPunishmentPacket.ACTION_KILL_PLAYER.equals(command)
                || HeroPunishmentPacket.ACTION_KICK_PLAYER.equals(command);
    }
}
