package com.whitecloud233.herobrine_companion.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.logging.LogUtils;
import com.whitecloud233.herobrine_companion.HerobrineCompanion;
import com.whitecloud233.herobrine_companion.entity.BirthdayCakePropEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelManager;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import org.slf4j.Logger;

/**
 * 无名之蛋糕摆件的渲染器 —— 对应 Bedrock 的 birthday_cake_prop.entity.json
 * (geometry.herobrine_companion.birthday_cake + textures/entity/birthday_cake)。
 *
 * <p>模型来自 gen-cake-prop-blockmodel.ps1 从 Bedrock {@code birthday_cake.geo.json}
 * 转换出的方块模型 {@code models/entity/birthday_cake.json}(方块模型原生支持逐面 UV),
 * 由 {@code ModelEvent.RegisterAdditional} 烘焙后在此渲染。
 *
 * <p><b>兜底</b>:若该模型因任何原因没能烘焙,这里会退回渲染原版蛋糕方块并打出一次性警告,
 * 保证摆件在任何情况下都看得见,而不是变成一块缺失模型贴图或者完全不可见。
 */
public class BirthdayCakePropRenderer extends EntityRenderer<BirthdayCakePropEntity> {

    private static final Logger LOGGER = LogUtils.getLogger();

    /**
     * 由 gen-cake-prop-blockmodel.ps1 生成、并在 {@code ClientEvents#onRegisterAdditionalModels}
     * 里通过 {@code ModelEvent.RegisterAdditional} 注册烘焙的模型。
     *
     * <p><b>⚠️ variant 必须是 {@code standalone}</b>：NeoForge 1.21.1 的
     * {@code RegisterAdditional.register()} 会强制 {@code getVariant().equals("standalone")}，
     * 用 {@code ModelResourceLocation.inventory(...)} 会抛
     * {@code IllegalArgumentException: Side-loaded models must use the 'standalone' variant}；
     * 这个异常发生在资源重载期间，会让整次重载失败(游戏清空已选资源包重新加载)。</p>
     *
     * <p><b>⚠️ 路径必须带 {@code item/} 前缀</b>：{@code RegisterAdditional} 的条目在
     * {@code ModelBakery} 里是<b>原样</b>拿 id 当文件路径的(不像物品模型会补 {@code item/})，
     * 写成 {@code standalone(MODID:birthday_cake_prop)} 会去找
     * {@code models/birthday_cake_prop.json}(不存在)。</p>
     *
     * <p>cake 摆件是实体而不是物品，没有原版物品模型自动烘焙，所以必须显式 side-load。</p>
     */
    public static final ModelResourceLocation MODEL = ModelResourceLocation.standalone(
            ResourceLocation.fromNamespaceAndPath(HerobrineCompanion.MODID, "item/birthday_cake_prop"));

    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(HerobrineCompanion.MODID, "textures/item/birthday_cake_prop.png");

    /** 缺失模型只警告一次,避免每帧刷屏。 */
    private static boolean missingModelWarned = false;
    private static boolean renderLogged = false;

    public BirthdayCakePropRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.3F;
    }

    @Override
    public void render(BirthdayCakePropEntity entity, float entityYaw, float partialTick,
                       PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        Minecraft minecraft = Minecraft.getInstance();
        ModelManager modelManager = minecraft.getModelManager();
        BakedModel model = modelManager.getModel(MODEL);
        boolean missing = model == modelManager.getMissingModel();
        if (!renderLogged) {
            renderLogged = true;
            LOGGER.info("[HeroGift] birthday cake prop render called: model={} missing={}", MODEL, missing);
        }
        if (missing && !missingModelWarned) {
            missingModelWarned = true;
            LOGGER.warn("[HeroGift] birthday cake prop model {} was not baked; "
                    + "falling back to the vanilla cake block model. "
                    + "Check that ModelEvent.RegisterAdditional ran and that the model json exists.",
                    MODEL);
        }
        poseStack.pushPose();
        // 方块模型以方块原点为 (0,0,0)、尺寸 1×1×1;实体原点在脚下正中,故平移到方块中心
        poseStack.translate(-0.5D, 0.0D, -0.5D);
        if (missing) {
            minecraft.getBlockRenderer().renderSingleBlock(Blocks.CAKE.defaultBlockState(),
                    poseStack, buffer, packedLight, OverlayTexture.NO_OVERLAY);
        } else {
            // 方块模型的 UV 以方块纹理图集的 sprite 为基准,因此必须用方块渲染类型
            RenderType renderType = RenderType.cutout();
            minecraft.getItemRenderer().renderModelLists(model, ItemStack.EMPTY, packedLight,
                    OverlayTexture.NO_OVERLAY, poseStack, buffer.getBuffer(renderType));
        }
        poseStack.popPose();
        super.render(entity, entityYaw, partialTick, poseStack, buffer, packedLight);
    }

    @Override
    public ResourceLocation getTextureLocation(BirthdayCakePropEntity entity) {
        return TEXTURE;
    }
}