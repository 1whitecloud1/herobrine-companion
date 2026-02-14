package com.whitecloud233.modid.herobrine_companion.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class PeacefulPacket {
    private final boolean peaceful;

    public PeacefulPacket(boolean peaceful) {
        this.peaceful = peaceful;
    }

    public PeacefulPacket(FriendlyByteBuf buf) {
        this.peaceful = buf.readBoolean();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBoolean(this.peaceful);
    }

    public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer serverPlayer = context.getSender();
            if (serverPlayer != null) {
                // 检查前置条件：是否去过维度
                if (!serverPlayer.getPersistentData().getBoolean("HasVisitedHeroDimension")) {
                    serverPlayer.sendSystemMessage(Component.translatable("message.herobrine_companion.hero_not_ready"));
                    return;
                }

                if (this.peaceful) {
                    // === 开启和平 ===
                    serverPlayer.addTag("herobrine_companion_peaceful");
                    
                    // 音效：幻术师准备法术 (神秘感)
                    serverPlayer.level().playSound(null, serverPlayer.blockPosition(), SoundEvents.ILLUSIONER_PREPARE_MIRROR, SoundSource.PLAYERS, 1.0f, 0.8f);
                    
                    // 警告提示：告知玩家不能攻击
                    serverPlayer.sendSystemMessage(Component.translatable("message.herobrine_companion.peace_enabled_warning"));
                    
                } else {
                    // === 关闭和平 ===
                    serverPlayer.removeTag("herobrine_companion_peaceful");
                    
                    // 音效：信标关闭 (机械感/终止感)
                    serverPlayer.level().playSound(null, serverPlayer.blockPosition(), SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 1.0f, 1.0f);
                    
                    serverPlayer.sendSystemMessage(Component.translatable("message.herobrine_companion.peace_disabled"));
                }
            }
        });
        context.setPacketHandled(true);
    }
}
