package com.whitecloud233.herobrine_companion.compat.epicfight;

import com.sun.source.tree.MethodTree;
import com.whitecloud233.herobrine_companion.regression.SourceFixturesChecks;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

/** Verify pinned third-party injection sites and run the production ownership guard. */
public final class OptionalCompatChecks {
    private static int checks;
    public static void main(String[] args) throws Exception {
        ClassNode wom = readClass("reascer/wom/gameasset/WOMAnimations");
        MethodNode callback = wom.methods.stream().filter(m -> m.name.equals("lambda$build$196")).findFirst().orElseThrow();
        String descriptor = "(Lnet/neoforged/neoforge/registries/DeferredHolder;Ljava/lang/Object;)V";
        int resets = 0;
        Set<String> keys = new java.util.HashSet<>();
        for (var instruction : callback.instructions) {
            if (instruction instanceof MethodInsnNode call && call.owner.equals("yesman/epicfight/skill/SkillDataManager")
                    && call.name.equals("setDataSync") && call.desc.equals(descriptor)) resets++;
            if (instruction instanceof FieldInsnNode field && field.owner.equals("reascer/wom/skill/WOMSkillDataKeys")) keys.add(field.name);
        }
        check(resets == 3 && keys.equals(Set.of("LAPSE", "PARTICLE", "ACTIVE")), "Pinned WOM reset sites changed");
        ClassNode mixin = readClass("com/whitecloud233/herobrine_companion/compat/mixin/epicfight/WomAntitheusLapseMixin");
        check(mixin.methods.stream().filter(m -> m.name.equals("herobrineCompanion$resetOnlyOwnedSkillData"))
                .anyMatch(m -> m.desc.startsWith("(Lyesman/epicfight/skill/SkillDataManager;Lnet/neoforged/neoforge/registries/DeferredHolder;Ljava/lang/Object;")),
                "Redirect uses the target NeoForge holder signature");
        ClassNode effects = readClass("com/hm/efn/util/EffekUnits");
        check(effects.methods.stream().filter(m -> m.name.equals("VFXENABLE") && m.desc.equals("()Z"))
                .anyMatch(m -> java.util.stream.StreamSupport.stream(m.instructions.spliterator(), false)
                        .anyMatch(i -> i instanceof FieldInsnNode f && f.owner.equals("com/hm/efn/EFNClientConfig") && f.name.equals("VFX_PLUS"))),
                "Nightfall config access changed: revisit the server guard");
        ClassNode trigger = readClass("com/hm/efn/client/events/AddTriggerEvent");
        check(trigger.methods.stream().filter(m -> m.name.equals("onEffectAdded"))
                .anyMatch(m -> java.util.stream.StreamSupport.stream(m.instructions.spliterator(), false)
                        .anyMatch(i -> i instanceof MethodInsnNode call && call.owner.equals(effects.name) && call.name.equals("VFXENABLE"))),
                "Nightfall effect hook changed");
        String base = "src/main/java/com/whitecloud233/herobrine_companion/compat/";
        String source = Files.readString(Path.of(base + "epicfight/WomAntitheusLapseCompat.java"));
        String methods = SourceFixturesChecks.members(source, member -> member instanceof MethodTree method
                && Set.of("resetOnlyOwnedSkillData", "setDataSyncIfOwned").contains(method.getName().toString()));
        try (var fixture = SourceFixturesChecks.compile("OwnershipFixture", "import java.util.*; public class OwnershipFixture {" + HARNESS + methods + "}")) {
            checks += (int) fixture.type().getMethod("run").invoke(null);
        }
        String guard = SourceFixturesChecks.members(Files.readString(Path.of(base + "mixin/epicfight/EffekUnitsVfxMixin.java")),
                member -> member instanceof MethodTree method && method.getName().toString().equals("herobrineCompanion$guardServerVfx"));
        guard = guard.replaceAll("@Inject\\([^\\n]*\\)\\s*", "");
        try (var fixture = SourceFixturesChecks.compile("NightfallSideFixture", "public class NightfallSideFixture {" + SIDES + guard + "}")) {
            checks += (int) fixture.type().getMethod("run").invoke(null);
        }
        String config = Files.readString(Path.of("src/main/resources/herobrine_companion.compat.mixins.json"));
        check(config.contains("epicfight.EffekUnitsVfxMixin") && config.contains("epicfight.WomAntitheusLapseMixin"), "Both optional guards are registered");
        System.out.println("OptionalCompatChecks: " + checks + " checks passed");
    }
    private static ClassNode readClass(String name) throws Exception {
        try (var stream = OptionalCompatChecks.class.getResourceAsStream("/" + name + ".class")) {
            if (stream == null) throw new AssertionError("Missing pinned class " + name);
            ClassNode node = new ClassNode(); new ClassReader(stream).accept(node, 0); return node;
        }
    }
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        checks++;
    }
    private static final String SIDES = """
        enum Dist { CLIENT, DEDICATED_SERVER }
        static class FMLEnvironment { static Dist dist; }
        static class CallbackInfoReturnable<T> { T value; void setReturnValue(T result) { value = result; } }
        public static int run() {
            var result = new CallbackInfoReturnable<Boolean>(); FMLEnvironment.dist = Dist.DEDICATED_SERVER;
            herobrineCompanion$guardServerVfx(result);
            if (!Boolean.FALSE.equals(result.value)) throw new AssertionError("Dedicated server must skip client config");
            result = new CallbackInfoReturnable<Boolean>(); FMLEnvironment.dist = Dist.CLIENT;
            herobrineCompanion$guardServerVfx(result);
            if (result.value != null) throw new AssertionError("Client VFX behavior must remain with Nightfall");
            return 2;
        }
        """;
    private static final String HARNESS = """
        static class SkillDataKey<T> {}
        static class DeferredHolder<R, T extends R> {}
        static class Skill {}
        static class LivingEntityPatch<T> {}
        static class SkillDataManager {
            Set<Object> keys = Collections.newSetFromMap(new IdentityHashMap<>()); int writes;
            boolean hasData(Object key) { return keys.contains(key); }
            <T> void setDataSync(DeferredHolder<SkillDataKey<?>, ? extends SkillDataKey<T>> key, T value) { writes++; }
        }
        record SkillContainer(SkillDataManager manager) { SkillDataManager getDataManager() { return manager; } }
        static class ServerPlayerPatch extends LivingEntityPatch<Object> {
            Map<Skill, SkillContainer> skills = new IdentityHashMap<>();
            SkillContainer getSkill(Skill skill) { return skills.get(skill); }
        }
        record WomSkillState(DeferredHolder<?, ?> lapse, DeferredHolder<?, ?> particle, DeferredHolder<?, ?> active, Skill passive, Skill innate) {}
        static class LazyWomSkills { static WomSkillState STATE; }
        public static int run() {
            var lapse = new DeferredHolder<SkillDataKey<?>, SkillDataKey<Boolean>>();
            var particle = new DeferredHolder<SkillDataKey<?>, SkillDataKey<Boolean>>();
            var active = new DeferredHolder<SkillDataKey<?>, SkillDataKey<Boolean>>();
            var other = new DeferredHolder<SkillDataKey<?>, SkillDataKey<Boolean>>();
            var passive = new Skill(); var innate = new Skill();
            LazyWomSkills.STATE = new WomSkillState(lapse, particle, active, passive, innate);
            var player = new ServerPlayerPatch(); var original = new SkillDataManager();
            original.keys.addAll(List.of(lapse, particle, active));
            player.skills.put(passive, new SkillContainer(original)); player.skills.put(innate, new SkillContainer(original));
            for (var key : List.of(lapse, particle, active)) resetOnlyOwnedSkillData(player, original, key, false);
            if (original.writes != 3) throw new AssertionError("Owned state must reset");
            var switched = new SkillDataManager(); switched.keys.addAll(original.keys);
            player.skills.put(passive, new SkillContainer(switched)); player.skills.remove(innate);
            for (var key : List.of(lapse, particle, active)) resetOnlyOwnedSkillData(player, original, key, false);
            if (original.writes != 3 || switched.writes != 0) throw new AssertionError("A finishing animation must not reset a replaced weapon");
            player.skills.put(passive, new SkillContainer(original)); original.keys.remove(lapse);
            resetOnlyOwnedSkillData(player, original, lapse, false);
            if (original.writes != 3) throw new AssertionError("Removed key must not be written");
            resetOnlyOwnedSkillData(player, original, other, false);
            if (original.writes != 4) throw new AssertionError("Unrelated data preserves normal write behavior");
            resetOnlyOwnedSkillData(new LivingEntityPatch<>(), original, particle, false);
            if (original.writes != 4) throw new AssertionError("Non-player must not reset player slots");
            LazyWomSkills.STATE = null; resetOnlyOwnedSkillData(player, original, active, false);
            if (original.writes != 4) throw new AssertionError("Missing optional WOM must leave skill data alone");
            return 6;
        }
        """;
}
