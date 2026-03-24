package com.whitecloud233.herobrine_companion.client.gui;

import com.whitecloud233.herobrine_companion.entity.HeroEntity;
import com.whitecloud233.herobrine_companion.event.ModEvents;
import com.whitecloud233.herobrine_companion.network.PacketHandler;
import com.whitecloud233.herobrine_companion.network.SavePosePacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;

import java.util.ArrayList;
import java.util.List;

public class HeroPoseScreen extends Screen {

    private final int entityId;
    private HeroEntity dummyHero;

    // 视角控制
    private float lookX = 180;
    private float lookY = 0;
    private boolean isDragging = false;

    // --- 左侧滚动条专用变量 ---
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

    private PoseSlider sliderX;
    private PoseSlider sliderY;
    private PoseSlider sliderZ;

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

        // 底部保存按钮
        this.addRenderableWidget(Button.builder(Component.translatable("gui.herobrine_companion.save_pose"), button -> {
            PacketHandler.sendToServer(new SavePosePacket(this.entityId, true, this.dummyHero.customPoseAngles));

            if (this.minecraft != null && this.minecraft.level != null) {
                Entity targetEntity = this.minecraft.level.getEntity(this.entityId);
                if (targetEntity instanceof HeroEntity targetHero) {
                    targetHero.isPoseEditing = true;
                    for (int i = 0; i < 10; i++) {
                        System.arraycopy(this.dummyHero.customPoseAngles[i], 0, targetHero.customPoseAngles[i], 0, 3);
                    }
                }
            }

            this.minecraft.setScreen(new HeroScreen(this.entityId));
        }).bounds(centerX - 45, bottomY, 90, 20).build());

        // 清除按钮
        this.addRenderableWidget(Button.builder(Component.translatable("gui.herobrine_companion.clear_pose"), button -> {
            PacketHandler.sendToServer(new SavePosePacket(this.entityId, false, new float[10][3]));

            if (this.minecraft != null && this.minecraft.level != null) {
                Entity targetEntity = this.minecraft.level.getEntity(this.entityId);
                if (targetEntity instanceof HeroEntity targetHero) {
                    targetHero.isPoseEditing = false;
                    targetHero.customPoseAngles = new float[10][3];
                }
            }

            this.minecraft.setScreen(new HeroScreen(this.entityId));
        }).bounds(centerX + 55, bottomY, 90, 20).build());


        // --- 左侧滚动列表构建 ---
        partButtons.clear();
        for (int i = 0; i < partKeys.length; i++) {
            final int partIndex = i;
            Button btn = Button.builder(Component.translatable(partKeys[i]), button -> {
                this.selectedPart = partIndex;
                updateSlidersToCurrentPart();
            }).bounds(10, 0, 85, 20).build();
            partButtons.add(btn);
        }

        // 右侧滑块区域
        int rightPanelX = this.width - 130;
        sliderX = new PoseSlider(rightPanelX, 50, 120, 20, "gui.herobrine_companion.pose.rot_x", 0);
        sliderY = new PoseSlider(rightPanelX, 80, 120, 20, "gui.herobrine_companion.pose.rot_y", 1);
        sliderZ = new PoseSlider(rightPanelX, 110, 120, 20, "gui.herobrine_companion.pose.rot_z", 2);

        this.addRenderableWidget(sliderX);
        this.addRenderableWidget(sliderY);
        this.addRenderableWidget(sliderZ);

        this.addRenderableWidget(Button.builder(Component.translatable("gui.herobrine_companion.pose.reset_part"), button -> {
            this.dummyHero.customPoseAngles[selectedPart][0] = 0;
            this.dummyHero.customPoseAngles[selectedPart][1] = 0;
            this.dummyHero.customPoseAngles[selectedPart][2] = 0;
            updateSlidersToCurrentPart();
        }).bounds(rightPanelX, 140, 120, 20).build());

        updateSlidersToCurrentPart();
    }

    private void updateSlidersToCurrentPart() {
        if (this.dummyHero != null) {
            sliderX.setAngle(this.dummyHero.customPoseAngles[selectedPart][0]);
            sliderY.setAngle(this.dummyHero.customPoseAngles[selectedPart][1]);
            sliderZ.setAngle(this.dummyHero.customPoseAngles[selectedPart][2]);
        }
    }

    // ========================================================
    // 【核心修复】：禁用原版的黑色渐变遮罩和世界模糊（大雾）
    // ========================================================
    @Override
    public void renderBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        // 留空：禁用原版自带的世界模糊和黑色背景遮罩
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        // [修改 1] 1.21.1 的 renderBackground 必须传入四个参数 (现在由于重写了上面的方法，这行不会再画大雾了)
        this.renderBackground(guiGraphics, mouseX, mouseY, partialTick);

        int centerX = this.width / 2;
        int centerY = this.height / 2;

        guiGraphics.fill(0, 0, 110, this.height, 0x88000000);
        guiGraphics.fill(this.width - 140, 0, this.width, this.height, 0x88000000);

        guiGraphics.drawCenteredString(this.font, Component.translatable("gui.herobrine_companion.pose.title_3d"), centerX, 10, 0xFFFFFF);
        Component editLabel = Component.translatable("gui.herobrine_companion.pose.editing").append(Component.translatable(partKeys[selectedPart]));
        guiGraphics.drawString(this.font, editLabel, this.width - 130, 30, 0x55FF55);
        guiGraphics.drawCenteredString(this.font, Component.translatable("gui.herobrine_companion.pose.drag_hint"), centerX, 25, 0xAAAAAA);

        // --- 渲染左侧滚动列表 ---
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
            int barX = 100;
            int barW = 4;
            int thumbH = Math.max(20, (int) ((viewHeight / (float) contentHeight) * viewHeight));
            int thumbY = viewYStart + (int) ((scrollOffset / maxScroll) * (viewHeight - thumbH));

            guiGraphics.fill(barX, viewYStart, barX + barW, viewYEnd, 0x55000000);
            int thumbColor = isDraggingScrollbar ? 0xFFFFFFFF : 0xFFAAAAAA;
            guiGraphics.fill(barX, thumbY, barX + barW, thumbY + thumbH, thumbColor);
        }

        // --- 渲染 3D 模型 ---
        if (this.dummyHero != null) {
            this.dummyHero.yBodyRot = lookX;
            this.dummyHero.setYRot(lookX);
            this.dummyHero.yHeadRot = lookX;
            this.dummyHero.yHeadRotO = lookX;
            this.dummyHero.setXRot(lookY);

            // [修改 2] 1.21.1 使用新的 JOML 旋转渲染方式替代被移除的 FollowsMouse 方法
            // 翻转 Z 轴是因为 GUI 渲染体系和世界渲染体系的倒置特性
            org.joml.Quaternionf pose = new org.joml.Quaternionf().rotationZ((float) Math.PI);
            InventoryScreen.renderEntityInInventory(
                    guiGraphics,
                    centerX, centerY + 90,
                    75.0f,
                    new org.joml.Vector3f(0, 0, 0),
                    pose,
                    null,
                    this.dummyHero
            );
        }

        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    // --- 鼠标滚动事件 ---
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        // [修改 3] 1.21.1 鼠标滚动事件分为 scrollX 和 scrollY，原 delta 变为 scrollY
        int viewHeight = (this.height - 35) - 40;
        int contentHeight = partKeys.length * 24;
        int maxScroll = Math.max(0, contentHeight - viewHeight);

        if (mouseX < 110 && maxScroll > 0) {
            scrollOffset -= scrollY * 18.0f; // 取 scrollY 作为纵向滚动量
            scrollOffset = Math.max(0, Math.min(scrollOffset, maxScroll));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int viewYStart = 40;
        int viewYEnd = this.height - 35;

        if (mouseX < 110 && mouseY >= viewYStart && mouseY <= viewYEnd) {
            int viewHeight = viewYEnd - viewYStart;
            int contentHeight = partKeys.length * 24;
            int maxScroll = Math.max(0, contentHeight - viewHeight);

            if (maxScroll > 0 && mouseX >= 95 && mouseX <= 108) {
                isDraggingScrollbar = true;
                return true;
            }

            for (Button btn : partButtons) {
                if (btn.mouseClicked(mouseX, mouseY, button)) return true;
            }
        }

        if (mouseX > 110 && mouseX < this.width - 140 && mouseY < this.height - 30) {
            isDragging = true;
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        isDraggingScrollbar = false;
        isDragging = false;
        for (Button btn : partButtons) {
            btn.mouseReleased(mouseX, mouseY, button);
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (isDraggingScrollbar) {
            int viewHeight = (this.height - 35) - 40;
            int contentHeight = partKeys.length * 24;
            int maxScroll = Math.max(0, contentHeight - viewHeight);

            if (maxScroll > 0) {
                int thumbH = Math.max(20, (int) ((viewHeight / (float) contentHeight) * viewHeight));
                float scrollRatio = (float) dragY / (viewHeight - thumbH);
                scrollOffset += scrollRatio * maxScroll;
                scrollOffset = Math.max(0, Math.min(scrollOffset, maxScroll));
                return true;
            }
        }

        if (isDragging) {
            lookX += (float) dragX * 2.0f;
            lookY -= (float) dragY * 2.0f;
            lookY = Math.max(-90, Math.min(90, lookY));
            return true;
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