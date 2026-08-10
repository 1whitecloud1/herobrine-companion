package com.whitecloud233.modid.herobrine_companion.network;

import com.whitecloud233.modid.herobrine_companion.entity.HeroEntity;
import com.whitecloud233.modid.herobrine_companion.entity.ai.agent.HeroAgent;
import com.whitecloud233.modid.herobrine_companion.item.HeroSummonItem;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * C→S 记忆清理（记忆管理器按钮）：清空 HeroMemory 的叙事层（保留玩家偏好）或全部。
 *
 * <p>用于"玩家改了人设后清掉旧人设下的记忆"，避免历史与当前设定冲突。
 * 目标 Hero 由发送者 UUID 反查；清理后立即落盘，并回发最新（通常为空）摘要
 * 让客户端缓存同步。</p>
 */
public class ClearHeroMemoryPacket {

    /** 只清叙事（episodes），保留玩家偏好 facts。 */
    public static final int MODE_KEEP_FACTS = 0;

    /** 清空全部记忆。 */
    public static final int MODE_CLEAR_ALL = 1;

    private final int mode;

    public ClearHeroMemoryPacket(int mode) {
        this.mode = mode;
    }

    public ClearHeroMemoryPacket(FriendlyByteBuf buf) {
        this.mode = buf.readVarInt();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(this.mode);
    }

    public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        PacketDispatch.enqueueServer(context, () -> {
            ServerPlayer player = context.getSender();
            if (player != null) {
                clear(player, this.mode);
            }
        });
    }

    private static void clear(ServerPlayer player, int mode) {
        if (player.getServer() == null) {
            return;
        }
        HeroEntity hero = HeroSummonItem.findHeroInAnyDimension(player.getServer(), player.getUUID());
        if (hero == null || !hero.isAlive()) {
            return;
        }
        HeroAgent agent = hero.getHeroAgent();
        if (agent != null) {
            agent.clearMemory(hero, mode != MODE_CLEAR_ALL);
        }
        // 清理后回发最新摘要（此时通常为空），让客户端缓存同步。
        HeroAgent cleared = agent;
        PacketHandler.sendToPlayer(new HeroMemoryDigestPacket(
                cleared == null ? "" : cleared.buildMemoryDigest(hero)), player);
    }
}
