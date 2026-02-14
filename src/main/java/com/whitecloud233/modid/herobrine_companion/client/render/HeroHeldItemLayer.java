package com.whitecloud233.modid.herobrine_companion.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.whitecloud233.modid.herobrine_companion.HerobrineCompanion;
import com.whitecloud233.modid.herobrine_companion.client.model.HeroModel;
import com.whitecloud233.modid.herobrine_companion.entity.HeroEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

public class HeroHeldItemLayer extends RenderLayer<HeroEntity, PlayerModel<HeroEntity>> {
    private final ItemInHandRenderer itemInHandRenderer;

    public HeroHeldItemLayer(RenderLayerParent<HeroEntity, PlayerModel<HeroEntity>> renderer, ItemInHandRenderer itemInHandRenderer) {
        super(renderer);
        this.itemInHandRenderer = itemInHandRenderer;
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource buffer, int packedLight, HeroEntity entity, float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
        // 只有在播放抚摸镰刀动画时才进行自定义渲染
        if (!entity.isInspectingScythe()) {
            return;
        }

        // [修复] 从 HerobrineCompanion 类中获取物品
        ItemStack scytheStack = new ItemStack(HerobrineCompanion.POEM_OF_THE_END.get());
        if (scytheStack.isEmpty()) return;


        poseStack.pushPose();

        // 1. 获取右手骨骼的位置和旋转
        // 这会将 PoseStack 移动到右手的坐标系中
        ((HeroModel)this.getParentModel()).rightArm.translateAndRotate(poseStack);

        // 2. [修复] 调整镰刀在手中的位置和角度

        poseStack.translate(0.1F, 0.5F, 0.1F);

        // 旋转：让镰刀横在胸前
        // 关键：先绕 Z 轴旋转90度，把物品从“竖着”变成“横着”
        poseStack.mulPose(Axis.ZP.rotationDegrees(90.0F));
        // 然后绕 X 轴旋转，调整刀刃的朝向
        poseStack.mulPose(Axis.XP.rotationDegrees(180.0F));
        // 最后绕 Y 轴旋转，让镰刀的头部朝前
        poseStack.mulPose(Axis.YP.rotationDegrees(30.0F));


        // 3. 渲染物品
        this.itemInHandRenderer.renderItem(
                entity,
                scytheStack,
                ItemDisplayContext.THIRD_PERSON_RIGHT_HAND,
                false,
                poseStack,
                buffer,
                packedLight
        );

        poseStack.popPose();
    }
}

