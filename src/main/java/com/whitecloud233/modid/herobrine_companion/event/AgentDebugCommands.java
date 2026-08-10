package com.whitecloud233.modid.herobrine_companion.event;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.whitecloud233.modid.herobrine_companion.HerobrineCompanion;
import com.whitecloud233.modid.herobrine_companion.entity.HeroEntity;
import com.whitecloud233.modid.herobrine_companion.entity.ai.agent.AgentFrame;
import com.whitecloud233.modid.herobrine_companion.entity.ai.agent.AgentIntention;
import com.whitecloud233.modid.herobrine_companion.entity.ai.agent.AgentIntentionClassifier.Classification;
import com.whitecloud233.modid.herobrine_companion.entity.ai.agent.AgentStatusCollector;
import com.whitecloud233.modid.herobrine_companion.entity.ai.agent.AgentStatusSnapshot;
import com.whitecloud233.modid.herobrine_companion.entity.ai.agent.HeroAgent;
import com.whitecloud233.modid.herobrine_companion.entity.ai.learning.HeroBrain;
import com.whitecloud233.modid.herobrine_companion.entity.ai.agent.task.PlannedTask;
import com.whitecloud233.modid.herobrine_companion.network.AgentStatusPacket;
import com.whitecloud233.modid.herobrine_companion.network.PacketHandler;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Agent 调试命令：
 * <ul>
 *   <li>{@code /hbagent task <repair|inspect|clear>} —— M3 端到端验证：把一段演示任务序列注入
 *       发送者绑定 Hero 的任务队列，便于在游戏内观察队列推进（导航 → 粒子 → 播报）。</li>
 *   <li>{@code /hbagent status} —— M5 可观测入口：推送一份 agent 状态快照到发送者客户端面板。</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = HerobrineCompanion.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class AgentDebugCommands {

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(Commands.literal("hbagent")
                .then(Commands.literal("task")
                        .then(Commands.argument("scene", StringArgumentType.word())
                                .executes(context -> {
                                    ServerPlayer player = context.getSource().getPlayerOrException();
                                    String scene = StringArgumentType.getString(context, "scene").toLowerCase(Locale.ROOT);
                                    return runTaskScene(player, scene);
                                })))
                .then(Commands.literal("status")
                        .executes(context -> {
                            ServerPlayer player = context.getSource().getPlayerOrException();
                            return sendStatus(player);
                        })));
    }

    /**
     * 采集并推送一份 agent 状态快照到发送者客户端（M5 可观测面板入口）。
     *
     * <p>服务端只负责"给数据"，是否弹面板由客户端决定，因此本方法不引用任何 GUI 类。</p>
     */
    private static int sendStatus(ServerPlayer player) {
        HeroEntity hero = findBoundHero(player.getUUID());
        if (hero == null) {
            player.sendSystemMessage(Component.translatable("message.herobrine_companion.agent_debug.no_hero"));
            return 0;
        }
        AgentStatusSnapshot snapshot = AgentStatusCollector.collect(hero, hero.getHeroAgent());
        PacketHandler.sendToPlayer(new AgentStatusPacket(snapshot), player);
        player.sendSystemMessage(Component.translatable("message.herobrine_companion.agent_debug.last_decision", snapshot.lastDecision()));
        return 1;
    }

    private static int runTaskScene(ServerPlayer player, String scene) {
        HeroEntity hero = findBoundHero(player.getUUID());
        if (hero == null) {
            player.sendSystemMessage(Component.translatable("message.herobrine_companion.agent_debug.no_hero"));
            return 0;
        }
        HeroAgent agent = hero.getHeroAgent();

        switch (scene) {
            case "clear" -> {
                agent.taskQueue().clear();
                player.sendSystemMessage(Component.translatable("message.herobrine_companion.agent_debug.queue_cleared"));
                return 1;
            }
            case "repair", "inspect" -> {
                AgentIntention intention = "repair".equals(scene)
                        ? AgentIntention.REMEDIATE : AgentIntention.INVESTIGATE;
                AgentFrame frame = AgentFrame.capture(hero);
                Classification classification = Classification.world(intention, "debug 命令触发");
                List<PlannedTask> tasks = agent.taskPlanner().plan(frame, classification);
                if (tasks.isEmpty()) {
                    player.sendSystemMessage(Component.translatable("message.herobrine_companion.agent_debug.scene_no_tasks"));
                    return 0;
                }
                agent.requestTasks(tasks);
                player.sendSystemMessage(Component.translatable("message.herobrine_companion.agent_debug.queued_tasks", tasks.size(), scene));
                return 1;
            }
            default -> {
                player.sendSystemMessage(Component.translatable("message.herobrine_companion.agent_debug.invalid_scene"));
                return 0;
            }
        }
    }

    /** 在全局活跃 Hero 缓存里找主人的绑定 Hero。 */
    private static HeroEntity findBoundHero(UUID ownerUuid) {
        for (HeroEntity hero : HeroBrain.ACTIVE_HEROES) {
            if (ownerUuid.equals(hero.getOwnerUUID())) {
                return hero;
            }
        }
        return null;
    }
}