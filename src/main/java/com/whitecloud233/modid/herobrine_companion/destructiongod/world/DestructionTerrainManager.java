package com.whitecloud233.modid.herobrine_companion.destructiongod.world;

import com.whitecloud233.modid.herobrine_companion.HerobrineCompanion;
import com.whitecloud233.modid.herobrine_companion.entity.HeroEntity;
import com.whitecloud233.modid.herobrine_companion.world.terrain.TerrainEditTask;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.protocol.game.ClientboundForgetLevelChunkPacket;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Mod.EventBusSubscriber(modid = HerobrineCompanion.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class DestructionTerrainManager {
    private static void scheduleTask(TerrainTask task) {
        TerrainRuntimeScheduler.scheduleTask(task);
    }

    public static void startDestructionCombo(ServerLevel level, LivingEntity caster) {
        Vec3 flatLook = flattenLook(caster.getLookAngle());
        Vec3 origin = caster.position().add(0.0D, 0.5D, 0.0D).add(flatLook.scale(3.0D));

        startWorldRend(level, caster, origin, flatLook, 96.0D, 8, 12, 0);
        startApocalypseCrack(level, caster, origin.add(flatLook.scale(6.0D)), flatLook, 88.0D, 7.0D, 2, 9, 6);
        startWorldPeel(level, caster, origin.add(flatLook.scale(12.0D)), flatLook, 72.0D, 8, 10);

        level.playSound(null, caster.getX(), caster.getY(), caster.getZ(), SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 5.0F, 0.55F);
        level.playSound(null, caster.getX(), caster.getY(), caster.getZ(), SoundEvents.TRIDENT_THUNDER, SoundSource.WEATHER, 5.0F, 0.8F);
    }

    public static void startWorldRend(ServerLevel level, @Nullable LivingEntity caster, Vec3 origin, Vec3 direction, double maxLength, int halfWidth, int depth, int delayTicks) {
        scheduleTask(new WorldRendTask(level, caster, origin, direction, maxLength, halfWidth, depth, delayTicks));
    }

    public static void startMountainSplitRend(ServerLevel level, @Nullable LivingEntity caster, Vec3 origin, Vec3 direction, double maxLength, int halfWidth, int trenchDepth, int faultDepth, int delayTicks) {
        scheduleTask(new MountainSplitRendTask(level, caster, origin, direction, maxLength, halfWidth, trenchDepth, faultDepth, delayTicks));
    }

    public static void startBladeLineRend(ServerLevel level, @Nullable LivingEntity caster, Vec3 origin, Vec3 direction, double maxLength, int halfWidth, int depth, int delayTicks, float slashRollDegrees) {
        scheduleTask(new BladeLineRendTask(level, caster, origin, direction, maxLength, halfWidth, depth, delayTicks, slashRollDegrees));
    }

    public static void startScytheFaultSplit(ServerLevel level, @Nullable LivingEntity caster, Vec3 origin, Vec3 direction, double maxLength, int terrainHalfWidth, int lineHalfWidth, int shiftDistance, int delayTicks) {
        scheduleTask(new com.whitecloud233.modid.herobrine_companion.destructiongod.world.ScytheFaultSplitTask(level, caster, origin, direction, maxLength, terrainHalfWidth, lineHalfWidth, shiftDistance, delayTicks));
    }

    public static void startApocalypseCrack(ServerLevel level, @Nullable LivingEntity caster, Vec3 origin, Vec3 direction, double maxLength, double sideOffset, int crackHalfWidth, int depth, int delayTicks) {
        scheduleTask(new ApocalypseCrackTask(level, caster, origin, direction, maxLength, sideOffset, crackHalfWidth, depth, delayTicks));
    }

    public static void startWorldPeel(ServerLevel level, @Nullable LivingEntity caster, Vec3 origin, Vec3 direction, double maxLength, int radius, int delayTicks) {
        scheduleTask(new WorldPeelTask(level, caster, origin, direction, maxLength, radius, delayTicks));
    }

    public static void startWorldCollapse(ServerLevel level, @Nullable LivingEntity caster, Vec3 center, double startRadius, double endRadius, int bandWidth, int delayTicks) {
        scheduleTask(new WorldCollapseTask(level, caster, center, startRadius, endRadius, bandWidth, delayTicks));
    }

    public static void startDestructionLightningStrike(ServerLevel level, @Nullable LivingEntity caster, Vec3 center, int radius, int depth, int delayTicks) {
        scheduleTask(new DestructionLightningStrikeTask(level, caster, center, radius, depth, delayTicks));
    }

    public static void startDestructionLightningArc(ServerLevel level, @Nullable LivingEntity caster, Vec3 start, Vec3 end, double radius, int delayTicks) {
        scheduleTask(new LightningSegmentTask(level, caster, start, end, radius, delayTicks, LightningSegmentTask.Kind.ARC));
    }

    public static void startDestructionLightningBeam(ServerLevel level, @Nullable LivingEntity caster, Vec3 start, Vec3 end, double radius, int delayTicks) {
        scheduleTask(new LightningSegmentTask(level, caster, start, end, radius, delayTicks, LightningSegmentTask.Kind.BEAM));
    }

    public static void startDestructionGodOrb(ServerLevel level, @Nullable LivingEntity caster, Vec3 start, Vec3 impact, int fallTicks, double startRadius, double maxOrbRadius, double craterRadius, int craterDepth, int delayTicks) {
        scheduleTask(new DestructionGodOrbTask(level, caster, start, impact, fallTicks, startRadius, maxOrbRadius, craterRadius, craterDepth, delayTicks));
    }

    public static void startThunderSkyNet(ServerLevel level, @Nullable LivingEntity caster, Vec3 center, double cloudY, double radius, int durationTicks, int strikesPerPulse, int pulseIntervalTicks, int delayTicks) {
        scheduleTask(new ThunderSkyNetTask(level, caster, center, cloudY, radius, durationTicks, strikesPerPulse, pulseIntervalTicks, delayTicks));
    }

    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (!(event.level instanceof ServerLevel serverLevel)) {
            return;
        }
        if (!TerrainRuntimeScheduler.hasWork()) {
            return;
        }
        TerrainRuntimeScheduler.tick(serverLevel);
    }

    static Vec3 flattenLook(Vec3 look) {
        Vec3 flat = new Vec3(look.x, 0.0D, look.z);
        if (flat.lengthSqr() < 1.0E-4D) {
            return new Vec3(0.0D, 0.0D, 1.0D);
        }
        return flat.normalize();
    }

    static void destroyBlock(ServerLevel level, BlockPos pos, TickBudget budget, DestructionMode mode, boolean absolute) {
        if (!DestructionTuning.TERRAIN_DAMAGE_ENABLED || mode == DestructionMode.VISUAL || !budget.tryVisit()) {
            return;
        }
        if (!level.isLoaded(pos)) {
            return;
        }

        BlockState state = level.getBlockState(pos);
        if (state.isAir()) {
            return;
        }
        if (!canBreak(level, pos, state, mode, absolute)) {
            return;
        }

        if (!level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_INVISIBLE)) return;
        level.sendParticles(ParticleTypes.POOF, pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D, 2, 0.15D, 0.15D, 0.15D, 0.02D);
        budget.consume();
    }





    private static boolean setBlockStateFast(ServerLevel level, BlockPos pos, BlockState newState, Set<ChunkPos> touchedChunks, LevelLightEngine lightEngine) {
        LevelChunk chunk = level.getChunkAt(pos);
        int sectionIndex = chunk.getSectionIndex(pos.getY());
        if (sectionIndex < 0 || sectionIndex >= chunk.getSections().length) return false;
        BlockState currentState = level.getBlockState(pos);
        if (currentState == newState) return false;
        if (currentState.hasBlockEntity() || level.getBlockEntity(pos) != null) chunk.removeBlockEntity(pos);
        LevelChunkSection section = chunk.getSections()[sectionIndex];
        section.setBlockState(pos.getX() & 15, pos.getY() & 15, pos.getZ() & 15, newState);
        touchedChunks.add(chunk.getPos());
        lightEngine.checkBlock(pos);
        return true;
    }
    static TerrainMutationBatch clearBlocksBatch(ServerLevel level, List<BlockPos> positions, DestructionMode mode, boolean absolute) {
        List<BlockPos> snapshot = positions.stream().map(BlockPos::immutable).toList();
        Set<ChunkPos> touched = new HashSet<>();
        LevelLightEngine light = level.getLightEngine();
        return new TerrainMutationBatch(snapshot.size(), index -> {
            if (!DestructionTuning.TERRAIN_DAMAGE_ENABLED || mode == DestructionMode.VISUAL) return false;
            BlockPos pos = snapshot.get(index);
            if (!level.isLoaded(pos)) return false;
            BlockState state = level.getBlockState(pos);
            return !state.isAir() && canBreak(level, pos, state, mode, absolute)
                    && setBlockStateFast(level, pos, Blocks.AIR.defaultBlockState(), touched, light);
        }, () -> {
            updateTouchedChunks(level, touched, light);
            touched.clear();
        });
    }

    static TerrainMutationBatch faultChangesBatch(ServerLevel level, List<BlockPos> clearPositions,
                                                   List<BlockPos> annihilatePositions, Map<BlockPos, BlockState> placements,
                                                   DestructionMode mode) {
        List<BlockPos> clear = clearPositions.stream().map(BlockPos::immutable).toList();
        List<BlockPos> annihilate = annihilatePositions.stream().map(BlockPos::immutable).toList();
        List<Map.Entry<BlockPos, BlockState>> place = placements.entrySet().stream()
                .map(entry -> Map.entry(entry.getKey().immutable(), entry.getValue())).toList();
        Set<Long> processed = new HashSet<>();
        Set<ChunkPos> touched = new HashSet<>();
        LevelLightEngine light = level.getLightEngine();
        return new TerrainMutationBatch(clear.size() + annihilate.size() + place.size(), index -> {
            if (!DestructionTuning.TERRAIN_DAMAGE_ENABLED || mode == DestructionMode.VISUAL) return false;
            BlockPos pos;
            BlockState target;
            if (index < clear.size() + annihilate.size()) {
                boolean clearing = index < clear.size();
                pos = clearing ? clear.get(index) : annihilate.get(index - clear.size());
                // Match the original priority: clear positions take precedence over annihilation.
                if (!processed.add(pos.asLong()) || !level.isLoaded(pos)) return false;
                BlockState state = level.getBlockState(pos);
                if (!(clearing ? canFaultClear(level, pos, state) : canAnnihilate(level, pos, state))) return false;
                target = Blocks.AIR.defaultBlockState();
            } else {
                var entry = place.get(index - clear.size() - annihilate.size());
                pos = entry.getKey();
                target = entry.getValue();
                if (!level.isLoaded(pos)) return false;
                BlockState existing = level.getBlockState(pos);
                if (!existing.isAir() && !canFaultClear(level, pos, existing)) return false;
            }
            return setBlockStateFast(level, pos, target, touched, light);
        }, () -> {
            updateTouchedChunks(level, touched, light);
            touched.clear();
        });
    }


    private static void updateTouchedChunks(ServerLevel level, Set<ChunkPos> touchedChunks, LevelLightEngine lightEngine) {
        for (ChunkPos chunkPos : touchedChunks) {
            LevelChunk chunk = level.getChunk(chunkPos.x, chunkPos.z);

            // 收尾（重算 section 计数 + 高度图 + 标脏）与整块补光统一走 TerrainEditTask，
            // 和虚空/荒芜/整平三个地形任务保持同一份实现。
            TerrainEditTask.finishChunk(chunk);
            TerrainEditTask.lightWholeChunk(level, chunk);

            level.getChunkSource().chunkMap.getPlayers(chunkPos, false).forEach(player -> {
                player.connection.send(new ClientboundForgetLevelChunkPacket(chunkPos.x, chunkPos.z));
                player.connection.send(new ClientboundLevelChunkWithLightPacket(chunk, lightEngine, null, null));
            });
        }
    }

    static boolean canBreak(ServerLevel level, BlockPos pos, BlockState state, DestructionMode mode, boolean absolute) {
        if (state.getDestroySpeed(level, pos) < 0.0F) {
            return false;
        }
        if (!state.getFluidState().isEmpty()) {
            return false;
        }
        if (isProtectedBlock(level, pos, state)) {
            return false;
        }

        if (absolute) {
            return true;
        }

        boolean hasBlockEntity = state.hasBlockEntity() || level.getBlockEntity(pos) != null;
        if (hasBlockEntity && !DestructionTuning.BREAK_CONTAINERS) {
            return false;
        }

        if (mode == DestructionMode.DIVINE) {
            return true;
        }

        return isNaturalTerrain(state) || isExposedScaffold(level, pos);
    }

    private static boolean isProtectedBlock(ServerLevel level, BlockPos pos, BlockState state) {
        if (state.is(Blocks.BEDROCK)
                || state.is(Blocks.END_PORTAL_FRAME)
                || state.is(Blocks.END_PORTAL)
                || state.is(Blocks.END_GATEWAY)
                || state.is(Blocks.NETHER_PORTAL)
                || state.is(Blocks.COMMAND_BLOCK)
                || state.is(Blocks.CHAIN_COMMAND_BLOCK)
                || state.is(Blocks.REPEATING_COMMAND_BLOCK)
                || state.is(Blocks.STRUCTURE_BLOCK)
                || state.is(Blocks.JIGSAW)
                || state.is(Blocks.BARRIER)
                || state.is(Blocks.SPAWNER)
                || state.is(Blocks.BEACON)
                || state.is(Blocks.RESPAWN_ANCHOR)
                || state.is(Blocks.REINFORCED_DEEPSLATE)) {
            return true;
        }

        return !DestructionTuning.BREAK_CONTAINERS && level.getBlockEntity(pos) != null;
    }

    private static boolean isFaultProtectedBlock(ServerLevel level, BlockPos pos, BlockState state) {
        if (state.is(Blocks.END_PORTAL_FRAME)
                || state.is(Blocks.END_PORTAL)
                || state.is(Blocks.END_GATEWAY)
                || state.is(Blocks.NETHER_PORTAL)
                || state.is(Blocks.COMMAND_BLOCK)
                || state.is(Blocks.CHAIN_COMMAND_BLOCK)
                || state.is(Blocks.REPEATING_COMMAND_BLOCK)
                || state.is(Blocks.STRUCTURE_BLOCK)
                || state.is(Blocks.JIGSAW)
                || state.is(Blocks.BARRIER)
                || state.is(Blocks.SPAWNER)
                || state.is(Blocks.BEACON)
                || state.is(Blocks.RESPAWN_ANCHOR)
                || state.is(Blocks.REINFORCED_DEEPSLATE)) {
            return true;
        }

        return !DestructionTuning.BREAK_CONTAINERS && level.getBlockEntity(pos) != null;
    }

    private static boolean canAnnihilate(ServerLevel level, BlockPos pos, BlockState state) {
        if (state.isAir()) {
            return false;
        }
        return !isFaultProtectedBlock(level, pos, state);
    }

    static boolean canFaultClear(ServerLevel level, BlockPos pos, BlockState state) {
        if (state.isAir()) {
            return false;
        }
        return !isFaultProtectedBlock(level, pos, state);
    }

    private static boolean isNaturalTerrain(BlockState state) {
        return state.is(BlockTags.DIRT)
                || state.is(BlockTags.BASE_STONE_OVERWORLD)
                || state.is(BlockTags.LOGS)
                || state.is(BlockTags.LEAVES)
                || state.is(BlockTags.SAND)
                || state.is(Blocks.GRAVEL)
                || state.is(Blocks.COBBLESTONE)
                || state.is(Blocks.STONE)
                || state.is(Blocks.DEEPSLATE)
                || state.is(Blocks.END_STONE)
                || state.is(Blocks.NETHERRACK)
                || state.is(Blocks.BLACKSTONE)
                || state.is(Blocks.BASALT)
                || state.is(Blocks.SOUL_SOIL)
                || state.is(Blocks.SOUL_SAND)
                || state.is(Blocks.MAGMA_BLOCK)
                || state.is(Blocks.GRASS_BLOCK)
                || state.is(Blocks.DIRT_PATH)
                || state.is(Blocks.PODZOL)
                || state.is(Blocks.MYCELIUM);
    }

    static boolean isExposedScaffold(ServerLevel level, BlockPos pos) {
        int airSides = 0;
        int supports = 0;
        for (Direction direction : Direction.values()) {
            BlockPos neighborPos = pos.relative(direction);
            BlockState neighborState = level.getBlockState(neighborPos);
            if (neighborState.isAir()) {
                airSides++;
            } else if (direction == Direction.DOWN || direction.getAxis().isHorizontal()) {
                supports++;
            }
        }

        int surfaceY = level.getHeight(Heightmap.Types.MOTION_BLOCKING, pos.getX(), pos.getZ());
        return airSides >= 4 || supports <= 1 || pos.getY() > surfaceY + 4;
    }

    static void damageAndPush(ServerLevel level, @Nullable LivingEntity caster, AABB area, Vec3 center, float damage, double horizontalPush, double verticalPush) {
        List<LivingEntity> entities = level.getEntitiesOfClass(LivingEntity.class, area, entity -> entity.isAlive() && entity != caster && !(entity instanceof HeroEntity));
        for (LivingEntity entity : entities) {
            double distance = entity.position().distanceTo(center);
            double maxDistance = Math.max(1.0D, Math.sqrt(area.getSize()));
            double scale = Mth.clamp(1.0D - (distance / maxDistance), 0.2D, 1.0D);
            entity.hurt(level.damageSources().magic(), damage * (float) scale);

            Vec3 push = entity.position().subtract(center);
            if (push.lengthSqr() < 1.0E-4D) {
                push = new Vec3(0.0D, 1.0D, 0.0D);
            } else {
                push = push.normalize();
            }
            entity.push(push.x * horizontalPush * scale, verticalPush * scale, push.z * horizontalPush * scale);
        }
    }

    static void clearLooseEntities(ServerLevel level, @Nullable LivingEntity caster, AABB area) {
        List<Entity> entities = level.getEntitiesOfClass(Entity.class, area, entity -> entity != caster && !(entity instanceof Player) && !(entity instanceof HeroEntity));
        for (Entity entity : entities) {
            if (entity instanceof Projectile || entity instanceof ItemEntity) {
                entity.discard();
            }
        }
    }

    static void collectLightningStrikeBlocks(ServerLevel level, Vec3 center, int radius, int depth, List<BlockPos> collector, Set<Long> queuedKeys) {
        int centerX = Mth.floor(center.x);
        int centerZ = Mth.floor(center.z);
        int surfaceY = level.getHeight(Heightmap.Types.MOTION_BLOCKING, centerX, centerZ);
        int topY = Math.min(level.getMaxBuildHeight() - 1, Math.max(surfaceY + 10, Mth.floor(center.y) + 6));
        int bottomY = Math.max(level.getMinBuildHeight() + 1, surfaceY - depth);
        int outerRadius = radius + 2;

        for (int dx = -outerRadius; dx <= outerRadius; dx++) {
            for (int dz = -outerRadius; dz <= outerRadius; dz++) {
                double dist = Math.sqrt(dx * dx + dz * dz);
                if (dist > outerRadius) {
                    continue;
                }
                double normalized = Mth.clamp(1.0D - dist / outerRadius, 0.0D, 1.0D);
                int localTop = topY - Mth.floor((1.0D - normalized) * 4.0D);
                int localBottom = Math.max(level.getMinBuildHeight() + 1, bottomY - Mth.floor(normalized * 8.0D));
                int x = centerX + dx;
                int z = centerZ + dz;
                for (int y = localTop; y >= localBottom; y--) {
                    BlockPos pos = new BlockPos(x, y, z);
                    long key = pos.asLong();
                    if (queuedKeys.add(key)) {
                        collector.add(pos);
                    }
                }
            }
        }
    }

    static void spawnSpatialRendParticles(ServerLevel level, Vec3 center, Vec3 forward, Vec3 perpendicular, int halfWidth, double segmentLength, float slashRollDegrees) {
        double crackHalfLength = Math.max(4.0D, segmentLength * 1.05D);
        int samples = Math.max(10, Mth.ceil(crackHalfLength * 3.5D));
        double edgeDistance = halfWidth + 0.55D;
        double rollRadians = Math.toRadians(slashRollDegrees);
        Vec3 slashAxis = perpendicular.scale(Math.cos(rollRadians)).add(0.0D, Math.sin(rollRadians), 0.0D).normalize();

        for (int i = 0; i <= samples; i++) {
            double progress = i / (double) samples;
            double along = Mth.lerp(progress, -crackHalfLength, crackHalfLength);
            Vec3 crackPoint = center.add(forward.scale(along));
            double crackY = crackPoint.y + 0.55D + ((i & 1) == 0 ? 0.08D : 0.18D);

            level.sendParticles(ParticleTypes.END_ROD, crackPoint.x, crackY, crackPoint.z, 1, 0.03D, 0.22D, 0.03D, 0.0D);
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, crackPoint.x, crackY + 0.05D, crackPoint.z, 1, 0.05D, 0.14D, 0.05D, 0.01D);

            if ((i & 1) == 0) {
                level.sendParticles(ParticleTypes.FLASH, crackPoint.x, crackY + 0.08D, crackPoint.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
            }

            Vec3 leftEdge = crackPoint.add(slashAxis.scale(edgeDistance));
            Vec3 rightEdge = crackPoint.add(slashAxis.scale(-edgeDistance));

            level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, leftEdge.x, leftEdge.y + 0.18D, leftEdge.z, 1, 0.08D, 0.12D, 0.08D, 0.004D);
            level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, rightEdge.x, rightEdge.y + 0.18D, rightEdge.z, 1, 0.08D, 0.12D, 0.08D, 0.004D);

            if (i % 3 == 0) {
                level.sendParticles(ParticleTypes.FLASH, leftEdge.x, leftEdge.y + 0.38D, leftEdge.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
                level.sendParticles(ParticleTypes.FLASH, rightEdge.x, rightEdge.y + 0.38D, rightEdge.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
            }
        }

        level.sendParticles(ParticleTypes.CLOUD, center.x, center.y + 0.9D, center.z, 10, halfWidth * 0.22D, 0.3D, halfWidth * 0.22D, 0.01D);
    }

}

