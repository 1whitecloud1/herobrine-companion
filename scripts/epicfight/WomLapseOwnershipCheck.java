package com.whitecloud233.modid.herobrine_companion.compat.epicfight;

import com.google.gson.GsonBuilder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import yesman.epicfight.skill.SkillContainer;
import yesman.epicfight.skill.SkillDataKey;
import yesman.epicfight.skill.SkillDataManager;

/** Reproduce the cleared-slot crash with EF's real data map, without a game. */
public final class WomLapseOwnershipCheck {
    private static int checks;

    private static final class Owner extends SkillContainer {
        final RecordingManager manager = new RecordingManager();
        Owner() { super(null, null); }
        @Override public SkillDataManager getDataManager() { return manager; }
    }

    private static final class RecordingManager extends SkillDataManager {
        int syncs;
        RecordingManager() { super(null); }
        @Override public <T> void setDataSync(SkillDataKey<T> key, T value) {
            // Exercise EF's registration check and value mutation; only the
            // network transport is replaced because there is no player/server.
            super.setData(key, value);
            syncs++;
        }
    }

    private static void check(boolean result, String message) {
        if (!result) throw new AssertionError(message);
        checks++;
    }

    private static ClassNode bytecode(String name) throws Exception {
        var node = new ClassNode();
        try (var input = WomLapseOwnershipCheck.class.getClassLoader().getResourceAsStream(name + ".class")) {
            if (input == null) throw new AssertionError("Missing class: " + name);
            new ClassReader(input).accept(node, 0);
        }
        return node;
    }

    public static void main(String[] args) throws Exception {
        var key = new SkillDataKey<Boolean>(null, true, false);
        var passive = new Owner();
        boolean reproduced = false;
        try { passive.manager.setDataSync(key, false); }
        catch (IllegalStateException expected) { reproduced = expected.getMessage().contains("unregistered"); }
        check(reproduced, "Original write should reproduce the unregistered-key crash");

        WomAntitheusLapseCompat.setDataSyncIfOwned(passive, passive.manager, key, false);
        check(passive.manager.syncs == 0 && !passive.manager.hasData(key), "Do not register missing state during a callback");
        passive.manager.registerData(key);
        WomAntitheusLapseCompat.setDataSyncIfOwned(passive, passive.manager, key, false);
        check(passive.manager.syncs == 1 && !passive.manager.getDataValue(key), "The owning skill must still reset and synchronize");

        passive.manager.setData(key, true);
        WomAntitheusLapseCompat.setDataSyncIfOwned(null, passive.manager, key, false);
        check(passive.manager.syncs == 1 && passive.manager.getDataValue(key), "Removed owner must not update a surviving slot");
        var replacement = new Owner();
        replacement.manager.registerData(key);
        WomAntitheusLapseCompat.setDataSyncIfOwned(passive, replacement.manager, key, false);
        check(replacement.manager.syncs == 0 && replacement.manager.getDataValue(key), "A shared key in a different skill must remain unchanged");

        passive.manager.clearData();
        WomAntitheusLapseCompat.setDataSyncIfOwned(passive, passive.manager, key, false);
        check(passive.manager.syncs == 1 && passive.manager.keySet().isEmpty(), "Cleared skill data must stay cleared");
        var innate = new Owner(); innate.manager.registerData(key);
        WomAntitheusLapseCompat.setDataSyncIfOwned(innate, innate.manager, key, false);
        check(innate.manager.syncs == 1 && !innate.manager.getDataValue(key), "An existing innate owner can reset independently of the passive");

        var wom = bytecode("reascer/wom/gameasset/WOMAnimations");
        var callback = wom.methods.stream().filter(m -> m.name.equals("lambda$build$196")).findFirst().orElseThrow();
        String argsDescriptor = "Lyesman/epicfight/world/capabilities/entitypatch/LivingEntityPatch;Lyesman/epicfight/api/asset/AssetAccessor;Lyesman/epicfight/api/animation/property/AnimationParameters;";
        check(callback.desc.equals("(" + argsDescriptor + ")V") && (callback.access & Opcodes.ACC_STATIC) != 0,
                "Pinned WOM callback signature changed");
        List<String> keys = new ArrayList<>();
        int writes = 0;
        for (var instruction : callback.instructions) {
            if (instruction instanceof FieldInsnNode f && f.owner.equals("reascer/wom/skill/WOMSkillDataKeys")) keys.add(f.name);
            if (instruction instanceof MethodInsnNode m && m.owner.equals("yesman/epicfight/skill/SkillDataManager")
                    && m.name.equals("setDataSync") && m.desc.equals("(Lyesman/epicfight/skill/SkillDataKey;Ljava/lang/Object;)V")) writes++;
        }
        check(writes == 3 && keys.equals(List.of("LAPSE", "PARTICLE", "ACTIVE")), "Redirect no longer matches all three WOM writes");
        var mixin = bytecode("com/whitecloud233/modid/herobrine_companion/mixin/epicfight/WomAntitheusLapseMixin");
        var redirect = mixin.methods.stream().filter(m -> m.name.equals("herobrineCompanion$resetOnlyOwnedSkillData")).findFirst().orElseThrow();
        check(redirect.desc.equals("(Lyesman/epicfight/skill/SkillDataManager;Lyesman/epicfight/skill/SkillDataKey;Ljava/lang/Object;" + argsDescriptor + ")V")
                && (redirect.access & Opcodes.ACC_STATIC) != 0, "Redirect descriptor does not capture WOM callback arguments");
        var config = Files.readString(Path.of("src/main/resources/herobrine_companion.mixins.json"));
        check(config.contains("epicfight.WomAntitheusLapseMixin"), "Ownership fix is not registered");

        var report = Map.of("status", "passed", "checks", checks, "wom", "2.0.171 / artifact 8632596",
                "animation", "wom:biped/skill/antitheus_lapse", "event_seconds", 1.75,
                "original_crash_reproduced", reproduced, "matched_state_writes", writes,
                "limitation", "Real EF data manager and installed WOM bytecode checked; no live Minecraft or network transport test.");
        Path output = Path.of("build/scythe_unity_pack_09/wom_lapse_ownership_validation.json");
        Files.createDirectories(output.getParent());
        Files.writeString(output, new GsonBuilder().setPrettyPrinting().create().toJson(report) + "\n");
        System.out.println("WOM_LAPSE_OWNERSHIP_PASSED " + checks + " checks, " + writes + " matching writes");
    }
}
