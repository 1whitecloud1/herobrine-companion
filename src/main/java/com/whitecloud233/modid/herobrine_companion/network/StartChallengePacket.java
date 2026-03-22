package com.whitecloud233.modid.herobrine_companion.network;

import com.whitecloud233.modid.herobrine_companion.client.fight.HeroChallengeManager;
import com.whitecloud233.modid.herobrine_companion.entity.HeroEntity;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class StartChallengePacket {
    private final int entityId;
    private final int challengeMode; // 0 = 简单, 1 = 普通, 2 = 困难

    public StartChallengePacket(int entityId, int challengeMode) {
        this.entityId = entityId;
        this.challengeMode = challengeMode;
    }

    public StartChallengePacket(FriendlyByteBuf buffer) {
        this.entityId = buffer.readInt();
        this.challengeMode = buffer.readInt();
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeInt(this.entityId);
        buffer.writeInt(this.challengeMode);
    }

    public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null) return;

            ServerLevel level = player.serverLevel();
            Entity entity = level.getEntity(this.entityId);

            // 在 StartChallengePacket 的 handle 方法内部：
            if (entity instanceof HeroEntity hero) {
                // 调用管理器正式开启 Boss 战
                // [修复] 将 payload.challengeMode() 改为 this.challengeMode
                HeroChallengeManager.startChallenge(hero, player, this.challengeMode);
            }
        });
        context.setPacketHandled(true);
    }
}