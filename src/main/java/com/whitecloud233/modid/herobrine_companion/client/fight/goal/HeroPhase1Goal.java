package com.whitecloud233.modid.herobrine_companion.client.fight.goal;

import com.whitecloud233.modid.herobrine_companion.entity.HeroEntity;
import com.whitecloud233.modid.herobrine_companion.network.PacketHandler;
import com.whitecloud233.modid.herobrine_companion.network.PaleLightningArcPacket;
import com.whitecloud233.modid.herobrine_companion.network.PaleLightningPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.List;

public class HeroPhase1Goal extends Goal {

    private final HeroEntity hero;
    private LivingEntity target;
    private int phaseTicks;
    private double targetHoverY = 107.0;

    private final List<PendingStrike> pendingStrikes = new ArrayList<>();

    private static class PendingStrike {
        final Vec3 pos;
        int ticksLeft;

        PendingStrike(Vec3 pos, int ticksLeft) {
            this.pos = pos;
            this.ticksLeft = ticksLeft;
        }
    }

    public HeroPhase1Goal(HeroEntity hero) {
        this.hero = hero;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
    }

    @Override
    public boolean canUse() {
        return this.hero.getEntityData().get(HeroEntity.IS_CHALLENGE_ACTIVE)
                || this.hero.getPersistentData().getBoolean("IsChallengeActive");
    }

    @Override
    public boolean canContinueToUse() {
        return canUse();
    }

    @Override
    public void start() {
        this.phaseTicks = this.hero.getPersistentData().getInt("ChallengePhaseTicks");
    }

    @Override
    public void tick() {
        // 【核心修复 1】：将雷电的倒计时和触发提取到最前面！
        // 绝对不能放在 return 的后面，否则 Boss 一卡顿，服务端的雷就会延误，导致和客户端圆圈脱节！
        if (this.hero.level() instanceof ServerLevel serverLevel) {
            Iterator<PendingStrike> iterator = this.pendingStrikes.iterator();
            while (iterator.hasNext()) {
                PendingStrike strike = iterator.next();
                strike.ticksLeft--;
                if (strike.ticksLeft <= 0) {
                    dealLightningDamage(serverLevel, strike.pos);
                    iterator.remove();
                }
            }
        }

        // 目标寻找与重连逻辑
        if (this.target == null || !this.target.isAlive() || this.target.isRemoved()) {
            Player nearestPlayer = this.hero.level().getNearestPlayer(this.hero, 100.0);
            if (nearestPlayer != null && nearestPlayer.getPersistentData().getBoolean("IsChallengeActive")) {
                this.target = nearestPlayer;
            } else {
                hoverIdle();
                return; // 以前这里的 return 会卡死雷电，现在提上去了就安全了！
            }
        }

        // 玩家在线，时间轴继续流动
        this.phaseTicks++;
        this.hero.getPersistentData().putInt("ChallengePhaseTicks", this.phaseTicks);
        this.hero.getEntityData().set(HeroEntity.CHALLENGE_TICKS, this.phaseTicks);

        this.hero.getLookControl().setLookAt(this.target, 30.0F, 30.0F);

        // 悬浮移动逻辑
        double currentY = this.hero.getY();
        double dy = 0;
        if (currentY < this.targetHoverY) {
            dy = 0.08;
        } else if (currentY > this.targetHoverY + 0.2) {
            dy = -0.04;
        } else {
            dy = Math.sin(this.phaseTicks * 0.05) * 0.02;
        }
        this.hero.setDeltaMovement(0, dy, 0);

        // 技能释放逻辑
        if (this.hero.level() instanceof ServerLevel serverLevel) {
            if (this.phaseTicks > 30) {
                if (this.phaseTicks % 8 == 0) {
                    int count = 1 + serverLevel.getRandom().nextInt(1);
                    for (int i = 0; i < count; i++) castPaleLightningPillar();
                }
            }
            if (this.phaseTicks > 30 && this.phaseTicks % 3 == 0) castIndependentLightningWeb();
        }
    }

    private void hoverIdle() {
        double currentY = this.hero.getY();
        double dy = 0;
        if (currentY < this.targetHoverY) dy = 0.08;
        else if (currentY > this.targetHoverY + 0.2) dy = -0.04;
        this.hero.setDeltaMovement(0, dy, 0);
    }

    private void castPaleLightningPillar() {
        if (!(this.hero.level() instanceof ServerLevel serverLevel)) return;

        Vec3 center = this.target != null ? this.target.position() : this.hero.position();
        double spreadRadius = 15.0;
        double angle = serverLevel.random.nextDouble() * Math.PI * 2;
        double distance = Math.sqrt(serverLevel.random.nextDouble()) * spreadRadius;

        double targetX = center.x + Math.cos(angle) * distance;
        double targetZ = center.z + Math.sin(angle) * distance;

        // 动态获取地表真实高度，避免虚空判定
        double groundY = serverLevel.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, (int)targetX, (int)targetZ);
        if (groundY < serverLevel.getMinBuildHeight()) groundY = center.y;

        PaleLightningPacket packet = new PaleLightningPacket(targetX, groundY, targetZ, 6.0f);
        PacketHandler.sendToTracking(packet, this.hero);

        Vec3 groundPos = new Vec3(targetX, groundY, targetZ);
        this.pendingStrikes.add(new PendingStrike(groundPos, 17));
    }

    private void dealLightningDamage(ServerLevel level, Vec3 strikePos) {
        double radius = 3.0;

        // 粗筛：用一个足够深的正方形把附近的玩家抓出来，防止因为跳跃或地形漏掉
        AABB searchBox = new AABB(
                strikePos.x - radius - 1.0, strikePos.y - 10.0, strikePos.z - radius - 1.0,
                strikePos.x + radius + 1.0, strikePos.y + 20.0, strikePos.z + radius + 1.0
        );

        // 中心锚点火焰（这次去掉了散布，让你一眼看出真正的伤害原点在哪）
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.FLAME,
                strikePos.x, strikePos.y + 0.1, strikePos.z,
                15, 0.0, 0.0, 0.0, 0.01);

        List<LivingEntity> targets = level.getEntitiesOfClass(LivingEntity.class, searchBox);

        float damageMultiplier = this.hero.getPersistentData().contains("ChallengeDamageMultiplier") ?
                this.hero.getPersistentData().getFloat("ChallengeDamageMultiplier") : 1.0f;
        float finalDamage = 10.0f * damageMultiplier;

        for (LivingEntity entity : targets) {
            if (entity == this.hero) continue;

            // 【核心修复 2：彻底绑定圆圈面积】：将正方形筛查转为绝对的圆形判定！
            double dx = entity.getX() - strikePos.x;
            double dz = entity.getZ() - strikePos.z;
            // 勾股定理算真实距离的平方
            double distanceSq = dx * dx + dz * dz;

            // 如果距离超过了 3.0 的半径，说明在圆圈外，不管是不是在方形格子里都免伤！
            if (distanceSq > (radius * radius)) {
                continue;
            }

            // 确认在圆圈内，劈！
            DamageSource source = level.damageSources().magic();
            entity.hurt(source, finalDamage);

            Vec3 knockbackDir = entity.position().subtract(strikePos).normalize();
            entity.push(knockbackDir.x * 0.5, 0.4, knockbackDir.z * 0.5);
        }
    }

    private void castIndependentLightningWeb() {
        if (!(this.hero.level() instanceof ServerLevel serverLevel)) return;
        double handX = this.hero.getX() - Math.sin(Math.toRadians(this.hero.yBodyRot + 45)) * 0.6;
        double handY = this.hero.getY() + 1.6;
        double handZ = this.hero.getZ() + Math.cos(Math.toRadians(this.hero.yBodyRot + 45)) * 0.6;
        Vec3 handPos = new Vec3(handX, handY, handZ);

        int webNodes = 5;
        for (int i = 0; i < webNodes; i++) {
            double timeAngle = (this.phaseTicks + i * (360.0 / webNodes)) * Math.PI / 180.0;
            double radius = 1.5 + Math.sin(this.phaseTicks * 0.1) * 0.5;

            double endX = handPos.x + Math.cos(timeAngle) * radius;
            double endY = handPos.y + Math.sin(timeAngle * 2) * radius;
            double endZ = handPos.z + Math.sin(timeAngle) * radius;
            Vec3 endPos = new Vec3(endX, endY, endZ);

            PaleLightningArcPacket packet = new PaleLightningArcPacket(handPos, endPos);
            PacketHandler.sendToTracking(packet, this.hero);
        }
    }

    @Override
    public void stop() {}
}