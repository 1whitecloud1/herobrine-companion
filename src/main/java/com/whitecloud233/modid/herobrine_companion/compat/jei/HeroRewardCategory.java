package com.whitecloud233.modid.herobrine_companion.compat.jei;

import com.whitecloud233.modid.herobrine_companion.HerobrineCompanion;
import com.whitecloud233.modid.herobrine_companion.entity.logic.HeroRewards;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
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
        // 增加高度以容纳 Input 槽位
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
    public IDrawable getBackground() {
        return background;
    }

    @Override
    public IDrawable getIcon() {
        return icon;
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, HeroRewards.Reward recipe, IFocusGroup focuses) {
        // 添加一个 Input 槽位，显示 TAB_ICON，代表“来源”
        // 这样逻辑上就是：Herobrine (Input) -> Reward (Output)
        // 虽然实际上不需要消耗 Herobrine，但这在 JEI 中很常见
        builder.addSlot(RecipeIngredientRole.INPUT, 5, 5)
                .addIngredients(VanillaTypes.ITEM_STACK, java.util.List.of(new ItemStack(HerobrineCompanion.TAB_ICON.get())));

        // 显示奖励物品 (Output)
        int x = 5;
        int y = 35;
        for (int i = 0; i < recipe.items.size(); i++) {
            if (i >= 5) break;
            builder.addSlot(RecipeIngredientRole.OUTPUT, x + i * 20, y)
                    .addIngredients(VanillaTypes.ITEM_STACK, java.util.List.of(recipe.items.get(i)));
        }
    }

    @Override
    public void draw(HeroRewards.Reward recipe, mezz.jei.api.gui.ingredient.IRecipeSlotsView recipeSlotsView, GuiGraphics guiGraphics, double mouseX, double mouseY) {
        Font font = Minecraft.getInstance().font;
        
        // 绘制 Input 槽位背景
        this.slotDrawable.draw(guiGraphics, 4, 4);
        
        // 绘制信任度文本 (向右移动一点)
        Component trustText = Component.translatable("gui.herobrine_companion.reward_tooltip", recipe.requiredTrust);
        guiGraphics.drawString(font, trustText, 30, 10, 0xFF404040, false);
        
        // 绘制 Output 槽位背景
        int x = 4;
        int y = 34;
        for (int i = 0; i < recipe.items.size(); i++) {
            if (i >= 5) break;
            this.slotDrawable.draw(guiGraphics, x + i * 20, y);
        }
    }
}
