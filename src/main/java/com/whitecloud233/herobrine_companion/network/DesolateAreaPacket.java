package com.whitecloud233.herobrine_companion.network;

import com.whitecloud233.herobrine_companion.HerobrineCompanion;
import com.whitecloud233.herobrine_companion.world.terrain.TerrainEditTask;
import com.whitecloud233.herobrine_companion.entity.HeroEntity;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.Queue;

public record DesolateAreaPacket(int entityId) implements CustomPacketPayload {

    /** 以玩家所在区块为中心的半径（2 → 5x5 区块）。 */
    private static final int DESOLATE_RADIUS = 2;

    public static final CustomPacketPayload.Type<DesolateAreaPacket> TYPE = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(HerobrineCompanion.MODID, "desolate_area"));

    public static final StreamCodec<ByteBuf, DesolateAreaPacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.INT, DesolateAreaPacket::entityId,
            DesolateAreaPacket::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(DesolateAreaPacket payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                ServerLevel level = player.serverLevel();
                Entity entity = level.getEntity(payload.entityId);

                if (!(entity instanceof HeroEntity hero)) {
                    return;
                }

                if (hero.getTrustLevel() < 70) {
                    player.sendSystemMessage(Component.translatable("message.herobrine_companion.desolate_locked_trust", 70));
                    return;
                }

                if (level.dimension() != Level.OVERWORLD) {
                    player.sendSystemMessage(Component.translatable("message.herobrine_companion.overworld_only"));
                    return;
                }

                player.sendSystemMessage(Component.translatable("message.herobrine_companion.desolate_start"));

                Queue<ChunkPos> chunksToClear = TerrainEditTask.chunkQueue(player.chunkPosition(), DESOLATE_RADIUS);

                DesolateTask task = new DesolateTask(level, chunksToClear, player);
                task.start();
            }
        });
    }

    /**
     * 荒芜：贴着每列最高的地表切平，切掉之上的一切，并把切空部分点亮。
     *
     * <p>与虚空不同，这里保留地形轮廓（山还是山、海还是海），只削掉「地表之上的东西」。
     * 采样时把水面也算地表（{@code skipFluidSources = false}），否则湖会被整列抽干。</p>
     */
    public static class DesolateTask extends TerrainEditTask {

        public DesolateTask(ServerLevel level, Queue<ChunkPos> queue, ServerPlayer player) {
            super(level, queue, player, DEFAULT_CHUNKS_PER_TICK,
                    Component.translatable("message.herobrine_companion.desolate_complete"));
        }

        @Override
        protected void editColumn(ServerLevel level, LevelChunk chunk, ChunkPos chunkPos,
                                  LevelChunkSection[] sections, int x, int z) {
            int minBuildHeight = level.getMinBuildHeight();

            ColumnSurface surface = findSurface(chunk, x, z, minBuildHeight, level.getMaxBuildHeight(), false);
            int surfaceY = surface != null ? surface.y() : minBuildHeight;
            int clearStartY = surface != null ? surfaceY + 1 : minBuildHeight;

            for (int i = sections.length - 1; i >= 0; i--) {
                LevelChunkSection section = sections[i];
                int sectionMinY = chunk.getSectionYFromSectionIndex(i) * 16;
                int sectionMaxY = sectionMinY + 15;

                if (sectionMinY > clearStartY) {
                    clearColumnAbove(chunk, chunkPos, section, sectionMinY, x, z, sectionMinY);
                } else if (sectionMaxY >= clearStartY) {
                    clearColumnAbove(chunk, chunkPos, section, sectionMinY, x, z, clearStartY);
                }
            }

            lightColumnAbove(level, chunk, x, z, surfaceY);
        }
    }
}
