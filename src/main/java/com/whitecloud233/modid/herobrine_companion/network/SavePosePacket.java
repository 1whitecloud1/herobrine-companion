package com.whitecloud233.modid.herobrine_companion.network;

import com.whitecloud233.modid.herobrine_companion.entity.HeroEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class SavePosePacket {
    private final int entityId;
    private final boolean isPosing;
    private final float[][] angles;

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
        for (int i = 0; i < 10; i++) { // 改为 10
            for (int j = 0; j < 3; j++) {
                this.angles[i][j] = buf.readFloat();
            }
        }
    }

    // 编码器：将数据写入网络字节流
    public void encode(FriendlyByteBuf buf) {
        buf.writeInt(this.entityId);
        buf.writeBoolean(this.isPosing);

        for (int i = 0; i < 10; i++) { // 改为 10
            for (int j = 0; j < 3; j++) {
                buf.writeFloat(this.angles[i][j]);
            }
        }
    }

    // 处理器：包到达目的地时的处理逻辑
    public void handle(Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        context.enqueueWork(() -> {
            if (context.getDirection().getReceptionSide().isServer()) {
                // --- 1. 服务端收到客户端 (GUI) 的发包 ---
                ServerPlayer sender = context.getSender();
                if (sender != null && sender.level() != null) {
                    Entity entity = sender.level().getEntity(this.entityId);
                    if (entity instanceof HeroEntity hero) {
                        // 更新服务端的实体数据
                        hero.isPoseEditing = this.isPosing;

                        // 👇 【核心修复】：补回丢失的这一行！把网络包里的角度赋值给服务端的实体
                        hero.customPoseAngles = copyAngles(this.angles);

                        // 收到新姿势后，立即备份到全局存档确保持久化
                        com.whitecloud233.modid.herobrine_companion.entity.logic.data.HeroStateManager.backupToGlobal(hero);

                        // 核心：广播给所有正在渲染该实体的客户端，让所有人都能看到新姿势
                        PacketHandler.sendToTracking(new SavePosePacket(this.entityId, this.isPosing, hero.customPoseAngles), hero);
                    }
                }
            } else {
                // --- 2. 客户端收到服务端的广播发包 ---
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> handleOnClient(this));
            }
        });
        context.setPacketHandled(true);
    }

    // 隔离纯客户端端代码，防止服务端加载 Minecraft.getInstance() 导致崩溃
    private static void handleOnClient(SavePosePacket msg) {
        if (Minecraft.getInstance().level != null) {
            Entity entity = Minecraft.getInstance().level.getEntity(msg.entityId);
            if (entity instanceof HeroEntity hero) {
                hero.isPoseEditing = msg.isPosing;
                hero.customPoseAngles = copyAngles(msg.angles);
            }
        }
    }

    // 辅助方法：深拷贝二维数组
    private static float[][] copyAngles(float[][] source) {
        float[][] dest = new float[10][3]; // 改为 10
        for (int i = 0; i < 10; i++) { // 改为 10
            System.arraycopy(source[i], 0, dest[i], 0, 3);
        }
        return dest;
    }
}