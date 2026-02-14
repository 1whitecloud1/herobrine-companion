package com.whitecloud233.modid.herobrine_companion.network;

import com.whitecloud233.modid.herobrine_companion.item.HeroSummonItem;
import com.whitecloud233.modid.herobrine_companion.world.inventory.HeroContractMenu;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class ContractPacket {

    public ContractPacket() {}

    public ContractPacket(FriendlyByteBuf buf) {}

    public void encode(FriendlyByteBuf buf) {}

    public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer serverPlayer = context.getSender();
            if (serverPlayer != null) {
                if (serverPlayer.containerMenu instanceof HeroContractMenu menu) {
                    ItemStack slot0 = menu.container.getItem(0);
                    ItemStack slot1 = menu.container.getItem(1);

                    if (slot0.getItem() == Items.NETHER_STAR && slot1.getItem() == Items.DRAGON_BREATH) {
                        // Consume items
                        slot0.shrink(1);
                        slot1.shrink(1);

                        // Find the Hero Summon Item in player's hand
                        ItemStack heldItem = serverPlayer.getMainHandItem();
                        if (heldItem.getItem() instanceof HeroSummonItem) {
                            // Bind item
                            CompoundTag tag = heldItem.getOrCreateTag();
                            tag.putBoolean("BoundHero", true);
                            tag.putString("OwnerName", serverPlayer.getName().getString());
                            
                            // Set fancy name
                            heldItem.setHoverName(Component.translatable("item.herobrine_companion.bound_shelter_name", serverPlayer.getName().getString()));
                            
                            // Play sound
                            serverPlayer.level().playSound(null, serverPlayer.blockPosition(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 1.0f, 1.0f);
                            
                            // Close menu
                            serverPlayer.closeContainer();
                        }
                    }
                }
            }
        });
        context.setPacketHandled(true);
    }
}
