package com.whitecloud233.modid.herobrine_companion.entity.visual;

/** 每个实体独立的客户端播放状态；不保存、不联网，也不依赖客户端类加载。 */
public final class HeroVisualState {
    private static final float OFFER_BLEND_STEP = 0.25F;
    private static final float VARIANT_BLEND_STEP = 0.2F;
    private static final float WALK_CYCLE_TICKS = 40.0F;
    // Bedrock animation.hero.move 每前进 360/38.17 格完成一个步态环。
    private static final float WALK_CYCLE_PER_BLOCK = 38.17F / 360.0F;
    private static final float WALK_MAX_STEP_BLOCKS = 4.0F;

    private boolean setupDone;
    private float floatingAmount, floatingAmountO;
    private float importedWalkTicks, importedWalkTicksO;
    private int idleVariantClip = -1;
    private int idleVariantStartTick;
    private int idleVariantNextAt = 100;
    private int idleVariantLast = -1;
    private boolean idleVariantActive;
    private int gestureEventCursor;
    private int gestureTokenSeen = -1;
    private int gestureStartTick;
    private int gestureClipId;
    private boolean gestureFinished;
    private float offerBlend, offerBlendO;
    private float variantBlend, variantBlendO;

    public boolean isSetupDone() { return setupDone; }
    public void markSetupDone() { setupDone = true; }

    public void tickLocomotion(boolean floating, boolean groundWalking, double movedX, double movedZ) {
        floatingAmountO = floatingAmount;
        floatingAmount = approach(floatingAmount, floating ? 1.0F : 0.0F, 0.05F);
        importedWalkTicksO = importedWalkTicks;
        importedWalkTicks = advanceWalkAnimationTicks(importedWalkTicks, floating, groundWalking, movedX, movedZ);
        if (importedWalkTicks == 0.0F) importedWalkTicksO = 0.0F;
    }

    public static float advanceWalkAnimationTicks(float ticks, boolean floating, boolean groundWalking,
                                                  double movedX, double movedZ) {
        if (floating) return 0.0F;
        double distance = Math.hypot(movedX, movedZ);
        float movedBlocks = Double.isFinite(distance)
                ? (float) Math.min(distance, WALK_MAX_STEP_BLOCKS) : 0.0F;
        // 位移包可能先于走路标记到达；传送纠偏则不能把相位无限推进。
        if (!groundWalking && movedBlocks == 0.0F) return 0.0F;
        return ticks + movedBlocks * WALK_CYCLE_PER_BLOCK * WALK_CYCLE_TICKS;
    }

    public float getFloatingAmount(float partialTick) {
        return lerp(partialTick, floatingAmountO, floatingAmount);
    }

    public float getImportedWalkAnimationTime(float partialTick) {
        return lerp(partialTick, importedWalkTicksO, importedWalkTicks) / 20.0F;
    }

    public int getGestureClipId() { return gestureClipId; }
    public int getGestureStartTick() { return gestureStartTick; }
    public int getGestureEventCursor() { return gestureEventCursor; }
    public int getGestureTokenSeen() { return gestureTokenSeen; }
    public boolean isGestureFinished() { return gestureFinished; }

    public void startGesture(int clipId, int token, int localTick) {
        gestureTokenSeen = token;
        gestureEventCursor = 0;
        gestureClipId = clipId;
        // 服务端与客户端实体的 tickCount 起点不同，播放只能使用本地时钟。
        gestureStartTick = localTick;
        gestureFinished = false;
    }

    public void finishGesture(int eventCount) {
        gestureFinished = true;
        gestureEventCursor = eventCount;
    }

    public void advanceGestureEvent() { gestureEventCursor++; }

    public void clearGesture() {
        gestureEventCursor = 0;
        gestureClipId = 0;
        gestureFinished = false;
    }

    public int getIdleVariantClip() { return idleVariantClip; }
    public int getIdleVariantStartTick() { return idleVariantStartTick; }
    public int getIdleVariantNextAt() { return idleVariantNextAt; }
    public int getIdleVariantLast() { return idleVariantLast; }
    public boolean isIdleVariantActive() { return idleVariantActive; }
    public void scheduleIdleVariant(int nextAt) { idleVariantNextAt = nextAt; }

    public void startIdleVariant(int clip, int localTick) {
        idleVariantClip = clip;
        idleVariantStartTick = localTick;
        idleVariantLast = clip;
        idleVariantActive = true;
    }

    public void finishIdleVariant(int nextAt) {
        // 淡出结束前保留 clip，渲染器仍需采样它。
        idleVariantActive = false;
        idleVariantNextAt = nextAt;
    }

    public void tickOfferBlend(boolean active) {
        offerBlendO = offerBlend;
        offerBlend = approach(offerBlend, active ? 1.0F : 0.0F, OFFER_BLEND_STEP);
    }

    public void tickVariantBlend(boolean active) {
        variantBlendO = variantBlend;
        variantBlend = approach(variantBlend, active ? 1.0F : 0.0F, VARIANT_BLEND_STEP);
    }

    public float getOfferBlend() { return offerBlend; }
    public float getVariantBlend() { return variantBlend; }
    public float getOfferBlend(float partialTick) { return lerp(partialTick, offerBlendO, offerBlend); }
    public float getVariantBlend(float partialTick) { return lerp(partialTick, variantBlendO, variantBlend); }

    private static float approach(float current, float target, float step) {
        if (current < target) return Math.min(target, current + step);
        if (current > target) return Math.max(target, current - step);
        return current;
    }

    private static float lerp(float partialTick, float previous, float current) {
        return previous + partialTick * (current - previous);
    }
}
