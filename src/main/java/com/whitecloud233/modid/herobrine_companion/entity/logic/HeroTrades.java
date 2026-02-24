package com.whitecloud233.modid.herobrine_companion.entity.logic;

import com.mojang.logging.LogUtils;
import com.whitecloud233.modid.herobrine_companion.HerobrineCompanion;
import com.whitecloud233.modid.herobrine_companion.compat.KubeJS.SafeKubeJSCaller;
import com.whitecloud233.modid.herobrine_companion.entity.HeroEntity;
import com.whitecloud233.modid.herobrine_companion.compat.KubeJS.HerobrineCompanionKubeJSPlugin;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraftforge.fml.ModList;
import org.slf4j.Logger;

public class HeroTrades {

    private static final Logger LOGGER = LogUtils.getLogger();

    public static MerchantOffers getOffers(HeroEntity hero) {
        LOGGER.info("Generating offers for Hero. Trust level: {}", hero.getTrustLevel());
        MerchantOffers offers = new MerchantOffers();
        int trust = hero.getTrustLevel();

        // Level 0:
        offers.add(new MerchantOffer(
            new ItemStack(HerobrineCompanion.CORRUPTED_CODE.get(), 4),
            new ItemStack(Items.ENDER_CHEST, 1),
            Integer.MAX_VALUE, 2, 0.05f
        ));
        offers.add(new MerchantOffer(
                new ItemStack(Items.DIAMOND, 1),
                new ItemStack(Items.EXPERIENCE_BOTTLE, 6),
                Integer.MAX_VALUE, 2, 0.05f
        ));
        offers.add(new MerchantOffer(
            new ItemStack(HerobrineCompanion.VOID_MARROW.get(), 2),
            new ItemStack(Items.BEDROCK, 1),
            Integer.MAX_VALUE, 2, 0.05f
        ));

        // Level 1 (Trust >= 2):
        if (trust >= 2) {
            offers.add(new MerchantOffer(
                new ItemStack(HerobrineCompanion.VOID_MARROW.get(), 4),
                new ItemStack(Items.DIAMOND, 1),
                Integer.MAX_VALUE, 5, 0.05f
            ));
            offers.add(new MerchantOffer(
                new ItemStack(HerobrineCompanion.UNSTABLE_GUNPOWDER.get(), 1),
                new ItemStack(Items.ENDER_PEARL, 4),
                Integer.MAX_VALUE, 5, 0.05f
            ));
        }

        // Level 2 (Trust >= 20):
        if (trust >= 20) {
            offers.add(new MerchantOffer(
                new ItemStack(HerobrineCompanion.UNSTABLE_GUNPOWDER.get(), 2),
                new ItemStack(HerobrineCompanion.GLITCH_FRAGMENT.get(), 1),
                Integer.MAX_VALUE, 10, 0.05f
            ));
            offers.add(new MerchantOffer(
                    new ItemStack(HerobrineCompanion.SOURCE_CODE_FRAGMENT.get(), 2),
                    new ItemStack(Items.TOTEM_OF_UNDYING, 1),
                    Integer.MAX_VALUE, 2, 0.05f
            ));
        }

        // Level 3 (Trust >= 40): Glitch Fragment -> Diamond Block
        if (trust >= 40) {
            offers.add(new MerchantOffer(
                new ItemStack(HerobrineCompanion.GLITCH_FRAGMENT.get(), 2),
                new ItemStack(Items.DIAMOND_BLOCK, 1),
                Integer.MAX_VALUE, 15, 0.05f
            ));
            offers.add(new MerchantOffer(
                new ItemStack(HerobrineCompanion.GLITCH_FRAGMENT.get(), 4),
                new ItemStack(Items.NETHERITE_INGOT, 1),
                Integer.MAX_VALUE, 15, 0.05f
            ));
            offers.add(new MerchantOffer(
                    new ItemStack(HerobrineCompanion.CORRUPTED_CODE.get(), 1),
                    new ItemStack(HerobrineCompanion.VOID_MARROW.get(), 1),
                    new ItemStack(HerobrineCompanion.MEMORY_SHARD.get(), 1),
                    Integer.MAX_VALUE, 15, 0.10f
            ));
            offers.add(new MerchantOffer(
                    new ItemStack(HerobrineCompanion.SOURCE_CODE_FRAGMENT.get(), 1),
                    new ItemStack(HerobrineCompanion.RECALL_STONE.get(), 1),
                    Integer.MAX_VALUE, 30, 0.10f
            ));
        }

        // Level 4 (Trust >= 70): Memory Shard (new rare item)
        if (trust >= 70) {
            offers.add(new MerchantOffer(
                new ItemStack(HerobrineCompanion.UNSTABLE_GUNPOWDER.get(), 4), 
                new ItemStack(HerobrineCompanion.VOID_MARROW.get(), 4),
                new ItemStack(Items.NETHER_STAR, 1),
                Integer.MAX_VALUE, 40, 0.10f 
            ));
            offers.add(new MerchantOffer(
                new ItemStack(HerobrineCompanion.SOURCE_CODE_FRAGMENT.get(), 2),
                new ItemStack(Items.DRAGON_BREATH, 1),
                Integer.MAX_VALUE, 30, 0.10f
            ));
        }

        // --- 核心修改：在此处调用 KubeJS 事件 ---
        // --- 核心修改：安全调用 KubeJS 事件 ---
        if (ModList.get().isLoaded("kubejs")) {
            // 只有在 KubeJS 真实存在时，才去调用辅助类，完美避开类加载报错
            SafeKubeJSCaller.fireEvent(offers, hero);
        }
// -------------------------------------
        return offers;
    }

    public static void onTrade(HeroEntity hero, MerchantOffer offer) {
        offer.increaseUses();
        if (offer.shouldRewardExp()) {
            int xp = offer.getXp();
            hero.increaseTrust(xp);
            hero.resetOffers(); 
        }
    }
}