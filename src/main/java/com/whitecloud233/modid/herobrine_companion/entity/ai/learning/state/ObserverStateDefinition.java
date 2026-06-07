package com.whitecloud233.modid.herobrine_companion.entity.ai.learning.state;

import com.whitecloud233.modid.herobrine_companion.entity.HeroEntity;
import com.whitecloud233.modid.herobrine_companion.entity.ai.learning.SimpleNeuralNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

public final class ObserverStateDefinition implements HeroMindStateDefinition {
    private static final float DIRECT_ATTACK_EXIT_TO_JUDGE_MIN = 0.20f;

    private static final String OBSERVED_LOCATIONS_KEY = "ObserverObservedLocations";
    private static final String TRACE_COOLDOWN_KEY = "ObserverTraceCooldown";
    private static final String INVISIBLE_UNTIL_KEY = "ObserverInvisibleUntil";
    private static final int RECORD_LOCATION_INTERVAL = 100;
    private static final int MAX_RECORDED_LOCATIONS = 6;
    private static final double LOCATION_MERGE_DIST_SQR = 36.0D;
    private static final int TRACE_COOLDOWN = 1200;
    private static final int TRACE_MIN_COUNT = 2;
    private static final int TRACE_MIN_OFFSET = 3;
    private static final int TRACE_MAX_OFFSET = 6;
    private static final float TRACE_CHANCE = 0.18F;
    private static final int INVISIBILITY_CHECK_INTERVAL = 20;
    private static final float INVISIBILITY_CHANCE = 0.08F;
    private static final int INVISIBILITY_MIN_TICKS = 20;
    private static final int INVISIBILITY_MAX_TICKS = 60;

    @Override
    public SimpleNeuralNetwork.MindState state() {
        return SimpleNeuralNetwork.MindState.OBSERVER;
    }

    @Override
    public boolean shouldEnter(HeroMindStateSnapshot snapshot) {
        return snapshot.respectWeight() < 0.65f
                && snapshot.entropyScore() < 0.35f
                && snapshot.metaScore() < 0.30f
                && snapshot.nostalgiaScore() < 0.35f;
    }

    @Override
    public SimpleNeuralNetwork.MindState shouldExit(HeroMindStateSnapshot snapshot, HeroEntity hero) {
        if (snapshot.nostalgiaScore() >= 0.35f && snapshot.sorrowWeight() >= 0.30f) return SimpleNeuralNetwork.MindState.REMINISCING;
        if (snapshot.entropyScore() >= 0.35f) return SimpleNeuralNetwork.MindState.MAINTAINER;
        if (snapshot.metaScore() >= 0.30f) return SimpleNeuralNetwork.MindState.GLITCH_LORD;
        if (MonsterKingStateDefinition.meetsEntryRequirements(snapshot)) {
            return SimpleNeuralNetwork.MindState.MONSTER_KING;
        }
        if (snapshot.directAttackScore() >= DIRECT_ATTACK_EXIT_TO_JUDGE_MIN
                && snapshot.annoyanceWeight() >= 0.50f) {
            return SimpleNeuralNetwork.MindState.JUDGE;
        }
        if (snapshot.respectWeight() >= 0.65f) return SimpleNeuralNetwork.MindState.PROTECTOR;
        if (snapshot.curiosityWeight() >= 0.55f) return SimpleNeuralNetwork.MindState.PRANKSTER;
        return null;
    }

    @Override
    public int minDwellTicks() {
        return 1200;
    }
    public static void clearObserverInvisibility(HeroEntity hero) {
        if (hero == null) {
            return;
        }
        hero.getPersistentData().remove(INVISIBLE_UNTIL_KEY);
        if (hero.isInvisible()) {
            hero.setInvisible(false);
        }
    }
    @Override
    public void tickServer(HeroEntity hero) {
        long now = hero.level().getGameTime();
        long invisibleUntil = hero.getPersistentData().getLong(INVISIBLE_UNTIL_KEY);
        if (invisibleUntil > now) {
            if (!hero.isInvisible()) {
                hero.setInvisible(true);
            }
        } else {
            if (hero.isInvisible()) {
                hero.setInvisible(false);
            }
            hero.getPersistentData().remove(INVISIBLE_UNTIL_KEY);
            if (hero.tickCount % INVISIBILITY_CHECK_INTERVAL == 0 && hero.getRandom().nextFloat() < INVISIBILITY_CHANCE) {
                int duration = INVISIBILITY_MIN_TICKS + hero.getRandom().nextInt(INVISIBILITY_MAX_TICKS - INVISIBILITY_MIN_TICKS + 1);
                hero.getPersistentData().putLong(INVISIBLE_UNTIL_KEY, now + duration);
                hero.setInvisible(true);
            }
        }

        ServerPlayer focus = HeroStateBehaviorSupport.getFocusPlayer(hero, 24.0D);
        if (focus == null) return;

        recordObservedLocation(hero, focus);
        HeroStateBehaviorSupport.ensureFloating(hero);
        HeroStateBehaviorSupport.clearAggro(hero);
        double distSqr = hero.distanceToSqr(focus);

        if (distSqr < 36.0D) {
            HeroStateBehaviorSupport.driftAway(hero, focus, 4.5D, 1.05D);
            HeroStateBehaviorSupport.alignHeadToBody(hero);
            return;
        }

        if (HeroStateBehaviorSupport.shouldStareTeleport(hero, focus, distSqr)
                && HeroStateBehaviorSupport.teleportToPatrolPoint(hero, focus, 8.0D, 20.0D)) {
            return;
        }

        if (hero.tickCount % 60 == 0) {
            ServerLevel level = (ServerLevel) hero.level();
            BlockPos perch = HeroStateBehaviorSupport.findHighestNearbyPerch(level, focus.blockPosition(), 8, 14, 2, 6);
            if (perch != null && (distSqr > 64.0D || hero.getRandom().nextFloat() < 0.35F)) {
                HeroStateBehaviorSupport.teleportToPerch(hero, perch);
                return;
            }
        }

        if (distSqr > 64.0D && distSqr < 400.0D && hero.getRandom().nextFloat() < 0.35F) {
            HeroStateBehaviorSupport.moveToOrbitStable(hero, focus, 10.0D, 0.65D, 120.0F, 18.0F, 40);
            HeroStateBehaviorSupport.alignHeadToBody(hero);
            return;
        }

        if (HeroStateBehaviorSupport.shouldShadowTeleport(hero, focus, distSqr)
                && HeroStateBehaviorSupport.teleportNearShadow(hero, focus)) {
            return;
        }

        if (hero.tickCount % 40 == 0 || hero.getDeltaMovement().horizontalDistanceSqr() < 0.01D) {
            HeroStateBehaviorSupport.clearAggroAndLookAt(hero, focus);
        } else {
            HeroStateBehaviorSupport.alignHeadToBody(hero);
        }

        if (hero.tickCount % 120 == 0) {
            HeroStateBehaviorSupport.clearAnomalyFire((ServerLevel) hero.level(), hero.blockPosition(), 5);
        }

        if (hero.tickCount % 80 == 0
                && hero.level().getGameTime() >= hero.getPersistentData().getLong(TRACE_COOLDOWN_KEY)
                && hero.getRandom().nextFloat() < TRACE_CHANCE
                && tryLeaveTraceTorch(hero, (ServerLevel) hero.level())) {
            hero.getPersistentData().putLong(TRACE_COOLDOWN_KEY, hero.level().getGameTime() + TRACE_COOLDOWN);
        }
    }

    @Override
    public void tickClientAmbient(HeroEntity hero) {
    }

    private static void recordObservedLocation(HeroEntity hero, ServerPlayer focus) {
        if (hero.tickCount % RECORD_LOCATION_INTERVAL != 0) {
            return;
        }

        ListTag locations = hero.getPersistentData().getList(OBSERVED_LOCATIONS_KEY, Tag.TAG_COMPOUND);
        double px = focus.getX();
        double py = focus.getY();
        double pz = focus.getZ();
        boolean merged = false;

        for (int i = 0; i < locations.size(); i++) {
            CompoundTag hotspot = locations.getCompound(i);
            double dx = px - hotspot.getDouble("x");
            double dz = pz - hotspot.getDouble("z");
            if (dx * dx + dz * dz < LOCATION_MERGE_DIST_SQR) {
                hotspot.putDouble("x", px);
                hotspot.putDouble("y", py);
                hotspot.putDouble("z", pz);
                hotspot.putInt("count", hotspot.getInt("count") + 1);
                merged = true;
                break;
            }
        }

        if (!merged) {
            CompoundTag hotspot = new CompoundTag();
            hotspot.putDouble("x", px);
            hotspot.putDouble("y", py);
            hotspot.putDouble("z", pz);
            hotspot.putInt("count", 1);
            locations.add(hotspot);
        }

        while (locations.size() > MAX_RECORDED_LOCATIONS) {
            int removeIndex = 0;
            int minCount = Integer.MAX_VALUE;
            for (int i = 0; i < locations.size(); i++) {
                int count = locations.getCompound(i).getInt("count");
                if (count < minCount) {
                    minCount = count;
                    removeIndex = i;
                }
            }
            locations.remove(removeIndex);
        }

        hero.getPersistentData().put(OBSERVED_LOCATIONS_KEY, locations);
    }

    private static boolean tryLeaveTraceTorch(HeroEntity hero, ServerLevel level) {
        ListTag locations = hero.getPersistentData().getList(OBSERVED_LOCATIONS_KEY, Tag.TAG_COMPOUND);
        if (locations.isEmpty()) {
            return false;
        }

        CompoundTag bestHotspot = null;
        int bestCount = TRACE_MIN_COUNT - 1;
        for (int i = 0; i < locations.size(); i++) {
            CompoundTag hotspot = locations.getCompound(i);
            int count = hotspot.getInt("count");
            if (count > bestCount) {
                bestCount = count;
                bestHotspot = hotspot;
            }
        }
        if (bestHotspot == null) {
            return false;
        }

        int baseX = (int) Math.round(bestHotspot.getDouble("x"));
        int baseY = (int) Math.round(bestHotspot.getDouble("y"));
        int baseZ = (int) Math.round(bestHotspot.getDouble("z"));
        for (int attempt = 0; attempt < 6; attempt++) {
            double angle = hero.getRandom().nextDouble() * Math.PI * 2.0D;
            int offset = TRACE_MIN_OFFSET + hero.getRandom().nextInt(TRACE_MAX_OFFSET - TRACE_MIN_OFFSET + 1);
            int tx = (int) Math.round(baseX + Math.cos(angle) * offset);
            int tz = (int) Math.round(baseZ + Math.sin(angle) * offset);
            for (int dy = 3; dy >= -4; dy--) {
                BlockPos floor = new BlockPos(tx, baseY + dy, tz);
                BlockPos torchPos = floor.above();
                if (!HeroStateBehaviorSupport.isStandable(level, floor)) {
                    continue;
                }
                if (!level.isEmptyBlock(torchPos) || hasNearbyTorch(level, torchPos, 2)) {
                    continue;
                }
                if (!level.setBlockAndUpdate(torchPos, Blocks.TORCH.defaultBlockState())) {
                    continue;
                }
                Vec3 fxPos = new Vec3(torchPos.getX() + 0.5D, torchPos.getY() + 0.6D, torchPos.getZ() + 0.5D);
                HeroStateBehaviorSupport.spawnParticles(level, ParticleTypes.END_ROD, fxPos, 5, 0.15D);
                level.playSound(null, torchPos, SoundEvents.WOOD_PLACE, SoundSource.HOSTILE, 0.5F, 0.9F);
                return true;
            }
        }
        return false;
    }

    private static boolean hasNearbyTorch(ServerLevel level, BlockPos center, int range) {
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-range, -1, -range), center.offset(range, 2, range))) {
            if (level.getBlockState(pos).is(Blocks.TORCH)
                    || level.getBlockState(pos).is(Blocks.WALL_TORCH)
                    || level.getBlockState(pos).is(Blocks.SOUL_TORCH)
                    || level.getBlockState(pos).is(Blocks.REDSTONE_TORCH)) {
                return true;
            }
        }
        return false;
    }
}
