package com.whitecloud233.modid.herobrine_companion.combat.poem;

import com.whitecloud233.modid.herobrine_companion.combat.PoemAttackInput;
import com.whitecloud233.modid.herobrine_companion.entity.ai.goal.HeroBattleStanceGoal;
import java.util.UUID;
import net.minecraft.util.profiling.InactiveProfiler;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/** Real authored clips, their server blade volumes, input buffering and Hero link timing, without EF. */
public final class PoemActionChecks {
    private static int checks;
    public static void main(String[] args) { run(); }
    public static void run() {
        inputContextChanges();
        battleGoalCadence();
        heroMoveSelection();
        PoemMotionLibrary lib = PoemMotionLibrary.get();
        check(PoemMotionLibrary.basicMove(false, false) == 0, "Standing attack chooses ground chain");
        check(PoemMotionLibrary.basicMove(true, false) == PoemMotionLibrary.DASH, "Sprinting chooses dash");
        check(PoemMotionLibrary.basicMove(true, true) == PoemMotionLibrary.AIR, "Air attack takes priority over sprint");
        for (int mode = 0; mode < 4; mode++) {
            double battleTick = 0;
            for (int serial = 1; serial <= 16; serial++) {
                int step = PoemHeroCombo.step(serial);
                check(step == (serial - 1) % 4, "Hero traverses all four cuts repeatedly");
                var timing = lib.timing(mode, step);
                var first = timing.contacts().get(0);
                var last = timing.contacts().get(timing.contacts().size() - 1);
                int hit = PoemHeroCombo.hitTick(mode, serial), link = PoemHeroCombo.chainTick(mode, serial);
                check(hit >= first.start() * 20 - .0001 && hit <= first.end() * 20 + 1.0001, "Hero impact belongs to the authored strike");
                check(link >= last.end() * 20 - .0001, "Hero never chains before the last blade contact");
                check(link <= PoemHeroCombo.durationTicks(mode, serial), "Follow-up starts before idle/recovery gap");
                check(Math.abs(PoemHeroCombo.durationTicks(mode, serial) - timing.duration() * 20) < 1.0001,
                        "Each cut retains its own native duration");
                double next = battleTick + link;
                check(next > battleTick, "Repeated Hero chain keeps advancing without resetting to idle");
                battleTick = next;
            }
            for (int move = 0; move < PoemMotionLibrary.MOVE_COUNT; move++) {
                var clip = lib.clip(mode, move);
                var timing = clip.timing;
                for (float t : new float[]{0, timing.contacts().get(0).start(), timing.recovery(), timing.duration()}) {
                    Matrix4f[] world = clip.sample(t, true);
                    check(world.length == 20, "Every ground, dash, air and gesture clip has the full rig");
                    for (Matrix4f joint : world) for (float f : joint.get(new float[16])) check(Float.isFinite(f), "Pose stays finite");
                    int tool = lib.joint("Tool_R");
                    check(world[tool].equals(clip.sampleJoint(tool, t, true), .0001f), "Blade socket matches the full rendered pose");
                }
                if (move < PoemMotionLibrary.DASH) continue;
                PoemComboClock clock = new PoemComboClock();
                check(clock.request(mode, move, 0) && clock.step() == move, "Special intent selects its own clip");
                check(!clock.request(mode, 0, 1) && clock.step() == move, "Early click cannot interrupt special contacts");
                double recovery = timing.recovery() * 20;
                check(!clock.request(mode, 0, recovery - 1), "Special buffers the next ordinary cut");
                check(clock.tick(Math.ceil(recovery)) && clock.step() == 0, "Special returns to cut one at recovery");
                clock.synchronize(mode, move, 100);
                check(clock.step() == move && clock.timing() == lib.timing(mode, move), "Tracking sync accepts every special move");

                var actor = new PoemBladeSweep.Actor(Vec3.ZERO, 0, false, 0);
                var sweep = new PoemBladeSweep(mode, move, 100, actor);
                var window = timing.contacts().get(0);
                var hits = sweep.poll(100 + window.end() * 20, actor);
                check(!sweep.cancelled() && !hits.isEmpty(), "Special attacks generate real server blade sweeps");
                var hit = hits.get(0);
                var bounds = hit.boxes().get(0).bounds();
                UUID victim = UUID.randomUUID();
                check(sweep.claim(hit, victim, bounds) && !sweep.claim(hit, victim, bounds), "A special hits a victim once per contact");
            }
            PoemComboClock clock = new PoemComboClock();
            check(clock.request(mode, 0), "Ordinary combo starts");
            double recovery = clock.timing().recovery() * 20;
            check(!clock.request(mode, PoemMotionLibrary.DASH, recovery - 1), "Dash intent can be buffered from a cut");
            check(clock.tick(Math.ceil(recovery)) && clock.step() == PoemMotionLibrary.DASH, "Buffered intent preserves dash rather than selecting another basic cut");
            check(!clock.request(mode, 99, 1000) && !clock.request(mode, 2, 1000), "Client cannot force invalid or arbitrary combo indices");
            check(clock.request((mode + 1) % 4, PoemMotionLibrary.AIR, 1000), "Mode change starts a valid new air move");
        }
        PoemAttackInput input = new PoemAttackInput();
        check(input.attackPressed(-1, 0) == PoemAttackInput.NONE, "Plain press waits for tap/hold resolution");
        check(input.attackReleased(100) == PoemAttackInput.LIGHT, "Tap selects a contextual basic attack");
        input.attackPressed(-1, 1000);
        check(input.attackHeld(1350) == PoemAttackInput.HEAVY, "Held attack selects heavy");
        check(input.attackHeld(1400) == PoemAttackInput.NONE && input.attackReleased(1450) == PoemAttackInput.NONE, "A held heavy fires only once");
        input.usePressed();
        check(input.attackPressed(2, 2000) == 2, "Use/attack chord selects heavy");
        check(!input.useReleased(), "Heavy chord does not also activate the item's right-click skill");
        System.out.println("PoemActionChecks: " + checks + " checks passed; 30 native clips, dash/air/gestures, blade collision, Hero chain and tap/hold input");
    }
    private static void heroMoveSelection() {
        java.util.Set<Integer> ground = new java.util.HashSet<>(), air = new java.util.HashSet<>();
        for (int serial = 1; serial <= 28; serial++) {
            int move = PoemHeroCombo.selectMove(serial, false, false);
            ground.add(move);
            air.add(PoemHeroCombo.selectMove(serial, true, false));
            check(PoemHeroCombo.selectMove(serial, false, true) == PoemMotionLibrary.DASH, "A gap selects a real dash");
            check(PoemHeroCombo.selectMove(serial, true, true) != PoemMotionLibrary.DASH, "Air moves take priority over ground dash");
        }
        check(ground.equals(java.util.Set.of(0, 1, 2, 3, 6, 8, 10)), "Ground combat cycles all four cuts and all three gestures");
        check(air.equals(java.util.Set.of(5, 7, 9, 11)), "Aerial combat cycles air cut, flurry, uppercut and heavy");
        for (int mode = 0; mode < 4; mode++) for (int move = 0; move < PoemMotionLibrary.MOVE_COUNT; move++) {
            var timing = PoemMotionLibrary.get().timing(mode, move);
            int link = PoemHeroCombo.chainForMove(mode, move);
            for (var contact : timing.contacts()) check(PoemHeroCombo.contactTick(contact) < link,
                    "No follow-up truncates a flurry or aerial contact");
            check(link <= PoemHeroCombo.durationForMove(mode, move), "Every move chains before its clip ends");
        }
        Vec3 offset = new Vec3(4, 0, 0), falling = new Vec3(0, -.2, 0);
        Vec3 step = com.whitecloud233.modid.herobrine_companion.entity.ai.combat.HeroPoemCombat.attackVelocity(falling, offset, 0, false, true);
        Vec3 dash = com.whitecloud233.modid.herobrine_companion.entity.ai.combat.HeroPoemCombat.attackVelocity(falling, offset, PoemMotionLibrary.DASH, false, true);
        check(step.x > 0 && dash.x > step.x && dash.x <= .65 && step.y == falling.y,
                "Ground cuts step physically, dash moves faster, and gravity is preserved");
        Vec3 rising = com.whitecloud233.modid.herobrine_companion.entity.ai.combat.HeroPoemCombat.attackVelocity(Vec3.ZERO, offset, 8, true, true);
        check(rising.y > 0 && rising.y <= .28, "Uppercut flight climbs with bounded real vertical velocity");
        Vec3 blocked = com.whitecloud233.modid.herobrine_companion.entity.ai.combat.HeroPoemCombat.attackVelocity(Vec3.ZERO, offset, 4, false, false);
        check(blocked.horizontalDistanceSqr() == 0, "Collision stops the forward attack drive");
        Vec3 close = com.whitecloud233.modid.herobrine_companion.entity.ai.combat.HeroPoemCombat.attackVelocity(Vec3.ZERO, new Vec3(.5, -4, 0), 5, true, true);
        check(close.horizontalDistanceSqr() == 0 && close.y >= -.22 && close.y < 0, "Air attack brakes near target and descends safely");
    }

    private static void inputContextChanges() {
        for (boolean epicFight : new boolean[]{false, true}) {
            for (int scenario = 0; scenario < 4; scenario++) {
                PoemAttackInput input = new PoemAttackInput();
                input.usePressed();
                input.attackPressed(scenario == 3 ? 2 : -1, 1000);
                input.takeEngineControl(); // Press already handled by standalone input or EF.
                if (scenario == 1) input.attackReleased(1100); // Completed tap.
                if (scenario == 2) input.attackHeld(1400); // Heavy, still held while switching.
                if (!epicFight) input.takeEngineControl(); // Standalone send() consumes resolved input immediately.
                input.reset(epicFight); // Unequip, mode change, screen, death, or lost focus.
                for (int tick = 0; tick < 40; tick++) {
                    check(!input.consumesAttack() && !input.holdsUse(), "Context change releases held input");
                    check(input.hasEngineControl() == epicFight,
                            "Only EF may retain an unacknowledged clear; ordinary vanilla clicks must survive");
                    input.reset(epicFight); // Invalid context is checked on every client tick/input event.
                }
                check(input.takeEngineControl() == (epicFight ? PoemAttackInput.CLEAR_ENGINE_INPUT : 0),
                        "Reset cancels light replay while preserving EF's one-shot reservation clear");
                input.reset(epicFight);
                check(!input.hasEngineControl(), "Acknowledged clear never reappears");
                check(input.attackReleased(1500) == PoemAttackInput.NONE && !input.useReleased(),
                        "Releasing cancelled keys never attacks or replays use");
                check(input.attackPressed(-1, 2000) == PoemAttackInput.NONE
                                && input.attackReleased(2100) == PoemAttackInput.LIGHT,
                        "Re-equipping Poem accepts a fresh tap");
            }
        }
    }

    private static void battleGoalCadence() {
        GoalSelector selector = new GoalSelector(() -> InactiveProfiler.INSTANCE);
        CountingBattleGoal goal = new CountingBattleGoal();
        selector.addGoal(0, goal);
        // Mob alternates full selector ticks and tickRunningGoals(false). Exercise the real
        // scheduler with Hero's inherited cadence policy instead of assuming one goal tick/frame.
        for (int tick = 0; tick < 120; tick++) {
            if (tick % 2 == 0) selector.tick(); else selector.tickRunningGoals(false);
            check(goal.ticks == tick + 1, "Hero combat advances on every game tick, matching rendered clips");
        }
    }

    private static final class CountingBattleGoal extends HeroBattleStanceGoal {
        int ticks;
        CountingBattleGoal() { super(null); }
        @Override public boolean canUse() { return true; }
        @Override public boolean canContinueToUse() { return true; }
        @Override public void start() { }
        @Override public void tick() { ticks++; }
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
}
