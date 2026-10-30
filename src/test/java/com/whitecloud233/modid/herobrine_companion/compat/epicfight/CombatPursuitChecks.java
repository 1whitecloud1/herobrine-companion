package com.whitecloud233.modid.herobrine_companion.compat.epicfight;

import com.sun.source.tree.MethodTree;
import com.whitecloud233.modid.herobrine_companion.regression.SourceFixturesChecks;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

/** Executes production pursuit and combo predicates with inert world/animation inputs. */
public final class CombatPursuitChecks {
    private static final String BASE = "src/main/java/com/whitecloud233/modid/herobrine_companion/";

    public static void main(String[] args) throws Exception {
        Path root = args.length == 0 ? Path.of("") : Path.of(args[0]);
        String pursuit = members(root, "entity/ai/combat/HeroCombatPursuit.java", null);
        String chase = members(root, "compat/epicfight/HeroEpicFightChaseGoal.java", null);
        String nightfall = members(root, "compat/epicfight/HeroNightfallBehaviorGates.java",
                Set.of("canStartDefaultCombo", "canContinueDefaultCombo"));
        String playerLike = members(root, "compat/epicfight/HeroEpicFightPatch.java",
                Set.of("canStartPlayerLikeGroundCombo", "isPlayerLikeGroundState"));
        String movement = members(root, "entity/logic/HeroMovementState.java", Set.of("afterAiStep"));
        String harness;
        try (var stream = CombatPursuitChecks.class.getResourceAsStream("/regression/combat-pursuit.java.txt")) {
            if (stream == null) throw new AssertionError("Missing combat pursuit harness");
            harness = new String(stream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        }
        harness = harness.replace("/* PURSUIT */", pursuit).replace("/* CHASE */", chase)
                .replace("/* NIGHTFALL */", nightfall).replace("/* PLAYER_LIKE */", playerLike)
                .replace("/* MOVEMENT */", movement);
        try (var fixture = SourceFixturesChecks.compile("CombatPursuitProbe", harness)) {
            fixture.type().getMethod("verify").invoke(null);
        }
    }

    private static String members(Path root, String file, Set<String> methods) throws Exception {
        return SourceFixturesChecks.members(Files.readString(root.resolve(BASE + file)), tree ->
                methods == null || tree instanceof MethodTree method && methods.contains(method.getName().toString()));
    }
}
