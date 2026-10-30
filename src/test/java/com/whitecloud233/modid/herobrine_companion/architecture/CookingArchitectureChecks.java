package com.whitecloud233.modid.herobrine_companion.architecture;

import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.ImportTree;
import com.sun.source.tree.LiteralTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.NewClassTree;
import com.sun.source.util.JavacTask;
import com.sun.source.util.TreeScanner;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaFileObject;
import javax.tools.ToolProvider;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class CookingArchitectureChecks {
    private static final Set<String> COOKWARE_ACTIONS = Set.of(
            "onShovelHit", "onPlaceOil", "addIngredient", "addSoupBase", "onLitClick",
            "onPutItem", "onCutItem", "takeFood", "placeFood", "takeOutProduct", "takeTeapot", "addTeaFluid");

    public static void main(String[] args) throws Exception {
        Path root = Path.of(args.length == 0 ? "." : args[0]).toAbsolutePath().normalize();
        Path sourceRoot = root.resolve("src/main/java/com/whitecloud233/modid/herobrine_companion");
        List<Path> files;
        try (var paths = Files.walk(sourceRoot)) {
            files = paths.filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> relevant(sourceRoot.relativize(path).toString().replace('\\', '/'))).toList();
        }
        List<String> failures = new ArrayList<>();
        List<String> backendOrder = new ArrayList<>();
        Set<String> bootCalls = new HashSet<>();
        int[] cookwareActorChecks = {0};
        var diagnostics = new DiagnosticCollector<JavaFileObject>();
        var compiler = ToolProvider.getSystemJavaCompiler();
        try (var manager = compiler.getStandardFileManager(diagnostics, null, StandardCharsets.UTF_8)) {
            JavacTask parser = (JavacTask) compiler.getTask(null, manager, diagnostics,
                    List.of("-proc:none", "--release", "17"), null, manager.getJavaFileObjectsFromPaths(files));
            for (CompilationUnitTree unit : parser.parse()) {
                String file = sourceRoot.relativize(Path.of(unit.getSourceFile().toUri())).toString().replace('\\', '/');
                boolean domain = file.startsWith("entity/cooking/");
                boolean facade = file.equals("compat/cooking/HeroCookingCompat.java");
                boolean bootstrap = file.equals("compat/cooking/CookingCompatBootstrap.java");
                boolean kaleidoscope = file.startsWith("compat/kaleidoscope/");
                boolean kitchenFacade = file.equals("compat/kaleidoscope/HeroKaleidoscopeCompat.java");
                boolean kitchenCore = kitchenFacade || file.endsWith("/KitchenSession.java") || file.endsWith("/KitchenRecipes.java");
                Set<String> calls = new HashSet<>();
                new TreeScanner<Void, Void>() {
                    @Override public Void visitMemberSelect(MemberSelectTree tree, Void ignored) {
                        String name = tree.toString();
                        if (kitchenCore && name.startsWith("com.github.ysbbbbbb.kaleidoscopecookery.")) {
                            failures.add(file + " eagerly depends on optional cookware implementation: " + name);
                        }
                        if (domain && (name.contains(".compat.") || name.startsWith("net.minecraft.client.")
                                || name.startsWith("net.minecraftforge.client.") || name.startsWith("java.lang.reflect.")
                                || name.equals("net.minecraftforge.fml.ModList"))) {
                            failures.add(file + " leaks backend/UI implementation: " + name);
                        }
                        if (!facade && !file.startsWith("compat/farmersdelight/") && !file.startsWith("compat/kaleidoscope/")
                                && name.contains("HeroCookingCompat")) failures.add(file + " calls legacy cooking facade");
                        return super.visitMemberSelect(tree, ignored);
                    }
                    @Override public Void visitMethodInvocation(MethodInvocationTree tree, Void ignored) {
                        String name = tree.getMethodSelect().toString();
                        calls.add(name);
                        if (kaleidoscope
                                && tree.getMethodSelect() instanceof MemberSelectTree method
                                && COOKWARE_ACTIONS.contains(method.getIdentifier().toString())) {
                            cookwareActorChecks[0]++;
                            if (tree.getArguments().size() < 2 || !tree.getArguments().get(1).toString().equals("hero")) {
                                failures.add("Cookware effects must target Hero, not the ingredient owner: " + tree);
                            }
                        }
                        if (kaleidoscope
                                && name.equals("withTemporaryShift") && !tree.getArguments().get(0).toString().equals("hero")) {
                            failures.add("Cooking must not change the owner's sneaking state");
                        }
                        if (file.equals("HerobrineCompanion.java")) bootCalls.add(name);
                        if ((facade || file.endsWith("CookingBackend.java")) && (name.endsWith(".heal")
                                || name.endsWith(".onGift") || name.endsWith(".inputMonsterEmpathy"))) {
                            failures.add(file + " owns cooking sharing rules");
                        }
                        return super.visitMethodInvocation(tree, ignored);
                    }
                    @Override public Void visitLiteral(LiteralTree tree, Void ignored) {
                        if (domain && tree.getValue() instanceof String value && (value.contains("Kaleidoscope")
                                || value.equals("farmersdelight") || value.equals("kaleidoscope_cookery"))) {
                            failures.add(file + " hard-codes a specific cooking backend");
                        }
                        return super.visitLiteral(tree, ignored);
                    }
                    @Override public Void visitNewClass(NewClassTree tree, Void ignored) {
                        if (bootstrap && tree.getIdentifier().toString().endsWith("CookingBackend")) backendOrder.add(tree.getIdentifier().toString());
                        return super.visitNewClass(tree, ignored);
                    }
                    @Override public Void visitMethod(MethodTree tree, Void ignored) {
                        if (kitchenFacade && tree.getBody() != null && tree.getBody().getStatements().size() > 3) {
                            failures.add("Cookery public entry point must only guard and delegate: " + tree.getName());
                        }
                        if (facade && tree.getModifiers().getFlags().contains(javax.lang.model.element.Modifier.STATIC)
                                && tree.getBody() != null && tree.getBody().getStatements().size() != 1) {
                            failures.add("Legacy facade must only delegate: " + tree.getName());
                        }
                        return super.visitMethod(tree, ignored);
                    }
                }.scan(unit, null);
                if (file.equals("compat/cooking/SelectCookOptionPacket.java") && !calls.contains("CookingService.requestCookSelection")) {
                    failures.add("Cooking selection packet bypasses service authorization");
                }
                if (file.equals("entity/logic/data/HeroLogic.java") && !calls.contains("CookingService.requestCookSelection")) {
                    failures.add("Legacy selection entry bypasses service authorization");
                }
                if (file.equals("compat/kaleidoscope/KitchenStations.java")
                        && !calls.contains("KitchenActorInteraction.perform")) {
                    failures.add("Cookware must deliver the Hero's returned items to the owner");
                }
            }
        }
        for (var diagnostic : diagnostics.getDiagnostics()) if (diagnostic.getKind() == Diagnostic.Kind.ERROR) failures.add(diagnostic.toString());
        if (!backendOrder.equals(List.of("FarmersDelightCookingBackend", "KaleidoscopeCookingBackend"))) failures.add("Cooking backend priority changed: " + backendOrder);
        if (bootCalls.stream().noneMatch(name -> name.endsWith("CookingCompatBootstrap.registerBackends"))) failures.add("Cooking backends are not registered at mod bootstrap");
        if (files.isEmpty()) failures.add("No cooking sources checked");
        if (cookwareActorChecks[0] < 18) failures.add("Missing cookware actor checks: " + cookwareActorChecks[0]);
        if (!failures.isEmpty()) throw new AssertionError(String.join("\n", failures));
        System.out.println("CookingArchitectureChecks: " + files.size() + " cooking source boundaries and "
                + cookwareActorChecks[0] + " cookware actor checks passed");
    }

    private static boolean relevant(String file) {
        return file.startsWith("entity/cooking/") || file.startsWith("compat/cooking/")
                || file.startsWith("compat/kaleidoscope/")
                || file.equals("entity/logic/data/HeroLogic.java") || file.equals("HerobrineCompanion.java")
                || file.equals("entity/ai/learning/HeroDomesticActivityGoal.java") || file.equals("entity/ai/learning/HeroInvitedActionGoal.java")
                || file.equals("network/NetworkClientBridge.java") || file.equals("client/network/ClientUiDispatch.java")
                || file.equals("client/model/HeroModel.java") || file.equals("client/render/HeroHeldItemLayer.java")
                || file.equals("entity/logic/HeroInvitationHelper.java") || file.equals("item/HeroSummonItem.java");
    }
}
