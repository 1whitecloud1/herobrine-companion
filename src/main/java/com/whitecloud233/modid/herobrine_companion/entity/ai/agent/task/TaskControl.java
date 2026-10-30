package com.whitecloud233.modid.herobrine_companion.entity.ai.agent.task;

import com.whitecloud233.modid.herobrine_companion.entity.HeroEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

/**
 * 任务运行时持有的<b>移动控制权凭据</b>（交接阶段 B）。
 *
 * <p><b>为什么需要它</b>：任务以前直接 {@code hero.getNavigation().moveTo(...)}，而
 * 跟随 / 送礼 / 巡视等 Goal 同时也在写同一个导航，两边互相覆盖——表现是"走两步被拽回去"。
 * Minecraft 自带的仲裁机制是 GoalSelector 的 MOVE/LOOK 互斥，所以任务不再自己写导航，
 * 而是把目标登记在这里；由接入 GoalSelector 的
 * {@link com.whitecloud233.modid.herobrine_companion.entity.ai.goal.HeroTaskControlGoal}
 * 取得 MOVE/LOOK 后按这份目标推进。谁能写导航由游戏本体决定，不由两套循环商量。</p>
 *
 * <p><b>单一职责</b>：只保存"当前任务想去哪、要多快、到没到"，不做寻路调度、不做任务状态机。</p>
 */
public final class TaskControl {

    /** 控制权持有者（任务 ID / 所属长目标），用于"只有拥有控制权的任务能改导航"。 */
    private String holder = "";

    private BlockPos targetPos;
    private Vec3 targetVec;
    private double speed = 1.0D;
    private double arriveDistance = 2.5D;
    /** 是否允许跟随一个会移动的实体目标（"回到我身边"必须实时解析玩家位置）。 */
    private boolean followEntity;
    private java.util.UUID targetEntityUuid;
    private long acquiredTick = -1L;
    private long lastProgressTick = -1L;
    private double lastDistanceSqr = Double.MAX_VALUE;
    /** 已经到达过一次（移动类任务据此完成，不必自己重复判距离）。 */
    private boolean arrived;

    public String holder() {
        return holder;
    }

    public boolean isHeld() {
        return !holder.isEmpty();
    }

    public boolean isHeldBy(String taskHolder) {
        return isHeld() && holder.equals(taskHolder == null ? "" : taskHolder);
    }

    public BlockPos targetPos() {
        return targetPos;
    }

    public Vec3 targetVec() {
        return targetVec;
    }

    public double speed() {
        return speed;
    }

    public double arriveDistance() {
        return arriveDistance;
    }

    public boolean followEntity() {
        return followEntity;
    }

    public java.util.UUID targetEntityUuid() {
        return targetEntityUuid;
    }

    public long acquiredTick() {
        return acquiredTick;
    }

    public boolean arrived() {
        return arrived;
    }

    public void setArrived(boolean arrived) {
        this.arrived = arrived;
    }

    /** 无进展判定：距上次"更近了"过去多少 tick。 */
    public long ticksSinceProgress(long gameTime) {
        return lastProgressTick < 0L ? 0L : gameTime - lastProgressTick;
    }

    /**
     * 申请控制权（同一持有者可重复调用以更新目标）。
     *
     * @return 是否取得（被其他持有者占用时返回 false，调用方必须放弃或等待，不能抢占）
     */
    public boolean acquire(String taskHolder, HeroEntity hero, long gameTime) {
        String safe = taskHolder == null ? "" : taskHolder;
        if (isHeld() && !holder.equals(safe)) {
            return false;
        }
        holder = safe;
        acquiredTick = gameTime;
        lastProgressTick = gameTime;
        lastDistanceSqr = Double.MAX_VALUE;
        // 同一持有者重复申请（任务重试 / 更新目标）也要把"已到达"清掉，
        // 否则上一轮的 arrived 会让新目标立刻判完成。
        arrived = false;
        if (hero != null) {
            hero.setTaskMovementActive(true);
        }
        return true;
    }

    /** 设定固定方块目标。 */
    public void moveTo(BlockPos pos, double speed, double arriveDistance) {
        this.targetPos = pos;
        this.targetVec = pos == null ? null : Vec3.atCenterOf(pos);
        this.speed = speed;
        this.arriveDistance = Math.max(0.5D, arriveDistance);
        this.followEntity = false;
        this.targetEntityUuid = null;
        this.arrived = false;
    }

    /** 设定"跟随某个实体的实时位置"目标（玩家走开时目标跟着更新，不用旧坐标）。 */
    public void follow(java.util.UUID entityUuid, double speed, double arriveDistance) {
        this.targetEntityUuid = entityUuid;
        this.followEntity = true;
        this.targetPos = null;
        this.targetVec = null;
        this.speed = speed;
        this.arriveDistance = Math.max(0.5D, arriveDistance);
        this.arrived = false;
    }

    /** 释放控制权：清目标并通知实体收回任务移动标记。 */
    public void release(HeroEntity hero) {
        holder = "";
        targetPos = null;
        targetVec = null;
        targetEntityUuid = null;
        followEntity = false;
        arrived = false;
        acquiredTick = -1L;
        lastProgressTick = -1L;
        lastDistanceSqr = Double.MAX_VALUE;
        if (hero != null) {
            hero.setTaskMovementActive(false);
            hero.getNavigation().stop();
        }
    }

    /**
     * 重新下发同一个目标（任务重试时用）：不改变持有者，只重置进度采样。
     *
     * <p>重试必须重新置为"未到达"，否则上一轮遗留的 {@code arrived=true}
     * 会让任务立刻判定 DONE，看似成功、实际一步没走。</p>
     */
    public void reopen() {
        this.arrived = false;
        this.lastProgressTick = this.acquiredTick;
        this.lastDistanceSqr = Double.MAX_VALUE;
    }

    /**
     * 记录一次位置采样，维护"最近是否有进展"。
     *
     * @return true 表示本次比历史最近距离更近（有进展）
     */
    public boolean recordDistance(double distanceSqr, long gameTime) {
        if (distanceSqr + 1.0E-4D < lastDistanceSqr) {
            lastDistanceSqr = distanceSqr;
            lastProgressTick = gameTime;
            return true;
        }
        return false;
    }
}
