package com.whitecloud233.herobrine_companion.entity.ai.learning;

import com.whitecloud233.herobrine_companion.entity.HeroEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.EnderChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext; // [NeoForge 1.21] 必需
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

public class HeroInvitedActionGoal extends Goal {
    private final HeroEntity hero;
    private BlockPos targetPos;
    private int actionType;
    private int timer;
    private boolean hasArrived;
    private Entity seatEntity;
    private int navigationTicks;
    private Vec3 lastPos;

    private boolean isGuardingContainer = false;
    private boolean isGuardingDoor = false;

    private Vec3 wanderTarget;
    private Vec3 cachedDoorStandPos;

    public HeroInvitedActionGoal(HeroEntity hero) {
        this.hero = hero;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        BlockPos pos = this.hero.getInvitedPos();
        if (pos == null) return false;

        SimpleNeuralNetwork.MindState state = this.hero.getHeroBrain().getState();
        if (state == SimpleNeuralNetwork.MindState.JUDGE && this.hero.getRandom().nextFloat() < 0.7f) {
            this.hero.setInvitedPos(null);
            this.hero.setInvitedAction(0);

            this.hero.level().playSound(null, this.hero.blockPosition(), SoundEvents.BEACON_DEACTIVATE, SoundSource.NEUTRAL, 1.0f, 0.5f);

            if (this.hero.level() instanceof ServerLevel serverLevel) {
                serverLevel.sendParticles(ParticleTypes.SMOKE,
                        this.hero.getX(), this.hero.getY() + 1.8, this.hero.getZ(),
                        5, 0.2, 0.2, 0.2, 0.0);
            }
            if (this.hero.getOwnerUUID() != null) {
                Player owner = this.hero.level().getPlayerByUUID(this.hero.getOwnerUUID());
                if (owner != null) {
                    owner.sendSystemMessage(net.minecraft.network.chat.Component.translatable("message.herobrine_companion.invite_refuse"));
                }
            }
            return false;
        }
        this.targetPos = pos;
        this.actionType = this.hero.getInvitedAction();
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        BlockPos currentInvitedPos = this.hero.getInvitedPos();
        if (currentInvitedPos == null || !currentInvitedPos.equals(this.targetPos)) {
            return false;
        }
        if (this.actionType == 2 || this.actionType == 3) {
            return true;
        }
        return this.timer < 400;
    }

    @Override
    public void start() {
        this.timer = 0;
        this.navigationTicks = 0;
        this.hasArrived = false;
        this.seatEntity = null;
        this.lastPos = this.hero.position();
        this.wanderTarget = null;
        this.cachedDoorStandPos = null;

        this.hero.getNavigation().stop();
        this.hero.setNoGravity(true);

        if (this.actionType == 3) {
            BlockState state = this.hero.level().getBlockState(this.targetPos);
            this.isGuardingDoor = state.getBlock() instanceof DoorBlock || state.getBlock() instanceof TrapDoorBlock || state.getBlock() instanceof FenceGateBlock;
            this.isGuardingContainer = state.hasBlockEntity() || state.getBlock() instanceof EnderChestBlock;
        }
    }

    @Override
    public void stop() {
        if (this.hero.isPassenger()) {
            this.hero.stopRiding();
        }
        if (this.seatEntity != null && this.seatEntity.isAlive()) {
            this.seatEntity.discard();
        }
        this.seatEntity = null;
        this.targetPos = null;
        this.actionType = 0;
        if (this.hero.getInvitedPos() != null) {
            this.hero.setInvitedPos(null);
            this.hero.setInvitedAction(0);
        }
        this.hero.setNoGravity(false);
        this.isGuardingDoor = false;
        this.isGuardingContainer = false;
        this.cachedDoorStandPos = null;
    }

    @Override
    public void tick() {
        if (this.targetPos == null) return;

        Vec3 destination;
        if (this.actionType == 3 && this.isGuardingDoor) {
            if (this.cachedDoorStandPos == null || this.timer % 100 == 0) {
                this.cachedDoorStandPos = getDoorStandPos();
            }
            destination = this.cachedDoorStandPos;
        } else {
            destination = Vec3.atCenterOf(this.targetPos);
        }

        double arriveDistance = (this.actionType == 3 && this.isGuardingContainer) ? 16.0D : 4.0D;
        double distSqr = this.hero.distanceToSqr(destination);

        if (!this.hasArrived) {
            this.navigationTicks++;

            if (this.navigationTicks % 20 == 0) {
                double moveDist = this.hero.position().distanceToSqr(this.lastPos);
                this.lastPos = this.hero.position();
                if (moveDist < 0.01D && distSqr > 16.0D) {
                    teleportToTarget(destination);
                    return;
                }
            }

            if (this.navigationTicks > 100 && distSqr > 9.0D) {
                teleportToTarget(destination);
                return;
            }

            if (distSqr < arriveDistance) {
                this.hasArrived = true;
                this.hero.setDeltaMovement(Vec3.ZERO);
                if (!this.isGuardingContainer) {
                    this.hero.setNoGravity(false);
                }
                performAction();
            } else {
                moveTowardsTarget(destination);
            }
        } else {
            this.timer++;

            if (this.actionType == 3 && this.isGuardingContainer) {
                this.hero.setNoGravity(true);
            } else if (!this.hero.isNoGravity() && this.hero.isNoGravity()) {
                this.hero.setNoGravity(false);
            }

            if (this.actionType == 3) {
                if (this.isGuardingContainer) {
                    performContainerGuardLogic(Vec3.atCenterOf(this.targetPos));
                } else if (this.isGuardingDoor) {
                    performDoorGuardLogic();
                } else {
                    performGenericGuardLogic(destination);
                }
            } else if (this.actionType != 2) {
                this.hero.getLookControl().setLookAt(destination);
            }

            if (this.actionType == 1 && this.timer % 10 == 0) spawnInspectParticles();
            if (this.actionType == 2 && this.timer % 20 == 0) spawnRestParticles();
        }
    }

    // ====================================================================================
    //                                  智能站位计算 (1.21.1 适配)
    // ====================================================================================

    private Vec3 getDoorStandPos() {
        BlockState state = this.hero.level().getBlockState(this.targetPos);
        if (!(state.getBlock() instanceof DoorBlock)) {
            return Vec3.atCenterOf(this.targetPos);
        }

        Direction facing = state.getValue(DoorBlock.FACING);
        Direction left = facing.getCounterClockWise();
        Direction right = facing.getClockWise();

        Vec3 doorCenter = Vec3.atCenterOf(this.targetPos);
        Vec3 forwardOffset = Vec3.atLowerCornerOf(facing.getNormal()).scale(0.5);

        Vec3 leftStand = doorCenter.add(forwardOffset).add(Vec3.atLowerCornerOf(left.getNormal()).scale(1.5));
        Vec3 rightStand = doorCenter.add(forwardOffset).add(Vec3.atLowerCornerOf(right.getNormal()).scale(1.5));

        BlockPos leftPos = BlockPos.containing(leftStand);
        BlockPos rightPos = BlockPos.containing(rightStand);

        // [NeoForge 1.21] 使用 CollisionContext
        CollisionContext context = CollisionContext.of(this.hero);
        boolean leftOk = this.hero.level().getBlockState(leftPos).getCollisionShape(this.hero.level(), leftPos, context).isEmpty();
        boolean rightOk = this.hero.level().getBlockState(rightPos).getCollisionShape(this.hero.level(), rightPos, context).isEmpty();

        leftStand = leftStand.add(0, -0.5, 0);
        rightStand = rightStand.add(0, -0.5, 0);

        if (leftOk) return leftStand;
        if (rightOk) return rightStand;

        return doorCenter.add(Vec3.atLowerCornerOf(facing.getNormal()).scale(2.0)).add(0, -0.5, 0);
    }

    // ====================================================================================
    //                                  守护逻辑
    // ====================================================================================

    private void performDoorGuardLogic() {
        extinguishFireAround(this.targetPos);

        BlockState state = this.hero.level().getBlockState(this.targetPos);
        if (!(state.getBlock() instanceof DoorBlock)) return;

        boolean isOpen = state.getValue(DoorBlock.OPEN);
        Direction facing = state.getValue(DoorBlock.FACING);
        BlockPos doorPos = this.targetPos;

        if (this.cachedDoorStandPos != null) {
            double dist = this.hero.distanceToSqr(this.cachedDoorStandPos);
            if (dist > 0.05) {
                Vec3 move = this.cachedDoorStandPos.subtract(this.hero.position()).scale(0.1);
                this.hero.setDeltaMovement(move);
            }
        }

        Vec3 doorFront = Vec3.atCenterOf(doorPos).add(Vec3.atLowerCornerOf(facing.getNormal()).scale(5));
        this.hero.getLookControl().setLookAt(doorFront);

        List<LivingEntity> nearbyEntities = this.hero.level().getEntitiesOfClass(LivingEntity.class, new AABB(doorPos).inflate(3.5));
        boolean ownerNear = false;
        boolean enemyNear = false;

        for (LivingEntity entity : nearbyEntities) {
            if (entity.getUUID().equals(this.hero.getOwnerUUID())) {
                ownerNear = true;
            } else if (entity instanceof Mob || (entity instanceof Player && !entity.getUUID().equals(this.hero.getOwnerUUID()))) {
                if (entity.distanceToSqr(Vec3.atCenterOf(doorPos)) < 5.0) {
                    enemyNear = true;
                }
            }
        }

        if (ownerNear) {
            if (!isOpen && !enemyNear) {
                this.hero.level().playSound(null, doorPos, SoundEvents.ILLUSIONER_CAST_SPELL, SoundSource.PLAYERS, 0.5f, 2.0f);
                ((DoorBlock)state.getBlock()).setOpen(this.hero, this.hero.level(), state, doorPos, true);
                spawnDoorMagic(doorPos);
            }
            return;
        }

        if (isOpen) {
            if (enemyNear) {
                ((DoorBlock)state.getBlock()).setOpen(this.hero, this.hero.level(), state, doorPos, false);
                this.hero.level().playSound(null, doorPos, SoundEvents.ZOMBIE_VILLAGER_CURE, SoundSource.BLOCKS, 0.5f, 0.5f);
                if (this.hero.level() instanceof ServerLevel serverLevel) {
                    serverLevel.sendParticles(ParticleTypes.EXPLOSION,
                            doorPos.getX() + 0.5, doorPos.getY() + 1.0, doorPos.getZ() + 0.5,
                            1, 0, 0, 0, 0);
                }
            } else {
                ((DoorBlock)state.getBlock()).setOpen(this.hero, this.hero.level(), state, doorPos, false);
                this.hero.level().playSound(null, doorPos, SoundEvents.WOODEN_DOOR_CLOSE, SoundSource.BLOCKS, 1.0f, 1.0f);
            }
        }
    }

    private void extinguishFireAround(BlockPos center) {
        if (this.hero.level().isClientSide || this.hero.tickCount % 5 != 0) return;
        ServerLevel level = (ServerLevel) this.hero.level();

        int scanRadius = 10;
        BlockPos start = center.offset(-scanRadius, -scanRadius, -scanRadius);
        BlockPos end = center.offset(scanRadius, scanRadius, scanRadius);

        for (BlockPos pos : BlockPos.betweenClosed(start, end)) {
            BlockState bs = level.getBlockState(pos);
            if (bs.is(Blocks.FIRE) || bs.is(Blocks.SOUL_FIRE) || bs.getBlock() instanceof MagmaBlock) {
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
                level.sendParticles(ParticleTypes.SMOKE,
                        pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                        3, 0.2, 0.2, 0.2, 0.0);
            }
        }
    }

    private void performContainerGuardLogic(Vec3 chestPos) {
        extinguishFireAround(BlockPos.containing(chestPos));

        if (this.hero.tickCount % 100 == 0 || this.wanderTarget == null || this.hero.distanceToSqr(this.wanderTarget) < 1.0) {
            double angle = this.hero.getRandom().nextDouble() * Math.PI * 2;
            double radius = 2.0 + this.hero.getRandom().nextDouble() * 2.0;
            double x = chestPos.x + Math.cos(angle) * radius;
            double z = chestPos.z + Math.sin(angle) * radius;
            double y = chestPos.y + 0.5 + Math.sin(this.hero.tickCount * 0.05) * 0.5;
            this.wanderTarget = new Vec3(x, y, z);
        }
        Vec3 moveDir = this.wanderTarget.subtract(this.hero.position()).normalize().scale(0.02);
        this.hero.setDeltaMovement(this.hero.getDeltaMovement().add(moveDir).scale(0.9));

        boolean staringAtThreat = false;
        List<Player> nearbyPlayers = this.hero.level().getEntitiesOfClass(Player.class, new AABB(this.targetPos).inflate(5.0));

        for (Player p : nearbyPlayers) {
            if (!p.getUUID().equals(this.hero.getOwnerUUID()) && !p.isCreative()) {
                if (p.distanceToSqr(chestPos) < 12.25) {
                    staringAtThreat = true;
                    this.hero.getLookControl().setLookAt(p, 100.0f, 100.0f);
                    if (this.hero.tickCount % 20 == 0) {
                        this.hero.level().playSound(null, this.hero.blockPosition(), SoundEvents.WARDEN_HEARTBEAT, SoundSource.HOSTILE, 1.0f, 0.5f);
                        p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 20, 0, true, false));
                        p.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, 20, 1, true, false));
                    }
                }
            }
        }

        if (!staringAtThreat) {
            if (this.hero.tickCount % 60 == 0) {
                double randX = this.hero.getX() + (this.hero.getRandom().nextDouble() - 0.5) * 10;
                double randZ = this.hero.getZ() + (this.hero.getRandom().nextDouble() - 0.5) * 10;
                this.hero.getLookControl().setLookAt(randX, this.hero.getEyeY(), randZ);
            }
        }

        if (this.hero.tickCount % 15 == 0 && this.hero.level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.SCULK_SOUL,
                    chestPos.x, chestPos.y + 0.5, chestPos.z,
                    2, 0.4, 0.4, 0.4, 0.02);
            if (this.hero.getRandom().nextInt(5) == 0) {
                serverLevel.sendParticles(ParticleTypes.SQUID_INK,
                        chestPos.x, chestPos.y + 0.8, chestPos.z,
                        0, 0, 0.1, 0, 0.5);
            }
        }
    }

    private void performGenericGuardLogic(Vec3 targetVec) {
        Vec3 away = this.hero.position().add(this.hero.position().subtract(targetVec));
        this.hero.getLookControl().setLookAt(away);
        if (this.timer % 60 == 0 && this.hero.level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.PORTAL, this.targetPos.getX() + 0.5, this.targetPos.getY() + 1.0, this.targetPos.getZ() + 0.5, 3, 0.5, 0.5, 0.5, 0.05);
        }
        if (this.timer % 100 == 0 && this.hero.getRandom().nextBoolean()) {
            double angle = this.hero.getRandom().nextDouble() * Math.PI * 2;
            Vec3 lookTarget = this.hero.position().add(Math.cos(angle) * 10, 0, Math.sin(angle) * 10);
            this.hero.getLookControl().setLookAt(lookTarget);
        }
    }

    private void spawnDoorMagic(BlockPos pos) {
        if (this.hero.level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.WITCH, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, 5, 0.2, 0.5, 0.2, 0.0);
        }
    }

    private void spawnInspectParticles() {
        if (this.hero.level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.ENCHANT, this.targetPos.getX() + 0.5, this.targetPos.getY() + 1.0, this.targetPos.getZ() + 0.5, 1, 0, 0, 0, 0);
        }
    }

    private void spawnRestParticles() {
        if (this.hero.level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.REVERSE_PORTAL, this.hero.getX(), this.hero.getY() + 0.5, this.hero.getZ(), 1, 0.2, 0.2, 0.2, 0);
        }
    }

    private void moveTowardsTarget(Vec3 targetVec) {
        Vec3 toTarget = targetVec.subtract(this.hero.position());
        double dist = toTarget.length();
        double speed = 0.45;
        Vec3 desiredMove = toTarget.normalize().scale(speed);
        Vec3 avoidance = calculateAvoidance(desiredMove, speed);

        double targetY = targetVec.y;
        if (dist > 3.0) targetY += 1.5;
        double verticalForce = (targetY - this.hero.getY()) * 0.1;
        verticalForce = Mth.clamp(verticalForce, -0.3, 0.3);

        Vec3 finalMove = desiredMove.scale(0.8).add(avoidance.scale(0.2));
        this.hero.setDeltaMovement(finalMove.x, verticalForce, finalMove.z);

        double angle = Mth.atan2(finalMove.z, finalMove.x) * (180F / Math.PI) - 90F;
        this.hero.setYRot(rotlerp(this.hero.getYRot(), (float) angle, 10.0F));
        this.hero.yBodyRot = this.hero.getYRot();
    }

    private Vec3 calculateAvoidance(Vec3 currentVelocity, double speed) {
        Vec3 force = Vec3.ZERO;
        double checkDist = 1.5;
        Vec3 forward = currentVelocity.normalize();
        if (isBlocked(this.hero.position().add(0, 0.5, 0), forward, checkDist)) force = force.add(forward.scale(-1.0));
        Vec3 left = forward.yRot((float)Math.toRadians(45));
        if (isBlocked(this.hero.position().add(0, 0.5, 0), left, checkDist)) force = force.add(left.scale(-0.8));
        Vec3 right = forward.yRot((float)Math.toRadians(-45));
        if (isBlocked(this.hero.position().add(0, 0.5, 0), right, checkDist)) force = force.add(right.scale(-0.8));
        return force.normalize().scale(speed);
    }

    private boolean isBlocked(Vec3 origin, Vec3 direction, double length) {
        Vec3 end = origin.add(direction.scale(length));
        return this.hero.level().clip(new ClipContext(
                origin, end,
                ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE,
                this.hero
        )).getType() != HitResult.Type.MISS;
    }

    private float rotlerp(float current, float target, float maxDelta) {
        float f = Mth.wrapDegrees(target - current);
        if (f > maxDelta) f = maxDelta;
        if (f < -maxDelta) f = -maxDelta;
        return current + f;
    }

    private void teleportToTarget(Vec3 destination) {
        this.hero.teleportTo(destination.x, destination.y, destination.z);
        this.hero.level().playSound(null, this.hero.blockPosition(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.HOSTILE, 1.0F, 1.0F);
        this.hasArrived = true;
        this.hero.setDeltaMovement(Vec3.ZERO);
        if (!this.isGuardingContainer) this.hero.setNoGravity(false);
        performAction();
    }

    private void performAction() {
        if (this.hero.getOwnerUUID() != null) {
            this.hero.getHeroBrain().inputCreativity(this.hero.getOwnerUUID(), 0.1f);
            this.hero.getHeroBrain().inputExploration(this.hero.getOwnerUUID(), 0.05f);

            Player owner = this.hero.level().getPlayerByUUID(this.hero.getOwnerUUID());
            if (owner instanceof ServerPlayer serverPlayer) {
                if (this.actionType == 1) {
                    HeroDialogueHandler.onInspectBlock(this.hero, serverPlayer, this.hero.level().getBlockState(this.targetPos));
                    this.hero.getHeroBrain().inputMeta(this.hero.getOwnerUUID(), 0.05f);
                } else if (this.actionType == 2) {
                    this.hero.getHeroBrain().inputNostalgia(this.hero.getOwnerUUID(), 0.1f);
                } else if (this.actionType == 3) {
                    this.hero.getHeroBrain().inputViolence(this.hero.getOwnerUUID(), 0.05f);
                    this.hero.getHeroBrain().inputCreativity(this.hero.getOwnerUUID(), 0.05f);
                }
            }
        }
        if (this.actionType == 2) {
            createSeatAndSit();
        }
    }

    private void createSeatAndSit() {
        if (this.hero.level().isClientSide) return;

        BlockState state = this.hero.level().getBlockState(this.targetPos);
        CollisionContext context = CollisionContext.of(this.hero);
        VoxelShape shape = state.getCollisionShape(this.hero.level(), this.targetPos, context);

        double seatHeight = 0.0;
        boolean foundSeat = false;

        if (!shape.isEmpty()) {
            List<AABB> aabbs = shape.toAabbs();
            double maxArea = -1.0;
            double bestHeight = 999.0;
            for (AABB box : aabbs) {
                if (box.maxY >= 0.2 && box.maxY <= 0.8) {
                    double area = (box.maxX - box.minX) * (box.maxZ - box.minZ);
                    if (area > maxArea) {
                        maxArea = area;
                        bestHeight = box.maxY;
                        foundSeat = true;
                    } else if (Math.abs(area - maxArea) < 0.001 && box.maxY < bestHeight) {
                        bestHeight = box.maxY;
                    }
                }
            }
            if (foundSeat) seatHeight = bestHeight;
            else seatHeight = shape.max(Direction.Axis.Y);
        }

        double ridingOffset = 0.6;
        if (ridingOffset < 0.1) ridingOffset = 0.6;

        double sitX = this.targetPos.getX() + 0.5;
        double sitY = this.targetPos.getY() + seatHeight - ridingOffset - 0.4;
        double sitZ = this.targetPos.getZ() + 0.5;

        if (state.getBlock() instanceof BedBlock) sitY += 0.1;

        Float targetYRot = null;
        if (state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
            Direction facing = state.getValue(BlockStateProperties.HORIZONTAL_FACING);
            if (state.getBlock() instanceof StairBlock) targetYRot = facing.getOpposite().toYRot();
            else targetYRot = facing.toYRot();
        }

        AreaEffectCloud seat = new AreaEffectCloud(this.hero.level(), sitX, sitY, sitZ);
        seat.setRadius(0.0F);
        seat.setDuration(Integer.MAX_VALUE);
        seat.setWaitTime(0);

        if (targetYRot != null) {
            seat.setYRot(targetYRot);
            seat.setYBodyRot(targetYRot);
            this.hero.setYRot(targetYRot);
            this.hero.setYBodyRot(targetYRot);
            this.hero.setYHeadRot(targetYRot);
        }

        this.hero.level().addFreshEntity(seat);
        this.seatEntity = seat;
        this.hero.startRiding(seat, true);
    }
}