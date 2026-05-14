package com.whitecloud233.herobrine_companion.event;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.whitecloud233.herobrine_companion.HerobrineCompanion;
import com.whitecloud233.herobrine_companion.entity.logic.data.HeroCrossChatManager;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

@EventBusSubscriber(modid = HerobrineCompanion.MODID)
public class HeroCrossChatCommands {
    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("hbchat")
                .then(Commands.literal("request")
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(context -> {
                                    ServerPlayer self = context.getSource().getPlayerOrException();
                                    ServerPlayer target = EntityArgument.getPlayer(context, "player");
                                    HeroCrossChatManager.INSTANCE.requestSession(self, target);
                                    return 1;
                                })))
                .then(Commands.literal("accept")
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(context -> {
                                    ServerPlayer self = context.getSource().getPlayerOrException();
                                    ServerPlayer requester = EntityArgument.getPlayer(context, "player");
                                    HeroCrossChatManager.INSTANCE.respondToRequest(self, requester, true);
                                    return 1;
                                })))
                .then(Commands.literal("deny")
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(context -> {
                                    ServerPlayer self = context.getSource().getPlayerOrException();
                                    ServerPlayer requester = EntityArgument.getPlayer(context, "player");
                                    HeroCrossChatManager.INSTANCE.respondToRequest(self, requester, false);
                                    return 1;
                                })))
                .then(Commands.literal("send")
                        .then(Commands.argument("message", StringArgumentType.greedyString())
                                .executes(context -> {
                                    ServerPlayer self = context.getSource().getPlayerOrException();
                                    HeroCrossChatManager.INSTANCE.sendPlayerMessage(self, StringArgumentType.getString(context, "message"));
                                    return 1;
                                })))
                .then(Commands.literal("hb")
                        .executes(context -> {
                            HeroCrossChatManager.INSTANCE.openPersistentHbChat(context.getSource().getPlayerOrException());
                            return 1;
                        })
                        .then(Commands.argument("message", StringArgumentType.greedyString())
                                .executes(context -> {
                                    ServerPlayer self = context.getSource().getPlayerOrException();
                                    HeroCrossChatManager.INSTANCE.sendHbToHbMessage(self, StringArgumentType.getString(context, "message"));
                                    return 1;
                                })))
                .then(Commands.literal("auto")
                        .then(Commands.literal("on")
                                .executes(context -> {
                                    HeroCrossChatManager.INSTANCE.setAutoHbConversation(context.getSource().getPlayerOrException(), true);
                                    return 1;
                                }))
                        .then(Commands.literal("off")
                                .executes(context -> {
                                    HeroCrossChatManager.INSTANCE.setAutoHbConversation(context.getSource().getPlayerOrException(), false);
                                    return 1;
                                })))
                .then(Commands.literal("chat")
                        .executes(context -> {
                            HeroCrossChatManager.INSTANCE.openPersistentChat(context.getSource().getPlayerOrException());
                            return 1;
                        }))
                .then(Commands.literal("close")
                        .executes(context -> {
                            HeroCrossChatManager.INSTANCE.closeSession(context.getSource().getPlayerOrException());
                            return 1;
                        }))
                .then(Commands.literal("info")
                        .executes(context -> {
                            HeroCrossChatManager.INSTANCE.sendInfo(context.getSource().getPlayerOrException());
                            return 1;
                        }))
                .executes(context -> {
                    HeroCrossChatManager.INSTANCE.sendInfo(context.getSource().getPlayerOrException());
                    return 1;
                }));
    }
}

