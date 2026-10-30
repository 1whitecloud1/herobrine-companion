package com.whitecloud233.herobrine_companion.network;

import com.whitecloud233.herobrine_companion.HerobrineCompanion;
import com.whitecloud233.herobrine_companion.entity.HeroEntity;
import com.whitecloud233.herobrine_companion.entity.ai.agent.AgentTaskRequestHandler;
import com.whitecloud233.herobrine_companion.entity.ai.agent.HeroAgent;
import com.whitecloud233.herobrine_companion.entity.ai.agent.tool.AgentToolArgs;
import com.whitecloud233.herobrine_companion.entity.ai.agent.tool.HeroSummonTool;
import com.whitecloud233.herobrine_companion.item.HeroSummonItem;
import com.whitecloud233.herobrine_companion.world.structure.ModStructures;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

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
            if (requestId != null) {
                PacketHandler.sendToPlayer(new AgentToolResultPacket(requestId, name, false, "我暂时不在，无法处理"), player);
            }
            return;
        }
        HeroAgent agent = hero.getHeroAgent();

        if (KIND_TOOL.equals(kind) || KIND_TASK.equals(kind)) {
            // 试炼维度里 HeroEntity 刻意跳过 agent.tick（脑冻结，见 HeroEntity#tick 的维度判断），
            // 请求投递后永远不会被消费，客户端只能干等 30 秒拿到"无法确认结果"。
            // 这里直接处理或回执，不让它静默消失。
            if (hero.level().dimension() == ModStructures.END_RING_DIMENSION_KEY) {
                boolean handled = KIND_TOOL.equals(kind) && HeroSummonTool.ID.equals(name)
                        && HeroSummonItem.performSummonOrTeleport(player.serverLevel(), player, player.position());
                if (requestId != null) {
                    PacketHandler.sendToPlayer(new AgentToolResultPacket(requestId, name, handled,
                            handled ? "已传送到你身边" : "我正被困在试炼维度，暂时无法执行该请求"), player);
                }
                return;
            }
        }
        if (KIND_TOOL.equals(kind)) {
            // 显式工具请求直接进有界执行入口（交接 A1）：短动作当 tick 执行并回执，
            // 需要等待的按 FIFO 排队，队满回 busy——不再经过自主分类，也不会互相覆盖。
            agent.submitToolRequest(hero, name, AgentToolArgs.fromJson(argsJson), player.getUUID(), false, requestId);
        } else if (KIND_TASK.equals(kind)) {
            // D1：任务控制（start/status/pause/resume/cancel）由服务端解析扁平参数后操作既有队列，
            // 不再只按 name=scene 规划，也不再默认执行修复。
            AgentToolArgs taskArgs = AgentToolArgs.fromJson(argsJson);
            AgentTaskRequestHandler.Outcome outcome = AgentTaskRequestHandler.handle(hero, taskArgs, name);
            String receipt = outcome.message();
            if ("inspect".equals(name)) {
                receipt += "。inspect 任务只检查异常方块，不查询附近实体。"
                        + "如果玩家只是问附近有什么，请直接使用本轮系统注入的 Current Environment 实体列表作答。";
            }
            PacketHandler.sendToPlayer(new AgentToolResultPacket(requestId, name, outcome.ok(), receipt), player);
        }
    }
}
