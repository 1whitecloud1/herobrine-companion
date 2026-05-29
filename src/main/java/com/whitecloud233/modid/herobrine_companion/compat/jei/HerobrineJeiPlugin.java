package com.whitecloud233.modid.herobrine_companion.compat.jei;

import com.mojang.logging.LogUtils;
import com.whitecloud233.modid.herobrine_companion.HerobrineCompanion;
import com.whitecloud233.modid.herobrine_companion.entity.HeroEntity;
import com.whitecloud233.modid.herobrine_companion.event.HeroRewards;
import com.whitecloud233.modid.herobrine_companion.event.HeroTrades;
import com.whitecloud233.modid.herobrine_companion.event.ModEvents;
import com.whitecloud233.modid.herobrine_companion.item.LoreFragmentItem;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
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
        return new ResourceLocation("herobrine_companion", "jei_plugin");
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
        registerGhostDropInfo(registration);
        registerTreasureInfo(registration);
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        // 交易配方催化剂: TAB_ICON
        registration.addRecipeCatalyst(new ItemStack(HerobrineCompanion.TAB_ICON.get()), HeroTradeCategory.RECIPE_TYPE);
        
        // 奖励配方催化剂: TAB_ICON 和 HERO_SHELTER
        registration.addRecipeCatalyst(new ItemStack(HerobrineCompanion.TAB_ICON.get()), HeroRewardCategory.RECIPE_TYPE);
        registration.addRecipeCatalyst(new ItemStack(HerobrineCompanion.HERO_SHELTER.get()), HeroRewardCategory.RECIPE_TYPE);
    }

    private static void registerGhostDropInfo(IRecipeRegistration registration) {
        registration.addItemStackInfo(
                new ItemStack(HerobrineCompanion.CORRUPTED_CODE.get()),
                Component.translatable("jei.herobrine_companion.drop.from", Component.translatable("entity.herobrine_companion.ghost_zombie")),
                Component.translatable("jei.herobrine_companion.drop.amount.1_2_looting")
        );

        registration.addItemStackInfo(
                new ItemStack(HerobrineCompanion.VOID_MARROW.get()),
                Component.translatable("jei.herobrine_companion.drop.from", Component.translatable("entity.herobrine_companion.ghost_skeleton")),
                Component.translatable("jei.herobrine_companion.drop.amount.1_2_looting")
        );

        registration.addItemStackInfo(
                new ItemStack(HerobrineCompanion.UNSTABLE_GUNPOWDER.get()),
                Component.translatable("jei.herobrine_companion.drop.from", Component.translatable("entity.herobrine_companion.ghost_creeper")),
                Component.translatable("jei.herobrine_companion.drop.amount.1_2_looting")
        );

        registration.addItemStackInfo(
                new ItemStack(HerobrineCompanion.SOURCE_CODE_FRAGMENT.get()),
                Component.translatable("jei.herobrine_companion.drop.from", Component.translatable("entity.herobrine_companion.ghost_steve"))
        );

        registration.addItemStackInfo(
                new ItemStack(HerobrineCompanion.GLITCH_FRAGMENT.get()),
                Component.translatable("jei.herobrine_companion.drop.from", Component.translatable("jei.herobrine_companion.drop.ghost_trio")),
                Component.translatable("jei.herobrine_companion.drop.chance.5")
        );

        registration.addItemStackInfo(
                createLoreFragmentStack("fragment_5"),
                Component.translatable("jei.herobrine_companion.drop.from", Component.translatable("jei.herobrine_companion.drop.ghost_trio")),
                Component.translatable("jei.herobrine_companion.drop.fragment_5_only"),
                Component.translatable("jei.herobrine_companion.drop.chance.10")
        );
    }

    private static ItemStack createLoreFragmentStack(String fragmentId) {
        ItemStack stack = new ItemStack(HerobrineCompanion.LORE_FRAGMENT.get());
        stack.getOrCreateTag().putString(LoreFragmentItem.LORE_ID_KEY, fragmentId);
        return stack;
    }

    private static void registerTreasureInfo(IRecipeRegistration registration) {
        registration.addItemStackInfo(
                new ItemStack(HerobrineCompanion.ETERNAL_KEY.get()),
                Component.translatable("jei.herobrine_companion.source.from_chests"),
                Component.translatable("jei.herobrine_companion.source.eternal_key_chests")
        );
    }
}
