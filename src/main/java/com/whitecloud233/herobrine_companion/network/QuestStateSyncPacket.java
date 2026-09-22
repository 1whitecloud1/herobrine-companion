package com.whitecloud233.herobrine_companion.network;

import com.whitecloud233.herobrine_companion.HerobrineCompanion;
import com.whitecloud233.herobrine_companion.client.ClientQuestState;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.HashMap;
import java.util.Map;

/**
 * 单一职责：S→C 同步玩家委托状态：
 * <ul>
 *   <li>当前进行中的委托 ID（0 = 无）——供“手持交付物品右键 Hero”开屏拦截；</li>
 *   <li>各委托的重复接取冷却结束时刻（世界游戏刻）——供界面锁定展示。</li>
 * </ul>
 * 真正的接受 / 交付 / 冷却校验始终由服务端权威执行。
 */
public record QuestStateSyncPacket(int activeQuestId, Map<Integer, Long> cooldownEnds) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<QuestStateSyncPacket> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(HerobrineCompanion.MODID, "quest_state_sync"));

    /** 1.21.1 ByteBufCodecs 没有 LONG 常量，用原始读写兜底。 */
    private static final StreamCodec<FriendlyByteBuf, Long> LONG_CODEC =
            StreamCodec.of(FriendlyByteBuf::writeLong, FriendlyByteBuf::readLong);

    public static final StreamCodec<RegistryFriendlyByteBuf, QuestStateSyncPacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.INT, QuestStateSyncPacket::activeQuestId,
            ByteBufCodecs.map(HashMap::new, ByteBufCodecs.INT, LONG_CODEC), QuestStateSyncPacket::cooldownEnds,
            QuestStateSyncPacket::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(QuestStateSyncPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> ClientQuestState.setQuestState(packet.activeQuestId, packet.cooldownEnds));
    }
}