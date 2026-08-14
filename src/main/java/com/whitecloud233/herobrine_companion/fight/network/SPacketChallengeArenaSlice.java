package com.whitecloud233.herobrine_companion.fight.network;

import com.whitecloud233.herobrine_companion.client.network.ClientFxHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public class SPacketChallengeArenaSlice implements CustomPacketPayload {
    public static final Type<SPacketChallengeArenaSlice> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("herobrine_companion", "challenge_arena_slice"));
    public static final StreamCodec<FriendlyByteBuf, SPacketChallengeArenaSlice> STREAM_CODEC =
            StreamCodec.ofMember(SPacketChallengeArenaSlice::encode, SPacketChallengeArenaSlice::new);

    public final Vec3 center;
    public final Vec3 capNormal;
    public final Vec3 cutDirection;
    public final float cutOffset;
    public final float arenaRadius;
    public final int fallTicks;
    public final int holdTicks;
    public final boolean flash;

    public SPacketChallengeArenaSlice(Vec3 center, Vec3 capNormal, Vec3 cutDirection, float cutOffset, float arenaRadius, int fallTicks, int holdTicks, boolean flash) {
        this.center = center;
        this.capNormal = capNormal;
        this.cutDirection = cutDirection;
        this.cutOffset = cutOffset;
        this.arenaRadius = arenaRadius;
        this.fallTicks = fallTicks;
        this.holdTicks = holdTicks;
        this.flash = flash;
    }

    public SPacketChallengeArenaSlice(FriendlyByteBuf buf) {
        this.center = new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
        this.capNormal = new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
        this.cutDirection = new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
        this.cutOffset = buf.readFloat();
        this.arenaRadius = buf.readFloat();
        this.fallTicks = buf.readVarInt();
        this.holdTicks = buf.readVarInt();
        this.flash = buf.readBoolean();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeDouble(this.center.x);
        buf.writeDouble(this.center.y);
        buf.writeDouble(this.center.z);
        buf.writeDouble(this.capNormal.x);
        buf.writeDouble(this.capNormal.y);
        buf.writeDouble(this.capNormal.z);
        buf.writeDouble(this.cutDirection.x);
        buf.writeDouble(this.cutDirection.y);
        buf.writeDouble(this.cutDirection.z);
        buf.writeFloat(this.cutOffset);
        buf.writeFloat(this.arenaRadius);
        buf.writeVarInt(this.fallTicks);
        buf.writeVarInt(this.holdTicks);
        buf.writeBoolean(this.flash);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public void handle(IPayloadContext context) {
        context.enqueueWork(() -> ClientFxHandler.handleChallengeArenaSlice(this));
    }
}
