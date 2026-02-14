package com.whitecloud233.modid.herobrine_companion.compat.jei;

import com.mojang.logging.LogUtils;
import com.whitecloud233.modid.herobrine_companion.HerobrineCompanion;
import com.whitecloud233.modid.herobrine_companion.entity.HeroEntity;
import com.whitecloud233.modid.herobrine_companion.entity.logic.HeroRewards;
import com.whitecloud233.modid.herobrine_companion.entity.logic.HeroTrades;
import com.whitecloud233.modid.herobrine_companion.event.ModEvents;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;

@JeiPlugin
public class HerobrineJeiPlugin implements IModPlugin {
    private static final Logger LOGGER = LogUtils.getLogger();

    @Override
    public ResourceLocation getPluginUid() {
        return ResourceLocation.fromNamespaceAndPath("herobrine_companion", "jei_plugin");
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        registration.addRecipeCategories(new HeroTradeCategory(registration.getJeiHelpers().getGuiHelper()));
        registration.addRecipeCategories(new HeroRewardCategory(registration.getJeiHelpers().getGuiHelper()));
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        // 1. 注册交易配方
        if (Minecraft.getInstance().level != null) {
            try {
                HeroEntity dummyHero = new HeroEntity(ModEvents.HERO.get(), Minecraft.getInstance().level);
                dummyHero.setTrustLevel(100); 
                
                MerchantOffers offers = HeroTrades.getOffers(dummyHero);
                List<MerchantOffer> offerList = new ArrayList<>(offers);

                registration.addRecipes(HeroTradeCategory.RECIPE_TYPE, offerList);
            } catch (Exception e) {
                LOGGER.error("Failed to generate Herobrine trades for JEI", e);
            }
        }

        // 2. 注册奖励配方
        // 确保奖励列表已初始化
        if (HeroRewards.REWARDS.isEmpty()) {
            LOGGER.warn("HeroRewards list is empty during JEI registration! Forcing reset.");
            HeroRewards.reset();
        }
        
        LOGGER.info("Registering {} Hero Rewards to JEI.", HeroRewards.REWARDS.size());
        registration.addRecipes(HeroRewardCategory.RECIPE_TYPE, HeroRewards.REWARDS);
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        // 交易配方催化剂: TAB_ICON
        registration.addRecipeCatalyst(new ItemStack(HerobrineCompanion.TAB_ICON.get()), HeroTradeCategory.RECIPE_TYPE);
        
        // 奖励配方催化剂: TAB_ICON 和 HERO_SHELTER
        registration.addRecipeCatalyst(new ItemStack(HerobrineCompanion.TAB_ICON.get()), HeroRewardCategory.RECIPE_TYPE);
        registration.addRecipeCatalyst(new ItemStack(HerobrineCompanion.HERO_SHELTER.get()), HeroRewardCategory.RECIPE_TYPE);
    }
}
