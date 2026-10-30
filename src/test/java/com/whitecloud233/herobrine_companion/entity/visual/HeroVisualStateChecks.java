package com.whitecloud233.herobrine_companion.entity.visual;

public final class HeroVisualStateChecks {
    private static int checks;

    public static void main(String[] args) {
        locomotion();
        gestures();
        idleVariants();
        isolation();
        System.out.println("HeroVisualStateChecks: " + checks + " checks passed");
    }

    private static void locomotion() {
        HeroVisualState state = new HeroVisualState();
        check(!state.isSetupDone(), "A new entity still needs client setup");
        state.markSetupDone();
        check(state.isSetupDone(), "Client setup runs once per state");
        close(state.getFloatingAmount(0.5F), 0.0F, "Fresh floating blend");
        close(state.getImportedWalkAnimationTime(0.5F), 0.0F, "Fresh walking clock");
        state.tickLocomotion(true, false, 0.0, 0.0);
        close(state.getFloatingAmount(0.0F), 0.0F, "Floating retains previous tick");
        close(state.getFloatingAmount(0.5F), 0.025F, "Floating interpolates");
        close(state.getFloatingAmount(1.0F), 0.05F, "Floating advances by one tick");
        for (int i = 0; i < 100; i++) state.tickLocomotion(true, false, 0.0, 0.0);
        close(state.getFloatingAmount(1.0F), 1.0F, "Floating blend saturates");
        for (int i = 0; i < 100; i++) state.tickLocomotion(false, false, 0.0, 0.0);
        close(state.getFloatingAmount(1.0F), 0.0F, "Landing blend saturates");

        float halfStride = walk(22, 4.3 / 20.0, true);
        check(halfStride > 19.0F && halfStride < 21.0F, "Normal speed reaches half a stride after 22 ticks");
        close(walk(22, 4.3 / 20.0, false), halfStride, "Delayed walking flag does not freeze motion");
        close(walk(44, 4.3 / 40.0, true), halfStride, "Slower movement preserves phase per distance");
        close(walk(11, 4.3 / 10.0, true), halfStride, "Faster movement preserves phase per distance");
        close(HeroVisualState.advanceWalkAnimationTicks(7.0F, false, true, 0.0, 0.0), 7.0F,
                "A blocked path does not march in place");
        close(HeroVisualState.advanceWalkAnimationTicks(7.0F, false, false, 0.0, 0.0), 0.0F,
                "Standing still without a path resets the phase");
        close(HeroVisualState.advanceWalkAnimationTicks(7.0F, true, true, 0.2, 0.0), 0.0F,
                "Flight cancels walking");
        close(HeroVisualState.advanceWalkAnimationTicks(0.0F, false, false, 0.3, 0.4),
                HeroVisualState.advanceWalkAnimationTicks(0.0F, false, false, 0.5, 0.0),
                "Diagonal displacement uses horizontal distance");
        close(HeroVisualState.advanceWalkAnimationTicks(0.0F, false, true, 1000.0, 0.0),
                HeroVisualState.advanceWalkAnimationTicks(0.0F, false, true, 4.0, 0.0),
                "Teleport displacement is capped");
        for (double invalid : new double[]{Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY}) {
            close(HeroVisualState.advanceWalkAnimationTicks(7.0F, false, true, invalid, 0.0), 7.0F,
                    "Invalid coordinates cannot poison walking time");
        }
        state.tickLocomotion(false, true, 0.2, 0.0);
        float first = state.getImportedWalkAnimationTime(1.0F);
        close(state.getImportedWalkAnimationTime(0.0F), 0.0F, "Walking interpolates from previous tick");
        close(state.getImportedWalkAnimationTime(0.5F), first / 2.0F, "Walking interpolation uses seconds");
        state.tickLocomotion(false, true, 0.2, 0.0);
        close(state.getImportedWalkAnimationTime(0.0F), first, "Walking retains its previous phase");
        state.tickLocomotion(true, true, 0.2, 0.0);
        close(state.getImportedWalkAnimationTime(0.0F), 0.0F, "Flight clears both walking endpoints");
        close(state.getImportedWalkAnimationTime(1.0F), 0.0F, "Flight clears current walking time");
        state.tickLocomotion(false, true, 0.2, 0.0);
        state.tickLocomotion(false, false, 0.0, 0.0);
        close(state.getImportedWalkAnimationTime(0.5F), 0.0F, "Standing still clears interpolated phase");
    }

    private static float walk(int ticks, double distance, boolean flag) {
        float clock = 0.0F;
        for (int i = 0; i < ticks; i++) {
            clock = HeroVisualState.advanceWalkAnimationTicks(clock, false, flag, distance, 0.0);
        }
        return clock;
    }

    private static void gestures() {
        HeroVisualState state = new HeroVisualState();
        check(state.getGestureClipId() == 0 && state.getGestureTokenSeen() == -1,
                "A fresh entity has not consumed a gesture token");
        state.startGesture(3, 17, 25);
        check(state.getGestureClipId() == 3 && state.getGestureTokenSeen() == 17
                && state.getGestureStartTick() == 25 && !state.isGestureFinished(),
                "Gesture starts from the client's local tick");
        for (int i = 1; i <= 4; i++) {
            state.tickOfferBlend(true);
            close(state.getOfferBlend(), i * 0.25F, "Offer fade-in takes four ticks");
            close(state.getOfferBlend(0.0F), (i - 1) * 0.25F, "Offer retains previous blend");
            close(state.getOfferBlend(0.5F), (i - 0.5F) * 0.25F, "Offer blend interpolates");
        }
        state.tickOfferBlend(true);
        close(state.getOfferBlend(), 1.0F, "Offer blend cannot exceed one");
        state.advanceGestureEvent();
        check(state.getGestureEventCursor() == 1, "Consumed sound advances the event cursor");
        state.finishGesture(5);
        check(state.isGestureFinished() && state.getGestureEventCursor() == 5
                && state.getGestureClipId() == 3, "Finished clip remains available during fade-out");
        for (int i = 1; i <= 4; i++) {
            state.tickOfferBlend(false);
            close(state.getOfferBlend(), 1.0F - i * 0.25F, "Offer fade-out takes four ticks");
        }
        state.tickOfferBlend(false);
        close(state.getOfferBlend(), 0.0F, "Offer blend cannot become negative");
        state.clearGesture();
        check(state.getGestureClipId() == 0 && state.getGestureEventCursor() == 0 && !state.isGestureFinished(),
                "Fade-out releases the local clip");
        check(state.getGestureTokenSeen() == 17, "Clearing a clip does not make an old token look new");
        state.startGesture(4, 18, 80);
        check(state.getGestureStartTick() == 80 && state.getGestureEventCursor() == 0
                && state.getGestureTokenSeen() == 18, "A later gesture starts an independent timeline");
    }

    private static void idleVariants() {
        HeroVisualState state = new HeroVisualState();
        check(state.getIdleVariantClip() == -1 && state.getIdleVariantLast() == -1
                && state.getIdleVariantNextAt() == 100 && !state.isIdleVariantActive(),
                "Idle schedule retains the original defaults");
        state.scheduleIdleVariant(400);
        state.startIdleVariant(5, 150);
        check(state.getIdleVariantClip() == 5 && state.getIdleVariantLast() == 5
                && state.getIdleVariantStartTick() == 150 && state.isIdleVariantActive()
                && state.getIdleVariantNextAt() == 400, "Starting a variant preserves the next schedule");
        for (int i = 1; i <= 5; i++) {
            state.tickVariantBlend(true);
            close(state.getVariantBlend(), i * 0.2F, "Variant fade-in takes five ticks");
            close(state.getVariantBlend(0.5F), (i - 0.5F) * 0.2F, "Variant interpolates blend");
        }
        state.finishIdleVariant(550);
        check(!state.isIdleVariantActive() && state.getIdleVariantClip() == 5
                && state.getIdleVariantNextAt() == 550, "Finishing retains the variant for fading");
        for (int i = 1; i <= 5; i++) {
            state.tickVariantBlend(false);
            close(state.getVariantBlend(), 1.0F - i * 0.2F, "Variant fade-out takes five ticks");
        }
        state.tickVariantBlend(false);
        close(state.getVariantBlend(), 0.0F, "Variant weight cannot become negative");
    }

    private static void isolation() {
        HeroVisualState first = new HeroVisualState();
        HeroVisualState second = new HeroVisualState();
        first.markSetupDone();
        first.startGesture(2, 9, 100);
        first.startIdleVariant(4, 100);
        first.tickOfferBlend(true);
        first.tickVariantBlend(true);
        first.tickLocomotion(true, false, 0.0, 0.0);
        check(!second.isSetupDone() && second.getGestureClipId() == 0 && second.getIdleVariantClip() == -1,
                "Entities never share setup, gestures or idle state");
        close(second.getOfferBlend(), 0.0F, "Offer weight is per entity");
        close(second.getVariantBlend(), 0.0F, "Variant weight is per entity");
        close(second.getFloatingAmount(1.0F), 0.0F, "Locomotion is per entity");
    }

    private static void close(float actual, float expected, String message) {
        check(Math.abs(actual - expected) < 0.001F, message + ": " + actual + " != " + expected);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        checks++;
    }
}
