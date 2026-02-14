package com.whitecloud233.modid.herobrine_companion.entity;

import com.whitecloud233.modid.herobrine_companion.HerobrineCompanion;
import com.whitecloud233.modid.herobrine_companion.item.LoreFragmentItem;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.Level;

public class GlitchVillagerEntity extends Villager {

    public GlitchVillagerEntity(EntityType<? extends Villager> entityType, Level level) {
        super(entityType, level);
        this.setCustomName(Component.literal("N."));
        this.setCustomNameVisible(true);
    }

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        InteractionResult result = super.mobInteract(player, hand);

        if (!this.level().isClientSide) {
            // 1.20.1: SoundEvents.MUSIC_DISC_11 is a SoundEvent
            this.playSound(SoundEvents.MUSIC_DISC_11, 1.0F, 0.5F);

            ItemStack fragment = new ItemStack(HerobrineCompanion.LORE_FRAGMENT.get());
            CompoundTag tag = new CompoundTag();
            tag.putString(LoreFragmentItem.LORE_ID_KEY, "fragment_11");
            // 1.20.1: Use setTag
            fragment.setTag(tag);

            this.spawnAtLocation(fragment);

            this.discard();
        }
        return result;
    }

    @Override
    public MerchantOffers getOffers() {
        if (this.offers == null) {
            this.offers = new MerchantOffers();
        }
        if (this.offers.isEmpty()) {
            // 1.20.1: MerchantOffer(ItemStack baseCostA, ItemStack result, int maxUses, int xp, float priceMultiplier)
            this.offers.add(new MerchantOffer(
                    new ItemStack(Items.EMERALD, 64),
                    new ItemStack(Items.BARRIER),
                    1, 0, 0.0f
            ));
        }
        return this.offers;
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide) {
            this.setYHeadRot(this.getYHeadRot() + 45.0F);
            this.setXRot(this.random.nextFloat() * 360.0F);
        }
    }
}
