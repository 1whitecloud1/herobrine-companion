package com.whitecloud233.herobrine_companion.network;

import com.whitecloud233.herobrine_companion.HerobrineCompanion;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record PeacefulPacket(boolean peaceful) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<PeacefulPacket> TYPE = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(HerobrineCompanion.MODID, "peaceful"));
    
    public static final StreamCodec<ByteBuf, PeacefulPacket> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.BOOL,
        PeacefulPacket::peaceful,
        PeacefulPacket::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(PeacefulPacket payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer serverPlayer) {
                // 检查前置条件：是否去过维度
                if (!serverPlayer.getPersistentData().getBoolean("HasVisitedHeroDimension")) {
                    serverPlayer.sendSystemMessage(Component.translatable("message.herobrine_companion.hero_not_ready"));
                    return;
                }

                if (payload.peaceful) {
                    // === 开启和平 ===
                    if (!serverPlayer.getTags().contains("herobrine_companion_peaceful")) {
                        serverPlayer.addTag("herobrine_companion_peaceful");
                        
                        // 音效：幻术师准备法术 (神秘感)
                        serverPlayer.level().playSound(null, serverPlayer.blockPosition(), SoundEvents.ILLUSIONER_PREPARE_MIRROR, SoundSource.PLAYERS, 1.0f, 0.8f);
                        
                        // 警告提示：告知玩家不能攻击
                        serverPlayer.sendSystemMessage(Component.translatable("message.herobrine_companion.peace_enabled_warning"));
                    }
                    
                } else {
                    // === 关闭和平 ===
                    if (serverPlayer.getTags().contains("herobrine_companion_peaceful")) {
                        serverPlayer.removeTag("herobrine_companion_peaceful");
                        
                        // 音效：信标关闭 (机械感/终止感)
                        serverPlayer.level().playSound(null, serverPlayer.blockPosition(), SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 1.0f, 1.0f);
                        
                        serverPlayer.sendSystemMessage(Component.translatable("message.herobrine_companion.peace_disabled"));
                    }
                }
            }
        });
    }
}
