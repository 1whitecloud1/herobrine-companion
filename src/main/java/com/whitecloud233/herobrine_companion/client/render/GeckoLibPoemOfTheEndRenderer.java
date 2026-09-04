package com.whitecloud233.herobrine_companion.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.whitecloud233.herobrine_companion.client.model.PoemOfTheEndGeoModel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.renderer.GeoObjectRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

/**
 * 终末之诗的 GeckoLib 3D 物品渲染器（仅在玩家安装 GeckoLib 时被创建）。
 *
 * <p>不能直接用原厂 {@code GeoItemRenderer<T>}：它的泛型上界是
 * {@code T extends Item & GeoAnimatable}，而物品类已刻意不再实现 GeoItem
 * （见 {@link PoemOfTheEndAnimatable}）。因此改用 {@link GeoObjectRenderer}
 * 驱动独立的动画单例，并在这里复刻 {@code GeoItemRenderer.renderByItem}
 * 的缓冲区处理——GUI 走 BufferSource 并手动 {@code endBatch()}，
 * 其余上下文必须把传入的 bufferSource 原样传下去由调用方 flush。</p>
 *
 * <p>显示变换<b>不</b>在这里手写：全部由 {@code models/item/poem_of_the_end.json}
 * 的 {@code display} 块提供，vanilla 的 {@code BakedModel.applyTransform} 会在
 * 调用本方法前施加。手写 {@code ItemTransform} 必须自行 ×0.0625，1.20.1 分支
 * 曾因漏掉这步导致模型被平移出视锥体、完全不可见。</p>
 *
 * <p>自发光沿用基岩版约定（贴图 alpha 不满的像素发光），由构造器注册的
 * {@link AutoGlowingGeoLayer} 实现；基础层用真实世界光照，只有发光像素恒亮。</p>
 */
public final class GeckoLibPoemOfTheEndRenderer extends BlockEntityWithoutLevelRenderer {

    private static final PoemOfTheEndAnimatable ANIMATABLE = new PoemOfTheEndAnimatable();

    private final GeoObjectRenderer<PoemOfTheEndAnimatable> renderer =
            new NonReTranslatingGeoObjectRenderer(new PoemOfTheEndGeoModel());

    /**
     * {@link GeoObjectRenderer#preRender} 末尾无条件执行
     * {@code poseStack.translate(0.5F, 0.51F, 0.5F)}（为把对象摆到方块中心而设计）。
     * 而 {@code GeoRenderer.reRender} 会以 {@code isReRender=true} <b>再次</b>调用
     * {@code preRender}，于是自发光层被平移了两遍 → 发光部位整体偏移
     * (+0.5, +0.51, +0.5)，看起来「向右上方分离」。
     *
     * <p>这里在重绘时把那次多余的平移抵消掉。基础层保持原样，
     * 以免动到已经调好的 display 变换。</p>
     */
    private static final class NonReTranslatingGeoObjectRenderer extends GeoObjectRenderer<PoemOfTheEndAnimatable> {

        private NonReTranslatingGeoObjectRenderer(PoemOfTheEndGeoModel model) {
            super(model);
        }

        @Override
        public void preRender(PoseStack poseStack, PoemOfTheEndAnimatable animatable, BakedGeoModel model,
                              MultiBufferSource bufferSource, VertexConsumer buffer, boolean isReRender,
                              float partialTick, int packedLight, int packedOverlay, int colour) {
            super.preRender(poseStack, animatable, model, bufferSource, buffer, isReRender,
                    partialTick, packedLight, packedOverlay, colour);
            if (isReRender) {
                poseStack.translate(-0.5F, -0.51F, -0.5F);
            }
        }
    }

    private GeckoLibPoemOfTheEndRenderer() {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(),
                Minecraft.getInstance().getEntityModels());
        // 自发光层：沿用基岩版的约定 —— 贴图 alpha 不满(0<a<255)的像素为自发光。
        // poem_of_the_end_geo.png 里 α=0 是空白、α=255 是普通像素，
        // 另有 231 个半透明像素（青色描边/宝石）才是要发光的部分，
        // 已在同名 .mcmeta 的 "glowsections" 里列成 59 个矩形。
        // AutoGlowingGeoLayer 把这些像素抠成独立的 _glowmask 贴图（并从基础贴图里
        // 清零，避免两层叠加变亮两倍），再用不采样 lightmap 的 geo_glowing_layer
        // 以 FULL_SKY 亮度重绘一遍。
        renderer.addRenderLayer(new AutoGlowingGeoLayer<>(renderer));
    }

    public static BlockEntityWithoutLevelRenderer create() {
        return new GeckoLibPoemOfTheEndRenderer();
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext context, PoseStack poseStack,
                             MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        // 物品栏图标沿用 2D 贴图（poem_of_the_end.png），与未装 GeckoLib 时完全一致；
        // 3D 模型只用于手持/地面/头顶/展示框等场景。
        if (context == ItemDisplayContext.GUI) {
            PoemOfTheEndFlatRenderer.render(stack, context, poseStack, bufferSource, packedLight, packedOverlay);
            return;
        }

        float partialTick = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(true);
        ResourceLocation texture = renderer.getTextureLocation(ANIMATABLE);
        // 基础层用 entityTranslucent（与 Celestisynth 一致）；自发光部分由构造器里
        // 注册的 AutoGlowingGeoLayer 单独重绘，不能整体用 entityTranslucentEmissive
        // ——那会让整把镰刀发光，且把半透明像素画成半透明而非实心。
        RenderType renderType = RenderType.entityTranslucent(texture);

        // 手持/地面/第三人称等：必须把传入的 bufferSource 原样传下去，由调用方
        // （实体/世界渲染器）负责 flush。回退到全局 bufferSource 会让顶点写进
        // 永远不被 flush 的缓冲区 → 物品不可见。
        VertexConsumer buffer =
                ItemRenderer.getFoilBufferDirect(bufferSource, renderType, false, stack.hasFoil());
        renderer.render(poseStack, ANIMATABLE, bufferSource, renderType, buffer, packedLight, partialTick);
    }
}