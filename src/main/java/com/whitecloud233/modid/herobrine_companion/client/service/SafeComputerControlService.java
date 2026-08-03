package com.whitecloud233.modid.herobrine_companion.client.service;

import com.mojang.logging.LogUtils;
import com.whitecloud233.modid.herobrine_companion.client.gui.ComputerControlConfirmScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.fml.loading.FMLPaths;
import org.slf4j.Logger;

import java.io.IOException;
import java.awt.HeadlessException;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

final class SafeComputerControlService {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String TARGET_ENV = "HC_SAFE_TARGET";
    private static final String PROGRAM_ENV = "HC_SAFE_PROGRAM";
    private static final long PROCESS_TIMEOUT_SECONDS = 8L;
    private static final AtomicBoolean CONFIRMATION_PENDING = new AtomicBoolean(false);
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "Herobrine-Safe-Computer-Control");
        thread.setDaemon(true);
        return thread;
    });

    private SafeComputerControlService() {}

    static boolean isSupportedHost() {
        String osName = System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT);
        return osName.startsWith("windows") && resolveSystemProgram("System32", "cmd.exe") != null;
    }

    static CompletableFuture<Result> requestExecution(AIComputerControlSupport.ComputerAction action) {
        if (!LLMConfig.isComputerControlEnabled()) {
            return CompletableFuture.completedFuture(Result.of(Status.DISABLED, "disabled"));
        }
        if (!isSupportedHost()) {
            return CompletableFuture.completedFuture(Result.of(Status.UNSUPPORTED, "unsupported_host"));
        }
        if (action == null || action.action() == null) {
            return CompletableFuture.completedFuture(Result.of(Status.FAILED, "invalid_action"));
        }
        if (!CONFIRMATION_PENDING.compareAndSet(false, true)) {
            return CompletableFuture.completedFuture(Result.of(Status.BUSY, "confirmation_pending"));
        }

        CompletableFuture<Result> resultFuture = new CompletableFuture<>();
        resultFuture.whenComplete((result, error) -> CONFIRMATION_PENDING.set(false));
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.tell(() -> openConfirmation(minecraft, action, resultFuture));
        return resultFuture;
    }

    private static void openConfirmation(Minecraft minecraft, AIComputerControlSupport.ComputerAction action,
                                         CompletableFuture<Result> resultFuture) {
        if (minecraft.player == null || !LLMConfig.isComputerControlEnabled()) {
            resultFuture.complete(Result.of(Status.DISABLED, "client_unavailable"));
            return;
        }

        PreparedDisplay display;
        try {
            display = prepareDisplay(action);
        } catch (RuntimeException error) {
            LOGGER.warn("Rejected invalid safe computer-control display data", error);
            resultFuture.complete(Result.of(Status.FAILED, "invalid_action_data"));
            return;
        }

        Screen previousScreen = minecraft.screen;
        minecraft.setScreen(new ComputerControlConfirmScreen(
                previousScreen,
                display.description(),
                display.commandPreview(),
                approved -> {
                    if (!approved) {
                        LOGGER.info("Local computer action {} was cancelled by the player", action.action().id());
                        resultFuture.complete(Result.of(Status.CANCELLED, "player_cancelled"));
                        return;
                    }
                    CompletableFuture.supplyAsync(() -> execute(action), EXECUTOR)
                            .whenComplete((result, error) -> {
                                if (error != null) {
                                    LOGGER.error("Safe local computer action failed", error);
                                    resultFuture.complete(Result.of(Status.FAILED, "execution_exception"));
                                } else {
                                    resultFuture.complete(result);
                                }
                            });
                }
        ));
    }

    private static Result execute(AIComputerControlSupport.ComputerAction action) {
        if (!LLMConfig.isComputerControlEnabled()) {
            return Result.of(Status.DISABLED, "disabled_before_execution");
        }
        if (!isSupportedHost()) {
            return Result.of(Status.UNSUPPORTED, "unsupported_host");
        }

        try {
            Result result = switch (action.action()) {
                case OPEN_NOTEPAD -> launchSystemProgram(resolveSystemProgram("System32", "notepad.exe"), null);
                case OPEN_CALCULATOR -> launchSystemProgram(resolveSystemProgram("System32", "calc.exe"), null);
                case OPEN_CMD -> launchSystemProgram(resolveSystemProgram("System32", "cmd.exe"), null);
                case OPEN_PAINT -> launchSystemProgram(resolveSystemProgram("System32", "mspaint.exe"), null);
                case OPEN_TASK_MANAGER -> launchSystemProgram(resolveSystemProgram("System32", "Taskmgr.exe"), null);
                case OPEN_WORKSPACE -> openWorkspace();
                case CREATE_FOLDER -> createWorkspaceFolder(action.name());
                case CREATE_NOTE -> createWorkspaceNote(action.name(), action.content());
                case COPY_CLIPBOARD -> copyToClipboard(action.content());
            };
            LOGGER.info("Safe local computer action {} completed with status {}", action.action().id(), result.status());
            return result;
        } catch (Exception error) {
            LOGGER.error("Safe local computer action {} failed", action.action().id(), error);
            return Result.of(Status.FAILED, "io_error");
        }
    }

    private static Result openWorkspace() throws IOException, InterruptedException {
        Path workspace = ensureWorkspaceRoot();
        return launchSystemProgram(resolveSystemProgram("", "explorer.exe"), workspace);
    }

    private static Result createWorkspaceFolder(String name) throws IOException, InterruptedException {
        Path workspace = ensureWorkspaceRoot();
        Path target = resolveWorkspaceEntry(workspace, name);
        if (Files.exists(target, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(target)) {
            return Result.of(Status.FAILED, "target_exists");
        }

        Map<String, String> environment = new HashMap<>();
        environment.put(TARGET_ENV, target.toString());
        boolean success = runCmd("mkdir \"%" + TARGET_ENV + "%\"", environment);
        return success && Files.isDirectory(target, LinkOption.NOFOLLOW_LINKS) && !Files.isSymbolicLink(target)
                ? Result.of(Status.SUCCESS, "folder_created")
                : Result.of(Status.FAILED, "folder_create_failed");
    }

    private static Result createWorkspaceNote(String requestedName, String content) throws IOException, InterruptedException {
        Path workspace = ensureWorkspaceRoot();
        String fileName = requestedName.toLowerCase(java.util.Locale.ROOT).endsWith(".txt")
                ? requestedName
                : requestedName + ".txt";
        Path target = resolveWorkspaceEntry(workspace, fileName);
        if (Files.exists(target, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(target)) {
            return Result.of(Status.FAILED, "target_exists");
        }
        Files.writeString(target, content, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);

        Result launchResult = launchSystemProgram(resolveSystemProgram("System32", "notepad.exe"), target);
        return launchResult.status() == Status.SUCCESS
                ? Result.of(Status.SUCCESS, "note_created")
                : Result.of(Status.FAILED, "note_created_but_not_opened");
    }

    private static Result copyToClipboard(String content) {
        try {
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(content), null);
            return Result.of(Status.SUCCESS, "clipboard_updated");
        } catch (HeadlessException | IllegalStateException | SecurityException error) {
            LOGGER.warn("The local clipboard was unavailable for the approved safe computer action", error);
            return Result.of(Status.FAILED, "clipboard_unavailable");
        }
    }

    private static Result launchSystemProgram(Path program, Path target) throws IOException, InterruptedException {
        if (program == null || !Files.isRegularFile(program)) {
            return Result.of(Status.FAILED, "program_unavailable");
        }
        Map<String, String> environment = new HashMap<>();
        environment.put(PROGRAM_ENV, program.toString());
        String command = "start \"\" /b \"%" + PROGRAM_ENV + "%\"";
        if (target != null) {
            environment.put(TARGET_ENV, target.toString());
            command += " \"%" + TARGET_ENV + "%\"";
        }
        return runCmd(command, environment)
                ? Result.of(Status.SUCCESS, "program_started")
                : Result.of(Status.FAILED, "program_start_failed");
    }

    private static boolean runCmd(String generatedCommand, Map<String, String> environment)
            throws IOException, InterruptedException {
        Path cmd = resolveSystemProgram("System32", "cmd.exe");
        if (cmd == null) {
            return false;
        }

        ProcessBuilder builder = new ProcessBuilder(cmd.toString(), "/d", "/s", "/c", generatedCommand);
        builder.redirectOutput(ProcessBuilder.Redirect.DISCARD);
        builder.redirectError(ProcessBuilder.Redirect.DISCARD);
        if (environment != null) {
            builder.environment().putAll(environment);
        }

        Process process = builder.start();
        process.getOutputStream().close();

        if (!process.waitFor(PROCESS_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            process.destroy();
            if (!process.waitFor(1L, TimeUnit.SECONDS)) {
                process.destroyForcibly();
            }
            return false;
        }
        return process.exitValue() == 0;
    }

    private static PreparedDisplay prepareDisplay(AIComputerControlSupport.ComputerAction action) {
        Path workspace = getWorkspaceRoot();
        return switch (action.action()) {
            case OPEN_NOTEPAD -> new PreparedDisplay(
                    Component.translatable("gui.herobrine_companion.computer_control.action.open_notepad"),
                    "cmd.exe /d /s /c start notepad.exe");
            case OPEN_CALCULATOR -> new PreparedDisplay(
                    Component.translatable("gui.herobrine_companion.computer_control.action.open_calculator"),
                    "cmd.exe /d /s /c start calc.exe");
            case OPEN_CMD -> new PreparedDisplay(
                    Component.translatable("gui.herobrine_companion.computer_control.action.open_cmd"),
                    "cmd.exe /d /s /c start cmd.exe [no generated command]");
            case OPEN_PAINT -> new PreparedDisplay(
                    Component.translatable("gui.herobrine_companion.computer_control.action.open_paint"),
                    "cmd.exe /d /s /c start mspaint.exe");
            case OPEN_TASK_MANAGER -> new PreparedDisplay(
                    Component.translatable("gui.herobrine_companion.computer_control.action.open_task_manager"),
                    "cmd.exe /d /s /c start Taskmgr.exe");
            case OPEN_WORKSPACE -> new PreparedDisplay(
                    Component.translatable("gui.herobrine_companion.computer_control.action.open_workspace", workspace),
                    "cmd.exe /d /s /c start explorer.exe [isolated workspace]");
            case CREATE_FOLDER -> {
                Path target = resolveWorkspaceEntry(workspace, action.name());
                yield new PreparedDisplay(
                        Component.translatable("gui.herobrine_companion.computer_control.action.create_folder", target),
                        "cmd.exe /d /s /c mkdir [isolated workspace target]");
            }
            case CREATE_NOTE -> {
                String fileName = action.name().toLowerCase(java.util.Locale.ROOT).endsWith(".txt")
                        ? action.name()
                        : action.name() + ".txt";
                Path target = resolveWorkspaceEntry(workspace, fileName);
                yield new PreparedDisplay(
                        Component.translatable("gui.herobrine_companion.computer_control.action.create_note", target, summarize(action.content())),
                        "safe UTF-8 write; cmd.exe /d /s /c start notepad.exe [new note]");
            }
            case COPY_CLIPBOARD -> new PreparedDisplay(
                    Component.translatable("gui.herobrine_companion.computer_control.action.copy_clipboard", summarize(action.content())),
                    "safe local clipboard write [plain text only]");
        };
    }

    private static Path getWorkspaceRoot() {
        return FMLPaths.GAMEDIR.get().resolve("herobrine_ai_workspace").toAbsolutePath().normalize();
    }

    private static Path ensureWorkspaceRoot() throws IOException {
        Path gameRoot = FMLPaths.GAMEDIR.get().toAbsolutePath().normalize();
        Path workspace = getWorkspaceRoot();
        if (!workspace.startsWith(gameRoot)) {
            throw new IOException("The isolated workspace escaped the game directory");
        }
        Files.createDirectories(workspace);
        if (Files.isSymbolicLink(workspace)) {
            throw new IOException("The isolated workspace cannot be a symbolic link");
        }
        Path gameReal = gameRoot.toRealPath();
        Path workspaceReal = workspace.toRealPath();
        if (!workspaceReal.startsWith(gameReal)) {
            throw new IOException("The isolated workspace resolved outside the game directory");
        }
        return workspace;
    }

    private static Path resolveWorkspaceEntry(Path workspace, String name) {
        Path target = workspace.resolve(name).toAbsolutePath().normalize();
        if (!target.startsWith(workspace) || target.getParent() == null || !target.getParent().equals(workspace)) {
            throw new IllegalArgumentException("Target escaped the isolated Herobrine AI workspace");
        }
        return target;
    }

    private static Path resolveSystemProgram(String subdirectory, String fileName) {
        String systemRoot = System.getenv("SystemRoot");
        if (systemRoot == null || systemRoot.isBlank()) {
            return null;
        }
        try {
            Path root = Path.of(systemRoot).toAbsolutePath().normalize();
            Path program = subdirectory == null || subdirectory.isBlank()
                    ? root.resolve(fileName).normalize()
                    : root.resolve(subdirectory).resolve(fileName).normalize();
            return program.startsWith(root) && Files.isRegularFile(program) ? program : null;
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static String summarize(String content) {
        String normalized = content == null ? "" : content.replace('\r', ' ').replace('\n', ' ').trim();
        return normalized.length() <= 80 ? normalized : normalized.substring(0, 77) + "...";
    }

    enum Status {
        SUCCESS,
        CANCELLED,
        FAILED,
        DISABLED,
        UNSUPPORTED,
        BUSY
    }

    record Result(Status status, String detail) {
        static Result of(Status status, String detail) {
            return new Result(status, detail == null ? "" : detail);
        }
    }

    private record PreparedDisplay(Component description, String commandPreview) {}
}
