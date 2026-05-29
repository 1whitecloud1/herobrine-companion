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
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.MerchantOffer;

public class HeroTradeCategory implements IRecipeCategory<MerchantOffer> {
    public static final RecipeType<MerchantOffer> RECIPE_TYPE = RecipeType.create(HerobrineCompanion.MODID, "hero_trade", MerchantOffer.class);

    private final IDrawable background;
    private final IDrawable icon;
    private final IDrawable slotBackground;
    private final Component localizedName;

    public HeroTradeCategory(IGuiHelper guiHelper) {
        this.background = guiHelper.createBlankDrawable(82, 34);
        this.slotBackground = guiHelper.getSlotDrawable();
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
    public int getWidth() {
        return background.getWidth();
    }

    @Override
    public int getHeight() {
        return background.getHeight();
    }

    @Override
    public IDrawable getIcon() {
        return icon;
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, MerchantOffer recipe, IFocusGroup focuses) {
        builder.addSlot(RecipeIngredientRole.INPUT, 1, 9)
                .addIngredients(VanillaTypes.ITEM_STACK, java.util.List.of(recipe.getCostA()));

        if (!recipe.getCostB().isEmpty()) {
            builder.addSlot(RecipeIngredientRole.INPUT, 26, 9)
                    .addIngredients(VanillaTypes.ITEM_STACK, java.util.List.of(recipe.getCostB()));
        }

        builder.addSlot(RecipeIngredientRole.OUTPUT, 61, 9)
                .addIngredients(VanillaTypes.ITEM_STACK, java.util.List.of(recipe.getResult()));
    }

    @Override
    public void draw(MerchantOffer recipe, IRecipeSlotsView recipeSlotsView, GuiGraphics guiGraphics, double mouseX, double mouseY) {
        background.draw(guiGraphics, 0, 0);
        slotBackground.draw(guiGraphics, 0, 8);
        slotBackground.draw(guiGraphics, 25, 8);
        slotBackground.draw(guiGraphics, 60, 8);

        Font font = Minecraft.getInstance().font;
        guiGraphics.drawString(font, "->", 45, 13, 0xFF404040, false);
    }
}
