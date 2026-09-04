package com.whitecloud233.herobrine_companion.config;

import com.whitecloud233.herobrine_companion.client.gui.DirectoryChooser;
import com.whitecloud233.herobrine_companion.client.gui.HeroScreen;
import com.whitecloud233.herobrine_companion.client.service.LLMConfig;
import com.whitecloud233.herobrine_companion.client.service.LocalModelConnector;
import com.whitecloud233.herobrine_companion.client.service.LocalModelLauncher;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 本地模型管理页（独立页面）。
 *
 * <p>职责：
 * <ol>
 *   <li>启用/关闭开关：启用 = 下载缺失文件 → 启动 llama-server → 写入本地槽位与聊天路由；
 *       关闭 = 结束 llama 进程（含 8090 端口残留进程）并清除本地配置，对话回到云端/全局。</li>
 *   <li>模型目录：1.5B / 3B / 7B(Q4·Q5·Q8) / 14B，点击一行 = 选择该档位并下载启用；已下载的档位点击即切换。</li>
 *   <li>按玩家实际显卡/内存给出"适合哪个参数"的推荐与逐档适配说明。</li>
 *   <li>下载进度实时显示（进度条 + 百分比 + 速度）。</li>
 * </ol>
 *
 * <p>布局自适应：底部状态区固定，中部目录区弹性伸缩，任何窗口高度都至少展示 3 行
 * 并给出可拖拽滚动条（拖拽滑块 / 点击轨道跳页 / 鼠标滚轮）。文件就绪状态带
 * 4 秒 TTL 缓存（动作完成后立即失效），避免每帧对 GGUF 反复做磁盘校验导致界面卡顿。</p>
 */
public class LocalModelScreen extends Screen {
    private static final int MAX_PANEL_WIDTH = 560;
    private static final int MIN_PANEL_HEIGHT = 260;
    private static final int MAX_PANEL_HEIGHT = 440;
    private static final int BUTTON_HEIGHT = 20;
    private static final int ROW_HEIGHT = 26;
    private static final int CONTROL_GAP = 8;
    private static final long FILE_STATE_TTL_MS = 4_000L; // 文件就绪状态缓存时长
    private static final int COL_BG = 0xFF2B2B2B;
    private static final int COL_BORDER = 0xFF555555;
    private static final int COL_TITLE = 0xFFCC7832;
    private static final int COL_TEXT = 0xFFA9B7C6;
    private static final int COL_INFO = 0xFF6A8759;
    private static final int COL_WARN = 0xFFFF8080;
    private static final int COL_GOOD = 0xFF80FF80;
    private static final int COL_LINK = 0xFF9FD2FF;
    private static final int COL_ROW_BG = 0xFF202020;
    private static final int COL_ROW_CURRENT = 0xFF1F3A24;
    private static final int COL_ROW_HOVER = 0xFF2A2A31;

    private final Screen lastScreen;

    private HeroScreen.ThemedButton toggleButton;
    private HeroScreen.ThemedButton downloadButton;
    private HeroScreen.ThemedButton dirButton;
    private boolean busy = false;
    private boolean progressVisible = false;
    private double progressFraction = 0.0D;
    private String progressLabel = "";
    private long lastProgressBytes = 0L;
    private long lastProgressNanos = 0L;
    private double speedBytesPerSecond = 0.0D;
    private Component status = Component.literal("");
    private int statusColor = 0xAAAAAA;
    private boolean serverRunning = false;
    /** 按钮文案脏检查（tick 每帧调用，避免每帧重建 Component）。 */
    private String lastToggleText = "";
    private Boolean lastDownloadActive = null;

    private int catalogScroll = 0;
    private boolean draggingScrollbar = false;
    private double dragGrabOffsetY = 0.0D;
    private final List<int[]> rowAreas = new ArrayList<>(); // {x,y,w,h,entryIndex}

    // 性能缓存：文件就绪状态（TTL）+ 硬件文案（会话内不变，只算一次）
    private final Map<String, Boolean> filePresence = new HashMap<>();
    private final Map<String, Long> filePresenceAt = new HashMap<>();
    /** 已提交后台校验但还没回结果的档位（避免每帧重复提交同一档位）。 */
    private final Map<String, Long> filePresenceInflight = new HashMap<>();
    private boolean textsBuilt = false;
    private boolean textsBuilding = false;
    private String gpuText = "";
    private String recText = "";
    /** 每行右态文案缓存：渲染每帧都会重算，文案只依赖档位与固定状态，可长期复用。 */
    private final Map<String, String> rowTextCache = new HashMap<>();

    /**
     * 页面专属的后台探测线程（daemon，随游戏退出）。
     *
     * <p>GGUF 就绪校验要解析 GB 级文件的头部（tokenizer 词表十几万条），显卡探测要跑
     * nvidia-smi（最多 4 秒）。这两件事绝不能落在渲染线程上，否则界面会周期性卡住。</p>
     */
    private static final java.util.concurrent.ExecutorService PROBE_EXECUTOR =
            java.util.concurrent.Executors.newSingleThreadExecutor(runnable -> {
                Thread thread = new Thread(runnable, "herobrine-localmodel-probe");
                thread.setDaemon(true);
                return thread;
            });

    public LocalModelScreen(Screen lastScreen) {
        super(Component.translatable("gui.herobrine_companion.local_model.title"));
        this.lastScreen = lastScreen;
        this.serverRunning = LocalModelLauncher.isServerRunningCached();
    }

    @Override
    protected void init() {
        super.init();
        LLMConfig.ensureLoaded();
        // 后台预热：显卡探测与 GGUF 校验都不在渲染线程上做（首次打开页面也不卡）
        this.warmUpFileStates();
        this.ensureTextsBuilt();
        LocalModelLauncher.probeServerRunningAsync().thenAccept(running -> {
            if (this.minecraft != null) {
                this.minecraft.execute(() -> {
                    if (this.minecraft.screen == this) {
                        this.serverRunning = running;
                    }
                });
            }
        });

        int screenMargin = 8;
        int panelWidth = Math.max(1, Math.min(MAX_PANEL_WIDTH, this.width - screenMargin * 2));
        int panelLeft = Math.max(screenMargin, (this.width - panelWidth) / 2);
        int panelHeight = clamp(this.height - screenMargin * 2, MIN_PANEL_HEIGHT, MAX_PANEL_HEIGHT);
        int panelTop = Math.max(screenMargin, (this.height - panelHeight) / 2);
        // 操作行：启用/关闭 · 下载所选 · 更改位置 · 返回
        int toggleWidth = 120;
        int downloadWidth = 100;
        int dirWidth = 80;
        int backWidth = 60;
        int actionGroupWidth = toggleWidth + CONTROL_GAP + downloadWidth + CONTROL_GAP + dirWidth + CONTROL_GAP + backWidth;
        int actionX = panelLeft + (panelWidth - actionGroupWidth) / 2;
        int buttonY = panelTop + panelHeight - 36;

        this.toggleButton = this.addRenderableWidget(new HeroScreen.ThemedButton(
                actionX, buttonY, toggleWidth, BUTTON_HEIGHT,
                this.getToggleMessage(),
                button -> this.onToggleClicked(),
                null));
        this.downloadButton = this.addRenderableWidget(new HeroScreen.ThemedButton(
                actionX + toggleWidth + CONTROL_GAP, buttonY, downloadWidth, BUTTON_HEIGHT,
                Component.translatable("gui.herobrine_companion.local_model.download_selected"),
                button -> this.onDownloadClicked(),
                Tooltip.create(Component.translatable("gui.herobrine_companion.local_model.download_tooltip"))));
        this.dirButton = this.addRenderableWidget(new HeroScreen.ThemedButton(
                actionX + toggleWidth + CONTROL_GAP + downloadWidth + CONTROL_GAP, buttonY, dirWidth, BUTTON_HEIGHT,
                Component.translatable("gui.herobrine_companion.local_model.change_dir"),
                button -> this.onChangeDirClicked(),
                Tooltip.create(Component.translatable("gui.herobrine_companion.local_model.dir_tooltip"))));
        this.addRenderableWidget(new HeroScreen.ThemedButton(
                actionX + toggleWidth + CONTROL_GAP + downloadWidth + CONTROL_GAP + dirWidth + CONTROL_GAP,
                buttonY, backWidth, BUTTON_HEIGHT,
                Component.translatable("gui.herobrine_companion.local_model.back"),
                button -> Minecraft.getInstance().setScreen(this.lastScreen),
                null));
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private boolean isEnabled() {
        return LLMConfig.isLocalRouteActive();
    }

    private Component getToggleMessage() {
        if (this.busy) {
            return Component.translatable("gui.herobrine_companion.local_model.busy");
        }
        return Component.translatable(this.isEnabled()
                ? "gui.herobrine_companion.local_model.disable"
                : "gui.herobrine_companion.local_model.enable");
    }

    private void updateToggleState() {
        if (this.toggleButton != null) {
            // tick 每帧都会调用：只在文案真的变了才重建组件，避免每帧白造 Component
            Component message = this.getToggleMessage();
            String text = message.getString();
            if (!text.equals(this.lastToggleText)) {
                this.lastToggleText = text;
                this.toggleButton.setMessage(message);
            }
            this.toggleButton.active = !this.busy;
        }
        if (this.dirButton != null) {
            this.dirButton.active = !this.busy;
        }
        this.updateDownloadState();
    }

    /** 下载按钮：忙时或所选模型已下载（含 .part 续传中）时不可用。 */
    private void updateDownloadState() {
        if (this.downloadButton == null) {
            return;
        }
        LocalModelLauncher.CatalogEntry entry = this.selectedOrAutoEntry();
        boolean downloaded = entry != null && this.filesPresent(entry);
        boolean active = !this.busy && !downloaded;
        if (this.lastDownloadActive == null || this.lastDownloadActive != active) {
            this.lastDownloadActive = active;
            this.downloadButton.active = active;
            this.downloadButton.setMessage(Component.translatable(downloaded
                    ? "gui.herobrine_companion.local_model.downloaded_short"
                    : "gui.herobrine_companion.local_model.download_selected"));
        }
    }

    /** 当前所选档位；未手动选择时回落自动档位。 */
    private LocalModelLauncher.CatalogEntry selectedOrAutoEntry() {
        LocalModelLauncher.CatalogEntry choice = LocalModelLauncher.catalogEntry(LocalModelLauncher.modelChoiceKey());
        return choice != null ? choice : LocalModelLauncher.autoModelEntry();
    }

    // ---------- 状态缓存 ----------

    /**
     * 文件就绪状态（渲染用，绝不阻塞渲染线程）。
     *
     * <p>命中 TTL 缓存直接返回；没有结果或缓存过期时【交给后台线程】重算，本帧沿用上一次的结论
     * （从未算过时按"未下载"显示）。这样 GB 级 GGUF 的头部解析不会卡住渲染。</p>
     */
    private boolean filesPresent(LocalModelLauncher.CatalogEntry entry) {
        long now = System.currentTimeMillis();
        Boolean cached = this.filePresence.get(entry.key());
        Long checkedAt = this.filePresenceAt.get(entry.key());
        if (cached != null && checkedAt != null && now - checkedAt < FILE_STATE_TTL_MS) {
            return cached;
        }
        this.refreshFileStateAsync(entry, now);
        return cached != null && cached;
    }

    /**
     * 动作路径（点击下载/启用/切换档位）用：同步取准确结果。
     * 用户主动点击时阻塞几十毫秒无感知，但能保证"已下载/未下载"判断准确；
     * Launcher 侧有按"路径+大小+修改时间"的校验缓存，命中时开销可忽略。
     */
    private boolean filesPresentNow(LocalModelLauncher.CatalogEntry entry) {
        boolean present = LocalModelLauncher.entryFilesPresent(entry);
        this.filePresence.put(entry.key(), present);
        this.filePresenceAt.put(entry.key(), System.currentTimeMillis());
        return present;
    }

    /** 后台校验单个档位：同一档位 10 秒内不重复提交，结果回渲染线程写入缓存。 */
    private void refreshFileStateAsync(LocalModelLauncher.CatalogEntry entry, long now) {
        Long inflight = this.filePresenceInflight.get(entry.key());
        if (inflight != null && now - inflight < 10_000L) {
            return;
        }
        this.filePresenceInflight.put(entry.key(), now);
        PROBE_EXECUTOR.submit(() -> {
            String key = entry.key();
            try {
                boolean present = LocalModelLauncher.entryFilesPresent(entry);
                if (this.minecraft != null) {
                    this.minecraft.execute(() -> {
                        this.filePresence.put(key, present);
                        this.filePresenceAt.put(key, System.currentTimeMillis());
                        this.filePresenceInflight.remove(key);
                    });
                } else {
                    this.filePresenceInflight.remove(key);
                }
            } catch (Throwable ignored) {
                this.filePresenceInflight.remove(key);
            }
        });
    }

    /** 任何可能改变文件集合的动作完成后调用，强制下次渲染重新校验。 */
    private void invalidateFileStates() {
        this.filePresence.clear();
        this.filePresenceAt.clear();
        this.filePresenceInflight.clear();
    }

    /** 打开页面时预热：后台把每个档位的文件状态算一遍，首屏之后所有查询都命中缓存。 */
    private void warmUpFileStates() {
        long now = System.currentTimeMillis();
        for (LocalModelLauncher.CatalogEntry entry : LocalModelLauncher.catalog()) {
            this.refreshFileStateAsync(entry, now);
        }
    }

    /** 渲染期文案缓存：同一键只计算一次（翻译查找与字符串格式化每帧重算没有意义）。 */
    private String cachedRowText(String cacheKey, java.util.function.Supplier<String> supplier) {
        String cached = this.rowTextCache.get(cacheKey);
        if (cached == null) {
            cached = supplier.get();
            this.rowTextCache.put(cacheKey, cached);
        }
        return cached;
    }

    /**
     * 显卡/推荐文案：会话内硬件与内存不变，只计算一次。
     *
     * <p>探测要跑 nvidia-smi（最坏 4 秒，且首次会连跑两次拿型号与显存），因此放到后台线程：
     * 探测完成前文案为空、界面照常响应，探测完成后自动回填。</p>
     */
    private void ensureTextsBuilt() {
        if (this.textsBuilt || this.textsBuilding) {
            return;
        }
        this.textsBuilding = true;
        PROBE_EXECUTOR.submit(() -> {
            String gpu;
            String rec;
            LocalModelLauncher.CatalogEntry recommended = LocalModelLauncher.recommendedEntry();
            if (LocalModelLauncher.supportsGpu()) {
                long vram = LocalModelLauncher.gpuVramMb();
                gpu = Component.translatable("gui.herobrine_companion.local_model.gpu_line",
                        LocalModelLauncher.gpuName(), vram).getString();
                rec = Component.translatable("gui.herobrine_companion.local_model.recommend_body",
                        recommended == null ? "-" : recommended.displayName(),
                        this.recommendReason(recommended)).getString();
            } else {
                gpu = Component.translatable("gui.herobrine_companion.local_model.gpu_none").getString();
                long ram = systemRamMb();
                rec = Component.translatable("gui.herobrine_companion.local_model.recommend_cpu",
                        recommended == null ? "-" : recommended.displayName(),
                        recommended == null ? "-" : this.recommendReason(recommended),
                        ram <= 0L ? "?" : String.valueOf(ram / 1024L)).getString();
            }
            if (this.minecraft != null) {
                this.minecraft.execute(() -> {
                    this.gpuText = gpu;
                    this.recText = rec;
                    this.textsBuilt = true;
                    this.textsBuilding = false;
                });
            }
        });
    }

    // ---------- 动作 ----------

    private void onToggleClicked() {
        if (this.busy) {
            return;
        }
        if (this.isEnabled()) {
            this.disableLocalModel();
        } else {
            this.enableLocalModel();
        }
    }

    /** 启用：文件未下载时不自动下载（下载由【下载所选模型】主动触发），提示玩家先下载。 */
    private void enableLocalModel() {
        LocalModelLauncher.CatalogEntry entry = this.selectedOrAutoEntry();
        if (entry == null || !this.filesPresentNow(entry)) {
            this.invalidateFileStates();
            this.setStatus(Component.translatable("gui.herobrine_companion.local_model.need_download"), COL_WARN);
            return;
        }
        if (!LocalModelLauncher.hasCustomRootDir()) {
            this.chooseDownloadDir(path -> {
                if (path != null) {
                    LocalModelLauncher.setCustomRootDir(path);
                    this.invalidateFileStates();
                    this.startServer();
                } else {
                    this.setStatus(Component.translatable("gui.herobrine_companion.local_model.dir_cancelled"), 0xAAAAAA);
                }
            });
            return;
        }
        this.startServer();
    }

    /** 仅启动服务器并写入本地槽位/路由（文件已确认就绪，prepareAsync 不会触发下载）。 */
    private void startServer() {
        this.busy = true;
        this.progressVisible = false;
        this.invalidateFileStates();
        this.setStatus(Component.translatable("gui.herobrine_companion.local_model.enabling"), 0xAAAAAA);
        this.updateToggleState();
        LocalModelLauncher.prepareAsync(this::onProgress).whenComplete((result, error) -> {
            if (this.minecraft != null) {
                this.minecraft.execute(() -> {
                    this.busy = false;
                    this.progressVisible = false;
                    this.invalidateFileStates();
                    this.updateToggleState();
                    if (this.minecraft.screen != this) {
                        return;
                    }
                    if (error != null || result == null) {
                        this.setStatus(Component.translatable("gui.herobrine_companion.local_model.error_prepare",
                                error == null ? "未知错误" : LocalModelLauncher.rootMessage(error)), COL_WARN);
                        return;
                    }
                    if (result.ready()) {
                        LocalModelConnector.applyConfig(result.endpoint(), result.modelId());
                        this.serverRunning = true;
                        this.setStatus(Component.translatable(
                                "gui.herobrine_companion.local_model.enabled_note",
                                result.modelId()), COL_GOOD);
                    } else {
                        this.setStatus(Component.translatable("gui.herobrine_companion.local_model.error_prepare",
                                result.message()), COL_WARN);
                    }
                });
            }
        });
    }

    /** 主动下载所选模型（引擎 + 模型分片，不启动服务器；支持断点续传）。 */
    private void onDownloadClicked() {
        if (this.busy) {
            return;
        }
        LocalModelLauncher.CatalogEntry entry = this.selectedOrAutoEntry();
        if (entry == null) {
            return;
        }
        if (this.filesPresentNow(entry)) {
            this.setStatus(Component.translatable("gui.herobrine_companion.local_model.already_downloaded",
                    entry.displayName()), COL_GOOD);
            return;
        }
        if (!LocalModelLauncher.hasCustomRootDir()) {
            this.chooseDownloadDir(path -> {
                if (path != null) {
                    LocalModelLauncher.setCustomRootDir(path);
                    this.invalidateFileStates();
                    this.startDownload(entry);
                } else {
                    this.setStatus(Component.translatable("gui.herobrine_companion.local_model.dir_cancelled"), 0xAAAAAA);
                }
            });
            return;
        }
        this.startDownload(entry);
    }

    private void startDownload(LocalModelLauncher.CatalogEntry entry) {
        this.busy = true;
        this.progressVisible = false;
        this.invalidateFileStates();
        this.setStatus(Component.translatable("gui.herobrine_companion.local_model.downloading_selected",
                entry.displayName()), 0xAAAAAA);
        this.updateToggleState();
        LocalModelLauncher.downloadSelectedAsync(this::onProgress).whenComplete((path, error) -> {
            if (this.minecraft != null) {
                this.minecraft.execute(() -> {
                    this.busy = false;
                    this.progressVisible = false;
                    this.invalidateFileStates();
                    this.updateToggleState();
                    if (this.minecraft.screen != this) {
                        return;
                    }
                    if (error != null) {
                        this.setStatus(Component.translatable("gui.herobrine_companion.local_model.download_failed",
                                LocalModelLauncher.rootMessage(error)), COL_WARN);
                        return;
                    }
                    if (!LocalModelLauncher.entryFilesPresent(entry)) {
                        this.setStatus(Component.translatable("gui.herobrine_companion.local_model.download_failed",
                                "文件校验未通过"), COL_WARN);
                        return;
                    }
                    this.setStatus(Component.translatable("gui.herobrine_companion.local_model.download_done",
                            entry.displayName()), COL_GOOD);
                });
            }
        });
    }

    /** 关闭：结束本模组进程 + 回收 8090 端口残留 llama 进程，并清除本地槽位与聊天路由。 */
    private void disableLocalModel() {
        this.busy = true;
        this.progressVisible = false;
        this.setStatus(Component.translatable("gui.herobrine_companion.local_model.disabling"), 0xAAAAAA);
        this.updateToggleState();
        LocalModelLauncher.stopServerAndReclaimPortAsync().whenComplete((ignored, error) -> {
            if (this.minecraft != null) {
                this.minecraft.execute(() -> {
                    this.busy = false;
                    this.serverRunning = false;
                    LLMConfig.clearLocalModel();
                    this.invalidateFileStates();
                    this.updateToggleState();
                    if (this.minecraft.screen == this) {
                        this.setStatus(Component.translatable("gui.herobrine_companion.local_model.disabled_note"),
                                COL_GOOD);
                    }
                });
            }
        });
    }

    private void onChangeDirClicked() {
        if (this.busy) {
            return;
        }
        Path initial = LocalModelLauncher.hasCustomRootDir() ? LocalModelLauncher.rootDir() : null;
        this.chooseDownloadDir(path -> {
            if (path != null) {
                LocalModelLauncher.setCustomRootDir(path);
                this.invalidateFileStates();
                this.setStatus(Component.translatable("gui.herobrine_companion.local_model.dir_line", path), COL_LINK);
            }
        });
    }

    private void chooseDownloadDir(java.util.function.Consumer<Path> callback) {
        this.setStatus(Component.translatable("gui.herobrine_companion.local_model.dir_prompt"), COL_LINK);
        Path initial = LocalModelLauncher.hasCustomRootDir() ? LocalModelLauncher.rootDir() : null;
        DirectoryChooser.chooseDirectoryAsync(
                Component.translatable("gui.herobrine_companion.local_model.dir_choose_title").getString(), initial)
                .thenAccept(path -> {
                    if (this.minecraft != null) {
                        this.minecraft.execute(() -> {
                            if (this.minecraft.screen == this) {
                                callback.accept(path);
                            }
                        });
                    }
                });
    }

    /** 点击某一行：仅选择该档位（不触发下载）。已下载的档位被选中且服务器在运行时，
     *  同步重启服务器加载新模型；未下载的档位由【下载所选模型】按钮主动下载。 */
    private void onRowClicked(int entryIndex) {
        if (this.busy) {
            return;
        }
        List<LocalModelLauncher.CatalogEntry> catalog = LocalModelLauncher.catalog();
        if (entryIndex < 0 || entryIndex >= catalog.size()) {
            return;
        }
        LocalModelLauncher.CatalogEntry entry = catalog.get(entryIndex);
        LocalModelLauncher.CatalogEntry currentChoice = LocalModelLauncher.catalogEntry(LocalModelLauncher.modelChoiceKey());
        boolean downloaded = this.filesPresentNow(entry);
        if (currentChoice != null && currentChoice.key().equalsIgnoreCase(entry.key())) {
            // 已是当前档位：无需切换；给个状态提示即可（区分已下载/未下载）。
            this.setStatus(Component.translatable(downloaded
                    ? "gui.herobrine_companion.local_model.selected_downloaded"
                    : "gui.herobrine_companion.local_model.selected", entry.displayName()),
                    downloaded ? COL_GOOD : COL_LINK);
            return;
        }
        LocalModelLauncher.setModelChoice(entry.key());
        this.invalidateFileStates();
        downloaded = this.filesPresentNow(entry);
        if (downloaded && this.serverRunning) {
            // 已下载且服务器在跑旧模型：重启加载新档位（无下载动作）
            this.busy = true;
            this.setStatus(Component.translatable("gui.herobrine_companion.local_model.switching",
                    entry.displayName()), 0xAAAAAA);
            this.updateToggleState();
            LocalModelLauncher.stopOwnedServer();
            LocalModelLauncher.prepareAsync(this::onProgress).whenComplete((result, error) -> {
                if (this.minecraft != null) {
                    this.minecraft.execute(() -> {
                        this.busy = false;
                        this.progressVisible = false;
                        this.invalidateFileStates();
                        this.updateToggleState();
                        if (this.minecraft.screen != this) {
                            return;
                        }
                        if (error != null || result == null || !result.ready()) {
                            this.setStatus(Component.translatable("gui.herobrine_companion.local_model.error_prepare",
                                    error != null ? LocalModelLauncher.rootMessage(error)
                                            : (result == null ? "未知错误" : result.message())), COL_WARN);
                            return;
                        }
                        LocalModelConnector.applyConfig(result.endpoint(), result.modelId());
                        this.serverRunning = true;
                        this.setStatus(Component.translatable(
                                "gui.herobrine_companion.local_model.switched", entry.displayName()), COL_GOOD);
                    });
                }
            });
            return;
        }
        if (downloaded) {
            this.setStatus(Component.translatable("gui.herobrine_companion.local_model.selected_downloaded",
                    entry.displayName()), COL_GOOD);
        } else {
            this.setStatus(Component.translatable("gui.herobrine_companion.local_model.selected",
                    entry.displayName()), COL_LINK);
        }
    }

    private void probeServer() {
        LocalModelLauncher.probeServerRunningAsync().thenAccept(running -> {
            if (this.minecraft != null) {
                this.minecraft.execute(() -> {
                    if (this.minecraft.screen == this) {
                        this.serverRunning = running;
                    }
                });
            }
        });
    }

    /** 下载/启动进度回调（工作线程触发，切回渲染线程更新 UI）。 */
    private void onProgress(LocalModelLauncher.DownloadProgress progress) {
        if (this.minecraft != null) {
            this.minecraft.execute(() -> {
                if (this.minecraft.screen != this) {
                    return;
                }
                this.progressVisible = true;
                this.progressFraction = progress.fraction();
                String label = progress.label() == null ? "" : progress.label();
                long now = System.nanoTime();
                if (!label.equals(this.progressLabel)
                        || progress.downloaded() < this.lastProgressBytes) {
                    this.progressLabel = label;
                    this.lastProgressBytes = progress.downloaded();
                    this.lastProgressNanos = now;
                    this.speedBytesPerSecond = 0.0D;
                } else if (this.lastProgressNanos > 0L) {
                    long elapsedNanos = now - this.lastProgressNanos;
                    if (elapsedNanos >= 200_000_000L) {
                        long deltaBytes = progress.downloaded() - this.lastProgressBytes;
                        double elapsedSeconds = elapsedNanos / 1_000_000_000.0D;
                        if (deltaBytes >= 0L && elapsedSeconds > 0.0D) {
                            double instantSpeed = deltaBytes / elapsedSeconds;
                            this.speedBytesPerSecond = this.speedBytesPerSecond <= 0.0D
                                    ? instantSpeed
                                    : this.speedBytesPerSecond * 0.65D + instantSpeed * 0.35D;
                        }
                        this.lastProgressBytes = progress.downloaded();
                        this.lastProgressNanos = now;
                    }
                }
                this.setStatus(this.buildProgressStatus(label, progress), COL_LINK);
            });
        }
    }

    private Component buildProgressStatus(String label, LocalModelLauncher.DownloadProgress progress) {
        boolean byteDownload = progress.total() >= 1_048_576L;
        if (byteDownload) {
            double doneMb = progress.downloaded() / 1_048_576.0D;
            double totalMb = progress.total() / 1_048_576.0D;
            String speed = this.speedBytesPerSecond > 0.0D
                    ? String.format(Locale.ROOT, " · %.1f MB/s", this.speedBytesPerSecond / 1_048_576.0D)
                    : " · 速度计算中...";
            return Component.literal(String.format(Locale.ROOT,
                    "%s %.1f / %.1f MB (%d%%)%s",
                    label, doneMb, totalMb, Math.round(progress.fraction() * 100), speed));
        }
        if (progress.total() > 0L) {
            return Component.literal(String.format(Locale.ROOT,
                    "%s (%d%%)", label, Math.round(progress.fraction() * 100)));
        }
        return Component.literal(label + "...");
    }

    private void setStatus(Component message, int color) {
        this.status = message == null ? Component.literal("") : message;
        this.statusColor = color;
    }

    /** 系统内存缓存（会话内不变；-1 = 还没探测过）。渲染每行都要用，不能每帧查一次 MXBean。 */
    private static volatile long cachedSystemRamMb = -1L;

    /** 系统内存（MB）；探测失败返回 0（= 未知）。 */
    private static long systemRamMb() {
        long cached = cachedSystemRamMb;
        if (cached >= 0L) {
            return cached;
        }
        long ram;
        try {
            com.sun.management.OperatingSystemMXBean bean =
                    (com.sun.management.OperatingSystemMXBean) java.lang.management.ManagementFactory.getOperatingSystemMXBean();
            ram = bean.getTotalPhysicalMemorySize() / 1_048_576L;
        } catch (Throwable ignored) {
            ram = 0L;
        }
        cachedSystemRamMb = ram;
        return ram;
    }

    // ---------- 渲染 ----------

    @Override
    public void tick() {
        super.tick();
        this.updateToggleState();
    }

    @Override
    public void renderBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        // 参照项目惯例（HeroScreen / ApiKeyInputScreen）：禁用原版自带的世界模糊和黑色遮罩
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.ensureTextsBuilt();

        int screenMargin = 8;
        int panelWidth = Math.max(1, Math.min(MAX_PANEL_WIDTH, this.width - screenMargin * 2));
        int panelLeft = Math.max(screenMargin, (this.width - panelWidth) / 2);
        int panelHeight = clamp(this.height - screenMargin * 2, MIN_PANEL_HEIGHT, MAX_PANEL_HEIGHT);
        int panelTop = Math.max(screenMargin, (this.height - panelHeight) / 2);
        int contentLeft = panelLeft + 16;
        int contentWidth = panelWidth - 32;
        int centerX = panelLeft + panelWidth / 2;
        int baseY = panelTop + panelHeight;

        guiGraphics.fill(panelLeft, panelTop, panelLeft + panelWidth, panelTop + panelHeight, COL_BG);
        guiGraphics.renderOutline(panelLeft, panelTop, panelWidth, panelHeight, COL_BORDER);
        guiGraphics.drawCenteredString(this.font, this.title, centerX, panelTop + 12, COL_TITLE);

        // ---- 信息区（显卡 + 推荐；窗口过矮时推荐块自动隐藏，保证目录区空间）----
        int contentTop = panelTop + 32;
        int recHeaderY = contentTop + 12;
        int recTextY = recHeaderY + 11;
        int candidateRowsTop = recTextY + 34; // 推荐文字 2 行 + 标题行 + 间距
        boolean showRec = (baseY - 100) - candidateRowsTop >= 3 * ROW_HEIGHT;
        int rowsTop = showRec ? candidateRowsTop : contentTop + 30;
        int rowsAreaHeight = Math.max(2 * ROW_HEIGHT, (baseY - 100) - rowsTop);

        guiGraphics.drawString(this.font, this.gpuText, contentLeft, contentTop, 0xFFFFFF, false);
        if (showRec) {
            guiGraphics.drawString(this.font,
                    Component.translatable("gui.herobrine_companion.local_model.recommend_title"),
                    contentLeft, recHeaderY, COL_INFO, false);
            // 若推荐文案超过 2 行，用裁剪只显示前 2 行，不侵占目录区
            guiGraphics.enableScissor(contentLeft, recTextY, contentLeft + contentWidth, recTextY + 20);
            guiGraphics.drawWordWrap(this.font, Component.literal(this.recText), contentLeft, recTextY,
                    contentWidth, COL_TEXT);
            guiGraphics.disableScissor();
        }

        // ---- 目录区（弹性区域：行高固定、滚动裁剪，绝不与状态区重叠）----
        String currentName = "-";
        LocalModelLauncher.CatalogEntry currentChoice = LocalModelLauncher.catalogEntry(LocalModelLauncher.modelChoiceKey());
        if (currentChoice == null) {
            currentChoice = LocalModelLauncher.autoModelEntry();
        }
        if (currentChoice != null) {
            currentName = currentChoice.displayName();
        }
        guiGraphics.drawString(this.font,
                Component.translatable("gui.herobrine_companion.local_model.catalog_title", currentName),
                contentLeft, rowsTop - 13, 0xFFFFFF, false);

        List<LocalModelLauncher.CatalogEntry> catalog = LocalModelLauncher.catalog();
        int totalRowsHeight = ROW_HEIGHT * catalog.size();
        int maxScroll = Math.max(0, totalRowsHeight - rowsAreaHeight);
        if (this.catalogScroll > maxScroll) {
            this.catalogScroll = maxScroll;
        }
        int rowWidth = maxScroll > 0 ? contentWidth - 14 : contentWidth;
        boolean overRows = mouseY >= rowsTop && mouseY < rowsTop + rowsAreaHeight;
        String recommendedKey = this.recommendedEntryKey();

        // 记录布局几何，供鼠标拖拽/点击/滚轮使用（与渲染使用同一组数据）
        this.lastRowsTop = rowsTop;
        this.lastRowsAreaHeight = rowsAreaHeight;
        this.lastContentLeft = contentLeft;
        this.lastContentWidth = contentWidth;

        this.rowAreas.clear();
        for (int i = 0; i < catalog.size(); i++) {
            int rowY = rowsTop + i * ROW_HEIGHT - this.catalogScroll;
            if (rowY + ROW_HEIGHT <= rowsTop || rowY >= rowsTop + rowsAreaHeight) {
                continue; // 滚动区外不绘制
            }
            LocalModelLauncher.CatalogEntry entry = catalog.get(i);
            boolean downloaded = this.filesPresent(entry);
            boolean isCurrent = currentChoice != null && currentChoice.key().equalsIgnoreCase(entry.key());
            boolean hovered = overRows && mouseX >= contentLeft && mouseX <= contentLeft + rowWidth
                    && mouseY >= rowY && mouseY < rowY + ROW_HEIGHT;

            if (hovered) {
                guiGraphics.fill(contentLeft, rowY, contentLeft + rowWidth, rowY + ROW_HEIGHT, COL_ROW_HOVER);
            } else {
                guiGraphics.fill(contentLeft, rowY, contentLeft + rowWidth, rowY + ROW_HEIGHT,
                        isCurrent ? COL_ROW_CURRENT : COL_ROW_BG);
            }
            // 右态文案只依赖"档位 + 固定状态"，先定状态再查缓存，避免每帧重复做翻译与格式化
            String tailState;
            int tailColor;
            if (isCurrent) {
                tailState = "current";
                tailColor = COL_GOOD;
            } else if (downloaded) {
                tailState = "downloaded";
                tailColor = COL_GOOD;
            } else if (!LocalModelLauncher.supportsGpu()) {
                // CPU 机器：按系统内存判定该档是否可跑（不再笼统提示"显存不足"）
                long ramMb = systemRamMb();
                if (entry.minVramMb() <= 0L) {
                    tailState = "cpu";
                } else if (ramMb > 0L && ramMb >= entry.minSystemRamMb()) {
                    tailState = "cpu_ram";
                } else {
                    tailState = "ram_no";
                }
                tailColor = "ram_no".equals(tailState) ? COL_WARN : COL_GOOD;
            } else if (this.fitsHardware(entry)) {
                tailState = "fit_ok";
                tailColor = COL_GOOD;
            } else {
                tailState = "fit_no";
                tailColor = COL_WARN;
            }
            String tail = this.cachedRowText("tail|" + tailState + "|" + entry.key(), () -> switch (tailState) {
                case "current" -> Component.translatable("gui.herobrine_companion.local_model.current_now").getString();
                case "downloaded" -> Component.translatable("gui.herobrine_companion.local_model.downloaded_click").getString();
                case "cpu" -> Component.translatable("gui.herobrine_companion.local_model.fit_cpu").getString();
                case "cpu_ram" -> Component.translatable("gui.herobrine_companion.local_model.fit_cpu_ram",
                        entry.minSystemRamMb() / 1024L).getString();
                case "ram_no" -> Component.translatable("gui.herobrine_companion.local_model.fit_ram_no",
                        formatGb(entry.minSystemRamMb())).getString();
                case "fit_ok" -> Component.translatable("gui.herobrine_companion.local_model.fit_ok").getString();
                default -> Component.translatable("gui.herobrine_companion.local_model.fit_no",
                        formatGb(entry.minVramMb())).getString();
            });
            String sizeText = this.cachedRowText("size|" + entry.key(), () -> String.format(Locale.ROOT,
                    "约 %.1f GB · %s | %s", entry.approxBytes() / 1_000_000_000.0D, entry.family(),
                    Component.translatable("gui.herobrine_companion.local_model.vram_need",
                            entry.minVramMb() <= 0L ? "-"
                                    : String.format(Locale.ROOT, "≥%dGB", entry.minVramMb() / 1024L)).getString()));
            // 右侧状态文字先画，左侧名称/大小按剩余宽度截断，避免窄窗口互相挤压
            guiGraphics.drawString(this.font, tail, contentLeft + rowWidth - this.font.width(tail) - 4,
                    rowY + 5, tailColor, false);
            int nameMaxWidth = Math.max(40, rowWidth - this.font.width(tail) - 28);
            String nameText = this.font.plainSubstrByWidth(entry.displayName(), nameMaxWidth);
            guiGraphics.drawString(this.font, nameText, contentLeft + 4, rowY + 3, 0xFFFFFF, false);
            int sizeMaxWidth = Math.max(40, rowWidth - 24);
            String sizeTextClipped = this.font.plainSubstrByWidth(sizeText, sizeMaxWidth);
            guiGraphics.drawString(this.font, sizeTextClipped, contentLeft + 4, rowY + 13, 0x9A9A9A, false);
            if (!isCurrent && entry.key().equals(recommendedKey)) {
                guiGraphics.drawString(this.font,
                        Component.translatable("gui.herobrine_companion.local_model.recommended_tag"),
                        contentLeft + 4 + this.font.width(nameText) + 8, rowY + 3, COL_LINK, false);
            }
            this.rowAreas.add(new int[]{contentLeft, rowY, rowWidth, ROW_HEIGHT, i});
        }

        // ---- 滚动条（可拖拽滑块 / 点击轨道跳页）----
        if (maxScroll > 0) {
            int trackX = contentLeft + contentWidth - 5;
            int thumbHeight = Math.max(10, (int) ((long) rowsAreaHeight * rowsAreaHeight / totalRowsHeight));
            int trackHeight = rowsAreaHeight;
            int thumbY = rowsTop + (trackHeight - thumbHeight) * this.catalogScroll / maxScroll;
            boolean thumbHovered = mouseX >= trackX - 2 && mouseX <= trackX + 6
                    && mouseY >= thumbY && mouseY < thumbY + thumbHeight;
            guiGraphics.fill(trackX, rowsTop, trackX + 4, rowsTop + trackHeight, 0xFF33343B);
            int thumbColor = this.draggingScrollbar ? 0xFFC8C8C8 : (thumbHovered ? 0xFFB8B8B8 : 0xFF8A8A8A);
            guiGraphics.fill(trackX, thumbY, trackX + 4, thumbY + thumbHeight, thumbColor);
        }

        // ---- 底部固定区（下载目录 / 进度条 / 状态 / 服务器状态）----
        String rootDirText = LocalModelLauncher.rootDir().toString();
        String dirLine = this.cachedRowText("dir|" + rootDirText, () -> Component.translatable(
                "gui.herobrine_companion.local_model.dir_line", rootDirText).getString());
        guiGraphics.drawString(this.font, this.font.plainSubstrByWidth(dirLine, contentWidth),
                contentLeft, baseY - 96, 0x8A8A8A, false);

        if (this.progressVisible) {
            int barWidth = Math.min(280, contentWidth);
            int barX = centerX - barWidth / 2;
            guiGraphics.fill(barX, baseY - 88, barX + barWidth, baseY - 81, 0xFF333333);
            int fill = (int) (barWidth * this.progressFraction);
            if (fill > 0) {
                guiGraphics.fill(barX, baseY - 88, barX + fill, baseY - 81, 0xFF66CC66);
            }
        }

        if (!this.status.getString().isBlank()) {
            // 状态文字最多 2 行，超出裁剪避免盖住服务器状态行
            guiGraphics.enableScissor(contentLeft, baseY - 78, contentLeft + contentWidth, baseY - 58);
            guiGraphics.drawWordWrap(this.font, this.status, contentLeft, baseY - 78, contentWidth, this.statusColor);
            guiGraphics.disableScissor();
        }
        boolean running = this.serverRunning;
        boolean enabled = this.isEnabled();
        String downgrade = LocalModelLauncher.gpuDowngradeReason();
        boolean downgraded = !downgrade.isBlank();
        String stateLine = this.cachedRowText("state|" + running + "|" + enabled + "|" + downgraded, () -> {
            String server = Component.translatable(running
                    ? "gui.herobrine_companion.local_model.server_running"
                    : "gui.herobrine_companion.local_model.server_stopped").getString();
            String state = Component.translatable(enabled
                    ? "gui.herobrine_companion.local_model.state_enabled"
                    : "gui.herobrine_companion.local_model.state_disabled").getString();
            String flag = downgraded
                    ? Component.translatable("gui.herobrine_companion.local_model.gpu_cpu_flag").getString() + " "
                    : "";
            return flag + server + "  ·  " + state;
        });
        guiGraphics.drawString(this.font, stateLine, contentLeft, baseY - 50,
                downgraded ? 0xFFD080 : 0xCFCFCF, false);
        if (downgraded && this.status.getString().isBlank()) {
            // 检测到显卡却降级 CPU：状态区空闲时常驻显示具体原因（下载/启动期间状态区让给进度）
            guiGraphics.drawString(this.font, this.font.plainSubstrByWidth(downgrade, contentWidth),
                    contentLeft, baseY - 62, 0xFFC080, false);
        }

        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    private String recommendedEntryKey() {
        LocalModelLauncher.CatalogEntry recommended = LocalModelLauncher.recommendedEntry();
        return recommended == null ? "" : recommended.key();
    }

    private boolean fitsHardware(LocalModelLauncher.CatalogEntry entry) {
        if (entry.minVramMb() <= 0L) {
            return true; // CPU 可运行
        }
        if (!LocalModelLauncher.supportsGpu()) {
            return false;
        }
        return LocalModelLauncher.gpuVramMb() >= entry.minVramMb();
    }

    private static String formatGb(long mb) {
        return String.format(Locale.ROOT, "%.1f GB", mb / 1024.0D);
    }

    /** 推荐理由：结合玩家显存/内存给出该档位的适配说明。 */
    private String recommendReason(LocalModelLauncher.CatalogEntry entry) {
        if (entry == null) {
            return "";
        }
        return switch (entry.key()) {
            case "qwen3-1b7" -> Component.translatable("gui.herobrine_companion.local_model.reason_qwen3_1b7").getString();
            case "qwen-3b-tuned" -> Component.translatable("gui.herobrine_companion.local_model.reason_3b").getString();
            case "qwen3-4b" -> Component.translatable("gui.herobrine_companion.local_model.reason_qwen3_4b").getString();
            case "qwen-7b" -> Component.translatable("gui.herobrine_companion.local_model.reason_7b").getString();
            case "qwen3-8b" -> Component.translatable("gui.herobrine_companion.local_model.reason_qwen3_8b").getString();
            case "qwen3-14b" -> Component.translatable("gui.herobrine_companion.local_model.reason_qwen3_14b").getString();
            case "qwen3-30b-a3b" -> Component.translatable("gui.herobrine_companion.local_model.reason_qwen3_30b").getString();
            default -> "";
        };
    }

    // ---------- 输入 ----------

    /** 渲染时记录的目录区几何（与渲染使用同一组数据，供鼠标命中判断）。 */
    private int lastRowsTop = 0;
    private int lastRowsAreaHeight = 0;
    private int lastContentLeft = 0;
    private int lastContentWidth = 0;

    private int contentLeftOfTrack() {
        return this.lastContentLeft + this.lastContentWidth - 5;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && this.lastRowsAreaHeight > 0) {
            int totalRows = LocalModelLauncher.catalog().size() * ROW_HEIGHT;
            int maxScroll = Math.max(0, totalRows - this.lastRowsAreaHeight);
            if (maxScroll > 0) {
                int trackX = this.contentLeftOfTrack();
                if (mouseX >= trackX - 2 && mouseX <= trackX + 6
                        && mouseY >= this.lastRowsTop && mouseY < this.lastRowsTop + this.lastRowsAreaHeight) {
                    int thumbHeight = Math.max(10, (int) ((long) this.lastRowsAreaHeight * this.lastRowsAreaHeight / totalRows));
                    int thumbY = this.lastRowsTop
                            + (this.lastRowsAreaHeight - thumbHeight) * this.catalogScroll / maxScroll;
                    if (mouseY >= thumbY && mouseY < thumbY + thumbHeight) {
                        // 按住滑块开始拖动
                        this.draggingScrollbar = true;
                        this.dragGrabOffsetY = mouseY - thumbY;
                        return true;
                    }
                    // 点击轨道：滑块中心跳到鼠标位置
                    double targetY = mouseY - thumbHeight / 2.0D;
                    this.catalogScroll = (int) Math.round(
                            (targetY - this.lastRowsTop) * maxScroll / (this.lastRowsAreaHeight - thumbHeight));
                    this.catalogScroll = clamp(this.catalogScroll, 0, maxScroll);
                    return true;
                }
            }
            for (int[] area : this.rowAreas) {
                if (mouseX >= area[0] && mouseX <= area[0] + area[2]
                        && mouseY >= area[1] && mouseY <= area[1] + area[3]) {
                    this.onRowClicked(area[4]);
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (this.draggingScrollbar && this.lastRowsAreaHeight > 0) {
            int totalRows = LocalModelLauncher.catalog().size() * ROW_HEIGHT;
            int maxScroll = Math.max(0, totalRows - this.lastRowsAreaHeight);
            int thumbHeight = Math.max(10, (int) ((long) this.lastRowsAreaHeight * this.lastRowsAreaHeight / totalRows));
            double sliderY = mouseY - this.dragGrabOffsetY;
            this.catalogScroll = (int) Math.round(
                    (sliderY - this.lastRowsTop) * maxScroll / Math.max(1, this.lastRowsAreaHeight - thumbHeight));
            this.catalogScroll = clamp(this.catalogScroll, 0, maxScroll);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (this.draggingScrollbar && button == 0) {
            this.draggingScrollbar = false;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        // 1.21.1：原版签名改为 (mouseX, mouseY, scrollX, scrollY)，垂直滚轮取 scrollY
        double scrollDelta = scrollY;
        if (this.lastRowsAreaHeight > 0) {
            int totalRows = LocalModelLauncher.catalog().size() * ROW_HEIGHT;
            int maxScroll = Math.max(0, totalRows - this.lastRowsAreaHeight);
            boolean overRowsArea = mouseY >= this.lastRowsTop && mouseY < this.lastRowsTop + this.lastRowsAreaHeight;
            if (maxScroll > 0 && overRowsArea && scrollDelta != 0.0D) {
                // 半行步进，平滑滚动
                int step = (int) (scrollDelta > 0 ? -ROW_HEIGHT / 2 : ROW_HEIGHT / 2);
                this.catalogScroll = clamp(this.catalogScroll + step, 0, maxScroll);
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}