package com.whitecloud233.herobrine_companion.entity.ai.learning.state;

import com.whitecloud233.herobrine_companion.config.Config;
import com.whitecloud233.herobrine_companion.entity.HeroEntity;
import com.whitecloud233.herobrine_companion.entity.ai.learning.HeroBrain;
import com.whitecloud233.herobrine_companion.entity.ai.learning.SimpleNeuralNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class PranksterStateDefinition implements HeroMindStateDefinition {
    private static final float DIRECT_ATTACK_EXIT_TO_JUDGE_MIN = 0.20f;

    private static final String FAKE_CHEST_POS_KEY = "PranksterFakeTreasureChest";
    private static final String FAKE_TORCHES_KEY = "PranksterFakeTreasureTorches";
    private static final String FAKE_DIM_KEY = "PranksterFakeTreasureDim";
    private static final String FAKE_EXPIRE_AT_KEY = "PranksterFakeTreasureExpireAt";

    private static final String COOLDOWN_FAKE_TREASURE = "PranksterCooldownFakeTreasure";
    private static final String COOLDOWN_LEAF_VANISH = "PranksterCooldownLeafVanish";
    private static final String COOLDOWN_EDGE_FLASH = "PranksterCooldownEdgeFlash";

    private static final int FAKE_TREASURE_COOLDOWN = 1200;
    private static final float FAKE_TREASURE_CHANCE = 0.18F;
    private static final double FAKE_TREASURE_MIN_DIST = 4.0D;
    private static final double FAKE_TREASURE_MAX_DIST = 8.0D;
    private static final int FAKE_TREASURE_LIFETIME = 1800;
    private static final int FAKE_TREASURE_SCAN_ATTEMPTS = 16;

    private static final int LEAF_VANISH_COOLDOWN = 900;
    private static final float LEAF_VANISH_CHANCE = 0.16F;
    private static final int LEAF_VANISH_SCAN_RANGE = 8;
    private static final int LEAF_VANISH_Y_RANGE = 6;
    private static final int LEAF_VANISH_SCAN_ATTEMPTS = 20;
    private static final int LEAF_VANISH_MAX_BLOCKS = 192;
    private static final int EDGE_FLASH_COOLDOWN = 800;
    private static final float EDGE_FLASH_CHANCE = 0.14F;

    private static final int TREE_LOG_SEARCH_RADIUS = 3;
    private static final int TREE_LOG_SEARCH_DOWN = 10;
    private static final int TREE_LOG_CLUSTER_HORIZONTAL_RADIUS = 5;
    private static final int TREE_LOG_CLUSTER_DOWN = 3;
    private static final int TREE_LOG_CLUSTER_UP = 18;
    private static final int TREE_LOG_CLUSTER_MAX_BLOCKS = 64;
    private static final int TREE_LEAF_BIND_RANGE = 7;

    private static final int[][] CARDINAL_OFFSETS = {
            {1, 0},
            {-1, 0},
            {0, 1},
            {0, -1}
    };
    private static final int[][] LOG_NEIGHBORS = {
            {1, 0, 0},
            {-1, 0, 0},
            {0, 1, 0},
            {0, -1, 0},
            {0, 0, 1},
            {0, 0, -1}
    };
    private static final int[][] CANOPY_NEIGHBORS = buildCanopyNeighbors();

    @Override
    public SimpleNeuralNetwork.MindState state() {
        return SimpleNeuralNetwork.MindState.PRANKSTER;
    }

    @Override
    public boolean shouldEnter(HeroMindStateSnapshot snapshot) {
        return snapshot.curiosityWeight() >= 0.55f
                && snapshot.annoyanceWeight() < 0.45f
                && snapshot.respectWeight() >= 0.30f
                && snapshot.respectWeight() <= 0.75f;
    }

    @Override
    public SimpleNeuralNetwork.MindState shouldExit(HeroMindStateSnapshot snapshot, HeroEntity hero) {
        if (snapshot.directAttackScore() >= DIRECT_ATTACK_EXIT_TO_JUDGE_MIN
                && snapshot.annoyanceWeight() >= 0.55f) {
            return SimpleNeuralNetwork.MindState.JUDGE;
        }
        if (snapshot.curiosityWeight() < 0.35f) return SimpleNeuralNetwork.MindState.OBSERVER;
        return null;
    }

    @Override
    public int minDwellTicks() {
        return 1200;
    }

    @Override
    public void tickServer(HeroEntity hero) {
        ServerPlayer focus = HeroStateBehaviorSupport.getFocusPlayer(hero, 24.0D);
        if (focus == null) return;

        ServerLevel level = (ServerLevel) hero.level();
        HeroStateBehaviorSupport.ensureFloating(hero);
        HeroStateBehaviorSupport.keepDistance(hero, focus, 6.0D, 14.0D, 0.9D, 0.6D);

        float boost = HeroStateBehaviorSupport.isNight(hero) ? 1.5F : 1.0F;

        if (!hasFakeTreasure(hero)
                && isCooldownReady(hero, COOLDOWN_FAKE_TREASURE)
                && hero.getRandom().nextFloat() < FAKE_TREASURE_CHANCE * boost
                && spawnFakeTreasure(hero, level, focus)) {
            startCooldown(hero, COOLDOWN_FAKE_TREASURE, FAKE_TREASURE_COOLDOWN);
            return;
        }

        if (Config.heroLeafVanishEnabled
                && isCooldownReady(hero, COOLDOWN_LEAF_VANISH)
                && hero.getRandom().nextFloat() < LEAF_VANISH_CHANCE * boost
                && tryVanishNearbyLeaves(hero, level, focus)) {
            startCooldown(hero, COOLDOWN_LEAF_VANISH, LEAF_VANISH_COOLDOWN);
            return;
        }

        if (hero.tickCount % 70 == 0 && hero.getRandom().nextFloat() < 0.22F * boost) {
            BlockPos behind = HeroStateBehaviorSupport.offsetBehind(focus, 3.0D);
            level.playSound(null, behind, SoundEvents.STONE_STEP, SoundSource.HOSTILE, 0.7F, 1.0F);
            HeroStateBehaviorSupport.spawnParticles(level, ParticleTypes.POOF, focus.position().add(0.0D, 1.0D, 0.0D), 6, 0.6D);
            return;
        }

        if (hero.tickCount % 90 == 0 && hero.getRandom().nextFloat() < 0.18F * boost) {
            if (HeroStateBehaviorSupport.toggleNearbyDoor(level, focus.blockPosition(), 6)) {
                return;
            }
        }

        if (hero.tickCount % 120 == 0 && hero.getRandom().nextFloat() < 0.12F * boost) {
            if (HeroStateBehaviorSupport.toggleNearbyLight(level, focus.blockPosition(), 8)) {
                return;
            }
        }

        if (isCooldownReady(hero, COOLDOWN_EDGE_FLASH)
                && hero.getRandom().nextFloat() < EDGE_FLASH_CHANCE * boost
                && tryEdgeFlash(hero, level, focus)) {
            startCooldown(hero, COOLDOWN_EDGE_FLASH, EDGE_FLASH_COOLDOWN);
            return;
        }

        if (hero.tickCount % 80 == 0 && hero.getRandom().nextFloat() < 0.10F * boost) {
            HeroStateBehaviorSupport.shortTeleport(hero, HeroStateBehaviorSupport.findNearbyTeleportPoint(hero, focus, 12.0D, 16.0D));
        }
    }

    @Override
    public void tickClientAmbient(HeroEntity hero) {
        HeroStateBehaviorSupport.spawnClientAmbient(hero, ParticleTypes.WITCH, 4, 1.4D, 0.6D, 1.4D);
        HeroStateBehaviorSupport.spawnClientAmbient(hero, ParticleTypes.PORTAL, 5, 1.6D, 0.5D, 1.6D);
    }

    public static void tickPersistentState(HeroEntity hero) {
        if (hero == null || hero.level().isClientSide) {
            return;
        }

        FakeTreasureState state = getFakeTreasureState(hero);
        if (state == null) {
            return;
        }

        String currentDimension = hero.level().dimension().location().toString();
        if (!state.dimensionKey().equals(currentDimension)) {
            if (hero.level().getGameTime() >= state.expireAt()) {
                clearFakeTreasureState(hero);
            }
            return;
        }

        ServerLevel level = (ServerLevel) hero.level();
        if (!level.getBlockState(state.chestPos()).is(Blocks.CHEST)) {
            clearFakeTreasureState(hero);
            return;
        }

        if (level.getGameTime() >= state.expireAt()) {
            dismissFakeTreasure(hero, level, state, false);
        }
    }

    public static boolean handleFakeTreasureInteract(ServerLevel level, ServerPlayer player, BlockPos pos) {
        for (HeroEntity hero : HeroBrain.ACTIVE_HEROES) {
            if (hero.level() != level || !hero.isAlive()) {
                continue;
            }

            FakeTreasureState state = getFakeTreasureState(hero);
            if (state == null) {
                continue;
            }

            if (!state.dimensionKey().equals(level.dimension().location().toString())) {
                continue;
            }

            if (!state.chestPos().equals(pos)) {
                continue;
            }

            dismissFakeTreasure(hero, level, state, true);
            return true;
        }

        return false;
    }

    private static boolean spawnFakeTreasure(HeroEntity hero, ServerLevel level, ServerPlayer focus) {
        FakeTreasureSite site = findFakeTreasureSite(hero, level, focus);
        if (site == null) {
            return false;
        }

        List<BlockPos> placed = new ArrayList<>(5);
        if (!level.setBlockAndUpdate(site.chestPos(), Blocks.CHEST.defaultBlockState())) {
            return false;
        }
        placed.add(site.chestPos());

        for (BlockPos torchPos : site.torchPositions()) {
            if (!level.setBlockAndUpdate(torchPos, Blocks.TORCH.defaultBlockState())) {
                for (BlockPos rollbackPos : placed) {
                    level.setBlockAndUpdate(rollbackPos, Blocks.AIR.defaultBlockState());
                }
                return false;
            }
            placed.add(torchPos);
        }

        setFakeTreasureState(hero, site.chestPos(), site.torchPositions(), level.dimension().location().toString(), level.getGameTime() + FAKE_TREASURE_LIFETIME);
        HeroStateBehaviorSupport.spawnParticles(level, ParticleTypes.PORTAL, Vec3.atCenterOf(site.chestPos()).add(0.0D, 0.1D, 0.0D), 10, 0.35D);
        level.playSound(null, site.chestPos(), SoundEvents.CHEST_OPEN, SoundSource.HOSTILE, 0.8F, 1.1F);
        return true;
    }

    private static FakeTreasureSite findFakeTreasureSite(HeroEntity hero, ServerLevel level, ServerPlayer focus) {
        Vec3 playerPos = focus.position();
        int baseY = focus.blockPosition().getY();

        for (int i = 0; i < FAKE_TREASURE_SCAN_ATTEMPTS; i++) {
            double angle = hero.getRandom().nextDouble() * Math.PI * 2.0D;
            double distance = Mth.lerp(hero.getRandom().nextDouble(), FAKE_TREASURE_MIN_DIST, FAKE_TREASURE_MAX_DIST);
            int tx = (int) Math.round(playerPos.x + Math.cos(angle) * distance);
            int tz = (int) Math.round(playerPos.z + Math.sin(angle) * distance);

            for (int dy = 3; dy >= -4; dy--) {
                int floorY = baseY + dy;
                BlockPos floor = new BlockPos(tx, floorY, tz);
                BlockPos chestPos = floor.above();
                BlockPos headPos = chestPos.above();
                if (!HeroStateBehaviorSupport.isStandable(level, floor)) {
                    continue;
                }
                if (!level.isEmptyBlock(chestPos) || !level.isEmptyBlock(headPos)) {
                    continue;
                }

                List<BlockPos> torches = new ArrayList<>(4);
                boolean valid = true;
                for (int[] offset : CARDINAL_OFFSETS) {
                    BlockPos torchFloor = floor.offset(offset[0], 0, offset[1]);
                    BlockPos torchPos = chestPos.offset(offset[0], 0, offset[1]);
                    if (!HeroStateBehaviorSupport.isStandable(level, torchFloor) || !level.isEmptyBlock(torchPos)) {
                        valid = false;
                        break;
                    }
                    torches.add(torchPos);
                }

                if (valid) {
                    return new FakeTreasureSite(chestPos, torches);
                }
            }
        }

        return null;
    }

    private static boolean tryVanishNearbyLeaves(HeroEntity hero, ServerLevel level, ServerPlayer focus) {
        List<ScoredBlockPos> candidates = new ArrayList<>();
        BlockPos origin = focus.blockPosition();

        for (int i = 0; i < LEAF_VANISH_SCAN_ATTEMPTS; i++) {
            BlockPos candidate = origin.offset(
                    hero.getRandom().nextInt(LEAF_VANISH_SCAN_RANGE * 2 + 1) - LEAF_VANISH_SCAN_RANGE,
                    hero.getRandom().nextInt(LEAF_VANISH_Y_RANGE + 2) - 1,
                    hero.getRandom().nextInt(LEAF_VANISH_SCAN_RANGE * 2 + 1) - LEAF_VANISH_SCAN_RANGE
            );
            if (!level.getBlockState(candidate).is(BlockTags.LEAVES)) {
                continue;
            }
            candidates.add(new ScoredBlockPos(horizontalDistanceSqr(candidate, focus.position()), candidate));
        }

        if (candidates.isEmpty()) {
            return false;
        }

        candidates.sort(Comparator.comparingDouble(ScoredBlockPos::distanceSqr));
        for (ScoredBlockPos candidate : candidates) {
            int removed = vanishTreeCanopy(level, candidate.pos());
            if (removed <= 0) {
                continue;
            }

            Vec3 fxPos = Vec3.atCenterOf(candidate.pos()).add(0.0D, 0.1D, 0.0D);
            HeroStateBehaviorSupport.spawnParticles(level, ParticleTypes.DRAGON_BREATH, fxPos, 8, 0.35D);
            level.playSound(null, candidate.pos(), SoundEvents.FIRE_EXTINGUISH, SoundSource.HOSTILE, 0.45F, 1.25F);
            return true;
        }

        return false;
    }

    private static boolean tryEdgeFlash(HeroEntity hero, ServerLevel level, ServerPlayer focus) {
        BlockPos flashPos = findEdgeFlashPoint(hero, level, focus);
        if (flashPos == null) {
            return false;
        }

        Vec3 target = new Vec3(flashPos.getX() + 0.5D, flashPos.getY(), flashPos.getZ() + 0.5D);
        HeroStateBehaviorSupport.shortTeleport(hero, target);
        HeroStateBehaviorSupport.lookAtPos(hero, focus.position().add(0.0D, focus.getBbHeight() * 0.7D, 0.0D));
        HeroStateBehaviorSupport.spawnParticles(level, ParticleTypes.PORTAL, hero.position().add(0.0D, 1.0D, 0.0D), 10, 0.25D);
        HeroStateBehaviorSupport.spawnParticles(level, ParticleTypes.SMOKE, hero.position().add(0.0D, 1.0D, 0.0D), 6, 0.2D);
        level.playSound(null, flashPos, SoundEvents.ENDERMAN_TELEPORT, SoundSource.HOSTILE, 0.35F, 1.35F);
        return true;
    }

    private static BlockPos findEdgeFlashPoint(HeroEntity hero, ServerLevel level, ServerPlayer focus) {
        Vec3 forward = Vec3.directionFromRotation(0.0F, focus.getYRot());
        Vec3 right = new Vec3(-forward.z, 0.0D, forward.x);
        int preferredSide = hero.getRandom().nextBoolean() ? 1 : -1;
        int[] sideOrder = new int[]{preferredSide, -preferredSide};
        double[] forwardDistances = new double[]{4.5D, 6.0D};
        double[] sideDistances = new double[]{4.5D, 6.0D};
        int baseY = focus.blockPosition().getY();

        for (int side : sideOrder) {
            for (double forwardDist : forwardDistances) {
                for (double sideDist : sideDistances) {
                    double tx = focus.getX() + forward.x * forwardDist + right.x * side * sideDist;
                    double tz = focus.getZ() + forward.z * forwardDist + right.z * side * sideDist;
                    int blockX = Mth.floor(tx);
                    int blockZ = Mth.floor(tz);
                    for (int dy = 3; dy >= -4; dy--) {
                        BlockPos floor = new BlockPos(blockX, baseY + dy, blockZ);
                        BlockPos foot = floor.above();
                        BlockPos head = foot.above();
                        if (!HeroStateBehaviorSupport.isStandable(level, floor)) {
                            continue;
                        }
                        if (!level.isEmptyBlock(foot) || !level.isEmptyBlock(head)) {
                            continue;
                        }
                        if (Vec3.atCenterOf(foot).distanceToSqr(focus.position()) < 9.0D) {
                            continue;
                        }
                        return foot;
                    }
                }
            }
        }
        return null;
    }

    private static int vanishTreeCanopy(ServerLevel level, BlockPos startPos) {
        BlockPos seedLogPos = findTreeLogSeed(level, startPos);
        if (seedLogPos == null) {
            return 0;
        }

        List<BlockPos> treeLogs = collectTreeLogs(level, seedLogPos);
        if (treeLogs.isEmpty()) {
            return 0;
        }

        List<BlockPos> canopy = collectTreeCanopy(level, startPos, treeLogs);
        int removed = 0;
        for (BlockPos pos : canopy) {
            if (level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState())) {
                removed++;
            }
        }
        return removed;
    }

    private static BlockPos findTreeLogSeed(ServerLevel level, BlockPos startPos) {
        BlockPos bestPos = null;
        int bestScore = Integer.MAX_VALUE;

        for (int offsetY = 0; offsetY <= TREE_LOG_SEARCH_DOWN; offsetY++) {
            int y = startPos.getY() - offsetY;
            for (int offsetX = -TREE_LOG_SEARCH_RADIUS; offsetX <= TREE_LOG_SEARCH_RADIUS; offsetX++) {
                for (int offsetZ = -TREE_LOG_SEARCH_RADIUS; offsetZ <= TREE_LOG_SEARCH_RADIUS; offsetZ++) {
                    BlockPos pos = new BlockPos(startPos.getX() + offsetX, y, startPos.getZ() + offsetZ);
                    if (!level.getBlockState(pos).is(BlockTags.LOGS)) {
                        continue;
                    }

                    int score = (Math.abs(offsetX) + Math.abs(offsetZ)) * 4 + offsetY;
                    if (score < bestScore) {
                        bestScore = score;
                        bestPos = pos;
                    }
                }
            }
        }

        return bestPos;
    }

    private static List<BlockPos> collectTreeLogs(ServerLevel level, BlockPos seedLogPos) {
        ArrayDeque<BlockPos> pending = new ArrayDeque<>();
        List<BlockPos> logs = new ArrayList<>();
        java.util.HashSet<BlockPos> visited = new java.util.HashSet<>();
        pending.add(seedLogPos);

        while (!pending.isEmpty() && logs.size() < TREE_LOG_CLUSTER_MAX_BLOCKS) {
            BlockPos pos = pending.removeFirst();
            if (!visited.add(pos)) {
                continue;
            }

            if (Math.abs(pos.getX() - seedLogPos.getX()) > TREE_LOG_CLUSTER_HORIZONTAL_RADIUS
                    || Math.abs(pos.getZ() - seedLogPos.getZ()) > TREE_LOG_CLUSTER_HORIZONTAL_RADIUS
                    || pos.getY() < seedLogPos.getY() - TREE_LOG_CLUSTER_DOWN
                    || pos.getY() > seedLogPos.getY() + TREE_LOG_CLUSTER_UP) {
                continue;
            }

            if (!level.getBlockState(pos).is(BlockTags.LOGS)) {
                continue;
            }

            logs.add(pos);
            for (int[] offset : LOG_NEIGHBORS) {
                pending.add(pos.offset(offset[0], offset[1], offset[2]));
            }
        }

        return logs;
    }

    private static List<BlockPos> collectTreeCanopy(ServerLevel level, BlockPos startPos, List<BlockPos> treeLogs) {
        int minX = treeLogs.stream().mapToInt(BlockPos::getX).min().orElse(startPos.getX()) - TREE_LEAF_BIND_RANGE;
        int maxX = treeLogs.stream().mapToInt(BlockPos::getX).max().orElse(startPos.getX()) + TREE_LEAF_BIND_RANGE;
        int minY = treeLogs.stream().mapToInt(BlockPos::getY).min().orElse(startPos.getY()) - 2;
        int maxY = treeLogs.stream().mapToInt(BlockPos::getY).max().orElse(startPos.getY()) + TREE_LEAF_BIND_RANGE;
        int minZ = treeLogs.stream().mapToInt(BlockPos::getZ).min().orElse(startPos.getZ()) - TREE_LEAF_BIND_RANGE;
        int maxZ = treeLogs.stream().mapToInt(BlockPos::getZ).max().orElse(startPos.getZ()) + TREE_LEAF_BIND_RANGE;

        ArrayDeque<BlockPos> pending = new ArrayDeque<>();
        List<BlockPos> canopy = new ArrayList<>();
        java.util.HashSet<BlockPos> visited = new java.util.HashSet<>();
        pending.add(startPos);

        while (!pending.isEmpty() && canopy.size() < LEAF_VANISH_MAX_BLOCKS) {
            BlockPos pos = pending.removeFirst();
            if (!visited.add(pos)) {
                continue;
            }

            if (pos.getX() < minX || pos.getX() > maxX
                    || pos.getY() < minY || pos.getY() > maxY
                    || pos.getZ() < minZ || pos.getZ() > maxZ) {
                continue;
            }

            BlockState state = level.getBlockState(pos);
            if (!state.is(BlockTags.LEAVES) || !leafBindsToTree(pos, treeLogs)) {
                continue;
            }

            canopy.add(pos);
            for (int[] offset : CANOPY_NEIGHBORS) {
                pending.add(pos.offset(offset[0], offset[1], offset[2]));
            }
        }

        return canopy;
    }

    private static boolean leafBindsToTree(BlockPos pos, List<BlockPos> treeLogs) {
        for (BlockPos logPos : treeLogs) {
            if (Math.abs(pos.getX() - logPos.getX()) <= TREE_LEAF_BIND_RANGE
                    && Math.abs(pos.getY() - logPos.getY()) <= TREE_LEAF_BIND_RANGE
                    && Math.abs(pos.getZ() - logPos.getZ()) <= TREE_LEAF_BIND_RANGE) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasFakeTreasure(HeroEntity hero) {
        return getFakeTreasureState(hero) != null;
    }

    private static void dismissFakeTreasure(HeroEntity hero, ServerLevel level, FakeTreasureState state, boolean opened) {
        for (BlockPos torchPos : state.torchPositions()) {
            if (level.getBlockState(torchPos).is(Blocks.TORCH)) {
                level.setBlockAndUpdate(torchPos, Blocks.AIR.defaultBlockState());
            }
        }
        if (level.getBlockState(state.chestPos()).is(Blocks.CHEST)) {
            level.setBlockAndUpdate(state.chestPos(), Blocks.AIR.defaultBlockState());
        }

        Vec3 center = Vec3.atCenterOf(state.chestPos()).add(0.0D, 0.1D, 0.0D);
        HeroStateBehaviorSupport.spawnParticles(level, opened ? ParticleTypes.PORTAL : ParticleTypes.SMOKE, center, 8, 0.3D);
        level.playSound(null, state.chestPos(), SoundEvents.CHEST_CLOSE, SoundSource.HOSTILE, 0.8F, opened ? 1.0F : 0.8F);
        clearFakeTreasureState(hero);
    }

    private static FakeTreasureState getFakeTreasureState(HeroEntity hero) {
        if (!hero.getPersistentData().contains(FAKE_CHEST_POS_KEY)) {
            return null;
        }

        BlockPos chestPos = BlockPos.of(hero.getPersistentData().getLong(FAKE_CHEST_POS_KEY));
        String dimensionKey = hero.getPersistentData().getString(FAKE_DIM_KEY);
        long expireAt = hero.getPersistentData().getLong(FAKE_EXPIRE_AT_KEY);
        long[] torchArray = hero.getPersistentData().getLongArray(FAKE_TORCHES_KEY);
        List<BlockPos> torchPositions = new ArrayList<>(torchArray.length);
        for (long rawPos : torchArray) {
            torchPositions.add(BlockPos.of(rawPos));
        }
        return new FakeTreasureState(chestPos, torchPositions, dimensionKey, expireAt);
    }

    private static void setFakeTreasureState(HeroEntity hero, BlockPos chestPos, List<BlockPos> torchPositions, String dimensionKey, long expireAt) {
        hero.getPersistentData().putLong(FAKE_CHEST_POS_KEY, chestPos.asLong());
        hero.getPersistentData().putString(FAKE_DIM_KEY, dimensionKey);
        hero.getPersistentData().putLong(FAKE_EXPIRE_AT_KEY, expireAt);
        long[] torches = new long[torchPositions.size()];
        for (int i = 0; i < torchPositions.size(); i++) {
            torches[i] = torchPositions.get(i).asLong();
        }
        hero.getPersistentData().putLongArray(FAKE_TORCHES_KEY, torches);
    }

    private static void clearFakeTreasureState(HeroEntity hero) {
        hero.getPersistentData().remove(FAKE_CHEST_POS_KEY);
        hero.getPersistentData().remove(FAKE_TORCHES_KEY);
        hero.getPersistentData().remove(FAKE_DIM_KEY);
        hero.getPersistentData().remove(FAKE_EXPIRE_AT_KEY);
    }

    private static boolean isCooldownReady(HeroEntity hero, String key) {
        return hero.level().getGameTime() >= hero.getPersistentData().getLong(key);
    }

    private static void startCooldown(HeroEntity hero, String key, int duration) {
        hero.getPersistentData().putLong(key, hero.level().getGameTime() + duration);
    }

    private static double horizontalDistanceSqr(BlockPos pos, Vec3 target) {
        double dx = pos.getX() + 0.5D - target.x;
        double dz = pos.getZ() + 0.5D - target.z;
        return dx * dx + dz * dz;
    }

    private static int[][] buildCanopyNeighbors() {
        List<int[]> neighbors = new ArrayList<>();
        for (int x = -1; x <= 1; x++) {
            for (int y = -1; y <= 1; y++) {
                for (int z = -1; z <= 1; z++) {
                    if (x == 0 && y == 0 && z == 0) {
                        continue;
                    }
                    neighbors.add(new int[]{x, y, z});
                }
            }
        }
        return neighbors.toArray(new int[0][]);
    }

    private record FakeTreasureSite(BlockPos chestPos, List<BlockPos> torchPositions) {
    }

    private record FakeTreasureState(BlockPos chestPos, List<BlockPos> torchPositions, String dimensionKey, long expireAt) {
    }

    private record ScoredBlockPos(double distanceSqr, BlockPos pos) {
    }
}
