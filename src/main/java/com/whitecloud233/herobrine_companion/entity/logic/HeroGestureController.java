package com.whitecloud233.herobrine_companion.entity.logic;

import com.whitecloud233.herobrine_companion.entity.HeroEntity;
import static com.whitecloud233.herobrine_companion.entity.HeroEntity.*;
import com.whitecloud233.herobrine_companion.entity.ai.HeroMoveControl;
import com.whitecloud233.herobrine_companion.entity.dialogue.SpeechBubbleTiming;
import com.whitecloud233.herobrine_companion.entity.visual.HeroClip;
import com.whitecloud233.herobrine_companion.entity.visual.HeroGestureLibrary;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/** Server gesture windows, display cleanup and the movement lock share one lifecycle. */
public final class HeroGestureController {
    private HeroGestureController() { }
    public static void start(HeroEntity hero, int clipId) {
        int duration = durationTicks(clipId);
        hero.getEntityData().set(OFFER_GESTURE_ID, clipId);
        hero.getEntityData().set(OFFER_GESTURE_START_TICK, hero.tickCount);
        hero.getEntityData().set(OFFER_GESTURE_DURATION_TICKS, duration);
        hero.getEntityData().set(OFFER_GESTURE_TOKEN, hero.getEntityData().get(OFFER_GESTURE_TOKEN) + 1);
        if (!hero.level().isClientSide) {
            // 立刻站定:不等下一 tick 的收尾,避免"边递东西边迈步"的第一帧
            hero.holdForGiftGesture();
        }
    }
    public static int durationTicks(int clipId) {
        HeroClip clip = HeroGestureLibrary.byClipId(clipId);
        return clip == null ? 80 : Math.round(clip.duration * 20.0F) + 8;
    }
    public static void clear(HeroEntity hero) {
        hero.getEntityData().set(OFFER_GESTURE_ID, 0);
        hero.setVisualMainHandItem(ItemStack.EMPTY);
    }
    public static void showGiftLine(HeroEntity hero, String text) {
        if (text == null || text.isBlank()) {
            return;
        }
        if (hero instanceof com.whitecloud233.herobrine_companion.entity.dialogue.SpeechBubbleAccessor accessor) {
            accessor.herobrineCompanion$showSpeechBubble(
                    net.minecraft.network.chat.Component.literal(text), SpeechBubbleTiming.durationTicks(text));
        }
    }
    public static void hold(HeroEntity hero) {
        if (!hero.isGiftGestureHolding()) {
            return;
        }
        if (!hero.getNavigation().isDone()) {
            hero.getNavigation().stop();
        }
        if (hero.getMoveControl() instanceof HeroMoveControl moveControl) {
            moveControl.stopMoving();
        }
        Vec3 movement = hero.getDeltaMovement();
        if (movement.horizontalDistanceSqr() > 1.0E-6D) {
            hero.setDeltaMovement(0.0D, movement.y, 0.0D);
        }
        hero.xxa = 0.0F;
        hero.zza = 0.0F;
        hero.setSpeed(0.0F);
        if (hero.getEntityData().get(IS_GROUND_WALKING)) {
            hero.getEntityData().set(IS_GROUND_WALKING, false);
        }
        if (hero.scytheAnimTick > 0) {
            hero.scytheAnimTick = 0;
            hero.getEntityData().set(INSPECTING_SCYTHE, false);
        }
        if (hero.debugAnimTick > 0) {
            hero.debugAnimTick = 0;
            hero.getEntityData().set(IS_DEBUGGING, false);
        }
    }
    public static boolean isBusy(HeroEntity hero) {
        if (hero.isOfferGestureActive() || hero.isPoseEditing) return true;
        if (hero.isBattleModeActive() || hero.isVisualEatingActive()) return true;
        if (hero.isInspectingScythe() || hero.isDebugAnim() || hero.isCastingThunder()) return true;
        if (hero.getEntityData().get(IS_CHALLENGE_ACTIVE)) return true;
        return hero.getInvitedAction() != 0;
    }
}
