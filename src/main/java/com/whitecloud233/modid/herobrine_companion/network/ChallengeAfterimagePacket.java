package com.whitecloud233.modid.herobrine_companion.network;

import com.whitecloud233.modid.herobrine_companion.fight.HeroAfterimage;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class ChallengeAfterimagePacket {
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

    public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        PacketDispatch.assertClient(context);
        context.enqueueWork(() -> NetworkClientBridge.handleChallengeAfterimage(this));
        context.setPacketHandled(true);
    }
}
