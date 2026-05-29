package com.whitecloud233.herobrine_companion.compat.jei;

import com.whitecloud233.herobrine_companion.HerobrineCompanion;
import com.whitecloud233.herobrine_companion.event.HeroRewards;
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
import net.minecraft.world.item.Items;

public class HeroRewardCategory implements IRecipeCategory<HeroRewards.Reward> {
    public static final RecipeType<HeroRewards.Reward> RECIPE_TYPE = RecipeType.create(HerobrineCompanion.MODID, "hero_reward", HeroRewards.Reward.class);

    private final IDrawable background;
    private final IDrawable icon;
    private final Component localizedName;
    private final IDrawable slotDrawable;

    public HeroRewardCategory(IGuiHelper guiHelper) {
        this.background = guiHelper.createBlankDrawable(120, 60);
        this.slotDrawable = guiHelper.getSlotDrawable();
        this.icon = guiHelper.createDrawableIngredient(VanillaTypes.ITEM_STACK, new ItemStack(Items.CHEST));
        this.localizedName = Component.translatable("gui.herobrine_companion.rewards_title");
    }

    @Override
    public RecipeType<HeroRewards.Reward> getRecipeType() {
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
    public void setRecipe(IRecipeLayoutBuilder builder, HeroRewards.Reward recipe, IFocusGroup focuses) {
        builder.addSlot(RecipeIngredientRole.INPUT, 5, 5)
                .addIngredients(VanillaTypes.ITEM_STACK, java.util.List.of(new ItemStack(HerobrineCompanion.TAB_ICON.get())));

        int x = 5;
        int y = 35;
        for (int i = 0; i < recipe.items.size() && i < 5; i++) {
            builder.addSlot(RecipeIngredientRole.OUTPUT, x + i * 20, y)
                    .addIngredients(VanillaTypes.ITEM_STACK, java.util.List.of(recipe.items.get(i)));
        }
    }

    @Override
    public void draw(HeroRewards.Reward recipe, IRecipeSlotsView recipeSlotsView, GuiGraphics guiGraphics, double mouseX, double mouseY) {
        background.draw(guiGraphics, 0, 0);

        Font font = Minecraft.getInstance().font;
        slotDrawable.draw(guiGraphics, 4, 4);

        Component trustText = Component.translatable("gui.herobrine_companion.reward_tooltip", recipe.requiredTrust);
        guiGraphics.drawString(font, trustText, 30, 10, 0xFF404040, false);

        int x = 4;
        int y = 34;
        for (int i = 0; i < recipe.items.size() && i < 5; i++) {
            slotDrawable.draw(guiGraphics, x + i * 20, y);
        }
    }
}
