package com.whitecloud233.herobrine_companion.fight;

import com.whitecloud233.herobrine_companion.entity.HeroEntity;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

public class HeroAfterimage {
    private final Vec3 position;
    private final float yRot;
    private final int maxTickCount;
    private float tickCount;

    public static HeroAfterimage of(HeroEntity entity, int maxTickCount) {
        return new HeroAfterimage(entity.position(), entity.getYRot(), maxTickCount);
    }

    public HeroAfterimage(Vec3 position, float yRot, int maxTickCount) {
        this(position, yRot, maxTickCount, 0.0F);
    }

    public HeroAfterimage(Vec3 position, float yRot, int maxTickCount, float tickCount) {
        this.position = position;
        this.yRot = yRot;
        this.maxTickCount = Math.max(1, maxTickCount);
        this.tickCount = tickCount;
    }

    public boolean tick() {
        this.tickCount += 1.0F;
        return this.tickCount >= this.maxTickCount;
    }

    public Vec3 getPosition() {
        return this.position;
    }

    public float getYRot() {
        return this.yRot;
    }

    public int getMaxTickCount() {
        return this.maxTickCount;
    }

    public float getTickCount() {
        return this.tickCount;
    }

    public float getAlpha01(float partialTick) {
        return Mth.clamp(1.0F - (this.tickCount + partialTick) / this.maxTickCount, 0.0F, 1.0F);
    }

    public int getAlpha(float partialTick) {
        return Mth.ceil(this.getAlpha01(partialTick) * 255.0F);
    }
}
