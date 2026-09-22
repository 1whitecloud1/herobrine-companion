package com.whitecloud233.modid.herobrine_companion.network;

import com.whitecloud233.modid.herobrine_companion.client.ClientQuestState;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * 单一职责：S→C 同步玩家委托状态：
 * <ul>
 *   <li>当前进行中的委托 ID（0 = 无）——供“手持交付物品右键 Hero”开屏拦截；</li>
 *   <li>各委托的重复接取冷却结束时刻（世界游戏刻）——供界面锁定展示。</li>
 * </ul>
 * 真正的接受 / 交付 / 冷却校验始终由服务端权威执行。
 */
public class QuestStateSyncPacket {

    private final int activeQuestId;
    private final Map<Integer, Long> cooldownEnds;

    public QuestStateSyncPacket(int activeQuestId, Map<Integer, Long> cooldownEnds) {
        this.activeQuestId = activeQuestId;
        this.cooldownEnds = new LinkedHashMap<>(cooldownEnds);
    }

    public QuestStateSyncPacket(FriendlyByteBuf buf) {
        this.activeQuestId = buf.readInt();
        int count = buf.readVarInt();
        this.cooldownEnds = new LinkedHashMap<>();
        for (int i = 0; i < count; i++) {
            this.cooldownEnds.put(buf.readInt(), buf.readLong());
        }
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeInt(this.activeQuestId);
        buf.writeVarInt(this.cooldownEnds.size());
        for (Map.Entry<Integer, Long> entry : this.cooldownEnds.entrySet()) {
            buf.writeInt(entry.getKey());
            buf.writeLong(entry.getValue());
        }
    }

    public static void handle(QuestStateSyncPacket packet, Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        PacketDispatch.assertClient(context);
        context.enqueueWork(() -> ClientQuestState.setQuestState(packet.activeQuestId, packet.cooldownEnds));
        context.setPacketHandled(true);
    }
}