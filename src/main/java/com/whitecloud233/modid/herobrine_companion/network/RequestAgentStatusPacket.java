package com.whitecloud233.modid.herobrine_companion.network;

import com.whitecloud233.modid.herobrine_companion.entity.HeroEntity;
import com.whitecloud233.modid.herobrine_companion.entity.ai.agent.AgentStatusCollector;
import com.whitecloud233.modid.herobrine_companion.entity.ai.agent.AgentStatusSnapshot;
import com.whitecloud233.modid.herobrine_companion.item.HeroSummonItem;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * C→S 状态查询（M5）：客户端请求自己绑定 Hero 的 agent 状态快照。
 *
 * <p><b>单一职责</b>：只做"校验所有权 → 委托采集 → 回传"。不自行读取 agent 内部结构
 * （交给 {@link AgentStatusCollector}），不构造 UI。</p>
 *
 * <p><b>安全</b>：包体无载荷 —— 目标 Hero 由发送者 UUID 在服务端反查，客户端无法指定他人的
 * Hero，天然满足设计文档 §6"所有权校验 / 动作只作用于所有者上下文"。</p>
 */
public class RequestAgentStatusPacket {

    public RequestAgentStatusPacket() {
    }

    public RequestAgentStatusPacket(FriendlyByteBuf buf) {
        // 无载荷：目标由发送者身份决定，不接受客户端指定。
    }

    public void encode(FriendlyByteBuf buf) {
        // 无载荷。
    }

    public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        PacketDispatch.enqueueServer(context, () -> {
            ServerPlayer player = context.getSender();
            if (player != null) {
                respond(player);
            }
        });
    }

    private static void respond(ServerPlayer player) {
        if (player.getServer() == null) {
            return;
        }
        HeroEntity hero = HeroSummonItem.findHeroInAnyDimension(player.getServer(), player.getUUID());
        if (hero == null || !hero.isAlive()) {
            player.sendSystemMessage(Component.translatable("message.herobrine_companion.agent.not_available"));
            return;
        }
        AgentStatusSnapshot snapshot = AgentStatusCollector.collect(hero, hero.getHeroAgent());
        PacketHandler.sendToPlayer(new AgentStatusPacket(snapshot), player);
    }
}
