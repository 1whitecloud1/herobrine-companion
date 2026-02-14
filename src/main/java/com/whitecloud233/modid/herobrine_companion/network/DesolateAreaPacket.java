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

public class DesolateAreaPacket {

    private int entityId;

    public DesolateAreaPacket(int entityId) {
        this.entityId = entityId;
    }

    public DesolateAreaPacket(FriendlyByteBuf buf) {
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

                if (hero.getTrustLevel() < 70) {
                    player.sendSystemMessage(Component.translatable("message.herobrine_companion.desolate_locked_trust", 70));
                    return;
                }
                
                if (level.dimension() != Level.OVERWORLD) {
                    player.sendSystemMessage(Component.translatable("message.herobrine_companion.overworld_only"));
                    return;
                }

                player.sendSystemMessage(Component.translatable("message.herobrine_companion.desolate_start"));
                
                ChunkPos center = player.chunkPosition();
                int radius = 2;
                
                Queue<ChunkPos> chunksToClear = new ArrayDeque<>();
                for (int x = -radius; x <= radius; x++) {
                    for (int z = -radius; z <= radius; z++) {
                        chunksToClear.add(new ChunkPos(center.x + x, center.z + z));
                    }
                }

                DesolateTask task = new DesolateTask(level, chunksToClear, player);
                MinecraftForge.EVENT_BUS.register(task);
            }
        });
        context.setPacketHandled(true);
    }

    public static class DesolateTask {
        private final ServerLevel level;
        private final Queue<ChunkPos> queue;
        private final Queue<ChunkPos> pendingUpdates = new ArrayDeque<>();
        private final ServerPlayer player;
        private final int chunksPerTick = 16; 

        public DesolateTask(ServerLevel level, Queue<ChunkPos> queue, ServerPlayer player) {
            this.level = level;
            this.queue = queue;
            this.player = player;
        }

        @SubscribeEvent
        public void onLevelTick(TickEvent.LevelTickEvent event) {
            if (event.level != this.level || event.phase != TickEvent.Phase.END) return;

            processPendingUpdates();

            for (int i = 0; i < chunksPerTick; i++) {
                if (queue.isEmpty()) {
                    if (pendingUpdates.isEmpty()) {
                        player.sendSystemMessage(Component.translatable("message.herobrine_companion.desolate_complete"));
                        MinecraftForge.EVENT_BUS.unregister(this);
                    }
                    return;
                }

                ChunkPos chunkPos = queue.poll();
                desolateChunk(level, chunkPos);
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

        private boolean isTerrain(BlockState state) {
            // 优先检查原版标签
            if (state.is(BlockTags.DIRT) || 
                state.is(BlockTags.SAND) || 
                state.is(BlockTags.BASE_STONE_OVERWORLD) ||
                state.is(BlockTags.TERRACOTTA) ||
                state.is(BlockTags.OVERWORLD_CARVER_REPLACEABLES)) {
                return true;
            }

            // 增加 Forge 通用标签检查 (极大提升模组兼容性)
            if (state.is(Tags.Blocks.STONE) ||
                state.is(Tags.Blocks.GRAVEL) || 
                state.is(Tags.Blocks.SAND) ||
                state.is(Tags.Blocks.NETHERRACK) || 
                state.is(Tags.Blocks.END_STONES) ||
                state.is(Tags.Blocks.ORES)) { 
                return true;
            }
            
            // 硬编码的特定方块检查
            return state.is(Blocks.GRAVEL) || 
                   state.is(Blocks.CLAY) || 
                   state.is(Blocks.WATER) || 
                   state.is(Blocks.LAVA) || 
                   state.is(Blocks.ICE) || 
                   state.is(Blocks.PACKED_ICE) || 
                   state.is(Blocks.BLUE_ICE) || 
                   state.is(Blocks.SNOW_BLOCK) || 
                   state.is(Blocks.BEDROCK) ||
                   state.is(Blocks.OBSIDIAN) ||
                   state.is(Blocks.MAGMA_BLOCK) ||
                   state.is(Blocks.GRASS_BLOCK) ||
                   state.is(Blocks.PODZOL) ||
                   state.is(Blocks.MYCELIUM) ||
                   state.is(Blocks.COARSE_DIRT) ||
                   state.is(Blocks.ROOTED_DIRT) ||
                   state.is(Blocks.MOSS_BLOCK) ||
                   state.is(Blocks.MUD) ||
                   state.is(Blocks.MUDDY_MANGROVE_ROOTS) ||
                   state.is(Blocks.CALCITE) ||
                   state.is(Blocks.TUFF) ||
                   state.is(Blocks.DRIPSTONE_BLOCK) ||
                   state.is(Blocks.POWDER_SNOW);
        }

        private void desolateChunk(ServerLevel level, ChunkPos chunkPos) {
            LevelChunk chunk = level.getChunk(chunkPos.x, chunkPos.z);
            LevelLightEngine lightEngine = level.getLightEngine();
            int minBuildHeight = level.getMinBuildHeight();
            int maxBuildHeight = level.getMaxBuildHeight();
            
            // Remove entities first (except players and Hero)
            AABB chunkBox = new AABB(
                chunkPos.getMinBlockX(), minBuildHeight, chunkPos.getMinBlockZ(),
                chunkPos.getMaxBlockX() + 1, maxBuildHeight, chunkPos.getMaxBlockZ() + 1
            );
            List<Entity> entities = level.getEntities(null, chunkBox);
            for (Entity entity : entities) {
                if (entity instanceof Player || entity instanceof HeroEntity) {
                    continue; 
                }
                entity.remove(Entity.RemovalReason.DISCARDED);
            }

            LevelChunkSection[] sections = chunk.getSections();
            
            // Column-based processing
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    int surfaceY = minBuildHeight;
                    boolean foundTerrain = false;

                    // 1. Scan from top to find terrain surface
                    // We iterate sections backwards for performance
                    for (int i = sections.length - 1; i >= 0; i--) {
                        LevelChunkSection section = sections[i];
                        if (section.hasOnlyAir()) continue;

                        int sectionY = chunk.getSectionYFromSectionIndex(i);
                        int startY = sectionY * 16 + 15;
                        int endY = sectionY * 16;

                        for (int y = startY; y >= endY; y--) {
                            BlockState state = section.getBlockState(x, y & 15, z);
                            if (state.isAir()) continue;

                            if (isTerrain(state)) {
                                surfaceY = y;
                                foundTerrain = true;
                                break;
                            }
                        }
                        if (foundTerrain) break;
                    }

                    // 2. Clear everything above surfaceY
                    // If no terrain found, surfaceY is minBuildHeight, so we clear everything (void chunk?)
                    // If terrain found at Y, we clear Y+1 upwards.
                    
                    int clearStartY = foundTerrain ? surfaceY + 1 : minBuildHeight;
                    
                    for (int i = sections.length - 1; i >= 0; i--) {
                        LevelChunkSection section = sections[i];
                        int sectionY = chunk.getSectionYFromSectionIndex(i);
                        int sectionMinY = sectionY * 16;
                        int sectionMaxY = sectionMinY + 15;

                        if (sectionMinY > clearStartY) {
                            // Whole section is above clear line
                            // But we only want to clear THIS column (x, z), not the whole section!
                            // Wait, previous code cleared whole section if min > target. That was wrong for terrain following.
                            // We must iterate y.
                            for (int y = 15; y >= 0; y--) {
                                if (!section.getBlockState(x, y, z).isAir()) {
                                    section.setBlockState(x, y, z, Blocks.AIR.defaultBlockState());
                                    // Handle BE removal
                                    BlockPos pos = new BlockPos(chunkPos.getMinBlockX() + x, sectionMinY + y, chunkPos.getMinBlockZ() + z);
                                    chunk.removeBlockEntity(pos);
                                }
                            }
                        } else if (sectionMaxY >= clearStartY) {
                            // Partial section
                            for (int y = 15; y >= 0; y--) {
                                int worldY = sectionMinY + y;
                                if (worldY >= clearStartY) {
                                    if (!section.getBlockState(x, y, z).isAir()) {
                                        section.setBlockState(x, y, z, Blocks.AIR.defaultBlockState());
                                        BlockPos pos = new BlockPos(chunkPos.getMinBlockX() + x, worldY, chunkPos.getMinBlockZ() + z);
                                        chunk.removeBlockEntity(pos);
                                    }
                                }
                            }
                        }
                    }
                    
                    // 3. Fix Lighting for this column
                    // Set SkyLight to 15 from top down to surfaceY + 1
                    for (int i = sections.length - 1; i >= 0; i--) {
                        LevelChunkSection section = sections[i];
                        int sectionY = chunk.getSectionYFromSectionIndex(i);
                        int sectionMinY = sectionY * 16;
                        int sectionMaxY = sectionMinY + 15;
                        
                        if (sectionMaxY <= surfaceY) break; // Below surface, no sky light change needed (usually)

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
                                    int worldY = sectionMinY + y;
                                    if (worldY > surfaceY) {
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
