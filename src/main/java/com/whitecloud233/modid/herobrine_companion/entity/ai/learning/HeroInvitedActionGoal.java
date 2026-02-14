package com.whitecloud233.modid.herobrine_companion.entity.ai.learning;

import com.whitecloud233.modid.herobrine_companion.entity.HeroEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
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

    public HeroInvitedActionGoal(HeroEntity hero) {
        this.hero = hero;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        BlockPos pos = this.hero.getInvitedPos();
        if (pos == null) return false;
        // [深度学习] 审判者状态下，有概率拒绝邀请
        SimpleNeuralNetwork.MindState state = this.hero.getHeroBrain().getState();
        if (state == SimpleNeuralNetwork.MindState.JUDGE && this.hero.getRandom().nextFloat() < 0.7f) {
            // 拒绝邀请
            this.hero.setInvitedPos(null);
            this.hero.setInvitedAction(0);

            // 播放拒绝音效
            this.hero.playSound(SoundEvents.VILLAGER_NO, 1.0f, 0.5f);

            // 发送拒绝消息
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
        // [关键修复] 实时检查 Hero 的邀请状态
        // 如果外部取消了邀请 (InvitedPos 变为空)，则停止 Goal
        BlockPos currentInvitedPos = this.hero.getInvitedPos();
        if (currentInvitedPos == null || !currentInvitedPos.equals(this.targetPos)) {
            return false;
        }
        
        // 如果是休息 (Action 2) 或 守卫 (Action 3)，无限持续，直到被外部打断
        if (this.actionType == 2 || this.actionType == 3) {
            return true;
        }
        
        // 其他动作 (如查看) 保持时间限制
        return this.timer < 400; 
    }

    @Override
    public void start() {
        this.timer = 0;
        this.navigationTicks = 0;
        this.hasArrived = false;
        this.seatEntity = null;
        this.lastPos = this.hero.position();
        
        this.hero.getNavigation().stop();
        this.hero.setNoGravity(true);
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
        // 注意：这里不需要再 setInvitedPos(null)，因为通常是外部置空导致 stop 触发
        // 但如果是 timer 到期导致的 stop，我们需要清理状态
        if (this.hero.getInvitedPos() != null) {
            this.hero.setInvitedPos(null);
            this.hero.setInvitedAction(0);
        }
        
        this.hero.setNoGravity(false);
    }

    @Override
    public void tick() {
        if (this.targetPos == null) return;

        Vec3 targetVec = Vec3.atCenterOf(this.targetPos);
        double distSqr = this.hero.distanceToSqr(targetVec);
        
        if (!this.hasArrived) {
            this.navigationTicks++;
            
            if (this.navigationTicks % 20 == 0) {
                double moveDist = this.hero.position().distanceToSqr(this.lastPos);
                this.lastPos = this.hero.position();
                if (moveDist < 0.01D && distSqr > 16.0D) {
                    teleportToTarget();
                    return;
                }
            }
            
            if (this.navigationTicks > 100 && distSqr > 9.0D) {
                teleportToTarget();
                return;
            }

            // 到达判定
            if (distSqr < 4.0D) { // 2格内
                this.hasArrived = true;
                this.hero.setDeltaMovement(Vec3.ZERO);
                // 到达后恢复重力，防止飘在空中
                this.hero.setNoGravity(false);
                performAction();
            } else {
                // [核心修改] 自定义飞行移动逻辑 (参考 GlitchEchoEntity)
                moveTowardsTarget(targetVec);
            }
        } else {
            // 到达后的逻辑 (保持不变)
            this.timer++;

            // 确保到达后不会飞起来
            if (!this.hero.isNoGravity() && this.hero.isNoGravity()) {
                this.hero.setNoGravity(false);
            }


            if (this.actionType == 3) { // 守卫
                Vec3 away = this.hero.position().add(this.hero.position().subtract(targetVec));
                this.hero.getLookControl().setLookAt(away);
            } else if (this.actionType != 2) { 
                this.hero.getLookControl().setLookAt(targetVec);
            }
            
            if (this.actionType == 1 && this.timer % 10 == 0) { 
                this.hero.level().addParticle(ParticleTypes.ENCHANT, 
                    this.targetPos.getX() + 0.5, this.targetPos.getY() + 1.0, this.targetPos.getZ() + 0.5, 
                    0, 0, 0);
            }
            
            if (this.actionType == 2 && this.timer % 20 == 0) {
                this.hero.level().addParticle(ParticleTypes.NOTE, 
                    this.hero.getX(), this.hero.getY() + 2.2, this.hero.getZ(), 
                    0, 0, 0);
            }
        }
    }
    
    private void moveTowardsTarget(Vec3 targetVec) {
        Vec3 toTarget = targetVec.subtract(this.hero.position());
        double dist = toTarget.length();
        
        double speed = 0.4; 
        Vec3 desiredMove = toTarget.normalize().scale(speed);

        Vec3 avoidance = calculateAvoidance(desiredMove, speed);
        
        double targetY = targetVec.y;
        if (dist > 3.0) {
            targetY += 1.0; 
        }
        double verticalForce = (targetY - this.hero.getY()) * 0.1;
        verticalForce = Mth.clamp(verticalForce, -0.2, 0.2);

        Vec3 finalMove = desiredMove.scale(0.7).add(avoidance.scale(0.3));
        
        this.hero.setDeltaMovement(finalMove.x, verticalForce, finalMove.z);
        
        double angle = Mth.atan2(finalMove.z, finalMove.x) * (180F / Math.PI) - 90F;
        this.hero.setYRot(rotlerp(this.hero.getYRot(), (float) angle, 20.0F));
        this.hero.yBodyRot = this.hero.getYRot();
    }
    
    private Vec3 calculateAvoidance(Vec3 currentVelocity, double speed) {
        Vec3 force = Vec3.ZERO;
        double checkDist = 1.5; 
        
        Vec3 forward = currentVelocity.normalize();
        if (isBlocked(this.hero.position().add(0, 0.5, 0), forward, checkDist)) {
            force = force.add(forward.scale(-1.0));
        }
        
        Vec3 left = forward.yRot((float)Math.toRadians(45));
        if (isBlocked(this.hero.position().add(0, 0.5, 0), left, checkDist)) {
            force = force.add(left.scale(-0.8));
        }

        Vec3 right = forward.yRot((float)Math.toRadians(-45));
        if (isBlocked(this.hero.position().add(0, 0.5, 0), right, checkDist)) {
            force = force.add(right.scale(-0.8));
        }
        
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

    private void teleportToTarget() {
        this.hero.teleportTo(this.targetPos.getX() + 0.5, this.targetPos.getY(), this.targetPos.getZ() + 0.5);
        this.hero.level().playSound(null, this.hero.blockPosition(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.HOSTILE, 1.0F, 1.0F);
        this.hasArrived = true;
        this.hero.setDeltaMovement(Vec3.ZERO);
        // 传送到达后也恢复重力
        this.hero.setNoGravity(false);
        performAction();
    }

    private void performAction() {
        // [深度学习] 行为反馈
        // 玩家主动邀请 Herobrine 做事，这是一种合作行为
        // 增加创造值 (Creativity) 和 探索值 (Exploration)，从而提高尊重 (Respect)
        UUID ownerUUID = this.hero.getOwnerUUID();
        if (ownerUUID != null) {
            this.hero.getHeroBrain().inputCreativity(ownerUUID, 0.1f);
            this.hero.getHeroBrain().inputExploration(ownerUUID, 0.05f);

            Player owner = this.hero.level().getPlayerByUUID(ownerUUID);
            if (owner instanceof ServerPlayer serverPlayer) {
                if (this.actionType == 1) { // Inspect
                    HeroDialogueHandler.onInspectBlock(this.hero, serverPlayer, this.hero.level().getBlockState(this.targetPos));
                    // 查看方块增加 Meta 感知
                    this.hero.getHeroBrain().inputMeta(ownerUUID, 0.05f);
                } else if (this.actionType == 2) { // Rest
                    // 休息增加怀旧值，让他平静
                    this.hero.getHeroBrain().inputNostalgia(ownerUUID, 0.1f);
                } else if (this.actionType == 3) { // Guard
                    // 守卫增加暴力值 (备战)，但也增加尊重 (信任)
                    this.hero.getHeroBrain().inputViolence(ownerUUID, 0.05f);
                    this.hero.getHeroBrain().inputCreativity(ownerUUID, 0.05f);
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
        VoxelShape shape = state.getCollisionShape(this.hero.level(), this.targetPos);
        
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
                    } 
                    else if (Math.abs(area - maxArea) < 0.001 && box.maxY < bestHeight) {
                        bestHeight = box.maxY;
                    }
                }
            }
            
            if (foundSeat) {
                seatHeight = bestHeight;
            } else {
                seatHeight = shape.max(Direction.Axis.Y);
            }
        }
        
        double ridingOffset = this.hero.getMyRidingOffset(); 
        if (ridingOffset < 0.1) ridingOffset = 0.6;

        double sitX = this.targetPos.getX() + 0.5;
        double sitY = this.targetPos.getY() + seatHeight - ridingOffset - 0.4;
        double sitZ = this.targetPos.getZ() + 0.5;
        
        if (state.getBlock() instanceof BedBlock) {
            sitY += 0.1; 
        }

        Float targetYRot = null;
        if (state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
            Direction facing = state.getValue(BlockStateProperties.HORIZONTAL_FACING);
            
            if (state.getBlock() instanceof StairBlock) {
                targetYRot = facing.getOpposite().toYRot();
            } else if (state.getBlock() instanceof BedBlock) {
                targetYRot = facing.toYRot(); 
            } else {
                targetYRot = facing.toYRot();
            }
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