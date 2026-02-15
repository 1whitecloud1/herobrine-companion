package com.whitecloud233.modid.herobrine_companion.compat.jei;

import com.whitecloud233.modid.herobrine_companion.HerobrineCompanion;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.MerchantOffer;

import java.util.List;

public class HeroTradeCategory implements IRecipeCategory<MerchantOffer> {
    // 定义 RecipeType
    public static final RecipeType<MerchantOffer> RECIPE_TYPE = RecipeType.create(HerobrineCompanion.MODID, "hero_trade", MerchantOffer.class);

    private final IDrawable background;
    private final IDrawable icon;
    private final IDrawable slotBackground;
    private final Component localizedName;

    public HeroTradeCategory(IGuiHelper guiHelper) {
        // 1. 创建空白背景 82x34
        this.background = guiHelper.createBlankDrawable(82, 34);

        // 2. 获取 JEI 标准槽位背景
        this.slotBackground = guiHelper.getSlotDrawable();

        // 3. 图标 (使用创造模式物品栏图标)
        this.icon = guiHelper.createDrawableIngredient(VanillaTypes.ITEM_STACK, new ItemStack(HerobrineCompanion.TAB_ICON.get()));

        // 4. 标题
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
                .addIngredients(VanillaTypes.ITEM_STACK, List.of(recipe.getCostA()));

        // 输入 2 (左侧第二个槽位，如果有)
        if (!recipe.getCostB().isEmpty()) {
            builder.addSlot(RecipeIngredientRole.INPUT, 26, 9)
                    .addIngredients(VanillaTypes.ITEM_STACK, List.of(recipe.getCostB()));
        }

        // 输出 (右侧槽位)
        builder.addSlot(RecipeIngredientRole.OUTPUT, 61, 9)
                .addIngredients(VanillaTypes.ITEM_STACK, List.of(recipe.getResult()));
    }

    @Override
    public void draw(MerchantOffer recipe, IRecipeSlotsView recipeSlotsView, GuiGraphics guiGraphics, double mouseX, double mouseY) {
        // 1. 绘制三个槽位背景
        // 槽位背景通常绘制在物品坐标(x,y)的左上角偏移位置
        slotBackground.draw(guiGraphics, 0, 8);
        slotBackground.draw(guiGraphics, 25, 8);
        slotBackground.draw(guiGraphics, 60, 8);

        // 2. 直接用代码画箭头
        Font font = Minecraft.getInstance().font;

        // 方案 A: 简单的 "->" 符号
        // 参数: font, 文本, x, y, 颜色(十六进制), 是否有阴影
        // 0xFF404040 是深灰色 (ARGB格式)
        guiGraphics.drawString(font, "->", 45, 13, 0xFF404040, false);

        // 方案 B (可选): 如果你想要更漂亮的 Unicode 箭头，可以注释掉上面那行，用下面这行:
        // guiGraphics.drawString(font, "➜", 45, 13, 0xFF404040, false);
    }
}