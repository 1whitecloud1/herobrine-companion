package com.whitecloud233.modid.herobrine_companion.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.whitecloud233.modid.herobrine_companion.HerobrineCompanion;
import com.whitecloud233.modid.herobrine_companion.client.model.HeroModel;
import com.whitecloud233.modid.herobrine_companion.entity.HeroEntity;
import com.whitecloud233.modid.herobrine_companion.compat.ArmourerWorkshop.HeroAWCompat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.HumanoidArm;

public class HeroHeldItemLayer extends RenderLayer<HeroEntity, PlayerModel<HeroEntity>> {

    public HeroHeldItemLayer(RenderLayerParent<HeroEntity, PlayerModel<HeroEntity>> renderer) {
        super(renderer);
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource buffer, int packedLight, HeroEntity entity, float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {

        // ==========================================
        // 状态 1：专属动作 —— 抚摸镰刀
        // ==========================================
        if (entity.isInspectingScythe()) {
            ItemStack scytheStack = new ItemStack(HerobrineCompanion.POEM_OF_THE_END.get());
            if (scytheStack.isEmpty()) return;

            poseStack.pushPose();
            ((HeroModel)this.getParentModel()).rightArm.translateAndRotate(poseStack);
            poseStack.translate(0.1F, 0.5F, 0.1F);
            poseStack.mulPose(Axis.ZP.rotationDegrees(90.0F));
            poseStack.mulPose(Axis.XP.rotationDegrees(180.0F));
            poseStack.mulPose(Axis.YP.rotationDegrees(30.0F));

            // 注意：恢复传入 entity，绝不能用 LocalPlayer！
            Minecraft.getInstance().getItemRenderer().renderStatic(
                    entity, scytheStack, ItemDisplayContext.THIRD_PERSON_RIGHT_HAND, false, poseStack, buffer, entity.level(), packedLight, net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY, entity.getId()
            );

            poseStack.popPose();
            return;
        }

        // ==========================================
        // 状态 2：平时手持武器
        // ==========================================
        boolean isRightHanded = entity.getMainArm() == HumanoidArm.RIGHT;
        ItemStack rightHandItem = isRightHanded ? entity.getMainHandItem() : entity.getOffhandItem();
        ItemStack leftHandItem = isRightHanded ? entity.getOffhandItem() : entity.getMainHandItem();

        if (!rightHandItem.isEmpty()) {
            this.renderArmWithItem(entity, rightHandItem, ItemDisplayContext.THIRD_PERSON_RIGHT_HAND, HumanoidArm.RIGHT, poseStack, buffer, packedLight);
        }
        if (!leftHandItem.isEmpty()) {
            this.renderArmWithItem(entity, leftHandItem, ItemDisplayContext.THIRD_PERSON_LEFT_HAND, HumanoidArm.LEFT, poseStack, buffer, packedLight);
        }
    }

    private void renderArmWithItem(HeroEntity entity, ItemStack itemStack, ItemDisplayContext displayContext, HumanoidArm arm, PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        poseStack.pushPose();

        this.getParentModel().translateToHand(arm, poseStack);
        poseStack.mulPose(com.mojang.math.Axis.XP.rotationDegrees(-90.0F));
        poseStack.mulPose(com.mojang.math.Axis.YP.rotationDegrees(180.0F));
        boolean isLeftHand = arm == HumanoidArm.LEFT;
        poseStack.translate((float)(isLeftHand ? -1 : 1) / 16.0F, 0.125F, -0.625F);

        if (HeroAWCompat.isLoaded() && HeroAWCompat.isAwItem(itemStack)) {
            float perfectScale = 16.0F;
            poseStack.scale(perfectScale, perfectScale, perfectScale);

            // ==========================================
            // 【镰刀专用调整区】
            // ==========================================
// 1. 水平旋转 (左右转动)

            // 2. 【核心】上下倾斜 (XP 轴)
            // 数值为正：武器尖端向下低头
            // 数值为负：武器尖端向上抬头
            // 建议步长：从 10.0F 或 -10.0F 开始试探
            poseStack.mulPose(com.mojang.math.Axis.XP.rotationDegrees(20.0F));
            // 1. 解决“方向反了”：
            // 如果镰刀刃朝后，把 180.0F 改为 0.0F；
            // 如果镰刀是侧着的，可以尝试 90.0F 或 270.0F。
            poseStack.mulPose(com.mojang.math.Axis.YP.rotationDegrees(0.0F));

            // 2. 解决“镰刀柄在手臂后”：
            // 参数说明：translate(左右, 上下, 前后)
            // 如果柄在手臂后面，你需要调整第三个参数（Z轴）。
            // 尝试把 0.05D 改为负数（如 -0.1D）将其拉到手心前方。
            // 如果柄太靠上或靠下，调整第二个参数（Y轴）。
            poseStack.translate(0D, 0.15D, 0D);

            // ==========================================
        }

        Minecraft.getInstance().getItemRenderer().renderStatic(
                entity, itemStack, displayContext, isLeftHand, poseStack, buffer, entity.level(), packedLight, net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY, entity.getId()
        );

        poseStack.popPose();
    }
}