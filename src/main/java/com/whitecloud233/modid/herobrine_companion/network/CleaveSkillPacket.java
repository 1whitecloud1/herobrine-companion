package com.whitecloud233.modid.herobrine_companion.network;

import com.whitecloud233.modid.herobrine_companion.item.PoemOfTheEndItem;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class CleaveSkillPacket {
    
    public CleaveSkillPacket() {}

    public CleaveSkillPacket(FriendlyByteBuf buf) {}

    public void encode(FriendlyByteBuf buf) {}

    public boolean handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context ctx = supplier.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player != null) {
                // 【加入 Debug 提示 1】

                if (player.getMainHandItem().getItem() instanceof PoemOfTheEndItem poemItem) {
                    // 【加入 Debug 提示 2】
                    poemItem.triggerWorldCleave(player.serverLevel(), player);
                } else {
                    player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§c[Debug] 服务端错误：你主手里拿的不是镰刀！"));
                }
            }
        });
        ctx.setPacketHandled(true);
        return true;
    }
}