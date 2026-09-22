package com.whitecloud233.modid.herobrine_companion.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.logging.LogUtils;
import com.whitecloud233.modid.herobrine_companion.HerobrineCompanion;
import com.whitecloud233.modid.herobrine_companion.entity.BirthdayCakePropEntity;
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
 * 转换出的方块模型 {@code models/entity/birthday_cake.json}(方块模型原生支持逐面 UV,
 * 而 1.20.1 的 {@code CubeListBuilder} 只有盒式 UV,故走这条路径),
 * 由 {@code ModelEvent.RegisterAdditional} 烘焙后在此渲染。
 *
 * <p><b>兜底</b>:若该模型因任何原因没能烘焙,这里会退回渲染原版蛋糕方块并打出一次性警告,
 * 保证摆件在任何情况下都看得见,而不是变成一块缺失模型贴图或者完全不可见。
 */
public class BirthdayCakePropRenderer extends EntityRenderer<BirthdayCakePropEntity> {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** 由 gen-cake-prop-blockmodel.ps1 生成、并在 ClientModSetup 里注册烘焙的模型。 */
    public static final ModelResourceLocation MODEL = new ModelResourceLocation(
            HerobrineCompanion.MODID, "birthday_cake_prop", "inventory");

    private static final ResourceLocation TEXTURE =
            new ResourceLocation(HerobrineCompanion.MODID, "textures/item/birthday_cake_prop.png");

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