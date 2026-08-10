package com.whitecloud233.modid.herobrine_companion.network;

import com.whitecloud233.modid.herobrine_companion.entity.HeroEntity;
import com.whitecloud233.modid.herobrine_companion.entity.ai.agent.HeroAgent;
import com.whitecloud233.modid.herobrine_companion.item.HeroSummonItem;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * C→S 对话结果回流(M4 Phase 1):把客户端一次对话回合的<b>最小摘要</b>
 * (玩家消息 + Herobrine 回复 + 来源类型)送回服务端,供 agent 写入
 * {@code HeroMemory} 情景层,让 agent 真正"记得住"对话内容。
 *
 * <p><b>服务端权威 / 所有权校验</b>:目标 Hero 由发送者 UUID 反查,不接受客户端指定
 * 他人 Hero;只写记忆、不改变世界状态,因此失败静默(不打扰聊天)。</p>
 *
 * <p><b>隐私与 token</b>:只带"一句话消息 + 一句话回复",不传完整会话;
 * 服务端仅存蒸馏条目({@code HeroMemory.rememberEvent}),由既有
 * {@code DefaultAgentReflector} 每 1200 tick 周期落盘到 {@code HeroWorldData}。</p>
 */
public class AgentChatOutcomePacket {

    /** 来源:玩家主动聊天(ClientChatHandler 云端分支)。 */
    public static final int KIND_PLAYER_CHAT = 0;

    /** 来源:角色对话(ActorDialogueService 的赠礼/休息/状态等台词)。 */
    public static final int KIND_ACTOR_DIALOGUE = 1;

    private final String message;
    private final String reply;
    private final int kind;

    public AgentChatOutcomePacket(String message, String reply, int kind) {
        this.message = message == null ? "" : message;
        this.reply = reply == null ? "" : reply;
        this.kind = kind;
    }

    public AgentChatOutcomePacket(FriendlyByteBuf buf) {
        this.message = buf.readUtf(512);
        this.reply = buf.readUtf(1024);
        this.kind = buf.readVarInt();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(this.message, 512);
        buf.writeUtf(this.reply, 1024);
        buf.writeVarInt(this.kind);
    }

    public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        PacketDispatch.enqueueServer(context, () -> {
            ServerPlayer player = context.getSender();
            if (player != null) {
                route(player, this.message, this.reply, this.kind);
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
