package com.whitecloud233.herobrine_companion.network.ai;

import com.whitecloud233.herobrine_companion.HerobrineCompanion;
import com.whitecloud233.herobrine_companion.entity.dialogue.SpeechBubbleAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record SyncSpeechBubblePacket(int entityId, String text, int durationTicks) implements CustomPacketPayload {
    public static final Type<SyncSpeechBubblePacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(HerobrineCompanion.MODID, "sync_speech_bubble"));
    public static final StreamCodec<FriendlyByteBuf, SyncSpeechBubblePacket> STREAM_CODEC =
            StreamCodec.ofMember(SyncSpeechBubblePacket::encode, SyncSpeechBubblePacket::new);

    public SyncSpeechBubblePacket(FriendlyByteBuf buf) {
        this(buf.readVarInt(), buf.readUtf(256), buf.readVarInt());
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(this.entityId);
        buf.writeUtf(this.text == null ? "" : this.text, 256);
        buf.writeVarInt(this.durationTicks);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SyncSpeechBubblePacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (FMLEnvironment.dist == Dist.CLIENT) {
                handleOnClient(packet);
            }
        });
    }

    private static void handleOnClient(SyncSpeechBubblePacket packet) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }

        Entity entity = minecraft.level.getEntity(packet.entityId);
        if (!(entity instanceof LivingEntity livingEntity) || !(livingEntity instanceof SpeechBubbleAccessor accessor)) {
            return;
        }

        String text = packet.text == null ? "" : packet.text.trim();
        if (text.isEmpty() || packet.durationTicks <= 0) {
            accessor.herobrineCompanion$clearSpeechBubble();
            return;
        }

        accessor.herobrineCompanion$showSpeechBubble(Component.literal(text), packet.durationTicks);
    }
}
