package com.whitecloud233.modid.herobrine_companion.client.gui;

import org.lwjgl.util.tinyfd.TinyFileDialogs;

import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

/**
 * 目录选择器：调用操作系统原生文件夹对话框（LWJGL TinyFileDialogs，与皮肤选择同一套实现），
 * 在后台线程弹出，不阻塞 Minecraft 渲染线程。
 * 用于让玩家自主选择本地模型（引擎 + 模型 GGUF）的下载位置。
 */
public final class DirectoryChooser {

    private DirectoryChooser() {
    }

    /**
     * 弹出操作系统原生目录选择框（异步）。
     *
     * @param title            对话框标题
     * @param initialDirectory 初始目录；为 null 时使用系统默认
     * @return 用户选择的目录；取消或对话框不可用时返回 null
     */
    public static CompletableFuture<Path> chooseDirectoryAsync(String title, Path initialDirectory) {
        CompletableFuture<Path> future = new CompletableFuture<>();
        Thread thread = new Thread(() -> {
            Path result = null;
            try {
                String defaultPath = initialDirectory == null ? "" : initialDirectory.toString();
                String selected = TinyFileDialogs.tinyfd_selectFolderDialog(title, defaultPath);
                if (selected != null && !selected.isBlank()) {
                    result = Path.of(selected);
                }
            } catch (Throwable ignored) {
                // 对话框不可用（缺库/无窗口环境）时按取消处理
            }
            future.complete(result);
        }, "herobrine-directory-chooser");
        thread.setDaemon(true);
        thread.start();
        return future;
    }
}
