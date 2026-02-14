package com.whitecloud233.modid.herobrine_companion.compat.jei;

import com.whitecloud233.modid.herobrine_companion.HerobrineCompanion;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.MerchantOffer;

public class HeroTradeCategory implements IRecipeCategory<MerchantOffer> {
    public static final RecipeType<MerchantOffer> RECIPE_TYPE = RecipeType.create(HerobrineCompanion.MODID, "hero_trade", MerchantOffer.class);
    
    private final IDrawable background;
    private final IDrawable icon;
    private final Component localizedName;

    public HeroTradeCategory(IGuiHelper guiHelper) {
        // 使用 JEI 自带的类似村民交易的背景
        ResourceLocation texture = ResourceLocation.fromNamespaceAndPath("jei", "textures/jei/gui/gui_vanilla.png");
        // 截取一部分作为背景 (0, 220) 是 JEI 默认的村民交易背景位置
        this.background = guiHelper.createDrawable(texture, 0, 220, 82, 34);
        
        // 使用创造模式物品栏的图标 (TAB_ICON)
        this.icon = guiHelper.createDrawableIngredient(VanillaTypes.ITEM_STACK, new ItemStack(HerobrineCompanion.TAB_ICON.get()));
        this.localizedName = Component.translatable("gui.herobrine_companion.jei.hero_trade");
    }

    @Override
    public RecipeType<MerchantOffer> getRecipeType() {
        return RECIPE_TYPE;
    }

    @Override
    public Component getTitle() {
        return localizedName;
    }

    @Override
    public IDrawable getBackground() {
        return background;
    }

    @Override
    public IDrawable getIcon() {
        return icon;
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, MerchantOffer recipe, IFocusGroup focuses) {
        // 输入 1 (左侧第一个槽位)
        builder.addSlot(RecipeIngredientRole.INPUT, 1, 9)
                .addIngredients(VanillaTypes.ITEM_STACK, java.util.List.of(recipe.getCostA()));

        // 输入 2 (左侧第二个槽位，如果有)
        if (!recipe.getCostB().isEmpty()) {
            builder.addSlot(RecipeIngredientRole.INPUT, 26, 9)
                    .addIngredients(VanillaTypes.ITEM_STACK, java.util.List.of(recipe.getCostB()));
        }

        // 输出 (右侧槽位)
        builder.addSlot(RecipeIngredientRole.OUTPUT, 61, 9)
                .addIngredients(VanillaTypes.ITEM_STACK, java.util.List.of(recipe.getResult()));
    }
}
