package com.whitecloud233.herobrine_companion.compat.epicfight;

import com.whitecloud233.herobrine_companion.combat.PoemAttackInput;
import com.whitecloud233.herobrine_companion.combat.PoemGestureQueue;

/** Input edges and recovery buffering without requiring a running Minecraft. */
public final class PoemInputCheck {
    private static int checks;
    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        var input = new PoemAttackInput();
        for (int gesture = 0; gesture < 3; gesture++) {
            input.reset(); input.usePressed();
            check(input.attackPressed(gesture, 0) == gesture && input.consumesAttack(), "Missing chord " + gesture);
            check(input.attackPressed(gesture, 100) == -1, "Mouse/keyboard repeat sent another packet");
            check(input.attackHeld(1000) == -1, "Holding a chord also fired heavy");
            check(input.attackReleased(1100) == -1, "Chord release also fired light");
            check(input.attackPressed(gesture, 1200) == gesture, "A second click with modifier held was lost");
            input.attackReleased(1300);
            check(!input.useReleased(), "Chord also fired the old right-click skill");
            check(!input.useReleased(), "Duplicate use release");
        }
        input.reset(); input.usePressed();
        check(input.useReleased(), "A right tap failed to replay vanilla use");
        check(!input.useReleased(), "Right tap replayed twice");
        input.usePressed(); input.usePressed();
        check(input.useReleased(), "Held/repeated use lost its single tap");
        input.reset(); input.takeEngineControl();
        check(input.attackPressed(-1, 1000) == -1 && input.consumesAttack(), "Plain press fired before tap/hold was known");
        check(input.takeEngineControl() == PoemAttackInput.CLEAR_ENGINE_INPUT, "Press did not clear native shared-key hold");
        check(input.takeEngineControl() == 0, "Engine control repeated");
        check(input.attackHeld(1349) == -1, "Heavy fired before 350 ms");
        check(input.attackReleased(1349) == PoemAttackInput.LIGHT && !input.consumesAttack(), "Short tap did not become light");
        check(input.takeEngineControl() == (PoemAttackInput.CLEAR_ENGINE_INPUT | PoemAttackInput.REPLAY_LIGHT_ATTACK),
                "Short tap did not enter EF's native light buffer");
        check(input.takeEngineControl() == 0 && input.attackReleased(1350) == -1, "Short tap replayed twice");
        input.usePressed();
        check(input.attackPressed(-1, 2000) == -1, "Hold began with a light attack");
        check(input.attackPressed(2, 2100) == -1, "Adding a modifier mid-hold created a chord");
        check(input.attackHeld(2349) == -1 && input.attackHeld(2350) == PoemAttackInput.HEAVY, "Heavy threshold is not 350 ms");
        check(input.takeEngineControl() == PoemAttackInput.CLEAR_ENGINE_INPUT, "Heavy leaked a light replay");
        check(input.attackHeld(2700) == -1 && input.attackHeld(9000) == -1, "Holding attack repeated heavy");
        check(input.attackReleased(9100) == -1 && input.takeEngineControl() == 0, "Heavy release added an attack");
        check(!input.useReleased(), "Held heavy also replayed shared use");
        check(input.attackPressed(-1, 10000) == -1 && input.attackReleased(10350) == PoemAttackInput.HEAVY,
                "A release between ticks lost a completed hold");
        check(input.takeEngineControl() == PoemAttackInput.CLEAR_ENGINE_INPUT, "Missed-tick heavy became light");
        check(input.attackPressed(-1, 11000) == -1 && input.attackReleased(11060) == PoemAttackInput.LIGHT,
                "A short tap after heavy did not recover");
        input.reset();
        check(input.takeEngineControl() == PoemAttackInput.CLEAR_ENGINE_INPUT, "Context change retained pending light replay");
        input.attackPressed(-1, 12000); input.reset();
        check(input.attackHeld(13000) == -1 && input.attackReleased(13000) == -1,
                "Slot/screen/mode/hurt reset completed a cancelled hold");
        check(input.attackPressed(2, 14000) == 2, "Next genuine chord after reset was lost");
        input.usePressed(); input.reset();
        check(!input.useReleased() && !input.consumesAttack(), "Slot/screen/mode reset leaked pending input");
        input.usePressed(); input.consumeUse();
        check(!input.useReleased(), "Heavy attack also fired pending use");
        input.reset();
        check(input.attackPressed(255, 15000) == -1 && input.attackReleased(15100) == PoemAttackInput.LIGHT,
                "Invalid modifier produced a gesture instead of an ordinary tap");

        var queue = new PoemGestureQueue();
        check(!queue.offer(-1, 0, 0, 0) && !queue.offer(3, 0, 0, 0), "Invalid gesture accepted");
        check(!queue.offer(2, 0, 9, 0) && !queue.offer(2, 0, 0, 4), "Invalid slot/mode accepted");
        check(queue.offer(2, 10, 1, 3), "Heavy request rejected");
        check(!queue.offer(0, 10, 1, 3), "Same-tick duplicate accepted");
        check(queue.poll(10, 1, 3, false) == -1, "Attack interrupted an action lock");
        check(queue.poll(18, 1, 3, true) == 2, "Recovery buffer dropped a valid heavy attack");
        check(queue.poll(18, 1, 3, true) == -1, "One gesture executed twice");
        queue.started(18, 50);
        check(!queue.offer(0, 20, 1, 3), "Early input bypassed extra-attack recovery");
        check(queue.offer(0, 60, 1, 3), "Input near recovery was rejected");
        check(queue.poll(67, 1, 3, true) == -1, "Extra attack skipped its recovery");
        check(queue.poll(68, 1, 3, true) == 0, "Buffered attack missed recovery boundary");
        check(queue.offer(1, 80, 1, 3), "Missing pending uppercut");
        check(queue.poll(81, 2, 3, true) == -1, "Pending attack followed an inventory-slot change");
        check(queue.poll(82, 1, 3, true) == -1, "Slot change only temporarily hid pending input");
        check(queue.offer(1, 85, 1, 3), "Missing mode-change fixture");
        check(queue.poll(86, 1, 2, true) == -1 && queue.poll(87, 1, 3, true) == -1, "Mode change retained pending input");
        check(queue.offer(2, 90, 1, 3), "Missing expiry fixture");
        check(queue.poll(101, 1, 3, true) == -1, "Stale input executed after the buffer expired");
        check(queue.offer(2, 102, 1, 3), "Missing cancellation fixture");
        queue.cancel();
        check(queue.poll(103, 1, 3, true) == -1, "Death/unequip/hurt cancellation leaked an attack");
        check(UnityScythePlungeAnimation.holdForLanding(.50F, 20, false), "High aerial heavy did not hold the windup");
        check(!UnityScythePlungeAnimation.holdForLanding(.50F, 20, true), "Heavy did not resume near ground");
        check(!UnityScythePlungeAnimation.holdForLanding(.50F, 60, false), "Void descent can hold forever");
        check(!UnityScythePlungeAnimation.holdForLanding(.72F, 20, false), "Heavy froze inside a blade contact phase");
        check(!UnityScythePlungeAnimation.holdForLanding(37F / 60F, 20, false), "Heavy held the first contact frame");
        System.out.println("POEM_INPUT_OK: " + checks + " checks; tap/350ms hold; three chords; no double attack; cancellation; recovery buffer; bounded plunge");
    }
}
