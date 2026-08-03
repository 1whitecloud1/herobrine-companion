package com.whitecloud233.modid.herobrine_companion.client.render;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.whitecloud233.modid.herobrine_companion.HerobrineCompanion;
import com.whitecloud233.modid.herobrine_companion.client.model.HeroModel;
import com.whitecloud233.modid.herobrine_companion.compat.accessories.HeroAccessoriesClientCompat;
import com.whitecloud233.modid.herobrine_companion.compat.simplehats.HeroSimpleHatsCompat;
import com.whitecloud233.modid.herobrine_companion.compat.waveycapes.HeroWaveyCapesCompat;
import com.whitecloud233.modid.herobrine_companion.entity.HeroEntity;
import com.whitecloud233.modid.herobrine_companion.fight.HeroAfterimage;
import com.whitecloud233.modid.herobrine_companion.world.structure.ModStructures;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.ByteArrayInputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class HeroRenderer extends LivingEntityRenderer<HeroEntity, PlayerModel<HeroEntity>> {

    private static final Logger LOGGER = LogUtils.getLogger();

    private static final ResourceLocation HERO_TEXTURE = ResourceLocation.tryParse(HerobrineCompanion.MODID + ":textures/entity/hero.png");
    private static final ResourceLocation HEROBRINE_TEXTURE = ResourceLocation.tryParse(HerobrineCompanion.MODID + ":textures/entity/herobrine.png");

    // 内置皮肤沿用的固定发光眼睛贴图(白点位置已按内置脸部手工对齐)
    public static final ResourceLocation DEFAULT_EYES = new ResourceLocation(HerobrineCompanion.MODID, "textures/entity/hero_eyes.png");
    // 哨兵:表示某皮肤已分析过且不含白色眼睛,避免重复分析
    private static final ResourceLocation NO_EYES = new ResourceLocation(HerobrineCompanion.MODID, "no_eyes");

    private static final Map<String, ResourceLocation> LOCAL_SKIN_CACHE = new HashMap<>();
    private static final Map<UUID, ResourceLocation> SYNCED_SKIN_CACHE = new HashMap<>();
    private static final Map<UUID, Integer> SYNCED_SKIN_HASH_CACHE = new HashMap<>();

    // 自定义皮肤自动识别生成的眼睛叠加图缓存(与上面的皮肤缓存一一对应)
    private static final Map<String, ResourceLocation> LOCAL_EYES_CACHE = new HashMap<>();
    private static final Map<UUID, ResourceLocation> SYNCED_EYES_CACHE = new HashMap<>();
    private static final Map<UUID, Integer> SYNCED_EYES_HASH_CACHE = new HashMap<>();

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

        HeroAccessoriesClientCompat.attachRenderLayer(this);
        HeroSimpleHatsCompat.attachRenderLayer(this);
        HeroWaveyCapesCompat.attachRenderLayer(this);

        // 注意：这里已经删除了之前的 HeroCustomSkinLayer，因为 AW 原生接管了。
    }

    public void addHeroRenderLayer(RenderLayer<HeroEntity, PlayerModel<HeroEntity>> layer) {
        this.addLayer(layer);
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
                ResourceLocation syncedSkin = getSyncedSkin(entity.getUUID());
                if (syncedSkin != null) {
                    return syncedSkin;
                }
                String customPath = entity.getCustomSkinName();
                if (customPath != null && !customPath.isEmpty()) {
                    return getLocalSkin(customPath);
                }
                return DefaultPlayerSkin.getDefaultSkin();
            default:
                return HEROBRINE_TEXTURE;
        }
    }

    /**
     * 返回当前应使用的发光眼睛贴图,逻辑与 {@link #getTextureLocation} 保持一致:
     * 内置皮肤用固定 {@link #DEFAULT_EYES};自定义皮肤用从其像素自动识别生成的对齐叠加图;
     * 自定义皮肤未检出白色眼睛时返回 {@code null}(不渲染发光)。
     */
    public static ResourceLocation getEyesTexture(HeroEntity entity) {
        if (entity.level().dimension() == ModStructures.END_RING_DIMENSION_KEY) {
            return DEFAULT_EYES;
        }

        if (entity.getSkinVariant() == HeroEntity.SKIN_CUSTOM) {
            // 眼睛图必须与身体图取自同一张皮肤:优先同步皮肤,其次本地文件
            if (getSyncedSkin(entity.getUUID()) != null) {
                return getSyncedEyes(entity.getUUID());
            }
            String customPath = entity.getCustomSkinName();
            if (customPath != null && !customPath.isEmpty()) {
                return getLocalEyes(customPath);
            }
            return null; // 回退默认玩家皮肤,无发光眼睛
        }

        // 内置 Herobrine / Hero 皮肤:沿用手工对齐的固定眼睛图
        return DEFAULT_EYES;
    }

    private static ResourceLocation getLocalSkin(String path) {
        if (!LOCAL_SKIN_CACHE.containsKey(path)) {
            loadLocalSkinAndEyes(path);
        }
        return LOCAL_SKIN_CACHE.get(path);
    }

    private static ResourceLocation getLocalEyes(String path) {
        if (!LOCAL_EYES_CACHE.containsKey(path)) {
            loadLocalSkinAndEyes(path);
        }
        ResourceLocation eyes = LOCAL_EYES_CACHE.get(path);
        return eyes == NO_EYES ? null : eyes;
    }

    // 一次解码,同时产出底图与眼睛叠加图并写入各自缓存
    private static void loadLocalSkinAndEyes(String path) {
        File file = new File(path);
        if (file.exists() && file.isFile()) {
            try (InputStream inputStream = new FileInputStream(file)) {
                NativeImage image = NativeImage.read(inputStream);
                String hash = Integer.toHexString(path.hashCode());
                // 先读取像素生成眼睛图,再把 image 交给 DynamicTexture(顺序安全)
                ResourceLocation eyes = buildEyesOverlay(image, "custom_skin_eyes_" + hash);
                LOCAL_EYES_CACHE.put(path, eyes == null ? NO_EYES : eyes);

                DynamicTexture texture = new DynamicTexture(image);
                ResourceLocation location = Minecraft.getInstance().getTextureManager().register("custom_skin_" + hash, texture);
                LOCAL_SKIN_CACHE.put(path, location);
                return;
            } catch (Exception e) {
                LOGGER.warn("Failed to load local hero skin from {}", path, e);
            }
        }
        LOCAL_SKIN_CACHE.put(path, DefaultPlayerSkin.getDefaultSkin());
        LOCAL_EYES_CACHE.put(path, NO_EYES);
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
            ResourceLocation eyes = buildEyesOverlay(image, "synced_hero_skin_eyes_" + safeId);
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
     * 命中则注册为动态贴图并返回其 ResourceLocation;未命中返回 null。
     */
    private static ResourceLocation buildEyesOverlay(NativeImage skin, String registerName) {
        try {
            HeroSkinEyeDetector.Result detection = HeroSkinEyeDetector.detect(skin);
            if (detection == null) {
                LOGGER.debug("No paired white eyes found in hero skin '{}' ({}x{})",
                        registerName, skin.getWidth(), skin.getHeight());
                return null;
            }

            LOGGER.debug("Detected {} eye pixels in hero skin '{}' at scale {}",
                    detection.pixelCount(), registerName, detection.scale());
            DynamicTexture texture = new DynamicTexture(detection.overlay());
            return Minecraft.getInstance().getTextureManager().register(registerName, texture);
        } catch (Exception e) {
            LOGGER.warn("Failed to build eyes overlay '{}'", registerName, e);
            return null;
        }
    }

    @Nullable
    @Override
    protected RenderType getRenderType(HeroEntity entity, boolean bodyVisible, boolean translucent, boolean glowing) {
        return RenderType.entityCutoutNoCull(this.getTextureLocation(entity));
    }

    @Override
    public void render(HeroEntity entity, float entityYaw, float partialTicks, PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        float floatAmount = entity.getFloatingAmount(partialTicks);
        float floatOffsetY = 0.0F;
        if (floatAmount > 0.01f) {
            float ageInTicks = entity.tickCount + partialTicks;
            float floatHeight = Mth.sin(ageInTicks * 0.1f) * 0.05f * floatAmount;
            float baseOffset = 0.05f * floatAmount;
            floatOffsetY = floatHeight + baseOffset;
            poseStack.translate(0.0D, floatOffsetY, 0.0D);
        }

        boolean isGlitching = entity.isGlitching();

        poseStack.pushPose();
        if (isGlitching) {
            double mainJitterX = (entity.getRandom().nextDouble() - 0.5) * 0.5;
            double mainJitterY = (entity.getRandom().nextDouble() - 0.5) * 0.5;
            double mainJitterZ = (entity.getRandom().nextDouble() - 0.5) * 0.5;
            poseStack.translate(mainJitterX, mainJitterY, mainJitterZ);
        }

        // AW 渲染上下文在客户端初始化阶段接通，这里只负责正常渲染 Hero 本体。
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

        renderChallengeAfterimages(entity, partialTicks, poseStack, buffer, floatOffsetY);
    }

    private void renderChallengeAfterimages(HeroEntity entity, float partialTicks, PoseStack poseStack,
                                             MultiBufferSource buffer, float floatOffsetY) {
        if (entity.getChallengeAfterimages().isEmpty()) {
            return;
        }

        Vec3 entityRenderPos = entity.getPosition(partialTicks).add(0.0D, floatOffsetY, 0.0D);
        ResourceLocation texture = this.getTextureLocation(entity);
        ResourceLocation eyes = HeroRenderer.getEyesTexture(entity);

        for (HeroAfterimage afterimage : entity.getChallengeAfterimages()) {
            int alphaInt = afterimage.getAlpha(partialTicks);
            if (alphaInt <= 0) {
                continue;
            }

            float alpha = alphaInt / 255.0F;
            Vec3 translation = afterimage.getPosition().subtract(entityRenderPos);

            poseStack.pushPose();
            poseStack.translate(translation.x, translation.y + 1.5D, translation.z);
            poseStack.scale(1.0F, -1.0F, 1.0F);
            this.setupRotations(entity, poseStack, getBob(entity, partialTicks), afterimage.getYRot(), partialTicks);

            if (texture != null) {
                VertexConsumer vertexConsumer = buffer.getBuffer(RenderType.entityTranslucent(texture));
                this.getModel().renderToBuffer(
                        poseStack,
                        vertexConsumer,
                        0xF000F0,
                        OverlayTexture.NO_OVERLAY,
                        0.45F,
                        0.85F,
                        1.0F,
                        alpha
                );
            }

            if (eyes != null) {
                VertexConsumer eyeConsumer = buffer.getBuffer(RenderType.eyes(eyes));
                this.getModel().renderToBuffer(
                        poseStack,
                        eyeConsumer,
                        0xF000F0,
                        OverlayTexture.NO_OVERLAY,
                        1.0F,
                        1.0F,
                        1.0F,
                        alpha
                );
            }

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
