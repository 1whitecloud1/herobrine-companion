package com.whitecloud233.modid.herobrine_companion.client.render;

import com.whitecloud233.modid.herobrine_companion.HerobrineCompanion;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.DefaultedItemGeoModel;

/**
 * 终末之诗的 GeckoLib 模型。
 *
 * <p>资源路径：{@code geo/item/poem_of_the_end.geo.json}、
 * {@code textures/item/poem_of_the_end_geo.png}、
 * {@code animations/item/poem_of_the_end.animation.json}。</p>
 */
public final class PoemOfTheEndGeoModel extends DefaultedItemGeoModel<PoemOfTheEndAnimatable> {

    public PoemOfTheEndGeoModel() {
        super(new ResourceLocation(HerobrineCompanion.MODID, "poem_of_the_end"));
        // withAltTexture 会经 buildFormattedTexturePath 自动补上 "textures/item/" 前缀
        // （DefaultedItemGeoModel.subtype() 在 4.7 与 4.8+ 都返回 "item"，行为一致，
        // 已用 javap 比对两版本字节码确认），因此这里只传文件名。
        withAltTexture(new ResourceLocation(HerobrineCompanion.MODID, "poem_of_the_end_geo"));
    }
}
