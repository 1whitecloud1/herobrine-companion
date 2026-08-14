package com.whitecloud233.herobrine_companion.network;

import com.whitecloud233.herobrine_companion.HerobrineCompanion;
import com.whitecloud233.herobrine_companion.entity.HeroEntity;
import com.whitecloud233.herobrine_companion.entity.ai.agent.AgentFrame;
import com.whitecloud233.herobrine_companion.entity.ai.agent.AgentIntention;
import com.whitecloud233.herobrine_companion.entity.ai.agent.AgentIntentionClassifier.Classification;
import com.whitecloud233.herobrine_companion.entity.ai.agent.HeroAgent;
import com.whitecloud233.herobrine_companion.entity.ai.agent.task.PlannedTask;
import com.whitecloud233.herobrine_companion.entity.ai.agent.tool.AgentToolArgs;
import com.whitecloud233.herobrine_companion.item.HeroSummonItem;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.List;
import java.util.UUID;

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
 */
public record AgentRequestPacket(String kind, String name, String argsJson, UUID requestId) implements CustomPacketPayload {

    public static final String KIND_TOOL = "tool";
    public static final String KIND_TASK = "task";

    public static final Type<AgentRequestPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(HerobrineCompanion.MODID, "agent_request"));

    private static final StreamCodec<FriendlyByteBuf, UUID> UUID_STREAM_CODEC =
            StreamCodec.<FriendlyByteBuf, UUID>of((buf, uuid) -> buf.writeUUID(uuid), buf -> buf.readUUID());

    public static final StreamCodec<FriendlyByteBuf, AgentRequestPacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.stringUtf8(16),
            AgentRequestPacket::kind,
            ByteBufCodecs.stringUtf8(64),
            AgentRequestPacket::name,
            ByteBufCodecs.stringUtf8(2048),
            AgentRequestPacket::argsJson,
            UUID_STREAM_CODEC,
            AgentRequestPacket::requestId,
            AgentRequestPacket::new
    );

    public AgentRequestPacket {
        kind = kind == null ? "" : kind;
        name = name == null ? "" : name;
        argsJson = argsJson == null ? "" : argsJson;
        requestId = requestId == null ? UUID.randomUUID() : requestId;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(AgentRequestPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            ServerPlayer player = context.player() instanceof ServerPlayer serverPlayer ? serverPlayer : null;
            if (player != null) {
                route(player, packet.kind(), packet.name(), packet.argsJson(), packet.requestId());
            }
        });
    }

    private static void route(ServerPlayer player, String kind, String name, String argsJson, UUID requestId) {
        if (player.getServer() == null) {
            return;
        }
        HeroEntity hero = HeroSummonItem.findHeroInAnyDimension(player.getServer(), player.getUUID());
        if (hero == null || !hero.isAlive()) {
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
