package com.whitecloud233.herobrine_companion.client.render;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.whitecloud233.herobrine_companion.fight.HeroAfterimage;
import com.mojang.logging.LogUtils;
import com.whitecloud233.herobrine_companion.HerobrineCompanion;
import com.whitecloud233.herobrine_companion.client.model.HeroModel;
import com.whitecloud233.herobrine_companion.compat.accessories.HeroAccessoriesClientCompat;
import com.whitecloud233.herobrine_companion.compat.accessories.HeroAccessoriesCompat;
import com.whitecloud233.herobrine_companion.compat.simplehats.HeroSimpleHatsCompat;
import com.whitecloud233.herobrine_companion.compat.waveycapes.HeroWaveyCapesCompat;
import com.whitecloud233.herobrine_companion.entity.HeroEntity;
import com.whitecloud233.herobrine_companion.world.structure.ModStructures;
import com.whitecloud233.herobrine_companion.compat.ArmourerWorkshop.HeroAWCompat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import org.joml.Matrix4f;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.slf4j.Logger;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class HeroRenderer extends LivingEntityRenderer<HeroEntity, PlayerModel<HeroEntity>> {

    private static final Logger LOGGER = LogUtils.getLogger();

    private static final ResourceLocation HERO_TEXTURE = ResourceLocation.tryParse(HerobrineCompanion.MODID + ":textures/entity/hero.png");
    private static final ResourceLocation HEROBRINE_TEXTURE = ResourceLocation.tryParse(HerobrineCompanion.MODID + ":textures/entity/herobrine.png");

    // 内置皮肤沿用的固定发光眼睛贴图(白点位置已按内置脸部手工对齐)
    public static final ResourceLocation DEFAULT_EYES = ResourceLocation.fromNamespaceAndPath(HerobrineCompanion.MODID, "textures/entity/hero_eyes.png");
    // 哨兵:表示某皮肤已分析过且不含白色眼睛,避免重复分析
    private static final ResourceLocation NO_EYES = ResourceLocation.fromNamespaceAndPath(HerobrineCompanion.MODID, "no_eyes");

    private static final Map<String, ResourceLocation> LOCAL_SKIN_CACHE = new HashMap<>();
    private static final Map<UUID, ResourceLocation> SYNCED_SKIN_CACHE = new HashMap<>();
    private static final Map<UUID, Integer> SYNCED_SKIN_HASH_CACHE = new HashMap<>();

    // 自定义皮肤自动识别生成的眼睛叠加图缓存(与上面的皮肤缓存一一对应)
    private static final Map<String, ResourceLocation> LOCAL_EYES_CACHE = new HashMap<>();
    private static final Map<UUID, ResourceLocation> SYNCED_EYES_CACHE = new HashMap<>();
    private static final Map<UUID, Integer> SYNCED_EYES_HASH_CACHE = new HashMap<>();

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
        HeroAccessoriesClientCompat.attachRenderLayer(this);
        HeroSimpleHatsCompat.attachRenderLayer(this);
        HeroWaveyCapesCompat.attachRenderLayer(this);

    }

    // =========================================================
    // [光影兼容版] 深度调用栈监听：穿透 Iris 的包装层
    // =========================================================
    // =========================================================
    // [光影兼容版] 混合型雷达：主动伪装 + 深度调用栈监听
    // =========================================================
    public void addHeroRenderLayer(RenderLayer<HeroEntity, PlayerModel<HeroEntity>> layer) {
        this.addLayer(layer);
    }

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
            ResourceLocation syncedSkin = getSyncedSkin(entity.getUUID());
            if (syncedSkin != null) {
                return syncedSkin;
            }
            String customPath = entity.getCustomSkinName();
            if (customPath != null && !customPath.isEmpty()) return getLocalSkin(customPath);
        }
        return HERO_TEXTURE;
    }

    /**
     * 返回当前应使用的发光眼睛贴图,逻辑与 {@link #getTextureLocation} 保持一致:
     * 内置皮肤用固定 {@link #DEFAULT_EYES};自定义皮肤用从其像素自动识别生成的对齐叠加图;
     * 自定义皮肤未检出白色眼睛时返回 {@code null}(不渲染发光)。
     */
    public static ResourceLocation getEyesTexture(HeroEntity entity) {
        if (entity.level().dimension() == ModStructures.END_RING_DIMENSION_KEY) return DEFAULT_EYES;
        int variant = entity.getSkinVariant();
        if (variant == HeroEntity.SKIN_CUSTOM) {
            // 眼睛图必须与身体图取自同一张皮肤:优先同步皮肤,其次本地文件
            if (getSyncedSkin(entity.getUUID()) != null) return getSyncedEyes(entity.getUUID());
            String customPath = entity.getCustomSkinName();
            if (customPath != null && !customPath.isEmpty()) return getLocalEyes(customPath);
            return DEFAULT_EYES; // 无自定义数据时身体回退 HERO_TEXTURE,眼睛用对齐的固定图
        }
        // SKIN_HEROBRINE / SKIN_HERO / 其它:沿用手工对齐的固定眼睛图
        return DEFAULT_EYES;
    }

    private static ResourceLocation getLocalSkin(String path) {
        loadLocalSkinAndEyes(path);
        ResourceLocation loc = LOCAL_SKIN_CACHE.get(path);
        return loc != null ? loc : HERO_TEXTURE;
    }

    private static ResourceLocation getLocalEyes(String path) {
        loadLocalSkinAndEyes(path);
        ResourceLocation eyes = LOCAL_EYES_CACHE.get(path);
        return (eyes == null || eyes == NO_EYES) ? null : eyes;
    }

    // 一次解码,同时产出底图与眼睛叠加图并写入各自缓存(仅在未缓存时执行)
    private static void loadLocalSkinAndEyes(String path) {
        if (LOCAL_SKIN_CACHE.containsKey(path)) return;
        File file = new File(path);
        if (file.exists() && file.isFile()) {
            try (InputStream inputStream = new FileInputStream(file)) {
                NativeImage image = NativeImage.read(inputStream);
                String hash = Integer.toHexString(path.hashCode());
                // 先读取像素生成眼睛图,再把 image 交给 DynamicTexture(顺序安全)
                ResourceLocation eyes = buildEyesOverlay(image, ResourceLocation.fromNamespaceAndPath(HerobrineCompanion.MODID, "custom_skin_eyes_" + hash));
                LOCAL_EYES_CACHE.put(path, eyes == null ? NO_EYES : eyes);

                DynamicTexture texture = new DynamicTexture(image);
                ResourceLocation location = ResourceLocation.fromNamespaceAndPath(HerobrineCompanion.MODID, "custom_skin_" + hash);
                Minecraft.getInstance().getTextureManager().register(location, texture);
                LOCAL_SKIN_CACHE.put(path, location);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
    private static ResourceLocation getSyncedSkin(UUID heroId) {
        if (heroId == null) {
            return null;
        }

        int currentHash = HeroClientSkinCache.getHash(heroId);
        ResourceLocation cachedTexture = SYNCED_SKIN_CACHE.get(heroId);
        if (cachedTexture != null && Integer.valueOf(currentHash).equals(SYNCED_SKIN_HASH_CACHE.get(heroId))) {
            return cachedTexture;
        }

        byte[] skinData = HeroClientSkinCache.get(heroId);
        if (skinData.length == 0) {
            SYNCED_SKIN_CACHE.remove(heroId);
            SYNCED_SKIN_HASH_CACHE.remove(heroId);
            SYNCED_EYES_CACHE.remove(heroId);
            SYNCED_EYES_HASH_CACHE.remove(heroId);
            return null;
        }

        try (InputStream inputStream = new ByteArrayInputStream(skinData)) {
            NativeImage image = NativeImage.read(inputStream);
            String safeId = heroId.toString().replace('-', '_');
            // 先读取像素生成眼睛图,再把 image 交给 DynamicTexture(顺序安全)
            ResourceLocation eyes = buildEyesOverlay(image, ResourceLocation.fromNamespaceAndPath(HerobrineCompanion.MODID, "synced_hero_skin_eyes_" + safeId));
            SYNCED_EYES_CACHE.put(heroId, eyes == null ? NO_EYES : eyes);
            SYNCED_EYES_HASH_CACHE.put(heroId, currentHash);

            DynamicTexture texture = new DynamicTexture(image);
            ResourceLocation location = Minecraft.getInstance().getTextureManager().register("synced_hero_skin_" + safeId, texture);
            SYNCED_SKIN_CACHE.put(heroId, location);
            SYNCED_SKIN_HASH_CACHE.put(heroId, currentHash);
            return location;
        } catch (Exception e) {
            LOGGER.warn("Failed to register synced hero skin for {}", heroId, e);
            SYNCED_SKIN_CACHE.remove(heroId);
            SYNCED_SKIN_HASH_CACHE.remove(heroId);
            SYNCED_EYES_CACHE.remove(heroId);
            SYNCED_EYES_HASH_CACHE.remove(heroId);
            return null;
        }
    }

    private static ResourceLocation getSyncedEyes(UUID heroId) {
        if (heroId == null) {
            return null;
        }
        int currentHash = HeroClientSkinCache.getHash(heroId);
        if (SYNCED_EYES_CACHE.containsKey(heroId)
                && Integer.valueOf(currentHash).equals(SYNCED_EYES_HASH_CACHE.get(heroId))) {
            ResourceLocation eyes = SYNCED_EYES_CACHE.get(heroId);
            return eyes == NO_EYES ? null : eyes;
        }
        // 缓存缺失或已过期:触发皮肤(连同眼睛)重新加载
        getSyncedSkin(heroId);
        ResourceLocation eyes = SYNCED_EYES_CACHE.get(heroId);
        return (eyes == null || eyes == NO_EYES) ? null : eyes;
    }

    /**
     * 从皮肤正脸区域识别白色眼睛,生成同尺寸、除眼睛外全透明的发光叠加图。
     * 命中则以 registerId 注册为动态贴图并返回该 ResourceLocation;未命中返回 null。
     */
    private static ResourceLocation buildEyesOverlay(NativeImage skin, ResourceLocation registerId) {
        try {
            HeroSkinEyeDetector.Result detection = HeroSkinEyeDetector.detect(skin);
            if (detection == null) {
                LOGGER.debug("No paired white eyes found in hero skin '{}' ({}x{})",
                        registerId, skin.getWidth(), skin.getHeight());
                return null;
            }

            LOGGER.debug("Detected {} eye pixels in hero skin '{}' at scale {}",
                    detection.pixelCount(), registerId, detection.scale());
            DynamicTexture texture = new DynamicTexture(detection.overlay());
            Minecraft.getInstance().getTextureManager().register(registerId, texture);
            return registerId;
        } catch (Exception e) {
            LOGGER.warn("Failed to build eyes overlay '{}'", registerId, e);
            return null;
        }
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

        AwakenedMobNameplateRenderer.renderForced(poseStack, buffer, entity, packedLight, partialTicks, 0xFFFF5555);
        EntitySpeechBubbleRenderer.render(poseStack, buffer, entity, packedLight, partialTicks);

        if (isGlitching) {
            float age = entity.tickCount + partialTicks;
            this.getModel().setupAnim(entity, 0, 0, age, entity.getYRot(), entity.getXRot());
            renderGlitchShadow(poseStack, buffer, packedLight, 0x60FF0000, entity);
            renderGlitchShadow(poseStack, buffer, packedLight, 0x6000FFFF, entity);
        }

        renderChallengeAfterimages(entity, partialTicks, poseStack, buffer);
    }

    private void renderChallengeAfterimages(HeroEntity entity, float partialTicks, PoseStack poseStack,
                                            MultiBufferSource buffer) {
        if (entity.getChallengeAfterimages().isEmpty()) {
            return;
        }

        net.minecraft.world.phys.Vec3 entityRenderPos = entity.getPosition(partialTicks);
        ResourceLocation texture = this.getTextureLocation(entity);
        ResourceLocation eyes = HeroRenderer.getEyesTexture(entity);

        for (HeroAfterimage afterimage : entity.getChallengeAfterimages()) {
            int alphaInt = afterimage.getAlpha(partialTicks);
            if (alphaInt <= 0) {
                continue;
            }

            float alpha = alphaInt / 255.0F;
            net.minecraft.world.phys.Vec3 translation = afterimage.getPosition().subtract(entityRenderPos);

            poseStack.pushPose();
            poseStack.translate(translation.x, translation.y + 1.5D, translation.z);
            poseStack.scale(1.0F, -1.0F, 1.0F);
            this.setupRotations(entity, poseStack, getBob(entity, partialTicks), afterimage.getYRot(), partialTicks, 0.0F);

            if (texture != null) {
                int bodyArgb = (alphaInt << 24) | 0x73D9FF;
                VertexConsumer vertexConsumer = buffer.getBuffer(RenderType.entityTranslucent(texture));
                this.getModel().renderToBuffer(poseStack, vertexConsumer, 0xF000F0, OverlayTexture.NO_OVERLAY, bodyArgb);
            }

            if (eyes != null) {
                int eyeArgb = (alphaInt << 24) | 0xFFFFFF;
                VertexConsumer eyeConsumer = buffer.getBuffer(RenderType.eyes(eyes));
                this.getModel().renderToBuffer(poseStack, eyeConsumer, 0xF000F0, OverlayTexture.NO_OVERLAY, eyeArgb);
            }

            poseStack.popPose();
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
        // [原版命名牌代码保持不变] —— 用红色绘制 Herobrine 名牌，与普通生物区分。
        double d0 = this.entityRenderDispatcher.distanceToSqr(entity);
        if (d0 <= 4096.0D) {
            boolean isSneaking = !entity.isDiscrete();
            float height = entity.getBbHeight() + 0.5F;
            int yOffset = "deadmau5".equals(displayName.getString()) ? -10 : 0;

            poseStack.pushPose();
            poseStack.translate(0.0D, height, 0.0D);
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
