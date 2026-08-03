package com.whitecloud233.modid.herobrine_companion.fight.skill;

import com.mojang.math.Transformation;
import com.whitecloud233.modid.herobrine_companion.HerobrineCompanion;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import javax.annotation.Nullable;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

@Mod.EventBusSubscriber(modid = HerobrineCompanion.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
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

    private static final Method DISPLAY_SET_TRANSFORMATION = findDeclaredMethod(Display.class, "setTransformation", Transformation.class);
    private static final Method DISPLAY_SET_INTERPOLATION_DURATION = findDeclaredMethod(Display.class, "setInterpolationDuration", int.class);
    private static final Method DISPLAY_SET_INTERPOLATION_DELAY = findDeclaredMethod(Display.class, "setInterpolationDelay", int.class);
    private static final Method DISPLAY_SET_VIEW_RANGE = findDeclaredMethod(Display.class, "setViewRange", float.class);
    private static final Method DISPLAY_SET_WIDTH = findDeclaredMethod(Display.class, "setWidth", float.class);
    private static final Method DISPLAY_SET_HEIGHT = findDeclaredMethod(Display.class, "setHeight", float.class);
    private static final Method DISPLAY_SET_SHADOW_RADIUS = findDeclaredMethod(Display.class, "setShadowRadius", float.class);
    private static final Method DISPLAY_SET_SHADOW_STRENGTH = findDeclaredMethod(Display.class, "setShadowStrength", float.class);
    private static final Method BLOCK_DISPLAY_SET_BLOCK_STATE = findDeclaredMethod(Display.BlockDisplay.class, "setBlockState", BlockState.class);

    private ChallengeArenaSliceManager() {
    }

    public static void start(ServerLevel level, java.util.UUID casterUuid, Vec3 center, Vec3 capNormal, Vec3 cutDirection, double cutOffset, double arenaRadius, int delayTicks) {
        ACTIVE_TASKS.add(new CakeSliceTask(level, casterUuid, center, capNormal, cutDirection, cutOffset, arenaRadius, delayTicks));
    }

    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.level instanceof ServerLevel level)) {
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
        private final java.util.UUID casterUuid;
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

        private CakeSliceTask(ServerLevel level, java.util.UUID casterUuid, Vec3 center, Vec3 capNormal, Vec3 cutDirection, double cutOffset, double arenaRadius, int delayTicks) {
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
                Display.BlockDisplay display = createDisplay(this.level, entry.pos(), entry.state());
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
                    applyDisplayState(slice.display(), slice.state(), fallTranslation, FALL_TICKS);
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
        return caster != null && !caster.isRemoved() && caster.getPersistentData().getBoolean("IsChallengeActive");
    }

    private record SliceDisplayEntry(BlockPos pos, BlockState state) {
    }

    private record SliceDisplay(Display.BlockDisplay display, BlockState state) {
    }

    @Nullable
    private static Display.BlockDisplay createDisplay(ServerLevel level, BlockPos pos, BlockState state) {
        Display.BlockDisplay display = EntityType.BLOCK_DISPLAY.create(level);
        if (display == null) {
            return null;
        }
        display.setPos(pos.getX(), pos.getY(), pos.getZ());
        display.setNoGravity(true);
        display.setInvulnerable(true);
        display.addTag(DISPLAY_TAG);
        applyDisplayState(display, state, Vec3.ZERO, 0);
        level.addFreshEntity(display);
        return display;
    }

    private static void applyDisplayState(Display.BlockDisplay display, BlockState state, Vec3 translation, int interpolationDuration) {
        boolean synced = invokePrivateMethod(BLOCK_DISPLAY_SET_BLOCK_STATE, display, state);

        Transformation transformation = new Transformation(
                new Vector3f((float) translation.x, (float) translation.y, (float) translation.z),
                new Quaternionf(),
                new Vector3f(1.0F, 1.0F, 1.0F),
                new Quaternionf()
        );
        synced |= invokePrivateMethod(DISPLAY_SET_TRANSFORMATION, display, transformation);
        synced |= invokePrivateMethod(DISPLAY_SET_INTERPOLATION_DURATION, display, Math.max(0, interpolationDuration));
        synced |= invokePrivateMethod(DISPLAY_SET_INTERPOLATION_DELAY, display, 0);
        synced |= invokePrivateMethod(DISPLAY_SET_VIEW_RANGE, display, 256.0F);
        synced |= invokePrivateMethod(DISPLAY_SET_WIDTH, display, 1.15F);
        synced |= invokePrivateMethod(DISPLAY_SET_HEIGHT, display, 1.15F);
        synced |= invokePrivateMethod(DISPLAY_SET_SHADOW_RADIUS, display, 0.0F);
        synced |= invokePrivateMethod(DISPLAY_SET_SHADOW_STRENGTH, display, 0.0F);

        if (!synced) {
            CompoundTag tag = new CompoundTag();
            tag.put(Display.BlockDisplay.TAG_BLOCK_STATE, NbtUtils.writeBlockState(state));
            tag.put(Display.TAG_TRANSFORMATION, createDisplayTransformationTag(translation));
            tag.putInt(Display.TAG_INTERPOLATION_DURATION, Math.max(0, interpolationDuration));
            tag.putInt(Display.TAG_START_INTERPOLATION, 0);
            tag.putFloat(Display.TAG_VIEW_RANGE, 256.0F);
            tag.putFloat(Display.TAG_WIDTH, 1.15F);
            tag.putFloat(Display.TAG_HEIGHT, 1.15F);
            tag.putFloat(Display.TAG_SHADOW_RADIUS, 0.0F);
            tag.putFloat(Display.TAG_SHADOW_STRENGTH, 0.0F);
            display.load(tag);
        }
    }

    private static CompoundTag createDisplayTransformationTag(Vec3 translation) {
        CompoundTag transformation = new CompoundTag();
        transformation.put("translation", floatList((float) translation.x, (float) translation.y, (float) translation.z));
        transformation.put("scale", floatList(1.0F, 1.0F, 1.0F));
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
}
