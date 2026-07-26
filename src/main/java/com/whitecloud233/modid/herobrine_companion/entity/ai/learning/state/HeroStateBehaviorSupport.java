package com.whitecloud233.modid.herobrine_companion.entity.ai.learning.state;

import com.whitecloud233.modid.herobrine_companion.entity.ai.learning.HeroStateGoals;
import com.whitecloud233.modid.herobrine_companion.entity.HeroEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Comparator;
import java.util.List;

final class HeroStateBehaviorSupport {

    private HeroStateBehaviorSupport() {
    }

    static ServerPlayer getOwner(HeroEntity hero) {
        if (hero.getOwnerUUID() == null) {
            return null;
        }
        Player player = hero.level().getPlayerByUUID(hero.getOwnerUUID());
        return player instanceof ServerPlayer serverPlayer && serverPlayer.isAlive() ? serverPlayer : null;
    }

    static ServerPlayer getNearestPlayer(HeroEntity hero, double range) {
        Player player = hero.level().getNearestPlayer(hero, range);
        return player instanceof ServerPlayer serverPlayer && serverPlayer.isAlive() ? serverPlayer : null;
    }

    static ServerPlayer getFocusPlayer(HeroEntity hero, double range) {
        ServerPlayer owner = getOwner(hero);
        return owner != null ? owner : getNearestPlayer(hero, range);
    }

    static boolean isRuntimeStateBlockingMindSupport(HeroEntity hero) {
        return hero.getTradingPlayer() != null
                || hero.getInvitedPos() != null
                || hero.isBattleModeActive()
                || hero.getVehicle() != null;
    }

    static boolean isRuntimeStateBlockingMindMovement(HeroEntity hero) {
        return isRuntimeStateBlockingMindSupport(hero)
                // 陪伴模式只保留状态支援效果，移动由专用跟随/救援 Goal 接管。
                || hero.isCompanionMode()
                || hasConflictingGoalControl(hero);
    }

    private static boolean hasConflictingGoalControl(HeroEntity hero) {
        for (WrappedGoal wrappedGoal : hero.getGoalSelector().getAvailableGoals()) {
            if (!wrappedGoal.isRunning()) {
                continue;
            }

            Goal goal = wrappedGoal.getGoal();
            if (goal instanceof HeroStateGoals || goal instanceof RandomLookAroundGoal || goal instanceof FloatGoal) {
                continue;
            }

            if (goal.getFlags().contains(Goal.Flag.LOOK) || goal.getFlags().contains(Goal.Flag.MOVE)) {
                return true;
            }
        }
        return false;
    }

    static void ensureFloating(HeroEntity hero) {
        if (!hero.isInWater()) {
            hero.setFloating(true);
            hero.setNoGravity(true);
        }
    }

    static void ensureGrounded(HeroEntity hero) {
        hero.noPhysics = false;
        if (hero.isFloating()) hero.setFloating(false);
        if (hero.isNoGravity()) hero.setNoGravity(false);
    }

    static void clearAggro(HeroEntity hero) {
        hero.setTarget(null);
    }

    static void stopAndLookAt(HeroEntity hero, Entity target) {
        hero.setTarget(null);
        hero.getNavigation().stop();
        if (hero.getDeltaMovement().horizontalDistanceSqr() > 0.02D) {
            alignHeadToBody(hero);
            return;
        }
        hero.getLookControl().setLookAt(target, 30.0F, 30.0F);
    }

    static void clearAggroAndLookAt(HeroEntity hero, Entity target) {
        hero.setTarget(null);
        if (hero.getDeltaMovement().horizontalDistanceSqr() > 0.02D) {
            alignHeadToBody(hero);
            return;
        }
        hero.getLookControl().setLookAt(target, 20.0F, 20.0F);
    }

    static void alignHeadToBody(HeroEntity hero) {
        hero.setTarget(null);
        float bodyYaw = hero.getYRot();
        hero.setYHeadRot(bodyYaw);
        hero.yHeadRotO = bodyYaw;
    }

    static void lookAtPos(HeroEntity hero, Vec3 pos) {
        hero.getLookControl().setLookAt(pos.x, pos.y, pos.z, 30.0F, 30.0F);
    }

    static void driftAway(HeroEntity hero, Entity target, double distance, double speed) {
        Vec3 away = hero.position().subtract(target.position());
        if (away.lengthSqr() < 0.001D) {
            away = new Vec3(hero.getRandom().nextDouble() - 0.5D, 0.0D, hero.getRandom().nextDouble() - 0.5D);
        }
        Vec3 goal = hero.position().add(away.normalize().scale(distance)).add(0.0D, 0.8D, 0.0D);
        hero.getMoveControl().setWantedPosition(goal.x, goal.y, goal.z, speed);
    }

    static void walkAway(HeroEntity hero, Entity target, double distance, double speed) {
        Vec3 away = hero.position().subtract(target.position());
        away = new Vec3(away.x, 0.0D, away.z);
        if (away.lengthSqr() < 0.001D) {
            away = new Vec3(hero.getRandom().nextDouble() - 0.5D, 0.0D,
                    hero.getRandom().nextDouble() - 0.5D);
        }
        Vec3 goal = hero.position().add(away.normalize().scale(distance));
        boolean pathStarted = hero.getNavigation().moveTo(goal.x, hero.getY(), goal.z, speed);
        if (!pathStarted) {
            hero.getMoveControl().setWantedPosition(goal.x, hero.getY(), goal.z, speed);
        }
    }

    static void moveToOrbit(HeroEntity hero, Entity target, double radius, double speed, float baseOffset) {
        float orbitYaw = target.getYRot() + baseOffset + hero.getRandom().nextFloat() * 80.0F - 40.0F;
        Vec3 orbit = Vec3.directionFromRotation(0.0F, orbitYaw).scale(radius);
        double targetY = target.getY() + 1.0D + Mth.clamp(target.getDeltaMovement().y, -0.5D, 1.0D);
        hero.getMoveControl().setWantedPosition(target.getX() + orbit.x, targetY, target.getZ() + orbit.z, speed);
    }

    static void moveToOrbitStable(HeroEntity hero, Entity target, double radius, double speed, float baseOffset, float swingDegrees, int segmentTicks) {
        int segment = Math.max(1, segmentTicks);
        int phase = (hero.tickCount / segment) & 1;
        float orbitYaw = target.getYRot() + baseOffset + (phase == 0 ? -swingDegrees : swingDegrees);
        Vec3 orbit = Vec3.directionFromRotation(0.0F, orbitYaw).scale(radius);
        double targetY = target.getY() + 1.0D + Mth.clamp(target.getDeltaMovement().y, -0.5D, 1.0D);
        hero.getMoveControl().setWantedPosition(target.getX() + orbit.x, targetY, target.getZ() + orbit.z, speed);
    }

    static boolean isPlayerStaringAtHero(ServerPlayer player, HeroEntity hero, double angleThresholdDeg) {
        Vec3 eyePos = player.getEyePosition();
        Vec3 toHero = hero.getEyePosition().subtract(eyePos);
        if (toHero.lengthSqr() < 0.001D) {
            return false;
        }
        Vec3 look = player.getLookAngle();
        double dot = look.normalize().dot(toHero.normalize());
        double threshold = Math.cos(Math.toRadians(angleThresholdDeg));
        return dot >= threshold;
    }

    static boolean shouldStareTeleport(HeroEntity hero, ServerPlayer focus, double distSqr) {
        if (distSqr <= 225.0D || distSqr >= 1600.0D) return false;
        if (hero.tickCount < 40) return false;
        return isPlayerStaringAtHero(focus, hero, 12.0D);
    }

    static boolean shouldShadowTeleport(HeroEntity hero, ServerPlayer focus, double distSqr) {
        if (!shouldStareTeleport(hero, focus, distSqr)) return false;
        if (hero.level().getBrightness(LightLayer.BLOCK, focus.blockPosition()) > 7) return false;
        return !focus.hasLineOfSight(hero);
    }

    static boolean teleportNearShadow(HeroEntity hero, ServerPlayer focus) {
        ServerLevel level = (ServerLevel) hero.level();
        Vec3 target = focus.position().add(buildShadowOffset(hero, focus));
        BlockPos landing = findStandingPos(level, BlockPos.containing(target.x, focus.getY(), target.z), 5, 5);
        if (landing == null) {
            return false;
        }
        spawnParticles(level, ParticleTypes.PORTAL, hero.position().add(0.0D, 1.0D, 0.0D), 10, 0.3D);
        hero.teleportTo(landing.getX() + 0.5D, landing.getY(), landing.getZ() + 0.5D);
        stopAndLookAt(hero, focus);
        hero.setDeltaMovement(Vec3.ZERO);
        spawnParticles(level, ParticleTypes.SMOKE, hero.position().add(0.0D, 1.0D, 0.0D), 8, 0.2D);
        level.playSound(null, hero.blockPosition(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.HOSTILE, 0.6F, 0.6F);
        return true;
    }

    static boolean teleportToPatrolPoint(HeroEntity hero, ServerPlayer focus, double minDist, double maxDist) {
        ServerLevel level = (ServerLevel) hero.level();
        Vec3 patrol = findOrbitPoint(hero, focus.position(), minDist, maxDist, focus.getYRot() + 180.0F, 12);
        if (patrol == null) {
            return false;
        }
        spawnParticles(level, ParticleTypes.PORTAL, hero.position().add(0.0D, 1.0D, 0.0D), 10, 0.3D);
        hero.teleportTo(patrol.x, patrol.y, patrol.z);
        hero.setDeltaMovement(Vec3.ZERO);
        stopAndLookAt(hero, focus);
        spawnParticles(level, ParticleTypes.SMOKE, hero.position().add(0.0D, 1.0D, 0.0D), 8, 0.2D);
        level.playSound(null, hero.blockPosition(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.HOSTILE, 0.55F, 0.75F);
        return true;
    }

    private static Vec3 buildShadowOffset(HeroEntity hero, ServerPlayer focus) {
        float angleOffset = hero.getRandom().nextBoolean() ? 110.0F : -110.0F;
        if (hero.getRandom().nextFloat() < 0.4F) {
            angleOffset = 180.0F + (hero.getRandom().nextFloat() - 0.5F) * 50.0F;
        }
        double distance = 4.0D + hero.getRandom().nextDouble() * 2.5D;
        return Vec3.directionFromRotation(0.0F, focus.getYRot() + angleOffset).scale(distance);
    }

    static boolean moveNearPlayer(HeroEntity hero, ServerPlayer player, double minDist, double maxDist, double speed) {
        Vec3 candidate = findOrbitPoint(hero, player.position(), minDist, maxDist, player.getYRot(), 6);
        if (candidate == null) {
            return false;
        }
        hero.getMoveControl().setWantedPosition(candidate.x, candidate.y, candidate.z, speed);
        return true;
    }

    static Vec3 findOrbitPoint(HeroEntity hero, Vec3 center, double minDist, double maxDist, float baseYaw, int attempts) {
        ServerLevel level = (ServerLevel) hero.level();
        for (int i = 0; i < attempts; i++) {
            float yaw = baseYaw + hero.getRandom().nextFloat() * 140.0F - 70.0F;
            double distance = Mth.lerp(hero.getRandom().nextDouble(), minDist, maxDist);
            Vec3 offset = Vec3.directionFromRotation(0.0F, yaw).scale(distance);
            BlockPos pos = BlockPos.containing(center.x + offset.x, center.y, center.z + offset.z);
            BlockPos stand = findStandingPos(level, pos, 4, 6);
            if (stand != null) {
                return new Vec3(stand.getX() + 0.5D, stand.getY() + 0.2D, stand.getZ() + 0.5D);
            }
        }
        return null;
    }

    static BlockPos findStandingPos(ServerLevel level, BlockPos center, int verticalUp, int verticalDown) {
        for (int dy = verticalUp; dy >= -verticalDown; dy--) {
            BlockPos floor = center.offset(0, dy - 1, 0);
            BlockPos foot = floor.above();
            BlockPos head = foot.above();
            if (isStandable(level, floor) && level.isEmptyBlock(foot) && level.isEmptyBlock(head)) {
                return foot;
            }
        }
        return null;
    }

    static boolean isStandable(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return !state.isAir() && state.isCollisionShapeFullBlock(level, pos) && !state.liquid();
    }

    static boolean isNight(HeroEntity hero) {
        return hero.level().isNight();
    }

    static void spawnParticles(ServerLevel level, ParticleOptions particle, Vec3 pos, int count, double spread) {
        level.sendParticles(particle, pos.x, pos.y, pos.z, count, spread, spread, spread, 0.01D);
    }

    static void spawnParticles(ServerLevel level, ParticleOptions particle, Vec3 pos, int count, double spread, double speed) {
        level.sendParticles(particle, pos.x, pos.y, pos.z, count, spread, spread, spread, speed);
    }

    static void playSound(ServerLevel level, BlockPos pos, SoundEvent sound, SoundSource source, float volume, float pitch) {
        level.playSound(null, pos, sound, source, volume, pitch);
    }

    static LivingEntity getRecentAttacker(ServerPlayer player, int windowTicks) {
        LivingEntity attacker = player.getLastHurtByMob();
        if (attacker == null || !attacker.isAlive()) return null;
        return player.tickCount - player.getLastHurtByMobTimestamp() <= windowTicks ? attacker : null;
    }

    static List<Monster> getNearbyMonsters(HeroEntity hero, Entity center, double range) {
        return hero.level().getEntitiesOfClass(Monster.class, center.getBoundingBox().inflate(range), Monster::isAlive);
    }

    static List<ItemEntity> getNearbyItems(HeroEntity hero, Entity center, double range) {
        return hero.level().getEntitiesOfClass(ItemEntity.class, center.getBoundingBox().inflate(range), ItemEntity::isAlive);
    }

    static Monster getNearestMonster(HeroEntity hero, Entity center, double range) {
        return getNearbyMonsters(hero, center, range).stream()
                .min(Comparator.comparingDouble(center::distanceToSqr))
                .orElse(null);
    }

    static void pacifyMonster(Monster monster) {
        monster.setTarget(null);
        monster.setAggressive(false);
    }

    static void commandMonsterEscort(Monster monster, HeroEntity hero, int index, int total, double radius) {
        double angle = (Math.PI * 2.0D * index) / Math.max(1, total);
        double tx = hero.getX() + Math.cos(angle) * radius;
        double tz = hero.getZ() + Math.sin(angle) * radius;
        monster.getNavigation().moveTo(tx, hero.getY(), tz, 1.0D);
        monster.getLookControl().setLookAt(hero, 20.0F, 20.0F);
    }

    static void forceMonsterPressure(Monster monster, ServerPlayer player) {
        monster.getLookControl().setLookAt(player, 20.0F, 20.0F);
        if (monster.distanceToSqr(player) < 64.0D && monster.getRandom().nextFloat() < 0.35F) {
            monster.setTarget(player);
        }
    }

    static int extinguishNearbyFire(ServerLevel level, BlockPos center, int horizontal, int down, int up, int limit) {
        int cleared = 0;
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-horizontal, -down, -horizontal), center.offset(horizontal, up, horizontal))) {
            if (cleared >= limit) break;
            BlockState state = level.getBlockState(pos);
            if (state.is(Blocks.FIRE) || state.is(Blocks.SOUL_FIRE)) {
                level.setBlockAndUpdate(pos.immutable(), Blocks.AIR.defaultBlockState());
                cleared++;
            }
        }
        return cleared;
    }

    static int clearNearbyLava(ServerLevel level, BlockPos center, int horizontal, int down, int up, int limit) {
        int cleared = 0;
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-horizontal, -down, -horizontal), center.offset(horizontal, up, horizontal))) {
            if (cleared >= limit) break;
            BlockState state = level.getBlockState(pos);
            if (state.getBlock() instanceof LiquidBlock && state.getFluidState().is(Fluids.LAVA)) {
                level.setBlockAndUpdate(pos.immutable(), Blocks.STONE.defaultBlockState());
                cleared++;
            }
        }
        return cleared;
    }

    static boolean clearAnomalyFire(ServerLevel level, BlockPos center, int range) {
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-range, -2, -range), center.offset(range, 4, range))) {
            BlockState state = level.getBlockState(pos);
            if ((state.is(Blocks.FIRE) || state.is(Blocks.SOUL_FIRE)) && !isStandable(level, pos.below())) {
                level.setBlockAndUpdate(pos.immutable(), Blocks.AIR.defaultBlockState());
                return true;
            }
        }
        return false;
    }

    static boolean toggleNearbyDoor(ServerLevel level, BlockPos center, int range) {
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-range, -1, -range), center.offset(range, 3, range))) {
            BlockState state = level.getBlockState(pos);
            if (state.getBlock() instanceof DoorBlock) {
                level.levelEvent(null, 1006, pos.immutable(), 0);
                return true;
            }
        }
        return false;
    }

    static boolean toggleNearbyLight(ServerLevel level, BlockPos center, int range) {
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-range, -2, -range), center.offset(range, 4, range))) {
            BlockState state = level.getBlockState(pos);
            if (state.is(Blocks.TORCH) || state.is(Blocks.WALL_TORCH) || state.is(Blocks.REDSTONE_TORCH)
                    || state.is(Blocks.SOUL_TORCH) || state.is(Blocks.LANTERN) || state.is(Blocks.SOUL_LANTERN)
                    || state.is(Blocks.GLOWSTONE) || state.is(Blocks.REDSTONE_LAMP)) {
                level.setBlockAndUpdate(pos.immutable(), Blocks.AIR.defaultBlockState());
                return true;
            }
        }
        return false;
    }

    static void shortTeleport(HeroEntity hero, Vec3 target) {
        ServerLevel level = (ServerLevel) hero.level();
        spawnParticles(level, ParticleTypes.END_ROD, hero.position().add(0.0D, 1.0D, 0.0D), 8, 0.2D);
        hero.teleportTo(target.x, target.y, target.z);
        spawnParticles(level, ParticleTypes.PORTAL, hero.position().add(0.0D, 1.0D, 0.0D), 10, 0.2D);
    }

    static Vec3 findNearbyTeleportPoint(HeroEntity hero, ServerPlayer focus, double minDist, double maxDist) {
        Vec3 point = findOrbitPoint(hero, focus.position(), minDist, maxDist, focus.getYRot() + 180.0F, 8);
        return point != null ? point : hero.position();
    }

    static boolean hasNearbyHostiles(HeroEntity hero, ServerPlayer player, double range) {
        return !getNearbyMonsters(hero, player, range).isEmpty();
    }

    static void keepDistance(HeroEntity hero, ServerPlayer player, double minDist, double maxDist, double closeSpeed, double farSpeed) {
        double distSqr = hero.distanceToSqr(player);
        if (distSqr < minDist * minDist) {
            driftAway(hero, player, minDist, closeSpeed);
        } else if (distSqr > maxDist * maxDist) {
            moveNearPlayer(hero, player, minDist, maxDist, farSpeed);
        }
    }

    static AABB aroundPlayer(ServerPlayer player, double range) {
        return player.getBoundingBox().inflate(range);
    }

    static void applyGlow(ServerLevel level, Vec3 pos) {
        level.sendParticles(ParticleTypes.GLOW, pos.x, pos.y, pos.z, 5, 0.3D, 0.5D, 0.3D, 0.01D);
    }

    static void applyEffect(LivingEntity entity, MobEffectInstance effect) {
        entity.addEffect(effect);
    }

    static void stareAtSky(HeroEntity hero) {
        hero.setXRot(-80.0F);
        hero.yHeadRot = hero.getYRot();
    }

    static void spawnClientAmbient(HeroEntity hero, ParticleOptions particle, int chance, double spreadXz, double baseY, double height) {
        if (hero.getRandom().nextInt(chance) != 0) return;
        double x = hero.getX() + (hero.getRandom().nextDouble() - 0.5D) * spreadXz;
        double y = hero.getY() + baseY + hero.getRandom().nextDouble() * height;
        double z = hero.getZ() + (hero.getRandom().nextDouble() - 0.5D) * spreadXz;
        hero.level().addParticle(particle, x, y, z, 0.0D, 0.0D, 0.0D);
    }

    static BlockPos findHighestNearbyPerch(ServerLevel level, BlockPos center, int minRadius, int maxRadius, int minY, int maxY) {
        for (int i = 0; i < 12; i++) {
            int dx = Mth.nextInt(level.random, -maxRadius, maxRadius);
            int dz = Mth.nextInt(level.random, -maxRadius, maxRadius);
            if ((dx * dx) + (dz * dz) < minRadius * minRadius) {
                continue;
            }
            BlockPos sample = center.offset(dx, 0, dz);
            for (int dy = maxY; dy >= minY; dy--) {
                BlockPos perch = findStandingPos(level, sample.above(dy), 1, 2);
                if (perch != null) {
                    return perch;
                }
            }
        }
        return null;
    }

    static boolean teleportToPerch(HeroEntity hero, BlockPos pos) {
        ServerLevel level = (ServerLevel) hero.level();
        spawnParticles(level, ParticleTypes.PORTAL, hero.position().add(0.0D, 1.0D, 0.0D), 8, 0.2D);
        hero.teleportTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D);
        hero.setDeltaMovement(Vec3.ZERO);
        spawnParticles(level, ParticleTypes.SMOKE, hero.position().add(0.0D, 1.0D, 0.0D), 6, 0.15D);
        return true;
    }

    static void moveToBlock(HeroEntity hero, BlockPos pos, double speed) {
        hero.getMoveControl().setWantedPosition(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D, speed);
    }

    static boolean isLowHealth(ServerPlayer player, double thresholdRatio) {
        return player.getHealth() <= player.getMaxHealth() * thresholdRatio;
    }

    static BlockPos offsetBehind(ServerPlayer player, double distance) {
        Vec3 backward = Vec3.directionFromRotation(0.0F, player.getYRot()).scale(-distance);
        return BlockPos.containing(player.getX() + backward.x, player.getY(), player.getZ() + backward.z);
    }

    static Direction randomHorizontalDirection(HeroEntity hero) {
        return Direction.Plane.HORIZONTAL.getRandomDirection(hero.getRandom());
    }
}
