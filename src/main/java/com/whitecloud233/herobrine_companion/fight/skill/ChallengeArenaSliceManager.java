package com.whitecloud233.herobrine_companion.fight.skill;

import com.whitecloud233.herobrine_companion.entity.HeroEntity;

import com.whitecloud233.herobrine_companion.fight.ChallengeSession;

import com.whitecloud233.herobrine_companion.util.BlockDisplayEffect;
import com.whitecloud233.herobrine_companion.HerobrineCompanion;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Display;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

@EventBusSubscriber(modid = HerobrineCompanion.MODID)
public final class ChallengeArenaSliceManager {
    private static final int DISPLAYS_PER_TICK = 180;
    private static final int MAX_DISPLAY_BLOCKS = 2200;
    private static final int FALL_DISTANCE = 132;
    private static final int FALL_TICKS = 96;
    private static final int HOLD_TICKS = 20;
    private static final int SOURCE_TOP_Y = 102;
    private static final int SOURCE_BOTTOM_Y = 100;
    private static final String DISPLAY_TAG = "herobrine_challenge_cake_slice";

    private static final List<CakeSliceTask> ACTIVE_TASKS = new ArrayList<>();

    private ChallengeArenaSliceManager() {
    }

    public static void start(ServerLevel level, UUID casterUuid, Vec3 center, Vec3 capNormal, Vec3 cutDirection, double cutOffset, double arenaRadius, int delayTicks) {
        ACTIVE_TASKS.add(new CakeSliceTask(level, casterUuid, center, capNormal, cutDirection, cutOffset, arenaRadius, delayTicks));
    }

    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }

        Iterator<CakeSliceTask> iterator = ACTIVE_TASKS.iterator();
        while (iterator.hasNext()) {
            CakeSliceTask task = iterator.next();
            if (task.level != level) {
                continue;
            }
            tickTask(task);
            if (task.isDone()) {
                iterator.remove();
            }
        }
    }

    private static void tickTask(CakeSliceTask task) {
        if (!isCasterActive(task)) {
            task.cancel();
            return;
        }

        if (!task.captured) {
            task.capture();
            task.captured = true;
        }

        if (task.delayTicks > 0) {
            task.delayTicks--;
            return;
        }

        if (!task.sourcesCleared) {
            task.clearSources();
            task.sourcesCleared = true;
            return;
        }

        if (task.displaySpawnIndex < task.displayEntries.size()) {
            task.spawnDisplayBatch();
            return;
        }

        if (!task.animationStarted) {
            task.startFallAnimation();
            task.animationStarted = true;
            return;
        }

        task.animationAge++;
        if (task.animationAge >= FALL_TICKS + HOLD_TICKS) {
            task.discardDisplays();
            task.done = true;
        }
    }

    private static final class CakeSliceTask {
        private final ServerLevel level;
        private final UUID casterUuid;
        private final Vec3 center;
        private final Vec3 capNormal;
        private final Vec3 cutDirection;
        private final double cutOffset;
        private final double arenaRadius;
        private final List<BlockPos> sourcePositions = new ArrayList<>();
        private final List<SliceDisplayEntry> displayEntries = new ArrayList<>();
        private final List<SliceDisplay> displays = new ArrayList<>();

        private int delayTicks;
        private int displaySpawnIndex;
        private int animationAge;
        private boolean captured;
        private boolean sourcesCleared;
        private boolean animationStarted;
        private boolean done;

        private CakeSliceTask(ServerLevel level, UUID casterUuid, Vec3 center, Vec3 capNormal, Vec3 cutDirection, double cutOffset, double arenaRadius, int delayTicks) {
            this.level = level;
            this.casterUuid = casterUuid;
            this.center = center;
            this.capNormal = capNormal;
            this.cutDirection = cutDirection;
            this.cutOffset = cutOffset;
            this.arenaRadius = arenaRadius;
            this.delayTicks = Math.max(0, delayTicks);
        }

        private boolean isDone() {
            return this.done;
        }

        private void capture() {
            int radius = Mth.ceil(this.arenaRadius);
            double radiusSq = this.arenaRadius * this.arenaRadius;

            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    int blockX = Mth.floor(this.center.x) + dx;
                    int blockZ = Mth.floor(this.center.z) + dz;
                    double px = blockX + 0.5D - this.center.x;
                    double pz = blockZ + 0.5D - this.center.z;
                    double distSq = px * px + pz * pz;
                    if (distSq > radiusSq) {
                        continue;
                    }

                    double capDepth = px * this.capNormal.x + pz * this.capNormal.z;
                    if (capDepth < this.cutOffset - 0.75D) {
                        continue;
                    }

                    boolean sampled = (((blockX * 31) ^ (blockZ * 17)) & 7) < 6;
                    for (int y = SOURCE_BOTTOM_Y; y <= SOURCE_TOP_Y; y++) {
                        BlockPos pos = new BlockPos(blockX, y, blockZ);
                        BlockState state = this.level.getBlockState(pos);
                        if (!state.isAir()) {
                            this.sourcePositions.add(pos);
                            if (sampled && state.getRenderShape() == RenderShape.MODEL && this.displayEntries.size() < MAX_DISPLAY_BLOCKS) {
                                this.displayEntries.add(new SliceDisplayEntry(pos, state));
                            }
                        }
                    }
                }
            }
        }

        private void spawnDisplayBatch() {
            int spawned = 0;
            while (spawned < DISPLAYS_PER_TICK && this.displaySpawnIndex < this.displayEntries.size()) {
                SliceDisplayEntry entry = this.displayEntries.get(this.displaySpawnIndex++);
                Display.BlockDisplay display = BlockDisplayEffect.create(this.level, entry.pos(), entry.state(), DISPLAY_TAG);
                if (display != null) {
                    this.displays.add(new SliceDisplay(display, entry.state()));
                }
                spawned++;
            }
        }

        private void clearSources() {
            for (BlockPos pos : this.sourcePositions) {
                if (this.level.isLoaded(pos) && !this.level.getBlockState(pos).isAir()) {
                    this.level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
                }
            }
            this.sourcePositions.clear();
        }

        private void startFallAnimation() {
            Vec3 fallTranslation = new Vec3(0.0D, -FALL_DISTANCE, 0.0D);
            for (SliceDisplay slice : this.displays) {
                if (!slice.display().isRemoved()) {
                    BlockDisplayEffect.apply(slice.display(), slice.state(), fallTranslation, FALL_TICKS);
                }
            }

            this.level.playSound(null, this.center.x, this.center.y, this.center.z, SoundEvents.WARDEN_SONIC_BOOM, SoundSource.WEATHER, 6.0F, 0.42F);
            this.level.playSound(null, this.center.x, this.center.y, this.center.z, SoundEvents.TRIDENT_THUNDER, SoundSource.WEATHER, 6.2F, 0.62F);
            this.level.playSound(null, this.center.x, this.center.y, this.center.z, SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 4.4F, 0.5F);
        }

        private void discardDisplays() {
            for (SliceDisplay slice : this.displays) {
                if (!slice.display().isRemoved()) {
                    slice.display().discard();
                }
            }
            this.displays.clear();
            this.displayEntries.clear();
        }

        private void cancel() {
            this.discardDisplays();
            this.sourcePositions.clear();
            this.done = true;
        }
    }

    private static boolean isCasterActive(CakeSliceTask task) {
        if (task.casterUuid == null) {
            return true;
        }
        Entity caster = task.level.getEntity(task.casterUuid);
        return caster instanceof HeroEntity hero && !caster.isRemoved() && ChallengeSession.isHeroInChallenge(hero);
    }

    private record SliceDisplayEntry(BlockPos pos, BlockState state) {
    }

    private record SliceDisplay(Display.BlockDisplay display, BlockState state) {
    }

}
