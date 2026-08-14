package com.whitecloud233.herobrine_companion.fight.network;

import com.whitecloud233.herobrine_companion.client.fight.HeroChallengeManager;
import com.whitecloud233.herobrine_companion.entity.HeroEntity;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record CPacketCollapseFinished() implements CustomPacketPayload {

    // 1.21.1 必须定义唯一的 Payload Type
    public static final Type<CPacketCollapseFinished> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("herobrine_companion", "collapse_finished"));

    // 使用 StreamCodec.unit() 完美处理无需传输数据的空数据包
    public static final StreamCodec<FriendlyByteBuf, CPacketCollapseFinished> STREAM_CODEC = StreamCodec.unit(new CPacketCollapseFinished());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    // 1.21.1 的处理逻辑入口，使用 IPayloadContext
    public void handle(IPayloadContext context) {
        context.enqueueWork(() -> {
            // 这是服务器收到包后执行的逻辑
            if (context.player() instanceof ServerPlayer player) {
                if (player.level() instanceof ServerLevel level) {
                    // 找到正在和这个玩家对峙的处于“假死阶段”的 Herobrine
                    for (var entity : level.getAllEntities()) {
                        if (entity instanceof HeroEntity hero && hero.getPersistentData().getBoolean("IsFakeOutPhase")) {
                            // 清除假死计时器
                            hero.getPersistentData().remove("FakeOutTimer");
                            // 真正的胜利结算：传回主世界，重置场地，播放胜利音效！
                            HeroChallengeManager.endChallenge(hero, true);
                            break;
                        }
                    }
                }
            }
        });
    }
}