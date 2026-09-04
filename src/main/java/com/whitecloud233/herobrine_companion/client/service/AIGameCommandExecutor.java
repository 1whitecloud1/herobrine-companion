package com.whitecloud233.herobrine_companion.client.service;

import com.whitecloud233.herobrine_companion.HerobrineCompanion;
import com.whitecloud233.herobrine_companion.entity.ai.agent.tool.HeroSummonTool;
import com.whitecloud233.herobrine_companion.item.HeroSummonItem;
import com.whitecloud233.herobrine_companion.item.SourceFlowItem;
import com.whitecloud233.herobrine_companion.network.AgentRequestPacket;
import com.whitecloud233.herobrine_companion.network.HeroAIActionPacket;
import com.whitecloud233.herobrine_companion.network.HeroPunishmentPacket;
import com.whitecloud233.herobrine_companion.network.PacketHandler;
import com.whitecloud233.herobrine_companion.network.TeleportToHeroPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;

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
    public static final String ACTION_SUMMON_HERO_TO_PLAYER = "action:summon_hero_to_player";
    public static final String ACTION_TELEPORT_TO_END = "action:teleport_player_to_end";
    public static final String ACTION_TELEPORT_TO_NETHER = "action:teleport_player_to_nether";
    public static final String ACTION_TELEPORT_TO_OVERWORLD = "action:teleport_player_to_overworld";

    private static final String VANILLA_NAMESPACE = "minecraft";

    private AIGameCommandExecutor() {
    }

    /**
     * 执行一条命令串。返回 {@code CompletableFuture<Boolean>}，{@code true} 表示已提交/成功执行。
     * 会先做权限与安全校验（禁开作弊 / 引用未注册的资源 ID 时拒绝）。
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

            } else if (ACTION_SUMMON_HERO_TO_PLAYER.equals(command)) {
                // 【行为1：AI 传送到玩家身边 / 按需召唤】
                if (mc.hasSingleplayerServer() && mc.getSingleplayerServer() != null) {
                    var server = mc.getSingleplayerServer();
                    server.execute(() -> {
                        try {
                            ServerPlayer serverPlayer = server.getPlayerList().getPlayer(mc.player.getUUID());
                            if (serverPlayer == null) {
                                future.complete(false);
                                return;
                            }
                            boolean success = HeroSummonItem.performSummonOrTeleport(
                                    serverPlayer.serverLevel(), serverPlayer, serverPlayer.position());
                            future.complete(success);
                        } catch (Exception e) {
                            future.complete(false);
                        }
                    });
                } else {
                    // 多人游戏：交给服务端 agent 统一工具链路（hero_summon_to_player 允许无 Hero 时召唤）
                    PacketHandler.sendToServer(new AgentRequestPacket(
                            AgentRequestPacket.KIND_TOOL, HeroSummonTool.ID, "{}", UUID.randomUUID()));
                    future.complete(true);
                }

            } else if (ACTION_TELEPORT_TO_END.equals(command)
                    || ACTION_TELEPORT_TO_NETHER.equals(command)
                    || ACTION_TELEPORT_TO_OVERWORLD.equals(command)) {
                // 【行为：玩家维度传送（末地/下界/主世界）】单机直改，联机走命令。
                performDimensionTeleport(command, future);

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

    /**
     * 维度传送（末地/下界/主世界）。单机直接改玩家所在维度；联机走命令（需要 OP）。
     * 末地传送到黑曜石平台（100, 50, 0）安全落点；主世界/下界保持当前坐标换维度。
     */
    private static void performDimensionTeleport(String action, CompletableFuture<Boolean> future) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.hasSingleplayerServer() && mc.getSingleplayerServer() != null) {
            var server = mc.getSingleplayerServer();
            server.execute(() -> {
                try {
                    ServerPlayer serverPlayer = server.getPlayerList().getPlayer(mc.player.getUUID());
                    if (serverPlayer == null) {
                        future.complete(false);
                        return;
                    }
                    ServerLevel target;
                    if (ACTION_TELEPORT_TO_END.equals(action)) {
                        target = server.getLevel(Level.END);
                    } else if (ACTION_TELEPORT_TO_NETHER.equals(action)) {
                        target = server.getLevel(Level.NETHER);
                    } else {
                        target = server.getLevel(Level.OVERWORLD);
                    }
                    if (target == null) {
                        future.complete(false);
                        return;
                    }
                    if (ACTION_TELEPORT_TO_END.equals(action)) {
                        serverPlayer.teleportTo(target, 100.0D, 50.0D, 0.0D,
                                serverPlayer.getYRot(), serverPlayer.getXRot());
                    } else {
                        serverPlayer.teleportTo(target, serverPlayer.getX(), serverPlayer.getY(), serverPlayer.getZ(),
                                serverPlayer.getYRot(), serverPlayer.getXRot());
                    }
                    future.complete(true);
                } catch (Exception e) {
                    future.complete(false);
                }
            });
        } else {
            if (mc.player == null) {
                future.complete(false);
                return;
            }
            String command;
            if (ACTION_TELEPORT_TO_END.equals(action)) {
                command = "execute in minecraft:the_end run tp @s 100 50 0";
            } else if (ACTION_TELEPORT_TO_NETHER.equals(action)) {
                command = "execute in minecraft:the_nether run tp @s ~ ~ ~";
            } else {
                command = "execute in minecraft:overworld run tp @s ~ ~ ~";
            }
            mc.player.connection.sendCommand(command);
            future.complete(true);
        }
    }

    private static boolean isPermissionBypassAction(String command) {
        if (HeroAIActionPacket.isSupportedAction(command)
                || isExtremePunishmentAction(command)) {
            return true;
        }
        // 玩家传送到自己的 Hero / 让 Hero 来到玩家身边，以及本地确定性层的维度传送，
        // 属于模组物品同等的自主动作，不应要求开启作弊或 OP 权限。
        if (ACTION_TELEPORT_TO_HERO.equals(command)
                || ACTION_SUMMON_HERO_TO_PLAYER.equals(command)
                || ACTION_TELEPORT_TO_END.equals(command)
                || ACTION_TELEPORT_TO_NETHER.equals(command)
                || ACTION_TELEPORT_TO_OVERWORLD.equals(command)) {
            return true;
        }
        String normalized = AIActionIntentInference.normalizeActionInferenceText(command);
        return normalized.startsWith("tp @e[type=" + HerobrineCompanion.MODID + ":hero")
                || normalized.startsWith("tp @s @e[type=" + HerobrineCompanion.MODID + ":hero");
    }

    private static boolean isSafeGeneratedCommand(String command) {
        if (command == null || command.isBlank()) {
            return false;
        }
        String normalized = AIActionIntentInference.normalizeActionInferenceText(command);
        if (ACTION_TELEPORT_TO_HERO.equals(normalized)
                || ACTION_SUMMON_HERO_TO_PLAYER.equals(normalized)
                || ACTION_TELEPORT_TO_END.equals(normalized)
                || ACTION_TELEPORT_TO_NETHER.equals(normalized)
                || ACTION_TELEPORT_TO_OVERWORLD.equals(normalized)
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
            String namespace = matcher.group(1);
            if (VANILLA_NAMESPACE.equals(namespace)) {
                continue;
            }
            // 模组内容只放行“真实注册”的资源：物品/方块/实体/效果/附魔/粒子/音效/属性。
            // 未安装的模组、拼错的 ID、URL 等一律拒绝，防止幻觉 ID 或命令注入。
            if (!MinecraftJavaIdResolver.isRegisteredModResource(matcher.group())) {
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
