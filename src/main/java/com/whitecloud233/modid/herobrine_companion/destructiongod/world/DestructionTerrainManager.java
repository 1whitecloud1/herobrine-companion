package com.whitecloud233.modid.herobrine_companion.destructiongod.world;

import com.whitecloud233.modid.herobrine_companion.HerobrineCompanion;
import com.whitecloud233.modid.herobrine_companion.config.Config;
import com.whitecloud233.modid.herobrine_companion.entity.HeroEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.core.particles.BlockParticleOption;
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
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.chunk.DataLayer;
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
import java.util.ArrayList;
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
        scheduleTask(new DestructionLightningArcTask(level, caster, start, end, radius, delayTicks));
    }

    public static void startDestructionLightningBeam(ServerLevel level, @Nullable LivingEntity caster, Vec3 start, Vec3 end, double radius, int delayTicks) {
        scheduleTask(new DestructionLightningBeamTask(level, caster, start, end, radius, delayTicks));
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

    static void destroyBlock(ServerLevel level, BlockPos pos, TickBudget budget, DestructionMode mode) {
        destroyBlock(level, pos, budget, mode, true, false);
    }

    static void destroyBlock(ServerLevel level, BlockPos pos, TickBudget budget, DestructionMode mode, boolean allowRestore, boolean absolute) {
        if (!Config.destructionGodTerrainDamageEnabled || mode == DestructionMode.VISUAL || !budget.hasBudget()) {
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

        if (allowRestore) {
            rememberForRestore(level, pos, state);
        }
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_INVISIBLE);
        level.sendParticles(ParticleTypes.POOF, pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D, 2, 0.15D, 0.15D, 0.15D, 0.02D);
        budget.consume();
    }

    static void clearBlocksInstant(ServerLevel level, List<BlockPos> positions, DestructionMode mode, boolean absolute) {
        if (!Config.destructionGodTerrainDamageEnabled || mode == DestructionMode.VISUAL || positions.isEmpty()) {
            return;
        }

        Set<ChunkPos> touchedChunks = new HashSet<>();
        LevelLightEngine lightEngine = level.getLightEngine();
        for (BlockPos pos : positions) {
            if (!level.isLoaded(pos)) {
                continue;
            }
            BlockState state = level.getBlockState(pos);
            if (state.isAir() || !canBreak(level, pos, state, mode, absolute)) {
                continue;
            }

            LevelChunk chunk = level.getChunkAt(pos);
            int sectionIndex = chunk.getSectionIndex(pos.getY());
            if (sectionIndex < 0 || sectionIndex >= chunk.getSections().length) {
                continue;
            }

            if (state.hasBlockEntity() || level.getBlockEntity(pos) != null) {
                chunk.removeBlockEntity(pos);
            }

            LevelChunkSection section = chunk.getSections()[sectionIndex];
            section.setBlockState(pos.getX() & 15, pos.getY() & 15, pos.getZ() & 15, Blocks.AIR.defaultBlockState());
            touchedChunks.add(chunk.getPos());
            lightEngine.checkBlock(pos);
        }

        updateTouchedChunks(level, touchedChunks, lightEngine);
    }

    static void applyFaultSplitChangesInstant(ServerLevel level, List<BlockPos> clearPositions, List<BlockPos> annihilatePositions, Map<BlockPos, BlockState> placements, DestructionMode mode) {
        if (!Config.destructionGodTerrainDamageEnabled || mode == DestructionMode.VISUAL) {
            return;
        }

        Set<ChunkPos> touchedChunks = new HashSet<>();
        LevelLightEngine lightEngine = level.getLightEngine();
        Set<Long> processed = new HashSet<>();

        for (BlockPos pos : clearPositions) {
            long key = pos.asLong();
            if (!processed.add(key) || !level.isLoaded(pos)) {
                continue;
            }
            BlockState state = level.getBlockState(pos);
            if (!canFaultClear(level, pos, state)) {
                continue;
            }
            setBlockStateFast(level, pos, Blocks.AIR.defaultBlockState(), touchedChunks, lightEngine);
        }

        for (BlockPos pos : annihilatePositions) {
            long key = pos.asLong();
            if (!processed.add(key) || !level.isLoaded(pos)) {
                continue;
            }
            BlockState state = level.getBlockState(pos);
            if (!canAnnihilate(level, pos, state)) {
                continue;
            }
            setBlockStateFast(level, pos, Blocks.AIR.defaultBlockState(), touchedChunks, lightEngine);
        }

        for (Map.Entry<BlockPos, BlockState> entry : placements.entrySet()) {
            BlockPos pos = entry.getKey();
            if (!level.isLoaded(pos)) {
                continue;
            }
            BlockState existing = level.getBlockState(pos);
            if (!existing.isAir() && !canFaultClear(level, pos, existing)) {
                continue;
            }
            setBlockStateFast(level, pos, entry.getValue(), touchedChunks, lightEngine);
        }

        updateTouchedChunks(level, touchedChunks, lightEngine);
    }

    private static void setBlockStateFast(ServerLevel level, BlockPos pos, BlockState newState, Set<ChunkPos> touchedChunks, LevelLightEngine lightEngine) {
        LevelChunk chunk = level.getChunkAt(pos);
        int sectionIndex = chunk.getSectionIndex(pos.getY());
        if (sectionIndex < 0 || sectionIndex >= chunk.getSections().length) {
            return;
        }
        BlockState currentState = level.getBlockState(pos);
        if (currentState.hasBlockEntity() || level.getBlockEntity(pos) != null) {
            chunk.removeBlockEntity(pos);
        }
        LevelChunkSection section = chunk.getSections()[sectionIndex];
        section.setBlockState(pos.getX() & 15, pos.getY() & 15, pos.getZ() & 15, newState);
        touchedChunks.add(chunk.getPos());
        lightEngine.checkBlock(pos);
    }

    private static void updateTouchedChunks(ServerLevel level, Set<ChunkPos> touchedChunks, LevelLightEngine lightEngine) {
        for (ChunkPos chunkPos : touchedChunks) {
            LevelChunk chunk = level.getChunk(chunkPos.x, chunkPos.z);
            for (LevelChunkSection section : chunk.getSections()) {
                section.recalcBlockCounts();
            }
            Heightmap.primeHeightmaps(chunk, Set.of(Heightmap.Types.MOTION_BLOCKING, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Heightmap.Types.OCEAN_FLOOR, Heightmap.Types.WORLD_SURFACE));
            chunk.setUnsaved(true);

            for (int i = 0; i < chunk.getSections().length; i++) {
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
                        java.util.Arrays.fill(dataLayer.getData(), (byte) 0xFF);
                    }
                }
            }

            level.getChunkSource().chunkMap.getPlayers(chunkPos, false).forEach(player -> {
                player.connection.send(new ClientboundForgetLevelChunkPacket(chunkPos.x, chunkPos.z));
                player.connection.send(new ClientboundLevelChunkWithLightPacket(chunk, lightEngine, null, null));
            });
        }
    }

    private static void rememberForRestore(ServerLevel level, BlockPos pos, BlockState state) {
        return;
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
        if (hasBlockEntity && !Config.destructionGodBreakContainers) {
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

        return !Config.destructionGodBreakContainers && level.getBlockEntity(pos) != null;
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

        return !Config.destructionGodBreakContainers && level.getBlockEntity(pos) != null;
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

    private static class MountainSplitRendTask extends TerrainTask {
        private final int halfWidth;
        private final int trenchDepth;
        private final int faultDepth;

        private MountainSplitRendTask(ServerLevel level, @Nullable LivingEntity caster, Vec3 origin, Vec3 direction, double maxLength, int halfWidth, int trenchDepth, int faultDepth, int delayTicks) {
            super(level, caster, origin, direction, maxLength, delayTicks);
            this.halfWidth = Math.max(3, halfWidth);
            this.trenchDepth = Math.max(8, trenchDepth);
            this.faultDepth = Math.max(4, faultDepth);
        }

        @Override
        protected boolean doTick(TickBudget budget) {
            int stepsPerTick = 5;
            double stepSize = 1.75D;
            DestructionMode mode = DestructionMode.current();
            LivingEntity caster = this.getCaster();

            for (int step = 0; step < stepsPerTick; step++) {
                if (this.currentLength >= this.maxLength) {
                    Vec3 end = this.centerPoint();
                    int impactRadius = Math.max(5, this.halfWidth / 2 + 2);
                    List<BlockPos> impactBlocks = new ArrayList<>();
                    carveImpactBasin(end, impactRadius, impactBlocks);
                    clearBlocksInstant(this.level, impactBlocks, mode, false);
                    this.level.playSound(null, end.x, end.y, end.z, SoundEvents.WARDEN_SONIC_BOOM, SoundSource.AMBIENT, 6.0F, 0.45F);
                    this.level.playSound(null, end.x, end.y, end.z, SoundEvents.GENERIC_EXPLODE, SoundSource.AMBIENT, 9.0F, 0.55F);
                    damageAndPush(this.level, caster, new AABB(end.x - 16.0D, end.y - 8.0D, end.z - 16.0D, end.x + 16.0D, end.y + 12.0D, end.z + 16.0D), end, 34.0F, 2.2D, 1.25D);
                    this.level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, end.x, end.y + 0.5D, end.z, 4, 1.0D, 0.5D, 1.0D, 0.0D);
                    return true;
                }

                Vec3 center = this.centerPoint();
                BlockPos centerPos = new BlockPos(Mth.floor(center.x), Mth.floor(center.y), Mth.floor(center.z));
                if (!this.level.isLoaded(centerPos)) {
                    return true;
                }

                List<BlockPos> toClear = new ArrayList<>();
                for (int lateral = -this.halfWidth; lateral <= this.halfWidth; lateral++) {
                    Vec3 lateralOffset = this.perpendicular.scale(lateral);
                    int blockX = Mth.floor(center.x + lateralOffset.x);
                    int blockZ = Mth.floor(center.z + lateralOffset.z);
                    int surfaceY = this.level.getHeight(Heightmap.Types.MOTION_BLOCKING, blockX, blockZ);
                    double normalized = 1.0D - (Math.abs(lateral) / (double) this.halfWidth);
                    double trenchFactor = Mth.clamp(normalized, 0.0D, 1.0D);
                    int localDepth = this.trenchDepth + Mth.floor(trenchFactor * this.faultDepth);
                    int edgeCut = Mth.floor((1.0D - trenchFactor) * 4.0D);
                    int topY = Math.min(this.level.getMaxBuildHeight() - 1, surfaceY + 1);
                    int bottomY = Math.max(this.level.getMinBuildHeight() + 1, surfaceY - localDepth - edgeCut);

                    for (int y = topY; y >= bottomY; y--) {
                        toClear.add(new BlockPos(blockX, y, blockZ));
                    }

                    if (trenchFactor > 0.68D) {
                        int extraBottom = Math.max(this.level.getMinBuildHeight() + 1, bottomY - 2 - Mth.floor(trenchFactor * 3.0D));
                        for (int y = bottomY - 1; y >= extraBottom; y--) {
                            toClear.add(new BlockPos(blockX, y, blockZ));
                        }
                    }
                }
                clearBlocksInstant(this.level, toClear, mode, false);

                AABB slice = new AABB(center.x - this.halfWidth - 1.5D, center.y - 8.0D, center.z - this.halfWidth - 1.5D,
                        center.x + this.halfWidth + 1.5D, center.y + 18.0D, center.z + this.halfWidth + 1.5D);
                damageAndPush(this.level, caster, slice, center, 22.0F, 1.6D, 0.85D);
                clearLooseEntities(this.level, caster, slice);

                this.level.sendParticles(ParticleTypes.EXPLOSION, center.x, center.y + 0.8D, center.z, 5, 0.7D, 0.4D, 0.7D, 0.01D);
                this.level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, center.x, center.y + 1.2D, center.z, 8, 0.8D, 0.4D, 0.8D, 0.01D);
                if (step == 0) {
                    this.level.playSound(null, center.x, center.y, center.z, SoundEvents.WITHER_SHOOT, SoundSource.AMBIENT, 2.8F, 0.35F);
                    this.level.playSound(null, center.x, center.y, center.z, SoundEvents.TRIDENT_THUNDER, SoundSource.WEATHER, 4.0F, 0.7F);
                }

                this.currentLength += stepSize;
            }

            return false;
        }

        private void carveImpactBasin(Vec3 center, int radius, List<BlockPos> toClear) {
            int blockCenterX = Mth.floor(center.x);
            int blockCenterZ = Mth.floor(center.z);
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    double distSq = dx * dx + dz * dz;
                    if (distSq > radius * radius) {
                        continue;
                    }
                    int blockX = blockCenterX + dx;
                    int blockZ = blockCenterZ + dz;
                    int surfaceY = this.level.getHeight(Heightmap.Types.MOTION_BLOCKING, blockX, blockZ);
                    int basinDepth = this.trenchDepth / 2 + Math.max(1, radius - Mth.floor(Math.sqrt(distSq)));
                    int topY = Math.min(this.level.getMaxBuildHeight() - 1, surfaceY + 1);
                    int bottomY = Math.max(this.level.getMinBuildHeight() + 1, surfaceY - basinDepth);
                    for (int y = topY; y >= bottomY; y--) {
                        toClear.add(new BlockPos(blockX, y, blockZ));
                    }
                }
            }
        }
    }

    private static class DestructionGodOrbTask extends TerrainTask {
        private final Vec3 impact;
        private final int fallTicks;
        private final double startRadius;
        private final double maxOrbRadius;
        private final double craterRadius;
        private final int craterDepth;
        private final List<BlockPos> pendingCraterBlocks = new ArrayList<>();
        private int age;
        private int craterIndex;
        private boolean impactPrepared;

        private DestructionGodOrbTask(ServerLevel level, @Nullable LivingEntity caster, Vec3 start, Vec3 impact, int fallTicks, double startRadius, double maxOrbRadius, double craterRadius, int craterDepth, int delayTicks) {
            super(level, caster, start, impact.subtract(start), start.distanceTo(impact), delayTicks);
            this.impact = impact;
            this.fallTicks = Math.max(40, fallTicks);
            this.startRadius = Math.max(1.5D, startRadius);
            this.maxOrbRadius = Math.max(this.startRadius + 4.0D, maxOrbRadius);
            this.craterRadius = Math.max(this.maxOrbRadius * 2.0D, craterRadius);
            this.craterDepth = Math.max(24, craterDepth);
        }

        @Override
        protected boolean doTick(TickBudget budget) {
            LivingEntity caster = this.getCaster();
            if (this.age < this.fallTicks) {
                Vec3 orbCenter = this.orbCenter();
                double radius = this.currentRadius();
                this.renderFallingOrb(orbCenter, radius);
                this.suckTerrain(orbCenter, radius, budget);
                this.pullEntities(caster, orbCenter, radius);
                this.age++;
                return false;
            }

            if (!this.impactPrepared) {
                this.prepareImpactCrater();
                this.impactPrepared = true;
                this.level.playSound(null, this.impact.x, this.impact.y, this.impact.z, SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 10.0F, 0.35F);
                this.level.playSound(null, this.impact.x, this.impact.y, this.impact.z, SoundEvents.TRIDENT_THUNDER, SoundSource.WEATHER, 8.0F, 0.45F);
                this.level.playSound(null, this.impact.x, this.impact.y, this.impact.z, SoundEvents.WITHER_DEATH, SoundSource.HOSTILE, 6.0F, 0.65F);
                this.level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, this.impact.x, this.impact.y + 1.0D, this.impact.z, 12, 2.0D, 1.0D, 2.0D, 0.0D);
                this.level.sendParticles(ParticleTypes.FLASH, this.impact.x, this.impact.y + 1.0D, this.impact.z, 10, 2.0D, 1.0D, 2.0D, 0.0D);
                AABB blast = new AABB(this.impact.x - this.craterRadius, this.impact.y - this.craterDepth, this.impact.z - this.craterRadius,
                        this.impact.x + this.craterRadius, this.impact.y + this.craterRadius * 0.45D, this.impact.z + this.craterRadius);
                damageAndPush(this.level, caster, blast, this.impact, 46.0F, 3.2D, 1.4D);
                clearLooseEntities(this.level, caster, blast);
            }

            clearBlocksInstant(this.level, this.pendingCraterBlocks, DestructionMode.current(), true);
            this.pendingCraterBlocks.clear();
            this.craterIndex = 0;
            return true;
        }

        private Vec3 orbCenter() {
            double progress = Mth.clamp(this.age / (double) this.fallTicks, 0.0D, 1.0D);
            Vec3 apex = this.origin.lerp(this.impact, 0.5D).add(0.0D, 18.0D, 0.0D);
            double inv = 1.0D - progress;
            return this.origin.scale(inv * inv).add(apex.scale(2.0D * inv * progress)).add(this.impact.scale(progress * progress));
        }

        private double currentRadius() {
            double progress = Mth.clamp(this.age / (double) this.fallTicks, 0.0D, 1.0D);
            return Mth.lerp(progress * progress, this.startRadius, this.maxOrbRadius);
        }

        private void renderFallingOrb(Vec3 center, double radius) {
            this.level.sendParticles(ParticleTypes.ELECTRIC_SPARK, center.x, center.y, center.z, 12, radius * 0.18D, radius * 0.18D, radius * 0.18D, 0.05D);
            this.level.sendParticles(ParticleTypes.REVERSE_PORTAL, center.x, center.y, center.z, 8, radius * 0.22D, radius * 0.22D, radius * 0.22D, 0.02D);
            if (this.age % 10 == 0) {
                this.level.playSound(null, center.x, center.y, center.z, SoundEvents.RESPAWN_ANCHOR_CHARGE, SoundSource.HOSTILE, 2.2F, 0.55F + this.level.random.nextFloat() * 0.15F);
            }
        }

        private void suckTerrain(Vec3 center, double radius, TickBudget budget) {
            int pulls = 32;
            double scanRadius = radius + 8.0D;
            DestructionMode mode = DestructionMode.current();
            for (int i = 0; i < pulls && budget.hasBudget(); i++) {
                double angle = this.level.random.nextDouble() * Math.PI * 2.0D;
                double distance = Math.sqrt(this.level.random.nextDouble()) * scanRadius;
                int x = Mth.floor(center.x + Math.cos(angle) * distance);
                int z = Mth.floor(center.z + Math.sin(angle) * distance);
                int surfaceY = this.level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z) - 1;
                int y = Mth.clamp(surfaceY - this.level.random.nextInt(4), this.level.getMinBuildHeight() + 1, this.level.getMaxBuildHeight() - 1);
                BlockPos pos = new BlockPos(x, y, z);
                BlockState state = this.level.getBlockState(pos);
                if (state.isAir() || !canBreak(this.level, pos, state, mode, true)) {
                    continue;
                }
                destroyBlock(this.level, pos, budget, mode, false, true);
                Vec3 p = Vec3.atCenterOf(pos);
                this.level.sendParticles(ParticleTypes.POOF, p.x, p.y, p.z, 2, 0.08D, 0.08D, 0.08D, 0.02D);
                this.level.sendParticles(ParticleTypes.PORTAL, p.x, p.y, p.z, 6, (center.x - p.x) * 0.07D, (center.y - p.y) * 0.07D, (center.z - p.z) * 0.07D, 0.16D);
                this.level.sendParticles(ParticleTypes.END_ROD, p.x, p.y, p.z, 1, (center.x - p.x) * 0.03D, (center.y - p.y) * 0.03D, (center.z - p.z) * 0.03D, 0.0D);
                this.level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state), p.x, p.y, p.z, 8, (center.x - p.x) * 0.06D, (center.y - p.y) * 0.06D, (center.z - p.z) * 0.06D, 0.08D);
            }
        }

        private void pullEntities(@Nullable LivingEntity caster, Vec3 center, double radius) {
            AABB area = new AABB(center.x - radius, center.y - radius, center.z - radius, center.x + radius, center.y + radius, center.z + radius);
            List<LivingEntity> targets = this.level.getEntitiesOfClass(LivingEntity.class, area, entity -> entity.isAlive() && entity != caster && !(entity instanceof HeroEntity));
            for (LivingEntity target : targets) {
                Vec3 pull = center.subtract(target.position());
                if (pull.lengthSqr() > 1.0E-4D) {
                    pull = pull.normalize();
                    target.push(pull.x * 0.18D, pull.y * 0.12D + 0.05D, pull.z * 0.18D);
                }
                if (this.age % 15 == 0) {
                    target.hurt(this.level.damageSources().magic(), 4.0F);
                }
            }
        }

        private void prepareImpactCrater() {
            int radius = Mth.ceil(this.craterRadius);
            int centerX = Mth.floor(this.impact.x);
            int centerY = Mth.floor(this.impact.y);
            int centerZ = Mth.floor(this.impact.z);
            double radiusSq = this.craterRadius * this.craterRadius;
            double depth = this.craterDepth;
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    double horizontalSq = dx * dx + dz * dz;
                    if (horizontalSq > radiusSq) {
                        continue;
                    }
                    double horizontalFactor = Math.sqrt(horizontalSq) / this.craterRadius;
                    double localDepth = depth * Math.sqrt(Math.max(0.0D, 1.0D - horizontalFactor * horizontalFactor));
                    int topY = Math.min(this.level.getMaxBuildHeight() - 1, centerY + 4 - Mth.floor(horizontalFactor * 4.0D));
                    int bottomY = Math.max(this.level.getMinBuildHeight() + 1, centerY - Mth.ceil(localDepth));
                    for (int y = topY; y >= bottomY; y--) {
                        double vertical = (centerY - y) / Math.max(1.0D, depth);
                        if (horizontalFactor * horizontalFactor + vertical * vertical <= 1.08D) {
                            this.pendingCraterBlocks.add(new BlockPos(centerX + dx, y, centerZ + dz));
                        }
                    }
                }
            }
        }
    }
}



