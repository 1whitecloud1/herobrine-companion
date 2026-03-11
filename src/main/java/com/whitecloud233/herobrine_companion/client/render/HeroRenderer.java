package com.whitecloud233.herobrine_companion.client.render;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.whitecloud233.herobrine_companion.HerobrineCompanion;
import com.whitecloud233.herobrine_companion.client.model.HeroModel;
import com.whitecloud233.herobrine_companion.entity.HeroEntity;
import com.whitecloud233.herobrine_companion.world.structure.ModStructures;
import com.whitecloud233.herobrine_companion.compat.ArmourerWorkshop.HeroAWCompat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

public class HeroRenderer extends LivingEntityRenderer<HeroEntity, PlayerModel<HeroEntity>> {

    private static final ResourceLocation HERO_TEXTURE = ResourceLocation.fromNamespaceAndPath(HerobrineCompanion.MODID, "textures/entity/hero.png");
    private static final ResourceLocation HEROBRINE_TEXTURE = ResourceLocation.fromNamespaceAndPath(HerobrineCompanion.MODID, "textures/entity/herobrine.png");

    private static final Map<String, ResourceLocation> LOCAL_SKIN_CACHE = new HashMap<>();

    // 用于骗过 AW 的纯原版玩家模型
    // 用于骗过 AW 的纯原版玩家模型
    private final PlayerModel<HeroEntity> dummyVanillaModel;

    // 【补充这一行】新增伪装开关变量
    public boolean spoofModelForAW = false;

    public HeroRenderer(EntityRendererProvider.Context context) {
        super(context, new HeroModel(context.bakeLayer(HeroModel.LAYER_LOCATION), false), 0.5f);
        this.dummyVanillaModel = new PlayerModel<>(context.bakeLayer(ModelLayers.PLAYER), false);

        this.addLayer(new HeroEyesLayer(this));
        // 注意这里已经回归正常
        this.addLayer(new HeroHeldItemLayer(this, context.getItemInHandRenderer()));
        this.addLayer(new HeroHologramLayer(this));
        this.addLayer(new HeroChargedLayer(this, context.getModelSet()));

        this.addLayer(new HumanoidArmorLayer<>(
                this,
                new net.minecraft.client.model.HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER_INNER_ARMOR)),
                new net.minecraft.client.model.HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER_OUTER_ARMOR)),
                context.getModelManager()
        ));
    }

    // =========================================================
    // [光影兼容版] 深度调用栈监听：穿透 Iris 的包装层
    // =========================================================
    // =========================================================
    // [光影兼容版] 混合型雷达：主动伪装 + 深度调用栈监听
    // =========================================================
    @Override
    public PlayerModel<HeroEntity> getModel() {
        // 1. 主动伪装：专门解决武器渲染（由 HeroHeldItemLayer 触发）
        if (this.spoofModelForAW) {
            this.syncDummyModel();
            return this.dummyVanillaModel;
        }

        // 2. 被动雷达：专门解决护甲渲染（穿透 Iris 的包装层）
        StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
        for (int i = 2; i < Math.min(32, stackTrace.length); i++) {
            if (stackTrace[i].getClassName().contains("armourers_workshop")) {
                this.syncDummyModel();
                return this.dummyVanillaModel;
            }
        }
        return super.getModel();
    }

    private void syncDummyModel() {
        PlayerModel<HeroEntity> actual = super.getModel();
        this.dummyVanillaModel.head.copyFrom(actual.head);
        this.dummyVanillaModel.hat.copyFrom(actual.hat);
        this.dummyVanillaModel.body.copyFrom(actual.body);
        this.dummyVanillaModel.jacket.copyFrom(actual.jacket);
        this.dummyVanillaModel.rightArm.copyFrom(actual.rightArm);
        this.dummyVanillaModel.rightSleeve.copyFrom(actual.rightSleeve);
        this.dummyVanillaModel.leftArm.copyFrom(actual.leftArm);
        this.dummyVanillaModel.leftSleeve.copyFrom(actual.leftSleeve);
        this.dummyVanillaModel.rightLeg.copyFrom(actual.rightLeg);
        this.dummyVanillaModel.rightPants.copyFrom(actual.rightPants);
        this.dummyVanillaModel.leftLeg.copyFrom(actual.leftLeg);
        this.dummyVanillaModel.leftPants.copyFrom(actual.leftPants);

        this.dummyVanillaModel.crouching = actual.crouching;
        this.dummyVanillaModel.riding = actual.riding;
        this.dummyVanillaModel.young = actual.young;
        this.dummyVanillaModel.rightArmPose = actual.rightArmPose;
        this.dummyVanillaModel.leftArmPose = actual.leftArmPose;
        this.dummyVanillaModel.attackTime = actual.attackTime;
    }

    @Override
    public ResourceLocation getTextureLocation(HeroEntity entity) {
        if (entity.level().dimension() == ModStructures.END_RING_DIMENSION_KEY) return HEROBRINE_TEXTURE;
        int variant = entity.getSkinVariant();
        if (variant == HeroEntity.SKIN_HEROBRINE) return HEROBRINE_TEXTURE;
        if (variant == HeroEntity.SKIN_HERO) return HERO_TEXTURE;
        if (variant == HeroEntity.SKIN_CUSTOM) {
            String customPath = entity.getCustomSkinName();
            if (customPath != null && !customPath.isEmpty()) return getLocalSkin(customPath);
        }
        return HERO_TEXTURE;
    }

    private ResourceLocation getLocalSkin(String path) {
        if (LOCAL_SKIN_CACHE.containsKey(path)) return LOCAL_SKIN_CACHE.get(path);
        File file = new File(path);
        if (file.exists() && file.isFile()) {
            try (InputStream inputStream = new FileInputStream(file)) {
                NativeImage image = NativeImage.read(inputStream);
                DynamicTexture texture = new DynamicTexture(image);
                String safeName = "custom_skin_" + Integer.toHexString(path.hashCode());
                ResourceLocation location = ResourceLocation.fromNamespaceAndPath(HerobrineCompanion.MODID, safeName);
                Minecraft.getInstance().getTextureManager().register(location, texture);
                LOCAL_SKIN_CACHE.put(path, location);
                return location;
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return HERO_TEXTURE;
    }

    @Override
    public void render(HeroEntity entity, float entityYaw, float partialTicks, PoseStack poseStack, MultiBufferSource buffer, int packedLight) {

        HeroAWCompat.syncContext(this, entity);

        float floatAmount = entity.getFloatingAmount(partialTicks);
        if (floatAmount > 0.01f) {
            float age = entity.tickCount + partialTicks;
            poseStack.translate(0.0D, (Mth.sin(age * 0.1f) * 0.05f + 0.05f) * floatAmount, 0.0D);
        }

        boolean isGlitching = entity.isGlitching();

        poseStack.pushPose();
        try {
            if (isGlitching) {
                double jitter = 0.02;
                poseStack.translate((entity.getRandom().nextDouble() - 0.5) * jitter, (entity.getRandom().nextDouble() - 0.5) * jitter, (entity.getRandom().nextDouble() - 0.5) * jitter);
            }

            // 彻底去除所有的 endBatch 干扰，让 Iris 自己管理缓冲区
            super.render(entity, entityYaw, partialTicks, poseStack, buffer, packedLight);
        } finally {
            poseStack.popPose();
        }

        if (isGlitching) {
            float age = entity.tickCount + partialTicks;
            this.getModel().setupAnim(entity, 0, 0, age, entity.getYRot(), entity.getXRot());
            renderGlitchShadow(poseStack, buffer, packedLight, 0x60FF0000, entity);
            renderGlitchShadow(poseStack, buffer, packedLight, 0x6000FFFF, entity);
        }
    }

    private void renderGlitchShadow(PoseStack poseStack, MultiBufferSource buffer, int light, int color, HeroEntity entity) {
        // ... (保持不变) ...
        poseStack.pushPose();
        poseStack.scale(-1.0F, -1.0F, 1.0F);
        poseStack.translate(0.0D, -1.501D, 0.0D);
        poseStack.translate((entity.getRandom().nextDouble() - 0.5) * 0.4, (entity.getRandom().nextDouble() - 0.5) * 0.2, (entity.getRandom().nextDouble() - 0.5) * 0.4);

        ResourceLocation tex = this.getTextureLocation(entity);
        VertexConsumer vc = buffer.getBuffer(RenderType.entityTranslucentEmissive(tex));
        this.getModel().renderToBuffer(poseStack, vc, light, OverlayTexture.NO_OVERLAY, color);
        poseStack.popPose();
    }

    @Override
    protected void renderNameTag(HeroEntity entity, Component displayName, PoseStack poseStack, MultiBufferSource buffer, int packedLight, float partialTick) {
        super.renderNameTag(entity, displayName, poseStack, buffer, packedLight, partialTick);
    }
}