package com.whitecloud233.herobrine_companion.config;

import com.whitecloud233.herobrine_companion.client.gui.HeroTheme;
import com.whitecloud233.herobrine_companion.client.gui.HeroScreen;
import com.whitecloud233.herobrine_companion.client.tts.LocalTtsProtocol;
import com.whitecloud233.herobrine_companion.client.tts.SystemSpeechOutput;
import com.whitecloud233.herobrine_companion.client.tts.TtsEngine;
import com.whitecloud233.herobrine_companion.client.tts.TtsSettings;
import com.whitecloud233.herobrine_companion.client.tts.TtsSpeechService;
import com.whitecloud233.herobrine_companion.client.tts.VoiceCatalog;
import com.whitecloud233.herobrine_companion.client.tts.VoiceOption;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * 语音（TTS）设置页：让 Herbrine 在<b>玩家发起的聊天</b>里把回复念出来。
 *
 * <p>职责：只做界面、草稿与校验；持久化交给 {@link TtsSettings}，播报交给 {@link TtsSpeechService}，
 * 音色枚举交给 {@link VoiceCatalog}。纯客户端页面，不涉及服务端逻辑或网络包。</p>
 *
 * <p>布局采用"行"模型：{@link #describeRows()} 先算出当前引擎真正需要的行，
 * 再逐行放置控件。行数决定面板高度，因此切换引擎既不会重叠也不会留白。</p>
 */
public class TtsSettingsScreen extends Screen {

    private static final String PREFIX = "gui.herobrine_companion.tts.";
    private static final String STATE_PREFIX = "gui.herobrine_companion.config.";
    private static final int MAX_PANEL_WIDTH = 470;
    private static final int MAX_PANEL_HEIGHT = 360;
    private static final int BUTTON_HEIGHT = TtsLayout.BUTTON_HEIGHT;
    private static final int GAP = 6;
    private static final int COL_BG = HeroTheme.BG_MAIN;
    private static final int COL_BORDER = HeroTheme.BORDER;
    private static final int COL_TITLE = HeroTheme.LABEL;
    private static final int COL_LABEL = HeroTheme.TEXT;
    private static final int COL_HINT = HeroTheme.INFO;
    private static final int COL_WARN = HeroTheme.WARN;
    private static final int COL_OK = 0xFF55D67A;

    private static final double[] SPEED_CHOICES = {0.75D, 1.0D, 1.25D, 1.5D, 2.0D};
    private static final double[] VOLUME_CHOICES = {0.3D, 0.6D, 0.8D, 1.0D};
    private static final int[] MAX_CHARS_CHOICES = {80, 120, 200, 300, 500};
    private static final String[] LANGUAGES = {"zh", "en", "ja", "ko", "auto"};

    /** 试听超过这个时间仍无结果，判定为没有反应并提示检查。 */
    private static final long PREVIEW_PENDING_MS = 8_000L;

    /**
     * 一行：左侧标签（可为 null）+ 一组等宽列。
     *
     * <p>同一行内每块控件用不同的 {@link TtsCell#index}，从结构上杜绝"两个控件落在同一坐标"。</p>
     */
    private record Row(String labelKey, List<TtsCell> cells) {
    }

    private final Screen lastScreen;
    private final List<Row> rows = new ArrayList<>();
    private final List<Object> rowWidgets = new ArrayList<>();

    private Button enabledButton;
    private Button engineButton;
    private Button voiceCycleButton;
    private Button protocolButton;
    private Button referenceLangButton;
    private Button speedButton;
    private Button volumeButton;
    private Button maxCharsButton;
    private Button interruptButton;

    private EditBox systemVoiceBox;
    private EditBox cloudEndpointBox;
    private EditBox cloudModelBox;
    private EditBox cloudVoiceBox;
    private EditBox cloudApiKeyBox;
    private EditBox localEndpointBox;
    private EditBox localModelBox;
    private EditBox localVoiceBox;
    private EditBox localKeyBox;
    private EditBox localReferenceAudioBox;
    private EditBox localReferenceTextBox;

    private boolean enabledDraft;
    private TtsEngine engineDraft;
    private double speedDraft;
    private double volumeDraft;
    private int maxCharsDraft;
    private boolean interruptDraft;
    private String systemVoiceDraft;
    private String cloudEndpointDraft;
    private String cloudModelDraft;
    private String cloudVoiceDraft;
    private String cloudApiKeyDraft;
    private String localEndpointDraft;
    private LocalTtsProtocol localProtocolDraft;
    private String localModelDraft;
    private String localVoiceDraft;
    private String localKeyDraft;
    private String localReferenceAudioDraft;
    private String localReferenceTextDraft;
    private String localReferenceLangDraft;
    private boolean draftsLoaded;

    /** 试听结果回显：失败时展示服务端原始错误，成功时提示已播放。 */
    private String previewMessage;
    private boolean previewFailed;
    private long previewStartedAt;

    private List<VoiceOption> voiceChoices = List.of(VoiceOption.AUTO);
    private int voiceChoiceIndex;

    public TtsSettingsScreen(Screen lastScreen) {
        super(Component.translatable(PREFIX + "title"));
        this.lastScreen = lastScreen;
    }

    @Override
    protected void init() {
        this.captureDrafts();
        super.init();
        // 每次打开页面都以磁盘为准：既保证重进游戏后能看到已保存的字段，
        // 也避免"上一轮 rebuild 把草稿清空"造成的静默丢失。
        TtsSettings.reloadFromDisk();
        this.resetDraftsFromSettings();
        this.draftsLoaded = true;
        this.loadVoiceChoices();
        this.build();
    }

    /**
     * 每 tick 刷新试听状态：错误由异步播报线程写入，界面需要主动轮询才能看到。
     */
    @Override
    public void tick() {
        super.tick();
        TtsSpeechService service = TtsSpeechService.getInstance();
        String error = service.lastError();
        if (error != null) {
            this.previewFailed = true;
            this.previewMessage = null;
        }
    }
    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(this.lastScreen);
    }

    // ---------------- 构建 ----------------

    /** 引擎/协议变化后整页重建：行数变了，槽位与面板高度都要重算。 */
    private void rebuild() {
        this.captureDrafts();
        this.clearWidgets();
        this.build();
    }

    private void build() {
        this.rows.clear();
        this.rowWidgets.clear();
        this.resetWidgetRefs();
        this.describeRows();

        TtsLayout layout = this.createLayout();
        int controlHeight = layout.controlHeight();

        // 第 0 行固定是"总开关 + 引擎"，两列平分内容区（不缩进标签列）
        int halfWidth = Math.max(1, (layout.contentWidth() - GAP) / 2);
        this.enabledButton = this.addRenderableWidget(new HeroScreen.ThemedButton(
                layout.contentX(), layout.rowY(0), halfWidth, controlHeight, Component.empty(),
                button -> {
                    this.enabledDraft = !this.enabledDraft;
                    this.refreshMessages();
                }, Tooltip.create(Component.translatable(PREFIX + "tooltip.enabled"))));
        this.engineButton = this.addRenderableWidget(new HeroScreen.ThemedButton(
                layout.contentX() + halfWidth + GAP, layout.rowY(0), halfWidth, controlHeight, Component.empty(),
                button -> {
                    this.engineDraft = this.engineDraft.next();
                    this.rebuild();
                }, Tooltip.create(Component.translatable(PREFIX + "tooltip.engine"))));

        // 数据行从第 1 行开始，严格按 describeRows() 的顺序放置
        for (int index = 0; index < this.rows.size(); index++) {
            int slot = index + 1;
            Row row = this.rows.get(index);
            this.rowWidgets.add(this.createRowWidgets(row, layout, slot, controlHeight));
        }

        this.buildTailControls(layout);
        this.refreshMessages();
        if (this.systemVoiceBox != null) {
            this.setInitialFocus(this.systemVoiceBox);
        }
    }

    /**
     * 描述当前引擎需要的行。顺序即渲染顺序，行数即面板高度。
     */
    private void describeRows() {
        switch (this.engineDraft) {
            case SYSTEM -> this.rows.add(new Row(PREFIX + "system_voice",
                    List.of(new TtsCell(0, 2), new TtsCell(1, 2))));
            case CLOUD -> {
                this.rows.add(new Row(PREFIX + "cloud_endpoint", List.of(new TtsCell(0, 1))));
                // 模型与音色各占一半，标签用合并后的"模型 / 音色"
                this.rows.add(new Row(PREFIX + "cloud_model_voice",
                        List.of(new TtsCell(0, 2), new TtsCell(1, 2))));
                this.rows.add(new Row(PREFIX + "cloud_api_key", List.of(new TtsCell(0, 1))));
            }
            case LOCAL -> {
                this.rows.add(new Row(PREFIX + "local_endpoint",
                        List.of(new TtsCell(0, 2), new TtsCell(1, 2))));
                this.rows.add(new Row(PREFIX + "local_model_voice",
                        List.of(new TtsCell(0, 2), new TtsCell(1, 2))));
                // 密钥行始终存在：否则切换协议后草稿里的 localKey 无处显示，
                // 保存时又会被写回去，导致"密钥里出现参考音频路径"这类串位。
                this.rows.add(new Row(PREFIX + "local_api_key", List.of(new TtsCell(0, 1))));
                if (this.localProtocolDraft == LocalTtsProtocol.GPT_SOVITS) {
                    this.rows.add(new Row(PREFIX + "local_reference_audio", List.of(new TtsCell(0, 1))));
                    this.rows.add(new Row(PREFIX + "local_reference_text", List.of(new TtsCell(0, 1))));
                }
            }
        }
    }

    /**
     * 按行类型放置控件。行内容由引擎决定，这里只负责"这一行放什么控件"。
     */
    private Object createRowWidgets(Row row, TtsLayout layout, int slot, int controlHeight) {
        String labelKey = row.labelKey();
        List<TtsCell> cells = row.cells();

        if (PREFIX.concat("system_voice").equals(labelKey)) {
            // 左半：已安装音色循环；右半：可手填的音色全名
            Button cycle = this.addRenderableWidget(new HeroScreen.ThemedButton(
                    layout.columnX(cells.get(0)), layout.rowY(slot), layout.columnWidth(cells.get(0)), controlHeight,
                    Component.empty(), button -> {
                this.voiceChoiceIndex = (this.voiceChoiceIndex + 1) % this.voiceChoices.size();
                this.systemVoiceDraft = this.voiceChoices.get(this.voiceChoiceIndex).name();
                if (this.systemVoiceBox != null) {
                    this.systemVoiceBox.setValue(this.systemVoiceDraft);
                }
                this.refreshMessages();
            }, Tooltip.create(Component.translatable(PREFIX + "tooltip.system_voice"))));
            this.voiceCycleButton = cycle;
            this.systemVoiceBox = this.createBox(layout, slot, cells.get(1), PREFIX + "system_voice_custom",
                    this.systemVoiceDraft, PREFIX + "tooltip.system_voice_custom", value -> {
                        this.systemVoiceDraft = value;
                        this.voiceChoiceIndex = this.indexOfVoice(value);
                        this.refreshMessages();
                    });
            return cycle;
        }

        if (PREFIX.concat("cloud_endpoint").equals(labelKey)) {
            this.cloudEndpointBox = this.createBox(layout, slot, cells.get(0), PREFIX + "cloud_endpoint",
                    this.cloudEndpointDraft, PREFIX + "tooltip.cloud_endpoint",
                    value -> this.cloudEndpointDraft = value);
            return this.cloudEndpointBox;
        }
        if (PREFIX.concat("cloud_model_voice").equals(labelKey)) {
            this.cloudModelBox = this.createBox(layout, slot, cells.get(0), PREFIX + "cloud_model",
                    this.cloudModelDraft, PREFIX + "tooltip.cloud_model",
                    value -> this.cloudModelDraft = value);
            this.cloudVoiceBox = this.createBox(layout, slot, cells.get(1), PREFIX + "cloud_voice",
                    this.cloudVoiceDraft, PREFIX + "tooltip.cloud_voice",
                    value -> this.cloudVoiceDraft = value);
            return this.cloudModelBox;
        }
        if (PREFIX.concat("cloud_api_key").equals(labelKey)) {
            this.cloudApiKeyBox = this.createBox(layout, slot, cells.get(0), PREFIX + "cloud_api_key",
                    this.cloudApiKeyDraft, PREFIX + "tooltip.cloud_api_key",
                    value -> this.cloudApiKeyDraft = value);
            return this.cloudApiKeyBox;
        }

        if (PREFIX.concat("local_endpoint").equals(labelKey)) {
            this.localEndpointBox = this.createBox(layout, slot, cells.get(0), PREFIX + "local_endpoint",
                    this.localEndpointDraft, PREFIX + "tooltip.local_endpoint",
                    value -> this.localEndpointDraft = value);
            this.protocolButton = this.addRenderableWidget(new HeroScreen.ThemedButton(
                    layout.columnX(cells.get(1)), layout.rowY(slot), layout.columnWidth(cells.get(1)), controlHeight,
                    Component.empty(), button -> {
                this.localProtocolDraft = this.localProtocolDraft.next();
                this.rebuild();
            }, Tooltip.create(Component.translatable(PREFIX + "tooltip.local_protocol"))));
            return this.localEndpointBox;
        }
        if (PREFIX.concat("local_model_voice").equals(labelKey)) {
            this.localModelBox = this.createBox(layout, slot, cells.get(0), PREFIX + "local_model",
                    this.localModelDraft, PREFIX + "tooltip.local_model",
                    value -> this.localModelDraft = value);
            this.localVoiceBox = this.createBox(layout, slot, cells.get(1), PREFIX + "local_voice",
                    this.localVoiceDraft, PREFIX + "tooltip.local_voice",
                    value -> this.localVoiceDraft = value);
            return this.localModelBox;
        }
        if (PREFIX.concat("local_reference_audio").equals(labelKey)) {
            this.localReferenceAudioBox = this.createBox(layout, slot, cells.get(0), PREFIX + "local_reference_audio",
                    this.localReferenceAudioDraft, PREFIX + "tooltip.local_reference_audio",
                    value -> this.localReferenceAudioDraft = value);
            return this.localReferenceAudioBox;
        }
        if (PREFIX.concat("local_reference_text").equals(labelKey)) {
            this.localReferenceTextBox = this.createBox(layout, slot, cells.get(0), PREFIX + "local_reference_text",
                    this.localReferenceTextDraft, PREFIX + "tooltip.local_reference_text",
                    value -> this.localReferenceTextDraft = value);
            // 语种按钮挂在参考文本行右端，不额外占用一整行
            this.referenceLangButton = this.addRenderableWidget(new HeroScreen.ThemedButton(
                    layout.contentX() + layout.contentWidth() - 68, layout.rowY(slot), 68, controlHeight,
                    Component.empty(), button -> {
                this.localReferenceLangDraft = nextLanguage(this.localReferenceLangDraft);
                this.refreshMessages();
            }, Tooltip.create(Component.translatable(PREFIX + "tooltip.local_reference_lang"))));
            this.localReferenceTextBox.setWidth(Math.max(40, this.localReferenceTextBox.getWidth() - 74));
            return this.localReferenceTextBox;
        }
        if (PREFIX.concat("local_api_key").equals(labelKey)) {
            this.localKeyBox = this.createBox(layout, slot, cells.get(0), PREFIX + "local_api_key",
                    this.localKeyDraft, PREFIX + "tooltip.local_api_key",
                    value -> this.localKeyDraft = value);
            return this.localKeyBox;
        }
        return null;
    }

    private EditBox createBox(TtsLayout layout, int slot, TtsCell cell, String labelKey,
                              String initialValue, String tooltipKey, Consumer<String> listener) {
        EditBox box = this.addRenderableWidget(new MouseSelectableEditBox(this.font,
                layout.columnX(cell), layout.rowY(slot), layout.columnWidth(cell), layout.controlHeight(),
                Component.translatable(labelKey)));
        box.setMaxLength(512);
        box.setValue(initialValue == null ? "" : initialValue);
        box.setResponder(listener);
        box.setTooltip(Tooltip.create(Component.translatable(tooltipKey)));
        return box;
    }

    private void buildTailControls(TtsLayout layout) {
        int numericSlot = this.rows.size() + 1;
        int interruptSlot = numericSlot + 1;
        int width = layout.columnWidth(3);
        this.speedButton = this.addRenderableWidget(new HeroScreen.ThemedButton(
                layout.columnX(new TtsCell(0, 3)), layout.rowY(numericSlot), width, layout.controlHeight(),
                Component.empty(), button -> {
            this.speedDraft = nextSpeed(this.speedDraft);
            this.refreshMessages();
        }, Tooltip.create(Component.translatable(PREFIX + "tooltip.speed"))));
        this.volumeButton = this.addRenderableWidget(new HeroScreen.ThemedButton(
                layout.columnX(new TtsCell(1, 3)), layout.rowY(numericSlot), width, layout.controlHeight(),
                Component.empty(), button -> {
            this.volumeDraft = nextVolume(this.volumeDraft);
            this.refreshMessages();
        }, Tooltip.create(Component.translatable(PREFIX + "tooltip.volume"))));
        this.maxCharsButton = this.addRenderableWidget(new HeroScreen.ThemedButton(
                layout.columnX(new TtsCell(2, 3)), layout.rowY(numericSlot), width, layout.controlHeight(),
                Component.empty(), button -> {
            this.maxCharsDraft = nextMaxChars(this.maxCharsDraft);
            this.refreshMessages();
        }, Tooltip.create(Component.translatable(PREFIX + "tooltip.max_chars"))));

        this.interruptButton = this.addRenderableWidget(new HeroScreen.ThemedButton(
                layout.contentX(), layout.rowY(interruptSlot), layout.contentWidth(), layout.controlHeight(),
                Component.empty(), button -> {
            this.interruptDraft = !this.interruptDraft;
            this.refreshMessages();
        }, Tooltip.create(Component.translatable(PREFIX + "tooltip.interrupt"))));

        int actionWidth = layout.actionWidth(4);
        this.addRenderableWidget(new HeroScreen.ThemedButton(
                layout.actionX(0, 4), layout.actionY(), actionWidth, BUTTON_HEIGHT,
                Component.translatable(PREFIX + "test"), button -> this.previewVoice(),
                Tooltip.create(Component.translatable(PREFIX + "tooltip.test"))));
        this.addRenderableWidget(new HeroScreen.ThemedButton(
                layout.actionX(1, 4), layout.actionY(), actionWidth, BUTTON_HEIGHT,
                Component.translatable(PREFIX + "stop"), button -> TtsSpeechService.getInstance().stop(), null));
        this.addRenderableWidget(new HeroScreen.ThemedButton(
                layout.actionX(2, 4), layout.actionY(), actionWidth, BUTTON_HEIGHT,
                Component.translatable(PREFIX + "save"), button -> this.saveAndClose(), null));
        this.addRenderableWidget(new HeroScreen.ThemedButton(
                layout.actionX(3, 4), layout.actionY(), actionWidth, BUTTON_HEIGHT,
                Component.translatable("gui.herobrine_companion.back"), button -> this.onClose(), null));
    }

    private void resetWidgetRefs() {
        this.enabledButton = null;
        this.engineButton = null;
        this.voiceCycleButton = null;
        this.protocolButton = null;
        this.referenceLangButton = null;
        this.speedButton = null;
        this.volumeButton = null;
        this.maxCharsButton = null;
        this.interruptButton = null;
        this.systemVoiceBox = null;
        this.cloudEndpointBox = null;
        this.cloudModelBox = null;
        this.cloudVoiceBox = null;
        this.cloudApiKeyBox = null;
        this.localEndpointBox = null;
        this.localModelBox = null;
        this.localVoiceBox = null;
        this.localKeyBox = null;
        this.localReferenceAudioBox = null;
        this.localReferenceTextBox = null;
    }

    // ---------------- 布局 ----------------

    private TtsLayout createLayout() {
        // 行数：1（开关）+ 数据行 + 2（语速行、打断行）
        return TtsLayout.of(this.width, this.height, MAX_PANEL_WIDTH, MAX_PANEL_HEIGHT, this.rows.size() + 3);
    }

    // ---------------- 音色枚举 ----------------

    private void loadVoiceChoices() {
        List<VoiceOption> choices = new ArrayList<>();
        choices.add(VoiceOption.AUTO);
        choices.addAll(VoiceCatalog.cachedSystemVoices());
        this.voiceChoices = List.copyOf(choices);
        this.voiceChoiceIndex = this.indexOfVoice(this.systemVoiceDraft);

        VoiceCatalog.loadSystemVoices().thenAccept(voices -> Minecraft.getInstance().execute(() -> {
            List<VoiceOption> merged = new ArrayList<>();
            merged.add(VoiceOption.AUTO);
            if (voices != null) {
                merged.addAll(voices);
            }
            this.voiceChoices = List.copyOf(merged);
            this.voiceChoiceIndex = this.indexOfVoice(this.systemVoiceDraft);
            this.refreshMessages();
        }));
    }

    private int indexOfVoice(String name) {
        String target = name == null ? "" : name.trim();
        for (int index = 0; index < this.voiceChoices.size(); index++) {
            if (this.voiceChoices.get(index).name().equalsIgnoreCase(target)) {
                return index;
            }
        }
        return 0;
    }

    // ---------------- 草稿 ----------------

    private void resetDraftsFromSettings() {
        this.enabledDraft = TtsSettings.enabled;
        this.engineDraft = TtsSettings.engine == null ? TtsSettings.DEFAULT_ENGINE : TtsSettings.engine;
        this.speedDraft = TtsSettings.speed;
        this.volumeDraft = TtsSettings.volume;
        this.maxCharsDraft = TtsSettings.maxChars;
        this.interruptDraft = TtsSettings.interruptCurrent;
        this.systemVoiceDraft = TtsSettings.systemVoice;
        this.cloudEndpointDraft = TtsSettings.cloudEndpoint;
        this.cloudModelDraft = TtsSettings.cloudModel;
        this.cloudVoiceDraft = TtsSettings.cloudVoice;
        this.cloudApiKeyDraft = TtsSettings.cloudApiKey;
        this.localEndpointDraft = TtsSettings.localEndpoint;
        this.localProtocolDraft = TtsSettings.localProtocol == null
                ? LocalTtsProtocol.OPENAI : TtsSettings.localProtocol;
        this.localModelDraft = TtsSettings.localModel;
        this.localVoiceDraft = TtsSettings.localVoice;
        this.localKeyDraft = TtsSettings.localApiKey;
        this.localReferenceAudioDraft = TtsSettings.localReferenceAudio;
        this.localReferenceTextDraft = TtsSettings.localReferenceText;
        this.localReferenceLangDraft = TtsSettings.localReferenceLang;
    }

    /**
     * 把界面上的输入同步回草稿。
     *
     * <p><b>只在控件真实存在时覆盖</b>：切换引擎/协议后，某些框这一轮根本没被创建
     *（例如 OpenAI 兼容模式下没有参考音频框）。若无条件用 {@code null} 或空值覆盖草稿，
     * 玩家先前填好的字段会被静默清空——这正是"重进游戏后参考音频/参考文本消失"的原因。</p>
     */
    private void captureDrafts() {
        if (this.systemVoiceBox != null) {
            this.systemVoiceDraft = this.systemVoiceBox.getValue();
        }
        if (this.cloudEndpointBox != null) {
            this.cloudEndpointDraft = this.cloudEndpointBox.getValue();
        }
        if (this.cloudModelBox != null) {
            this.cloudModelDraft = this.cloudModelBox.getValue();
        }
        if (this.cloudVoiceBox != null) {
            this.cloudVoiceDraft = this.cloudVoiceBox.getValue();
        }
        if (this.cloudApiKeyBox != null) {
            this.cloudApiKeyDraft = this.cloudApiKeyBox.getValue();
        }
        if (this.localEndpointBox != null) {
            this.localEndpointDraft = this.localEndpointBox.getValue();
        }
        if (this.localModelBox != null) {
            this.localModelDraft = this.localModelBox.getValue();
        }
        if (this.localVoiceBox != null) {
            this.localVoiceDraft = this.localVoiceBox.getValue();
        }
        if (this.localKeyBox != null) {
            this.localKeyDraft = this.localKeyBox.getValue();
        }
        if (this.localReferenceAudioBox != null) {
            this.localReferenceAudioDraft = this.localReferenceAudioBox.getValue();
        }
        if (this.localReferenceTextBox != null) {
            this.localReferenceTextDraft = this.localReferenceTextBox.getValue();
        }
    }

    private void applyDraftsToSettings() {
        TtsSettings.enabled = this.enabledDraft;
        TtsSettings.engine = this.engineDraft;
        TtsSettings.speed = this.speedDraft;
        TtsSettings.volume = this.volumeDraft;
        TtsSettings.maxChars = this.maxCharsDraft;
        TtsSettings.interruptCurrent = this.interruptDraft;
        TtsSettings.systemVoice = nullToEmpty(this.systemVoiceDraft);
        TtsSettings.cloudEndpoint = nullToEmpty(this.cloudEndpointDraft);
        TtsSettings.cloudModel = nullToEmpty(this.cloudModelDraft);
        TtsSettings.cloudVoice = nullToEmpty(this.cloudVoiceDraft);
        TtsSettings.cloudApiKey = nullToEmpty(this.cloudApiKeyDraft);
        TtsSettings.localEndpoint = nullToEmpty(this.localEndpointDraft);
        TtsSettings.localProtocol = this.localProtocolDraft;
        TtsSettings.localModel = nullToEmpty(this.localModelDraft);
        TtsSettings.localVoice = nullToEmpty(this.localVoiceDraft);
        TtsSettings.localApiKey = nullToEmpty(this.localKeyDraft);
        // 参考音频/文本只在 GPT-SoVITS 界面出现。切到其它协议时草稿仍是已保存的值，
        // 因此这里可以直接落盘，不会误清空——前提是草稿始终与控件解耦（见 captureDrafts）。
        TtsSettings.localReferenceAudio = nullToEmpty(this.localReferenceAudioDraft);
        TtsSettings.localReferenceText = nullToEmpty(this.localReferenceTextDraft);
        TtsSettings.localReferenceLang = nullToEmpty(this.localReferenceLangDraft).isEmpty()
                ? "zh" : this.localReferenceLangDraft;
    }

    /**
     * 试听：把当前界面上（尚未保存）的设置直接用于一次播报。
     *
     * <p>播报结果会在状态区回显：失败时显示服务端返回的原始错误，
     * 玩家不必去翻日志就知道是端点、密钥还是音色的问题。</p>
     */
    private void previewVoice() {
        this.captureDrafts();
        this.applyDraftsToSettings();
        TtsSpeechService service = TtsSpeechService.getInstance();
        service.reload();
        service.clearLastError();
        this.previewStartedAt = System.currentTimeMillis();
        // 试听前先做一次本地可判断的校验，避免把"没填完"当成服务端故障
        String localProblem = this.describeMissingField();
        if (localProblem != null) {
            this.previewMessage = localProblem;
            this.previewFailed = true;
            return;
        }
        this.previewMessage = null;
        this.previewFailed = false;
        service.speakSample(Component.translatable(PREFIX + "sample_line").getString());
    }

    /** 试听前检查当前引擎缺哪些必填项；齐全返回 {@code null}。 */
    private String describeMissingField() {
        return switch (this.engineDraft) {
            case SYSTEM -> SystemSpeechOutput.platformSupportsSystemSpeech()
                    ? null : Component.translatable(PREFIX + "warning.system_unavailable").getString();
            case CLOUD -> {
                if (nullToEmpty(this.cloudEndpointDraft).isEmpty()) {
                    yield Component.translatable(PREFIX + "missing.cloud_endpoint").getString();
                }
                if (nullToEmpty(this.cloudApiKeyDraft).isEmpty()) {
                    yield Component.translatable(PREFIX + "missing.cloud_api_key").getString();
                }
                if (nullToEmpty(this.cloudVoiceDraft).isEmpty()) {
                    yield Component.translatable(PREFIX + "missing.cloud_voice").getString();
                }
                yield null;
            }
            case LOCAL -> {
                if (nullToEmpty(this.localEndpointDraft).isEmpty()) {
                    yield Component.translatable(PREFIX + "warning.local_incomplete").getString();
                }
                if (this.localProtocolDraft == LocalTtsProtocol.GPT_SOVITS
                        && nullToEmpty(this.localReferenceAudioDraft).isEmpty()) {
                    yield Component.translatable(PREFIX + "missing.local_reference_audio").getString();
                }
                yield null;
            }
        };
    }

    private void saveAndClose() {
        this.captureDrafts();
        this.applyDraftsToSettings();
        TtsSettings.save();
        TtsSpeechService.getInstance().reload();
        Minecraft.getInstance().setScreen(this.lastScreen);
    }

    // ---------------- 文案 ----------------

    private void refreshMessages() {
        if (this.enabledButton != null) {
            this.enabledButton.setMessage(Component.translatable(PREFIX + "toggle_enabled", stateLabel(this.enabledDraft)));
        }
        if (this.engineButton != null) {
            this.engineButton.setMessage(Component.translatable(PREFIX + "toggle_engine",
                    Component.translatable(this.engineDraft.labelKey())));
        }
        if (this.voiceCycleButton != null) {
            VoiceOption current = this.voiceChoiceIndex >= 0 && this.voiceChoiceIndex < this.voiceChoices.size()
                    ? this.voiceChoices.get(this.voiceChoiceIndex) : VoiceOption.AUTO;
            String label = current.name().isBlank()
                    ? Component.translatable(PREFIX + "voice_auto").getString()
                    : current.displayText();
            this.voiceCycleButton.setMessage(Component.literal(label));
        }
        if (this.protocolButton != null) {
            this.protocolButton.setMessage(Component.translatable(this.localProtocolDraft.labelKey()));
        }
        if (this.referenceLangButton != null) {
            this.referenceLangButton.setMessage(Component.translatable(PREFIX + "toggle_lang", this.localReferenceLangDraft));
        }
        if (this.speedButton != null) {
            this.speedButton.setMessage(Component.translatable(PREFIX + "toggle_speed",
                    String.format(Locale.ROOT, "%.2f", this.speedDraft)));
        }
        if (this.volumeButton != null) {
            this.volumeButton.setMessage(Component.translatable(PREFIX + "toggle_volume",
                    String.format(Locale.ROOT, "%.1f", this.volumeDraft)));
        }
        if (this.maxCharsButton != null) {
            this.maxCharsButton.setMessage(Component.translatable(PREFIX + "toggle_max_chars", this.maxCharsDraft));
        }
        if (this.interruptButton != null) {
            this.interruptButton.setMessage(Component.translatable(PREFIX + "toggle_interrupt", stateLabel(this.interruptDraft)));
        }
    }

    private static Component stateLabel(boolean enabled) {
        return Component.translatable(STATE_PREFIX + (enabled ? "state.enabled" : "state.disabled"));
    }

    private static double nextSpeed(double current) {
        for (double choice : SPEED_CHOICES) {
            if (choice > current + 1.0E-4D) {
                return choice;
            }
        }
        return SPEED_CHOICES[0];
    }

    private static double nextVolume(double current) {
        for (double choice : VOLUME_CHOICES) {
            if (choice > current + 1.0E-4D) {
                return choice;
            }
        }
        return VOLUME_CHOICES[0];
    }

    private static int nextMaxChars(int current) {
        for (int choice : MAX_CHARS_CHOICES) {
            if (choice > current) {
                return choice;
            }
        }
        return MAX_CHARS_CHOICES[0];
    }

    private static String nextLanguage(String current) {
        for (int index = 0; index < LANGUAGES.length; index++) {
            if (LANGUAGES[index].equalsIgnoreCase(current)) {
                return LANGUAGES[(index + 1) % LANGUAGES.length];
            }
        }
        return LANGUAGES[0];
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    // ---------------- 绘制 ----------------

    @Override
    public void renderBackground(@NotNull GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        // 不压暗世界：与 LLM 设置页保持一致
    }

    @Override
    public void render(@NotNull GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        TtsLayout layout = this.createLayout();
        HeroTheme.plainPanel(guiGraphics, layout.startX(), layout.startY(), layout.panelWidth(), layout.panelHeight());
        guiGraphics.drawCenteredString(this.font, this.title,
                layout.startX() + layout.panelWidth() / 2, layout.startY() + 7, COL_TITLE);

        for (int index = 0; index < this.rows.size(); index++) {
            Row row = this.rows.get(index);
            if (row.labelKey() == null) {
                continue;
            }
            guiGraphics.drawString(this.font, Component.translatable(row.labelKey()),
                    layout.contentX(), layout.rowY(index + 1) + 5, COL_LABEL, false);
        }

        Component hint = this.buildStatusHint();
        int color = this.isStatusWarning() ? COL_WARN : COL_OK;
        guiGraphics.drawWordWrap(this.font, hint, layout.contentX(), layout.hintY(),
                layout.contentWidth(), color);

        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    private boolean isEngineIncomplete() {
        return switch (this.engineDraft) {
            case SYSTEM -> !SystemSpeechOutput.platformSupportsSystemSpeech();
            case CLOUD -> nullToEmpty(this.cloudEndpointDraft).isEmpty()
                    || nullToEmpty(this.cloudApiKeyDraft).isEmpty();
            case LOCAL -> nullToEmpty(this.localEndpointDraft).isEmpty();
        };
    }

    private Component buildStatusHint() {
        // 试听产生的结果优先显示：玩家点完按钮最想知道的就是"成了没有、错在哪"
        Component preview = this.buildPreviewHint();
        if (preview != null) {
            return preview;
        }
        if (this.isEngineIncomplete()) {
            return Component.translatable(this.engineDraft == TtsEngine.SYSTEM
                    ? PREFIX + "warning.system_unavailable"
                    : this.engineDraft == TtsEngine.CLOUD
                    ? PREFIX + "warning.cloud_incomplete"
                    : PREFIX + "warning.local_incomplete");
        }
        if (!this.enabledDraft) {
            return Component.translatable(PREFIX + "hint_disabled");
        }
        if (this.engineDraft == TtsEngine.LOCAL) {
            return Component.translatable(this.localProtocolDraft.hintKey());
        }
        return Component.translatable(PREFIX + "hint");
    }

    /**
     * 试听状态：失败显示具体原因（含服务端原文），成功显示已播放，进行中显示等待。
     *
     * <p>返回 {@code null} 表示没有可显示的试听状态，交给常规提示。</p>
     */
    private Component buildPreviewHint() {
        TtsSpeechService service = TtsSpeechService.getInstance();
        if (this.previewFailed && this.previewMessage != null) {
            return Component.literal(this.previewMessage);
        }
        String error = service.lastError();
        if (error != null) {
            return Component.translatable(PREFIX + "preview.failed", error);
        }
        if (this.previewStartedAt <= 0L) {
            return null;
        }
        // 试听已发出但还没出结果：区分"合成中"与"其实没有反应"
        long elapsed = System.currentTimeMillis() - this.previewStartedAt;
        if (elapsed < PREVIEW_PENDING_MS) {
            return Component.translatable(PREFIX + "preview.pending");
        }
        if (service.successCount() == 0L) {
            return Component.translatable(PREFIX + "preview.timeout");
        }
        return Component.translatable(PREFIX + "preview.ok");
    }

    /** 试听结果是否需要用告警色显示。 */
    private boolean isStatusWarning() {
        TtsSpeechService service = TtsSpeechService.getInstance();
        return this.previewFailed || service.lastError() != null || this.isEngineIncomplete();
    }

    // ---------------- 输入框编辑转发 ----------------
    // 原版 EditBox 不会自己处理拖动选择与剪切/全选等快捷键，
    // 必须由界面把这些事件转发给它，这里统一转给所有输入框。

    /** 当前界面里所有可编辑的输入框。 */
    private List<EditBox> editableBoxes() {
        List<EditBox> boxes = new ArrayList<>(12);
        addIfPresent(boxes, this.systemVoiceBox);
        addIfPresent(boxes, this.cloudEndpointBox);
        addIfPresent(boxes, this.cloudModelBox);
        addIfPresent(boxes, this.cloudVoiceBox);
        addIfPresent(boxes, this.cloudApiKeyBox);
        addIfPresent(boxes, this.localEndpointBox);
        addIfPresent(boxes, this.localModelBox);
        addIfPresent(boxes, this.localVoiceBox);
        addIfPresent(boxes, this.localKeyBox);
        addIfPresent(boxes, this.localReferenceAudioBox);
        addIfPresent(boxes, this.localReferenceTextBox);
        return boxes;
    }

    private static void addIfPresent(List<EditBox> boxes, EditBox box) {
        if (box != null) {
            boxes.add(box);
        }
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (MouseSelectableEditBox.dispatchChar(this.editableBoxes(), codePoint, modifiers)) {
            return true;
        }
        return super.charTyped(codePoint, modifiers);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (MouseSelectableEditBox.dispatchKey(this.editableBoxes(), keyCode, scanCode, modifiers)) {
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (MouseSelectableEditBox.dispatchDrag(this.editableBoxes(), mouseX, mouseY, button, dragX, dragY)) {
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (MouseSelectableEditBox.dispatchRelease(this.editableBoxes(), mouseX, mouseY, button)) {
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }
    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
