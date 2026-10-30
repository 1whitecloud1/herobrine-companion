package com.whitecloud233.modid.herobrine_companion.network;

import com.whitecloud233.modid.herobrine_companion.world.terrain.TerrainEditTask;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.network.NetworkEvent;

import java.util.HashSet;
import java.util.Queue;
import java.util.function.Supplier;

public class ClearAreaPacket {
    private static final int DEFAULT_VOID_DOMAIN_RADIUS = 8;

    public ClearAreaPacket() {}

    public ClearAreaPacket(FriendlyByteBuf buf) {}

    public void encode(FriendlyByteBuf buf) {}

    public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        PacketDispatch.enqueueServer(context, () -> {
            ServerPlayer player = context.getSender();
            if (player != null) {
                startVoidDomain(player, DEFAULT_VOID_DOMAIN_RADIUS, true, true,
                        Component.translatable("message.herobrine_companion.void_domain_init",
                                player.getPersistentData().getInt("VoidDomainUsageCount") + 1));
            }
        });
    }

    public static boolean startVoidDomain(ServerPlayer player, int radius, boolean requireHeroDimensionVisit,
                                          boolean enforceUsageLimit, Component startMessage) {
        if (player == null) {
            return false;
        }

        if (requireHeroDimensionVisit && !player.getPersistentData().getBoolean("HasVisitedHeroDimension")) {
            player.sendSystemMessage(Component.translatable("message.herobrine_companion.hero_not_ready"));
            return false;
        }

        ServerLevel level = player.serverLevel();
        if (level.dimension() != Level.OVERWORLD) {
            player.sendSystemMessage(Component.translatable("message.herobrine_companion.void_domain_overworld_only"));
            return false;
        }

        CompoundTag playerData = player.getPersistentData();
        int usageCount = playerData.getInt("VoidDomainUsageCount");
        if (enforceUsageLimit && usageCount >= 2) {
            player.sendSystemMessage(Component.translatable("message.herobrine_companion.void_domain_limit"));
            return false;
        }

        if (enforceUsageLimit) {
            playerData.putInt("VoidDomainUsageCount", usageCount + 1);
        }

        player.sendSystemMessage(startMessage);
        player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 600, 255, false, false));
        player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 600, 1, false, false));

        Queue<ChunkPos> chunksToClear = TerrainEditTask.chunkQueue(player.chunkPosition(), Math.max(1, radius));
        VoidDomainTask task = new VoidDomainTask(level, chunksToClear, player);
        MinecraftForge.EVENT_BUS.register(task);
        return true;
    }

    /**
     * 虚空领域：把整个区块抹成空气（只保留世界最低层的基岩），并把天空光全置满。
     *
     * <p>整块处理，不按列走，因此覆写 {@link TerrainEditTask#editChunk} 而不是 {@code editColumn}。</p>
     */
    public static class VoidDomainTask extends TerrainEditTask {

        public VoidDomainTask(ServerLevel level, Queue<ChunkPos> queue, ServerPlayer player) {
            super(level, queue, player, DEFAULT_CHUNKS_PER_TICK,
                    Component.translatable("message.herobrine_companion.void_domain_complete"));
        }

        @Override
        protected void editChunk(ServerLevel level, ChunkPos chunkPos) {
            LevelChunk chunk = level.getChunk(chunkPos.x, chunkPos.z);

            // 方块实体先清：容器/告示牌被抹掉后留着会变成悬空实体。
            for (BlockPos pos : new HashSet<>(chunk.getBlockEntities().keySet())) {
                chunk.removeBlockEntity(pos);
            }
            evictEntities(level, chunkPos);

            LevelChunkSection[] sections = chunk.getSections();
            int minBuildHeight = level.getMinBuildHeight();

            for (int i = 0; i < sections.length; i++) {
                LevelChunkSection section = sections[i];
                if (section.hasOnlyAir()) {
                    continue;
                }

                int sectionMinY = chunk.getSectionYFromSectionIndex(i) * 16;
                for (int x = 0; x < 16; x++) {
                    for (int z = 0; z < 16; z++) {
                        for (int y = 0; y < 16; y++) {
                            // 保留世界底层那一层基岩，否则玩家会直接掉出世界。
                            if (sectionMinY + y == minBuildHeight) {
                                continue;
                            }
                            section.setBlockState(x, y, z, Blocks.AIR.defaultBlockState());
                        }
                    }
                }
            }

            finishChunk(chunk);
            lightWholeChunk(level, chunk);
        }
    }
}
