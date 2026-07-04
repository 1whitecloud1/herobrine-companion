package com.whitecloud233.herobrine_companion.entity.ai.goal;

import com.whitecloud233.herobrine_companion.entity.HeroEntity;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

public class HeroFloatingFlyGoal extends Goal {
    private final HeroEntity hero;

    public HeroFloatingFlyGoal(HeroEntity hero) {
        this.hero = hero;
        this.setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        if (this.hero.isBattleModeActive()) return false;
        // [新增] 如果正在交易，禁止自由飞行
        if (this.hero.getTradingPlayer() != null) return false;
        return hero.isFloating() && hero.getNavigation().isDone() && hero.getRandom().nextInt(50) == 0;
    }

    @Override
    public boolean canContinueToUse() {
        if (this.hero.isBattleModeActive()) return false;
        // [新增] 如果正在交易，立即停止
        if (this.hero.getTradingPlayer() != null) return false;
        return hero.isFloating() && hero.getNavigation().isInProgress();
    }
    @Override
    public void start() {
        Vec3 target = findRandomAirPos();
        if (target != null) {
            hero.getNavigation().moveTo(target.x, target.y, target.z, 0.15D);
        }
    }
    
    @Override
    public void tick() {
        // 移动朝向由 MoveControl 管理；这里不再写 LookControl，避免非陪伴自由飞行时抽搐。
    }

    private Vec3 findRandomAirPos() {
        RandomSource rand = hero.getRandom();
        double x = hero.getX() + (rand.nextDouble() - 0.5) * 20.0;
        double y = hero.getY() + (rand.nextDouble() - 0.5) * 10.0;
        double z = hero.getZ() + (rand.nextDouble() - 0.5) * 20.0;
        return new Vec3(x, y, z);
    }
}
