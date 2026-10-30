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
        return SavedZones.get(level).isTracked(pos);
    }

    /**
     * 是否完全没有被追踪的异常方块。
     * 供 AI 扫描逻辑零成本早退——没有任何异常方块时不必做任何范围查询。
     */
    public static boolean hasNoTrackedAnomalyBlocks(ServerLevel level) {
        return level == null || SavedZones.get(level).hasNoTrackedPositions();
    }

    /**
     * 收集 center 周围 radius 内已被追踪的异常方块坐标（最多 max 个）。
     *
     * <p>取代「遍历整个立方体逐个 {@code getBlockState} + isTracked」的做法：
     * 复杂度从 O((2r+1)^3) 降到 O(已追踪方块数)，且每次命中都不会重复查询同一 chunk。</p>
     */
    public static List<BlockPos> collectTrackedAnomalyBlocks(ServerLevel level, BlockPos center, int radius, int max) {
        if (level == null || center == null || max <= 0) {
            return List.of();
        }
        return SavedZones.get(level).collectTrackedWithin(
                center.getX(), center.getY(), center.getZ(), radius, max);
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

    public static void tick(ServerLevel level) {
        if (level == null || level.isClientSide()) {
            return;
        }
        long gameTime = level.getGameTime();
        if (gameTime <= 0L || gameTime % BLOCK_MUTATION_INTERVAL_TICKS != 0L) {
            return;
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

        /** 是否完全没有被追踪的异常方块（供调用方零成本早退）。 */
        public boolean hasNoTrackedPositions() {
            return trackedPositions.isEmpty();
        }

        /**
         * 收集落在以 center 为中心、半径 radius（三轴独立，等价于边长 2r+1 的立方体）内的
         * 已追踪异常方块坐标。最多返回 max 个。
         *
         * <p><b>为什么用集合反查而不是全量扫描</b>：{@link #isTracked(BlockPos)} 的语义等价于
         * {@code trackedPositions.contains(pos)}，所以「立方体内哪些位置被追踪」只需要遍历
         * {@code trackedPositions}（通常几十到几百个），而不是遍历 27 万个坐标逐个查询。
         * 调用方（如 {@code HeroAnomalySupport}）原本每 10 tick 做 274,625 次
         * {@code getBlockState}，改造后复杂度只与已追踪方块数量成正比。</p>
         *
         * @return 命中坐标；当命中数超过 max 时按「到中心的平方距离」由近到远取前 max 个，
         *         否则原样返回全部命中项。无命中时返回空列表
         */
        public List<BlockPos> collectTrackedWithin(int centerX, int centerY, int centerZ, int radius, int max) {
            if (trackedPositions.isEmpty() || max <= 0) {
                return List.of();
            }
            int minX = centerX - radius, maxX = centerX + radius;
            int minY = centerY - radius, maxY = centerY + radius;
            int minZ = centerZ - radius, maxZ = centerZ + radius;
            // 先收集命中项再按「到中心的平方距离」排序：HashSet 的遍历顺序是哈希序，
            // 直接取前 max 个会让 Hero 在不同方块之间无规律跳转（而且同一世界重启后顺序还会变）。
            // 排序保证最近优先，行为确定，代价只有命中项数量级的 n log n（通常是个位数到几十）。
            List<BlockPos> found = new ArrayList<>();
            for (long packed : trackedPositions) {
                BlockPos pos = BlockPos.of(packed);
                if (pos.getX() < minX || pos.getX() > maxX) continue;
                if (pos.getY() < minY || pos.getY() > maxY) continue;
                if (pos.getZ() < minZ || pos.getZ() > maxZ) continue;
                found.add(pos);
            }
            if (found.size() <= 1) {
                return found;
            }
            // max 远大于命中数时（例如 AOE 净化要清掉范围内全部异常方块）不需要排序，
            // 排序只服务于「只要最近 max 个」的调用方。
            if (found.size() > max) {
                found.sort(java.util.Comparator.comparingLong(p -> {
                    long dx = (long) p.getX() - centerX;
                    long dy = (long) p.getY() - centerY;
                    long dz = (long) p.getZ() - centerZ;
                    return dx * dx + dy * dy + dz * dz;
                }));
                return new ArrayList<>(found.subList(0, max));
            }
            return found;
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


