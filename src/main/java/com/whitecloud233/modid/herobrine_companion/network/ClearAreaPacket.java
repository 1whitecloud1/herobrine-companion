package com.whitecloud233.modid.herobrine_companion.network;

import com.whitecloud233.modid.herobrine_companion.entity.HeroEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundForgetLevelChunkPacket;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
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
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.function.Supplier;

public class ClearAreaPacket {

    public ClearAreaPacket() {}

    public ClearAreaPacket(FriendlyByteBuf buf) {}

    public void encode(FriendlyByteBuf buf) {}

    public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player != null) {
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

                ChunkPos center = player.chunkPosition();
                int radius = 8; 
                
                Queue<ChunkPos> chunksToClear = new ArrayDeque<>();
                for (int x = -radius; x <= radius; x++) {
                    for (int z = -radius; z <= radius; z++) {
                        chunksToClear.add(new ChunkPos(center.x + x, center.z + z));
                    }
                }

                VoidDomainTask task = new VoidDomainTask(level, chunksToClear, player);
                MinecraftForge.EVENT_BUS.register(task);
            }
        });
        context.setPacketHandled(true);
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

        @SubscribeEvent
        public void onLevelTick(TickEvent.LevelTickEvent event) {
            if (event.level != this.level || event.phase != TickEvent.Phase.END) return;

            processPendingUpdates();

            for (int i = 0; i < chunksPerTick; i++) {
                if (queue.isEmpty()) {
                    if (pendingUpdates.isEmpty()) {
                        player.sendSystemMessage(Component.translatable("message.herobrine_companion.void_domain_complete"));
                        MinecraftForge.EVENT_BUS.unregister(this);
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
                    p.connection.send(new ClientboundForgetLevelChunkPacket(chunkPos.x, chunkPos.z));
                    p.connection.send(new ClientboundLevelChunkWithLightPacket(chunk, level.getLightEngine(), null, null));
                });
                
                if (pendingUpdates.size() < chunksPerTick) break;
            }
        }

        private void clearChunkFast(ServerLevel level, ChunkPos chunkPos) {
            LevelChunk chunk = level.getChunk(chunkPos.x, chunkPos.z);
            LevelLightEngine lightEngine = level.getLightEngine();
            
            Set<BlockPos> blockEntitiesToRemove = new HashSet<>(chunk.getBlockEntities().keySet());
            for (BlockPos pos : blockEntitiesToRemove) {
                chunk.removeBlockEntity(pos);
            }
            
            AABB chunkBox = new AABB(
                chunkPos.getMinBlockX(), level.getMinBuildHeight(), chunkPos.getMinBlockZ(),
                chunkPos.getMaxBlockX() + 1, level.getMaxBuildHeight(), chunkPos.getMaxBlockZ() + 1
            );
            
            List<Entity> entities = level.getEntities(null, chunkBox);
            for (Entity entity : entities) {
                if (entity instanceof Player || entity instanceof HeroEntity) {
                    continue; 
                }
                entity.remove(Entity.RemovalReason.DISCARDED);
            }

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
