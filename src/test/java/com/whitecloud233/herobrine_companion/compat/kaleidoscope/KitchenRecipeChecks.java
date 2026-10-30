package com.whitecloud233.herobrine_companion.compat.kaleidoscope;

import com.whitecloud233.herobrine_companion.entity.cooking.IngredientMatcher;
import com.sun.source.tree.MethodTree;
import com.sun.source.util.JavacTask;
import com.sun.source.util.TreeScanner;
import javax.tools.ToolProvider;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;

public final class KitchenRecipeChecks {
    private static int checks;

    public static void main(String[] args) throws Exception {
        Predicate<String> beef = "beef"::equals;
        Predicate<String> meat = item -> item.equals("beef") || item.equals("pork");
        List<Predicate<String>> recipe = List.of(meat, beef);
        boolean[] used = new boolean[2];
        check(IngredientMatcher.matchExistingInputs(List.of("beef", "pork"), recipe, used, 0)
                && used[0] && used[1], "Existing inputs must backtrack to reserve the restrictive slot");
        used = new boolean[2];
        check(!IngredientMatcher.matchExistingInputs(List.of("beef", "carrot"), recipe, used, 0)
                && !used[0] && !used[1], "Rejected inputs must restore ingredient reservations");
        check(!IngredientMatcher.matchExistingInputs(List.of("beef", "beef", "beef"), recipe, new boolean[2], 0),
                "Two ingredient slots must reject three existing inputs");
        check(IngredientMatcher.matchExistingInputs(List.of(), recipe, new boolean[2], 0),
                "Empty cookware must not consume any ingredient reservations");

        for (int beefCount = 0; beefCount <= 3; beefCount++) {
            for (int porkCount = 0; porkCount <= 3; porkCount++) {
                int[] counts = {beefCount, porkCount};
                List<String> chosen = new ArrayList<>();
                boolean matched = IngredientMatcher.matchRemainingIngredients(recipe, List.of("beef", "pork"), counts, chosen, 0);
                check(matched == (beefCount >= 1 && beefCount + porkCount >= 2), "Incorrect inventory feasibility");
                if (matched) {
                    check(chosen.size() == 2 && meat.test(chosen.get(0)) && beef.test(chosen.get(1)),
                            "Selected stacks must satisfy their corresponding slots");
                    check(counts[0] >= 0 && counts[1] >= 0 && counts[0] + counts[1] == beefCount + porkCount - 2,
                            "Allocation must spend exactly two items without negative counts");
                } else {
                    check(chosen.isEmpty() && Arrays.equals(counts, new int[]{beefCount, porkCount}),
                            "An impossible allocation must restore all counts and choices");
                }
            }
        }
        int[] oneBeef = {1};
        List<String> chosen = new ArrayList<>();
        check(!IngredientMatcher.matchRemainingIngredients(List.of(beef, beef), List.of("beef"), oneBeef, chosen, 0)
                && oneBeef[0] == 1 && chosen.isEmpty(), "Duplicate ingredients must not reuse one item");
        check(IngredientMatcher.matchRemainingIngredients(List.of(), List.of("beef"), oneBeef, chosen, 0)
                && oneBeef[0] == 1 && chosen.isEmpty(), "A complete recipe must not request another ingredient");
        verifyOptionMapping();
        System.out.println("KitchenRecipeChecks: " + checks + " checks passed");
    }

    private static void verifyOptionMapping() throws Exception {
        // Execute the production method with inert value types: vanilla bootstrap needs a mod loader.
        Path source = Path.of("src/main/java").resolve(KitchenRecipeChecks.class.getPackageName().replace('.', '/'))
                .resolve("KitchenStations.java");
        var compiler = ToolProvider.getSystemJavaCompiler();
        String[] method = {null};
        try (var manager = compiler.getStandardFileManager(null, null, StandardCharsets.UTF_8)) {
            JavacTask task = (JavacTask) compiler.getTask(null, manager, null, List.of("-proc:none"), null,
                    manager.getJavaFileObjects(source));
            for (var unit : task.parse()) {
                new TreeScanner<Void, Void>() {
                    @Override public Void visitMethod(MethodTree tree, Void unused) {
                        if (tree.getName().contentEquals("mapOptions")) method[0] = tree.toString();
                        return super.visitMethod(tree, unused);
                    }
                }.scan(unit, null);
            }
        }
        check(method[0] != null, "Production option mapping is missing");
        String fixture = """
                import java.util.*;
                public class MappingProbe {
                    record Stack(int count) {
                        Stack copy() { return new Stack(count); }
                    }
                    record BackendCookOption(String recipeId, Stack result, int maxRepeatCount) {}
                    record CookOption(String recipeId, Stack result, int maxRepeatCount) {
                        CookOption { result = result.copy(); }
                    }
                    %s
                    public static void verify() {
                        if (!mapOptions(List.of()).isEmpty()) throw new AssertionError("Empty options changed");
                        var first = new BackendCookOption("first", new Stack(3), 2);
                        var second = new BackendCookOption("second", new Stack(7), 5);
                        var single = mapOptions(List.of(first));
                        if (single.size() != 1 || !single.get(0).recipeId().equals("first")
                                || single.get(0).maxRepeatCount() != 2 || single.get(0).result().count() != 3)
                            throw new AssertionError("Single option metadata changed");
                        var input = new ArrayList<>(List.of(second, first));
                        var multiple = mapOptions(input);
                        if (multiple.size() != 2 || !multiple.get(0).recipeId().equals("second")
                                || multiple.get(0).maxRepeatCount() != 5 || multiple.get(0).result().count() != 7
                                || !multiple.get(1).recipeId().equals("first"))
                            throw new AssertionError("Recipe count or order changed");
                        input.clear();
                        if (multiple.size() != 2 || multiple.get(0).result() == second.result())
                            throw new AssertionError("Mapped options alias the input");
                    }
                }
                """.formatted(method[0]);
        Path directory = Files.createTempDirectory("hc-option-mapping-");
        try {
            Path fixtureSource = directory.resolve("MappingProbe.java");
            Files.writeString(fixtureSource, fixture, StandardCharsets.UTF_8);
            check(compiler.run(null, null, null, "-proc:none", "--release", "17", "-encoding", "UTF-8", "-d", directory.toString(),
                    fixtureSource.toString()) == 0, "Could not compile production mapping fixture");
            try (var loader = new URLClassLoader(new URL[]{directory.toUri().toURL()}, ClassLoader.getPlatformClassLoader())) {
                Class.forName("MappingProbe", true, loader).getMethod("verify").invoke(null);
                checks++;
            }
        } finally {
            try (var paths = Files.walk(directory)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
            }
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        checks++;
    }
}
