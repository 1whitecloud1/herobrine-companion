package com.whitecloud233.herobrine_companion.destructiongod.world;

import com.sun.source.tree.MethodTree;
import com.sun.source.tree.VariableTree;
import com.whitecloud233.herobrine_companion.destructiongod.entity.DestructionSkill;
import com.whitecloud233.herobrine_companion.regression.SourceFixturesChecks;
import com.whitecloud233.herobrine_companion.util.SegmentProjection;
import net.minecraft.world.phys.Vec3;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Differential skill contracts plus real sweep/projection behavior, without a mod-loader world. */
public final class DestructionRefactorChecks {
    private static int checks;
    public static void main(String[] args) throws Exception {
        skillContracts();
        lightningContracts();
        sweepContracts();
        projectionContracts();
        check("arc minimum unchanged", LightningSegmentTask.Kind.ARC.minimumRadius == 1.5);
        check("beam minimum unchanged", LightningSegmentTask.Kind.BEAM.minimumRadius == 2.5);
        System.out.println("DestructionRefactorChecks: " + checks + " checks passed");
    }

    private static void skillContracts() throws Exception {
        String current = Files.readString(Path.of("src/main/java/com/whitecloud233/herobrine_companion/destructiongod/entity/DestructionGodHerobrineEntity.java"));
        Set<String> names = Set.of("tickCooldowns", "isSkillReady", "setSkillCooldown", "hasGlobalSkillInterval",
                "getSkillDuration", "getSkillTriggerTick", "getThunderSkyNetDuration", "getBaseCooldown", "isScytheSkill");
        String selected = SourceFixturesChecks.members(current, member -> {
            if (member instanceof MethodTree method) return names.contains(method.getName().toString());
            if (member instanceof VariableTree field) return field.getType().toString().equals("int")
                    || field.getName().contentEquals("skillCooldowns");
            return false;
        });
        String before = SourceFixturesChecks.resource("skill-baseline.java");
        before = before.substring(before.indexOf('{') + 1, before.lastIndexOf('}'));
        String stubs = "public int state, phase=1; public int getSkillState(){return state;} public int getBossPhase(){return phase;}";
        String imports = "import net.minecraft.util.Mth; import com.whitecloud233.herobrine_companion.destructiongod.entity.DestructionSkill;";
        try (var oldFixture = SourceFixturesChecks.compile("BeforeSkills", imports + "public class BeforeSkills {" + before + stubs + "}");
             var newFixture = SourceFixturesChecks.compile("AfterSkills", imports + "public class AfterSkills {" + selected + stubs + "}")) {
            Object old = oldFixture.type().getConstructor().newInstance(), now = newFixture.type().getConstructor().newInstance();
            for (int id : new int[]{-100, -1, 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, Integer.MAX_VALUE}) {
                check("static duration id=" + id, call(old, "getSkillDuration", id).equals(call(now, "getSkillDuration", id)));
                check("static trigger id=" + id, call(old, "getSkillTriggerTick", id).equals(call(now, "getSkillTriggerTick", id)));
                check("cooldown metadata id=" + id, call(old, "getBaseCooldown", id).equals(call(now, "getBaseCooldown", id)));
                check("scythe membership id=" + id, call(old, "isScytheSkill", id).equals(call(now, "isScytheSkill", id)));
                set(old, "state", id); set(now, "state", id);
                for (int phase : new int[]{0, 1, 2, 3, 4, 5}) {
                    set(old, "phase", phase); set(now, "phase", phase);
                    check("casting duration id=" + id + " phase=" + phase, call(old, "getSkillDuration").equals(call(now, "getSkillDuration")));
                    check("casting trigger id=" + id, call(old, "getSkillTriggerTick").equals(call(now, "getSkillTriggerTick")));
                }
                for (int value : new int[]{-1, 0, 1, 3}) {
                    call(old, "setSkillCooldown", id, value); call(now, "setSkillCooldown", id, value);
                    for (int tick = 0; tick < 5; tick++) {
                        check("readiness id=" + id + " value=" + value + " tick=" + tick,
                                call(old, "isSkillReady", id).equals(call(now, "isSkillReady", id)));
                        call(old, "tickCooldowns"); call(now, "tickCooldowns");
                    }
                }
            }
            for (Object fixture : new Object[]{old, now}) {
                call(fixture, "setSkillCooldown", 1, 0);
                set(fixture, "globalSkillIntervalTicks", 2);
                check("global cooldown blocks ready skill", call(fixture, "isSkillReady", 1).equals(false));
                call(fixture, "tickCooldowns");
                check("global cooldown still blocks after one tick", call(fixture, "isSkillReady", 1).equals(false));
                call(fixture, "tickCooldowns");
                check("global cooldown releases after two ticks", call(fixture, "isSkillReady", 1).equals(true));
            }
            Object another = newFixture.type().getConstructor().newInstance();
            call(now, "setSkillCooldown", 1, 99);
            check("cooldowns are per entity", call(another, "isSkillReady", 1).equals(true));
        }
        float[] volumes = {0,3.5F,2.5F,2.2F,4F,2.3F,2.4F,3F,3.2F,4F,4.5F,3.8F};
        float[] pitches = {0,.6F,.7F,.45F,.5F,.6F,.6F,.45F,.55F,.45F,.55F,.55F};
        for (DestructionSkill skill : DestructionSkill.values()) {
            check("explicit id round trip", DestructionSkill.fromId(skill.id()) == skill);
            if (skill == DestructionSkill.NONE) check("NONE is silent", skill.sound() == null);
            else check("sound levels retained: " + skill, skill.sound().volume() == volumes[skill.id()] && skill.sound().pitch() == pitches[skill.id()]);
        }
    }

    private static Object call(Object target, String name, Object... args) throws Exception {
        Class<?>[] types = new Class<?>[args.length];
        java.util.Arrays.fill(types, int.class);
        Method method = target.getClass().getDeclaredMethod(name, types);
        method.setAccessible(true);
        return method.invoke(target, args);
    }
    private static void set(Object target, String name, int value) throws Exception {
        var field = target.getClass().getDeclaredField(name); field.setAccessible(true); field.setInt(target, value);
    }

    private static void lightningContracts() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/whitecloud233/herobrine_companion/destructiongod/world/LightningSegmentTask.java"));
        Set<String> names = Set.of("doTick", "prepareBlocks", "damageCylinder");
        String after = SourceFixturesChecks.members(source, member -> member instanceof MethodTree method && names.contains(method.getName().toString())).replace("@Override", "");
        String harness = SourceFixturesChecks.resource("lightning-harness.java.txt");
        String imports = "import net.minecraft.world.phys.*; import net.minecraft.core.BlockPos; import net.minecraft.util.Mth; import javax.annotation.Nullable; import java.util.*; import com.whitecloud233.herobrine_companion.util.SegmentProjection;";
        try (var current = SourceFixturesChecks.compile("AfterLightning", imports+"public class AfterLightning {"+harness+after+"}")) {
            for (int kind=0;kind<2;kind++) {
                String before = SourceFixturesChecks.resource("lightning-"+(kind==0?"arc":"beam")+"-baseline.java");
                before = before.substring(before.indexOf('{')+1,before.lastIndexOf('}'));
                String name = kind==0?"BeforeArc":"BeforeBeam";
                try (var old = SourceFixturesChecks.compile(name,imports+"public class "+name+" {"+harness+before+"}")) {
                    var oldRun=old.type().getMethod("run",int.class,Vec3.class,Vec3.class,double.class);
                    var newRun=current.type().getMethod("run",int.class,Vec3.class,Vec3.class,double.class);
                    for (Vec3 end:List.of(new Vec3(10,0,0),new Vec3(0,12,0),Vec3.ZERO)) {
                        for (double radius:new double[]{-1D,1.75D,4D}) {
                            Object expected=oldRun.invoke(old.type().getConstructor().newInstance(),kind,Vec3.ZERO,end,radius);
                            Object actual=newRun.invoke(current.type().getConstructor().newInstance(),kind,Vec3.ZERO,end,radius);
                            check("lightning damage/knockback/blocks/effects and retry kind="+kind+" end="+end+" radius="+radius,expected.equals(actual));
                        }
                    }
                }
            }
        }
    }

    private static void sweepContracts() {
        for (double[] values : new double[][]{{4,1.25}, {2,2.5}, {3,1.5}, {5,1.75}}) {
            int steps = (int) values[0]; double size = values[1];
            Sweep task = new Sweep(steps * size, steps, size, 2);
            check("delay first tick", !task.tick(new TickBudget(4096)) && task.positions.isEmpty());
            check("delay second tick", !task.tick(new TickBudget(4096)) && task.positions.isEmpty());
            check("last step does not eagerly finish", !task.tick(new TickBudget(4096)) && task.completed == 0);
            check("step count and distance", task.positions.size() == steps && task.currentLength == steps * size);
            check("first step starts at origin", task.positions.get(0) == 0);
            check("completion is on following tick", task.tick(new TickBudget(4096)) && task.completed == 1);
            Sweep unloaded = new Sweep(100, steps, size, 0);
            unloaded.stopAt = 0;
            check("unloaded stop does not advance or finish effects", unloaded.tick(new TickBudget(4096)) && unloaded.currentLength == 0 && unloaded.completed == 0);
        }
        Sweep empty = new Sweep(0, 4, 1.25, 0);
        check("empty sweep finishes without steps", empty.tick(new TickBudget(4096)) && empty.completed == 1 && empty.positions.isEmpty());
    }
    private static final class Sweep extends TerrainTask {
        final int steps; final double size; final List<Double> positions = new ArrayList<>();
        int completed, stopAt = -1;
        Sweep(double length, int steps, double size, int delay) {
            super(null, null, Vec3.ZERO, new Vec3(0, 0, 1), length, delay);
            this.steps = steps; this.size = size;
        }
        @Override protected boolean doTick(TickBudget budget) {
            return sweep(steps, size, index -> {
                if (index == stopAt) return false;
                positions.add(currentLength); return true;
            }, () -> completed++);
        }
    }

    private static void projectionContracts() {
        SegmentProjection line = new SegmentProjection(new Vec3(1,2,3), new Vec3(5,2,3));
        check("projection interior", line.closestPoint(new Vec3(3,9,7)).equals(new Vec3(3,2,3)));
        check("projection before start clamps", line.closestPoint(new Vec3(-2,9,7)).equals(new Vec3(1,2,3)));
        check("projection past end clamps", line.closestPoint(new Vec3(20,9,7)).equals(new Vec3(5,2,3)));
        check("degenerate projection matches original threshold", new SegmentProjection(Vec3.ZERO, new Vec3(.001,0,0)).closestPoint(new Vec3(1,2,3)).equals(Vec3.ZERO));
    }
    private static void check(String label, boolean condition) { if (!condition) throw new AssertionError(label); checks++; }
}
