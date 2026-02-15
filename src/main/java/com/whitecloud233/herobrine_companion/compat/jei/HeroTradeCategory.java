package com.whitecloud233.herobrine_companion.compat.jei;

import com.whitecloud233.herobrine_companion.HerobrineCompanion;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.client.Minecraft; // 需要导入 Minecraft
import net.minecraft.client.gui.Font;    // 需要导入 Font
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.MerchantOffer;

public class HeroTradeCategory implements IRecipeCategory<MerchantOffer> {
    public static final RecipeType<MerchantOffer> RECIPE_TYPE = RecipeType.create(HerobrineCompanion.MODID, "hero_trade", MerchantOffer.class);

    private final IDrawable background;
    private final IDrawable icon;
    private final IDrawable slotBackground;
    // private final IDrawable arrow; // 不需要这个变量了，我们直接画
    private final Component localizedName;

    public HeroTradeCategory(IGuiHelper guiHelper) {
        // 1. 创建空白背景 82x34
        this.background = guiHelper.createBlankDrawable(82, 34);

        // 2. 槽位背景
        this.slotBackground = guiHelper.getSlotDrawable();

        // 3. 箭头：不需要加载图片了！

        // 4. 图标
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
        // 输入 1
        builder.addSlot(RecipeIngredientRole.INPUT, 1, 9)
                .addIngredients(VanillaTypes.ITEM_STACK, java.util.List.of(recipe.getCostA()));

        // 输入 2
        if (!recipe.getCostB().isEmpty()) {
            builder.addSlot(RecipeIngredientRole.INPUT, 26, 9)
                    .addIngredients(VanillaTypes.ITEM_STACK, java.util.List.of(recipe.getCostB()));
        }

        // 输出
        builder.addSlot(RecipeIngredientRole.OUTPUT, 61, 9)
                .addIngredients(VanillaTypes.ITEM_STACK, java.util.List.of(recipe.getResult()));
    }

    @Override
    public void draw(MerchantOffer recipe, IRecipeSlotsView recipeSlotsView, GuiGraphics guiGraphics, double mouseX, double mouseY) {
        // 1. 绘制三个槽位背景
        slotBackground.draw(guiGraphics, 0, 8);
        slotBackground.draw(guiGraphics, 25, 8);
        slotBackground.draw(guiGraphics, 60, 8);

        // 2. 【核心修改】在这里直接用代码画箭头！
        // 方案 A: 画一个字符串箭头 "->"
        Font font = Minecraft.getInstance().font;

        // drawString 参数: (font, 文本, x, y, 颜色, 是否有阴影)
        // 0xFF404040 是深灰色，类似 GUI 里的文字颜色
        // 坐标 (45, 13) 是大概居中的位置，你可以微调
        guiGraphics.drawString(font, "->", 45, 13, 0xFF404040, false);

        // 方案 B: (如果你喜欢更高级的) 也可以画 Unicode 箭头 "➜"
        // guiGraphics.drawString(font, "➜", 45, 13, 0xFF404040, false);

        // 方案 C: (如果你想画一个矩形条当箭头)
        // fill 参数: (x1, y1, x2, y2, color)
        // guiGraphics.fill(43, 16, 58, 18, 0xFF808080); // 画一条灰色的横线
    }
}