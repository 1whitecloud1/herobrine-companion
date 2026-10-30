package com.whitecloud233.herobrine_companion.regression;

import com.sun.source.tree.MethodTree;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;

/** Runs actual protection transitions and End Ring movement with inert game inputs. */
public final class ProtectionAndEndRingChecks {
    private static final String MOD = "com.whitecloud233.herobrine_companion";

    public static void main(String[] args) throws Exception {
        Path root = args.length == 0 ? Path.of("") : Path.of(args[0]);
        Path sourceRoot = root.resolve("src/main/java/" + MOD.replace('.', '/'));
        String fixture;
        try (var stream = ProtectionAndEndRingChecks.class.getResourceAsStream("/regression/protection-endring.java.txt")) {
            if (stream == null) throw new AssertionError("Missing protection/End Ring fixture");
            fixture = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
        Map<String, String> sources = Map.of(
                "PROTECTION", members(sourceRoot, "entity/logic/data/PeacefulProtection.java", null),
                "COMBAT", members(sourceRoot, "event/CombatMobsHandler.java", null),
                "CLIENT", members(sourceRoot, "client/network/ClientStateSync.java", Set.of("applyPeacefulProtection")),
                "ANCHOR", members(sourceRoot, "entity/logic/data/HeroDimensionHandler.java", Set.of("handleVoidProtection")),
                "INTRO", members(sourceRoot, "event/HeroEndringEvent.java", Set.of("runIntroSequence", "finishIntro")),
                "GRAVITY", members(sourceRoot, "entity/HeroEntity.java", Set.of("isNoGravity")),
                "CONTEXT", members(sourceRoot, "util/EndRingContext.java", null));
        for (var source : sources.entrySet()) {
            fixture = fixture.replace("/* " + source.getKey() + " */", source.getValue());
        }
        fixture = fixture.replace(MOD + ".entity.logic.data.PeacefulProtection", "PeacefulProtection")
                .replace(MOD + ".entity.ai.HeroMoveControl", "HeroMoveControl")
                .replace("net.minecraft.world.phys.Vec3", "Vec3");
        try (var compiled = SourceFixturesChecks.compile("ProtectionAndEndRingProbe", fixture)) {
            compiled.type().getMethod("verify").invoke(null);
        }
    }

    private static String members(Path root, String file, Set<String> methods) throws Exception {
        return SourceFixturesChecks.members(Files.readString(root.resolve(file)), tree -> methods == null
                || tree instanceof MethodTree method && methods.contains(method.getName().toString()));
    }
}
