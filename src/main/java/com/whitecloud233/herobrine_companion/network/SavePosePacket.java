package com.whitecloud233.herobrine_companion.network;

import com.whitecloud233.herobrine_companion.HerobrineCompanion;
import com.whitecloud233.herobrine_companion.entity.HeroEntity;
import com.whitecloud233.herobrine_companion.entity.logic.data.HeroStateManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public class SavePosePacket implements CustomPacketPayload {

    // 1.21.1: 必须定义一个独特的 TYPE
    public static final CustomPacketPayload.Type<SavePosePacket> TYPE = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(HerobrineCompanion.MODID, "save_pose"));

    // 1.21.1: 使用 StreamCodec 进行编解码
    public static final StreamCodec<FriendlyByteBuf, SavePosePacket> STREAM_CODEC = StreamCodec.ofMember(SavePosePacket::encode, SavePosePacket::new);

    public final int entityId;
    public final boolean isPosing;
    public final float[][] angles;

    // 构造函数：用于在代码中实例化发送
    public SavePosePacket(int entityId, boolean isPosing, float[][] angles) {
        this.entityId = entityId;
        this.isPosing = isPosing;
        this.angles = angles;
    }

    // 解码器：从网络字节流还原数据
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

    // 编码器：将数据写入网络字节流
    public void encode(FriendlyByteBuf buf) {
        buf.writeInt(this.entityId);
        buf.writeBoolean(this.isPosing);

        for (int i = 0; i < 10; i++) {
            for (int j = 0; j < 3; j++) {
                buf.writeFloat(this.angles[i][j]);
            }
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    // 处理器：包到达目的地时的处理逻辑
    public void handle(IPayloadContext context) {
        context.enqueueWork(() -> {
            // 1.21.1: 通过 flow() 判断包的方向
            if (context.flow().isServerbound()) {
                // --- 1. 服务端收到客户端 (GUI) 的发包 ---
                Player player = context.player();
                if (player instanceof ServerPlayer sender && sender.level() != null) {
                    Entity entity = sender.level().getEntity(this.entityId);
                    if (entity instanceof HeroEntity hero) {
                        hero.isPoseEditing = this.isPosing;
                        hero.customPoseAngles = copyAngles(this.angles);

                        // 收到新姿势后，立即备份到全局存档确保持久化
                        HeroStateManager.backupToGlobal(hero);

                        // 广播给所有正在渲染该实体的客户端
                        PacketHandler.sendToTracking(new SavePosePacket(this.entityId, this.isPosing, hero.customPoseAngles), hero);
                    }
                }
            } else if (context.flow().isClientbound()) {
                // --- 2. 客户端收到服务端的广播发包 ---
                // 因为处于 isClientbound 分支内，服务端不会执行这里，也就不会引发 Minecraft.getInstance() 的类加载崩溃
                com.whitecloud233.herobrine_companion.client.network.ClientStateSync.applySavePose(
                        this.entityId, this.isPosing, copyAngles(this.angles));
            }
        });
    }

    // 辅助方法：深拷贝二维数组
    private static float[][] copyAngles(float[][] source) {
        float[][] dest = new float[10][3];
        for (int i = 0; i < 10; i++) {
            System.arraycopy(source[i], 0, dest[i], 0, 3);
        }
        return dest;
    }
}