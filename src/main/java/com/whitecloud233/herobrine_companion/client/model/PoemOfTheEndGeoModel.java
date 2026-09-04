package com.whitecloud233.herobrine_companion.client.model;

import com.whitecloud233.herobrine_companion.HerobrineCompanion;
import com.whitecloud233.herobrine_companion.client.render.PoemOfTheEndAnimatable;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.DefaultedItemGeoModel;

/**
 * GeckoLib 模型定义 —— 终末之诗 (poem_of_the_end)
 * <p>
 * 模型文件: assets/herobrine_companion/geo/item/poem_of_the_end.geo.json
 * 贴图文件: assets/herobrine_companion/textures/item/poem_of_the_end_geo.png
 * 动画文件: assets/herobrine_companion/animations/item/poem_of_the_end.animation.json
 * <p>
 * 泛型是 {@link PoemOfTheEndAnimatable} 而非物品类：物品类不再实现 GeoItem，
 * 以便没装 GeckoLib 时可安全加载（见 PoemOfTheEndAnimatable 的类注释）。
 */
public class PoemOfTheEndGeoModel extends DefaultedItemGeoModel<PoemOfTheEndAnimatable> {

	public PoemOfTheEndGeoModel() {
		super(ResourceLocation.fromNamespaceAndPath(HerobrineCompanion.MODID, "poem_of_the_end"));
		// 使用新的 3D 贴图（liandao.png），保留原贴图 poem_of_the_end.png 不动
		// withAltTexture 会自动补上 "textures/item/" 前缀与 ".png" 后缀
		withAltTexture(ResourceLocation.fromNamespaceAndPath(HerobrineCompanion.MODID, "poem_of_the_end_geo"));
	}
}