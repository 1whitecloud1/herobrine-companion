package com.whitecloud233.herobrine_companion.entity;

import com.whitecloud233.herobrine_companion.HerobrineCompanion;
import com.whitecloud233.herobrine_companion.item.LoreFragmentItem;
import net.minecraft.core.component.DataComponents;
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
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.trading.ItemCost;
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
            // 修复：SoundEvents.MUSIC_DISC_11 是 Holder.Reference，需要 .value() 或 .get()
            // 但 playSound 接受 SoundEvent。在 NeoForge/新版本中，SoundEvents 的字段通常是 Holder。
            // 检查 Entity.playSound 签名，通常是 (SoundEvent, float, float)
            // 如果 SoundEvents.MUSIC_DISC_11 是 Holder，则使用 .value()
            this.playSound(SoundEvents.MUSIC_DISC_11.value(), 1.0F, 0.5F);
            
            ItemStack fragment = new ItemStack(HerobrineCompanion.LORE_FRAGMENT.get());
            CompoundTag tag = new CompoundTag();
            tag.putString(LoreFragmentItem.LORE_ID_KEY, "fragment_11");
            fragment.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
            
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
             // 修复：MerchantOffer 构造函数在 1.20.5+ 变更为 (ItemCost, ItemStack, int, int, float)
             // 第一个参数不再是 ItemStack，而是 ItemCost
             this.offers.add(new MerchantOffer(
                new ItemCost(Items.EMERALD, 64),
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
