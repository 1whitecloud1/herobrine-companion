package com.whitecloud233.herobrine_companion.network;

import com.whitecloud233.herobrine_companion.HerobrineCompanion;
import com.whitecloud233.herobrine_companion.entity.HeroEntity;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.game.ClientboundForgetLevelChunkPacket;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Queue;
import java.util.Set;

public record ClearAreaPacket() implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<ClearAreaPacket> TYPE = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(HerobrineCompanion.MODID, "clear_area"));
    
    public static final StreamCodec<ByteBuf, ClearAreaPacket> STREAM_CODEC = StreamCodec.unit(new ClearAreaPacket());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(@SuppressWarnings("unused") ClearAreaPacket payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                // Check if player has visited the new dimension
                if (!player.getPersistentData().getBoolean("HasVisitedHeroDimension")) {
                    player.sendSystemMessage(Component.translatable("message.herobrine_companion.hero_not_ready"));
                    return;
                }

                ServerLevel level = player.serverLevel();
                
                if (level.dimension() != Level.OVERWORLD) {
                    player.sendSystemMessage(Component.translatable("message.herobrine_companion.void_domain_overworld_only"));
                    return;
                }

                CompoundTag playerData = player.getPersistentData();
                int usageCount = playerData.getInt("VoidDomainUsageCount");

                if (usageCount >= 2) {
                    player.sendSystemMessage(Component.translatable("message.herobrine_companion.void_domain_limit"));
                    return;
                }

                playerData.putInt("VoidDomainUsageCount", usageCount + 1);

                player.sendSystemMessage(Component.translatable("message.herobrine_companion.void_domain_init", usageCount + 1));
                
                player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 600, 255, false, false));
                player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 600, 1, false, false));

                // Control generation position: Center on player's chunk
                // If you want to control it more precisely (e.g. offset), modify 'center' here.
                // Currently it is centered on the player.
                ChunkPos center = player.chunkPosition();
                int radius = 8; 
                
                Queue<ChunkPos> chunksToClear = new ArrayDeque<>();
                for (int x = -radius; x <= radius; x++) {
                    for (int z = -radius; z <= radius; z++) {
                        chunksToClear.add(new ChunkPos(center.x + x, center.z + z));
                    }
                }

                VoidDomainTask task = new VoidDomainTask(level, chunksToClear, player);
                NeoForge.EVENT_BUS.register(task);
            }
        });
    }

    public static class VoidDomainTask {
        private final ServerLevel level;
        private final Queue<ChunkPos> queue;
        private final Queue<ChunkPos> pendingUpdates = new ArrayDeque<>();
        private final ServerPlayer player;
        private final int chunksPerTick = 16; 

        public VoidDomainTask(ServerLevel level, Queue<ChunkPos> queue, ServerPlayer player) {
            this.level = level;
            this.queue = queue;
            this.player = player;
        }

        @net.neoforged.bus.api.SubscribeEvent
        public void onLevelTick(LevelTickEvent.Post event) {
            if (event.getLevel() != this.level) return;

            processPendingUpdates();

            for (int i = 0; i < chunksPerTick; i++) {
                if (queue.isEmpty()) {
                    if (pendingUpdates.isEmpty()) {
                        player.sendSystemMessage(Component.translatable("message.herobrine_companion.void_domain_complete"));
                        NeoForge.EVENT_BUS.unregister(this);
                    }
                    return;
                }

                ChunkPos chunkPos = queue.poll();
                clearChunkFast(level, chunkPos);
                pendingUpdates.add(chunkPos);
            }
        }

        private void processPendingUpdates() {
            while (!pendingUpdates.isEmpty()) {
                ChunkPos chunkPos = pendingUpdates.poll();
                LevelChunk chunk = level.getChunk(chunkPos.x, chunkPos.z);
                
                level.getChunkSource().chunkMap.getPlayers(chunkPos, false).forEach(p -> {
                    p.connection.send(new ClientboundForgetLevelChunkPacket(chunkPos));
                    p.connection.send(new ClientboundLevelChunkWithLightPacket(chunk, level.getLightEngine(), null, null));
                });
                
                if (pendingUpdates.size() < chunksPerTick) break;
            }
        }

        private void clearChunkFast(ServerLevel level, ChunkPos chunkPos) {
            LevelChunk chunk = level.getChunk(chunkPos.x, chunkPos.z);
            LevelLightEngine lightEngine = level.getLightEngine();
            
            // 1. Remove BlockEntities
            Set<BlockPos> blockEntitiesToRemove = new HashSet<>(chunk.getBlockEntities().keySet());
            for (BlockPos pos : blockEntitiesToRemove) {
                chunk.removeBlockEntity(pos);
            }
            
            // 2. Remove Entities (Prevent falling/lag)
            AABB chunkBox = new AABB(
                chunkPos.getMinBlockX(), level.getMinBuildHeight(), chunkPos.getMinBlockZ(),
                chunkPos.getMaxBlockX() + 1, level.getMaxBuildHeight(), chunkPos.getMaxBlockZ() + 1
            );
            
            List<Entity> entities = level.getEntities(null, chunkBox);
            for (Entity entity : entities) {
                if (entity instanceof Player || entity instanceof HeroEntity) {
                    continue; 
                }
                entity.discard();
            }

            // 3. Clear Sections
            LevelChunkSection[] sections = chunk.getSections();
            for (int i = 0; i < sections.length; i++) {
                LevelChunkSection section = sections[i];
                if (section.hasOnlyAir()) continue;

                int sectionY = chunk.getSectionYFromSectionIndex(i);
                int minSectionY = sectionY * 16;
                
                if (minSectionY <= level.getMinBuildHeight()) {
                    for (int x = 0; x < 16; x++) {
                        for (int z = 0; z < 16; z++) {
                            for (int y = 0; y < 16; y++) {
                                int worldY = minSectionY + y;
                                if (worldY == level.getMinBuildHeight()) continue;
                                section.setBlockState(x, y, z, Blocks.AIR.defaultBlockState());
                            }
                        }
                    }
                } else {
                    for (int x = 0; x < 16; x++) {
                        for (int z = 0; z < 16; z++) {
                            for (int y = 0; y < 16; y++) {
                                section.setBlockState(x, y, z, Blocks.AIR.defaultBlockState());
                            }
                        }
                    }
                }
                section.recalcBlockCounts();
            }

            Heightmap.primeHeightmaps(chunk, Set.of(Heightmap.Types.MOTION_BLOCKING, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Heightmap.Types.OCEAN_FLOOR, Heightmap.Types.WORLD_SURFACE));
            chunk.setUnsaved(true);
            
            // 4. Manual Light Injection
            for (int i = 0; i < sections.length; i++) {
                LevelChunkSection section = sections[i];
                int sectionY = chunk.getSectionYFromSectionIndex(i);
                SectionPos sectionPos = SectionPos.of(chunkPos, sectionY);

                var skyListener = lightEngine.getLayerListener(LightLayer.SKY);
                if (skyListener != null) {
                    DataLayer dataLayer = skyListener.getDataLayerData(sectionPos);
                    
                    if (dataLayer == null) {
                        lightEngine.checkBlock(sectionPos.origin().offset(8, 8, 8));
                        dataLayer = skyListener.getDataLayerData(sectionPos);
                    }
                    
                    if (dataLayer != null) {
                        Arrays.fill(dataLayer.getData(), (byte) 0xFF);
                    }
                }
            }
        }
    }
}
