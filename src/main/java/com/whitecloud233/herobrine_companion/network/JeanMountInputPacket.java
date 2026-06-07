package com.whitecloud233.herobrine_companion.network;

import com.whitecloud233.herobrine_companion.HerobrineCompanion;
import com.whitecloud233.herobrine_companion.event.HerobrineFamilyMountEvents;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public class JeanMountInputPacket implements CustomPacketPayload {
    public static final Type<JeanMountInputPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(HerobrineCompanion.MODID, "jean_mount_input"));
    public static final StreamCodec<FriendlyByteBuf, JeanMountInputPacket> STREAM_CODEC =
            StreamCodec.ofMember(JeanMountInputPacket::encode, JeanMountInputPacket::new);

    private final float strafe;
    private final float forward;
    private final boolean jump;
    private final boolean dismount;
    private final float yaw;
    private final float pitch;

    public JeanMountInputPacket(float strafe, float forward, boolean jump, boolean dismount, float yaw, float pitch) {
        this.strafe = strafe;
        this.forward = forward;
        this.jump = jump;
        this.dismount = dismount;
        this.yaw = yaw;
        this.pitch = pitch;
    }

    public JeanMountInputPacket(FriendlyByteBuf buf) {
        this.strafe = buf.readFloat();
        this.forward = buf.readFloat();
        this.jump = buf.readBoolean();
        this.dismount = buf.readBoolean();
        this.yaw = buf.readFloat();
        this.pitch = buf.readFloat();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeFloat(this.strafe);
        buf.writeFloat(this.forward);
        buf.writeBoolean(this.jump);
        buf.writeBoolean(this.dismount);
        buf.writeFloat(this.yaw);
        buf.writeFloat(this.pitch);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(JeanMountInputPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                HerobrineFamilyMountEvents.updateInput(player, packet.strafe, packet.forward,
                        packet.jump, packet.dismount, packet.yaw, packet.pitch);
            }
        });
    }
}
