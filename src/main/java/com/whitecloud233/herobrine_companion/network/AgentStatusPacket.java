package com.whitecloud233.herobrine_companion.network;

import com.whitecloud233.herobrine_companion.HerobrineCompanion;
import com.whitecloud233.herobrine_companion.client.network.ClientAgentStatus;
import com.whitecloud233.herobrine_companion.entity.ai.agent.AgentStatusSnapshot;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * S→C 状态快照（M5）：把服务端采集的 agent 状态送到客户端可观测面板。
 * 序列化委托给 {@link AgentStatusSnapshot} 自身；客户端落地委托给 {@link ClientAgentStatus}。
 */
public record AgentStatusPacket(AgentStatusSnapshot snapshot) implements CustomPacketPayload {

    public static final Type<AgentStatusPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(HerobrineCompanion.MODID, "agent_status"));

    public static final StreamCodec<FriendlyByteBuf, AgentStatusPacket> STREAM_CODEC =
            StreamCodec.ofMember(AgentStatusPacket::encode, AgentStatusPacket::new);

    public AgentStatusPacket(FriendlyByteBuf buf) {
        this(AgentStatusSnapshot.decode(buf));
    }

    public AgentStatusPacket {
        snapshot = snapshot == null ? AgentStatusSnapshot.empty() : snapshot;
    }

    public void encode(FriendlyByteBuf buf) {
        this.snapshot.encode(buf);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(AgentStatusPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (FMLEnvironment.dist == Dist.CLIENT) {
                ClientAgentStatus.accept(packet.snapshot());
            }
        });
    }
}
