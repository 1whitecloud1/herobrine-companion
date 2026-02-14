package com.whitecloud233.modid.herobrine_companion.network;

import com.whitecloud233.modid.herobrine_companion.entity.HeroEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundForgetLevelChunkPacket;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.Tags;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayDeque;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.function.Supplier;

public class FlattenAreaPacket {

    private int entityId;

    public FlattenAreaPacket(int entityId) {
        this.entityId = entityId;
    }

    public FlattenAreaPacket(FriendlyByteBuf buf) {
        this.entityId = buf.readInt();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeInt(this.entityId);
    }

    public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player != null) {
                ServerLevel level = player.serverLevel();
                Entity entity = level.getEntity(this.entityId);
                
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
                
                ChunkPos center = player.chunkPosition();
                int radius = 2; // 5x5 chunk area
                
                Queue<ChunkPos> chunksToClear = new ArrayDeque<>();
                for (int x = -radius; x <= radius; x++) {
                    for (int z = -radius; z <= radius; z++) {
                        chunksToClear.add(new ChunkPos(center.x + x, center.z + z));
                    }
                }

                // 传入玩家以确定目标高度 (player Y - 1)
                FlattenTask task = new FlattenTask(level, chunksToClear, player);
                MinecraftForge.EVENT_BUS.register(task);
            }
        });
        context.setPacketHandled(true);
    }

    public static class FlattenTask {
        private final ServerLevel level;
        private final Queue<ChunkPos> queue;
        private final Queue<ChunkPos> pendingUpdates = new ArrayDeque<>();
        private final ServerPlayer player;
        private final int chunksPerTick = 16; 
        private final int targetY; // 目标平整高度

        public FlattenTask(ServerLevel level, Queue<ChunkPos> queue, ServerPlayer player) {
            this.level = level;
            this.queue = queue;
            this.player = player;
            // 设定目标为玩家脚下的方块高度
            this.targetY = player.getBlockY() - 1;
        }

        @SubscribeEvent
        public void onLevelTick(TickEvent.LevelTickEvent event) {
            if (event.level != this.level || event.phase != TickEvent.Phase.END) return;

            processPendingUpdates();

            for (int i = 0; i < chunksPerTick; i++) {
                if (queue.isEmpty()) {
                    if (pendingUpdates.isEmpty()) {
                        player.sendSystemMessage(Component.translatable("message.herobrine_companion.flatten_complete"));
                        MinecraftForge.EVENT_BUS.unregister(this);
                    }
                    return;
                }

                ChunkPos chunkPos = queue.poll();
                flattenChunkToLevel(level, chunkPos);
                pendingUpdates.add(chunkPos);
            }
        }

        private void processPendingUpdates() {
            while (!pendingUpdates.isEmpty()) {
                ChunkPos chunkPos = pendingUpdates.poll();
                LevelChunk chunk = level.getChunk(chunkPos.x, chunkPos.z);
                
                level.getChunkSource().chunkMap.getPlayers(chunkPos, false).forEach(p -> {
                    p.connection.send(new ClientboundForgetLevelChunkPacket(chunkPos.x, chunkPos.z));
                    p.connection.send(new ClientboundLevelChunkWithLightPacket(chunk, level.getLightEngine(), null, null));
                });
                
                if (pendingUpdates.size() < chunksPerTick) break;
            }
        }

        // 增强版：同时包含原版标签和Forge通用标签，最大化模组兼容性
        private boolean isTerrain(BlockState state) {
            // 1. 原版通用标签
            if (state.is(BlockTags.DIRT) || 
                state.is(BlockTags.SAND) || 
                state.is(BlockTags.BASE_STONE_OVERWORLD) ||
                state.is(BlockTags.TERRACOTTA) ||
                state.is(BlockTags.OVERWORLD_CARVER_REPLACEABLES)) {
                return true;
            }

            // 2. Forge 通用标签 (这对模组方块极其重要)
            if (state.is(Tags.Blocks.STONE) ||
                state.is(BlockTags.DIRT) || // Tags.Blocks.DIRT doesn't exist, use BlockTags.DIRT
                state.is(Tags.Blocks.GRAVEL) || 
                state.is(Tags.Blocks.SAND) ||
                state.is(Tags.Blocks.NETHERRACK) || 
                state.is(Tags.Blocks.END_STONES)) { 
                return true;
            }
            
            // 3. 特殊硬编码检查
            return state.is(Blocks.GRAVEL) || 
                   state.is(Blocks.CLAY) || 
                   state.is(Blocks.MOSS_BLOCK) ||
                   state.is(Blocks.MUD) ||
                   state.is(Blocks.SNOW_BLOCK) ||
                   state.is(Blocks.POWDER_SNOW) ||
                   state.is(Blocks.ICE) || 
                   state.is(Blocks.PACKED_ICE) || 
                   state.is(Blocks.BLUE_ICE) ||
                   state.is(Blocks.MAGMA_BLOCK) ||
                   state.is(Blocks.OBSIDIAN) ||
                   state.is(Tags.Blocks.ORES);
        }

        // 动态采样：获取某一列最原本的地表材质
        private BlockState getOriginalSurfaceBlock(LevelChunk chunk, int x, int z, int minBuildHeight, int maxBuildHeight) {
            LevelChunkSection[] sections = chunk.getSections();
            
            for (int y = maxBuildHeight - 1; y >= minBuildHeight; y--) {
                int sectionIndex = chunk.getSectionIndex(y);
                LevelChunkSection section = sections[sectionIndex];
                
                if (section.hasOnlyAir()) continue;

                BlockState state = section.getBlockState(x, y & 15, z);
                if (state.isAir()) continue;

                // 找到第一个是地形且不是液体的方块
                if (isTerrain(state) && !state.getFluidState().isSource()) {
                    return state;
                }
            }
            // 兜底：如果没找到（比如虚空），返回草方块
            return Blocks.GRASS_BLOCK.defaultBlockState();
        }

        private void flattenChunkToLevel(ServerLevel level, ChunkPos chunkPos) {
            LevelChunk chunk = level.getChunk(chunkPos.x, chunkPos.z);
            LevelLightEngine lightEngine = level.getLightEngine();
            int minBuildHeight = level.getMinBuildHeight();
            int maxBuildHeight = level.getMaxBuildHeight();
            
            // 1. 移除区域内的所有实体 (玩家和Hero除外)
            AABB chunkBox = new AABB(
                chunkPos.getMinBlockX(), minBuildHeight, chunkPos.getMinBlockZ(),
                chunkPos.getMaxBlockX() + 1, maxBuildHeight, chunkPos.getMaxBlockZ() + 1
            );
            List<Entity> entities = level.getEntities(null, chunkBox);
            for (Entity entity : entities) {
                if (!(entity instanceof Player) && !(entity instanceof HeroEntity)) {
                    entity.remove(Entity.RemovalReason.DISCARDED);
                }
            }

            LevelChunkSection[] sections = chunk.getSections();
            
            // 2. 逐列处理：Cut & Fill
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    
                    // --- 步骤 0: 采样 ---
                    // 动态获取这一列应该铺什么材质（兼容模组群系）
                    BlockState surfaceState = getOriginalSurfaceBlock(chunk, x, z, minBuildHeight, maxBuildHeight);

                    // --- 步骤 A: Cut (挖) ---
                    // 从最高点向下，清除直到 targetY 上方
                    for (int y = maxBuildHeight - 1; y > targetY; y--) {
                        int sectionIndex = chunk.getSectionIndex(y);
                        LevelChunkSection section = sections[sectionIndex];
                        // 只有非空气才操作，节省性能
                        if (!section.getBlockState(x, y & 15, z).isAir()) {
                            section.setBlockState(x, y & 15, z, Blocks.AIR.defaultBlockState());
                            // 如果有方块实体，移除它
                            BlockPos pos = new BlockPos(chunkPos.getMinBlockX() + x, y, chunkPos.getMinBlockZ() + z);
                            chunk.removeBlockEntity(pos);
                        }
                    }

                    // --- 步骤 B: Surface (铺面) ---
                    // 在目标高度 targetY 放置采样到的材质
                    int surfaceSectionIndex = chunk.getSectionIndex(targetY);
                    LevelChunkSection surfaceSection = sections[surfaceSectionIndex];
                    surfaceSection.setBlockState(x, targetY & 15, z, surfaceState);

                    // --- 步骤 C: Fill (填) ---
                    // 防止浮空，向下填充直到接触实地
                    for (int y = targetY - 1; y >= minBuildHeight; y--) {
                        int secIdx = chunk.getSectionIndex(y);
                        LevelChunkSection section = sections[secIdx];
                        BlockState currentState = section.getBlockState(x, y & 15, z);

                        // 遇到固体方块（非空气、非流体、非树叶），说明到底了，停止填充
                        if (!currentState.isAir() && currentState.getFluidState().isEmpty() && !currentState.is(BlockTags.LEAVES)) {
                            // 细节修正：如果原本表面的草方块被埋了，转为泥土
                            if (currentState.is(Blocks.GRASS_BLOCK)) {
                                section.setBlockState(x, y & 15, z, Blocks.DIRT.defaultBlockState());
                            }
                            break; 
                        }

                        // 智能填充材质选择
                        if (surfaceState.is(BlockTags.SAND)) {
                             // 如果表面是沙子，下面全填沙子（防止像泥土一样突兀）
                             section.setBlockState(x, y & 15, z, surfaceState); 
                        } else {
                            // 否则：表层下3格填泥土，深层填石头
                            if (targetY - y <= 3) {
                                section.setBlockState(x, y & 15, z, Blocks.DIRT.defaultBlockState());
                            } else {
                                section.setBlockState(x, y & 15, z, Blocks.STONE.defaultBlockState());
                            }
                        }
                    }
                    
                    // --- 步骤 D: 光照修复 (可选但推荐) ---
                    // 简单重置该列的天空光照，使其重新计算
                    for (int i = sections.length - 1; i >= 0; i--) {
                        int sectionY = chunk.getSectionYFromSectionIndex(i);
                        int sectionMaxY = sectionY * 16 + 15;
                        if (sectionMaxY <= targetY) break;

                        SectionPos sectionPos = SectionPos.of(chunkPos, sectionY);
                        var skyListener = lightEngine.getLayerListener(LightLayer.SKY);
                        if (skyListener != null) {
                            DataLayer dataLayer = skyListener.getDataLayerData(sectionPos);
                            if (dataLayer == null) {
                                lightEngine.checkBlock(sectionPos.origin().offset(8, 8, 8));
                                dataLayer = skyListener.getDataLayerData(sectionPos);
                            }
                            
                            if (dataLayer != null) {
                                byte[] data = dataLayer.getData();
                                for (int y = 15; y >= 0; y--) {
                                    int worldY = sectionY * 16 + y;
                                    if (worldY > targetY) {
                                        // 强制设为 15 (满光照)
                                        int index = (y << 8) | (z << 4) | x;
                                        int byteIndex = index >> 1;
                                        int shift = (index & 1) * 4;
                                        data[byteIndex] = (byte)((data[byteIndex] & ~(0xF << shift)) | (0xF << shift));
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Heightmap.primeHeightmaps(chunk, Set.of(Heightmap.Types.MOTION_BLOCKING, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Heightmap.Types.OCEAN_FLOOR, Heightmap.Types.WORLD_SURFACE));
            chunk.setUnsaved(true);
        }
    }
}