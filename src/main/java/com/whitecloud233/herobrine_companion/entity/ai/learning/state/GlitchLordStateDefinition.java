package com.whitecloud233.herobrine_companion.entity.ai.learning.state;

import com.whitecloud233.herobrine_companion.entity.HeroEntity;
import com.whitecloud233.herobrine_companion.entity.ai.learning.SimpleNeuralNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class GlitchLordStateDefinition implements HeroMindStateDefinition {

    private static final String NO_CONTACT_KEY = "MindGlitchNoContactTicks";
    private static final String ACTIVE_BLOCK_GLITCHES_KEY = "MindGlitchBlockGlitches";

    private static final int BLOCK_GLITCH_SCAN_RANGE = 6;
    private static final int BLOCK_GLITCH_SCAN_ATTEMPTS = 48;
    private static final int BLOCK_GLITCH_MAX_BLOCKS = 8;
    private static final int BLOCK_GLITCH_DURATION_TICKS = 80;
    private static final int BLOCK_GLITCH_RESTORES_PER_TICK = 3;

    private static final Set<Block> GLITCH_SOURCE_BLOCKS = Set.of(
            Blocks.STONE,
            Blocks.COBBLESTONE,
            Blocks.DIRT,
            Blocks.GRASS_BLOCK,
            Blocks.COARSE_DIRT,
            Blocks.SAND,
            Blocks.RED_SAND,
            Blocks.GRAVEL,
            Blocks.CLAY,
            Blocks.MUD,
            Blocks.GRANITE,
            Blocks.DIORITE,
            Blocks.ANDESITE,
            Blocks.DEEPSLATE,
            Blocks.COBBLED_DEEPSLATE,
            Blocks.TUFF,
            Blocks.CALCITE
    );

    private static final List<Block> GLITCH_REPLACEMENTS = List.of(
            Blocks.GLOWSTONE,
            Blocks.END_STONE,
            Blocks.OBSIDIAN,
            Blocks.CRYING_OBSIDIAN,
            Blocks.MAGMA_BLOCK,
            Blocks.PRISMARINE,
            Blocks.PRISMARINE_BRICKS,
            Blocks.DARK_PRISMARINE,
            Blocks.PURPUR_BLOCK,
            Blocks.QUARTZ_BRICKS,
            Blocks.NETHER_BRICKS,
            Blocks.BLACKSTONE,
            Blocks.POLISHED_BLACKSTONE_BRICKS,
            Blocks.AMETHYST_BLOCK,
            Blocks.REDSTONE_BLOCK,
            Blocks.GOLD_BLOCK,
            Blocks.EMERALD_BLOCK,
            Blocks.DIAMOND_BLOCK
    );

    @Override
    public SimpleNeuralNetwork.MindState state() {
        return SimpleNeuralNetwork.MindState.GLITCH_LORD;
    }

    @Override
    public boolean shouldEnter(HeroMindStateSnapshot snapshot) {
        return snapshot.metaScore() >= 0.30f;
    }

    @Override
    public SimpleNeuralNetwork.MindState shouldExit(HeroMindStateSnapshot snapshot, HeroEntity hero) {
        if (snapshot.metaScore() < 0.20f) return SimpleNeuralNetwork.MindState.OBSERVER;
        if (hero != null && hero.getPersistentData().getInt(NO_CONTACT_KEY) >= 4800) return SimpleNeuralNetwork.MindState.OBSERVER;
        return null;
    }

    @Override
    public int minDwellTicks() {
        return 1600;
    }

    @Override
    public void tickServer(HeroEntity hero) {
        ServerPlayer focus = HeroStateBehaviorSupport.getFocusPlayer(hero, 24.0D);
        if (focus == null) return;

        ServerLevel level = (ServerLevel) hero.level();
        HeroStateBehaviorSupport.ensureFloating(hero);
        HeroStateBehaviorSupport.keepDistance(hero, focus, 4.0D, 10.0D, 0.8D, 0.6D);
        HeroStateBehaviorSupport.stopAndLookAt(hero, focus);

        int noContact = hero.getPersistentData().getInt(NO_CONTACT_KEY) + 10;
        hero.getPersistentData().putInt(NO_CONTACT_KEY, noContact);

        if (hero.tickCount % 80 == 0) {
            HeroStateBehaviorSupport.spawnParticles(level, ParticleTypes.DRAGON_BREATH, hero.position().add(0.0D, 1.0D, 0.0D), 6, 0.4D, 0.02D);
            level.playSound(null, hero.blockPosition(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.HOSTILE, 0.35F, 1.4F);
        }

        if (hero.tickCount % 120 == 0 && hero.getRandom().nextFloat() < 0.12F) {
            tryStartBlockGlitch(hero, level, focus);
        }

        if (hero.tickCount % 140 == 0 && hero.getRandom().nextFloat() < 0.20F) {
            hero.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 8, 0, false, false));
            HeroStateBehaviorSupport.shortTeleport(hero, HeroStateBehaviorSupport.findNearbyTeleportPoint(hero, focus, 4.0D, 8.0D));
            hero.getPersistentData().putInt(NO_CONTACT_KEY, 0);
        }
    }

    @Override
    public void tickClientAmbient(HeroEntity hero) {
        HeroStateBehaviorSupport.spawnClientAmbient(hero, ParticleTypes.ENCHANT, 4, 1.4D, 0.5D, 1.8D);
        HeroStateBehaviorSupport.spawnClientAmbient(hero, ParticleTypes.DRAGON_BREATH, 5, 1.2D, 0.8D, 1.2D);
    }

    public static void tickPersistentState(HeroEntity hero) {
        if (hero == null || hero.level().isClientSide) {
            return;
        }

        List<GlitchBlockEntry> activeEntries = getActiveGlitchBlocks(hero);
        if (activeEntries.isEmpty()) {
            return;
        }

        long now = hero.level().getGameTime();
        int restoreBudget = BLOCK_GLITCH_RESTORES_PER_TICK;
        List<GlitchBlockEntry> remainingEntries = new ArrayList<>(activeEntries.size());

        for (GlitchBlockEntry entry : activeEntries) {
            if (entry.restoreAt() > now) {
                remainingEntries.add(entry);
                continue;
            }

            if (restoreBudget <= 0) {
                remainingEntries.add(entry);
                continue;
            }

            ServerLevel targetLevel = resolveLevel(hero, entry.dimensionKey());
            if (targetLevel == null) {
                remainingEntries.add(entry);
                continue;
            }

            Block originalBlock = getBlockByKey(entry.originalBlockKey());
            Block replacementBlock = getBlockByKey(entry.replacementBlockKey());
            if (originalBlock == null || replacementBlock == null) {
                continue;
            }

            BlockState currentState = targetLevel.getBlockState(entry.pos());
            if (currentState.is(replacementBlock)) {
                targetLevel.setBlockAndUpdate(entry.pos(), originalBlock.defaultBlockState());
                HeroStateBehaviorSupport.spawnParticles(targetLevel, ParticleTypes.REVERSE_PORTAL, entry.center(), 8, 0.25D, 0.02D);
                HeroStateBehaviorSupport.spawnParticles(targetLevel, ParticleTypes.DRAGON_BREATH, entry.center().add(0.0D, 0.3D, 0.0D), 4, 0.15D, 0.01D);
                restoreBudget--;
            }
        }

        setActiveGlitchBlocks(hero, remainingEntries);
    }

    private static boolean tryStartBlockGlitch(HeroEntity hero, ServerLevel level, ServerPlayer focus) {
        List<GlitchBlockEntry> activeEntries = getActiveGlitchBlocks(hero);
        if (!activeEntries.isEmpty()) {
            return false;
        }

        RandomSource random = hero.getRandom();
        BlockPos[] centers = {
                focus.blockPosition().below(),
                hero.blockPosition().below()
        };

        List<BlockPos> candidates = new ArrayList<>(BLOCK_GLITCH_MAX_BLOCKS);
        Set<Long> seenPositions = new HashSet<>();

        for (int attempt = 0; attempt < BLOCK_GLITCH_SCAN_ATTEMPTS && candidates.size() < BLOCK_GLITCH_MAX_BLOCKS; attempt++) {
            BlockPos center = centers[random.nextInt(centers.length)];
            int dx = random.nextInt(BLOCK_GLITCH_SCAN_RANGE * 2 + 1) - BLOCK_GLITCH_SCAN_RANGE;
            int dz = random.nextInt(BLOCK_GLITCH_SCAN_RANGE * 2 + 1) - BLOCK_GLITCH_SCAN_RANGE;
            int distanceSqr = dx * dx + dz * dz;
            if (distanceSqr < 4 || distanceSqr > BLOCK_GLITCH_SCAN_RANGE * BLOCK_GLITCH_SCAN_RANGE) {
                continue;
            }

            BlockPos candidate = findGlitchFloor(level, center.offset(dx, 0, dz));
            if (candidate == null || !seenPositions.add(candidate.asLong())) {
                continue;
            }

            candidates.add(candidate);
        }

        if (candidates.isEmpty()) {
            return false;
        }

        long restoreAt = level.getGameTime() + BLOCK_GLITCH_DURATION_TICKS;
        List<GlitchBlockEntry> newEntries = new ArrayList<>(candidates.size());

        for (BlockPos candidate : candidates) {
            BlockState originalState = level.getBlockState(candidate);
            Block replacementBlock = pickReplacement(random, originalState.getBlock());
            if (replacementBlock == null) {
                continue;
            }

            if (!level.setBlockAndUpdate(candidate, replacementBlock.defaultBlockState())) {
                continue;
            }

            newEntries.add(new GlitchBlockEntry(
                    candidate.immutable(),
                    getBlockKey(originalState.getBlock()),
                    getBlockKey(replacementBlock),
                    level.dimension().location().toString(),
                    restoreAt
            ));

            HeroStateBehaviorSupport.spawnParticles(level, ParticleTypes.END_ROD, Vec3.atCenterOf(candidate).add(0.0D, 0.3D, 0.0D), 4, 0.20D, 0.01D);
            HeroStateBehaviorSupport.spawnParticles(level, ParticleTypes.DRAGON_BREATH, Vec3.atCenterOf(candidate).add(0.0D, 0.6D, 0.0D), 3, 0.18D, 0.01D);
        }

        if (newEntries.isEmpty()) {
            return false;
        }

        setActiveGlitchBlocks(hero, newEntries);
        level.playSound(null, focus.blockPosition(), SoundEvents.END_PORTAL_SPAWN, SoundSource.HOSTILE, 0.55F, 0.85F);
        return true;
    }

    @Nullable
    private static BlockPos findGlitchFloor(ServerLevel level, BlockPos origin) {
        for (int dy = 3; dy >= -4; dy--) {
            BlockPos floor = origin.offset(0, dy, 0);
            BlockState state = level.getBlockState(floor);
            if (!isValidGlitchSource(level, floor, state)) {
                continue;
            }
            return floor.immutable();
        }
        return null;
    }

    private static boolean isValidGlitchSource(ServerLevel level, BlockPos pos, BlockState state) {
        if (!GLITCH_SOURCE_BLOCKS.contains(state.getBlock())) {
            return false;
        }
        if (!state.isCollisionShapeFullBlock(level, pos)) {
            return false;
        }
        return level.isEmptyBlock(pos.above());
    }

    @Nullable
    private static Block pickReplacement(RandomSource random, Block originalBlock) {
        List<Block> candidates = new ArrayList<>(GLITCH_REPLACEMENTS.size());
        for (Block block : GLITCH_REPLACEMENTS) {
            if (block != originalBlock) {
                candidates.add(block);
            }
        }
        if (candidates.isEmpty()) {
            return null;
        }
        return candidates.get(random.nextInt(candidates.size()));
    }

    private static List<GlitchBlockEntry> getActiveGlitchBlocks(HeroEntity hero) {
        ListTag listTag = hero.getPersistentData().getList(ACTIVE_BLOCK_GLITCHES_KEY, Tag.TAG_COMPOUND);
        if (listTag.isEmpty()) {
            return List.of();
        }

        List<GlitchBlockEntry> entries = new ArrayList<>(listTag.size());
        for (int i = 0; i < listTag.size(); i++) {
            CompoundTag entryTag = listTag.getCompound(i);
            if (!entryTag.contains("Pos") || !entryTag.contains("OriginalBlock") || !entryTag.contains("ReplacementBlock") || !entryTag.contains("Dimension")) {
                continue;
            }

            entries.add(new GlitchBlockEntry(
                    BlockPos.of(entryTag.getLong("Pos")),
                    entryTag.getString("OriginalBlock"),
                    entryTag.getString("ReplacementBlock"),
                    entryTag.getString("Dimension"),
                    entryTag.getLong("RestoreAt")
            ));
        }
        return entries;
    }

    private static void setActiveGlitchBlocks(HeroEntity hero, List<GlitchBlockEntry> entries) {
        if (entries.isEmpty()) {
            hero.getPersistentData().remove(ACTIVE_BLOCK_GLITCHES_KEY);
            return;
        }

        ListTag listTag = new ListTag();
        for (GlitchBlockEntry entry : entries) {
            CompoundTag entryTag = new CompoundTag();
            entryTag.putLong("Pos", entry.pos().asLong());
            entryTag.putString("OriginalBlock", entry.originalBlockKey());
            entryTag.putString("ReplacementBlock", entry.replacementBlockKey());
            entryTag.putString("Dimension", entry.dimensionKey());
            entryTag.putLong("RestoreAt", entry.restoreAt());
            listTag.add(entryTag);
        }
        hero.getPersistentData().put(ACTIVE_BLOCK_GLITCHES_KEY, listTag);
    }

    @Nullable
    private static ServerLevel resolveLevel(HeroEntity hero, String dimensionKey) {
        if (hero.level() instanceof ServerLevel currentLevel
                && currentLevel.dimension().location().toString().equals(dimensionKey)) {
            return currentLevel;
        }

        if (hero.level().getServer() == null) {
            return null;
        }

        ResourceLocation dimensionLocation = ResourceLocation.tryParse(dimensionKey);
        if (dimensionLocation == null) {
            return null;
        }

        ResourceKey<Level> targetKey = ResourceKey.create(Registries.DIMENSION, dimensionLocation);
        return hero.level().getServer().getLevel(targetKey);
    }

    @Nullable
    private static Block getBlockByKey(String blockKey) {
        ResourceLocation location = ResourceLocation.tryParse(blockKey);
        if (location == null) {
            return null;
        }

        Block block = BuiltInRegistries.BLOCK.get(location);
        return block == Blocks.AIR && !location.equals(BuiltInRegistries.BLOCK.getDefaultKey()) ? null : block;
    }

    private static String getBlockKey(Block block) {
        ResourceLocation key = BuiltInRegistries.BLOCK.getKey(block);
        return key == null ? "minecraft:air" : key.toString();
    }

    private record GlitchBlockEntry(
            BlockPos pos,
            String originalBlockKey,
            String replacementBlockKey,
            String dimensionKey,
            long restoreAt
    ) {
        private net.minecraft.world.phys.Vec3 center() {
            return net.minecraft.world.phys.Vec3.atCenterOf(this.pos);
        }
    }
}
