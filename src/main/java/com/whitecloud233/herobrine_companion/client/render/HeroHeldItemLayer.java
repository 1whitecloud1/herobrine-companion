package com.whitecloud233.herobrine_companion.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.whitecloud233.herobrine_companion.HerobrineCompanion;
import com.whitecloud233.herobrine_companion.client.model.HeroModel;
import com.whitecloud233.herobrine_companion.compat.cooking.HeroCookingCompat;
import com.whitecloud233.herobrine_companion.compat.epicfight.HeroEpicFightCompat;
import com.whitecloud233.herobrine_companion.compat.kaleidoscope.HeroKaleidoscopeCompat;
import com.whitecloud233.herobrine_companion.entity.HeroEntity;
import com.whitecloud233.herobrine_companion.compat.ArmourerWorkshop.HeroAWCompat;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

public class HeroHeldItemLayer extends ItemInHandLayer<HeroEntity, PlayerModel<HeroEntity>> {

    private final ItemInHandRenderer customItemInHandRenderer;
    private final HeroRenderer heroRenderer;

    public HeroHeldItemLayer(HeroRenderer renderer, ItemInHandRenderer itemInHandRenderer) {
        super(renderer, itemInHandRenderer);
        this.heroRenderer = renderer;
        this.customItemInHandRenderer = itemInHandRenderer;
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource buffer, int packedLight, HeroEntity entity, float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
        if (HeroEpicFightCompat.shouldUseEpicFightHeldItemLayer(entity)) {
            return;
        }
        if (entity.isInspectingScythe()) {
            ItemStack scytheStack = new ItemStack(HerobrineCompanion.POEM_OF_THE_END.get());
            if (scytheStack.isEmpty()) return;

            poseStack.pushPose();
            ((HeroModel)this.getParentModel()).rightArm.translateAndRotate(poseStack);
            poseStack.translate(0.1F, 0.5F, 0.1F);
            poseStack.mulPose(Axis.ZP.rotationDegrees(90.0F));
            poseStack.mulPose(Axis.XP.rotationDegrees(180.0F));
            poseStack.mulPose(Axis.YP.rotationDegrees(30.0F));

            this.customItemInHandRenderer.renderItem(entity, scytheStack, ItemDisplayContext.THIRD_PERSON_RIGHT_HAND, false, poseStack, buffer, packedLight);
            poseStack.popPose();
            return;
        }
        boolean isRightHanded = entity.getMainArm() == HumanoidArm.RIGHT;
        ItemStack rightHandItem = isRightHanded ? entity.getMainHandItem() : entity.getOffhandItem();
        ItemStack leftHandItem = isRightHanded ? entity.getOffhandItem() : entity.getMainHandItem();

        ItemStack visualMainHandItem = entity.getVisualMainHandItem();
        ItemStack visualOffHandItem = entity.getVisualOffHandItem();
        if (!visualMainHandItem.isEmpty()) {
            if (isRightHanded) rightHandItem = visualMainHandItem;
            else leftHandItem = visualMainHandItem;
        }
        if (!visualOffHandItem.isEmpty()) {
            if (isRightHanded) leftHandItem = visualOffHandItem;
            else rightHandItem = visualOffHandItem;
        }
        // 主动开启伪装，骗过 AW 武器渲染器
        ItemStack cookMainHandItem = HeroCookingCompat.getCookMainHandDisplay(entity);
        ItemStack cookOffhandItem = HeroCookingCompat.getCookOffhandDisplay(entity);
        if (!cookMainHandItem.isEmpty() || !cookOffhandItem.isEmpty()) {
            if (!visualMainHandItem.isEmpty()) {
                cookMainHandItem = visualMainHandItem;
            }
            if (!visualOffHandItem.isEmpty()) {
                cookOffhandItem = visualOffHandItem;
            }
            HumanoidArm mainArm = entity.getMainArm();
            if (!cookMainHandItem.isEmpty()) {
                this.renderArmWithItem(entity, cookMainHandItem,
                        mainArm == HumanoidArm.RIGHT ? ItemDisplayContext.THIRD_PERSON_RIGHT_HAND : ItemDisplayContext.THIRD_PERSON_LEFT_HAND,
                        mainArm, poseStack, buffer, packedLight);
            }
            if (!cookOffhandItem.isEmpty()) {
                HumanoidArm offhandArm = mainArm.getOpposite();
                this.renderArmWithItem(entity, cookOffhandItem,
                        offhandArm == HumanoidArm.RIGHT ? ItemDisplayContext.THIRD_PERSON_RIGHT_HAND : ItemDisplayContext.THIRD_PERSON_LEFT_HAND,
                        offhandArm, poseStack, buffer, packedLight);
            }
            return;
        }

        this.heroRenderer.spoofModelForAW = true;
        try {
            renderHands(poseStack, buffer, packedLight, entity, rightHandItem, leftHandItem);
        } finally {
            this.heroRenderer.spoofModelForAW = false;
        }
    }

    private void renderHands(PoseStack poseStack, MultiBufferSource buffer, int packedLight, HeroEntity entity,
                             ItemStack rightHandItem, ItemStack leftHandItem) {
        if (rightHandItem.isEmpty() && leftHandItem.isEmpty()) {
            return;
        }

        poseStack.pushPose();
        if (this.getParentModel().young) {
            poseStack.translate(0.0F, 0.75F, 0.0F);
            poseStack.scale(0.5F, 0.5F, 0.5F);
        }
        if (!rightHandItem.isEmpty()) {
            this.renderArmWithItem(entity, rightHandItem,
                    ItemDisplayContext.THIRD_PERSON_RIGHT_HAND,
                    HumanoidArm.RIGHT, poseStack, buffer, packedLight);
        }
        if (!leftHandItem.isEmpty()) {
            this.renderArmWithItem(entity, leftHandItem,
                    ItemDisplayContext.THIRD_PERSON_LEFT_HAND,
                    HumanoidArm.LEFT, poseStack, buffer, packedLight);
        }
        poseStack.popPose();
    }

    // =========================================================
    // [核心修复] 彻底接管单臂渲染：先平移到手心，再原地放大！
    // =========================================================
    // =========================================================
    // [核心修复] 彻底接管单臂渲染：先微调对齐手掌，再原地放大！
    // =========================================================
    @Override
    protected void renderArmWithItem(LivingEntity entity, ItemStack itemStack, ItemDisplayContext displayContext, HumanoidArm arm, PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        if (itemStack.isEmpty()) return;

        poseStack.pushPose();
        if (entity instanceof HeroEntity hero && shouldUseCookAnchor(hero, itemStack) && this.getParentModel() instanceof HeroModel heroModel) {
            heroModel.translateToUpperArm(arm, poseStack);
        } else {
            ((net.minecraft.client.model.ArmedModel) this.getParentModel()).translateToHand(arm, poseStack);
        }
        // 1. 先执行原版的手臂平移，把渲染矩阵的中心点移动到“手掌心”
        poseStack.mulPose(Axis.XP.rotationDegrees(-90.0F));
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F));
        boolean isLeftHand = arm == HumanoidArm.LEFT;
        poseStack.translate((float)(isLeftHand ? -1 : 1) / 16.0F, 0.125F, -0.625F);

        // 2. 针对 AW 武器进行精准的握把微调和放大
        if (HeroAWCompat.isAwItem(itemStack)) {

            // 【剑柄滑轨器】
            // 如果实体握着的是武器头部，说明武器整体太“靠下”了。
            // 调整 offsetY 的值（这里的单位是格/Block）。
            // 增大 offsetY 会把武器顺着剑刃方向“往上抽”，你可以尝试 0.5F, 0.8F 或 1.0F
            float offsetX = 0.0F;   // 左右偏移
            float offsetY = 1.9F;   // 上下偏移（沿着手臂/剑刃方向滑动）
            float offsetZ = 0.0F;   // 前后偏移
            poseStack.translate(offsetX, offsetY, offsetZ);

            // 完美对齐后，再放大 16 倍，此时绝不会跑偏！
            float scaleFactor = 16.0F;
            poseStack.scale(scaleFactor, scaleFactor, scaleFactor);
        }

        // 3. 渲染物品（千万不能再调用 super，否则会发生二次平移！）
        this.customItemInHandRenderer.renderItem(entity, itemStack, displayContext, isLeftHand, poseStack, buffer, packedLight);

        poseStack.popPose();
    }
    private boolean shouldUseCookAnchor(HeroEntity entity, ItemStack itemStack) {
        if (entity.getInvitedAction() != HeroCookingCompat.INVITED_ACTION_COOK || itemStack.isEmpty()) {
            return false;
        }

        ItemStack cookMainHandItem = HeroCookingCompat.getCookMainHandDisplay(entity);
        if (!cookMainHandItem.isEmpty() && ItemStack.isSameItemSameComponents(itemStack, cookMainHandItem)) {
            return true;
        }

        ItemStack cookOffhandItem = HeroCookingCompat.getCookOffhandDisplay(entity);
        return !cookOffhandItem.isEmpty() && ItemStack.isSameItemSameComponents(itemStack, cookOffhandItem);
    }
}
