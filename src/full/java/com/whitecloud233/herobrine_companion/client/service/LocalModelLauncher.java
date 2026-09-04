package com.whitecloud233.herobrine_companion.client.service;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * 本地模型一键准备器：自动下载 llama.cpp 引擎 + 模型 GGUF，解压到 herobrine_local/，
 * 启动服务器进程并等待就绪。玩家只需点击"一键连接本地模型"。
 *
 * <p>本地根目录（{@link #rootDir()}）解析顺序：
 * ① 玩家在设置界面自选的位置（持久化到用户目录，跨整合包共享，选择一次即可）；
 * ② 系统属性 -Dherobrine.localDir=... ③ 环境变量 HEROBRINE_LOCAL_DIR=...
 * ④ 回退到当前游戏目录 herobrine_local/。
 * 前三者可指向一个全局共享目录（如 D:\AI\herobrine_local），让多个整合包共用同一份
 * 引擎 + 模型，避免每个整合包重复下载几个 GB 的依赖。</p>
 *
 * <p>目录结构（共享目录或游戏根目录下）：
 * <pre>
 * herobrine_local/
 * ├── bin/llama-server.exe + DLL        （llama.cpp：有 NVIDIA 独显自动用 CUDA 版 + -ngl 99；
 * │                                      无独显或 CUDA 起不来自动落 CPU 版，任何机器都能跑）
 * ├── models/herobrine-Q4_K_M.gguf      （按显存自动选择：≥6GB 用 7B，否则 3B）
 * └── server.log                        （服务器日志）
 * </pre>
 *
 * <p>引擎来源优先级：已就绪的本地文件 → 在线多源下载（CUDA：ModelScope→GitHub；CPU：ModelScope→GitHub）。
 * 模型档位按 nvidia-smi 显存自动选择（≥6GB → Qwen2.5-7B Q4_K_M，hf-mirror→HuggingFace；
 * 否则 → Qwen2.5-3B Q4_K_M，ModelScope 直链）。</p>
 */
public final class LocalModelLauncher {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** 模型档位文件名：3B（≤6GB 或无独显，单文件）与 7B（≥6GB 显存，官方两分片 Q4_K_M ≈4.5GB）。 */
    private static final String MODEL_FILE_3B = "herobrine-Q4_K_M.gguf";

    /** 3B 模型下载源：内置 ModelScope 直链（国内免加速器），-Dherobrine.localModelUrl 可覆盖。 */
    private static final List<String> MODEL_URLS_3B = List.of(System.getProperty("herobrine.localModelUrl",
            "https://modelscope.cn/mhybbsl/herobrine-gguf/resolve/master/herobrine-Q4_K_M.gguf"));

    /** 7B 模型分片 1 下载源：ModelScope 官方仓库（国内 CDN）优先，hf-mirror/HuggingFace 兜底。 */
    private static final List<String> MODEL_URLS_7B_SHARD1 = List.of(
            "https://modelscope.cn/models/Qwen/Qwen2.5-7B-Instruct-GGUF/resolve/master/qwen2.5-7b-instruct-q4_k_m-00001-of-00002.gguf",
            "https://hf-mirror.com/Qwen/Qwen2.5-7B-Instruct-GGUF/resolve/main/qwen2.5-7b-instruct-q4_k_m-00001-of-00002.gguf",
            "https://huggingface.co/Qwen/Qwen2.5-7B-Instruct-GGUF/resolve/main/qwen2.5-7b-instruct-q4_k_m-00001-of-00002.gguf");

    /** 7B 模型分片 2 下载源（同上顺序）。 */
    private static final List<String> MODEL_URLS_7B_SHARD2 = List.of(
            "https://modelscope.cn/models/Qwen/Qwen2.5-7B-Instruct-GGUF/resolve/master/qwen2.5-7b-instruct-q4_k_m-00002-of-00002.gguf",
            "https://hf-mirror.com/Qwen/Qwen2.5-7B-Instruct-GGUF/resolve/main/qwen2.5-7b-instruct-q4_k_m-00002-of-00002.gguf",
            "https://huggingface.co/Qwen/Qwen2.5-7B-Instruct-GGUF/resolve/main/qwen2.5-7b-instruct-q4_k_m-00002-of-00002.gguf");

    /** Qwen 官方 GGUF 仓库文件名 → 多源直链（ModelScope 国内 CDN 优先）。 */
    private static List<String> qwenUrls(String repoName, String fileName) {
        return List.of(
                "https://modelscope.cn/models/Qwen/" + repoName + "/resolve/master/" + fileName,
                "https://hf-mirror.com/Qwen/" + repoName + "/resolve/main/" + fileName,
                "https://huggingface.co/Qwen/" + repoName + "/resolve/main/" + fileName);
    }

    /** 任意组织 GGUF 仓库文件名 → 多源直链（ModelScope 可能没有该仓库，失败自动落到 hf-mirror）。 */
    private static List<String> genericUrls(String namespace, String repoName, String fileName) {
        return List.of(
                "https://modelscope.cn/models/" + namespace + "/" + repoName + "/resolve/master/" + fileName,
                "https://hf-mirror.com/" + namespace + "/" + repoName + "/resolve/main/" + fileName,
                "https://huggingface.co/" + namespace + "/" + repoName + "/resolve/main/" + fileName);
    }

    /**
     * 可选模型目录（对应"本地模型管理"页的下载列表）。
     *
     * <p>ladder 参数从低到高：1.5B → 3B → 7B，量化同为 7B 时 Q4_K_M < Q5_K_M < Q8_0，
     * 再往上 14B Q4_K_M。每档标注约占用空间、建议显存与 CPU 可运行性的下界，
     * 页面按玩家实际显卡/内存给出推荐。多分片档位在固定链接全挂时自动
     * 查询仓库文件列表（ModelScope / hf-mirror API）动态补齐分片。</p>
     */
    public record CatalogEntry(
            String key,
            String displayName,
            String family,          // 参数规模文案，如 "7B"
            long approxBytes,       // 约占用磁盘（字节，页面展示用）
            long minVramMb,         // 建议最低显存；0 = 无独显也能跑（CPU）
            long minSystemRamMb,    // CPU 推理建议最低系统内存
            String repoNs,          // 分片发现用仓库命名空间；空 = 不做自动发现
            String repoName,
            List<TierFile> files
    ) {
    }

    /**
     * 模型档位：一个档位可含多个分片文件（llama-server 用 -m 指向第一片自动加载其余分片）。
     */
    public record ModelTier(String key, String displayName, List<TierFile> files) {
        public TierFile firstFile() {
            return files.isEmpty() ? null : files.get(0);
        }
    }

    public record TierFile(String fileName, List<String> urls) {
    }

    /** 本地模型可选目录（顺序即页面展示顺序）。分片档位的固定直链按 Qwen 官方仓库命名约定；
     *  若仓库实际分片数与内置清单不一致，下载会走自动发现（ModelScope / hf-mirror 文件列表）补齐。
     *
     *  <p>模型选择说明（角色扮演向，中文优先）：
     *  <ul>
     *   <li>Qwen3 系列（1.7B / 4B / 8B / 14B / 30B-A3B）：比 Qwen2.5 同档位更聪明、更像会聊天的角色，
     *       非思考模式下回复直接、少说教；30B-A3B 为 MoE，激活参数仅 3B，显存够时速度快。</li>
     *   <li>Herobrine 3B 特调版：为模组人设蒸馏过的 Qwen2.5-3B，CPU/小显存机器的默认首选。</li>
     *   <li>Qwen2.5-7B：6GB 显存兼容档（8GB 卡已被 Qwen3-8B 取代）。</li>
     *  </ul></p>
     */
    private static final List<CatalogEntry> CATALOG = List.of(
            // Qwen 官方 1.7B 仓库只发了 Q8_0，没有 Q4_K_M；改用 ggml-org 的 Q4_K_M（已实测可下）
            new CatalogEntry("qwen3-1b7", "Qwen3-1.7B (Q4_K_M)", "1.7B",
                    1_280_000_000L, 0L, 4_096L, "ggml-org", "Qwen3-1.7B-GGUF",
                    List.of(new TierFile("Qwen3-1.7B-Q4_K_M.gguf",
                            genericUrls("ggml-org", "Qwen3-1.7B-GGUF", "Qwen3-1.7B-Q4_K_M.gguf")))),
            new CatalogEntry("qwen-3b-tuned", "Herobrine 3B 特调版 (Q4_K_M)", "3B",
                    2_000_000_000L, 0L, 8_192L, "", "",
                    List.of(new TierFile(MODEL_FILE_3B, MODEL_URLS_3B))),
            new CatalogEntry("qwen3-4b", "Qwen3-4B (Q4_K_M)", "4B",
                    2_600_000_000L, 0L, 8_192L, "Qwen", "Qwen3-4B-GGUF",
                    List.of(new TierFile("Qwen3-4B-Q4_K_M.gguf",
                            qwenUrls("Qwen3-4B-GGUF", "Qwen3-4B-Q4_K_M.gguf")))),
            new CatalogEntry("qwen-7b", "Qwen2.5-7B (Q4_K_M)", "7B",
                    4_680_000_000L, 6_144L, 16_384L, "Qwen", "Qwen2.5-7B-Instruct-GGUF",
                    List.of(new TierFile("qwen2.5-7b-instruct-q4_k_m-00001-of-00002.gguf", MODEL_URLS_7B_SHARD1),
                            new TierFile("qwen2.5-7b-instruct-q4_k_m-00002-of-00002.gguf", MODEL_URLS_7B_SHARD2))),
            new CatalogEntry("qwen3-8b", "Qwen3-8B (Q4_K_M)", "8B",
                    5_000_000_000L, 8_192L, 12_288L, "Qwen", "Qwen3-8B-GGUF",
                    List.of(new TierFile("Qwen3-8B-Q4_K_M.gguf",
                            qwenUrls("Qwen3-8B-GGUF", "Qwen3-8B-Q4_K_M.gguf")))),
            // 14B / 30B-A3B 官方仓库是【单文件】GGUF，没有 -0000x-of-0000x 分片（照抄 7B 的分片命名会 404）
            new CatalogEntry("qwen3-14b", "Qwen3-14B (Q4_K_M)", "14B",
                    9_000_000_000L, 12_288L, 16_384L, "Qwen", "Qwen3-14B-GGUF",
                    List.of(new TierFile("Qwen3-14B-Q4_K_M.gguf",
                            qwenUrls("Qwen3-14B-GGUF", "Qwen3-14B-Q4_K_M.gguf")))),
            new CatalogEntry("qwen3-30b-a3b", "Qwen3-30B-A3B (Q4_K_M)", "30B-A3B",
                    18_560_000_000L, 20_480L, 24_576L, "Qwen", "Qwen3-30B-A3B-GGUF",
                    List.of(new TierFile("Qwen3-30B-A3B-Q4_K_M.gguf",
                            qwenUrls("Qwen3-30B-A3B-GGUF", "Qwen3-30B-A3B-Q4_K_M.gguf")))),
            // google/ 官方仓库在 HF 上需要登录授权（匿名 401），魔搭也没镜像；ggml-org 的量化版可匿名下载
            new CatalogEntry("gemma3-4b", "Gemma-3-4B-IT (Q4_K_M)", "4B",
                    2_490_000_000L, 4_096L, 8_192L, "ggml-org", "gemma-3-4b-it-GGUF",
                    List.of(new TierFile("gemma-3-4b-it-Q4_K_M.gguf",
                            genericUrls("ggml-org", "gemma-3-4b-it-GGUF", "gemma-3-4b-it-Q4_K_M.gguf")))),
            // mistralai/ 官方仓库同样是 gated（401）；ModelScope 的 LLM-Research 镜像可匿名下载（单文件）
            new CatalogEntry("mistral-nemo-12b", "Mistral Nemo 12B (Q4_K_M)", "12B",
                    7_480_000_000L, 12_288L, 16_384L, "LLM-Research", "Mistral-Nemo-Instruct-2407-GGUF",
                    List.of(new TierFile("Mistral-Nemo-Instruct-2407-Q4_K_M.gguf",
                            genericUrls("LLM-Research", "Mistral-Nemo-Instruct-2407-GGUF",
                                    "Mistral-Nemo-Instruct-2407-Q4_K_M.gguf"))))
    );

    private static volatile ModelTier selectedTier;
    private static volatile boolean tierProbed = false;
    private static volatile long gpuVramMb = -1L;

    /** 玩家自选的模型档位 key（null = 未选择，按显存自动）。持久化到用户目录，跨整合包共享。 */
    private static volatile String modelChoiceKey = null;
    private static volatile boolean modelChoiceLoaded = false;

    private static Path modelChoiceFile() {
        return Path.of(System.getProperty("user.home", "."))
                .resolve(".herobrine_companion").resolve("model_choice.txt");
    }

    /** 当前模型目录列表（页面展示用，顺序即推荐阅读顺序）。 */
    public static List<CatalogEntry> catalog() {
        return CATALOG;
    }

    /** 按 key 找目录项；未知 key 返回 null（调用方回落自动推荐）。 */
    public static CatalogEntry catalogEntry(String key) {
        if (key == null || key.isBlank()) {
            return null;
        }
        for (CatalogEntry entry : CATALOG) {
            if (entry.key().equalsIgnoreCase(key.trim())) {
                return entry;
            }
        }
        return null;
    }

    /** 玩家自选档位 key；未选择返回 null（= 自动）。从用户目录文件读取，跨整合包共享。 */
    public static String modelChoiceKey() {
        if (!modelChoiceLoaded) {
            modelChoiceLoaded = true;
            try {
                Path file = modelChoiceFile();
                if (Files.isRegularFile(file)) {
                    String value = Files.readString(file, StandardCharsets.UTF_8).trim();
                    if (!value.isEmpty() && catalogEntry(value) != null) {
                        modelChoiceKey = value;
                    }
                }
            } catch (Exception ignored) {
                // 读不到按未设置处理
            }
        }
        return modelChoiceKey;
    }

    /** 设置玩家自选档位（null = 恢复自动选择）。持久化到用户目录；更换档位后清档位缓存。 */
    public static void setModelChoice(String key) {
        if (key == null || key.isBlank() || catalogEntry(key) == null) {
            modelChoiceKey = null;
        } else {
            modelChoiceKey = key.trim();
        }
        modelChoiceLoaded = true;
        tierProbed = false; // 等待下一次读取时按新档位重算
        try {
            Path file = modelChoiceFile();
            if (modelChoiceKey == null) {
                Files.deleteIfExists(file);
            } else {
                Path parent = file.getParent();
                if (parent != null) {
                    Files.createDirectories(parent);
                }
                Files.writeString(file, modelChoiceKey, StandardCharsets.UTF_8);
            }
        } catch (IOException ignored) {
            // 持久化失败不影响本次会话使用
        }
    }

    /** 无自选时的自动档位：与页面推荐一致（Qwen3 优先的按显存阶梯）。 */
    public static CatalogEntry autoModelEntry() {
        return recommendedEntry();
    }

    /**
     * 页面推荐档位：按玩家实际显卡给出最优一档（Qwen3 系列优先）。
     * CPU → Herobrine 3B 特调版；4GB 起 → Qwen3-4B；8GB 起 → Qwen3-8B；
     * 12GB 起 → Qwen3-14B；20GB 起 → Qwen3-30B-A3B（MoE）。
     */
    public static CatalogEntry recommendedEntry() {
        if (!supportsGpu()) {
            return catalogEntry("qwen-3b-tuned");
        }
        long vram = gpuVramMb();
        if (vram >= 20_480L) {
            return catalogEntry("qwen3-30b-a3b");
        }
        if (vram >= 12_288L) {
            return catalogEntry("qwen3-14b");
        }
        if (vram >= 8_192L) {
            return catalogEntry("qwen3-8b");
        }
        if (vram >= 4_096L) {
            return catalogEntry("qwen3-4b");
        }
        return catalogEntry("qwen-3b-tuned");
    }

    /**
     * 当前选择的模型档位（按显存自动选择，结果缓存整个会话）：
     * 玩家在"本地模型管理"页自选后按自选档位；未自选时保持旧行为
     * （NVIDIA 显存 ≥6GB → 7B，否则 → 3B）。
     */
    public static ModelTier selectedModelTier() {
        if (!tierProbed) {
            tierProbed = true;
            selectedTier = resolveModelTier();
        }
        return selectedTier;
    }

    /** 运行时按仓库实际文件发现的分片覆盖（key → 文件清单）；固定直链全挂时自动补齐。 */
    private static final Map<String, List<TierFile>> DISCOVERED = new ConcurrentHashMap<>();

    /** 进行中的下载（按 .part 路径去重）：自动连接与手动下载并发时复用同一流程，
     *  避免两条链路同时打开/写入同一个 .part 文件（Windows 下会导致文件被占用而写入失败）。 */
    private static final Map<Path, CompletableFuture<Path>> ACTIVE_DOWNLOADS = new ConcurrentHashMap<>();

    /** 本次会话内已确认连不上的下载源主机（连接超时/DNS 失败）：后续同一主机的地址直接跳过，
     *  不再每个地址都白等 10 秒 connect timeout（国内网络访问 huggingface.co 的典型表现）。 */
    private static final Set<String> UNREACHABLE_HOSTS = ConcurrentHashMap.newKeySet();

    /** 单个下载地址的最大尝试次数（网络抖动重试；本地写入失败不重试，重试也无效）。 */
    private static final int PER_URL_MAX_ATTEMPTS = 3;

    /** 下载进度回调的最小间隔（100ms）：避免高速下载时把渲染线程的任务队列刷满。 */
    private static final long PROGRESS_EMIT_INTERVAL_NANOS = 100_000_000L;

    private static ModelTier resolveModelTier() {
        CatalogEntry choice = catalogEntry(modelChoiceKey());
        if (choice == null || choice.files().isEmpty()) {
            choice = autoModelEntry();
            if (choice == null || choice.files().isEmpty()) {
                choice = catalogEntry("qwen-3b-tuned");
            }
        }
        List<TierFile> discovered = DISCOVERED.get(choice.key());
        List<TierFile> files = (discovered != null && !discovered.isEmpty()) ? discovered : choice.files();
        return new ModelTier(choice.key(), choice.displayName(), files);
    }

    /** 显存总量（MB）。nvidia-smi memory.total；无独显/探测失败返回 0。 */
    public static long gpuVramMb() {
        if (gpuVramMb < 0L) {
            gpuVramMb = probeVramMb();
        }
        return gpuVramMb;
    }

    private static long probeVramMb() {
        try {
            ProcessBuilder pb = new ProcessBuilder("nvidia-smi", "--query-gpu=memory.total", "--format=csv,noheader,nounits");
            pb.redirectErrorStream(true);
            Process p = pb.start();
            if (!p.waitFor(4, java.util.concurrent.TimeUnit.SECONDS)) {
                p.destroyForcibly();
                return 0L;
            }
            String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
            int newline = out.indexOf('\n');
            if (newline > 0) {
                out = out.substring(0, newline);
            }
            out = out.replaceAll("[^0-9]", "");
            return out.isEmpty() ? 0L : Long.parseLong(out);
        } catch (Exception e) {
            return 0L;
        }
    }
    public static final String SERVER_EXE = "llama-server.exe";
    public static final int DEFAULT_PORT = 8090;
    public static final String DEFAULT_ENDPOINT = "http://127.0.0.1:" + DEFAULT_PORT + "/v1/chat/completions";

    /**
     * 启动参数：上下文窗口大小。
     *
     * <p>必须 ≥ 实际聊天请求的 token 数。mod 聊天会携带完整人设提示 + 世界指令 +
     * 工具目录 + 会话历史，Qwen2.5 tokenizer 下真实请求普遍 10k+ token（此前 -c 4096
     * 导致服务器直接拒绝，聊天表现为 network error）。16k 在 CPU/GPU 上都只需
     * ~1.2GB KV 缓存（GQA 4 头），3B Q4_K 全程 <4GB 内存，8G 显存机可跑 GPU 版。</p>
     */
    public static final int LOCAL_CTX = 16384;

    /**
     * 推理引擎下载源（同一 b10488 版本，多源按顺序自动重试）：
     * 有 NVIDIA 独显时优先 CUDA 自包含包（含全部 CPU 件 + ggml-cuda/cuBLAS 运行库），
     * 全挂则继续尝试 CPU 包兜底，保证任何环境都能跑。国内首选 ModelScope 直链。
     */
    private static final List<String> ENGINE_GPU_URLS = List.of(
            "https://modelscope.cn/mhybbsl/herobrine-gguf/resolve/master/herobrine-engine-cuda.zip",
            "https://github.com/ggml-org/llama.cpp/releases/download/b10488/llama-b10488-bin-win-cuda-13.0-x64.zip");

    private static final List<String> ENGINE_CPU_URLS = List.of(
            "https://modelscope.cn/mhybbsl/herobrine-gguf/resolve/master/herobrine-engine-cpu.zip",
            "https://github.com/ggml-org/llama.cpp/releases/download/b10488/llama-b10488-bin-win-cpu-x64.zip");

    /** 无独显时只尝试 CPU 源。 */
    private static List<String> engineUrls(boolean wantGpu) {
        if (wantGpu) {
            java.util.List<String> urls = new java.util.ArrayList<>(ENGINE_GPU_URLS);
            urls.addAll(ENGINE_CPU_URLS); // GPU 源全挂 → 自动落 CPU 源
            return urls;
        }
        return ENGINE_CPU_URLS;
    }

    /** 显卡探测结果缓存。 */
    private static volatile boolean gpuAvailable = false;
    private static volatile boolean gpuAvailableProbed = false;
    /** 当前已就绪引擎是否带 CUDA 库（决定启动时是否加 -ngl）。 */
    private static volatile boolean gpuEngine = false;

    private static final String LOCAL_API_KEY = "sk-local";
    // 代理交给动态选择器：开关关闭即直连，开启后下载链路跟随系统代理；
    // 回环地址（本地 llama 服务的 /health、/props）由选择器判为直连，不受影响。
    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .proxy(SystemProxy.SELECTOR)
            .build();

    /** 启动的服务器进程（退出游戏时自动结束）。 */
    private static volatile Process serverProcess;

    /** 检测到 NVIDIA 显卡却最终以 CPU 推理时的具体原因（空串 = 未发生）；页面状态行与日志展示。 */
    private static volatile String gpuDowngradeReason = "";

    /** 当前"GPU→CPU 降级"的具体原因；空串表示没有降级。 */
    public static String gpuDowngradeReason() {
        return gpuDowngradeReason;
    }

    /** 新一轮准备/下载开始前重置降级原因（避免残留上一轮的结论）。 */
    private static void resetGpuDowngrade() {
        gpuDowngradeReason = "";
    }

    /** 按翻译键取文本（降级原因文案统一走语言文件；工作线程调用，与 AIService 既有用法一致）。 */
    private static String tr(String key, Object... args) {
        return Component.translatable(key, args).getString();
    }

    /** 当前模型的显存占用提示片段（按翻译键带参）；大小未知返回空串。 */
    private static String cudaSizeHint() {
        CatalogEntry entry = catalogEntry(selectedModelTier().key());
        if (entry == null || entry.approxBytes() <= 0L) {
            return "";
        }
        double weightGb = entry.approxBytes() / 1_000_000_000.0D;
        return tr("gui.herobrine_companion.local_model.downgrade_size_hint", weightGb, weightGb + 1.3D);
    }

    public record DownloadProgress(long downloaded, long total, String label) {
        public double fraction() {
            return total > 0 ? Math.min(1.0, (double) downloaded / total) : 0.0;
        }
    }

    public record PrepareResult(boolean ready, String endpoint, String modelId, String message) {
    }

    private LocalModelLauncher() {
    }

    /**
     * 本地根目录。解析顺序：
     * ① 玩家在设置界面选择的位置（{@link #setCustomRootDir(Path)}，持久化到用户目录
     *    ~/.herobrine_companion/local_dir.txt，所有整合包共享，只需选择一次）；
     * ② 系统属性 -Dherobrine.localDir=...（与 -Dherobrine.localModelUrl 同风格）；
     * ③ 环境变量 HEROBRINE_LOCAL_DIR=...；
     * ④ 回退到当前游戏目录 herobrine_local/（原行为）。
     *
     * <p>共享目录建议用绝对路径（如 D:\AI\herobrine_local）；多个整合包指向同一目录时，
     * 引擎与模型只下载一次，之后所有整合包直接复用（serverPresent/modelPresent 命中）。</p>
     */
    public static Path rootDir() {
        Path custom = customRootDir();
        if (custom != null) {
            return custom;
        }
        String shared = System.getProperty("herobrine.localDir");
        if (shared == null || shared.isBlank()) {
            shared = System.getenv("HEROBRINE_LOCAL_DIR");
        }
        if (shared != null && !shared.isBlank()) {
            return Path.of(shared).toAbsolutePath().normalize();
        }
        return Minecraft.getInstance().gameDirectory.toPath().resolve("herobrine_local");
    }

    /** 持久化的自定义根目录（内存缓存 + 用户目录文件）。 */
    private static volatile Path customRootDir;
    private static volatile boolean customRootLoaded = false;

    /** 持久化文件：用户目录（跨整合包共享，选择一次处处生效）。 */
    private static Path customRootFile() {
        return Path.of(System.getProperty("user.home", "."))
                .resolve(".herobrine_companion").resolve("local_dir.txt");
    }

    private static Path customRootDir() {
        if (!customRootLoaded) {
            customRootLoaded = true;
            try {
                Path file = customRootFile();
                if (Files.isRegularFile(file)) {
                    String value = Files.readString(file, StandardCharsets.UTF_8).trim();
                    if (!value.isEmpty()) {
                        customRootDir = Path.of(value).toAbsolutePath().normalize();
                    }
                }
            } catch (Exception ignored) {
                // 读不到按未设置处理
            }
        }
        return customRootDir;
    }

    /** 设置玩家自选下载位置（null 清除选择）。持久化到用户目录，所有整合包共享。 */
    public static void setCustomRootDir(Path dir) {
        if (dir == null) {
            customRootDir = null;
        } else {
            customRootDir = dir.toAbsolutePath().normalize();
        }
        customRootLoaded = true;
        try {
            Path file = customRootFile();
            if (customRootDir == null) {
                Files.deleteIfExists(file);
            } else {
                Path parent = file.getParent();
                if (parent != null) {
                    Files.createDirectories(parent);
                }
                Files.writeString(file, customRootDir.toString(), StandardCharsets.UTF_8);
            }
        } catch (IOException ignored) {
            // 持久化失败不影响本次会话使用
        }
    }

    /** 玩家是否已自选下载位置。 */
    public static boolean hasCustomRootDir() {
        return customRootDir() != null;
    }

    /** 是否需要下载（引擎或模型缺失）。界面可在下载前据此提示玩家选择位置。 */
    public static boolean needsDownload() {
        return !serverPresent() || !modelPresent();
    }

    public static Path modelsDir() {
        return rootDir().resolve("models");
    }

    public static Path binDir() {
        return rootDir().resolve("bin");
    }

    /** 主模型文件路径（档位第一分片；llama-server 会按命名自动加载其余分片）。 */
    public static Path modelPath() {
        TierFile first = selectedModelTier().firstFile();
        return modelsDir().resolve(first == null ? MODEL_FILE_3B : first.fileName());
    }

    /** 档位内某个分片的目标路径。 */
    public static Path tierFilePath(TierFile tierFile) {
        return modelsDir().resolve(tierFile.fileName());
    }

    public static Path serverPath() {
        return binDir().resolve(SERVER_EXE);
    }

    /** 服务器二进制是否已就绪（exe + 核心 DLL 存在、非空且是合法 PE 文件）。 */
    public static boolean serverPresent() {
        return isValidExecutable(serverPath()) && isValidExecutable(binDir().resolve("ggml.dll"));
    }

    /** 引擎是否带 CUDA 后端（ggml-cuda.dll + 首选 CUDA 运行库）。 */
    public static boolean serverGpuPresent() {
        return serverPresent()
                && Files.isRegularFile(binDir().resolve("ggml-cuda.dll"))
                && Files.isRegularFile(binDir().resolve("cublasLt64_13.dll"));
    }

    /** 是否有 NVIDIA 独显（nvidia-smi 可用即视为有；结果缓存整个会话）。 */
    public static boolean supportsGpu() {
        if (!gpuAvailableProbed) {
            gpuAvailableProbed = true;
            gpuAvailable = probeGpu();
        }
        return gpuAvailable;
    }

    /** 探测 nvidia-smi：4 秒内以退出码 0 返回且输出至少一行说明有 NVIDIA 显卡；同时缓存显卡名。 */
    private static boolean probeGpu() {
        try {
            ProcessBuilder pb = new ProcessBuilder("nvidia-smi", "--query-gpu=name", "--format=csv,noheader");
            pb.redirectErrorStream(true);
            Process p = pb.start();
            boolean finished = p.waitFor(4, java.util.concurrent.TimeUnit.SECONDS);
            if (!finished) {
                p.destroyForcibly();
                return false;
            }
            if (p.exitValue() != 0) {
                return false;
            }
            try (InputStream in = p.getInputStream()) {
                java.util.Scanner sc = new java.util.Scanner(in, StandardCharsets.UTF_8.name()).useDelimiter("\\Z");
                String out = sc.hasNext() ? sc.next() : "";
                if (out.isBlank()) {
                    return false;
                }
                String first = out.trim();
                int newline = first.indexOf('\n');
                if (newline > 0) {
                    first = first.substring(0, newline);
                }
                gpuName = first.trim();
                return true;
            }
        } catch (Exception e) {
            return false;
        }
    }

    private static volatile String gpuName = "";

    /** NVIDIA 显卡名（探测成功后缓存；无独显返回空串）。 */
    public static String gpuName() {
        supportsGpu();
        return gpuName;
    }

    /** 模型是否已就绪：每个分片都通过 GGUF 结构校验（非空 + 头部/元数据/张量区完整）。
     *  残缺、截断或损坏的文件会判为缺失并触发重新下载，避免服务器加载坏模型时卡死或崩溃。 */
    public static boolean modelPresent() {
        for (TierFile tierFile : selectedModelTier().files()) {
            if (!isValidModelFile(tierFilePath(tierFile))) {
                return false;
            }
        }
        return true;
    }

    /** 目录项全部文件是否已通过 GGUF 校验（页面"已下载"标记用；不触发档位重算）。 */
    public static boolean entryFilesPresent(CatalogEntry entry) {
        if (entry == null || entry.files().isEmpty()) {
            return false;
        }
        for (TierFile tierFile : entry.files()) {
            if (!isValidModelFile(tierFilePath(tierFile))) {
                return false;
            }
        }
        return true;
    }

    /** GGUF 魔数（"GGUF"，小端读取为 0x46554747）。 */
    private static final int GGUF_MAGIC = 0x46554747;

    /** 校验模型文件时的读缓冲（GGUF 头部含十几万条 tokenizer 词条，小缓冲会放大系统调用次数）。 */
    private static final int MODEL_PROBE_BUFFER = 256 * 1024;

    /** GGUF 校验结果缓存：键 = 绝对路径|大小|修改时间。文件未变化就不重复解析 GB 级文件头部，
     *  避免"本地模型管理"页每几秒刷新一次就绪状态时在渲染线程上卡住。 */
    private static final Map<String, Boolean> MODEL_FILE_VALID = new ConcurrentHashMap<>();

    /** 缓存条目上限（下载期间文件持续变化会不断产生新键，超限整体清空防止无限增长）。 */
    private static final int MODEL_FILE_CACHE_LIMIT = 512;

    /** PE 可执行文件有效性：常规文件、非空且以 MZ 魔数开头（防残缺/截断的引擎文件被当成已就绪）。 */
    private static boolean isValidExecutable(Path path) {
        try {
            if (!Files.isRegularFile(path) || Files.size(path) < 64) {
                return false;
            }
            try (InputStream in = Files.newInputStream(path)) {
                byte[] head = in.readNBytes(2);
                return head.length == 2 && head[0] == 0x4D && head[1] == 0x5A; // "MZ"
            }
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * 校验 GGUF 模型文件结构完整性：解析头部（magic/版本/张量数/元数据键值/张量信息），
     * 要求整个头部可解析，且按 ggml 类型尺寸表推算的张量数据区不超出文件末尾。
     * 任意位置被截断/损坏的文件（包括只写了头部、数据区被砍断的残缺文件）都会判为无效。
     * 分片文件（00001-of-00002 等）每个分片都是自包含的 GGUF，各自独立校验。
     */
    private static boolean isValidModelFile(Path path) {
        if (path == null || !Files.isRegularFile(path)) {
            return false;
        }
        try {
            long fileSize = Files.size(path);
            if (fileSize < 24) {
                return false;
            }
            // 缓存键含大小与修改时间：文件没变就复用上次的结论（界面刷新/断线重连都只查缓存）
            FileTime mtime = Files.getLastModifiedTime(path);
            String cacheKey = path.toAbsolutePath() + "|" + fileSize + "|" + mtime.toMillis();
            Boolean cached = MODEL_FILE_VALID.get(cacheKey);
            if (cached != null) {
                return cached;
            }
            boolean valid;
            // 必须带缓冲：GGUF 元数据里的 tokenizer 词表是十几万个短字符串，
            // 无缓冲逐个 read 会产生 30 万+ 次系统调用（实测单个 3.8GB 分片 1.6s，加缓冲后 0.2s）
            try (InputStream in = new BufferedInputStream(Files.newInputStream(path), MODEL_PROBE_BUFFER)) {
                long dataEnd = ggufDataEnd(in);
                valid = dataEnd >= 0L && dataEnd <= fileSize;
            }
            if (MODEL_FILE_VALID.size() >= MODEL_FILE_CACHE_LIMIT) {
                // 下载过程中文件持续变化会不断产生新键，定期清空防止无限增长
                MODEL_FILE_VALID.clear();
            }
            MODEL_FILE_VALID.put(cacheKey, valid);
            return valid;
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * 流式解析 GGUF 头部：跳过全部元数据键值对与张量信息，返回按类型尺寸推算的
     * 张量数据区末尾位置；结构损坏返回 -1（未知张量类型不参与数据区校验，保持向前兼容）。
     */
    private static long ggufDataEnd(InputStream in) throws IOException {
        byte[] head = in.readNBytes(24);
        if (head.length < 24) {
            return -1L;
        }
        ByteBuffer bb = ByteBuffer.wrap(head).order(ByteOrder.LITTLE_ENDIAN);
        if (bb.getInt() != GGUF_MAGIC) {
            return -1L;
        }
        int version = bb.getInt();
        if (version < 1 || version > 3) {
            return -1L;
        }
        long tensorCount = bb.getLong();
        long kvCount = bb.getLong();
        if (tensorCount < 1L || kvCount < 0L) {
            return -1L;
        }
        try {
            for (long i = 0L; i < kvCount; i++) {
                if (!ggufSkipString(in)) {
                    return -1L;
                }
                if (!ggufSkipValue(in)) {
                    return -1L;
                }
            }
            long dataEnd = 0L;
            for (long i = 0L; i < tensorCount; i++) {
                if (!ggufSkipString(in)) {
                    return -1L; // 张量名
                }
                byte[] dimHead = in.readNBytes(4);
                if (dimHead.length < 4) {
                    return -1L;
                }
                int nDims = ByteBuffer.wrap(dimHead).order(ByteOrder.LITTLE_ENDIAN).getInt();
                if (nDims < 1 || nDims > 8) {
                    return -1L;
                }
                long elements = 1L;
                for (int d = 0; d < nDims; d++) {
                    byte[] dim = in.readNBytes(8);
                    if (dim.length < 8) {
                        return -1L;
                    }
                    long dimSize = ByteBuffer.wrap(dim).order(ByteOrder.LITTLE_ENDIAN).getLong();
                    if (dimSize < 0L) {
                        return -1L;
                    }
                    elements = Math.multiplyExact(elements, dimSize);
                }
                byte[] tHead = in.readNBytes(12);
                if (tHead.length < 12) {
                    return -1L;
                }
                ByteBuffer tb = ByteBuffer.wrap(tHead).order(ByteOrder.LITTLE_ENDIAN);
                int type = tb.getInt();
                long offset = tb.getLong();
                if (offset < 0L) {
                    return -1L;
                }
                long tensorBytes = ggmlTensorBytes(type, elements);
                if (tensorBytes >= 0L) {
                    dataEnd = Math.max(dataEnd, Math.addExact(offset, tensorBytes));
                }
            }
            return dataEnd;
        } catch (ArithmeticException e) {
            return -1L; // 维度乘积/偏移溢出：损坏文件
        }
    }

    /** GGUF 字符串：u64 长度 + 字节。 */
    private static boolean ggufSkipString(InputStream in) throws IOException {
        byte[] lenBytes = in.readNBytes(8);
        if (lenBytes.length < 8) {
            return false;
        }
        long len = ByteBuffer.wrap(lenBytes).order(ByteOrder.LITTLE_ENDIAN).getLong();
        if (len < 0L || len > 256L * 1024 * 1024) {
            return false;
        }
        return ggufSkipBytes(in, len);
    }

    /** GGUF 元数据值：u32 类型 + 载荷。 */
    private static boolean ggufSkipValue(InputStream in) throws IOException {
        byte[] typeBytes = in.readNBytes(4);
        if (typeBytes.length < 4) {
            return false;
        }
        int type = ByteBuffer.wrap(typeBytes).order(ByteOrder.LITTLE_ENDIAN).getInt();
        switch (type) {
            case 0: case 1: case 7: return ggufSkipBytes(in, 1);   // UINT8 / INT8 / BOOL
            case 2: case 3: return ggufSkipBytes(in, 2);           // UINT16 / INT16
            case 4: case 5: case 6: return ggufSkipBytes(in, 4);   // UINT32 / INT32 / FLOAT32
            case 8: return ggufSkipString(in);                     // STRING
            case 9: {                                               // ARRAY
                byte[] arrHead = in.readNBytes(12);
                if (arrHead.length < 12) {
                    return false;
                }
                ByteBuffer ab = ByteBuffer.wrap(arrHead).order(ByteOrder.LITTLE_ENDIAN);
                int elementType = ab.getInt();
                long count = ab.getLong();
                if (count < 0L || count > 100_000_000L) {
                    return false;
                }
                for (long i = 0L; i < count; i++) {
                    if (!ggufSkipValueElement(elementType, in)) {
                        return false;
                    }
                }
                return true;
            }
            case 10: case 11: case 12: return ggufSkipBytes(in, 8); // UINT64 / INT64 / FLOAT64
            default: return false;
        }
    }

    /** GGUF 数组元素（不允许嵌套数组）。 */
    private static boolean ggufSkipValueElement(int type, InputStream in) throws IOException {
        switch (type) {
            case 0: case 1: case 7: return ggufSkipBytes(in, 1);
            case 2: case 3: return ggufSkipBytes(in, 2);
            case 4: case 5: case 6: return ggufSkipBytes(in, 4);
            case 8: return ggufSkipString(in);
            case 10: case 11: case 12: return ggufSkipBytes(in, 8);
            default: return false;
        }
    }

    /** 跳过 count 字节（不依赖 JDK12+ 的 skipNBytes）：skip() 返回 0 时退化为逐字节读取，
     *  提前 EOF 说明文件被截断，返回 false。 */
    private static boolean ggufSkipBytes(InputStream in, long count) throws IOException {
        long remaining = count;
        while (remaining > 0L) {
            long skipped = in.skip(remaining);
            if (skipped <= 0L) {
                if (in.read() == -1) {
                    return false; // EOF：头部/元数据区被截断
                }
                remaining--;
            } else {
                remaining -= skipped;
            }
        }
        return true;
    }

    /**
     * ggml 张量类型 → 元素数为 elements 时的字节数。尺寸表与 llama.cpp ggml.h 一致；
     * 未知类型返回 -1（不参与数据区校验，保持向前兼容）。
     */
    private static long ggmlTensorBytes(int type, long elements) {
        int blockSize;
        int typeSize;
        switch (type) {
            case 0: blockSize = 1; typeSize = 4; break;    // F32
            case 1: blockSize = 1; typeSize = 2; break;    // F16
            case 2: blockSize = 32; typeSize = 18; break;  // Q4_0
            case 3: blockSize = 32; typeSize = 20; break;  // Q4_1
            case 4: blockSize = 16; typeSize = 9; break;   // Q4_2（旧格式）
            case 5: blockSize = 16; typeSize = 11; break;  // Q4_3（旧格式）
            case 6: blockSize = 32; typeSize = 22; break;  // Q5_0
            case 7: blockSize = 32; typeSize = 24; break;  // Q5_1
            case 8: blockSize = 32; typeSize = 34; break;  // Q8_0
            case 9: blockSize = 32; typeSize = 36; break;  // Q8_1
            case 10: blockSize = 256; typeSize = 84; break;   // Q2_K
            case 11: blockSize = 256; typeSize = 110; break;  // Q3_K
            case 12: blockSize = 256; typeSize = 144; break;  // Q4_K
            case 13: blockSize = 256; typeSize = 176; break;  // Q5_K
            case 14: blockSize = 256; typeSize = 210; break;  // Q6_K
            case 15: blockSize = 256; typeSize = 292; break;  // Q8_K
            case 16: blockSize = 256; typeSize = 20; break;   // IQ2_XXS
            case 17: blockSize = 256; typeSize = 24; break;   // IQ2_XS
            case 18: blockSize = 256; typeSize = 32; break;   // IQ3_XXS
            case 19: blockSize = 256; typeSize = 26; break;   // IQ1_S
            case 20: blockSize = 32; typeSize = 18; break;    // IQ4_NL
            case 21: blockSize = 256; typeSize = 44; break;   // IQ3_S
            case 22: blockSize = 256; typeSize = 36; break;   // IQ2_S
            case 23: blockSize = 256; typeSize = 44; break;   // IQ4_XS
            case 24: blockSize = 1; typeSize = 1; break;      // I8
            case 25: blockSize = 1; typeSize = 2; break;      // I16
            case 26: blockSize = 1; typeSize = 4; break;      // I32
            case 27: blockSize = 1; typeSize = 8; break;      // I64
            case 28: blockSize = 1; typeSize = 8; break;      // F64
            case 29: blockSize = 256; typeSize = 26; break;   // IQ1_M
            case 30: blockSize = 1; typeSize = 2; break;      // BF16
            default: return -1L;
        }
        long blocks = (elements + blockSize - 1) / blockSize;
        return Math.multiplyExact(blocks, (long) typeSize);
    }

    /** 进行中的准备流程：进世界自动连接与设置页手动点击并发时复用同一流程，避免重复下载/重复起服务。 */
    private static volatile CompletableFuture<PrepareResult> prepareInFlight;

    /** 退出存档标记：置位后任何新拉起的服务器进程都会被立即停止（下载中途退出存档时防止进程留在后台占内存）。 */
    private static volatile boolean sessionClosed = false;

    /**
     * 一键准备流程（异步）：
     * 已运行的服务直接返回；否则补服务器 → 补模型 → 启动 → 等健康。
     * 同一时间只允许一个准备流程：自动连接/手动点击并发时，后者直接复用前者的结果。
     *
     * @param progress 下载/启动阶段进度回调（任意线程，UI 侧需自行切回渲染线程）
     */
    public static CompletableFuture<PrepareResult> prepareAsync(Consumer<DownloadProgress> progress) {
        synchronized (LocalModelLauncher.class) {
            // 新一轮准备（进新存档/手动点击）视为新的游戏会话，清除退出标记；
            // 若复用仍在进行中的旧流程，旧流程启动服务器时也会读到最新标记。
            sessionClosed = false;
            resetGpuDowngrade();
            if (prepareInFlight != null && !prepareInFlight.isDone()) {
                return prepareInFlight;
            }
            CompletableFuture<PrepareResult> future = doPrepareAsync(progress);
            prepareInFlight = future;
            return future;
        }
    }

    /**
     * 只下载当前所选档位的文件（推理引擎 + 模型分片），不启动服务器。
     *
     * <p>「本地模型管理」页的【下载所选模型】按钮走这里：已就绪的文件自动跳过；
     * 支持断点续传（中断后保留 .part，换源/重试均从断点继续）；不改变任何路由/槽位配置。</p>
     *
     * @param progress 下载进度回调（任意线程，UI 侧需自行切回渲染线程）
     * @return 全部文件就绪后的模型路径
     */
    public static CompletableFuture<Path> downloadSelectedAsync(Consumer<DownloadProgress> progress) {
        synchronized (LocalModelLauncher.class) {
            sessionClosed = false; // 新下载视为新一轮会话（与 prepareAsync 语义一致）
            resetGpuDowngrade();
        }
        return ensureServerAsync(progress).thenCompose(ignored -> ensureModelAsync(progress));
    }

    private static CompletableFuture<PrepareResult> doPrepareAsync(Consumer<DownloadProgress> progress) {
        announceTier(progress);
        // 必要文件缺失 → 直接进入下载/启动，不先探测。
        // 否则探测可能因残留进程占着 8090 却不响应而卡死，导致永远走不到下载。
        if (!serverPresent() || !modelPresent()) {
            return prepareFreshAsync(progress);
        }
        return LocalModelConnector.probeAsync().thenCompose(probe -> {
            if (probe.reachable()) {
                // 有 NVIDIA 显卡但本地 bin 仍只有 CPU 引擎时，不能把正在运行的 CPU 服务
                // 当成可复用服务，否则永远不会下载 CUDA 包，也不会加 -ngl 99。
                if (supportsGpu() && !serverGpuPresent()) {
                    gpuDowngradeReason = tr("gui.herobrine_companion.local_model.downgrade_engine_cpu_only");
                    return restartOwnedServerOrExplain(progress,
                            "检测到 NVIDIA 显卡，但 8090 当前运行的是 CPU 引擎。请关闭旧的 llama-server 后重试，以启用 CUDA 加速。" );
                }
                // 已运行：进一步核对上下文窗口是否够用。旧服务器（如 -c 4096）会拒绝
                // 10k+ token 的聊天请求，直接用会导致"network error"，必须重启或提示。
                return probeContextAsync(probe.endpoint()).thenCompose(ctx -> {
                    if (ctx == null || ctx >= LOCAL_CTX) {
                        return CompletableFuture.completedFuture(
                                new PrepareResult(true, probe.endpoint(), probe.modelId(), "本地服务器已在运行"));
                    }
                    return restartOwnedServerOrExplain(progress,
                            "检测到旧版本地服务器（上下文 " + ctx + "）仍在运行。请先关闭它（或结束端口 8090 的进程），再点一次【一键连接】。" );
                });
            }
            return prepareFreshAsync(progress);
        }).exceptionally(e -> new PrepareResult(false, DEFAULT_ENDPOINT, LocalModelConnector.DEFAULT_MODEL,
                "准备失败: " + rootMessage(e)));
    }

    /** 一键开始前先播报检测结果与自动选择的档位（下载/启动进度回调会覆盖为实际阶段）。 */
    private static void announceTier(Consumer<DownloadProgress> progress) {
        if (progress == null) {
            return;
        }
        ModelTier tier = selectedModelTier();
        if (supportsGpu()) {
            progress.accept(new DownloadProgress(0, 0,
                    "检测到 " + gpuName() + "（" + gpuVramMb() + " MB 显存），自动选择 " + tier.displayName()));
        } else {
            progress.accept(new DownloadProgress(0, 0,
                    "未检测到独立显卡，自动选择 " + tier.displayName() + "（CPU 模式）"));
        }
    }

    /**
     * 旧服务占端口时：同一 JVM 且由本启动器拉起的进程可以安全重启；
     * 其他会话/手动启动的进程不擅自杀，返回明确提示。
     */
    private static CompletableFuture<PrepareResult> restartOwnedServerOrExplain(Consumer<DownloadProgress> progress,
                                                                                 String externalMessage) {
        Process owned = serverProcess;
        if (owned == null || !owned.isAlive()) {
            return CompletableFuture.completedFuture(new PrepareResult(false, DEFAULT_ENDPOINT,
                    LocalModelConnector.DEFAULT_MODEL, externalMessage));
        }
        owned.destroy();
        serverProcess = null;
        try {
            if (!owned.waitFor(5, java.util.concurrent.TimeUnit.SECONDS)) {
                owned.destroyForcibly();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            owned.destroyForcibly();
        }
        return prepareFreshAsync(progress);
    }

    /** 无可用服务器时的完整准备流程：补服务器 → 补模型 → 启动 → 等健康。
     *  任何阶段失败都映射为带信息的 PrepareResult（绝不向调用方抛出异常，避免 UI 卡在连接中）。 */
    private static CompletableFuture<PrepareResult> prepareFreshAsync(Consumer<DownloadProgress> progress) {
        return ensureServerAsync(progress)
                .thenCompose(ignored -> ensureModelAsync(progress))
                .thenCompose(ignored -> startServerAsync(progress))
                .thenApply(ok -> {
                    if (ok) {
                        return new PrepareResult(true, DEFAULT_ENDPOINT, LocalModelConnector.DEFAULT_MODEL, "服务器已启动");
                    }
                    Process p = serverProcess;
                    String detail = (p == null || p.isAlive())
                            ? "等待服务器就绪超时（3 分钟）"
                            : "服务器进程异常退出（可查看 server.cpu.log / server.cuda.log）";
                    return new PrepareResult(false, DEFAULT_ENDPOINT, LocalModelConnector.DEFAULT_MODEL,
                            "服务器启动失败：" + detail);
                })
                .exceptionally(e -> new PrepareResult(false, DEFAULT_ENDPOINT, LocalModelConnector.DEFAULT_MODEL,
                        "自动准备失败：" + rootMessage(e)));
    }

    /**
     * 读取正在运行的 llama.cpp 服务器的实际上下文窗口（GET /props →
     * default_generation_settings.n_ctx）。Ollama 或异常时返回 null（视为足够）。
     */
    private static CompletableFuture<Integer> probeContextAsync(String endpoint) {
        String base = endpoint == null ? DEFAULT_ENDPOINT : endpoint;
        int cut = base.indexOf("/v1/chat/completions");
        if (cut > 0) {
            base = base.substring(0, cut);
        }
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(base + "/props"))
                .timeout(Duration.ofSeconds(5))
                .header("Accept", "application/json")
                .GET().build();
        return CLIENT.sendAsync(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
                .orTimeout(8, java.util.concurrent.TimeUnit.SECONDS)
                .thenApply(response -> {
                    if (response.statusCode() < 200 || response.statusCode() >= 300) {
                        return null;
                    }
                    try {
                        JsonObject root = JsonParser.parseString(response.body()).getAsJsonObject();
                        if (root.has("default_generation_settings")) {
                            JsonObject dgs = root.getAsJsonObject("default_generation_settings");
                            if (dgs.has("n_ctx") && dgs.get("n_ctx").isJsonPrimitive()) {
                                return dgs.get("n_ctx").getAsInt();
                            }
                        }
                    } catch (Exception ignored) {
                        // 非标准响应按"未知上下文"处理，不阻断已运行流程
                    }
                    return null;
                })
                .exceptionally(e -> null);
    }

    /** 确保推理引擎就绪：有独显优先 CUDA 包（下载源全挂自动落 CPU 包），无独显走 CPU 包。 */
    private static CompletableFuture<Path> ensureServerAsync(Consumer<DownloadProgress> progress) {
        boolean wantGpu = supportsGpu();
        if (serverGpuPresent() && wantGpu) {
            gpuEngine = true;
            return CompletableFuture.completedFuture(serverPath());
        }
        if (serverPresent() && (!wantGpu || serverGpuPresent())) {
            // 已有合适引擎：有 NVIDIA 且 CUDA 库齐全才上卡；无 NVIDIA 即使目录里有 CUDA
            // DLL 也按 CPU 用，避免在没有可用驱动的机器上启动失败。
            gpuEngine = wantGpu && serverGpuPresent();
            if (wantGpu && !gpuEngine) {
                // 检测到了 NVIDIA 显卡，但库目录里只有 CPU 版引擎 → 记录具体原因
                gpuDowngradeReason = tr("gui.herobrine_companion.local_model.downgrade_bin_cpu_only");
                LOGGER.warn("检测到 NVIDIA 显卡，但本机引擎缺 CUDA 库，将以 CPU 推理：{}", gpuDowngradeReason);
            }
            return CompletableFuture.completedFuture(serverPath());
        }
        // 这里可能是“CPU 引擎已存在但玩家有 NVIDIA”：继续下载 CUDA 包并覆盖/补齐 DLL，
        // 不能因为 llama-server.exe 已存在就提前返回。
        try {
            Files.createDirectories(binDir());
        } catch (IOException e) {
            return CompletableFuture.failedFuture(e);
        }
        Path zipPath = rootDir().resolve("llama-engine.zip");
        // 清理上次中断留下的完整残包（未解压即退出）；.part 保留，供断点续传。
        deleteQuietly(zipPath);
        return downloadEngineFromUrls(engineUrls(wantGpu), 0, zipPath, "下载推理引擎", progress)
                .thenCompose(ignored -> CompletableFuture.runAsync(() -> {
                    try {
                        unzip(zipPath, binDir());
                        Files.deleteIfExists(zipPath);
                    } catch (IOException e) {
                        throw new CompletionException(e);
                    }
                }))
                .thenApply(ignored -> {
                    // 是否真拿到 CUDA 库以解压结果为准（源内容有差异也不影响运行）。
                    gpuEngine = wantGpu && serverGpuPresent();
                    if (wantGpu && !gpuEngine) {
                        gpuDowngradeReason = tr("gui.herobrine_companion.local_model.downgrade_pkg_no_cuda");
                        LOGGER.warn("检测到 NVIDIA 显卡，但引擎包内没有 CUDA 运行库，将以 CPU 推理：{}", gpuDowngradeReason);
                    }
                    return serverPath();
                });
    }

    /** 依次尝试多个引擎下载地址，全部失败才报错；单个地址失败时清理残留文件。 */
    private static CompletableFuture<Path> downloadEngineFromUrls(List<String> urls, int index, Path target,
                                                                  String label, Consumer<DownloadProgress> progress) {
        if (index >= urls.size()) {
            return CompletableFuture.failedFuture(new IllegalStateException(
                    "所有推理引擎下载地址均失败（共 " + urls.size() + " 个）。"));
        }
        String url = urls.get(index);
        return downloadToFile(url, target, label + " (" + safeHost(url) + ")", progress)
                .exceptionallyCompose(error -> {
                    // 单个地址失败：清理残留（.part 由 downloadToFile 清理，这里兜底清目标文件）
                    deleteQuietly(target);
                    return downloadEngineFromUrls(urls, index + 1, target, label, progress);
                });
    }

    /** 确保模型文件就绪：本地候选 → 在线多源下载（按档位逐个补齐分片）。
     *  残缺/损坏的既有文件不会通过校验，会被删除并重新下载（详见 {@link #isValidModelFile}）。 */
    private static CompletableFuture<Path> ensureModelAsync(Consumer<DownloadProgress> progress) {
        if (modelPresent()) {
            return CompletableFuture.completedFuture(modelPath());
        }
        try {
            Files.createDirectories(modelsDir());
        } catch (IOException e) {
            return CompletableFuture.failedFuture(e);
        }
        // 1) 本地候选文件（开发者工作区 / 用户手动放置 / 默认目录里已下载好的完整模型）：
        //    每个分片都有候选才整体复制；复制后仍不完整的（候选本身残缺）转在线下载。
        List<TierFile> tierFiles = selectedModelTier().files();
        boolean allCandidates = true;
        for (TierFile tierFile : tierFiles) {
            if (!isValidModelFile(tierFilePath(tierFile)) && findLocalModelCandidate(tierFile.fileName()) == null) {
                allCandidates = false;
                break;
            }
        }
        if (allCandidates) {
            return CompletableFuture.runAsync(() -> {
                try {
                    for (TierFile tierFile : tierFiles) {
                        Path target = tierFilePath(tierFile);
                        if (isValidModelFile(target)) {
                            continue;
                        }
                        Path candidate = findLocalModelCandidate(tierFile.fileName());
                        if (candidate != null && isValidModelFile(candidate)) {
                            Files.copy(candidate, target, StandardCopyOption.REPLACE_EXISTING);
                        }
                    }
                } catch (IOException e) {
                    throw new CompletionException(e);
                }
            }).thenCompose(ignored -> {
                for (TierFile tierFile : tierFiles) {
                    if (!isValidModelFile(tierFilePath(tierFile))) {
                        deleteQuietly(tierFilePath(tierFile)); // 清掉残缺残留，转在线下载
                        return ensureModelDownloadAsync(tierFiles, progress);
                    }
                }
                return CompletableFuture.completedFuture(modelPath());
            });
        }
        return ensureModelDownloadAsync(tierFiles, progress);
    }

    /** 2) 在线下载：逐个分片，每个分片多个地址按顺序自动重试；残缺目标文件先删除再下载。
     *  固定直链全挂时自动查询仓库文件列表补齐分片（见 {@link #discoverAndDownload}）。 */
    private static CompletableFuture<Path> ensureModelDownloadAsync(List<TierFile> tierFiles,
                                                                   Consumer<DownloadProgress> progress) {
        CompletableFuture<Path> chain = CompletableFuture.completedFuture(modelPath());
        for (TierFile tierFile : tierFiles) {
            Path target = tierFilePath(tierFile);
            if (isValidModelFile(target)) {
                continue; // 已通过结构校验才跳过；残缺/0 字节坏文件视为缺失，重新下载
            }
            deleteQuietly(target); // 清掉残缺残留，避免覆盖时异常（.part 保留，供断点续传）
            chain = chain.thenCompose(ignored ->
                    downloadModelFromUrls(tierFile.urls(), 0, target, tierFile.fileName(), progress)
                            .exceptionallyCompose(error -> {
                                // 该分片全部固定地址失败 → 按仓库实际文件发现补齐（只做一次）
                                deleteQuietly(target);
                                return discoverAndDownload(tierFile, progress);
                            }));
        }
        return chain.thenApply(ignored -> modelPath());
    }

    /**
     * 分片自动发现：固定直链全部失败时，按仓库实际文件列表补齐分片。
     * 依次尝试多个仓库候选（官方门牌号 → ModelScope 镜像组织 AI-ModelScope/）与
     * 多个文件列表端点（ModelScope API 两种写法 → hf-mirror API → HF 官方 API），
     * 任一命中即返回全部匹配分片的直链。所有失败都记录诊断信息，便于玩家反馈定位。
     */
    private static CompletableFuture<Path> discoverAndDownload(TierFile failedFile, Consumer<DownloadProgress> progress) {
        CatalogEntry entry = catalogEntryForFile(failedFile.fileName());
        if (entry == null || entry.repoNs() == null || entry.repoNs().isBlank()) {
            return CompletableFuture.failedFuture(new IllegalStateException(
                    "模型分片 " + failedFile.fileName() + " 的所有下载地址均失败。"));
        }
        return discoverShardUrls(entry).thenCompose(result -> {
            if (result.urls().isEmpty()) {
                LOGGER.warn("本地模型分片自动发现失败: {} ({})", failedFile.fileName(), result.detail());
                return CompletableFuture.failedFuture(new IllegalStateException(
                        "模型分片 " + failedFile.fileName() + " 的所有下载地址均失败，且仓库文件列表不可用（"
                                + result.detail() + "）。"));
            }
            List<TierFile> discoveredFiles = new ArrayList<>();
            for (String url : result.urls()) {
                String name = url.substring(url.lastIndexOf('/') + 1);
                if (!name.isBlank()) {
                    discoveredFiles.add(new TierFile(name, List.of(url)));
                }
            }
            DISCOVERED.put(entry.key(), discoveredFiles);
            tierProbed = false; // 档位重算：modelPath/modelPresent 改用发现的分片清单
            LOGGER.info("本地模型分片自动发现成功: {} → {} 个文件", entry.displayName(), discoveredFiles.size());

            CompletableFuture<Path> chain = CompletableFuture.completedFuture(modelsDir());
            for (TierFile tierFile : discoveredFiles) {
                Path target = tierFilePath(tierFile);
                if (isValidModelFile(target)) {
                    continue;
                }
                deleteQuietly(target); // .part 保留，供断点续传
                String url = tierFile.urls().get(0);
                String label = "下载模型 " + entry.displayName() + " (" + tierFile.fileName() + ")";
                chain = chain.thenCompose(ignored -> downloadToFile(url, target, label, progress));
            }
            return chain;
        }).exceptionallyCompose(e -> CompletableFuture.failedFuture(
                new IllegalStateException("模型分片 " + failedFile.fileName() + " 自动发现失败：" + rootMessage(e))));
    }

    private static CatalogEntry catalogEntryForFile(String fileName) {
        if (fileName == null) {
            return null;
        }
        for (CatalogEntry entry : CATALOG) {
            for (TierFile tierFile : entry.files()) {
                if (tierFile.fileName().equalsIgnoreCase(fileName)) {
                    return entry;
                }
            }
        }
        return null;
    }

    /** 文件基底名：去掉 "-00001-of-00002" 这类分片序号，用于匹配同一模型的所有实际分片。 */
    private static String shardBaseName(String fileName) {
        int index = fileName == null ? -1 : fileName.lastIndexOf("-0000");
        return index > 0 ? fileName.substring(0, index) : (fileName == null ? "" : fileName);
    }

    /** 自动发现结果：命中的直链列表 + 尝试过的端点诊断。 */
    private record DiscoverResult(List<String> urls, String detail) {
    }

    /** 简易 JSON 获取结果：响应体 + 诊断信息（HTTP 状态 / 网络错误摘要）。 */
    private record JsonFetch(String body, String diagnostic) {
        boolean ok() {
            return body != null && !body.isBlank();
        }
    }

    /**
     * 依次查询多个仓库候选（官方 → ModelScope 镜像组织）与多个文件列表端点，
     * 返回首个命中的模型全部分片直链；全部失败时返回带诊断详情的空结果。
     */
    private static CompletableFuture<DiscoverResult> discoverShardUrls(CatalogEntry entry) {
        String base = shardBaseName(entry.files().isEmpty() ? "" : entry.files().get(0).fileName());
        List<String> repos = new ArrayList<>();
        repos.add(entry.repoNs() + "/" + entry.repoName());
        // ModelScope 官方镜像组织：HF 仓库在魔搭上的常见门牌号（如 AI-ModelScope/Qwen3-8B-GGUF）
        if (!"AI-ModelScope".equalsIgnoreCase(entry.repoNs())) {
            repos.add("AI-ModelScope/" + entry.repoName());
        }
        return discoverShardUrlsForRepos(repos, 0, base, new ArrayList<>());
    }

    private static CompletableFuture<DiscoverResult> discoverShardUrlsForRepos(List<String> repos, int index,
                                                                                String base, List<String> diagnostics) {
        if (index >= repos.size()) {
            String detail = diagnostics.isEmpty() ? "无可用端点" : String.join("; ", diagnostics);
            return CompletableFuture.completedFuture(new DiscoverResult(List.of(), detail));
        }
        String repo = repos.get(index);
        return discoverShardUrlsForRepo(repo, base).thenCompose(result -> {
            if (!result.urls().isEmpty()) {
                return CompletableFuture.completedFuture(result);
            }
            diagnostics.add(repo + " [" + result.detail() + "]");
            return discoverShardUrlsForRepos(repos, index + 1, base, diagnostics);
        });
    }

    /** 单个仓库候选：ModelScope API（带/不带 Revision）→ hf-mirror API → HF 官方 API。 */
    private static CompletableFuture<DiscoverResult> discoverShardUrlsForRepo(String repo, String base) {
        String modelScopeUrl = "https://modelscope.cn/api/v1/models/" + repo + "/repo/files?Recursive=true&Revision=master";
        return fetchJsonSafely(modelScopeUrl).thenCompose(msFetch -> {
            ProbedFiles msFiles = parseModelScopeFiles(msFetch.body(), repo, base);
            if (msFiles.found()) {
                return CompletableFuture.completedFuture(
                        new DiscoverResult(msFiles.urls(), "ModelScope " + msFetch.diagnostic()));
            }
            // ModelScope 第一写法无结果（404/结构不符/文件名对不上）→ 换不带 Revision 的写法再试
            String msUrl2 = "https://modelscope.cn/api/v1/models/" + repo + "/repo/files?Recursive=true";
            return fetchJsonSafely(msUrl2).thenCompose(msFetch2 -> {
                ProbedFiles msFiles2 = parseModelScopeFiles(msFetch2.body(), repo, base);
                if (msFiles2.found()) {
                    return CompletableFuture.completedFuture(
                            new DiscoverResult(msFiles2.urls(), "ModelScope " + msFetch2.diagnostic()));
                }
                String msDiag = msFetch.diagnostic() + " / " + msFetch2.diagnostic()
                        + " 可用GGUF: " + describeGgufNames(msFiles, 10)
                        + (msFiles2.ggufNames().isEmpty() ? "" : " / " + describeGgufNames(msFiles2, 10));
                return fetchJsonSafely("https://hf-mirror.com/api/models/" + repo).thenCompose(hfFetch -> {
                    ProbedFiles hfFiles = parseHfFiles(hfFetch.body(), repo, base);
                    if (hfFiles.found()) {
                        return CompletableFuture.completedFuture(
                                new DiscoverResult(hfFiles.urls(), "hf-mirror " + hfFetch.diagnostic()));
                    }
                    String hfNames = describeGgufNames(hfFiles, 10);
                    return fetchJsonSafely("https://huggingface.co/api/models/" + repo).thenApply(hfFetch2 -> {
                        ProbedFiles hfFiles2 = parseHfFiles(hfFetch2.body(), repo, base);
                        String hf2Names = describeGgufNames(hfFiles2, 10);
                        if (hfFiles2.found()) {
                            return new DiscoverResult(hfFiles2.urls(), "HF " + hfFetch2.diagnostic());
                        }
                        String diag = "ModelScope " + msDiag
                                + "; hf-mirror " + hfFetch.diagnostic()
                                + (hfNames.isEmpty() ? "" : " 可用GGUF: " + hfNames)
                                + "; HF " + hfFetch2.diagnostic()
                                + (hf2Names.isEmpty() ? "" : " 可用GGUF: " + hf2Names);
                        return new DiscoverResult(List.of(), diag);
                    });
                });
            });
        });
    }

    /** GET 返回响应体 + 诊断；任何失败（网络/超时/非 2xx）都不抛异常。 */
    private static CompletableFuture<JsonFetch> fetchJsonSafely(String url) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(10))
                .header("Accept", "application/json")
                .header("User-Agent", "HerobrineCompanion/1.0")
                .GET().build();
        return CLIENT.sendAsync(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
                .handle((response, error) -> {
                    if (error != null) {
                        return new JsonFetch("", "网络错误 " + error.getClass().getSimpleName());
                    }
                    if (response.statusCode() != 200) {
                        return new JsonFetch("", "HTTP " + response.statusCode());
                    }
                    return new JsonFetch(response.body(), "HTTP 200");
                });
    }

    /** 文件列表解析结果：命中的下载直链 + 仓库全部 GGUF 文件名（诊断用）。 */
    private record ProbedFiles(List<String> urls, List<String> ggufNames) {
        boolean found() {
            return !urls.isEmpty();
        }
    }

    /**
     * 从仓库文件路径里挑出该模型的 GGUF，匹配策略逐级放宽（都不依赖内建文件名精确）：
     * ① 基底名前缀（忽略大小写，覆盖 qwen3-8b-q4_k_m-00001-of-00002.gguf 等分片风格）；
     * ② 名称含 q4_k_m；③ 名称含 q4；④ 名称含 q2/q3/q5/q6/q8/iq 系；⑤ 仓库里唯一一个 GGUF。
     */
    private static ProbedFiles matchGgufFiles(List<String> paths, String base, String urlPrefix) {
        List<String> allGguf = new ArrayList<>();
        List<String> matched = new ArrayList<>();
        String baseLower = base == null ? "" : base.toLowerCase(Locale.ROOT);
        for (String path : paths) {
            String name = pathName(path);
            if (!name.toLowerCase(Locale.ROOT).endsWith(".gguf")) {
                continue;
            }
            allGguf.add(name);
            if (!baseLower.isEmpty() && name.toLowerCase(Locale.ROOT).startsWith(baseLower)) {
                matched.add(path);
            }
        }
        if (matched.isEmpty()) {
            matched = pickByQuant(paths, "q4_k_m");
        }
        if (matched.isEmpty()) {
            matched = pickByQuant(paths, "q4");
        }
        if (matched.isEmpty()) {
            for (String token : new String[]{"q2", "q3", "q5", "q6", "q8", "iq4", "iq3", "iq2"}) {
                matched = pickByQuant(paths, token);
                if (!matched.isEmpty()) {
                    break;
                }
            }
        }
        if (matched.isEmpty() && allGguf.size() == 1) {
            for (String path : paths) {
                if (pathName(path).toLowerCase(Locale.ROOT).endsWith(".gguf")) {
                    matched.add(path);
                    break;
                }
            }
        }
        List<String> urls = new ArrayList<>();
        for (String path : matched) {
            urls.add(urlPrefix + path);
        }
        return new ProbedFiles(urls, allGguf);
    }

    /** 仓库内文件名包含指定 token（忽略大小写）的全部 GGUF 路径。 */
    private static List<String> pickByQuant(List<String> paths, String token) {
        List<String> hits = new ArrayList<>();
        String lowerToken = token.toLowerCase(Locale.ROOT);
        for (String path : paths) {
            String name = pathName(path);
            if (!name.toLowerCase(Locale.ROOT).endsWith(".gguf")) {
                continue;
            }
            if (name.toLowerCase(Locale.ROOT).contains(lowerToken)) {
                hits.add(path);
            }
        }
        return hits;
    }

    private static String pathName(String path) {
        int slash = path == null ? -1 : path.lastIndexOf('/');
        return slash >= 0 ? path.substring(slash + 1) : (path == null ? "" : path);
    }

    /** ModelScope repo/files API：Data.Files[].Path。 */
    private static ProbedFiles parseModelScopeFiles(String body, String repo, String base) {
        if (body == null || body.isBlank()) {
            return new ProbedFiles(List.of(), List.of());
        }
        try {
            List<String> paths = new ArrayList<>();
            JsonObject root = JsonParser.parseString(body).getAsJsonObject();
            if (root.has("Data") && root.getAsJsonObject("Data").has("Files")) {
                for (JsonElement element : root.getAsJsonObject("Data").getAsJsonArray("Files")) {
                    if (element.isJsonObject() && element.getAsJsonObject().has("Path")) {
                        paths.add(element.getAsJsonObject().get("Path").getAsString());
                    }
                }
            }
            return matchGgufFiles(paths, base, "https://modelscope.cn/models/" + repo + "/resolve/master/");
        } catch (Exception ignored) {
            return new ProbedFiles(List.of(), List.of());
        }
    }

    /** HuggingFace API：siblings[].rfilename。 */
    private static ProbedFiles parseHfFiles(String body, String repo, String base) {
        if (body == null || body.isBlank()) {
            return new ProbedFiles(List.of(), List.of());
        }
        try {
            List<String> paths = new ArrayList<>();
            JsonObject root = JsonParser.parseString(body).getAsJsonObject();
            if (root.has("siblings")) {
                for (JsonElement element : root.getAsJsonArray("siblings")) {
                    if (element.isJsonObject() && element.getAsJsonObject().has("rfilename")) {
                        paths.add(element.getAsJsonObject().get("rfilename").getAsString());
                    }
                }
            }
            return matchGgufFiles(paths, base, "https://hf-mirror.com/" + repo + "/resolve/main/");
        } catch (Exception ignored) {
            return new ProbedFiles(List.of(), List.of());
        }
    }

    /** 诊断串：仓库 GGUF 文件名单（去重、截断），多个仓库换行连接。 */
    private static String describeGgufNames(ProbedFiles probed, int limit) {
        if (probed.ggufNames().isEmpty()) {
            return "";
        }
        List<String> names = new ArrayList<>();
        for (String name : probed.ggufNames()) {
            if (!names.contains(name)) {
                names.add(name);
            }
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < names.size() && i < limit; i++) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(names.get(i));
        }
        if (names.size() > limit) {
            sb.append(" …(共 ").append(names.size()).append(" 个)");
        }
        return sb.toString();
    }

    /** 依次尝试多个模型下载地址，全部失败才报错；单个地址失败时清理残留文件。 */
    private static CompletableFuture<Path> downloadModelFromUrls(List<String> urls, int index, Path target,
                                                                 String fileName, Consumer<DownloadProgress> progress) {
        return downloadModelFromUrls(urls, index, 0, target, fileName, progress);
    }

    /**
     * 依次尝试多个下载地址（{@code attempt} = 当前地址已尝试次数）。
     *
     * <p>两层容错：① 同一地址网络抖动自动重试（退避 2s → 5s，最多 {@value #PER_URL_MAX_ATTEMPTS} 次，
     * 从 .part 断点续传，不浪费已下载的字节）；② 连接超时/DNS 失败的主机记入
     * {@link #UNREACHABLE_HOSTS}，本次会话内其余指向它的地址直接跳过，不再逐个空等 10 秒。
     * 本地写入类失败（磁盘满/被占用）不重试——重试无效，直接换源并保留断点。</p>
     */
    private static CompletableFuture<Path> downloadModelFromUrls(List<String> urls, int index, int attempt,
                                                                 Path target, String fileName,
                                                                 Consumer<DownloadProgress> progress) {
        // 跳过本次会话内已确认连不上的源（不再逐个空等 10 秒 connect timeout）
        int cursor = index;
        boolean skipped = false;
        while (cursor < urls.size() && UNREACHABLE_HOSTS.contains(safeHost(urls.get(cursor)))) {
            LOGGER.info("跳过已知连不上的下载源: {}", safeHost(urls.get(cursor)));
            cursor++;
            skipped = true;
        }
        final int nextIndex = cursor;
        // 换了新地址就重置尝试次数（旧地址的抖动次数与新地址无关）
        final int nextAttempt = skipped ? 0 : attempt;
        if (nextIndex >= urls.size()) {
            return CompletableFuture.failedFuture(new IllegalStateException(
                    "模型分片 " + fileName + " 的所有下载地址均失败（共 " + urls.size() + " 个）。"));
        }
        String url = urls.get(nextIndex);
        String host = safeHost(url);
        String label = selectedModelTier().files().size() > 1
                ? "下载模型 " + selectedModelTier().displayName() + " (" + fileName.replaceAll("^.*-0000(\\d)-of.*$", "分片$1") + ", " + host + ")"
                : "下载模型 " + selectedModelTier().displayName() + " (" + host + ")";
        return downloadToFile(url, target, label, progress)
                .exceptionallyCompose(error -> {
                    // 单个地址失败：记日志便于玩家反馈定位；清理残留（.part 保留，供断点续传）
                    LOGGER.warn("模型分片 {} 从 {} 下载失败: {}", fileName, host, rootMessage(error));
                    deleteQuietly(target);
                    if (isUnreachableHost(error)) {
                        // 根本连不上：拉黑主机，剩下的同主机地址不再逐个等待超时
                        UNREACHABLE_HOSTS.add(host);
                        return downloadModelFromUrls(urls, nextIndex + 1, 0, target, fileName, progress);
                    }
                    boolean localProblem = hasCause(error, java.nio.file.FileSystemException.class);
                    if (!localProblem && nextAttempt + 1 < PER_URL_MAX_ATTEMPTS) {
                        int delaySeconds = nextAttempt == 0 ? 2 : 5;
                        LOGGER.info("{} 秒后重试 {}（第 {}/{} 次）", delaySeconds, host,
                                nextAttempt + 2, PER_URL_MAX_ATTEMPTS);
                        return CompletableFuture
                                .supplyAsync(() -> target, CompletableFuture.delayedExecutor(
                                        delaySeconds, java.util.concurrent.TimeUnit.SECONDS))
                                .thenCompose(ignored -> downloadModelFromUrls(urls, nextIndex, nextAttempt + 1,
                                        target, fileName, progress));
                    }
                    return downloadModelFromUrls(urls, nextIndex + 1, 0, target, fileName, progress);
                });
    }

    /** 异常链中是否含指定类型的异常（自引用环安全）。 */
    private static boolean hasCause(Throwable throwable, Class<? extends Throwable> type) {
        Throwable current = throwable;
        Set<Throwable> seen = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        while (current != null && seen.add(current)) {
            if (type.isInstance(current)) {
                return true;
            }
            current = current.getCause() == current ? null : current.getCause();
        }
        return false;
    }

    /** 是否说明"主机根本连不上"（连接超时 / DNS 失败 / 无路由）：这类源重试没有意义。 */
    private static boolean isUnreachableHost(Throwable error) {
        if (hasCause(error, java.net.http.HttpConnectTimeoutException.class)
                || hasCause(error, java.net.UnknownHostException.class)
                || hasCause(error, java.net.NoRouteToHostException.class)) {
            return true;
        }
        String msg = rootMessage(error);
        if (msg == null) {
            return false;
        }
        String lower = msg.toLowerCase(Locale.ROOT);
        return lower.contains("connect timed out") || lower.contains("connection timed out")
                || lower.contains("connection refused");
    }

    private static String safeHost(String url) {
        try {
            String host = URI.create(url).getHost();
            return host == null || host.isBlank() ? "未知源" : host;
        } catch (Exception ignored) {
            return "未知源";
        }
    }

    /** 本地候选：游戏目录下常见位置 + 默认模型目录 + 开发工作区 .llm-work（按指定文件名）。
     *  返回第一个通过 GGUF 校验的候选；残缺/损坏的候选会被跳过（由在线下载兜底）。 */
    private static Path findLocalModelCandidate(String fileName) {
        Path gameDir = Minecraft.getInstance().gameDirectory.toPath();
        Path parent = gameDir.getParent();
        List<Path> candidates = new java.util.ArrayList<>(4);
        candidates.add(gameDir.resolve(fileName));
        candidates.add(gameDir.resolve("mods").resolve(fileName));
        candidates.add(gameDir.resolve("herobrine_local").resolve("models").resolve(fileName));
        if (parent != null) {
            candidates.add(parent.resolve(".llm-work").resolve(fileName));
        }
        for (Path path : candidates) {
            if (isValidModelFile(path)) {
                return path;
            }
        }
        return null;
    }

    /** 启动服务器进程并轮询 /health 直到就绪。引擎带 CUDA 库时默认上卡；初始化失败自动降级 CPU 重试。 */
    private static CompletableFuture<Boolean> startServerAsync(Consumer<DownloadProgress> progress) {
        boolean gpu = gpuEngine;
        return startServerProcessAsync(gpu, progress).thenCompose(ok -> {
            if (ok && gpu) {
                // 新引擎在 CUDA 初始化失败时会自动回退 CPU 且 /health 照常通过，
                // 仅靠"进程存活"会误判为 GPU 模式成功。这里复核 CUDA 日志关键字。
                // 命中"failed to initialize CUDA" 等时如实记录降级原因并继续按 CPU 服务。
                String cudaLogTail = tailOfFile(rootDir().resolve("server.cuda.log"), 12000);
                if (cudaLogTail != null
                        && cudaLogTail.toLowerCase(Locale.ROOT).contains("failed to initialize cuda")) {
                    gpuEngine = false;
                    gpuDowngradeReason = classifyCudaDowngrade(-1, cudaLogTail);
                    LOGGER.warn("检测到 NVIDIA 显卡，但引擎启动时 CUDA 初始化失败并自动回退 CPU 运行。"
                            + "已如实记录降级原因：{}", gpuDowngradeReason);
                    if (progress != null) {
                        progress.accept(new DownloadProgress(0, 0,
                                "注意：CUDA 初始化失败，实际按 CPU 运行。原因：" + gpuDowngradeReason));
                    }
                }
                return CompletableFuture.completedFuture(true);
            }
            if (ok || !gpu) {
                return CompletableFuture.completedFuture(ok);
            }
            // CUDA 初始化失败（显存不足/驱动过旧/运行库缺失等）：先取退出码与 CUDA 日志，
            // 分类出具体原因；再停止失败的 GPU 进程改纯 CPU 模式重试，避免进程占住 8090。
            int exitCode = -1;
            Process crashed = serverProcess;
            if (crashed != null && !crashed.isAlive()) {
                try {
                    exitCode = crashed.exitValue();
                } catch (IllegalThreadStateException ignored) {
                    // 进程刚退出竞争：按未知处理
                }
            }
            String cudaLogTail = tailOfFile(rootDir().resolve("server.cuda.log"), 6000);
            String shortTail = cudaLogTail == null ? "" : cudaLogTail.trim().replaceAll("\\s+", " ");
            if (shortTail.length() > 300) {
                shortTail = shortTail.substring(0, 300);
            }
            stopServerProcess();
            gpuEngine = false;
            gpuDowngradeReason = classifyCudaDowngrade(exitCode, cudaLogTail);
            LOGGER.warn("检测到 NVIDIA 显卡，但 CUDA 启动失败，已降级 CPU 推理。原因：{}（server.cuda.log 尾部：{}）",
                    gpuDowngradeReason, shortTail);
            if (progress != null) {
                progress.accept(new DownloadProgress(0, 0, "显卡初始化失败，改用 CPU 模式。原因：" + gpuDowngradeReason));
            }
            return startServerProcessAsync(false, progress);
        });
    }

    /** 读文件尾部文本（下载/启动日志诊断用）；读取失败返回空串。 */
    private static String tailOfFile(Path path, int maxChars) {
        try {
            if (path == null || !Files.isRegularFile(path)) {
                return "";
            }
            byte[] bytes = Files.readAllBytes(path);
            int skip = Math.max(0, bytes.length - maxChars);
            return new String(bytes, skip, bytes.length - skip, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return "";
        }
    }

    /** 根据 CUDA 日志尾部与进程退出码，给出"检测到显卡却降级 CPU"的具体原因（翻译键组装；页面/日志展示）。 */
    private static String classifyCudaDowngrade(int exitCode, String logTail) {
        String lower = logTail == null ? "" : logTail.toLowerCase(Locale.ROOT);
        String sizeHint = cudaSizeHint();
        // 引擎自动回退 CPU 场景（进程仍存活）：ggml 的 CUDA 后端初始化失败，错误串常为 (null)，
        // 多为驱动过旧（CUDA 13 运行库需要较新 NVIDIA 驱动）或运行库缺失/不匹配。
        if (lower.contains("failed to initialize cuda") || lower.contains("ggml_cuda_init")) {
            return tr("gui.herobrine_companion.local_model.downgrade_cuda_init_failed", sizeHint);
        }
        if (lower.contains("out of memory") || lower.contains("not enough memory")
                || lower.contains("failed to allocate") || lower.contains("cuda error: 2")) {
            return tr("gui.herobrine_companion.local_model.downgrade_oom", sizeHint);
        }
        if (lower.contains("no kernel image") || lower.contains("cuda driver version")
                || (lower.contains("driver") && (lower.contains("version") || lower.contains("not supported")))) {
            return tr("gui.herobrine_companion.local_model.downgrade_driver", sizeHint);
        }
        if (lower.contains("cannot load library") || lower.contains("libcublas")
                || lower.contains("ggml-cuda") || lower.contains("cuda.so") || lower.contains("could not load")) {
            return tr("gui.herobrine_companion.local_model.downgrade_lib_missing");
        }
        String tail = logTail == null ? "" : logTail.trim().replaceAll("\\s+", " ");
        if (tail.length() > 240) {
            tail = "…" + tail.substring(tail.length() - 240);
        }
        if (tail.isEmpty()) {
            tail = tr("gui.herobrine_companion.local_model.downgrade_no_log");
        }
        return tr("gui.herobrine_companion.local_model.downgrade_generic", exitCode, sizeHint, tail);
    }

    private static void stopServerProcess() {
        Process p = serverProcess;
        serverProcess = null;
        if (p == null || !p.isAlive()) {
            return;
        }
        p.destroy();
        try {
            if (!p.waitFor(3, java.util.concurrent.TimeUnit.SECONDS)) {
                p.destroyForcibly();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            p.destroyForcibly();
        }
    }

    /**
     * 退出存档时调用：停止本模组拉起的 llama-server 进程，释放内存/显存；
     * 顺手置位退出标记，防止下载中途退出存档后进程仍被拉起来。
     * 只处理本模组持有的进程，不碰玩家手动启动的外部服务器。
     */
    public static void shutdownForSession() {
        sessionClosed = true;
        stopServerProcess();
    }

    /** 停止本模组拉起的 llama-server 进程（不处理外部进程；最多等待 3 秒）。 */
    public static void stopOwnedServer() {
        stopServerProcess();
    }

    /** 当前已知的本地推理服务器运行状态（进程存活或最近一次健康探测通过）。 */
    private static volatile boolean serverRunning = false;

    /** 本地推理服务器是否在运行（同步读取缓存；页面状态行用）。 */
    public static boolean isServerRunningCached() {
        Process p = serverProcess;
        if (p != null && p.isAlive()) {
            serverRunning = true;
            return true;
        }
        return serverRunning;
    }

    /** 异步健康探测 8090（写缓存，供页面状态行显示；失败/超时视为未运行）。 */
    public static CompletableFuture<Boolean> probeServerRunningAsync() {
        Process p = serverProcess;
        if (p != null && p.isAlive()) {
            serverRunning = true;
            return CompletableFuture.completedFuture(true);
        }
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + DEFAULT_PORT + "/health"))
                .timeout(Duration.ofSeconds(2))
                .GET().build();
        return CLIENT.sendAsync(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
                .handle((response, error) -> {
                    boolean running = error == null && response != null
                            && response.statusCode() >= 200 && response.statusCode() < 300;
                    serverRunning = running;
                    return running;
                });
    }

    /**
     * 停止本地模型并回收进程（页面"关闭"动作）：
     * 先停掉本模组拉起的服务器，再查找仍监听默认端口 8090 的 llama-server 残留进程并结束
     * （按进程名二次校验，绝不误杀其他占用 8090 的程序）。异步执行，不阻塞渲染线程。
     */
    public static CompletableFuture<Void> stopServerAndReclaimPortAsync() {
        stopOwnedServer();
        serverRunning = false;
        return CompletableFuture.runAsync(() -> killLlamaServerOnPort(DEFAULT_PORT));
    }

    private static void killLlamaServerOnPort(int port) {
        String os = System.getProperty("os.name", "");
        try {
            if (os.toLowerCase(Locale.ROOT).contains("win")) {
                killLlamaServerOnPortWindows(port);
            } else {
                killLlamaServerOnPortUnix(port);
            }
        } catch (Exception ignored) {
            // 进程回收失败不影响主流程（下次准备/关闭会再尝试）
        }
    }

    private static void killLlamaServerOnPortWindows(int port) {
        Set<String> pids = listeningPidsOnPort(port);
        for (String pid : pids) {
            if (isLlamaServerPidWindows(pid)) {
                taskKill(pid);
            }
        }
    }

    /** netstat -ano 解析：监听给定端口的全部 PID（不限进程名）。 */
    private static Set<String> listeningPidsOnPort(int port) {
        Set<String> pids = new LinkedHashSet<>();
        try {
            ProcessBuilder pb = new ProcessBuilder("netstat", "-ano", "-p", "tcp");
            pb.redirectErrorStream(true);
            Process p = pb.start();
            if (!p.waitFor(5, java.util.concurrent.TimeUnit.SECONDS)) {
                p.destroyForcibly();
                return pids;
            }
            String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            String marker = ":" + port;
            for (String line : out.split("\\R")) {
                if (!line.contains("LISTENING") || !line.contains(marker)) {
                    continue;
                }
                String[] parts = line.trim().split("\\s+");
                if (parts.length >= 5) {
                    String pid = parts[parts.length - 1].trim();
                    if (!pid.isEmpty() && pid.chars().allMatch(Character::isDigit)) {
                        pids.add(pid);
                    }
                }
            }
        } catch (Exception ignored) {
            // netstat 不可用时按无残留处理
        }
        return pids;
    }

    private static boolean isLlamaServerPidWindows(String pid) {
        try {
            ProcessBuilder pb = new ProcessBuilder("tasklist", "/FI", "PID eq " + pid, "/FO", "CSV", "/NH");
            pb.redirectErrorStream(true);
            Process p = pb.start();
            if (!p.waitFor(4, java.util.concurrent.TimeUnit.SECONDS)) {
                p.destroyForcibly();
                return false;
            }
            String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            return out.toLowerCase(Locale.ROOT).contains("llama-server");
        } catch (Exception e) {
            return false;
        }
    }

    private static void taskKill(String pid) {
        try {
            new ProcessBuilder("taskkill", "/PID", pid, "/T", "/F").redirectErrorStream(true).start();
        } catch (Exception ignored) {
            // 结束失败下次重试
        }
    }

    private static void killLlamaServerOnPortUnix(int port) {
        for (String pid : listeningPidsOnPortUnix(port)) {
            if (isLlamaServerPidUnix(pid)) {
                killPidUnix(pid);
            }
        }
    }

    private static Set<String> listeningPidsOnPortUnix(int port) {
        Set<String> pids = new LinkedHashSet<>();
        try {
            ProcessBuilder pb = new ProcessBuilder("lsof", "-t", "-iTCP:" + port, "-sTCP:LISTEN");
            pb.redirectErrorStream(true);
            Process p = pb.start();
            if (!p.waitFor(5, java.util.concurrent.TimeUnit.SECONDS)) {
                p.destroyForcibly();
                return pids;
            }
            String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            for (String line : out.split("\\R")) {
                if (!line.isBlank()) {
                    pids.add(line.trim());
                }
            }
        } catch (Exception ignored) {
            // lsof 不可用时按无残留处理
        }
        return pids;
    }

    private static boolean isLlamaServerPidUnix(String pid) {
        try {
            ProcessBuilder pb = new ProcessBuilder("ps", "-p", pid, "-o", "comm=");
            pb.redirectErrorStream(true);
            Process p = pb.start();
            if (!p.waitFor(4, java.util.concurrent.TimeUnit.SECONDS)) {
                p.destroyForcibly();
                return false;
            }
            String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            return out.toLowerCase(Locale.ROOT).contains("llama");
        } catch (Exception e) {
            return false;
        }
    }

    private static void killPidUnix(String pid) {
        try {
            new ProcessBuilder("kill", "-9", pid).redirectErrorStream(true).start();
        } catch (Exception ignored) {
            // 结束失败下次重试
        }
    }

    private static CompletableFuture<Boolean> startServerProcessAsync(boolean gpuMode, Consumer<DownloadProgress> progress) {
        return CompletableFuture.supplyAsync(() -> {
            // 退出存档后不应再拉起服务器（下载完成但玩家已离开世界时防止进程留在后台）。
            if (sessionClosed) {
                return false;
            }
            try {
                Path logPath = rootDir().resolve(gpuMode ? "server.cuda.log" : "server.cpu.log");
                List<String> command = new java.util.ArrayList<>();
                command.add(serverPath().toString());
                command.add("-m");
                command.add(modelPath().toString());
                command.add("--host");
                command.add("127.0.0.1");
                command.add("--port");
                command.add(String.valueOf(DEFAULT_PORT));
                command.add("-c");
                command.add(String.valueOf(LOCAL_CTX));
                command.add("-t");
                command.add(String.valueOf(Math.max(2, Runtime.getRuntime().availableProcessors())));
                command.add("--alias");
                command.add("herobrine");
                if (gpuMode) {
                    command.add("-ngl");
                    command.add("99"); // 全量上卡：3B Q4_K 权重 1.94GB + 16k KV ≈ 3.2GB，8G 显存绰绰有余
                }
                command.add("--jinja");
                ProcessBuilder pb = new ProcessBuilder(command);
                pb.redirectErrorStream(true);
                pb.redirectOutput(logPath.toFile());
                serverProcess = pb.start();
                // 启动瞬间退出存档（竞态）：刚拉起的进程立即关掉，不占内存。
                if (sessionClosed) {
                    stopServerProcess();
                    return false;
                }
                Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                    Process p = serverProcess;
                    if (p != null) {
                        p.destroy();
                    }
                }));
                return true;
            } catch (IOException e) {
                throw new CompletionException(e);
            }
        }).thenCompose(started -> started
                ? waitForHealthAsync(progress)
                : CompletableFuture.completedFuture(false));
    }

    private static CompletableFuture<Boolean> waitForHealthAsync(Consumer<DownloadProgress> progress) {
        return waitForHealthInternal(progress, 0);
    }

    /**
     * 轮询 /health 直到就绪。单次连接失败/超时属于瞬时错误，继续轮询而不是直接判失败
     * （服务器加载模型期间可能短暂拒绝连接）；只有进程退出或达到次数上限（90×2s=3 分钟）才放弃。
     */
    private static CompletableFuture<Boolean> waitForHealthInternal(Consumer<DownloadProgress> progress, int attempt) {
        if (attempt >= 90) { // 90 * 2s = 3 分钟
            return CompletableFuture.completedFuture(false);
        }
        // 进程已退出（如模型损坏导致加载崩溃）：立即放弃，避免干等 3 分钟后才重试。
        Process p = serverProcess;
        if (p != null && !p.isAlive()) {
            return CompletableFuture.completedFuture(false);
        }
        if (progress != null) {
            progress.accept(new DownloadProgress(attempt, 90, "等待模型加载..."));
        }
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + DEFAULT_PORT + "/health"))
                .timeout(Duration.ofSeconds(2))
                .GET().build();
        return CLIENT.sendAsync(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
                .handle((response, error) -> {
                    if (error != null) {
                        return null; // 瞬时错误（连接被拒/超时）→ 继续轮询
                    }
                    if (response.statusCode() >= 200 && response.statusCode() < 300) {
                        try {
                            JsonObject obj = JsonParser.parseString(response.body()).getAsJsonObject();
                            if (obj.has("status") && "ok".equalsIgnoreCase(obj.get("status").getAsString())) {
                                return Boolean.TRUE;
                            }
                        } catch (Exception ignored) {
                            // 非标准响应按失败处理继续轮询
                        }
                    }
                    return Boolean.FALSE;
                })
                .thenCompose(result -> result == Boolean.TRUE
                        ? CompletableFuture.completedFuture(true)
                        : CompletableFuture.runAsync(() -> {
                            // 2 秒后继续轮询
                        }, CompletableFuture.delayedExecutor(2, java.util.concurrent.TimeUnit.SECONDS))
                                .thenCompose(ignored -> waitForHealthInternal(progress, attempt + 1)));
    }

    /**
     * 带进度回调的下载（支持重定向）。先写 .part 临时文件，完整校验通过后再原子改名，
     * 中断/退出游戏时不会在目标位置留下"看起来完整"的残缺文件。
     *
     * <p>断点续传：若 .part 已存在（上次中断留下的进度），自动带上 {@code Range: bytes=N-} 从断点
     * 继续；服务端返回 206 则续写，返回 200 则从头重下，返回 416 则清空重来。任何一次失败
     * 都保留 .part，换下载源重试同样从断点续传。</p>
     *
     * <p>并发去重：同一 .part 的下载请求（自动连接 + 页面手动下载）只会执行一份，
     * 彻底避免两条链路同时写一个文件导致 Windows 文件占用错误。</p>
     */
    private static CompletableFuture<Path> downloadToFile(String url, Path target,
                                                          String label, Consumer<DownloadProgress> progress) {
        Path part = target.resolveSibling(target.getFileName() + ".part");
        final long partBytes = existingPartBytes(part);
        if (partBytes > 0L) {
            deleteQuietly(target); // 目标文件不应存在；防止半成品挂正名
        }
        return ACTIVE_DOWNLOADS.computeIfAbsent(part, ignored ->
                downloadToFileInternal(url, part, target, label, progress, partBytes, partBytes > 0L)
                        .whenComplete((result, error) -> ACTIVE_DOWNLOADS.remove(part)));
    }

    /** .part 已有字节数；不存在/无法读取按 0 处理（断点续传起点）。 */
    private static long existingPartBytes(Path part) {
        try {
            return Files.isRegularFile(part) ? Files.size(part) : 0L;
        } catch (IOException ignored) {
            return 0L;
        }
    }

    /** 本地写入失败时的可读提示（磁盘满 / 只读 / 被其他程序占用）。 */
    private static String localWriteHint(Path part, IOException e) {
        String simple = e.getClass().getSimpleName();
        String detail = e.getMessage();
        String path = part == null ? "" : part.toString();
        if (detail == null || detail.isBlank() || detail.equals(path)) {
            detail = "";
        }
        return "本地写入失败（" + simple + (detail.isEmpty() ? "" : ": " + detail) + "）: " + path
                + "。可能原因：磁盘已满 / 目录只读 / 文件被其他程序（杀毒软件或另一个游戏实例）占用。"
                + "可检查下载目录属性后重试，.part 断点将保留";
    }

    private static CompletableFuture<Path> downloadToFileInternal(String url, Path part, Path target,
                                                                  String label, Consumer<DownloadProgress> progress,
                                                                  long offset, boolean ranged) {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofMinutes(10))
                .header("Accept", "application/octet-stream");
        if (ranged && offset > 0L) {
            builder.header("Range", "bytes=" + offset + "-");
        }
        HttpRequest request = builder.GET().build();
        return CLIENT.sendAsync(request, HttpResponse.BodyHandlers.ofInputStream())
                .thenCompose(response -> {
                    int status = response.statusCode();
                    if (status == 416) {
                        // 断点超过文件实际大小（.part 比服务器文件还大）：清空从头下载
                        deleteQuietly(part);
                        return downloadToFileInternal(url, part, target, label, progress, 0L, false);
                    }
                    if (status != 200 && status != 206) {
                        return CompletableFuture.failedFuture(new IOException("HTTP " + status + " for " + url));
                    }
                    boolean resuming = status == 206;
                    long resumedOffset = resuming ? offset : 0L;
                    long remaining = response.headers().firstValueAsLong("Content-Length").orElse(-1L);
                    long totalBytes = remaining > 0L ? resumedOffset + remaining : -1L;
                    return CompletableFuture.runAsync(() -> {
                        // 磁盘空间预检：已知总大小时，预留 512MB 余量，磁盘不足直接给出明确提示
                        if (totalBytes > 0L) {
                            try {
                                long usable = Files.getFileStore(part.getParent()).getUsableSpace();
                                if (usable >= 0L && usable < totalBytes + 512L * 1024 * 1024) {
                                    throw new IOException(String.format(Locale.ROOT,
                                            "下载目录所在磁盘可用空间不足（剩余 %.1f GB，本文件约需 %.1f GB）",
                                            usable / 1_000_000_000.0D, totalBytes / 1_000_000_000.0D));
                                }
                            } catch (IOException e) {
                                if (e instanceof java.nio.file.FileSystemException) {
                                    throw new CompletionException(new IOException(localWriteHint(part, e), e));
                                }
                                throw new CompletionException(e);
                            }
                        }
                        // 目录必须存在：缺失时 getFileStore / newOutputStream 都会抛 FileSystemException
                        Path partParent = part.getParent();
                        if (partParent != null) {
                            try {
                                Files.createDirectories(partParent);
                            } catch (IOException e) {
                                throw new CompletionException(new IOException(localWriteHint(part, e), e));
                            }
                        }
                        try (InputStream in = response.body();
                             OutputStream out = Files.newOutputStream(part,
                                     // CREATE 必带：.part 不存在时，单独给 TRUNCATE_EXISTING 会抛
                                     // NoSuchFileException（消息只有裸路径），表现为"每次下载都失败"
                                     StandardOpenOption.CREATE,
                                     StandardOpenOption.WRITE,
                                     resumedOffset > 0L ? StandardOpenOption.APPEND : StandardOpenOption.TRUNCATE_EXISTING)) {
                            byte[] buffer = new byte[64 * 1024];
                            long written = resumedOffset;
                            int read;
                            // 进度回调节流：回调会切到渲染线程更新界面，10MB/s 时原来每秒要提交
                            // 一百多个任务，主线程队列被刷屏；100ms 一次足够平滑（界面约 10fps 刷新）
                            long lastEmitNanos = System.nanoTime();
                            while ((read = in.read(buffer)) != -1) {
                                out.write(buffer, 0, read);
                                written += read;
                                if (progress != null) {
                                    long nowNanos = System.nanoTime();
                                    if (nowNanos - lastEmitNanos >= PROGRESS_EMIT_INTERVAL_NANOS) {
                                        lastEmitNanos = nowNanos;
                                        progress.accept(new DownloadProgress(written, totalBytes, label));
                                    }
                                }
                            }
                            // 收尾务必回调一次，保证界面拿到 100% 与最终字节数
                            if (progress != null) {
                                progress.accept(new DownloadProgress(written, totalBytes, label));
                            }
                            out.flush();
                            // 服务器声明了总大小但不一致（提前 EOF / 中途断连）：保留 .part 供断点续传
                            if (totalBytes > 0L && written != totalBytes) {
                                throw new IOException("下载不完整: 期望 " + totalBytes + " 字节, 实际 " + written + " 字节");
                            }
                            if (written <= 0L) {
                                throw new IOException("下载内容为空: " + url);
                            }
                        } catch (IOException e) {
                            // 失败不删 .part：下次下载从断点继续；写入类错误给出可读原因
                            if (e instanceof java.nio.file.FileSystemException) {
                                throw new CompletionException(new IOException(localWriteHint(part, e), e));
                            }
                            throw new CompletionException(e);
                        }
                    }).thenApply(ignored -> {
                        try {
                            // 完整下载成功后才改名成目标文件（同卷移动，覆盖旧残留）
                            Files.move(part, target, StandardCopyOption.REPLACE_EXISTING);
                        } catch (IOException e) {
                            throw new CompletionException(e);
                        }
                        return target;
                    });
                });
    }

    /** 静默删除文件（不存在或删除失败都不报错）。 */
    private static void deleteQuietly(Path path) {
        if (path == null) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // 清理失败不影响主流程
        }
    }

    /** 解压 zip 到目标目录（覆盖同名文件）。先解压到临时目录再整体搬入目标目录：
     *  中途被中断（游戏退出/崩溃）时不会在目标目录留下半个文件——残缺的 exe/dll 会被
     *  {@link #serverPresent()} 的 MZ 校验拦下，但避免产生"看起来完整"的坏文件更稳妥。 */
    private static void unzip(Path zipPath, Path targetDir) throws IOException {
        Files.createDirectories(targetDir);
        // 清理上次解压中断残留的临时目录（bin.tmp-xxx）
        Path parentDir = targetDir.getParent();
        if (parentDir != null) {
            try (Stream<Path> walk = Files.list(parentDir)) {
                for (Path sibling : (Iterable<Path>) walk.filter(path ->
                        path.getFileName().toString().startsWith(targetDir.getFileName() + ".tmp-"))::iterator) {
                    deleteRecursively(sibling);
                }
            } catch (IOException ignored) {
                // 清理失败不影响解压主流程
            }
        }
        Path staging = targetDir.resolveSibling(targetDir.getFileName() + ".tmp-" + Long.toHexString(System.nanoTime()));
        Files.createDirectories(staging);
        try {
            try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(zipPath))) {
                ZipEntry entry;
                while ((entry = zis.getNextEntry()) != null) {
                    Path out = staging.resolve(entry.getName()).normalize();
                    if (!out.startsWith(staging)) {
                        continue; // 防 zip 路径穿越
                    }
                    if (entry.isDirectory()) {
                        Files.createDirectories(out);
                    } else {
                        Path outParent = out.getParent();
                        if (outParent != null) {
                            Files.createDirectories(outParent);
                        }
                        Files.copy(zis, out, StandardCopyOption.REPLACE_EXISTING);
                    }
                    zis.closeEntry();
                }
            }
            // 临时目录内的文件逐个搬入目标目录（同卷移动，逐文件原子）
            try (Stream<Path> walk = Files.walk(staging)) {
                for (Path source : (Iterable<Path>) walk.filter(Files::isRegularFile)::iterator) {
                    Path relative = staging.relativize(source);
                    Path dest = targetDir.resolve(relative).normalize();
                    if (!dest.startsWith(targetDir)) {
                        continue;
                    }
                    Path destParent = dest.getParent();
                    if (destParent != null) {
                        Files.createDirectories(destParent);
                    }
                    Files.move(source, dest, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        } finally {
            deleteRecursively(staging);
        }
    }

    /** 递归删除目录（解压临时目录清理；失败不影响主流程）。 */
    private static void deleteRecursively(Path dir) {
        if (dir == null || !Files.exists(dir)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.sorted(java.util.Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                    // 清理失败不影响主流程
                }
            });
        } catch (IOException ignored) {
            // 目录读取失败不影响主流程
        }
    }

    /** 把异常链展开为最内层的可读信息（UI 兜底显示用）。 */
    /**
     * 异常链的可读诊断信息。
     *
     * <p>不能简单取最深 cause：本地写入失败时最深的是 {@link java.nio.file.FileSystemException}，
     * 其 {@code getMessage()} 只有裸文件路径（如 {@code E:\LLM\models\x.gguf.part}），会把外层
     * 包装好的中文提示（异常类型、可能原因、处理建议）全部丢掉，日志里只剩一个路径无法定位。
     * 这里由外向内取第一条有信息量的消息；实在没有再退回最深层，并补上异常类型名。</p>
     */
    public static String rootMessage(Throwable throwable) {
        if (throwable == null) {
            return "未知错误";
        }
        String deepest = null;
        Set<Throwable> seen = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        for (Throwable t = throwable; t != null && seen.add(t); t = (t.getCause() != t ? t.getCause() : null)) {
            String msg = describeThrowable(t);
            if (deepest == null) {
                deepest = msg;
            }
            if (!isBareFilePath(msg)) {
                return msg; // 找到人类可读的那条就停（外层包装的提示信息最全）
            }
        }
        return deepest == null ? throwable.getClass().getSimpleName() : deepest;
    }

    /** 单条异常的诊断文本：FileSystemException 补上类型名与 reason，避免只剩裸路径。 */
    private static String describeThrowable(Throwable t) {
        String msg = t.getMessage();
        if (t instanceof java.nio.file.FileSystemException fse) {
            String file = fse.getFile();
            if (msg != null && !msg.isBlank() && !msg.equals(file)) {
                return msg + "（" + t.getClass().getSimpleName() + "）";
            }
            StringBuilder sb = new StringBuilder(t.getClass().getSimpleName()).append(": ")
                    .append(file == null || file.isBlank() ? "?" : file);
            String reason = fse.getReason();
            if (reason != null && !reason.isBlank()) {
                sb.append(" [").append(reason).append("]");
            }
            return sb.toString();
        }
        return (msg == null || msg.isBlank()) ? t.getClass().getSimpleName() : msg;
    }

    /** 是否只是"一条裸文件路径"（没有异常类型、原因或说明文字）。 */
    private static boolean isBareFilePath(String msg) {
        if (msg == null || msg.isBlank()) {
            return true;
        }
        if (msg.indexOf(' ') >= 0 || msg.indexOf('(') >= 0 || msg.indexOf('（') >= 0 || msg.indexOf('：') >= 0) {
            return false;
        }
        return (msg.length() > 2 && msg.charAt(1) == ':') || msg.startsWith("/") || msg.startsWith("\\");
    }
}
