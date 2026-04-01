package com.whitecloud233.modid.herobrine_companion.client.fight.network;

import com.whitecloud233.modid.herobrine_companion.client.fight.HeroChallengeManager;
import com.whitecloud233.modid.herobrine_companion.entity.HeroEntity;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class CPacketCollapseFinished {

    public CPacketCollapseFinished() {}

    public CPacketCollapseFinished(FriendlyByteBuf buf) {}

    public void toBytes(FriendlyByteBuf buf) {}

    public void handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> {
            // 这是服务器收到包后执行的逻辑
            ServerPlayer player = context.getSender();
            if (player != null && player.level() instanceof ServerLevel level) {
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
        });
        context.setPacketHandled(true);
    }
}