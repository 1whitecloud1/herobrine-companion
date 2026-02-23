package com.whitecloud233.modid.herobrine_companion.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.whitecloud233.modid.herobrine_companion.client.model.HeroModel;
import com.whitecloud233.modid.herobrine_companion.entity.HeroEntity;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

// [修复] 将泛型改为 PlayerModel<HeroEntity> 以匹配 HeroRenderer
public class HeroChargedLayer extends RenderLayer<HeroEntity, PlayerModel<HeroEntity>> {
    // 使用原版高压苦力怕的电弧贴图
    private static final ResourceLocation POWER_LOCATION = new ResourceLocation("textures/entity/creeper/creeper_armor.png");

    private final HeroModel model;

    // [修复] 构造函数参数也需要改为 PlayerModel
    public HeroChargedLayer(RenderLayerParent<HeroEntity, PlayerModel<HeroEntity>> renderer, EntityModelSet modelSet) {
        super(renderer);
        // 我们需要一个单独的模型实例来渲染这一层，以便控制它的大小
        this.model = new HeroModel(modelSet.bakeLayer(HeroModel.LAYER_LOCATION), false);
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource buffer, int packedLight, HeroEntity entity, float limbSwing, float limbSwingAmount, float partialTicks, float ageInTicks, float netHeadYaw, float headPitch) {
        // [修改] 改为检查 shockTicks，只有在雷电降下后才显示电弧
        if (entity.shockTicks <= 0) return;

        // 2. 计算透明度 (Alpha) 实现渐隐
        // shockTicks 初始为 60 (3秒)
        float progress = (float)entity.shockTicks / HeroEntity.MAX_SHOCK_TICKS;
        float alpha = 0.3F;

        // 刚开始 (progress 接近 1.0) 保持全亮
        // 最后 1/3 时间 (progress < 0.3) 渐隐
        if (progress < 0.2F) {
            alpha = progress / 0.3F;
        }

        alpha = Mth.clamp(alpha, 0.0F, 1.0F);

        // 如果快消失了，就不画了
        if (alpha < 0.01F) return;

        poseStack.pushPose();

        // 3. 模型膨胀 (关键!)
        // 让电弧层比身体大一圈。对于类人生物，1.05F 到 1.1F 比较合适
        float scale = 1.5F;
        poseStack.scale(scale, scale, scale);
        //稍微修正一下位置，保证脚底对齐
        poseStack.translate(0.0D, -0.12D, 0.0D);

        // 4. 同步模型动作
        // 必须把主模型的动作复制给这个膨胀层模型
        // [修复] getParentModel() 返回的是 PlayerModel，我们需要将其属性复制到 HeroModel
        this.getParentModel().copyPropertiesTo(this.model);
        this.model.prepareMobModel(entity, limbSwing, limbSwingAmount, partialTicks);
        this.model.setupAnim(entity, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);

        // 5. 准备流动的渲染类型 (核心!!)
        // 计算流动时间
        float time = (float)entity.tickCount + partialTicks;
        // RenderType.energySwirl(贴图, X轴滚动速度, Y轴滚动速度)
        // 0.01F 是滚动速度，数值越大流动越快
        VertexConsumer vertexConsumer = buffer.getBuffer(RenderType.energySwirl(POWER_LOCATION, time * 0.5F, time * 0.5F));

        // 6. 设置电弧颜色 (RGBA)
        // 原图是白色的，我们在这里把它染成青蓝色
        float red = 1.0F;
        float green = 1.0F;
        float blue = 1.0F;

        // 7. 绘制
        // 使用 15728880 (0xF000F0) 强制全亮，让电弧在黑暗中发光
        this.model.renderToBuffer(
                poseStack,
                vertexConsumer,
                15728880,
                OverlayTexture.NO_OVERLAY,
                red,
                green,
                blue,
                alpha
        );

        poseStack.popPose();
    }
}