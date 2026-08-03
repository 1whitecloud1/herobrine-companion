package com.whitecloud233.modid.herobrine_companion.network;

import com.whitecloud233.modid.herobrine_companion.entity.HeroEntity;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class SavePosePacket {
    private final int entityId;
    private final boolean isPosing;
    private final float[][] angles;

    public SavePosePacket(int entityId, boolean isPosing, float[][] angles) {
        this.entityId = entityId;
        this.isPosing = isPosing;
        this.angles = angles;
    }

    public SavePosePacket(FriendlyByteBuf buf) {
        this.entityId = buf.readInt();
        this.isPosing = buf.readBoolean();
        this.angles = new float[10][3];
        for (int i = 0; i < 10; i++) {
            for (int j = 0; j < 3; j++) {
                this.angles[i][j] = buf.readFloat();
            }
        }
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeInt(this.entityId);
        buf.writeBoolean(this.isPosing);

        for (int i = 0; i < 10; i++) {
            for (int j = 0; j < 3; j++) {
                buf.writeFloat(this.angles[i][j]);
            }
        }
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        if (PacketDispatch.isServer(context)) {
            // 服务端:应用姿势、写全局备份,并回传给所有能看到实体的玩家
            PacketDispatch.enqueueServer(context, () -> {
                ServerPlayer sender = context.getSender();
                if (sender != null && sender.level() != null) {
                    Entity entity = sender.level().getEntity(this.entityId);
                    if (entity instanceof HeroEntity hero) {
                        hero.isPoseEditing = this.isPosing;
                        hero.customPoseAngles = copyAngles(this.angles);
                        com.whitecloud233.modid.herobrine_companion.entity.logic.data.HeroStateManager.backupToGlobal(hero);
                        PacketHandler.sendToTracking(new SavePosePacket(this.entityId, this.isPosing, hero.customPoseAngles), hero);
                    }
                }
            });
        } else {
            // 客户端:把姿势数据应用到本地实体
            PacketDispatch.assertClient(context);
            context.enqueueWork(() -> NetworkClientBridge.applySavePose(this.entityId, this.isPosing, copyAngles(this.angles)));
            context.setPacketHandled(true);
        }
    }

    private static float[][] copyAngles(float[][] source) {
        float[][] dest = new float[10][3];
        for (int i = 0; i < 10; i++) {
            System.arraycopy(source[i], 0, dest[i], 0, 3);
        }
        return dest;
    }
}
