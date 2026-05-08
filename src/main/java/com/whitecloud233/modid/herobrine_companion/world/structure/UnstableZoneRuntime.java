package com.whitecloud233.modid.herobrine_companion.world.structure;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class UnstableZoneRuntime {
    public static final int BLOCK_MUTATION_INTERVAL_TICKS = 20 * 10;
    private static final String DATA_NAME = "herobrine_companion_unstable_zone_registry";

    private static final List<Block> GENERATION_BLOCK_POOL = List.of(
            Blocks.NETHERRACK,
            Blocks.NETHERRACK,
            Blocks.SOUL_SOIL,
            Blocks.BLACKSTONE,
            Blocks.BASALT,
            Blocks.MAGMA_BLOCK,
            Blocks.END_STONE,
            Blocks.TINTED_GLASS,
            Blocks.WET_SPONGE,
            Blocks.GILDED_BLACKSTONE,
            Blocks.CRYING_OBSIDIAN
    );

    private static final List<Block> FLOAT_BLOCK_POOL = List.of(
            Blocks.CRYING_OBSIDIAN,
            Blocks.TINTED_GLASS
    );

    private static final Set<Block> SOURCE_ANOMALY_BLOCKS = Set.of(
            Blocks.SPAWNER,
            Blocks.NETHERRACK,
            Blocks.SOUL_SOIL,
            Blocks.BLACKSTONE,
            Blocks.BASALT,
            Blocks.MAGMA_BLOCK,
            Blocks.END_STONE,
            Blocks.TINTED_GLASS,
            Blocks.WET_SPONGE,
            Blocks.GILDED_BLACKSTONE,
            Blocks.CRYING_OBSIDIAN
    );

    private static volatile List<Block> mutationBlockPool;

    private UnstableZoneRuntime() {
    }

    public static BlockState getRandomGenerationBlock(RandomSource random) {
        return GENERATION_BLOCK_POOL.get(random.nextInt(GENERATION_BLOCK_POOL.size())).defaultBlockState();
    }

    public static BlockState getRandomFloatBlock(RandomSource random) {
        return FLOAT_BLOCK_POOL.get(random.nextInt(FLOAT_BLOCK_POOL.size())).defaultBlockState();
    }

    public static boolean isInUnstableZone(ServerLevel level, BlockPos pos) {
        return getUnstableZoneStart(level, pos).isValid();
    }

    public static boolean isTrackedAnomalyBlock(ServerLevel level, BlockPos pos, BlockState state) {
        if (level == null || pos == null || state == null || state.isAir()) {
            return false;
        }
        SavedZones savedZones = SavedZones.get(level);
        if (!savedZones.isTracked(pos)) {
            if (!isSourceAnomalyBlock(state)) {
                return false;
            }
            ensureTrackedZoneAt(level, pos);
            if (!savedZones.isTracked(pos)) {
                return false;
            }
        }
        return true;
    }

    public static void registerGeneratedBlocks(ServerLevel level, BoundingBox bounds, Collection<BlockPos> positions) {
        if (level == null || bounds == null || positions == null || positions.isEmpty()) {
            return;
        }
        SavedZones.get(level).addTrackedPositions(bounds, positions);
    }

    public static void removeTrackedBlocks(ServerLevel level, Collection<BlockPos> positions) {
        if (level == null || positions == null || positions.isEmpty()) {
            return;
        }
        SavedZones.get(level).removeTrackedPositions(positions);
    }

    public static void ensureTrackedZoneAt(ServerLevel level, BlockPos pos) {
        if (level == null || pos == null) {
            return;
        }
        StructureStart start = getUnstableZoneStart(level, pos);
        if (!start.isValid()) {
            return;
        }
        BoundingBox bounds = start.getBoundingBox();
        SavedZones savedZones = SavedZones.get(level);
        if (savedZones.hasZone(bounds) || !areBoundsLoaded(level, bounds)) {
            return;
        }
        Set<BlockPos> backfilled = scanExistingAnomalyBlocks(level, bounds);
        if (!backfilled.isEmpty()) {
            savedZones.addTrackedPositions(bounds, backfilled);
        }
    }

    public static void tick(ServerLevel level) {
        if (level == null || level.isClientSide()) {
            return;
        }
        long gameTime = level.getGameTime();
        if (gameTime <= 0L || gameTime % BLOCK_MUTATION_INTERVAL_TICKS != 0L) {
            return;
        }

        for (var player : level.players()) {
            BlockPos playerPos = player.blockPosition();
            if (isInUnstableZone(level, playerPos)) {
                ensureTrackedZoneAt(level, playerPos);
            }
        }

        SavedZones savedZones = SavedZones.get(level);
        if (savedZones.isEmpty()) {
            return;
        }

        RandomSource random = RandomSource.create(level.getSeed() ^ gameTime);
        List<BlockPos> stalePositions = new ArrayList<>();

        for (SavedZones.ZoneRecord zone : savedZones.getZones()) {
            if (!areBoundsLoaded(level, zone.bounds())) {
                continue;
            }
            for (long packedPos : zone.positions()) {
                BlockPos trackedPos = BlockPos.of(packedPos);
                BlockState currentState = level.getBlockState(trackedPos);
                if (currentState.isAir()) {
                    stalePositions.add(trackedPos);
                    continue;
                }
                if (currentState.is(Blocks.SPAWNER)) {
                    continue;
                }
                BlockState replacementState = getRandomMutationBlock(random, currentState);
                if (replacementState == null || replacementState.equals(currentState)) {
                    continue;
                }
                level.setBlock(trackedPos, replacementState, 3);
            }
        }

        if (!stalePositions.isEmpty()) {
            savedZones.removeTrackedPositions(stalePositions);
        }
    }

    private static StructureStart getUnstableZoneStart(ServerLevel level, BlockPos pos) {
        Structure structure = level.registryAccess().registryOrThrow(Registries.STRUCTURE).get(ModStructures.UNSTABLE_ZONE_KEY);
        if (structure == null) {
            return StructureStart.INVALID_START;
        }
        return level.structureManager().getStructureWithPieceAt(pos, structure);
    }

    private static boolean isSourceAnomalyBlock(BlockState state) {
        return state != null && SOURCE_ANOMALY_BLOCKS.contains(state.getBlock());
    }

    private static Set<BlockPos> scanExistingAnomalyBlocks(ServerLevel level, BoundingBox bounds) {
        if (!areBoundsLoaded(level, bounds)) {
            return Collections.emptySet();
        }
        Set<BlockPos> positions = new LinkedHashSet<>();
        int minY = Math.max(level.getMinBuildHeight(), bounds.minY());
        int maxY = Math.min(level.getMaxBuildHeight() - 1, bounds.maxY());
        for (int x = bounds.minX(); x <= bounds.maxX(); x++) {
            for (int z = bounds.minZ(); z <= bounds.maxZ(); z++) {
                for (int y = minY; y <= maxY; y++) {
                    BlockPos scanPos = new BlockPos(x, y, z);
                    BlockState state = level.getBlockState(scanPos);
                    if (isSourceAnomalyBlock(state)) {
                        positions.add(scanPos.immutable());
                    }
                }
            }
        }
        return positions;
    }

    private static boolean areBoundsLoaded(ServerLevel level, BoundingBox bounds) {
        int minChunkX = bounds.minX() >> 4;
        int maxChunkX = bounds.maxX() >> 4;
        int minChunkZ = bounds.minZ() >> 4;
        int maxChunkZ = bounds.maxZ() >> 4;
        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                if (!level.hasChunk(chunkX, chunkZ)) {
                    return false;
                }
            }
        }
        return true;
    }

    private static BlockState getRandomMutationBlock(RandomSource random, BlockState currentState) {
        List<Block> pool = getMutationBlockPool();
        if (pool.isEmpty()) {
            return currentState;
        }
        Block currentBlock = currentState.getBlock();
        for (int attempt = 0; attempt < 8; attempt++) {
            Block candidate = pool.get(random.nextInt(pool.size()));
            if (candidate != currentBlock) {
                return candidate.defaultBlockState();
            }
        }
        for (Block candidate : pool) {
            if (candidate != currentBlock) {
                return candidate.defaultBlockState();
            }
        }
        return currentState;
    }

    private static List<Block> getMutationBlockPool() {
        List<Block> cached = mutationBlockPool;
        if (cached != null) {
            return cached;
        }
        synchronized (UnstableZoneRuntime.class) {
            cached = mutationBlockPool;
            if (cached != null) {
                return cached;
            }
            List<Block> built = buildMutationBlockPool();
            mutationBlockPool = built;
            return built;
        }
    }

    private static List<Block> buildMutationBlockPool() {
        List<Block> blocks = new ArrayList<>();
        for (Block block : ForgeRegistries.BLOCKS.getValues()) {
            if (block == null) {
                continue;
            }
            ResourceLocation key = ForgeRegistries.BLOCKS.getKey(block);
            if (key == null || !Objects.equals("minecraft", key.getNamespace())) {
                continue;
            }
            BlockState state = block.defaultBlockState();
            if (state.isAir()) {
                continue;
            }
            if (!state.getFluidState().isEmpty()) {
                continue;
            }
            if (state.hasBlockEntity()) {
                continue;
            }
            if (block instanceof FallingBlock) {
                continue;
            }
            if (!state.canOcclude()) {
                continue;
            }
            if (!state.isCollisionShapeFullBlock(EmptyBlockGetter.INSTANCE, BlockPos.ZERO)) {
                continue;
            }
            blocks.add(block);
        }
        if (blocks.isEmpty()) {
            blocks.addAll(new LinkedHashSet<>(GENERATION_BLOCK_POOL));
        }
        return List.copyOf(blocks);
    }

    public static final class SavedZones extends SavedData {
        private final Map<String, ZoneRecord> zones = new HashMap<>();
        private final Set<Long> trackedPositions = new HashSet<>();

        public static SavedZones get(ServerLevel level) {
            return level.getDataStorage().computeIfAbsent(SavedZones::load, SavedZones::new, DATA_NAME);
        }

        public static SavedZones load(CompoundTag tag) {
            SavedZones data = new SavedZones();
            ListTag zonesTag = tag.getList("Zones", Tag.TAG_COMPOUND);
            for (int i = 0; i < zonesTag.size(); i++) {
                CompoundTag zoneTag = zonesTag.getCompound(i);
                BoundingBox bounds = new BoundingBox(
                        zoneTag.getInt("MinX"),
                        zoneTag.getInt("MinY"),
                        zoneTag.getInt("MinZ"),
                        zoneTag.getInt("MaxX"),
                        zoneTag.getInt("MaxY"),
                        zoneTag.getInt("MaxZ")
                );
                ZoneRecord zone = new ZoneRecord(bounds);
                long[] rawPositions = zoneTag.getLongArray("Positions");
                for (long packedPos : rawPositions) {
                    zone.positions.add(packedPos);
                    data.trackedPositions.add(packedPos);
                }
                data.zones.put(zoneId(bounds), zone);
            }
            return data;
        }

        @Override
        public CompoundTag save(CompoundTag tag) {
            ListTag zonesTag = new ListTag();
            for (ZoneRecord zone : zones.values()) {
                CompoundTag zoneTag = new CompoundTag();
                zoneTag.putInt("MinX", zone.bounds.minX());
                zoneTag.putInt("MinY", zone.bounds.minY());
                zoneTag.putInt("MinZ", zone.bounds.minZ());
                zoneTag.putInt("MaxX", zone.bounds.maxX());
                zoneTag.putInt("MaxY", zone.bounds.maxY());
                zoneTag.putInt("MaxZ", zone.bounds.maxZ());
                long[] packedPositions = zone.positions.stream().mapToLong(Long::longValue).toArray();
                zoneTag.putLongArray("Positions", packedPositions);
                zonesTag.add(zoneTag);
            }
            tag.put("Zones", zonesTag);
            return tag;
        }

        public boolean isEmpty() {
            return zones.isEmpty();
        }

        public boolean isTracked(BlockPos pos) {
            return trackedPositions.contains(pos.asLong());
        }

        public boolean hasZone(BoundingBox bounds) {
            return zones.containsKey(zoneId(bounds));
        }

        public Collection<ZoneRecord> getZones() {
            return zones.values();
        }

        public void addTrackedPositions(BoundingBox bounds, Collection<BlockPos> positions) {
            if (positions == null || positions.isEmpty()) {
                return;
            }
            ZoneRecord zone = zones.computeIfAbsent(zoneId(bounds), key -> new ZoneRecord(bounds));
            boolean changed = false;
            for (BlockPos pos : positions) {
                if (pos == null) {
                    continue;
                }
                long packedPos = pos.asLong();
                if (zone.positions.add(packedPos)) {
                    trackedPositions.add(packedPos);
                    changed = true;
                }
            }
            if (changed) {
                setDirty();
            }
        }

        public void removeTrackedPositions(Collection<BlockPos> positions) {
            if (positions == null || positions.isEmpty()) {
                return;
            }
            boolean changed = false;
            Set<Long> removals = new HashSet<>();
            for (BlockPos pos : positions) {
                if (pos != null) {
                    removals.add(pos.asLong());
                }
            }
            if (removals.isEmpty()) {
                return;
            }
            trackedPositions.removeAll(removals);
            for (ZoneRecord zone : zones.values()) {
                if (zone.positions.removeAll(removals)) {
                    changed = true;
                }
            }
            if (zones.values().removeIf(zone -> zone.positions.isEmpty())) {
                changed = true;
            }
            if (changed) {
                setDirty();
            }
        }

        public record ZoneRecord(BoundingBox bounds, Set<Long> positions) {
            private ZoneRecord(BoundingBox bounds) {
                this(bounds, new HashSet<>());
            }

            @Override
            public Set<Long> positions() {
                return new HashSet<>(positions);
            }
        }
    }

    private static String zoneId(BoundingBox bounds) {
        return bounds.minX() + ":" + bounds.minZ() + ":" + bounds.maxX() + ":" + bounds.maxZ();
    }
}


