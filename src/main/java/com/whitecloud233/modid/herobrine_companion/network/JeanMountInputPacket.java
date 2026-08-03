package com.whitecloud233.modid.herobrine_companion.network;

import com.whitecloud233.modid.herobrine_companion.event.HerobrineFamilyMountEvents;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class JeanMountInputPacket {
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

    public void handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context ctx = supplier.get();
        PacketDispatch.enqueueServer(ctx, () -> {
            ServerPlayer player = ctx.getSender();
            if (player != null) {
                HerobrineFamilyMountEvents.updateInput(player, this.strafe, this.forward, this.jump, this.dismount, this.yaw, this.pitch);
            }
        });
    }
}
