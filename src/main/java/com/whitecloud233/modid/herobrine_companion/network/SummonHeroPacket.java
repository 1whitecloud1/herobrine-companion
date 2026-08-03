package com.whitecloud233.modid.herobrine_companion.network;

import com.whitecloud233.modid.herobrine_companion.item.HeroSummonItem;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class SummonHeroPacket {

    public SummonHeroPacket() {}

    public SummonHeroPacket(FriendlyByteBuf buf) {}

    public void encode(FriendlyByteBuf buf) {}

    public void handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        PacketDispatch.enqueueServer(context, () -> {
            ServerPlayer player = context.getSender();
            if (player != null && player.level() instanceof ServerLevel serverLevel) {
                // 直接调用你写好的公共召唤/跨维度传送逻辑！
                HeroSummonItem.performSummonOrTeleport(serverLevel, player, player.position());
            }
        });
    }
}