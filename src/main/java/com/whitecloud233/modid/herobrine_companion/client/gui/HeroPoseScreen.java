package com.whitecloud233.modid.herobrine_companion.client.gui;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.whitecloud233.modid.herobrine_companion.entity.HeroEntity;
import com.whitecloud233.modid.herobrine_companion.event.ModEvents;
import com.whitecloud233.modid.herobrine_companion.network.PacketHandler;
import com.whitecloud233.modid.herobrine_companion.network.SavePosePacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;

import java.io.File;
import java.lang.reflect.Type;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

public class HeroPoseScreen extends Screen {

    private final int entityId;
    private HeroEntity dummyHero;

    // --- 视角与位置控制 ---
    private float lookX = 180;
    private float lookY = 0;
    private boolean isRotating = false;
    private boolean isPanning = false;
    private float renderOffsetX = 0;
    private float renderOffsetY = 0;
    private float renderScale = 75.0f;

    // --- 左侧滚动条 ---
    private float scrollOffset = 0;
    private boolean isDraggingScrollbar = false;
    private final List<Button> partButtons = new ArrayList<>();

    // 姿势数据状态
    private int selectedPart = 0;
    private final String[] partKeys = {
            "gui.herobrine_companion.pose.head",
            "gui.herobrine_companion.pose.body",
            "gui.herobrine_companion.pose.right_arm_upper",
            "gui.herobrine_companion.pose.right_arm_lower",
            "gui.herobrine_companion.pose.left_arm_upper",
            "gui.herobrine_companion.pose.left_arm_lower",
            "gui.herobrine_companion.pose.right_leg_upper",
            "gui.herobrine_companion.pose.right_leg_lower",
            "gui.herobrine_companion.pose.left_leg_upper",
            "gui.herobrine_companion.pose.left_leg_lower"
    };

    // --- 主界面 UI 组件 ---
    private PoseSlider sliderX;
    private PoseSlider sliderY;
    private PoseSlider sliderZ;
    private Button btnResetPart;
    private Button btnOpenPresets;
    private Button btnSavePose;
    private Button btnClearPose;

    // --- 预设新页面 UI 组件 ---
    private boolean isPresetMenuOpen = false;
    private float presetScrollOffset = 0;
    private boolean isDraggingPresetScrollbar = false;
    private final List<Button> dynamicPresetButtons = new ArrayList<>();

    private EditBox presetNameBox;
    private Button btnSavePreset;
    private Button btnLoadPreset;
    private Button btnDeletePreset;
    private Button btnClosePresets;

    // --- 预设数据存储 ---
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private Map<String, float[][]> savedPresets = new HashMap<>();
    private List<String> presetNames = new ArrayList<>();
    private int selectedPresetIndex = -1;

    public HeroPoseScreen(int entityId) {
        super(Component.translatable("gui.herobrine_companion.pose_editor_title"));
        this.entityId = entityId;
    }

    @Override
    protected void init() {
        super.init();
        if (this.minecraft == null || this.minecraft.level == null) return;

        this.dummyHero = ModEvents.HERO.get().create(this.minecraft.level);
        this.dummyHero.isPoseEditing = true;

        Entity realEntity = this.minecraft.level.getEntity(this.entityId);
        if (realEntity instanceof HeroEntity realHero) {
            this.dummyHero.setSkinVariant(realHero.getSkinVariant());
            if (realHero.getSkinVariant() == HeroEntity.SKIN_CUSTOM) {
                this.dummyHero.setCustomSkinName(realHero.getCustomSkinName());
            }

            if (realHero.isPoseEditing) {
                for (int i = 0; i < 10; i++) {
                    System.arraycopy(realHero.customPoseAngles[i], 0, this.dummyHero.customPoseAngles[i], 0, 3);
                }
            }
        }

        int centerX = this.width / 2;
        int bottomY = this.height - 25;

        // --- 初始化主界面按钮 ---
        btnSavePose = this.addRenderableWidget(Button.builder(Component.translatable("gui.herobrine_companion.save_pose"), button -> {
            PacketHandler.sendToServer(new SavePosePacket(this.entityId, true, this.dummyHero.customPoseAngles));
            applyPoseToRealEntity(true);
            this.minecraft.setScreen(new HeroScreen(this.entityId));
        }).bounds(centerX - 45, bottomY, 90, 20).build());

        btnClearPose = this.addRenderableWidget(Button.builder(Component.translatable("gui.herobrine_companion.clear_pose"), button -> {
            PacketHandler.sendToServer(new SavePosePacket(this.entityId, false, new float[10][3]));
            applyPoseToRealEntity(false);
            this.minecraft.setScreen(new HeroScreen(this.entityId));
        }).bounds(centerX + 55, bottomY, 90, 20).build());

        partButtons.clear();
        for (int i = 0; i < partKeys.length; i++) {
            final int partIndex = i;
            Button btn = Button.builder(Component.translatable(partKeys[i]), button -> {
                this.selectedPart = partIndex;
                updateSlidersToCurrentPart();
            }).bounds(10, 0, 85, 20).build();
            partButtons.add(btn);
        }

        int rightPanelX = this.width - 130;
        sliderX = this.addRenderableWidget(new PoseSlider(rightPanelX, 40, 120, 20, "gui.herobrine_companion.pose.rot_x", 0));
        sliderY = this.addRenderableWidget(new PoseSlider(rightPanelX, 65, 120, 20, "gui.herobrine_companion.pose.rot_y", 1));
        sliderZ = this.addRenderableWidget(new PoseSlider(rightPanelX, 90, 120, 20, "gui.herobrine_companion.pose.rot_z", 2));

        btnResetPart = this.addRenderableWidget(Button.builder(Component.translatable("gui.herobrine_companion.pose.reset_part"), button -> {
            this.dummyHero.customPoseAngles[selectedPart][0] = 0;
            this.dummyHero.customPoseAngles[selectedPart][1] = 0;
            this.dummyHero.customPoseAngles[selectedPart][2] = 0;
            updateSlidersToCurrentPart();
        }).bounds(rightPanelX, 115, 120, 20).build());

        // 进入预设管理页面的按钮
        btnOpenPresets = this.addRenderableWidget(Button.builder(Component.translatable("gui.herobrine_companion.pose.manage_presets"), button -> {
            this.isPresetMenuOpen = true;
            updateUIVisibility();
        }).bounds(rightPanelX, 150, 120, 20).build());

        // --- 初始化预设页面按钮 (初始隐藏) ---
        loadPresetsFromFile();

        presetNameBox = new EditBox(this.font, centerX - 100, this.height - 85, 200, 16, Component.literal("Preset Name"));
        presetNameBox.setMaxLength(30);
        this.addRenderableWidget(presetNameBox);

        btnSavePreset = this.addRenderableWidget(Button.builder(Component.translatable("gui.herobrine_companion.pose.save_preset"), button -> {
            String name = presetNameBox.getValue().trim();
            if (!name.isEmpty()) {
                float[][] copy = new float[10][3];
                for (int i = 0; i < 10; i++) System.arraycopy(dummyHero.customPoseAngles[i], 0, copy[i], 0, 3);
                savedPresets.put(name, copy);
                if (!presetNames.contains(name)) presetNames.add(name);
                selectedPresetIndex = presetNames.indexOf(name);
                savePresetsToFile();
                refreshPresetButtons();
                presetNameBox.setValue("");
            }
        }).bounds(centerX - 100, this.height - 60, 60, 20).build());

        btnLoadPreset = this.addRenderableWidget(Button.builder(Component.translatable("gui.herobrine_companion.pose.load_preset"), button -> {
            if (selectedPresetIndex >= 0 && selectedPresetIndex < presetNames.size()) {
                String name = presetNames.get(selectedPresetIndex);
                float[][] saved = savedPresets.get(name);
                if (saved != null) {
                    for (int i = 0; i < 10; i++) System.arraycopy(saved[i], 0, dummyHero.customPoseAngles[i], 0, 3);
                    updateSlidersToCurrentPart();
                }
            }
        }).bounds(centerX - 35, this.height - 60, 65, 20).build());

        btnDeletePreset = this.addRenderableWidget(Button.builder(Component.translatable("gui.herobrine_companion.pose.delete_preset"), button -> {
            if (selectedPresetIndex >= 0 && selectedPresetIndex < presetNames.size()) {
                String name = presetNames.get(selectedPresetIndex);
                savedPresets.remove(name);
                presetNames.remove(selectedPresetIndex);
                selectedPresetIndex = -1;
                savePresetsToFile();
                refreshPresetButtons();
            }
        }).bounds(centerX + 35, this.height - 60, 65, 20).build());

        btnClosePresets = this.addRenderableWidget(Button.builder(Component.translatable("gui.back"), button -> {
            this.isPresetMenuOpen = false;
            updateUIVisibility();
        }).bounds(centerX - 100, this.height - 30, 200, 20).build());

        refreshPresetButtons();
        updateUIVisibility();
        updateSlidersToCurrentPart();
    }

    private void applyPoseToRealEntity(boolean active) {
        if (this.minecraft != null && this.minecraft.level != null) {
            Entity targetEntity = this.minecraft.level.getEntity(this.entityId);
            if (targetEntity instanceof HeroEntity targetHero) {
                targetHero.isPoseEditing = active;
                if (active) {
                    for (int i = 0; i < 10; i++) {
                        System.arraycopy(this.dummyHero.customPoseAngles[i], 0, targetHero.customPoseAngles[i], 0, 3);
                    }
                } else {
                    targetHero.customPoseAngles = new float[10][3];
                }
            }
        }
    }

    private void updateUIVisibility() {
        boolean mainVis = !isPresetMenuOpen;
        sliderX.visible = mainVis;
        sliderY.visible = mainVis;
        sliderZ.visible = mainVis;
        btnResetPart.visible = mainVis;
        btnOpenPresets.visible = mainVis;
        btnSavePose.visible = mainVis;
        btnClearPose.visible = mainVis;

        boolean presetVis = isPresetMenuOpen;
        presetNameBox.visible = presetVis;
        btnSavePreset.visible = presetVis;
        btnLoadPreset.visible = presetVis;
        btnDeletePreset.visible = presetVis;
        btnClosePresets.visible = presetVis;
    }

    private void refreshPresetButtons() {
        dynamicPresetButtons.clear();
        int centerX = this.width / 2;
        for (int i = 0; i < presetNames.size(); i++) {
            String name = presetNames.get(i);
            final int index = i;
            Button b = Button.builder(Component.literal(name), btn -> selectedPresetIndex = index).bounds(centerX - 90, 0, 180, 20).build();
            dynamicPresetButtons.add(b);
        }
    }

    private File getPresetFile() {
        Minecraft mc = Minecraft.getInstance();
        String worldIdentifier = "unknown";
        if (mc.getSingleplayerServer() != null) {
            worldIdentifier = "local_" + mc.getSingleplayerServer().getWorldData().getLevelName();
        } else if (mc.getCurrentServer() != null) {
            worldIdentifier = "server_" + mc.getCurrentServer().ip;
        }
        worldIdentifier = worldIdentifier.replaceAll("[^a-zA-Z0-9\\-_]", "_");

        File dir = new File(mc.gameDirectory, "config/herobrine_companion/poses");
        if (!dir.exists()) dir.mkdirs();

        return new File(dir, worldIdentifier + ".json");
    }

    private void loadPresetsFromFile() {
        File file = getPresetFile();
        if (file.exists()) {
            try {
                savedPresets = this.readPresetMap(file);
                if (savedPresets == null) savedPresets = new HashMap<>();
                presetNames = new ArrayList<>(savedPresets.keySet());
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void savePresetsToFile() {
        try (var writer = Files.newBufferedWriter(getPresetFile().toPath(), StandardCharsets.UTF_8)) {
            GSON.toJson(savedPresets, writer);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private Map<String, float[][]> readPresetMap(File file) throws Exception {
        byte[] raw = Files.readAllBytes(file.toPath());
        Map<String, float[][]> fallback = null;
        for (Charset charset : this.getPresetCharsets()) {
            Map<String, float[][]> parsed = this.tryParsePresetMap(raw, charset);
            if (parsed == null) {
                continue;
            }
            if (!this.containsReplacementCharacter(parsed)) {
                return parsed;
            }
            if (fallback == null) {
                fallback = parsed;
            }
        }
        return fallback;
    }

    private Map<String, float[][]> tryParsePresetMap(byte[] raw, Charset charset) {
        try {
            Type type = new TypeToken<Map<String, float[][]>>(){}.getType();
            return GSON.fromJson(new String(raw, charset), type);
        } catch (Exception ignored) {
            return null;
        }
    }

    private List<Charset> getPresetCharsets() {
        LinkedHashSet<Charset> charsets = new LinkedHashSet<>();
        charsets.add(StandardCharsets.UTF_8);
        charsets.add(Charset.defaultCharset());
        charsets.add(Charset.forName("GB18030"));
        return new ArrayList<>(charsets);
    }

    private boolean containsReplacementCharacter(Map<String, float[][]> presets) {
        if (presets == null) {
            return false;
        }
        for (String name : presets.keySet()) {
            if (name != null && name.indexOf('\uFFFD') >= 0) {
                return true;
            }
        }
        return false;
    }

    private void updateSlidersToCurrentPart() {
        if (this.dummyHero != null) {
            sliderX.setAngle(this.dummyHero.customPoseAngles[selectedPart][0]);
            sliderY.setAngle(this.dummyHero.customPoseAngles[selectedPart][1]);
            sliderZ.setAngle(this.dummyHero.customPoseAngles[selectedPart][2]);
        }
    }

    // 【修复 1】1.20.1 中的 renderBackground 只有 1 个参数
    @Override
    public void renderBackground(GuiGraphics guiGraphics) {
        // 留空：禁用原版自带的世界模糊和黑色背景遮罩
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        // 【修复 2】配合 renderBackground 参数修改
        this.renderBackground(guiGraphics);

        int centerX = this.width / 2;
        int centerY = this.height / 2;

        // --- 1. 先在最底层渲染 3D 模型 ---
        if (this.dummyHero != null) {
            this.dummyHero.yBodyRot = lookX;
            this.dummyHero.setYRot(lookX);
            this.dummyHero.yHeadRot = lookX;
            this.dummyHero.yHeadRotO = lookX;
            this.dummyHero.setXRot(lookY);

            int renderX = centerX + (int)renderOffsetX;
            int renderY = centerY + 90 + (int)renderOffsetY;

            org.joml.Quaternionf pose = new org.joml.Quaternionf().rotationZ((float) Math.PI);

            // 【核心修复 3】1.20.1 的 renderEntityInInventory 只有 7 个参数，不能传 Vector3f
            InventoryScreen.renderEntityInInventory(
                    guiGraphics,
                    renderX, renderY,
                    (int)renderScale,
                    pose,
                    null,
                    this.dummyHero
            );
        }

        // --- 2. 强行拉高画笔的 Z 轴，确保所有 GUI 和黑色遮罩都不会被 3D 实体穿模 ---
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(0, 0, 250);

        // 渲染两侧半透明背景
        guiGraphics.fill(0, 0, 110, this.height, 0x88000000);
        guiGraphics.fill(this.width - 140, 0, this.width, this.height, 0x88000000);

        if (!isPresetMenuOpen) {
            // 标题和提示文字只有在预设页面未打开时才渲染
            guiGraphics.drawCenteredString(this.font, Component.translatable("gui.herobrine_companion.pose.title_3d"), centerX, 10, 0xFFFFFF);
            guiGraphics.drawCenteredString(this.font, Component.translatable("gui.herobrine_companion.pose.drag_hint"), centerX, 25, 0xAAAAAA);
            guiGraphics.drawCenteredString(this.font, Component.literal("Left: Rotate | Right: Pan | Scroll: Zoom | Mid: Reset"), centerX, 38, 0x777777);

            Component editLabel = Component.translatable("gui.herobrine_companion.pose.editing").append(Component.translatable(partKeys[selectedPart]));
            guiGraphics.drawString(this.font, editLabel, this.width - 130, 25, 0x55FF55);

            // 渲染左侧滚动列表
            int viewYStart = 40;
            int viewYEnd = this.height - 35;
            int viewHeight = viewYEnd - viewYStart;
            int contentHeight = partKeys.length * 24;
            int maxScroll = Math.max(0, contentHeight - viewHeight);
            this.scrollOffset = Math.max(0, Math.min(this.scrollOffset, maxScroll));

            guiGraphics.enableScissor(0, viewYStart, 110, viewYEnd);
            for (int i = 0; i < partButtons.size(); i++) {
                Button btn = partButtons.get(i);
                btn.setY(viewYStart + (i * 24) - (int)scrollOffset);
                btn.render(guiGraphics, mouseX, mouseY, partialTick);
            }
            guiGraphics.disableScissor();

            if (maxScroll > 0) {
                int barX = 100, barW = 4;
                int thumbH = Math.max(20, (int) ((viewHeight / (float) contentHeight) * viewHeight));
                int thumbY = viewYStart + (int) ((scrollOffset / maxScroll) * (viewHeight - thumbH));
                guiGraphics.fill(barX, viewYStart, barX + barW, viewYEnd, 0x55000000);
                guiGraphics.fill(barX, thumbY, barX + barW, thumbY + thumbH, isDraggingScrollbar ? 0xFFFFFFFF : 0xFFAAAAAA);
            }
        } else {
            // --- 预设新页面 (独立渲染区) ---
            guiGraphics.fill(0, 0, this.width, this.height, 0xD0000000); // 黑色半透明全屏遮罩
            guiGraphics.drawCenteredString(this.font, Component.translatable("gui.herobrine_companion.pose.manage_presets"), centerX, 20, 0xFFAA00);

            int listYStart = 40;
            int listYEnd = this.height - 100;
            int listHeight = listYEnd - listYStart;
            int contentHeight = presetNames.size() * 24;
            int maxScroll = Math.max(0, contentHeight - listHeight);
            this.presetScrollOffset = Math.max(0, Math.min(this.presetScrollOffset, maxScroll));

            // 背景框
            guiGraphics.fill(centerX - 100, listYStart, centerX + 100, listYEnd, 0x88000000);

            guiGraphics.enableScissor(centerX - 100, listYStart, centerX + 100, listYEnd);
            for (int i = 0; i < dynamicPresetButtons.size(); i++) {
                Button btn = dynamicPresetButtons.get(i);
                btn.setY(listYStart + (i * 24) - (int)presetScrollOffset);

                if (i == selectedPresetIndex) {
                    guiGraphics.fill(centerX - 92, btn.getY() - 2, centerX + 92, btn.getY() + 22, 0x5500FF00);
                }
                btn.render(guiGraphics, mouseX, mouseY, partialTick);
            }
            guiGraphics.disableScissor();

            if (maxScroll > 0) {
                int barX = centerX + 94, barW = 4;
                int thumbH = Math.max(20, (int) ((listHeight / (float) contentHeight) * listHeight));
                int thumbY = listYStart + (int) ((presetScrollOffset / maxScroll) * (listHeight - thumbH));
                guiGraphics.fill(barX, listYStart, barX + barW, listYEnd, 0x55000000);
                guiGraphics.fill(barX, thumbY, barX + barW, thumbY + thumbH, isDraggingPresetScrollbar ? 0xFFFFFFFF : 0xFFAAAAAA);
            }
        }

        // --- 3. 渲染其余按钮组件 ---
        super.render(guiGraphics, mouseX, mouseY, partialTick);

        // 恢复画笔原本的 Z 轴
        guiGraphics.pose().popPose();
    }

    // 【核心修复 4】1.20.1 中的 mouseScrolled 只有 3 个参数 (X轴 Y轴 和 增量delta)
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (isPresetMenuOpen) {
            int listHeight = (this.height - 100) - 40;
            int maxScroll = Math.max(0, presetNames.size() * 24 - listHeight);
            if (maxScroll > 0) {
                presetScrollOffset -= (float) (delta * 18.0f);
                presetScrollOffset = Math.max(0, Math.min(presetScrollOffset, maxScroll));
                return true;
            }
        } else {
            // 滚动侧边栏
            int viewHeight = (this.height - 35) - 40;
            int maxScroll = Math.max(0, partKeys.length * 24 - viewHeight);
            if (mouseX < 110 && maxScroll > 0) {
                scrollOffset -= (float) (delta * 18.0f);
                scrollOffset = Math.max(0, Math.min(scrollOffset, maxScroll));
                return true;
            }

            // --- 模型缩放功能 ---
            if (mouseX > 110 && mouseX < this.width - 140) {
                renderScale += (float) (delta * 8.0f);
                renderScale = Math.max(20.0f, Math.min(250.0f, renderScale)); // 限制缩放大小
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (isPresetMenuOpen) {
            int centerX = this.width / 2;
            int listYStart = 40;
            int listYEnd = this.height - 100;

            if (mouseX >= centerX - 100 && mouseX <= centerX + 100 && mouseY >= listYStart && mouseY <= listYEnd) {
                int maxScroll = Math.max(0, presetNames.size() * 24 - (listYEnd - listYStart));
                if (maxScroll > 0 && mouseX >= centerX + 92) {
                    isDraggingPresetScrollbar = true;
                    return true;
                }
                for (Button btn : dynamicPresetButtons) {
                    if (btn.mouseClicked(mouseX, mouseY, button)) return true;
                }
            }
            return super.mouseClicked(mouseX, mouseY, button);
        }

        int viewYStart = 40;
        int viewYEnd = this.height - 35;

        if (mouseX < 110 && mouseY >= viewYStart && mouseY <= viewYEnd) {
            int maxScroll = Math.max(0, partKeys.length * 24 - (viewYEnd - viewYStart));
            if (maxScroll > 0 && mouseX >= 95 && mouseX <= 108) {
                isDraggingScrollbar = true;
                return true;
            }
            for (Button btn : partButtons) {
                if (btn.mouseClicked(mouseX, mouseY, button)) return true;
            }
        }

        // --- 鼠标在中间 3D 区域点击 ---
        if (mouseX > 110 && mouseX < this.width - 140 && mouseY < this.height - 30) {
            if (button == 0) isRotating = true; // 左键旋转
            if (button == 1) isPanning = true;  // 右键平移
            if (button == 2) {                  // 中键重置视角
                renderOffsetX = 0;
                renderOffsetY = 0;
                renderScale = 75.0f;
                lookX = 180;
                lookY = 0;
            }
            return true;
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        isDraggingScrollbar = false;
        isDraggingPresetScrollbar = false;
        if (button == 0) isRotating = false;
        if (button == 1) isPanning = false;

        if (isPresetMenuOpen) {
            for (Button btn : dynamicPresetButtons) btn.mouseReleased(mouseX, mouseY, button);
        } else {
            for (Button btn : partButtons) btn.mouseReleased(mouseX, mouseY, button);
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (isPresetMenuOpen && isDraggingPresetScrollbar) {
            int listHeight = (this.height - 100) - 40;
            int maxScroll = Math.max(0, presetNames.size() * 24 - listHeight);
            if (maxScroll > 0) {
                int thumbH = Math.max(20, (int) ((listHeight / (float) (presetNames.size() * 24)) * listHeight));
                presetScrollOffset += ((float) dragY / (listHeight - thumbH)) * maxScroll;
                presetScrollOffset = Math.max(0, Math.min(presetScrollOffset, maxScroll));
                return true;
            }
        }

        if (!isPresetMenuOpen && isDraggingScrollbar) {
            int viewHeight = (this.height - 35) - 40;
            int maxScroll = Math.max(0, partKeys.length * 24 - viewHeight);
            if (maxScroll > 0) {
                int thumbH = Math.max(20, (int) ((viewHeight / (float) (partKeys.length * 24)) * viewHeight));
                scrollOffset += ((float) dragY / (viewHeight - thumbH)) * maxScroll;
                scrollOffset = Math.max(0, Math.min(scrollOffset, maxScroll));
                return true;
            }
        }

        // --- 模型拖动逻辑 ---
        if (!isPresetMenuOpen) {
            if (isRotating) { // 左键拖拽旋转
                lookX += (float) dragX * 2.0f;
                lookY -= (float) dragY * 2.0f;
                lookY = Math.max(-90, Math.min(90, lookY));
                return true;
            } else if (isPanning) { // 右键拖拽平移
                renderOffsetX += (float) dragX;
                renderOffsetY += (float) dragY;
                return true;
            }
        }

        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private class PoseSlider extends AbstractSliderButton {
        private final int axis;
        private final String prefixKey;

        public PoseSlider(int x, int y, int width, int height, String prefixKey, int axis) {
            super(x, y, width, height, Component.empty(), 0.5D);
            this.prefixKey = prefixKey;
            this.axis = axis;
            this.updateMessage();
        }

        public void setAngle(float radians) {
            this.value = (radians + Math.PI) / (Math.PI * 2);
            this.updateMessage();
        }

        @Override
        protected void updateMessage() {
            float degrees = (float) (this.value * 360.0 - 180.0);
            this.setMessage(Component.translatable(prefixKey).append(": " + String.format("%.1f°", degrees)));
        }

        @Override
        protected void applyValue() {
            float radians = (float) (this.value * Math.PI * 2 - Math.PI);
            if (dummyHero != null) {
                dummyHero.customPoseAngles[selectedPart][axis] = radians;
            }
        }
    }
}