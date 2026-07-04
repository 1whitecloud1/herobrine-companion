package com.whitecloud233.herobrine_companion.entity.ai.goal;

import com.whitecloud233.herobrine_companion.entity.HeroEntity;
import com.whitecloud233.herobrine_companion.entity.ai.HeroMoveControl;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;
import java.util.UUID;

public class HeroGodlyCompanionGoal extends Goal {
    private final HeroEntity hero;
    private final double speedModifier;
    private Player owner;

    // 参数配置
    private static final double HOVER_HEIGHT_AIR = 2.0D;
    private static final double MIN_FOLLOW_DISTANCE = 6.8D;
    private static final double MAX_FOLLOW_DISTANCE = 9.5D;
    private static final double MIN_SIDE_OFFSET = 0.8D;
    private static final double MAX_SIDE_OFFSET = 3.6D;
    private static final double COMFORT_RADIUS = 1.45D;
    public static final double STAY_STILL_RADIUS = 6.0D;
    private static final double FOLLOW_START_RADIUS = 7.5D;
    private static final double LANDING_THRESHOLD = 0.8D;
    private static final double HARD_TELEPORT_DISTANCE_SQR = 625.0D;

    // [新增] 战斗超时时间
    private static final int COMBAT_TIMEOUT = 100;
    private static final float HEAD_RETURN_SPEED = 4.0F;
    private static final float BODY_TURN_SPEED = 14.0F;

    private int teleportCooldown;

    private float stableFollowYaw;
    private double followSideSign = 1.0D;
    private double desiredFollowDistance = MIN_FOLLOW_DISTANCE;
    private double desiredSideOffset = 1.2D;
    private double desiredHeightOffset = 0.0D;
    private int nextPersonalSpaceTick;

    public HeroGodlyCompanionGoal(HeroEntity hero, double speed) {
        this.hero = hero;
        this.speedModifier = speed;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    public static boolean isOwnerWithinStayStillRadius(HeroEntity hero) {
        if (!hero.isCompanionMode() || hero.getOwnerUUID() == null) {
            return false;
        }

        Player owner = hero.level().getPlayerByUUID(hero.getOwnerUUID());
        return owner != null
                && owner.isAlive()
                && horizontalDistanceToOwnerSqr(hero, owner) <= (STAY_STILL_RADIUS * STAY_STILL_RADIUS);
    }

    private static boolean isOwnerBeyondFollowStartRadius(HeroEntity hero, Player owner) {
        return horizontalDistanceToOwnerSqr(hero, owner) > (FOLLOW_START_RADIUS * FOLLOW_START_RADIUS);
    }

    public static double horizontalDistanceToOwnerSqr(HeroEntity hero, Player owner) {
        double dx = hero.getX() - owner.getX();
        double dz = hero.getZ() - owner.getZ();
        return dx * dx + dz * dz;
    }

    /**
     * [新增] 统一的战斗状态判定
     * 与 ObserveAndRescueGoal 和 TeleportGoal 保持绝对一致，防止 AI 撕扯
     */
    /**
     * 【重写】精准判断玩家是否处于战斗状态
     */
    private boolean isInCombat(Player player) {
        // 1. 放宽伤害时间戳判定
        // 只要近期 (5秒内) 造成或受到过伤害，就算战斗状态缓冲期。
        // 【核心修复】：移除了对“攻击者必须还活着”的死板检测，防止秒杀怪物后立刻判定脱战。
        int currentTick = player.tickCount;
        if (player.getLastHurtByMobTimestamp() > 0 && (currentTick - player.getLastHurtByMobTimestamp()) < COMBAT_TIMEOUT) return true;
        if (player.getLastHurtMobTimestamp() > 0 && (currentTick - player.getLastHurtMobTimestamp()) < COMBAT_TIMEOUT) return true;

        // 2. 增加“仇恨感知”（主动预判）
        // 不要等挨打了才算战斗！扫描周围 16 格，只要有怪物把仇恨目标(Target)锁定为你，神明就会立刻察觉并避让。
        net.minecraft.world.phys.AABB searchBox = player.getBoundingBox().inflate(16.0D, 8.0D, 16.0D);
        java.util.List<net.minecraft.world.entity.Mob> threats = player.level().getEntitiesOfClass(
                net.minecraft.world.entity.Mob.class,
                searchBox,
                mob -> mob.getTarget() != null && mob.getTarget().getUUID().equals(player.getUUID())
        );

        return !threats.isEmpty();
    }

    @Override
    public boolean canUse() {
        if (this.hero.isBattleModeActive()) return false;

        if (!this.hero.isCompanionMode()) return false;
        // 如果正在交易，禁止跟随移动
        if (this.hero.getTradingPlayer() != null) return false;

        // 【修复点 1】：严格通过 UUID 获取自己真正的主人，禁止使用 getNearestPlayer 乱认主人
        UUID ownerId = this.hero.getOwnerUUID();
        if (ownerId == null) return false;

        Player player = this.hero.level().getPlayerByUUID(ownerId);
        // 如果主人离线、不在同一维度或死亡，停止跟随
        if (player == null || !player.isAlive()) return false;

        this.owner = player;

        // 【关键修复】如果玩家进入战斗，立刻禁用贴身跟随
        // 将身体的控制权完美移交给 HeroObserveAndRescueGoal
        if (isInCombat(this.owner)) return false;

        return isOwnerBeyondFollowStartRadius(this.hero, player);
    }

    @Override
    public boolean canContinueToUse() {
        if (this.hero.isBattleModeActive()) return false;
        if (!this.hero.isCompanionMode()) return false;
        // 如果正在交易，立即停止跟随
        if (this.hero.getTradingPlayer() != null) return false;
        if (this.owner == null || !this.owner.isAlive()) return false;

        // 【关键修复】如果在日常跟随中突然爆发战斗，立刻打断当前步伐
        if (isInCombat(this.owner)) return false;

        double distSqr = horizontalDistanceToOwnerSqr(this.hero, this.owner);
        return distSqr > (STAY_STILL_RADIUS * STAY_STILL_RADIUS);
    }

    @Override
    public void start() {
        // 【修复点 2】：彻底删除 this.hero.setOwnerUUID(this.owner.getUUID());
        // 绝对不能在行为 AI 中篡改主人的主权！

        // [核心优化] 强制进入飞行模式，抵抗卡顿
        this.hero.setFloating(true);
        this.hero.setNoGravity(true);
        this.hero.noPhysics = true;
        this.hero.getNavigation().stop(); // 停止任何地面寻路
        stopMoveControl();

        initializeFollowAnchor();
        chooseFollowSide();
        pickPersonalSpace(true);
    }

    @Override
    public void stop() {
        this.owner = null;
        this.hero.setDeltaMovement(Vec3.ZERO);
        stopMoveControl();
        if (!this.hero.isCompanionMode() || this.hero.level().noCollision(this.hero, this.hero.getBoundingBox())) {
            this.hero.noPhysics = false;
        }
        syncHeadToBody();
    }

    @Override
    public void tick() {
        this.hero.noPhysics = true;
        stopMoveControl();

        if (isOwnerWithinStayStillRadius(this.hero)) {
            this.hero.getNavigation().stop();
            this.hero.setDeltaMovement(Vec3.ZERO);
            return;
        }

        // 2. 兜底逻辑：距离过远传送
        double distToOwnerSqr = this.hero.distanceToSqr(this.owner);
        boolean ownerMoving = this.owner.getDeltaMovement().horizontalDistanceSqr() > 0.0025D;
        if (this.hero.tickCount >= this.nextPersonalSpaceTick && (ownerMoving || distToOwnerSqr < 49.0D)) {
            pickPersonalSpace(false);
        }

        if (distToOwnerSqr > HARD_TELEPORT_DISTANCE_SQR) {
            if (this.teleportCooldown-- <= 0) {
                teleportNearOwner();
                this.teleportCooldown = 20;
            }
            return;
        }

        // 4. 强制飞行锁定
        if (!this.hero.isFloating()) this.hero.setFloating(true);
        if (!this.hero.isNoGravity()) this.hero.setNoGravity(true);

        Vec3 targetPos = calculateFollowTarget();
        Vec3 toTarget = targetPos.subtract(this.hero.position());
        double distToTarget = toTarget.length();
        boolean comfortable = distToTarget < COMFORT_RADIUS && this.hero.hasLineOfSight(this.owner);
        boolean hoveringInPlace = distToTarget < 0.35D;

        if (comfortable) {
            this.hero.setDeltaMovement(protectCompanionFloor(this.hero.getDeltaMovement().scale(ownerMoving ? 0.55D : 0.25D)));
        } else {
            double ownerSpeed = this.owner.getDeltaMovement().horizontalDistance();
            double maxSpeed = 0.34D + this.speedModifier * 0.20D + Mth.clamp(ownerSpeed, 0.0D, 0.28D);
            if (distToTarget > 8.0D) {
                maxSpeed += 0.28D;
            } else if (distToTarget > 4.0D) {
                maxSpeed += 0.14D;
            }

            double targetSpeed = Math.min(maxSpeed, Math.max(0.04D, (distToTarget - COMFORT_RADIUS * 0.45D) * 0.38D));
            Vec3 desired = toTarget.normalize().scale(targetSpeed);
            Vec3 current = this.hero.getDeltaMovement();
            Vec3 velocity = current.scale(0.25D).add(desired.scale(0.75D));
            this.hero.setDeltaMovement(protectCompanionFloor(velocity));
        }

        if (!comfortable) {
            faceMovementDirection();
        } else if ((hoveringInPlace || comfortable) && distToOwnerSqr <= 36.0D) {
            this.hero.getLookControl().setLookAt(this.owner, 18.0F, 25.0F);
        } else {
            syncHeadToBody();
        }

        // === 6. [核心修复] 物理强行开门逻辑 (使用移动向量) ===
        // 当 Hero 发生水平碰撞，或者正在显著朝某处移动时进行探测
        if (this.hero.horizontalCollision || distToTarget > 0.5D) {
            // 重点：使用当前的移动速度向量（Velocity）来判定前方，而非 LookAngle
            Vec3 moveDir = this.hero.getDeltaMovement().normalize();

            // 如果移动速度极慢（静止），则回退到视线方向作为探测兜底
            if (this.hero.getDeltaMovement().lengthSqr() < 0.001) {
                moveDir = this.hero.getLookAngle();
            }

            // 探测 Hero 移动方向前方 0.8 格的位置
            net.minecraft.core.BlockPos frontPos = net.minecraft.core.BlockPos.containing(
                    this.hero.getX() + moveDir.x * 0.8,
                    this.hero.getY() + 0.1, // 确保探测高度在门的下半扇
                    this.hero.getZ() + moveDir.z * 0.8
            );

            // 循环检查：当前高度及头顶高度（应对门的两部分）
            for (int i = 0; i < 2; i++) {
                net.minecraft.core.BlockPos checkPos = (i == 0) ? frontPos : frontPos.above();
                net.minecraft.world.level.block.state.BlockState state = this.hero.level().getBlockState(checkPos);

                if (state.getBlock() instanceof net.minecraft.world.level.block.DoorBlock door) {
                    // 如果门是关着的，强行“用意念”将其推开
                    if (!state.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.OPEN)) {
                        door.setOpen(this.hero, this.hero.level(), state, checkPos, true);
                    }
                }
            }
        }
    }

    private void faceMovementDirection() {
        Vec3 movement = this.hero.getDeltaMovement();
        if (movement.horizontalDistanceSqr() <= 0.0025D) {
            syncHeadToBody();
            return;
        }

        float targetYRot = -((float) Mth.atan2(movement.x, movement.z)) * (180.0F / (float) Math.PI);
        float newYRot = rotlerp(this.hero.getYRot(), targetYRot, BODY_TURN_SPEED);
        this.hero.setYRot(newYRot);
        this.hero.yBodyRot = newYRot;
        this.hero.setYHeadRot(rotlerp(this.hero.yHeadRot, newYRot, HEAD_RETURN_SPEED));
        this.hero.yHeadRotO = this.hero.yHeadRot;

        if (Math.abs(this.hero.getXRot()) > 1.0F) {
            this.hero.setXRot(rotlerp(this.hero.getXRot(), 0.0F, 5.0F));
        }
    }

    private Vec3 calculateFollowTarget() {
        Vec3 ownerVelocity = this.owner.getDeltaMovement();
        if (ownerVelocity.horizontalDistanceSqr() > 0.0025D) {
            float moveYaw = (float) (Mth.atan2(ownerVelocity.z, ownerVelocity.x) * (180.0D / Math.PI)) - 90.0F;
            this.stableFollowYaw = rotlerp(this.stableFollowYaw, moveYaw, 12.0F);
        }

        Vec3 behind = Vec3.directionFromRotation(0.0F, this.stableFollowYaw + 180.0F).scale(this.desiredFollowDistance);
        Vec3 side = Vec3.directionFromRotation(0.0F, this.stableFollowYaw + 90.0F).scale(this.desiredSideOffset * this.followSideSign);

        double targetY = this.owner.getAbilities().flying || !this.owner.onGround()
                ? this.owner.getY() + HOVER_HEIGHT_AIR
                : this.owner.getY() + 0.5D + this.desiredHeightOffset;

        return new Vec3(this.owner.getX() + behind.x + side.x, targetY, this.owner.getZ() + behind.z + side.z);
    }

    private void initializeFollowAnchor() {
        Vec3 relative = this.hero.position().subtract(this.owner.position());
        if (relative.horizontalDistanceSqr() > 0.25D) {
            float heroDirectionYaw = -((float) Mth.atan2(relative.x, relative.z)) * (180.0F / (float) Math.PI);
            this.stableFollowYaw = Mth.wrapDegrees(heroDirectionYaw - 180.0F);
        } else {
            this.stableFollowYaw = this.owner.yBodyRot;
        }
    }

    private void chooseFollowSide() {
        Vec3 relative = this.hero.position().subtract(this.owner.position());
        Vec3 right = Vec3.directionFromRotation(0.0F, this.stableFollowYaw + 90.0F);
        double dot = relative.x * right.x + relative.z * right.z;
        if (Math.abs(dot) > 0.35D) {
            this.followSideSign = dot >= 0.0D ? 1.0D : -1.0D;
        } else {
            this.followSideSign = this.hero.getRandom().nextBoolean() ? 1.0D : -1.0D;
        }
    }

    private void pickPersonalSpace(boolean immediate) {
        this.desiredFollowDistance = Mth.lerp(this.hero.getRandom().nextDouble(), MIN_FOLLOW_DISTANCE, MAX_FOLLOW_DISTANCE);
        this.desiredSideOffset = Mth.lerp(this.hero.getRandom().nextDouble(), MIN_SIDE_OFFSET, MAX_SIDE_OFFSET);
        this.desiredHeightOffset = Mth.lerp(this.hero.getRandom().nextDouble(), -0.15D, 0.35D);
        if (!immediate && this.hero.getRandom().nextFloat() < 0.18F) {
            this.followSideSign = -this.followSideSign;
        }
        this.nextPersonalSpaceTick = this.hero.tickCount + 120 + this.hero.getRandom().nextInt(180);
    }

    private void teleportNearOwner() {
        Vec3 ownerVelocity = this.owner.getDeltaMovement();
        this.stableFollowYaw = ownerVelocity.horizontalDistanceSqr() > 0.0025D
                ? (float) (Mth.atan2(ownerVelocity.z, ownerVelocity.x) * (180.0D / Math.PI)) - 90.0F
                : this.owner.yBodyRot;
        chooseFollowSide();
        pickPersonalSpace(true);
        Vec3 target = calculateFollowTarget();

        // 【修改】废弃 teleportTo，改用 moveTo 强行降临
        this.hero.moveTo(target.x, target.y, target.z, this.hero.getYRot(), this.hero.getXRot());
        this.hero.setDeltaMovement(Vec3.ZERO);
        stopMoveControl();
        syncHeadToBodyInstant();
    }

    private void stopMoveControl() {
        if (this.hero.getMoveControl() instanceof HeroMoveControl heroMoveControl) {
            heroMoveControl.stopMoving();
        }
    }

    private Vec3 protectCompanionFloor(Vec3 velocity) {
        if (this.hero.getMoveControl() instanceof HeroMoveControl heroMoveControl) {
            return heroMoveControl.protectCompanionFloor(velocity);
        }
        return velocity;
    }

    private void syncHeadToBody() {
        if (this.hero.getLookControl().isLookingAtTarget()) {
            return;
        }

        float bodyYaw = this.hero.getYRot();
        this.hero.setYHeadRot(rotlerp(this.hero.yHeadRot, bodyYaw, HEAD_RETURN_SPEED));
    }

    private void syncHeadToBodyInstant() {
        float bodyYaw = this.hero.getYRot();
        this.hero.setYHeadRot(bodyYaw);
        this.hero.yHeadRotO = bodyYaw;
    }

    protected float rotlerp(float pStart, float pEnd, float pMaxIncrease) {
        float f = Mth.wrapDegrees(pEnd - pStart);
        if (f > pMaxIncrease) f = pMaxIncrease;
        if (f < -pMaxIncrease) f = -pMaxIncrease;
        return pStart + f;
    }
}
