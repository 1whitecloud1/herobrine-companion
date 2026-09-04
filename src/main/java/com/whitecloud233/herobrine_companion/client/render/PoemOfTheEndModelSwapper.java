package com.whitecloud233.herobrine_companion.client.render;

import com.mojang.logging.LogUtils;
import com.whitecloud233.herobrine_companion.HerobrineCompanion;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.event.ModelEvent;
import org.slf4j.Logger;

/**
 * 没装 GeckoLib 时，把终末之诗的物品模型换成 2D 版本。
 *
 * <p>3D 路径要求 {@code models/item/poem_of_the_end.json} 的 parent 是
 * {@code builtin/entity}——这是 vanilla {@code BakedModel.isCustomRenderer()}
 * 返回 true、进而走 {@code IClientItemExtensions.getCustomRenderer()} 的唯一开关。
 * 但没装 GeckoLib 时我们不注册自定义渲染器，此时 vanilla 会拿到默认 BEWLR，
 * 它不认识这个物品 → <b>什么都不画</b>（物品隐形）。</p>
 *
 * <p>所以这里直接做烘焙结果替换：把 {@code poem_of_the_end#inventory} 指向
 * 已烘焙的 {@code poem_of_the_end_base#inventory}（纯 2D 模型，自带 display）。
 * 之后 vanilla 走的就是普通物品渲染路径，无需任何自定义渲染器或手写变换。</p>
 */
public final class PoemOfTheEndModelSwapper {

    private static final Logger LOGGER = LogUtils.getLogger();

    /**
     * 3D 路径实际使用的模型（parent = builtin/entity）。
     * <p>这是 {@code ItemModelShaper.shapes} 里物品对应的键：vanilla 用
     * {@code ModelResourceLocation.inventory(物品注册名)}，<b>不带</b> {@code item/} 前缀
     * （文件路径的 {@code item/} 是 {@code loadItemModelAndDependencies} 内部补的）。</p>
     */
    private static final ModelResourceLocation TARGET = ModelResourceLocation.inventory(
            ResourceLocation.fromNamespaceAndPath(HerobrineCompanion.MODID, "poem_of_the_end"));

    /**
     * 2D 回退模型；不是任何物品的 id，故必须显式注册才会被烘焙。
     * <p><b>⚠️ variant 必须是 {@code standalone}</b>：NeoForge 1.21.1 的
     * {@code ModelEvent.RegisterAdditional.register()} 会强制
     * {@code getVariant().equals("standalone")}，否则抛
     * {@code IllegalArgumentException: Side-loaded models must use the 'standalone' variant}。</p>
     * <p><b>⚠️ 这里必须带 {@code item/} 前缀</b>：{@code RegisterAdditional} 的条目在
     * {@code ModelBakery} 里走的是 {@code getModel(mrl.id())}——<b>原样</b>拿 id 当文件路径，
     * 不像 {@code loadItemModelAndDependencies} 会补 {@code item/}。写成
     * {@code standalone(MODID:poem_of_the_end_base)} 会去找
     * {@code models/poem_of_the_end_base.json}（不存在）→ 烘焙成紫黑缺失模型。</p>
     */
    private static final ModelResourceLocation FALLBACK_2D = ModelResourceLocation.standalone(
            ResourceLocation.fromNamespaceAndPath(HerobrineCompanion.MODID, "item/poem_of_the_end_base"));

    private PoemOfTheEndModelSwapper() {
    }

    /** 2D 模型的烘焙位置，供 {@link PoemOfTheEndFlatRenderer} 画物品栏图标时取用。 */
    public static ModelResourceLocation flatModelLocation() {
        return FALLBACK_2D;
    }

    public static void onRegisterAdditional(final ModelEvent.RegisterAdditional event) {
        // 无条件烘焙：没装 GeckoLib 时它是完整回退模型；装了 GeckoLib 时
        // 物品栏图标仍用它（PoemOfTheEndFlatRenderer），只有手持/地面等用 3D。
        event.register(FALLBACK_2D);
    }

    public static void onModifyBakingResult(final ModelEvent.ModifyBakingResult event) {
        if (PoemOfTheEndGeoCompat.isGeckoLibLoaded()) {
            // 装了 GeckoLib：保留 builtin/entity 以便走自定义渲染器，不做替换
            return;
        }
        BakedModel baked2d = event.getModels().get(FALLBACK_2D);
        if (baked2d == null) {
            LOGGER.error("[poem] 2D 回退模型 {} 未烘焙成功，终末之诗可能不可见", FALLBACK_2D);
            return;
        }
        event.getModels().put(TARGET, baked2d);
        LOGGER.info("[poem] 已将 {} 替换为 2D 模型 {}", TARGET, FALLBACK_2D);
    }
}