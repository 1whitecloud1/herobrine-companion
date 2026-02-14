package com.whitecloud233.herobrine_companion.network;

import com.whitecloud233.herobrine_companion.HerobrineCompanion;
import com.whitecloud233.herobrine_companion.item.HeroSummonItem;
import com.whitecloud233.herobrine_companion.world.inventory.HeroContractMenu;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record ContractPacket() implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<ContractPacket> TYPE = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(HerobrineCompanion.MODID, "contract"));
    
    public static final StreamCodec<ByteBuf, ContractPacket> STREAM_CODEC = StreamCodec.unit(new ContractPacket());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(ContractPacket payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer serverPlayer) {
                if (serverPlayer.containerMenu instanceof HeroContractMenu menu) {
                    ItemStack slot0 = menu.container.getItem(0);
                    ItemStack slot1 = menu.container.getItem(1);

                    if (slot0.is(Items.NETHER_STAR) && slot1.is(Items.DRAGON_BREATH)) {
                        // Consume items
                        slot0.shrink(1);
                        slot1.shrink(1);

                        // Find the Hero Summon Item in player's hand
                        ItemStack heldItem = serverPlayer.getMainHandItem();
                        if (heldItem.getItem() instanceof HeroSummonItem) {
                            // Bind item
                            CompoundTag tag = new CompoundTag();
                            tag.putBoolean("BoundHero", true);
                            tag.putString("OwnerName", serverPlayer.getName().getString());
                            heldItem.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));

                            // Set fancy name
                            heldItem.set(DataComponents.CUSTOM_NAME, Component.translatable("item.herobrine_companion.bound_shelter_name", serverPlayer.getName().getString()));
                            
                            // Play sound
                            serverPlayer.level().playSound(null, serverPlayer.blockPosition(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 1.0f, 1.0f);
                            
                            // Close menu
                            serverPlayer.closeContainer();
                        }
                    }
                }
            }
        });
    }
}
