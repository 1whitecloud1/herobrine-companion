package com.whitecloud233.modid.herobrine_companion.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.logging.LogUtils;
import com.whitecloud233.modid.herobrine_companion.HerobrineCompanion;
import com.whitecloud233.modid.herobrine_companion.client.model.HeroDragonModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EnderDragonRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import org.slf4j.Logger;

public class HeroDragonRenderer extends EnderDragonRenderer {

    private static final Logger LOGGER = LogUtils.getLogger();
    
    // 使用自定义纹理路径
    private static final ResourceLocation DRAGON_TEXTURE = ResourceLocation.fromNamespaceAndPath(HerobrineCompanion.MODID, "textures/entity/hero_dragon.png");

    private final HeroDragonModel customModel;

    public HeroDragonRenderer(EntityRendererProvider.Context context) {
        super(context);
        LOGGER.error(">>> [HERO RENDERER] 构造函数被调用！正在初始化自定义模型... <<<");
        
        HeroDragonModel model = null;
        try {
            // 使用我们自定义的 Layer
            model = new HeroDragonModel(context.bakeLayer(HeroDragonModel.LAYER_LOCATION));
            LOGGER.error(">>> [HERO RENDERER] 自定义模型初始化完成！ <<<");
        } catch (Exception e) {
            LOGGER.error(">>> [HERO RENDERER] 模型初始化失败！", e);
        }
        this.customModel = model;
    }

    private int debugTimer = 0;

    @Override
    public void render(EnderDragon entity, float entityYaw, float partialTicks, PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        if (entity == null || this.customModel == null) {
            return;
        }

        if (debugTimer++ % 60 == 0) {
            if (entity.getPhaseManager() != null && entity.getPhaseManager().getCurrentPhase() != null) {
                // LOGGER.error(">>> [HERO RENDERER] 正在渲染末影龙！状态: " + entity.getPhaseManager().getCurrentPhase().getPhase());
            }
        }

        poseStack.pushPose();
        
        float f = (float)entity.getLatencyPos(7, partialTicks)[0];
        float f1 = (float)(entity.getLatencyPos(5, partialTicks)[1] - entity.getLatencyPos(10, partialTicks)[1]);
        poseStack.mulPose(com.mojang.math.Axis.YP.rotationDegrees(-f));
        poseStack.mulPose(com.mojang.math.Axis.XP.rotationDegrees(f1 * 10.0F));
        poseStack.translate(0.0F, 0.0F, 1.0F);
        poseStack.scale(-1.0F, -1.0F, 1.0F);
        
        // 【修正】大幅抬高模型
        // 之前 -2.0F 还是低，尝试 -8.0F
        poseStack.translate(0.0F, -4.0F, 0.0F);
        
        boolean isHurt = entity.hurtTime > 0;
        
        this.customModel.prepareMobModel(entity, 0.0F, 0.0F, partialTicks);
        this.customModel.setupAnim(entity, 0.0F, 0.0F, entity.tickCount + partialTicks, 0.0F, 0.0F);
        
        VertexConsumer vertexconsumer = buffer.getBuffer(RenderType.entityCutoutNoCull(DRAGON_TEXTURE));
        this.customModel.renderToBuffer(poseStack, vertexconsumer, packedLight, OverlayTexture.NO_OVERLAY, 1.0F, 1.0F, 1.0F, 1.0F);
        
        poseStack.popPose();
    }
    
    @Override
    public ResourceLocation getTextureLocation(EnderDragon entity) {
        return DRAGON_TEXTURE;
    }
}