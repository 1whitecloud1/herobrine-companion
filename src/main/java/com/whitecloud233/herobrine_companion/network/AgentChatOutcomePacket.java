package com.whitecloud233.herobrine_companion.network;

import com.whitecloud233.herobrine_companion.HerobrineCompanion;
import com.whitecloud233.herobrine_companion.entity.HeroEntity;
import com.whitecloud233.herobrine_companion.entity.ai.agent.HeroAgent;
import com.whitecloud233.herobrine_companion.item.HeroSummonItem;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * C→S 对话结果回流(M4 Phase 1):把客户端一次对话回合的<b>最小摘要</b>
 * (玩家消息 + Herobrine 回复 + 来源类型)送回服务端,供 agent 写入
 * {@code HeroMemory} 情景层,让 agent 真正"记得住"对话内容。
 */
public record AgentChatOutcomePacket(String message, String reply, int kind) implements CustomPacketPayload {

    /** 来源:玩家主动聊天(ClientChatHandler 云端分支)。 */
    public static final int KIND_PLAYER_CHAT = 0;

    /** 来源:角色对话(ActorDialogueService 的赠礼/休息/状态等台词)。 */
    public static final int KIND_ACTOR_DIALOGUE = 1;

    public static final Type<AgentChatOutcomePacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(HerobrineCompanion.MODID, "agent_chat_outcome"));

    public static final StreamCodec<ByteBuf, AgentChatOutcomePacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.stringUtf8(512),
            AgentChatOutcomePacket::message,
            ByteBufCodecs.stringUtf8(1024),
            AgentChatOutcomePacket::reply,
            ByteBufCodecs.VAR_INT,
            AgentChatOutcomePacket::kind,
            AgentChatOutcomePacket::new
    );

    public AgentChatOutcomePacket {
        message = message == null ? "" : message;
        reply = reply == null ? "" : reply;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(AgentChatOutcomePacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            ServerPlayer player = context.player() instanceof ServerPlayer serverPlayer ? serverPlayer : null;
            if (player != null) {
                route(player, packet.message(), packet.reply(), packet.kind());
            }
        });
    }

    private static void route(ServerPlayer player, String message, String reply, int kind) {
        if (player.getServer() == null) {
            return;
        }
        HeroEntity hero = HeroSummonItem.findHeroInAnyDimension(player.getServer(), player.getUUID());
        if (hero == null || !hero.isAlive()) {
            return; // agent 不在时静默丢弃:不回显提示,避免打扰聊天
        }
        HeroAgent agent = hero.getHeroAgent();
        if (agent != null) {
            agent.acceptChatOutcome(hero, message, reply, kind);
        }
    }
}
