package com.whitecloud233.modid.herobrine_companion.client.render;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.whitecloud233.modid.herobrine_companion.HerobrineCompanion;
import com.whitecloud233.modid.herobrine_companion.client.model.HeroModel;
import com.whitecloud233.modid.herobrine_companion.entity.HeroEntity;
import com.whitecloud233.modid.herobrine_companion.world.structure.ModStructures;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

public class HeroRenderer extends LivingEntityRenderer<HeroEntity, PlayerModel<HeroEntity>> {

    private static final ResourceLocation HERO_TEXTURE = new ResourceLocation(HerobrineCompanion.MODID, "textures/entity/hero.png");
    private static final ResourceLocation HEROBRINE_TEXTURE = new ResourceLocation(HerobrineCompanion.MODID, "textures/entity/herobrine.png");

    private static final Map<String, ResourceLocation> LOCAL_SKIN_CACHE = new HashMap<>();

    // 用于记录 AW 是否已经被注入到当前渲染器
    private boolean awInitialized = false;

    public HeroRenderer(EntityRendererProvider.Context context) {
        super(context, new HeroModel(context.bakeLayer(HeroModel.LAYER_LOCATION), false), 0.5f);

        this.addLayer(new HeroEyesLayer(this));
        // 传入 context.getItemInHandRenderer() 以满足原版图层的需求
        this.addLayer(new HeroHeldItemLayer(this));
        this.addLayer(new HeroHologramLayer(this));
        this.addLayer(new HeroChargedLayer(this, context.getModelSet()));

        this.addLayer(new HumanoidArmorLayer<>(
                this,
                new net.minecraft.client.model.HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER_INNER_ARMOR)),
                new net.minecraft.client.model.HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER_OUTER_ARMOR)),
                context.getModelManager()
        ));

        // 注意：这里已经删除了之前的 HeroCustomSkinLayer，因为 AW 原生接管了。
    }

    @Override
    public ResourceLocation getTextureLocation(HeroEntity entity) {
        if (entity.level().dimension() == ModStructures.END_RING_DIMENSION_KEY) {
            return HEROBRINE_TEXTURE;
        }

        int variant = entity.getSkinVariant();
        switch (variant) {
            case HeroEntity.SKIN_HEROBRINE:
                return HEROBRINE_TEXTURE;
            case HeroEntity.SKIN_HERO:
                return HERO_TEXTURE;
            case HeroEntity.SKIN_CUSTOM:
                String customPath = entity.getCustomSkinName();
                if (customPath != null && !customPath.isEmpty()) {
                    return getLocalSkin(customPath);
                }
                return DefaultPlayerSkin.getDefaultSkin();
            default:
                return HEROBRINE_TEXTURE;
        }
    }

    private ResourceLocation getLocalSkin(String path) {
        if (LOCAL_SKIN_CACHE.containsKey(path)) {
            return LOCAL_SKIN_CACHE.get(path);
        }

        File file = new File(path);
        if (file.exists() && file.isFile()) {
            try (InputStream inputStream = new FileInputStream(file)) {
                NativeImage image = NativeImage.read(inputStream);
                DynamicTexture texture = new DynamicTexture(image);
                String safeName = "custom_skin_" + Integer.toHexString(path.hashCode());
                ResourceLocation location = Minecraft.getInstance().getTextureManager().register(safeName, texture);
                LOCAL_SKIN_CACHE.put(path, location);
                return location;
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        LOCAL_SKIN_CACHE.put(path, DefaultPlayerSkin.getDefaultSkin());
        return DefaultPlayerSkin.getDefaultSkin();
    }

    @Nullable
    @Override
    protected RenderType getRenderType(HeroEntity entity, boolean bodyVisible, boolean translucent, boolean glowing) {
        return RenderType.entityCutoutNoCull(this.getTextureLocation(entity));
    }

    @Override
    public void render(HeroEntity entity, float entityYaw, float partialTicks, PoseStack poseStack, MultiBufferSource buffer, int packedLight) {

        // ==========================================
        // 【注入时刻】在创世神被渲染时，强行接通 AW 引擎！
        // ==========================================
        if (!awInitialized && com.whitecloud233.modid.herobrine_companion.compat.ArmourerWorkshop.HeroAWCompat.isLoaded()) {
            com.whitecloud233.modid.herobrine_companion.compat.ArmourerWorkshop.HeroAWCompat.attachAW(this);
            awInitialized = true; // 终身只需注入一次
        }

        float floatAmount = entity.getFloatingAmount(partialTicks);
        if (floatAmount > 0.01f) {
            float ageInTicks = entity.tickCount + partialTicks;
            float floatHeight = Mth.sin(ageInTicks * 0.1f) * 0.05f * floatAmount;
            float baseOffset = 0.05f * floatAmount;
            poseStack.translate(0.0D, floatHeight + baseOffset, 0.0D);
        }

        boolean isGlitching = entity.isGlitching();

        poseStack.pushPose();
        if (isGlitching) {
            double mainJitterX = (entity.getRandom().nextDouble() - 0.5) * 0.5;
            double mainJitterY = (entity.getRandom().nextDouble() - 0.5) * 0.5;
            double mainJitterZ = (entity.getRandom().nextDouble() - 0.5) * 0.5;
            poseStack.translate(mainJitterX, mainJitterY, mainJitterZ);
        }

        // super.render 将会触发底层的渲染，而因为我们上面注入了 AW 档案，
        // AW 潜伏在里面的 Mixin 会立刻被激活并全自动画上衣服和武器！
        super.render(entity, entityYaw, partialTicks, poseStack, buffer, packedLight);
        poseStack.popPose();

        if (isGlitching) {
            float ageInTicks = entity.tickCount + partialTicks;
            this.getModel().setupAnim(entity, 0, 0, ageInTicks, entity.getYRot(), entity.getXRot());

            poseStack.pushPose();
            poseStack.scale(-1.0F, -1.0F, 1.0F);
            poseStack.translate(0.0D, -1.501D, 0.0D);
            double rX = (entity.getRandom().nextDouble() - 0.5) * 0.8;
            double rY = (entity.getRandom().nextDouble() - 0.5) * 0.5;
            double rZ = (entity.getRandom().nextDouble() - 0.5) * 0.8;
            poseStack.translate(rX, rY, rZ);
            VertexConsumer redBuffer = buffer.getBuffer(RenderType.entityTranslucent(this.getTextureLocation(entity)));
            this.getModel().renderToBuffer(poseStack, redBuffer, packedLight, OverlayTexture.NO_OVERLAY, 1.0F, 0.0F, 0.0F, 0.5F);
            poseStack.popPose();

            poseStack.pushPose();
            poseStack.scale(-1.0F, -1.0F, 1.0F);
            poseStack.translate(0.0D, -1.501D, 0.0D);
            double bX = (entity.getRandom().nextDouble() - 0.5) * 0.8;
            double bY = (entity.getRandom().nextDouble() - 0.5) * 0.5;
            double bZ = (entity.getRandom().nextDouble() - 0.5) * 0.8;
            poseStack.translate(bX, bY, bZ);
            VertexConsumer blueBuffer = buffer.getBuffer(RenderType.entityTranslucent(this.getTextureLocation(entity)));
            this.getModel().renderToBuffer(poseStack, blueBuffer, packedLight, OverlayTexture.NO_OVERLAY, 0.0F, 1.0F, 1.0F, 0.5F);
            poseStack.popPose();
        }
    }

    @Override
    protected void renderNameTag(HeroEntity entity, Component displayName, PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        // [原版命名牌代码保持不变]
        double d0 = this.entityRenderDispatcher.distanceToSqr(entity);
        if (d0 <= 4096.0D) {
            boolean isSneaking = !entity.isDiscrete();
            float height = entity.getBbHeight() + 0.5F;
            int yOffset = "deadmau5".equals(displayName.getString()) ? -10 : 0;

            poseStack.pushPose();
            poseStack.translate(0.0D, (double)height, 0.0D);
            poseStack.mulPose(this.entityRenderDispatcher.cameraOrientation());
            poseStack.scale(-0.025F, -0.025F, 0.025F);

            Matrix4f matrix4f = poseStack.last().pose();
            float f1 = Minecraft.getInstance().options.getBackgroundOpacity(0.25F);
            int j = (int)(f1 * 255.0F) << 24;

            float xOffset = (float)(-this.getFont().width(displayName) / 2);
            Font.DisplayMode displayMode = isSneaking ? Font.DisplayMode.SEE_THROUGH : Font.DisplayMode.NORMAL;

            this.getFont().drawInBatch(displayName, xOffset, (float)yOffset, 0xFFFF0000, false, matrix4f, buffer, displayMode, j, packedLight);

            if (isSneaking) {
                this.getFont().drawInBatch(displayName, xOffset, (float)yOffset, -1, false, matrix4f, buffer, Font.DisplayMode.NORMAL, 0, packedLight);
            }
            poseStack.popPose();
        }
    }
}