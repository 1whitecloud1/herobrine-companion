package com.whitecloud233.herobrine_companion.network;

import com.whitecloud233.herobrine_companion.HerobrineCompanion;
import com.whitecloud233.herobrine_companion.world.terrain.TerrainEditTask;
import com.whitecloud233.herobrine_companion.entity.HeroEntity;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.Queue;

public record FlattenAreaPacket(int entityId) implements CustomPacketPayload {

    /** 以玩家所在区块为中心的半径（2 → 5x5 区块）。 */
    private static final int FLATTEN_RADIUS = 2;

    public static final CustomPacketPayload.Type<FlattenAreaPacket> TYPE = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(HerobrineCompanion.MODID, "flatten_area"));

    public static final StreamCodec<ByteBuf, FlattenAreaPacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.INT, FlattenAreaPacket::entityId,
            FlattenAreaPacket::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(FlattenAreaPacket payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                ServerLevel level = player.serverLevel();
                Entity entity = level.getEntity(payload.entityId);

                if (!(entity instanceof HeroEntity hero)) {
                    return;
                }

                // 信任度检查
                if (hero.getTrustLevel() < 50) {
                    player.sendSystemMessage(Component.translatable("message.herobrine_companion.flatten_locked_trust", 50));
                    return;
                }

                // 维度检查
                if (level.dimension() != Level.OVERWORLD) {
                    player.sendSystemMessage(Component.translatable("message.herobrine_companion.overworld_only"));
                    return;
                }

                player.sendSystemMessage(Component.translatable("message.herobrine_companion.flatten_start"));

                Queue<ChunkPos> chunksToClear = TerrainEditTask.chunkQueue(player.chunkPosition(), FLATTEN_RADIUS);

                // 传入玩家以确定目标高度 (player Y - 1)
                FlattenTask task = new FlattenTask(level, chunksToClear, player);
                task.start();
            }
        });
    }

    /**
     * 整平：把每列削/填到玩家脚下那一层，并铺回该列原本的地表材质。
     *
     * <p>四步走：采样 → 挖（targetY 以上清空）→ 铺（targetY 放采样到的地表）→ 填（向下补到实地）。</p>
     */
    public static class FlattenTask extends TerrainEditTask {

        /** 目标平整高度。 */
        private final int targetY;

        public FlattenTask(ServerLevel level, Queue<ChunkPos> queue, ServerPlayer player) {
            super(level, queue, player, DEFAULT_CHUNKS_PER_TICK,
                    Component.translatable("message.herobrine_companion.flatten_complete"));
            // 设定目标为玩家脚下的方块高度，并钳制在世界建造范围内（避免 section 索引越界）
            this.targetY = Math.max(level.getMinBuildHeight(),
                    Math.min(player.getBlockY() - 1, level.getMaxBuildHeight() - 1));
        }

        @Override
        protected void editColumn(ServerLevel level, LevelChunk chunk, ChunkPos chunkPos,
                                  LevelChunkSection[] sections, int x, int z) {
            int minBuildHeight = level.getMinBuildHeight();
            int maxBuildHeight = level.getMaxBuildHeight();

            // 步骤 0：采样这一列原本的地表材质（兼容模组群系）
            ColumnSurface sampled = findSurface(chunk, x, z, minBuildHeight, maxBuildHeight, true);
            BlockState surfaceState = sampled != null ? sampled.state() : Blocks.GRASS_BLOCK.defaultBlockState();

            // 步骤 1：挖 —— 从最高点向下清到 targetY 之上
            for (int y = maxBuildHeight - 1; y > this.targetY; y--) {
                LevelChunkSection section = sections[chunk.getSectionIndex(y)];
                if (section.getBlockState(x, y & 15, z).isAir()) {
                    continue;
                }
                section.setBlockState(x, y & 15, z, Blocks.AIR.defaultBlockState());
                chunk.removeBlockEntity(new BlockPos(
                        chunkPos.getMinBlockX() + x, y, chunkPos.getMinBlockZ() + z));
            }

            // 步骤 2：铺 —— 在目标高度放回采样到的材质
            sections[chunk.getSectionIndex(this.targetY)].setBlockState(x, this.targetY & 15, z, surfaceState);

            // 步骤 3：填 —— 向下补到实地，避免整平后出现浮空平台
            for (int y = this.targetY - 1; y >= minBuildHeight; y--) {
                LevelChunkSection section = sections[chunk.getSectionIndex(y)];
                BlockState currentState = section.getBlockState(x, y & 15, z);

                // 遇到固体方块（非空气、非流体、非树叶），说明到底了，停止填充
                if (!currentState.isAir() && currentState.getFluidState().isEmpty()
                        && !currentState.is(BlockTags.LEAVES)) {
                    // 原表层草方块被埋到下面时会持续保持"草"的外观，转成泥土更自然
                    if (currentState.is(Blocks.GRASS_BLOCK)) {
                        section.setBlockState(x, y & 15, z, Blocks.DIRT.defaultBlockState());
                    }
                    break;
                }

                if (surfaceState.is(BlockTags.SAND)) {
                    // 表面是沙子就整段填沙，避免沙下突然出现泥土层
                    section.setBlockState(x, y & 15, z, surfaceState);
                } else if (this.targetY - y <= 3) {
                    section.setBlockState(x, y & 15, z, Blocks.DIRT.defaultBlockState());
                } else {
                    section.setBlockState(x, y & 15, z, Blocks.STONE.defaultBlockState());
                }
            }

            // 步骤 4：光照修复 —— 削平/抬高后原版不会重算，必须手动补满
            lightColumnAbove(level, chunk, x, z, this.targetY);
        }
    }
}
