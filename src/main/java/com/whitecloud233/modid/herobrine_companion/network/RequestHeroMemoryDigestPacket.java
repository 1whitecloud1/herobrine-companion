package com.whitecloud233.modid.herobrine_companion.network;

import com.whitecloud233.modid.herobrine_companion.entity.HeroEntity;
import com.whitecloud233.modid.herobrine_companion.entity.ai.agent.HeroAgent;
import com.whitecloud233.modid.herobrine_companion.item.HeroSummonItem;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * C→S 记忆摘要请求：客户端进入聊天 / 需要时向服务端索要 {@code HeroMemory} 的
 * 长期记忆 digest（玩家偏好 + 重要情景 + 框定语），供注入聊天 LLM 上下文。
 *
 * <p><b>服务端权威</b>：目标 Hero 由发送者 UUID 反查；无 Hero 时静默返回空摘要
 * （客户端会缓存空串并周期性重试）。</p>
 */
public class RequestHeroMemoryDigestPacket {

    public RequestHeroMemoryDigestPacket() {
    }

    public RequestHeroMemoryDigestPacket(FriendlyByteBuf buf) {
        // 无载荷：目标由发送者身份决定。
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
            PacketHandler.sendToPlayer(new HeroMemoryDigestPacket(""), player);
            return;
        }
        HeroAgent agent = hero.getHeroAgent();
        String digest = agent == null ? "" : agent.buildMemoryDigest(hero);
        PacketHandler.sendToPlayer(new HeroMemoryDigestPacket(digest), player);
    }
}
