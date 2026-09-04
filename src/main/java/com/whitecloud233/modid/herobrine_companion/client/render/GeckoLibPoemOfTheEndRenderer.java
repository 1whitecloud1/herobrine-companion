package com.whitecloud233.modid.herobrine_companion.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.ItemTransform;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.joml.Vector3f;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.renderer.GeoObjectRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

/**
 * 终末之诗的 GeckoLib 物品渲染器（仅在玩家安装 GeckoLib 时被创建）。
 *
 * <p>物品模型 JSON 是 {@code builtin/entity}，Forge 会先施加显示变换再调用
 * {@link #renderByItem}。为避免与回退渲染器的显示变换冲突，模型 JSON 不写
 * display，这里在 {@code renderByItem} 内手动施加 liandao 的显示变换
 * （与 vanilla 的组合方式一致：display ∘ translate(-0.5)）。</p>
 *
 * <p>自发光沿用基岩版约定（贴图 alpha 不满的像素发光），由构造器注册的
 * {@link AutoGlowingGeoLayer} 实现。</p>
 */
public final class GeckoLibPoemOfTheEndRenderer extends BlockEntityWithoutLevelRenderer {

    private static final PoemOfTheEndAnimatable ANIMATABLE = new PoemOfTheEndAnimatable();

    // === liandao.json 的 display 变换 ===
    // translation 一律填 JSON 原值（1/16 格的像素单位），由 transform() 负责换算：
    // ItemTransform 的构造器不做任何单位处理，而 vanilla 读 JSON 时会
    // ×0.0625 再 clamp 到 ±5 格（ItemTransform$Deserializer.deserialize）。
    // 直接把像素值传构造器会让平移量放大 16 倍，模型被甩出视锥体 → 完全不可见。
    private static final ItemTransform IDENTITY = new ItemTransform(new Vector3f(), new Vector3f(), new Vector3f(1, 1, 1));
    private static final ItemTransform THIRD_PERSON_RIGHT =
            transform(new Vector3f(), new Vector3f(-6.5F, -32.25F, 20.5F), new Vector3f(1, 1, 1));
    private static final ItemTransform THIRD_PERSON_LEFT =
            transform(new Vector3f(), new Vector3f(6.25F, -32.25F, 21.25F), new Vector3f(1, 1, 1));
    private static final ItemTransform FIRST_PERSON_RIGHT =
            transform(new Vector3f(-15F, -3F, -10F), new Vector3f(-9F, -21F, 23F), new Vector3f(0.53F, 0.58F, 0.53F));
    private static final ItemTransform GROUND =
            transform(new Vector3f(), new Vector3f(-3.5F, -10F, 6.5F), new Vector3f(0.38F, 0.38F, 0.39F));
    private static final ItemTransform GUI =
            transform(new Vector3f(-96.66F, -49.25F, -88.64F), new Vector3f(-14.5F, -6.75F, 0F), new Vector3f(0.3F, 0.3F, 0.3F));
    private static final ItemTransform HEAD =
            transform(new Vector3f(), new Vector3f(-7.75F, -33.75F, 0F), new Vector3f(1, 1, 1));
    private static final ItemTransform FIXED =
            transform(new Vector3f(-93.19F, -54.64F, -93.21F), new Vector3f(-16.5F, -9.25F, -3.25F), new Vector3f(0.38F, 0.37F, 0.36F));

    /**
     * 按 vanilla 反序列化器的规则构造 display 变换。
     *
     * @param translationIn16ths 与模型 JSON 中 {@code display.translation} 同单位（1/16 格）
     */
    private static ItemTransform transform(Vector3f rotation, Vector3f translationIn16ths, Vector3f scale) {
        Vector3f t = new Vector3f(translationIn16ths).mul(0.0625F);
        t.set(Mth.clamp(t.x, -5F, 5F), Mth.clamp(t.y, -5F, 5F), Mth.clamp(t.z, -5F, 5F));
        return new ItemTransform(rotation, t, scale);
    }

    private final GeoObjectRenderer<PoemOfTheEndAnimatable> renderer = new NonReTranslatingGeoObjectRenderer(new PoemOfTheEndGeoModel());

    /** 物品栏图标用的 2D 渲染器（与未装 GeckoLib 时的回退渲染器是同一个类）。 */
    private final PoemOfTheEndFallbackRenderer flatRenderer = new PoemOfTheEndFallbackRenderer();

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
                              float partialTick, int packedLight, int packedOverlay,
                              float red, float green, float blue, float alpha) {
            super.preRender(poseStack, animatable, model, bufferSource, buffer, isReRender,
                    partialTick, packedLight, packedOverlay, red, green, blue, alpha);
            if (isReRender) {
                poseStack.translate(-0.5F, -0.51F, -0.5F);
            }
        }
    }

    private GeckoLibPoemOfTheEndRenderer() {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());
        // 自发光层：沿用基岩版的约定 —— 贴图 alpha 不满(0<a<255)的像素为自发光。
        // 贴图 poem_of_the_end_geo.png 里 α=0 是空白、α=255 是普通像素，
        // 另有 231 个半透明像素（青色描边/宝石）才是要发光的部分，
        // 已在同名 .mcmeta 的 "glowsections" 里列成 59 个矩形。
        // AutoGlowingGeoLayer 会把这些像素抠成独立的 _glowmask 贴图（并从基础贴图里
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
        // 交给同一个 2D 渲染器，保证两种模式下的图标像素级一致。
        if (context == ItemDisplayContext.GUI) {
            flatRenderer.renderByItem(stack, context, poseStack, bufferSource, packedLight, packedOverlay);
            return;
        }

        poseStack.pushPose();
        // 入口矩阵 = translate(-0.5)。先抵消它，施加 display 后再补回，
        // 使最终组合与 vanilla 完全一致：display ∘ translate(-0.5)。
        poseStack.translate(0.5F, 0.5F, 0.5F);
        transformFor(context).apply(false, poseStack);
        poseStack.translate(-0.5F, -0.5F, -0.5F);

        ResourceLocation texture = renderer.getTextureLocation(ANIMATABLE);
        // 基础层用 entityTranslucent（与 Celestisynth 一致）。自发光部分由构造器里
        // 注册的 AutoGlowingGeoLayer 单独重绘，见该处注释。
        RenderType renderType = RenderType.entityTranslucent(texture);

        // 必须把传入的 bufferSource 原样传下去，由调用方（实体/世界渲染器）负责 flush。
        // 绝不能回退到全局 bufferSource，否则顶点会写进永远不会被 flush 的缓冲区 → 物品不可见。
        VertexConsumer vertexConsumer = ItemRenderer.getFoilBufferDirect(bufferSource, renderType, false, stack.hasFoil());
        renderer.render(poseStack, ANIMATABLE, bufferSource, renderType, vertexConsumer, packedLight);
        poseStack.popPose();
    }

    private static ItemTransform transformFor(ItemDisplayContext context) {
        return switch (context) {
            case THIRD_PERSON_RIGHT_HAND -> THIRD_PERSON_RIGHT;
            case THIRD_PERSON_LEFT_HAND -> THIRD_PERSON_LEFT;
            case FIRST_PERSON_RIGHT_HAND, FIRST_PERSON_LEFT_HAND -> FIRST_PERSON_RIGHT;
            case GROUND -> GROUND;
            case GUI -> GUI;
            case HEAD -> HEAD;
            case FIXED -> FIXED;
            case NONE -> IDENTITY;
        };
    }

}
