package com.whitecloud233.herobrine_companion.destructiongod.world;

import com.mojang.math.Transformation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import javax.annotation.Nullable;
import java.lang.reflect.Method;
import java.util.*;

final class ScytheFaultSplitTask extends TerrainTask {
    private static final String FAULT_DISPLAY_TAG = "herobrine_fault_display";
    @Nullable
    private static final Method DISPLAY_SET_TRANSFORMATION = findDeclaredMethod(Display.class, "setTransformation", Transformation.class);
    @Nullable
    private static final Method DISPLAY_SET_INTERPOLATION_DURATION = findDeclaredMethod(Display.class, "setTransformationInterpolationDuration", int.class);
    @Nullable
    private static final Method DISPLAY_SET_INTERPOLATION_DELAY = findDeclaredMethod(Display.class, "setTransformationInterpolationDelay", int.class);
    @Nullable
    private static final Method DISPLAY_SET_VIEW_RANGE = findDeclaredMethod(Display.class, "setViewRange", float.class);
    @Nullable
    private static final Method DISPLAY_SET_WIDTH = findDeclaredMethod(Display.class, "setWidth", float.class);
    @Nullable
    private static final Method DISPLAY_SET_HEIGHT = findDeclaredMethod(Display.class, "setHeight", float.class);
    @Nullable
    private static final Method DISPLAY_SET_SHADOW_RADIUS = findDeclaredMethod(Display.class, "setShadowRadius", float.class);
    @Nullable
    private static final Method DISPLAY_SET_SHADOW_STRENGTH = findDeclaredMethod(Display.class, "setShadowStrength", float.class);
    @Nullable
    private static final Method BLOCK_DISPLAY_SET_BLOCK_STATE = findDeclaredMethod(Display.BlockDisplay.class, "setBlockState", BlockState.class);

    private static final int DISPLAY_MOVE_TICKS = 132;
    private static final int DISPLAY_HOLD_TICKS = 36;
    private static final int MAX_DISPLAY_BLOCKS = 8192;
    private static final int MAX_CAPTURE_BLOCKS = 180000;
    private static final int DISPLAY_SPAWNS_PER_TICK = 256;
    private static final int PLACEMENT_BLOCKS_PER_TICK = 192;
    private static final int BLOCK_MUTATION_BATCH_SIZE = 1024;
    private static final int SURFACE_MOVE_DEPTH = 4;
    private static final int SURFACE_VISUAL_DEPTH = 2;
    private static final int SIDE_WALL_MOVE_DEPTH = 7;
    private static final int SIDE_WALL_VISUAL_DEPTH = 4;
    private static final double COLUMN_LINE_PADDING = 0.66D;
    private static final double CORRIDOR_LINE_PADDING = 0.72D;
    private static final double PLATE_SEGMENT_LENGTH = 9.0D;
    private static final double CAPTURE_START_PADDING = 2.5D;
    private static final double CAPTURE_END_PADDING = 3.5D;
    private static final double DISPLAY_SHIFT_PER_SIDE = 0.50D;
    private static final int MIN_GROUP_COLUMNS = 3;

    private final int terrainHalfWidth;
    private final int shiftDistance;
    private final int corridorHalfWidth;
    private final int visualHalfWidth;
    private final List<BlockPos> pendingCorridorClears = new ArrayList<>();
    private final List<BlockPos> pendingPlateSourceClears = new ArrayList<>();
    private final List<FaultPlacement> pendingPlacements = new ArrayList<>();
    private final List<FaultPlateSnapshot> pendingSnapshots = new ArrayList<>();
    private final List<FaultDisplayGroup> displayGroups = new ArrayList<>();
    private boolean captured;
    private boolean displaysPrimed;
    private boolean animationStarted;
    private boolean finalSolidified;
    private int animationAge;
    private int capturedSnapshotBlocks;
    private int capturedDisplayBlocks;
    private int displaySpawnSnapshotIndex;
    private int displaySpawnEntryIndex;
    private int pendingPlacementIndex;
    private final Vec3 effectCenter;
    private final AABB ruptureBand;
    private final double displayShift;

    ScytheFaultSplitTask(ServerLevel level, @Nullable LivingEntity caster, Vec3 origin, Vec3 direction, double maxLength, int terrainHalfWidth, int lineHalfWidth, int shiftDistance, int delayTicks) {
        super(level, caster, origin, direction, maxLength, delayTicks);
        this.terrainHalfWidth = Math.max(5, terrainHalfWidth);
        this.shiftDistance = Math.max(1, shiftDistance);
        this.corridorHalfWidth = 0;
        this.visualHalfWidth = Math.min(12, Math.max(4, this.terrainHalfWidth + Math.max(0, lineHalfWidth)));
        this.displayShift = Mth.clamp(this.shiftDistance * 0.5D, DISPLAY_SHIFT_PER_SIDE, 0.75D);
        this.effectCenter = this.origin.add(this.forward.scale(this.maxLength * 0.5D));
        this.ruptureBand = new AABB(this.effectCenter.x - this.terrainHalfWidth - this.shiftDistance - 2.0D, this.effectCenter.y - 12.0D, this.effectCenter.z - this.terrainHalfWidth - this.shiftDistance - 2.0D,
                this.effectCenter.x + this.terrainHalfWidth + this.shiftDistance + 2.0D, this.effectCenter.y + 20.0D, this.effectCenter.z + this.terrainHalfWidth + this.shiftDistance + 2.0D);
    }

    @Override
    protected boolean doTick(TickBudget budget) {
        LivingEntity caster = this.getCaster();
        DestructionMode mode = DestructionMode.DIVINE;

        if (!this.captured) {
            if (!this.captureFaultState()) {
                return true;
            }
            this.captured = true;
            return false;
        }

        if (!this.animationStarted) {
            this.animationStarted = true;
            this.animationAge = 0;
            if (!this.displaysPrimed) {
                this.spawnDisplayGroupsMerged();
                this.displaysPrimed = true;
            }
            this.beginDisplayGroupSplit();
            this.clearFaultShellForDisplays();
            this.releaseCapturedFault(mode, caster);
            this.level.playSound(null, this.effectCenter.x, this.effectCenter.y + 0.2D, this.effectCenter.z, SoundEvents.WARDEN_SONIC_BOOM, SoundSource.WEATHER, 6.0F, 0.38F);
            this.level.playSound(null, this.effectCenter.x, this.effectCenter.y + 0.2D, this.effectCenter.z, SoundEvents.TRIDENT_THUNDER, SoundSource.WEATHER, 6.6F, 0.60F);
            this.level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, this.effectCenter.x, this.effectCenter.y + 0.8D, this.effectCenter.z, 5, 1.2D, 0.5D, 1.2D, 0.0D);
            this.spawnFaultLineBurst();
            return false;
        }

        this.animationAge++;
        if (!this.finalSolidified && this.animationAge >= DISPLAY_MOVE_TICKS) {
            if (this.restoreSourcePlateGroups(budget)) {
                this.finalSolidified = true;
                this.discardDisplays();
                return true;
            }
        }
        return false;
    }

    private boolean captureFaultState() {
        this.capturedSnapshotBlocks = 0;
        this.capturedDisplayBlocks = 0;
        this.pendingCorridorClears.clear();
        this.pendingPlateSourceClears.clear();
        this.pendingPlacements.clear();
        this.pendingSnapshots.clear();
        this.displayGroups.clear();
        this.displaySpawnSnapshotIndex = 0;
        this.displaySpawnEntryIndex = 0;
        this.pendingPlacementIndex = 0;
        List<BlockPos> corridorClears = new ArrayList<>();
        List<BlockPos> plateSourceClears = new ArrayList<>();
        Set<Long> corridorClearKeys = new HashSet<>();
        Set<Long> plateSourceClearKeys = new HashSet<>();
        List<FaultColumnSample> corridorColumns = new ArrayList<>();
        List<FaultColumnSample> leftColumns = new ArrayList<>();
        List<FaultColumnSample> rightColumns = new ArrayList<>();
        Set<Long> leftColumnKeys = new HashSet<>();
        Set<Long> rightColumnKeys = new HashSet<>();
        Vec3 end = this.origin.add(this.forward.scale(this.maxLength));
        Vec3 paddedStart = this.origin.subtract(this.forward.scale(CAPTURE_START_PADDING));
        Vec3 paddedEnd = end.add(this.forward.scale(CAPTURE_END_PADDING));
        Set<Long> corridorColumnKeys = this.collectCorridorColumnKeys(paddedStart, paddedEnd);
        double lateralReach = this.visualHalfWidth + 1.25D;
        int minX = Mth.floor(Math.min(paddedStart.x, paddedEnd.x) - lateralReach - 1.0D);
        int maxX = Mth.floor(Math.max(paddedStart.x, paddedEnd.x) + lateralReach + 1.0D);
        int minZ = Mth.floor(Math.min(paddedStart.z, paddedEnd.z) - lateralReach - 1.0D);
        int maxZ = Mth.floor(Math.max(paddedStart.z, paddedEnd.z) + lateralReach + 1.0D);

        for (int blockX = minX; blockX <= maxX; blockX++) {
            for (int blockZ = minZ; blockZ <= maxZ; blockZ++) {
                BlockPos samplePos = new BlockPos(blockX, this.level.getMinBuildHeight() + 1, blockZ);
                if (!this.level.isLoaded(samplePos)) {
                    continue;
                }

                Vec3 center = new Vec3(blockX + 0.5D, this.origin.y, blockZ + 0.5D);
                Vec3 delta = center.subtract(this.origin);
                double along = delta.dot(this.forward);
                if (along < -CAPTURE_START_PADDING || along > this.maxLength + CAPTURE_END_PADDING) {
                    continue;
                }

                double lateral = delta.dot(this.perpendicular);
                long key = columnKey(blockX, blockZ);
                int surfaceY = this.level.getHeight(Heightmap.Types.MOTION_BLOCKING, blockX, blockZ);
                double absLateral = Math.abs(lateral);
                if (corridorColumnKeys.contains(key)) {
                    corridorColumns.add(new FaultColumnSample(blockX, blockZ, surfaceY, along, lateral));
                    continue;
                }

                if (absLateral > this.visualHalfWidth + COLUMN_LINE_PADDING) {
                    continue;
                }

                if (lateral >= 0.0D) {
                    if (leftColumnKeys.add(key)) {
                        leftColumns.add(new FaultColumnSample(blockX, blockZ, surfaceY, along, lateral));
                    }
                } else {
                    if (rightColumnKeys.add(key)) {
                        rightColumns.add(new FaultColumnSample(blockX, blockZ, surfaceY, along, lateral));
                    }
                }
            }
        }

        if (leftColumns.isEmpty() && rightColumns.isEmpty() && corridorColumns.isEmpty()) {
            return false;
        }

        List<FaultPlateSnapshot> groupedSnapshots = new ArrayList<>();
        List<FaultPlateGroup> orderedGroups = new ArrayList<>();
        orderedGroups.addAll(this.buildFaultPlateGroups(leftColumns, corridorColumnKeys, true));
        orderedGroups.addAll(this.buildFaultPlateGroups(rightColumns, corridorColumnKeys, false));
        orderedGroups.sort(Comparator
                .comparingDouble(FaultPlateGroup::minAlong)
                .thenComparingDouble(FaultPlateGroup::maxAlong)
                .thenComparingInt(FaultPlateGroup::sideOrder));
        this.collectCorridorClears(corridorColumns, corridorClears, corridorClearKeys);
        this.capturePlateGroups(orderedGroups, groupedSnapshots, plateSourceClears, plateSourceClearKeys);
        groupedSnapshots = this.applyDistributedDisplayBudget(groupedSnapshots);
        this.collectRestorePlacements(groupedSnapshots, this.pendingPlacements);

        if (groupedSnapshots.isEmpty() && corridorClears.isEmpty()) {
            return false;
        }

        this.pendingCorridorClears.addAll(corridorClears);
        this.pendingPlateSourceClears.addAll(plateSourceClears);
        this.pendingSnapshots.addAll(groupedSnapshots);
        return true;
    }

    private void spawnDisplayGroupsMerged() {
        for (FaultPlateSnapshot snapshot : this.pendingSnapshots) {
            FaultPaletteSnapshot visualSnapshot = snapshot.visualSnapshot();
            if (visualSnapshot.entries().isEmpty()) {
                continue;
            }
            List<FaultDisplayBlock> groupBlocks = new ArrayList<>();
            for (FaultPaletteEntry entry : visualSnapshot.entries()) {
                BlockState state = visualSnapshot.palette().get(entry.paletteIndex());
                Display.BlockDisplay display = createFaultBlockDisplay(this.level, entry.pos(), state, Vec3.ZERO, new Vec3(1.0D, 1.0D, 1.0D));
                if (display != null) {
                    groupBlocks.add(new FaultDisplayBlock(display, state));
                }
            }
            if (!groupBlocks.isEmpty()) {
                this.displayGroups.add(new FaultDisplayGroup(List.copyOf(groupBlocks), snapshot.fullSnapshot(), snapshot.translation(), snapshot.blockOffset()));
            }
        }
        this.pendingSnapshots.clear();
    }

    private void releaseCapturedFault(DestructionMode mode, @Nullable LivingEntity caster) {
        this.startDisplayGroupAnimations();
        DestructionTerrainManager.damageAndPush(this.level, caster, this.ruptureBand, this.effectCenter, 24.0F, 1.7D, 0.9D);
        DestructionTerrainManager.clearLooseEntities(this.level, caster, this.ruptureBand);
        this.level.playSound(null, this.effectCenter.x, this.effectCenter.y, this.effectCenter.z, SoundEvents.WITHER_SHOOT, SoundSource.AMBIENT, 3.2F, 0.30F);
        this.level.playSound(null, this.effectCenter.x, this.effectCenter.y, this.effectCenter.z, SoundEvents.GENERIC_EXPLODE, SoundSource.AMBIENT, 4.0F, 0.62F);
    }

    private void beginDisplayGroupSplit() {
        for (FaultDisplayGroup group : this.displayGroups) {
            for (FaultDisplayBlock block : group.blocks()) {
                if (block.display().isRemoved()) {
                    continue;
                }
                applyFaultDisplayState(block.display(), block.state(), Vec3.ZERO, new Vec3(1.0D, 1.0D, 1.0D), 0);
            }
        }
    }

    private void clearFaultShellForDisplays() {
        if (this.pendingCorridorClears.isEmpty() && this.pendingPlateSourceClears.isEmpty()) {
            return;
        }
        List<BlockPos> clears = new ArrayList<>(this.pendingCorridorClears.size() + this.pendingPlateSourceClears.size());
        clears.addAll(this.pendingCorridorClears);
        clears.addAll(this.pendingPlateSourceClears);
        DestructionTerrainManager.applyFaultSplitChangesInstant(this.level, clears, List.of(), Map.of(), DestructionMode.DIVINE);
        this.pendingCorridorClears.clear();
        this.pendingPlateSourceClears.clear();
    }

    private void collectRestorePlacements(List<FaultPlateSnapshot> snapshots, List<FaultPlacement> placements) {
        Set<Long> placedKeys = new HashSet<>();
        for (FaultPlateSnapshot group : snapshots) {
            FaultPaletteSnapshot snapshot = group.fullSnapshot();
            for (FaultPaletteEntry entry : snapshot.entries()) {
                BlockPos target = entry.pos();
                if (placedKeys.add(target.asLong())) {
                    placements.add(new FaultPlacement(target, snapshot.palette().get(entry.paletteIndex())));
                }
            }
        }
    }

    private void capturePlateGroups(List<FaultPlateGroup> groups, List<FaultPlateSnapshot> groupedSnapshots, List<BlockPos> sourceClears, Set<Long> sourceClearKeys) {
        for (FaultPlateGroup group : groups) {
            if (this.capturedSnapshotBlocks >= MAX_CAPTURE_BLOCKS) {
                return;
            }
            FaultPaletteBuilder fullBuilder = new FaultPaletteBuilder();
            FaultPaletteBuilder visualBuilder = new FaultPaletteBuilder();
            int groupSnapshotBlocks = 0;
            boolean truncatedGroup = false;
            for (FaultColumnSample column : group.columns()) {
                int remainingCaptureBudget = MAX_CAPTURE_BLOCKS - this.capturedSnapshotBlocks - groupSnapshotBlocks;
                if (remainingCaptureBudget <= 0) {
                    truncatedGroup = true;
                    break;
                }
                ColumnCaptureResult captureResult = this.collectFaultPlateColumn(column, fullBuilder, visualBuilder, remainingCaptureBudget);
                groupSnapshotBlocks += captureResult.addedBlocks();
                if (captureResult.truncated()) {
                    truncatedGroup = true;
                    break;
                }
            }
            if (truncatedGroup) {
                return;
            }
            FaultPaletteSnapshot fullSnapshot = fullBuilder.snapshot();
            if (fullSnapshot.entries().isEmpty()) {
                continue;
            }
            FaultPaletteSnapshot visualSnapshot = visualBuilder.snapshot();
            for (FaultPaletteEntry entry : visualSnapshot.entries()) {
                BlockPos pos = entry.pos();
                if (sourceClearKeys.add(pos.asLong())) {
                    sourceClears.add(pos);
                }
            }
            this.capturedSnapshotBlocks += groupSnapshotBlocks;
            groupedSnapshots.add(new FaultPlateSnapshot(fullSnapshot, visualSnapshot, group.translation(), group.blockOffset()));
        }
    }

    private void collectCorridorClears(List<FaultColumnSample> corridorColumns, List<BlockPos> sourceClears, Set<Long> sourceClearKeys) {
        int minY = this.level.getMinBuildHeight();
        List<FaultColumnSample> sortedColumns = new ArrayList<>(corridorColumns);
        sortedColumns.sort(Comparator.comparingDouble(FaultColumnSample::along));
        List<List<BlockPos>> columnStacks = new ArrayList<>();
        int maxStackDepth = 0;
        for (FaultColumnSample column : sortedColumns) {
            BlockPos samplePos = new BlockPos(column.x(), minY + 1, column.z());
            if (!this.level.isLoaded(samplePos)) {
                continue;
            }

            int topY = Math.min(this.level.getMaxBuildHeight() - 1, column.surfaceY() + 1);
            int bottomY = minY;
            List<BlockPos> stack = new ArrayList<>();
            for (int y = topY; y >= bottomY; y--) {
                BlockPos pos = new BlockPos(column.x(), y, column.z());
                BlockState state = this.level.getBlockState(pos);
                if (!DestructionTerrainManager.canFaultClear(this.level, pos, state)) {
                    continue;
                }
                stack.add(pos);
            }
            if (!stack.isEmpty()) {
                columnStacks.add(stack);
                maxStackDepth = Math.max(maxStackDepth, stack.size());
            }
        }

        for (int layer = 0; layer < maxStackDepth; layer++) {
            for (List<BlockPos> stack : columnStacks) {
                if (layer >= stack.size()) {
                    continue;
                }
                BlockPos pos = stack.get(layer);
                if (sourceClearKeys.add(pos.asLong())) {
                    sourceClears.add(pos);
                }
            }
        }
    }

    private List<FaultPlateSnapshot> applyDistributedDisplayBudget(List<FaultPlateSnapshot> snapshots) {
        if (snapshots.isEmpty()) {
            return snapshots;
        }

        int visualGroupCount = 0;
        int totalVisualBlocks = 0;
        for (FaultPlateSnapshot snapshot : snapshots) {
            int count = this.countDisplayBlocks(snapshot.visualSnapshot());
            if (count > 0) {
                visualGroupCount++;
                totalVisualBlocks += count;
            }
        }

        if (totalVisualBlocks <= MAX_DISPLAY_BLOCKS) {
            this.capturedDisplayBlocks = totalVisualBlocks;
            return snapshots;
        }
        if (visualGroupCount <= 0) {
            this.capturedDisplayBlocks = 0;
            return snapshots;
        }

        List<FaultPlateSnapshot> limitedSnapshots = new ArrayList<>(snapshots.size());
        int remainingBudget = MAX_DISPLAY_BLOCKS;
        int remainingVisualGroups = visualGroupCount;
        int usedDisplayBlocks = 0;
        for (FaultPlateSnapshot snapshot : snapshots) {
            int visualCount = this.countDisplayBlocks(snapshot.visualSnapshot());
            if (visualCount <= 0) {
                limitedSnapshots.add(snapshot);
                continue;
            }

            int groupBudget = Math.max(1, remainingBudget / Math.max(1, remainingVisualGroups));
            FaultPaletteSnapshot limitedVisual = visualCount <= groupBudget
                    ? snapshot.visualSnapshot()
                    : this.limitVisualSnapshot(snapshot.visualSnapshot(), groupBudget);
            int limitedCount = this.countDisplayBlocks(limitedVisual);
            usedDisplayBlocks += limitedCount;
            remainingBudget = Math.max(0, remainingBudget - limitedCount);
            remainingVisualGroups--;
            limitedSnapshots.add(new FaultPlateSnapshot(snapshot.fullSnapshot(), limitedVisual, snapshot.translation(), snapshot.blockOffset()));
        }
        this.capturedDisplayBlocks = usedDisplayBlocks;
        return limitedSnapshots;
    }

    private FaultPaletteSnapshot limitVisualSnapshot(FaultPaletteSnapshot snapshot, int maxDisplayBlocks) {
        if (maxDisplayBlocks <= 0 || snapshot.entries().isEmpty()) {
            return FaultPaletteSnapshot.EMPTY;
        }
        int visualCount = this.countDisplayBlocks(snapshot);
        if (visualCount <= maxDisplayBlocks) {
            return snapshot;
        }

        List<FaultPaletteEntry> displayEntries = new ArrayList<>(Math.min(maxDisplayBlocks, snapshot.entries().size()));
        double step = visualCount / (double) maxDisplayBlocks;
        double nextPick = 0.0D;
        int visualIndex = 0;
        int picked = 0;
        for (FaultPaletteEntry entry : snapshot.entries()) {
            BlockState state = snapshot.palette().get(entry.paletteIndex());
            if (state.getRenderShape() != RenderShape.MODEL) {
                continue;
            }
            if (picked < maxDisplayBlocks && visualIndex + 1.0E-6D >= nextPick) {
                displayEntries.add(entry);
                picked++;
                nextPick = picked * step;
            }
            visualIndex++;
        }
        if (displayEntries.isEmpty()) {
            return FaultPaletteSnapshot.EMPTY;
        }
        return new FaultPaletteSnapshot(snapshot.palette(), List.copyOf(displayEntries));
    }

    private Set<Long> collectCorridorColumnKeys(Vec3 start, Vec3 end) {
        Set<Long> keys = new HashSet<>();
        int currentX = Mth.floor(start.x);
        int currentZ = Mth.floor(start.z);
        int targetX = Mth.floor(end.x);
        int targetZ = Mth.floor(end.z);
        double deltaX = end.x - start.x;
        double deltaZ = end.z - start.z;
        int stepX = Integer.compare(targetX, currentX);
        int stepZ = Integer.compare(targetZ, currentZ);
        double nextX = intBound(start.x, deltaX);
        double nextZ = intBound(start.z, deltaZ);
        double deltaStepX = stepX == 0 ? Double.POSITIVE_INFINITY : Math.abs(1.0D / deltaX);
        double deltaStepZ = stepZ == 0 ? Double.POSITIVE_INFINITY : Math.abs(1.0D / deltaZ);
        keys.add(columnKey(currentX, currentZ));

        while (currentX != targetX || currentZ != targetZ) {
            if (nextX < nextZ) {
                currentX += stepX;
                nextX += deltaStepX;
            } else if (nextZ < nextX) {
                currentZ += stepZ;
                nextZ += deltaStepZ;
            } else if (Math.abs(this.lateralDistance(currentX + stepX, currentZ)) <= Math.abs(this.lateralDistance(currentX, currentZ + stepZ))) {
                currentX += stepX;
                nextX += deltaStepX;
            } else {
                currentZ += stepZ;
                nextZ += deltaStepZ;
            }
            keys.add(columnKey(currentX, currentZ));
        }
        return keys;
    }

    private double lateralDistance(int blockX, int blockZ) {
        Vec3 center = new Vec3(blockX + 0.5D, this.origin.y, blockZ + 0.5D);
        return center.subtract(this.origin).dot(this.perpendicular);
    }

    private static double intBound(double value, double delta) {
        if (delta > 0.0D) {
            return (Math.floor(value) + 1.0D - value) / delta;
        }
        if (delta < 0.0D) {
            return (value - Math.floor(value)) / -delta;
        }
        return Double.POSITIVE_INFINITY;
    }

    private List<FaultPlateGroup> buildFaultPlateGroups(List<FaultColumnSample> columns, Set<Long> corridorColumnKeys, boolean positiveSide) {
        if (columns.isEmpty()) {
            return List.of();
        }

        Map<Long, FaultColumnSample> columnMap = new HashMap<>();
        for (FaultColumnSample column : columns) {
            columnMap.put(column.key(), column);
        }

        Set<Long> visited = new HashSet<>();
        List<FaultPlateGroup> groups = new ArrayList<>();
        BlockPos blockOffset = BlockPos.ZERO;
        Vec3 translation = this.sideDisplayTranslation(positiveSide);

        for (FaultColumnSample column : columns) {
            if (!visited.add(column.key())) {
                continue;
            }

            List<FaultColumnSample> component = this.collectFaultComponent(column, columnMap, visited);
            if (!this.componentTouchesCorridor(component, corridorColumnKeys)) {
                continue;
            }
            component.sort(Comparator.comparingDouble(FaultColumnSample::along));
            double minAlong = component.get(0).along();
            Map<Integer, List<FaultColumnSample>> segmented = new HashMap<>();
            for (FaultColumnSample sample : component) {
                int bucket = Math.max(0, Mth.floor((sample.along() - minAlong) / PLATE_SEGMENT_LENGTH));
                segmented.computeIfAbsent(bucket, ignored -> new ArrayList<>()).add(sample);
            }

            List<Integer> sortedBuckets = new ArrayList<>(segmented.keySet());
            sortedBuckets.sort(Integer::compareTo);
            List<List<FaultColumnSample>> groupedColumns = new ArrayList<>();
            for (int bucket : sortedBuckets) {
                List<FaultColumnSample> segmentColumns = segmented.get(bucket);
                segmentColumns.sort(Comparator.comparingDouble(FaultColumnSample::along));
                if (!groupedColumns.isEmpty() && segmentColumns.size() < MIN_GROUP_COLUMNS) {
                    groupedColumns.get(groupedColumns.size() - 1).addAll(segmentColumns);
                } else {
                    groupedColumns.add(new ArrayList<>(segmentColumns));
                }
            }

            for (List<FaultColumnSample> groupedColumnSet : groupedColumns) {
                if (groupedColumnSet.isEmpty()) {
                    continue;
                }
                double groupMinAlong = groupedColumnSet.stream().mapToDouble(FaultColumnSample::along).min().orElse(0.0D);
                double groupMaxAlong = groupedColumnSet.stream().mapToDouble(FaultColumnSample::along).max().orElse(groupMinAlong);
                groups.add(new FaultPlateGroup(List.copyOf(groupedColumnSet), translation, blockOffset, groupMinAlong, groupMaxAlong, positiveSide ? 0 : 1));
            }
        }

        groups.sort(Comparator
                .comparingDouble(FaultPlateGroup::minAlong)
                .thenComparingDouble(FaultPlateGroup::maxAlong)
                .thenComparingInt(FaultPlateGroup::sideOrder));
        return groups;
    }

    private Vec3 sideDisplayTranslation(boolean positiveSide) {
        return this.perpendicular.scale(positiveSide ? -this.displayShift : this.displayShift);
    }

    private boolean componentTouchesCorridor(List<FaultColumnSample> component, Set<Long> corridorColumnKeys) {
        for (FaultColumnSample column : component) {
            for (int offsetX = -1; offsetX <= 1; offsetX++) {
                for (int offsetZ = -1; offsetZ <= 1; offsetZ++) {
                    if (offsetX == 0 && offsetZ == 0) {
                        continue;
                    }
                    if (corridorColumnKeys.contains(columnKey(column.x() + offsetX, column.z() + offsetZ))) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private List<FaultColumnSample> collectFaultComponent(FaultColumnSample start, Map<Long, FaultColumnSample> columnMap, Set<Long> visited) {
        ArrayDeque<FaultColumnSample> queue = new ArrayDeque<>();
        List<FaultColumnSample> component = new ArrayList<>();
        queue.add(start);

        while (!queue.isEmpty()) {
            FaultColumnSample current = queue.removeFirst();
            component.add(current);
            this.tryQueueFaultNeighbor(current.x() + 1, current.z(), columnMap, visited, queue);
            this.tryQueueFaultNeighbor(current.x() - 1, current.z(), columnMap, visited, queue);
            this.tryQueueFaultNeighbor(current.x(), current.z() + 1, columnMap, visited, queue);
            this.tryQueueFaultNeighbor(current.x(), current.z() - 1, columnMap, visited, queue);
            this.tryQueueFaultNeighbor(current.x() + 1, current.z() + 1, columnMap, visited, queue);
            this.tryQueueFaultNeighbor(current.x() + 1, current.z() - 1, columnMap, visited, queue);
            this.tryQueueFaultNeighbor(current.x() - 1, current.z() + 1, columnMap, visited, queue);
            this.tryQueueFaultNeighbor(current.x() - 1, current.z() - 1, columnMap, visited, queue);
        }

        return component;
    }

    private void tryQueueFaultNeighbor(int x, int z, Map<Long, FaultColumnSample> columnMap, Set<Long> visited, ArrayDeque<FaultColumnSample> queue) {
        long key = columnKey(x, z);
        FaultColumnSample neighbor = columnMap.get(key);
        if (neighbor != null && visited.add(key)) {
            queue.addLast(neighbor);
        }
    }

    private int countDisplayBlocks(FaultPaletteSnapshot snapshot) {
        int count = 0;
        for (FaultPaletteEntry entry : snapshot.entries()) {
            BlockState state = snapshot.palette().get(entry.paletteIndex());
            if (state.getRenderShape() == RenderShape.MODEL) {
                count++;
            }
        }
        return count;
    }

    private ColumnCaptureResult collectFaultPlateColumn(FaultColumnSample column, FaultPaletteBuilder fullBuilder, FaultPaletteBuilder visualBuilder, int remainingCaptureBudget) {
        if (remainingCaptureBudget <= 0) {
            return new ColumnCaptureResult(0, true);
        }
        BlockPos samplePos = new BlockPos(column.x(), this.level.getMinBuildHeight() + 1, column.z());
        if (!this.level.isLoaded(samplePos)) {
            return new ColumnCaptureResult(0, false);
        }

        int topY = Math.min(this.level.getMaxBuildHeight() - 1, column.surfaceY() + 1);
        boolean nearFaultWall = Math.abs(column.lateral()) <= this.corridorHalfWidth + 2.25D;
        int moveDepth = nearFaultWall ? SIDE_WALL_MOVE_DEPTH : SURFACE_MOVE_DEPTH;
        int visualDepth = nearFaultWall ? SIDE_WALL_VISUAL_DEPTH : SURFACE_VISUAL_DEPTH;
        int bottomY = Math.max(this.level.getMinBuildHeight(), column.surfaceY() - moveDepth - 2);
        int addedBlocks = 0;
        boolean truncated = false;
        int movedLayers = 0;
        int visualLayers = 0;
        for (int y = topY; y >= bottomY && addedBlocks < remainingCaptureBudget; y--) {
            BlockPos pos = new BlockPos(column.x(), y, column.z());
            BlockState state = this.level.getBlockState(pos);
            if (state.isAir()) {
                continue;
            }
            if (!DestructionTerrainManager.canFaultClear(this.level, pos, state)) {
                continue;
            }

            if (movedLayers >= moveDepth) {
                break;
            }

            fullBuilder.add(pos, state);
            if (visualLayers < visualDepth && state.getRenderShape() == RenderShape.MODEL) {
                visualBuilder.add(pos, state);
                visualLayers++;
            }
            addedBlocks++;
            movedLayers++;
            if (addedBlocks >= remainingCaptureBudget && y > bottomY) {
                truncated = true;
            }
        }
        return new ColumnCaptureResult(addedBlocks, truncated);
    }

    private void startDisplayGroupAnimations() {
        for (FaultDisplayGroup group : this.displayGroups) {
            for (FaultDisplayBlock block : group.blocks()) {
                if (block.display().isRemoved()) {
                    continue;
                }
                applyFaultDisplayState(block.display(), block.state(), group.translation(), new Vec3(1.0D, 1.0D, 1.0D), DISPLAY_MOVE_TICKS);
            }
        }
    }

    private void discardDisplays() {
        if (!this.finalSolidified) {
            this.finalSolidified = true;
        }
        for (FaultDisplayGroup group : this.displayGroups) {
            for (FaultDisplayBlock block : group.blocks()) {
                if (!block.display().isRemoved()) {
                    block.display().discard();
                }
            }
        }
        this.displayGroups.clear();
    }

    private boolean restoreSourcePlateGroups(TickBudget budget) {
        int processedThisTick = 0;
        Map<BlockPos, BlockState> batch = new HashMap<>();
        while (this.pendingPlacementIndex < this.pendingPlacements.size() && budget.hasBudget() && processedThisTick < PLACEMENT_BLOCKS_PER_TICK) {
            FaultPlacement placement = this.pendingPlacements.get(this.pendingPlacementIndex++);
            batch.put(placement.pos(), placement.state());
            budget.consume();
            processedThisTick++;
            if (batch.size() >= BLOCK_MUTATION_BATCH_SIZE) {
                DestructionTerrainManager.applyFaultSplitChangesInstant(this.level, List.of(), List.of(), batch, DestructionMode.DIVINE);
                batch.clear();
            }
        }
        if (!batch.isEmpty()) {
            DestructionTerrainManager.applyFaultSplitChangesInstant(this.level, List.of(), List.of(), batch, DestructionMode.DIVINE);
        }
        if (this.pendingPlacementIndex >= this.pendingPlacements.size()) {
            this.pendingPlacements.clear();
            return true;
        }
        return false;
    }

    private void spawnFaultLineBurst() {
        int bursts = Math.max(12, Mth.ceil(this.maxLength / 6.0D));
        for (int i = 0; i <= bursts; i++) {
            double progress = i / (double) bursts;
            Vec3 sample = this.origin.add(this.forward.scale(this.maxLength * progress));
            spawnFaultSplitResidualParticles(this.level, sample);
        }
    }

    private long columnKey(int x, int z) {
        return (((long) x) << 32) ^ (z & 0xFFFFFFFFL);
    }


    private static void spawnFaultSplitResidualParticles(ServerLevel level, Vec3 center) {
        level.sendParticles(ParticleTypes.END_ROD, center.x, center.y + 0.08D, center.z, 2, 0.02D, 0.04D, 0.02D, 0.0D);
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, center.x, center.y + 0.10D, center.z, 2, 0.03D, 0.05D, 0.03D, 0.0D);
        level.sendParticles(ParticleTypes.FLASH, center.x, center.y + 0.12D, center.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
    }

    @Nullable
    private static Method findDeclaredMethod(Class<?> owner, String name, Class<?>... parameterTypes) {
        try {
            Method method = owner.getDeclaredMethod(name, parameterTypes);
            method.setAccessible(true);
            return method;
        } catch (ReflectiveOperationException exception) {
            return null;
        }
    }

    private static boolean invokePrivateMethod(@Nullable Method method, Object target, Object... args) {
        if (method == null) {
            return false;
        }
        try {
            method.invoke(target, args);
            return true;
        } catch (ReflectiveOperationException exception) {
            return false;
        }
    }

    private static Display.BlockDisplay createFaultBlockDisplay(ServerLevel level, BlockPos pos, BlockState state, Vec3 translation, Vec3 scale) {
        Display.BlockDisplay display = EntityType.BLOCK_DISPLAY.create(level);
        if (display == null) {
            return null;
        }
        display.setPos(pos.getX(), pos.getY(), pos.getZ());
        display.setNoGravity(true);
        display.setInvulnerable(true);
        display.addTag(FAULT_DISPLAY_TAG);
        applyFaultDisplayState(display, state, translation, scale, 0);
        level.addFreshEntity(display);
        return display;
    }

    private static void applyFaultDisplayState(Display.BlockDisplay display, BlockState state, Vec3 translation, Vec3 scale, int interpolationDuration) {
        boolean synced = invokePrivateMethod(BLOCK_DISPLAY_SET_BLOCK_STATE, display, state);
        synced |= invokePrivateMethod(DISPLAY_SET_INTERPOLATION_DURATION, display, Math.max(0, interpolationDuration));
        synced |= invokePrivateMethod(DISPLAY_SET_INTERPOLATION_DELAY, display, 0);

        Transformation transformation = new Transformation(
                new Vector3f((float) translation.x, (float) translation.y, (float) translation.z),
                new Quaternionf(),
                new Vector3f((float) scale.x, (float) scale.y, (float) scale.z),
                new Quaternionf()
        );
        synced |= invokePrivateMethod(DISPLAY_SET_TRANSFORMATION, display, transformation);
        synced |= invokePrivateMethod(DISPLAY_SET_VIEW_RANGE, display, 256.0F);
        synced |= invokePrivateMethod(DISPLAY_SET_WIDTH, display, 1.15F);
        synced |= invokePrivateMethod(DISPLAY_SET_HEIGHT, display, 1.15F);
        synced |= invokePrivateMethod(DISPLAY_SET_SHADOW_RADIUS, display, 0.0F);
        synced |= invokePrivateMethod(DISPLAY_SET_SHADOW_STRENGTH, display, 0.0F);

        if (!synced) {
            CompoundTag tag = new CompoundTag();
            tag.put(Display.BlockDisplay.TAG_BLOCK_STATE, NbtUtils.writeBlockState(state));
            tag.put(Display.TAG_TRANSFORMATION, createDisplayTransformationTag(translation, scale));
            tag.putInt(Display.TAG_TRANSFORMATION_INTERPOLATION_DURATION, Math.max(0, interpolationDuration));
            tag.putInt(Display.TAG_TRANSFORMATION_START_INTERPOLATION, 0);
            tag.putFloat("view_range", 256.0F);
            tag.putFloat("width", 1.15F);
            tag.putFloat("height", 1.15F);
            tag.putFloat("shadow_radius", 0.0F);
            tag.putFloat("shadow_strength", 0.0F);
            display.load(tag);
        }
    }

    private static CompoundTag createDisplayTransformationTag(Vec3 translation, Vec3 scale) {
        CompoundTag transformation = new CompoundTag();
        transformation.put("translation", floatList((float) translation.x, (float) translation.y, (float) translation.z));
        transformation.put("scale", floatList((float) scale.x, (float) scale.y, (float) scale.z));
        transformation.put("left_rotation", floatList(0.0F, 0.0F, 0.0F, 1.0F));
        transformation.put("right_rotation", floatList(0.0F, 0.0F, 0.0F, 1.0F));
        return transformation;
    }

    private static ListTag floatList(float... values) {
        ListTag listTag = new ListTag();
        for (float value : values) {
            listTag.add(FloatTag.valueOf(value));
        }
        return listTag;
    }

    private record FaultPaletteEntry(BlockPos pos, int paletteIndex) {}

    private record FaultPaletteSnapshot(List<BlockState> palette, List<FaultPaletteEntry> entries) {
        private static final FaultPaletteSnapshot EMPTY = new FaultPaletteSnapshot(List.of(), List.of());
    }

    private record FaultColumnSample(int x, int z, int surfaceY, double along, double lateral) {
        private long key() {
            return (((long) this.x) << 32) ^ (this.z & 0xFFFFFFFFL);
        }
    }

    private record ColumnCaptureResult(int addedBlocks, boolean truncated) {}

    private record FaultPlateGroup(List<FaultColumnSample> columns, Vec3 translation, BlockPos blockOffset, double minAlong, double maxAlong, int sideOrder) {}

    private record FaultPlateSnapshot(FaultPaletteSnapshot fullSnapshot, FaultPaletteSnapshot visualSnapshot, Vec3 translation, BlockPos blockOffset) {}

    private record FaultPlacement(BlockPos pos, BlockState state) {}

    private record FaultDisplayBlock(Display.BlockDisplay display, BlockState state) {}

    private record FaultDisplayGroup(List<FaultDisplayBlock> blocks, FaultPaletteSnapshot fullSnapshot, Vec3 translation, BlockPos blockOffset) {}

    private static class FaultPaletteBuilder {
        private final Map<BlockState, Integer> paletteLookup = new HashMap<>();
        private final List<BlockState> palette = new ArrayList<>();
        private final List<FaultPaletteEntry> entries = new ArrayList<>();

        private void add(BlockPos pos, BlockState state) {
            Integer index = this.paletteLookup.get(state);
            if (index == null) {
                index = this.palette.size();
                this.paletteLookup.put(state, index);
                this.palette.add(state);
            }
            this.entries.add(new FaultPaletteEntry(pos.immutable(), index));
        }

        private FaultPaletteSnapshot snapshot() {
            if (this.entries.isEmpty()) {
                return FaultPaletteSnapshot.EMPTY;
            }
            return new FaultPaletteSnapshot(List.copyOf(this.palette), List.copyOf(this.entries));
        }
    }
}

