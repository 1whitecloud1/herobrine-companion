package com.whitecloud233.modid.herobrine_companion.client.tts;

import com.whitecloud233.modid.herobrine_companion.util.NumberInputs;
import com.google.gson.Gson;
import com.whitecloud233.modid.herobrine_companion.util.AtomicFiles;
import com.google.gson.GsonBuilder;
import com.mojang.logging.LogUtils;
import net.minecraftforge.fml.loading.FMLPaths;
import org.slf4j.Logger;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;

/**
 * TTS 设置的读写与内存态（仅客户端，不上服务器、不发网络包）。
 *
 * <p>沿用 {@code LLMConfig} 的两文件约定：非敏感设置放 {@code config}，便于整合包分发；
 * API Key 单独存游戏目录下的私密文件，不会被整合包打包带走。</p>
 */
public final class TtsSettings {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /** 整合包可分发的普通设置。 */
    private static final File PUBLIC_CONFIG = FMLPaths.CONFIGDIR.get()
            .resolve("herobrine_companion").resolve("herobrine_companion_tts.json").toFile();

    /** 私密文件：只放云端 / 本地的 API Key。 */
    private static final File PRIVATE_SECRETS = FMLPaths.GAMEDIR.get()
            .resolve("herobrine_tts_secrets.json").toFile();

    public static final boolean DEFAULT_ENABLED = false;
    public static final TtsEngine DEFAULT_ENGINE = TtsEngine.SYSTEM;
    public static final double DEFAULT_VOLUME = 0.9D;
    public static final double DEFAULT_SPEED = 1.0D;
    public static final int DEFAULT_MAX_CHARS = 200;
    public static final boolean DEFAULT_INTERRUPT = true;
    public static final String DEFAULT_CLOUD_MODEL = TtsOptions.DEFAULT_CLOUD_MODEL;
    public static final String DEFAULT_CLOUD_VOICE = TtsOptions.DEFAULT_CLOUD_VOICE;
    public static final String DEFAULT_LOCAL_MODEL = TtsOptions.DEFAULT_LOCAL_MODEL;
    public static final String DEFAULT_LOCAL_VOICE = TtsOptions.DEFAULT_LOCAL_VOICE;
    public static final String DEFAULT_LOCAL_ENDPOINT = "http://127.0.0.1:8000";
    public static final String DEFAULT_CUSTOM_BODY = TtsOptions.DEFAULT_CUSTOM_BODY;

    private static boolean loaded = false;
    private static boolean loading = false;

    public static volatile boolean enabled = DEFAULT_ENABLED;
    public static volatile TtsEngine engine = DEFAULT_ENGINE;
    public static volatile double volume = DEFAULT_VOLUME;
    public static volatile double speed = DEFAULT_SPEED;
    public static volatile int maxChars = DEFAULT_MAX_CHARS;
    public static volatile boolean interruptCurrent = DEFAULT_INTERRUPT;

    /** 系统语音音色名，空 = 用系统默认音色。 */
    public static volatile String systemVoice = "";

    /** 云端 TTS。 */
    public static volatile String cloudEndpoint = "";
    public static volatile String cloudModel = DEFAULT_CLOUD_MODEL;
    public static volatile String cloudVoice = DEFAULT_CLOUD_VOICE;

    /** 本地部署 TTS（本机或局域网）。 */
    public static volatile String localEndpoint = "";
    public static volatile LocalTtsProtocol localProtocol = LocalTtsProtocol.OPENAI;
    public static volatile String localModel = DEFAULT_LOCAL_MODEL;
    public static volatile String localVoice = DEFAULT_LOCAL_VOICE;
    /** GPT-SoVITS 需要的参考音频/参考文本/语言。 */
    public static volatile String localReferenceAudio = "";
    public static volatile String localReferenceText = "";
    public static volatile String localReferenceLang = "zh";
    /** 自定义接口的请求体模板。 */
    public static volatile String localCustomBody = DEFAULT_CUSTOM_BODY;

    /** 敏感字段：只落私密文件。 */
    public static volatile String cloudApiKey = "";
    public static volatile String localApiKey = "";

    private TtsSettings() {
    }

    public static synchronized void ensureLoaded() {
        if (!loaded && !loading) {
            load();
        }
    }

    /** 设置页每次打开调用：玩家手改 JSON 后无需重启即可生效（以磁盘为准）。 */
    public static synchronized void reloadFromDisk() {
        load();
    }

    /** 当前设置的一份不可变快照。 */
    public static TtsOptions snapshot() {
        ensureLoaded();
        return new TtsOptions(engine, volume, speed, maxChars, interruptCurrent,
                new TtsOptions.SystemVoice(systemVoice),
                new TtsOptions.CloudVoice(cloudEndpoint, cloudApiKey, cloudModel, cloudVoice),
                new TtsOptions.LocalVoice(localEndpoint, localProtocol, localModel, localVoice,
                        localReferenceAudio, localReferenceText, localReferenceLang,
                        localCustomBody, localApiKey));
    }

    /**
     * 需要朗读时返回当前参数快照；总开关关闭、或所选引擎配置不全时返回 {@code null}。
     *
     * <p>"能不能念"的判断集中在这里，播报方只负责编排，不再各自重复校验。</p>
     */
    public static TtsOptions activeSnapshot() {
        ensureLoaded();
        if (!enabled) {
            return null;
        }
        TtsOptions options = snapshot();
        return options.isEngineConfigured() ? options : null;
    }

    /** 总开关打开、且当前引擎确实配置齐全。 */
    public static boolean isActive() {
        return activeSnapshot() != null;
    }

    /** 写回本地模型的常用默认端点（界面「填入默认端点」按钮用）。 */
    public static void useDefaultLocalEndpoint() {
        localEndpoint = DEFAULT_LOCAL_ENDPOINT;
    }

    public static synchronized void save() {
        try {
            loaded = true;
            clampAll();
            savePublicConfig();
            savePrivateSecrets();
        } catch (Exception e) {
            LOGGER.error("Failed to save Herobrine Companion TTS config", e);
        }
    }

    private static synchronized void load() {
        loading = true;
        try {
            resetToDefaults();
            readPublicConfig();
            readPrivateSecrets();
            clampAll();
            loaded = true;
            writeDefaultsWhenMissing();
        } finally {
            loading = false;
        }
    }

    private static void resetToDefaults() {
        enabled = DEFAULT_ENABLED;
        engine = DEFAULT_ENGINE;
        volume = DEFAULT_VOLUME;
        speed = DEFAULT_SPEED;
        maxChars = DEFAULT_MAX_CHARS;
        interruptCurrent = DEFAULT_INTERRUPT;
        systemVoice = "";
        cloudEndpoint = "";
        cloudModel = DEFAULT_CLOUD_MODEL;
        cloudVoice = DEFAULT_CLOUD_VOICE;
        localEndpoint = "";
        localProtocol = LocalTtsProtocol.OPENAI;
        localModel = DEFAULT_LOCAL_MODEL;
        localVoice = DEFAULT_LOCAL_VOICE;
        localReferenceAudio = "";
        localReferenceText = "";
        localReferenceLang = "zh";
        localCustomBody = DEFAULT_CUSTOM_BODY;
        cloudApiKey = "";
        localApiKey = "";
    }

    private static void clampAll() {
        if (engine == null) {
            engine = DEFAULT_ENGINE;
        }
        if (localProtocol == null) {
            localProtocol = LocalTtsProtocol.OPENAI;
        }
        volume = NumberInputs.finite(volume, TtsOptions.MIN_VOLUME, TtsOptions.MAX_VOLUME, DEFAULT_VOLUME);
        speed = NumberInputs.finite(speed, TtsOptions.MIN_SPEED, TtsOptions.MAX_SPEED, DEFAULT_SPEED);
        maxChars = NumberInputs.positive(maxChars, TtsOptions.MIN_MAX_CHARS, TtsOptions.MAX_MAX_CHARS, DEFAULT_MAX_CHARS);
        systemVoice = trim(systemVoice);
        cloudEndpoint = trim(cloudEndpoint);
        cloudApiKey = trim(cloudApiKey);
        cloudModel = trim(cloudModel).isEmpty() ? DEFAULT_CLOUD_MODEL : cloudModel.trim();
        cloudVoice = trim(cloudVoice).isEmpty() ? DEFAULT_CLOUD_VOICE : cloudVoice.trim();
        localEndpoint = trim(localEndpoint);
        localApiKey = trim(localApiKey);
        localModel = trim(localModel).isEmpty() ? DEFAULT_LOCAL_MODEL : localModel.trim();
        localVoice = trim(localVoice).isEmpty() ? DEFAULT_LOCAL_VOICE : localVoice.trim();
        localReferenceAudio = trim(localReferenceAudio);
        localReferenceText = trim(localReferenceText);
        localReferenceLang = trim(localReferenceLang).isEmpty() ? "zh" : localReferenceLang.trim();
        localCustomBody = trim(localCustomBody).isEmpty() ? DEFAULT_CUSTOM_BODY : localCustomBody;
    }

    private static void writeDefaultsWhenMissing() {
        if (!PUBLIC_CONFIG.exists()) {
            try {
                savePublicConfig();
            } catch (IOException e) {
                LOGGER.error("Failed to create default TTS config at {}", PUBLIC_CONFIG, e);
            }
        }
    }

    private static void readPublicConfig() {
        if (!PUBLIC_CONFIG.exists()) {
            return;
        }
        try (Reader reader = new InputStreamReader(new FileInputStream(PUBLIC_CONFIG), StandardCharsets.UTF_8)) {
            ConfigData data = GSON.fromJson(reader, ConfigData.class);
            if (data == null) {
                return;
            }
            if (data.enabled != null) {
                enabled = data.enabled;
            }
            if (data.engine != null) {
                engine = TtsEngine.fromSaved(data.engine);
            }
            if (data.volume != null) {
                volume = data.volume;
            }
            if (data.speed != null) {
                speed = data.speed;
            }
            if (data.maxChars != null) {
                maxChars = data.maxChars;
            }
            if (data.interruptCurrent != null) {
                interruptCurrent = data.interruptCurrent;
            }
            if (data.systemVoice != null) {
                systemVoice = data.systemVoice;
            }
            if (data.cloudEndpoint != null) {
                cloudEndpoint = data.cloudEndpoint;
            }
            if (data.cloudModel != null) {
                cloudModel = data.cloudModel;
            }
            if (data.cloudVoice != null) {
                cloudVoice = data.cloudVoice;
            }
            if (data.localEndpoint != null) {
                localEndpoint = data.localEndpoint;
            }
            if (data.localProtocol != null) {
                localProtocol = LocalTtsProtocol.fromSaved(data.localProtocol);
            }
            if (data.localModel != null) {
                localModel = data.localModel;
            }
            if (data.localVoice != null) {
                localVoice = data.localVoice;
            }
            if (data.localReferenceAudio != null) {
                localReferenceAudio = data.localReferenceAudio;
            }
            if (data.localReferenceText != null) {
                localReferenceText = data.localReferenceText;
            }
            if (data.localReferenceLang != null) {
                localReferenceLang = data.localReferenceLang;
            }
            if (data.localCustomBody != null) {
                localCustomBody = data.localCustomBody;
            }
        } catch (Exception e) {
            LOGGER.error("Failed to load Herobrine Companion TTS config from {}", PUBLIC_CONFIG, e);
        }
    }

    private static void readPrivateSecrets() {
        if (!PRIVATE_SECRETS.exists()) {
            return;
        }
        try (Reader reader = new InputStreamReader(new FileInputStream(PRIVATE_SECRETS), StandardCharsets.UTF_8)) {
            SecretData data = GSON.fromJson(reader, SecretData.class);
            if (data == null) {
                return;
            }
            if (data.cloudApiKey != null) {
                cloudApiKey = data.cloudApiKey;
            }
            if (data.localApiKey != null) {
                localApiKey = data.localApiKey;
            }
        } catch (Exception e) {
            LOGGER.error("Failed to load Herobrine Companion TTS secrets from {}", PRIVATE_SECRETS, e);
        }
    }

    private static void savePublicConfig() throws IOException {
        PUBLIC_CONFIG.getParentFile().mkdirs();
        ConfigData data = new ConfigData();
        data.enabled = enabled;
        data.engine = engine.id();
        data.volume = volume;
        data.speed = speed;
        data.maxChars = maxChars;
        data.interruptCurrent = interruptCurrent;
        data.systemVoice = systemVoice;
        data.cloudEndpoint = cloudEndpoint;
        data.cloudModel = cloudModel;
        data.cloudVoice = cloudVoice;
        data.localEndpoint = localEndpoint;
        data.localProtocol = localProtocol.id();
        data.localModel = localModel;
        data.localVoice = localVoice;
        data.localReferenceAudio = localReferenceAudio;
        data.localReferenceText = localReferenceText;
        data.localReferenceLang = localReferenceLang;
        data.localCustomBody = localCustomBody;
        AtomicFiles.writeJson(PUBLIC_CONFIG.toPath(), GSON, data);
    }

    private static void savePrivateSecrets() throws IOException {
        PRIVATE_SECRETS.getParentFile().mkdirs();
        SecretData data = new SecretData();
        data.cloudApiKey = cloudApiKey;
        data.localApiKey = localApiKey;
        AtomicFiles.writeJson(PRIVATE_SECRETS.toPath(), GSON, data);
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }

    /** 非敏感设置的磁盘结构（字段可空，缺失即沿用默认值）。 */
    private static final class ConfigData {
        Boolean enabled;
        String engine;
        Double volume;
        Double speed;
        Integer maxChars;
        Boolean interruptCurrent;
        String systemVoice;
        String cloudEndpoint;
        String cloudModel;
        String cloudVoice;
        String localEndpoint;
        String localProtocol;
        String localModel;
        String localVoice;
        String localReferenceAudio;
        String localReferenceText;
        String localReferenceLang;
        String localCustomBody;
    }

    /** 私密结构。 */
    private static final class SecretData {
        String cloudApiKey;
        String localApiKey;
    }
}
