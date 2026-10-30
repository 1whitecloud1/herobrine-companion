package com.whitecloud233.modid.herobrine_companion.client.gui;

import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

/** 文件选择只返回用户明确选定的路径，解析与保存由独立服务负责。 */
final class MemoryFileChooser {
    private MemoryFileChooser() { }

    static CompletableFuture<Path> choose(String title) {
        CompletableFuture<Path> result = new CompletableFuture<>();
        Thread thread = new Thread(() -> {
            try (MemoryStack stack = MemoryStack.stackPush()) {
                PointerBuffer filters = stack.mallocPointer(3);
                filters.put(stack.UTF8("*.txt")).put(stack.UTF8("*.md")).put(stack.UTF8("*.json")).flip();
                String selected = TinyFileDialogs.tinyfd_openFileDialog(title, "", filters, "UTF-8 memory files", false);
                result.complete(selected == null ? null : Path.of(selected));
            } catch (Throwable error) { result.completeExceptionally(error); }
        }, "HerobrineMemoryFileChooser");
        thread.setDaemon(true);
        thread.start();
        return result;
    }
}
