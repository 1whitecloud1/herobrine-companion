package com.whitecloud233.modid.herobrine_companion.entity.ai.goal;

import com.whitecloud233.modid.herobrine_companion.entity.HeroEntity;
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
        return hero.isFloating() && hero.getNavigation().isDone() && hero.getRandom().nextInt(50) == 0;
    }

    @Override
    public boolean canContinueToUse() {
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
        // [新增] 在自由飞行时，偶尔看看周围，避免一直盯着前方死板
        if (hero.tickCount % 40 == 0 && hero.getRandom().nextBoolean()) {
            // 随机看一个方向，或者看前方
            // 这里不做强制操作，依赖 RandomLookAroundGoal 即可
            // 但为了防止 MoveControl 强制把头扭回来，我们可以手动设置一下 LookControl
            // 让它看向移动方向，保持自然
            Vec3 delta = hero.getDeltaMovement();
            if (delta.lengthSqr() > 0.01) {
                hero.getLookControl().setLookAt(hero.getX() + delta.x * 5, hero.getEyeY() + delta.y * 5, hero.getZ() + delta.z * 5, 10.0F, 10.0F);
            }
        }
    }
    private Vec3 findRandomAirPos() {
        RandomSource rand = hero.getRandom();
        double x = hero.getX() + (rand.nextDouble() - 0.5) * 20.0;
        double y = hero.getY() + (rand.nextDouble() - 0.5) * 10.0;
        double z = hero.getZ() + (rand.nextDouble() - 0.5) * 20.0;
        
        return new Vec3(x, y, z);
    }
}