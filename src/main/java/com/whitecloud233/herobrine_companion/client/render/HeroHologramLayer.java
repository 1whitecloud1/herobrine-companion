package com.whitecloud233.herobrine_companion.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.whitecloud233.herobrine_companion.HerobrineCompanion;
import com.whitecloud233.herobrine_companion.entity.HeroEntity;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

public class HeroHologramLayer extends RenderLayer<HeroEntity, PlayerModel<HeroEntity>> {
    // 贴图路径：请确保你的资源包中有这个文件
    private static final ResourceLocation HOLOGRAM_TEXTURE = ResourceLocation.fromNamespaceAndPath(HerobrineCompanion.MODID, "textures/entity/hologram_screen.png");

    public HeroHologramLayer(RenderLayerParent<HeroEntity, PlayerModel<HeroEntity>> renderer) {
        super(renderer);
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource buffer, int packedLight, HeroEntity entity, float limbSwing, float limbSwingAmount, float partialTicks, float ageInTicks, float netHeadYaw, float headPitch) {
        // 只在调试动画状态下渲染
        if (!entity.isDebugAnim()) return;

        poseStack.pushPose();

        // --- 1. 定位 ---
        // 将坐标系移动到身体中心 (模型原点在脚底)
        // [修正] Y 轴向下为正，所以向上移动需要负值
        // 移动到胸口/头部高度 (约 1.5 米)
        poseStack.translate(0.0D, 0.0D, 0.0D);

        // 向前移动 (Z 轴负方向是前方)
        // 距离身体 0.6 格
        poseStack.translate(0.0D, 0.0D, -0.6D);

        // --- 2. 旋转 ---
        // 稍微向后倾斜 15 度，方便观看
        poseStack.mulPose(Axis.XP.rotationDegrees(15.0F));
        
        // 添加轻微的悬浮呼吸效果
        float breath = Mth.sin(ageInTicks * 0.1F) * 0.02F;
        poseStack.translate(0.0D, breath, 0.0D);

        // --- 3. 缩放 ---
        // 屏幕大小
        float scale = 1.1F;
        poseStack.scale(scale, scale, scale);

        // --- 4. 渲染 ---
        renderScreen(poseStack, buffer, entity.debugAnimTick);

        poseStack.popPose();
    }

    private void renderScreen(PoseStack poseStack, MultiBufferSource buffer, int animTick) {
        // 使用 entityTranslucent 渲染半透明材质
        // 如果想要双面可见，可以关闭剔除 (Cull)，或者画两次
        VertexConsumer vertexConsumer = buffer.getBuffer(RenderType.entityTranslucent(HOLOGRAM_TEXTURE));
        
        PoseStack.Pose last = poseStack.last();
        Matrix4f pose = last.pose();
        Matrix3f normal = last.normal();

        // 计算透明度淡入淡出
        // 动画总长 100 tick
        // 前 10 tick 淡入，后 10 tick 淡出
        int alpha = 200; // 最大不透明度 (0-255)
        if (animTick > 90) {
            alpha = (int) ((100 - animTick) / 10.0F * 200);
        } else if (animTick < 10) {
            alpha = (int) (animTick / 10.0F * 200);
        }
        
        // 确保 alpha 在有效范围内
        alpha = Mth.clamp(alpha, 0, 255);

        // 始终全亮 (忽略环境光)
        int light = 0xF000F0; 

        // 绘制矩形平面 (Quad)
        // 宽度 1.0 (-0.5 到 0.5), 高度 0.6 (-0.3 到 0.3)
        float w = 0.5F;
        float h = 0.3F;

        // [修正] 顶点坐标与 UV 的对应关系
        // Y 轴向下：-h 是上，h 是下
        // V 轴向下：0 是上，1 是下
        // U 轴：保持之前的翻转 (1->0) 以解决镜像问题
        
        // 顶点顺序：逆时针 (TL -> BL -> BR -> TR)
        
        // 左上 (Top-Left): x=-w, y=-h (上) -> u=1, v=0 (上)
        vertex(vertexConsumer, last, -w, -h, 0, 1, 0, light, alpha);
        
        // 左下 (Bottom-Left): x=-w, y=h (下) -> u=1, v=1 (下)
        vertex(vertexConsumer, last, -w, h, 0, 1, 1, light, alpha);
        
        // 右下 (Bottom-Right): x=w, y=h (下) -> u=0, v=1 (下)
        vertex(vertexConsumer, last, w, h, 0, 0, 1, light, alpha);
        
        // 右上 (Top-Right): x=w, y=-h (上) -> u=0, v=0 (上)
        vertex(vertexConsumer, last, w, -h, 0, 0, 0, light, alpha);
        
        // 背面 (同样逻辑)
        vertex(vertexConsumer, last, w, -h, 0, 0, 0, light, alpha);
        vertex(vertexConsumer, last, w, h, 0, 0, 1, light, alpha);
        vertex(vertexConsumer, last, -w, h, 0, 1, 1, light, alpha);
        vertex(vertexConsumer, last, -w, -h, 0, 1, 0, light, alpha);
    }

    private void vertex(VertexConsumer consumer, PoseStack.Pose pose, float x, float y, float z, float u, float v, int light, int alpha) {
        // 使用 addVertex 方法，并传入 Matrix4f
        consumer.addVertex(pose.pose(), x, y, z)
                .setColor(255, 255, 255, alpha) // 白色叠加，控制透明度
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light)
                .setNormal(pose, 0.0F, 0.0F, 1.0F);
    }
}
