package com.whitecloud233.modid.herobrine_companion.entity.ai.goal;

import com.whitecloud233.modid.herobrine_companion.entity.HeroEntity;
import java.util.UUID;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

public class HeroGodlyCompanionGoal extends Goal {
    private final HeroEntity hero;
    private final double speedModifier;
    private Player owner;

    // 参数配置
    private static final double HOVER_HEIGHT_AIR = 2.0D;
    private static final double FOLLOW_DISTANCE = 3.5D;
    private static final double LANDING_THRESHOLD = 0.8D;

    // [新增] 战斗超时时间
    private static final int COMBAT_TIMEOUT = 100;

    private int teleportCooldown;

    // 柔性跟随参数
    private float randomOffset;
    private float currentOrbitAngle;
    private float targetOrbitAngle;

    private int changePositionTimer;

    public HeroGodlyCompanionGoal(HeroEntity hero, double speed) {
        this.hero = hero;
        this.speedModifier = speed;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    /**
     * [新增] 统一的战斗状态判定
     * 与 ObserveAndRescueGoal 和 TeleportGoal 保持绝对一致，防止 AI 撕扯
     */
    private boolean isInCombat(Player player) {
        int currentTick = player.tickCount;
        int lastHurtByMobTime = player.getLastHurtByMobTimestamp();
        int lastHurtMobTime = player.getLastHurtMobTimestamp();

        boolean recentlyHurt = lastHurtByMobTime > 0 && (currentTick - lastHurtByMobTime) < COMBAT_TIMEOUT;
        boolean recentlyAttacked = lastHurtMobTime > 0 && (currentTick - lastHurtMobTime) < COMBAT_TIMEOUT;

        if (!recentlyHurt && !recentlyAttacked) return false;

        LivingEntity attacker = player.getLastHurtByMob();
        LivingEntity target = player.getLastHurtMob();

        boolean hasValidAttacker = attacker != null && attacker.isAlive() && attacker.distanceToSqr(player) < 900.0D;
        boolean hasValidTarget = target != null && target.isAlive() && target.distanceToSqr(player) < 900.0D;

        return hasValidAttacker || hasValidTarget;
    }

    @Override
    public boolean canUse() {
        if (!this.hero.isCompanionMode()) return false;
        if (this.hero.getTradingPlayer() != null) return false;

        java.util.UUID ownerId = this.hero.getOwnerUUID();
        if (ownerId == null) return false;

        net.minecraft.world.entity.player.Player player = this.hero.level().getPlayerByUUID(ownerId);
        if (player == null || !player.isAlive()) return false;

        this.owner = player;
        if (isInCombat(this.owner)) return false;

        // 【修复】：只要玩家离开 2.5 格以上就开始跟随，缩小启动延迟
        return this.hero.distanceToSqr(player) > (2.5D * 2.5D);
    }

    @Override
    public boolean canContinueToUse() {
        if (!this.hero.isCompanionMode()) return false;
        if (this.hero.getTradingPlayer() != null) return false;
        if (this.owner == null || !this.owner.isAlive()) return false;
        if (isInCombat(this.owner)) return false;

        // 【关键修复】：将中断距离缩减到远小于 FOLLOW_DISTANCE (3.5) 的值，例如 1.5 格
        // 这样他在 3.5 格的轨道上巡航时，绝对不会触发强行中断和急刹车
        return this.hero.distanceToSqr(this.owner) > (1.5D * 1.5D);
    }

    @Override
    public void start() {
        // 【修复点 2】：彻底删除 this.hero.setOwnerUUID(this.owner.getUUID());
        // 绝对不能在行为 AI 中篡改主人的主权！

        // [核心优化] 强制进入飞行模式，抵抗卡顿
        this.hero.setFloating(true);
        this.hero.setNoGravity(true);
        this.hero.getNavigation().stop(); // 停止任何地面寻路

        pickNewRandomPosition();
        this.targetOrbitAngle = this.owner.yBodyRot;
        this.currentOrbitAngle = this.targetOrbitAngle;
    }

    @Override
    public void stop() {
        this.owner = null;
        this.hero.setDeltaMovement(Vec3.ZERO);
    }

    @Override
    public void tick() {
        // === 1. 头部与身体转向修复 ===
        double d0 = this.owner.getX() - this.hero.getX();
        double d1 = this.owner.getZ() - this.hero.getZ();
        double distSqr = d0 * d0 + d1 * d1;

        if (distSqr > 0.1D) {
            float targetYRot = -((float)Mth.atan2(d0, d1)) * (180F / (float)Math.PI);
            this.hero.yBodyRot = rotlerp(this.hero.yBodyRot, targetYRot, 10.0F);
            this.hero.setYRot(this.hero.yBodyRot);
        }

        // 关键干扰项：由于此处强制注视主人，Hero 的 LookAngle 并不代表前进方向
        this.hero.getLookControl().setLookAt(this.owner, 30.0F, 40.0F);

        // 2. 兜底逻辑：距离过远传送
        double distToOwnerSqr = this.hero.distanceToSqr(this.owner);
        if (distToOwnerSqr > 200.0D) {
            if (this.teleportCooldown-- <= 0) {
                teleportNearOwner();
                this.teleportCooldown = 20;
            }
            return;
        }

        boolean isOwnerMoving = this.owner.getDeltaMovement().horizontalDistanceSqr() > 0.001;
        if (--this.changePositionTimer <= 0) {
            pickNewRandomPosition();
        }

        // 3. 计算轨道与目标位置
        updateTargetAngle(isOwnerMoving);
        this.currentOrbitAngle = rotlerp(this.currentOrbitAngle, this.targetOrbitAngle, 1.5F);
        Vec3 targetPos = calculateTargetPos(this.currentOrbitAngle);

        // 4. 强制飞行锁定
        if (!this.hero.isFloating()) this.hero.setFloating(true);
        if (!this.hero.isNoGravity()) this.hero.setNoGravity(true);

        // === 5. 移动速度优化 (解决由于频繁减速导致的粘滞感) ===
        double dx = this.hero.getX() - targetPos.x;
        double dz = this.hero.getZ() - targetPos.z;
        double distHorizontalSqr = dx * dx + dz * dz;
        double heightDiff = this.hero.getY() - targetPos.y;

        double speed = this.speedModifier;
        if (distHorizontalSqr > 25.0D) speed *= 1.5D;

        // 核心修复：只有极其接近(0.25格内)时才进入停靠减速，防止在跟随途中产生“阻力”
        boolean closeEnoughHorizontally = distHorizontalSqr < 0.0625D;
        boolean closeEnoughVertically = Math.abs(heightDiff) < 0.25D;

        if (closeEnoughHorizontally && closeEnoughVertically) {
            this.hero.setDeltaMovement(this.hero.getDeltaMovement().scale(0.5));
        } else {
            // 只要没到目的地，就利用 MoveControl 的平滑插值全力移动
            this.hero.getMoveControl().setWantedPosition(targetPos.x, targetPos.y, targetPos.z, speed);
        }

        // === 6. [核心修复] 物理强行开门逻辑 (使用移动向量) ===
        // 当 Hero 发生水平碰撞，或者正在显著朝某处移动时进行探测
        if (this.hero.horizontalCollision || distHorizontalSqr > 0.1D) {
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

    private void updateTargetAngle(boolean isMoving) {
        if (isMoving) {
            Vec3 velocity = this.owner.getDeltaMovement();
            float moveYaw = (float)(Mth.atan2(velocity.z, velocity.x) * (180.0D / Math.PI)) - 90.0F;
            this.targetOrbitAngle = moveYaw + this.randomOffset;
        }
    }

    private Vec3 calculateTargetPos(float angleDegrees) {
        double targetAngleRad = Math.toRadians(angleDegrees);

        double tx = this.owner.getX() + Math.sin(-targetAngleRad) * FOLLOW_DISTANCE;
        double tz = this.owner.getZ() + Math.cos(-targetAngleRad) * FOLLOW_DISTANCE;

        // [核心优化] 目标Y值计算简化
        // 始终以玩家为基准，不再尝试探测地面，因为我们是飞行的
        double targetY;
        if (this.owner.getAbilities().flying || !this.owner.onGround()) {
            targetY = this.owner.getY() + HOVER_HEIGHT_AIR;
        } else {
            // 即使玩家在地面，我们也只比他高一点点，做出“贴地飞行”的效果
            targetY = this.owner.getY() + 0.5;
        }
        return new Vec3(tx, targetY, tz);
    }

    private void pickNewRandomPosition() {
        this.randomOffset = this.hero.getRandom().nextFloat() * 360.0F;
        this.changePositionTimer = 400 + this.hero.getRandom().nextInt(200);
    }

    private void teleportNearOwner() {
        this.currentOrbitAngle = this.owner.yBodyRot + this.randomOffset;
        this.targetOrbitAngle = this.currentOrbitAngle;
        Vec3 target = calculateTargetPos(this.currentOrbitAngle);

        // 【修改】废弃 teleportTo，改用 moveTo 强行降临
        this.hero.moveTo(target.x, target.y, target.z, this.hero.getYRot(), this.hero.getXRot());
        this.hero.setDeltaMovement(Vec3.ZERO);
    }

    protected float rotlerp(float pStart, float pEnd, float pMaxIncrease) {
        float f = Mth.wrapDegrees(pEnd - pStart);
        if (f > pMaxIncrease) f = pMaxIncrease;
        if (f < -pMaxIncrease) f = -pMaxIncrease;
        return pStart + f;
    }
}
