package com.whitecloud233.modid.herobrine_companion.client.gui;

import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.tinyfd.TinyFileDialogs;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

/** Native file dialog only; file reading and upload are separate services. */
final class TemplateFileChooser {
    static CompletableFuture<Path> choose(String title) {
        CompletableFuture<Path> result = new CompletableFuture<>();
        Thread thread = new Thread(() -> {
            try (MemoryStack stack = MemoryStack.stackPush()) {
                PointerBuffer filters = stack.mallocPointer(4);
                filters.put(stack.UTF8("*.nbt")).put(stack.UTF8("*.litematic")).put(stack.UTF8("*.schem")).put(stack.UTF8("*.schematic")).flip();
                String path = TinyFileDialogs.tinyfd_openFileDialog(title, "", filters, "Minecraft building templates", false);
                result.complete(path == null ? null : Path.of(path));
            } catch (Throwable error) { result.completeExceptionally(error); }
        }, "HerobrineTemplateFileChooser");
        thread.setDaemon(true); thread.start();
        return result;
    }
}
