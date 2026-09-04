package com.whitecloud233.modid.herobrine_companion.compat.epicfight;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import yesman.epicfight.api.animation.AnimationManager;
import yesman.epicfight.api.animation.AnimationManager.AnimationAccessor;
import yesman.epicfight.api.animation.Joint;
import yesman.epicfight.api.animation.types.StaticAnimation;
import yesman.epicfight.api.asset.AssetAccessor;
import yesman.epicfight.api.client.animation.property.ClientAnimationProperties;
import yesman.epicfight.api.client.animation.property.TrailInfo;
import yesman.epicfight.client.ClientEngine;
import yesman.epicfight.client.particle.AnimationTrailParticle;
import yesman.epicfight.client.renderer.patched.item.RenderItemBase;
import yesman.epicfight.world.capabilities.EpicFightCapabilities;
import yesman.epicfight.world.capabilities.entitypatch.LivingEntityPatch;
import yesman.epicfight.world.capabilities.item.CapabilityItem;

import java.util.List;
import java.util.Optional;

/**
 * 终末之诗刀光拖尾粒子（1.20.1 Forge 移植版）。
 *
 * <p>完全复用 EpicFight 公开 API 的挥砍轨迹算法（贝塞尔弧光、工具关节绑定、
 * item skin trail 覆盖），只把渲染类型换成自持的加法混合发光通道
 * {@link GlowTrailRenderTypes}，并在 {@link #render} 里做 3 层"伪 bloom"：
 * 核心层原样绘制，另画两层按刀身截面绕中点放大 1.9x/3.0x 的柔光层，
 * 加法混合下大一圈的柔光叠加进画面，形成类似 bloom 管线的泛光。</p>
 */
public class GlowTrailParticle extends AnimationTrailParticle {
    /** 伪 bloom 光晕层：(截面放大倍数, 该层 alpha 系数)。核心 + 两层外围柔光。 */
    private static final float[][] BLOOM_PASSES = {
            {1.0F, 1.00F},
            {1.9F, 0.34F},
            {3.0F, 0.11F},
    };

    protected GlowTrailParticle(
            ClientLevel level,
            LivingEntityPatch<?> owner,
            Joint joint,
            AssetAccessor<? extends StaticAnimation> animation,
            TrailInfo trailInfo
    ) {
        super(level, owner, joint, animation, trailInfo);
    }

    @Override
    public ParticleRenderType getRenderType() {
        return GlowTrailRenderTypes.get(this.trailInfo.texturePath());
    }

    @Override
    public void render(VertexConsumer vertexConsumer, Camera camera, float partialTick) {
        if (this.trailEdges.isEmpty()) {
            return;
        }

        PoseStack poseStack = new PoseStack();
        int light = this.getLightColor(partialTick);
        this.setupPoseStack(poseStack, camera, partialTick);
        Matrix4f matrix4f = poseStack.last().pose();
        int edges = this.trailEdges.size() - 1;
        boolean startFade = this.trailEdges.get(0).lifetime == 1;
        boolean endFade = this.trailEdges.get(edges).lifetime == this.trailInfo.trailLifetime();
        float startEdge = (startFade ? this.trailInfo.interpolateCount() * 2 * partialTick : 0.0F) + this.startEdgeCorrection;
        float endEdge = endFade ? Math.min(edges - (this.trailInfo.interpolateCount() * 2) * (1.0F - partialTick), edges - 1) : edges - 1;
        float interval = 1.0F / (endEdge - startEdge);
        float fading = 1.0F;

        if (this.shouldRemove) {
            if (TrailInfo.isValidTime(this.trailInfo.fadeTime())) {
                fading = ((float) (this.lifetime - this.age) / (float) this.trailInfo.trailLifetime());
            } else {
                fading = Mth.clamp(((this.lifetime - this.age) + (1.0F - partialTick)) / this.trailInfo.trailLifetime(), 0.0F, 1.0F);
            }
        }

        float partialStartEdge = interval * (startEdge % 1.0F);
        float from = -partialStartEdge;
        float to = -partialStartEdge + interval;

        for (float[] pass : BLOOM_PASSES) {
            this.drawRibbonPass(vertexConsumer, matrix4f, startEdge, endEdge, interval, from, to, fading, light, pass[0], pass[1]);
        }
    }

    /**
     * 绘制一层光带。{@code scale} 为 1.0 时即原始形状；大于 1.0 时每个刀身截面
     * 绕自身中点放大，产生比核心更宽、更柔的光晕层（配合加法混合 = 泛光）。
     */
    private void drawRibbonPass(
            VertexConsumer vertexConsumer, Matrix4f matrix4f,
            float startEdge, float endEdge, float interval, float startFrom, float startTo,
            float fading, int light, float scale, float alphaMul
    ) {
        float from = startFrom;
        float to = startTo;
        for (int i = (int) startEdge; i < (int) endEdge + 1; i++) {
            // u 沿弧逐段推进，alphaFrom/alphaTo 必须在循环内逐段计算（与父类一致）
            float alphaFrom = Mth.clamp(from, 0.0F, 1.0F);
            float alphaTo = Mth.clamp(to, 0.0F, 1.0F);
            float a1 = this.alpha * alphaFrom * fading * alphaMul;
            float a2 = this.alpha * alphaTo * fading * alphaMul;
            TrailEdge e1 = this.trailEdges.get(i);
            TrailEdge e2 = this.trailEdges.get(i + 1);
            float s1x = (float) e1.start.x, s1y = (float) e1.start.y, s1z = (float) e1.start.z;
            float e1x = (float) e1.end.x, e1y = (float) e1.end.y, e1z = (float) e1.end.z;
            float s2x = (float) e2.start.x, s2y = (float) e2.start.y, s2z = (float) e2.start.z;
            float e2x = (float) e2.end.x, e2y = (float) e2.end.y, e2z = (float) e2.end.z;
            if (scale != 1.0F) {
                // 截面 (start<->end) 绕自身中点放大
                float m1x = (s1x + e1x) * 0.5F, m1y = (s1y + e1y) * 0.5F, m1z = (s1z + e1z) * 0.5F;
                s1x = m1x + (s1x - m1x) * scale;
                s1y = m1y + (s1y - m1y) * scale;
                s1z = m1z + (s1z - m1z) * scale;
                e1x = m1x + (e1x - m1x) * scale;
                e1y = m1y + (e1y - m1y) * scale;
                e1z = m1z + (e1z - m1z) * scale;
                float m2x = (s2x + e2x) * 0.5F, m2y = (s2y + e2y) * 0.5F, m2z = (s2z + e2z) * 0.5F;
                s2x = m2x + (s2x - m2x) * scale;
                s2y = m2y + (s2y - m2y) * scale;
                s2z = m2z + (s2z - m2z) * scale;
                e2x = m2x + (e2x - m2x) * scale;
                e2y = m2y + (e2y - m2y) * scale;
                e2z = m2z + (e2z - m2z) * scale;
            }
            Vector4f pos1 = new Vector4f(s1x, s1y, s1z, 1.0F);
            Vector4f pos2 = new Vector4f(e1x, e1y, e1z, 1.0F);
            Vector4f pos3 = new Vector4f(e2x, e2y, e2z, 1.0F);
            Vector4f pos4 = new Vector4f(s2x, s2y, s2z, 1.0F);
            pos1.mul(matrix4f);
            pos2.mul(matrix4f);
            pos3.mul(matrix4f);
            pos4.mul(matrix4f);
            vertexConsumer.vertex(pos1.x(), pos1.y(), pos1.z()).uv(from, 1.0F)
                    .color(this.rCol, this.gCol, this.bCol, a1).uv2(light).endVertex();
            vertexConsumer.vertex(pos2.x(), pos2.y(), pos2.z()).uv(from, 0.0F)
                    .color(this.rCol, this.gCol, this.bCol, a1).uv2(light).endVertex();
            vertexConsumer.vertex(pos3.x(), pos3.y(), pos3.z()).uv(to, 0.0F)
                    .color(this.rCol, this.gCol, this.bCol, a2).uv2(light).endVertex();
            vertexConsumer.vertex(pos4.x(), pos4.y(), pos4.z()).uv(to, 1.0F)
                    .color(this.rCol, this.gCol, this.bCol, a2).uv2(light).endVertex();
            from += interval;
            to += interval;
        }
    }

    /** 注册到 {@code herobrine_companion:glow_trail} 粒子的生成器（构造期传入 SpriteSet 即可，轨迹粒子不用贴图集）。 */
    public static class Provider implements ParticleProvider<SimpleParticleType> {
        public Provider(SpriteSet spriteSet) {
        }

        @Override
        public Particle createParticle(
                SimpleParticleType typeIn, ClientLevel level, double x, double y, double z,
                double xSpeed, double ySpeed, double zSpeed
        ) {
            int eid = (int) Double.doubleToRawLongBits(x);
            int animid = (int) Double.doubleToRawLongBits(z);
            int jointId = (int) Double.doubleToRawLongBits(xSpeed);
            int idx = (int) Double.doubleToRawLongBits(ySpeed);
            Entity entity = level.getEntity(eid);
            if (entity == null) {
                return null;
            }
            LivingEntityPatch<?> entitypatch = EpicFightCapabilities.getEntityPatch(entity, LivingEntityPatch.class);
            if (entitypatch == null) {
                return null;
            }
            AnimationAccessor<? extends StaticAnimation> animation = AnimationManager.byId(animid);
            if (animation == null) {
                return null;
            }
            Optional<List<TrailInfo>> trailInfo = animation.get().getProperty(ClientAnimationProperties.TRAIL_EFFECT);
            if (trailInfo.isEmpty()) {
                return null;
            }
            TrailInfo result = trailInfo.get().get(idx);
            if (result.hand() != null) {
                ItemStack stack = entitypatch.getOriginal().getItemInHand(result.hand());
                RenderItemBase renderItemBase = ClientEngine.getInstance().renderEngine.getItemRenderer(stack);
                if (renderItemBase != null && renderItemBase.trailInfo() != null) {
                    result = renderItemBase.trailInfo().overwrite(result);
                }
            }
            result = entitypatch.getEntityDecorations()
                    .getModifiedTrailInfo(result, result.hand() == null ? CapabilityItem.EMPTY : entitypatch.getAdvancedHoldingItemCapability(result.hand()));
            if (!result.playable()) {
                return null;
            }
            Joint trailJoint = entitypatch.getArmature().searchJointById(jointId);
            if (trailJoint == null) {
                return null;
            }
            return new GlowTrailParticle(level, entitypatch, trailJoint, animation, result);
        }
    }
}
