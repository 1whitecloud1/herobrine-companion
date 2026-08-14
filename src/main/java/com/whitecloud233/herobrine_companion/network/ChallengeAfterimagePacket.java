package com.whitecloud233.herobrine_companion.network;

import com.whitecloud233.herobrine_companion.client.network.ClientFxHandler;
import com.whitecloud233.herobrine_companion.fight.HeroAfterimage;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public class ChallengeAfterimagePacket implements CustomPacketPayload {
    public static final Type<ChallengeAfterimagePacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("herobrine_companion", "challenge_afterimage"));
    public static final StreamCodec<FriendlyByteBuf, ChallengeAfterimagePacket> STREAM_CODEC =
            StreamCodec.ofMember(ChallengeAfterimagePacket::encode, ChallengeAfterimagePacket::new);

    public final int entityId;
    public final double x;
    public final double y;
    public final double z;
    public final float yRot;
    public final int maxTickCount;
    public final float tickCount;

    public ChallengeAfterimagePacket(int entityId, HeroAfterimage afterimage) {
        this(entityId, afterimage.getPosition().x, afterimage.getPosition().y, afterimage.getPosition().z,
                afterimage.getYRot(), afterimage.getMaxTickCount(), afterimage.getTickCount());
    }

    public ChallengeAfterimagePacket(int entityId, double x, double y, double z, float yRot,
                                     int maxTickCount, float tickCount) {
        this.entityId = entityId;
        this.x = x;
        this.y = y;
        this.z = z;
        this.yRot = yRot;
        this.maxTickCount = maxTickCount;
        this.tickCount = tickCount;
    }

    public ChallengeAfterimagePacket(FriendlyByteBuf buf) {
        this.entityId = buf.readInt();
        this.x = buf.readDouble();
        this.y = buf.readDouble();
        this.z = buf.readDouble();
        this.yRot = buf.readFloat();
        this.maxTickCount = buf.readInt();
        this.tickCount = buf.readFloat();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeInt(this.entityId);
        buf.writeDouble(this.x);
        buf.writeDouble(this.y);
        buf.writeDouble(this.z);
        buf.writeFloat(this.yRot);
        buf.writeInt(this.maxTickCount);
        buf.writeFloat(this.tickCount);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public void handle(IPayloadContext context) {
        context.enqueueWork(() -> ClientFxHandler.handleChallengeAfterimage(this));
    }
}
