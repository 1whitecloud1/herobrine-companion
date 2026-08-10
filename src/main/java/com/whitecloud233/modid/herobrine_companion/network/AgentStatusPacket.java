package com.whitecloud233.modid.herobrine_companion.network;

import com.whitecloud233.modid.herobrine_companion.entity.ai.agent.AgentStatusSnapshot;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * S→C 状态快照（M5）：把服务端采集的 agent 状态送到客户端可观测面板。
 *
 * <p><b>单一职责</b>：只做搬运。序列化委托给 {@link AgentStatusSnapshot} 自身，
 * 客户端落地委托给 {@link NetworkClientBridge}（唯一 DistExecutor 边界）。</p>
 */
public class AgentStatusPacket {

    private final AgentStatusSnapshot snapshot;

    public AgentStatusPacket(AgentStatusSnapshot snapshot) {
        this.snapshot = snapshot == null ? AgentStatusSnapshot.empty() : snapshot;
    }

    public AgentStatusPacket(FriendlyByteBuf buf) {
        this.snapshot = AgentStatusSnapshot.decode(buf);
    }

    public void encode(FriendlyByteBuf buf) {
        this.snapshot.encode(buf);
    }

    public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        PacketDispatch.assertClient(context);
        // snapshot 是不可变记录（列表在构造期 List.copyOf），跨线程无需再做防御性拷贝。
        context.enqueueWork(() -> NetworkClientBridge.acceptAgentStatus(this.snapshot));
        context.setPacketHandled(true);
    }
}
