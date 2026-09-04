package com.whitecloud233.modid.herobrine_companion.network;

import com.whitecloud233.modid.herobrine_companion.entity.HeroEntity;
import com.whitecloud233.modid.herobrine_companion.entity.ai.agent.AgentFrame;
import com.whitecloud233.modid.herobrine_companion.entity.ai.agent.AgentIntention;
import com.whitecloud233.modid.herobrine_companion.entity.ai.agent.AgentIntentionClassifier.Classification;
import com.whitecloud233.modid.herobrine_companion.entity.ai.agent.HeroAgent;
import com.whitecloud233.modid.herobrine_companion.entity.ai.agent.task.PlannedTask;
import com.whitecloud233.modid.herobrine_companion.entity.ai.agent.tool.AgentToolArgs;
import com.whitecloud233.modid.herobrine_companion.entity.ai.agent.tool.HeroSummonTool;
import com.whitecloud233.modid.herobrine_companion.item.HeroSummonItem;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * C→S 桥（M4）：把客户端 LLM 对话发出的 agent 请求转交服务端 Hero 的 agent 循环。
 *
 * <p>承载两种指令（单包）：</p>
 * <ul>
 *   <li>{@code kind="tool"} → {@code name=toolId} + {@code argsJson} → {@link HeroAgent#requestTool}；
 *       服务端经统一工具注册表执行（白名单/校验/确认/审计）。</li>
 *   <li>{@code kind="task"} → {@code name=scene}(repair/inspect) → 任务规划器生成序列 → {@link HeroAgent#requestTasks}。</li>
 * </ul>
 *
 * <p>{@code requestId} 由客户端生成，随包往返：服务端执行工具后经
 * {@link AgentToolResultPacket} 把结果送回，客户端据此关联（M4 共享基础设施）。</p>
 *
 * <p>服务端权威：仅转发请求，实际校验与执行全在服务端 agent 内完成。</p>
 */
public class AgentRequestPacket {

    public static final String KIND_TOOL = "tool";
    public static final String KIND_TASK = "task";

    private final String kind;
    private final String name;
    private final String argsJson;
    private final UUID requestId;

    public AgentRequestPacket(String kind, String name, String argsJson, UUID requestId) {
        this.kind = kind == null ? "" : kind;
        this.name = name == null ? "" : name;
        this.argsJson = argsJson == null ? "" : argsJson;
        this.requestId = requestId == null ? UUID.randomUUID() : requestId;
    }

    public AgentRequestPacket(FriendlyByteBuf buf) {
        this.kind = buf.readUtf(16);
        this.name = buf.readUtf(64);
        this.argsJson = buf.readUtf(2048);
        this.requestId = buf.readUUID();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(this.kind, 16);
        buf.writeUtf(this.name, 64);
        buf.writeUtf(this.argsJson, 2048);
        buf.writeUUID(this.requestId);
    }

    public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        PacketDispatch.enqueueServer(context, () -> {
            ServerPlayer player = context.getSender();
            if (player != null) {
                route(player, this.kind, this.name, this.argsJson, this.requestId);
            }
        });
    }

    private static void route(ServerPlayer player, String kind, String name, String argsJson, UUID requestId) {
        if (player.getServer() == null) {
            return;
        }
        HeroEntity hero = HeroSummonItem.findHeroInAnyDimension(player.getServer(), player.getUUID());
        if (hero == null || !hero.isAlive()) {
            // hero_summon_to_player 是“召唤/传送到玩家身边”工具：即使当前没有存活 Hero，
            // 也应该允许它执行（会按需召唤新 Hero），而不是直接判定“无法处理”。
            if ((hero == null || !hero.isAlive()) && KIND_TOOL.equals(kind) && HeroSummonTool.ID.equals(name)) {
                boolean success = HeroSummonItem.performSummonOrTeleport(player.serverLevel(), player, player.position());
                if (requestId != null) {
                    PacketHandler.sendToPlayer(new AgentToolResultPacket(
                            requestId, name, success,
                            success ? "已传送到你身边" : "传送失败"), player);
                }
                return;
            }
            player.sendSystemMessage(Component.translatable("message.herobrine_companion.agent.not_available"));
            // P2：带 requestId 的工具请求补发失败结果，避免客户端等待结果包而悬挂。
            if (KIND_TOOL.equals(kind) && requestId != null) {
                PacketHandler.sendToPlayer(new AgentToolResultPacket(requestId, name, false, "我暂时不在，无法处理"), player);
            }
            return;
        }
        HeroAgent agent = hero.getHeroAgent();

        if (KIND_TOOL.equals(kind)) {
            agent.requestTool(name, AgentToolArgs.fromJson(argsJson), player.getUUID(), false, requestId);
        } else if (KIND_TASK.equals(kind)) {
            AgentIntention intention = switch (name) {
                case "repair" -> AgentIntention.REMEDIATE;
                case "inspect" -> AgentIntention.INVESTIGATE;
                default -> AgentIntention.REMEDIATE;
            };
            AgentFrame frame = AgentFrame.capture(hero);
            List<PlannedTask> tasks = agent.taskPlanner().plan(frame, Classification.world(intention, "LLM 对话触发"));
            agent.requestTasks(tasks);
        }
    }
}