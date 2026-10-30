package com.whitecloud233.modid.herobrine_companion.guide;

import com.whitecloud233.modid.herobrine_companion.entity.HeroEntity;
import com.whitecloud233.modid.herobrine_companion.entity.ai.learning.SimpleNeuralNetwork;
import com.whitecloud233.modid.herobrine_companion.util.HeroMenuValidity;
import com.whitecloud233.modid.herobrine_companion.world.structure.ModStructures;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.items.ItemHandlerHelper;
import net.minecraftforge.items.wrapper.PlayerMainInvWrapper;

/** Server-side, once-per-player delivery after the Herobrine panel opens. */
public final class CompanionGuideService {
    public static final String RECEIVED_TAG = "HasReceivedCompanionGuide";
    private static final ResourceLocation GUIDE_ITEM = new ResourceLocation("patchouli", "guide_book");
    private static final String BOOK_ID = "herobrine_companion:companion_guide";

    private CompanionGuideService() { }

    public static boolean grantOnFirstOpen(ServerPlayer player, int heroId) {
        if (player == null || !player.isAlive() || player.getPersistentData().getBoolean(RECEIVED_TAG)) {
            return false;
        }
        if (!(player.level().getEntity(heroId) instanceof HeroEntity hero)
                || !HeroMenuValidity.isHeroInPlayerLoadedArea(hero, player) || !player.getUUID().equals(hero.getOwnerUUID())
                || hero.isChallengeActiveState()
                || hero.level().dimension() == ModStructures.END_RING_DIMENSION_KEY
                || hero.getHeroBrain().getState() == SimpleNeuralNetwork.MindState.JUDGE) {
            return false;
        }

        // Registry lookup keeps Patchouli optional and avoids linking its classes on the server.
        Item item = ForgeRegistries.ITEMS.getValue(GUIDE_ITEM);
        if (item == null || item == Items.AIR) {
            return false;
        }
        ItemStack guide = new ItemStack(item);
        guide.getOrCreateTag().putString("patchouli:book", BOOK_ID);
        // Inventory.add discards overflow in creative mode; use the inventory handler in both modes.
        ItemStack remainder = ItemHandlerHelper.insertItemStacked(new PlayerMainInvWrapper(player.getInventory()), guide, false);
        if (!remainder.isEmpty() && player.drop(remainder, false) == null) {
            return false;
        }
        player.getPersistentData().putBoolean(RECEIVED_TAG, true);
        player.inventoryMenu.broadcastChanges();
        return true;
    }
}
