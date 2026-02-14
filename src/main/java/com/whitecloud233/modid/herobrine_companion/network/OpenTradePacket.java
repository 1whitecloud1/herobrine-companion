package com.whitecloud233.modid.herobrine_companion.network;

import com.whitecloud233.modid.herobrine_companion.entity.HeroEntity;
import com.whitecloud233.modid.herobrine_companion.world.inventory.HeroMerchantMenu;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.network.NetworkEvent;

import java.util.OptionalInt;
import java.util.function.Supplier;

public class OpenTradePacket {
    private final int entityId;

    public OpenTradePacket(int entityId) {
        this.entityId = entityId;
    }

    public OpenTradePacket(FriendlyByteBuf buf) {
        this.entityId = buf.readVarInt();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(this.entityId);
    }

    public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player != null) {
                Entity entity = player.level().getEntity(this.entityId);

                if (entity instanceof HeroEntity hero) {
                    if (player.distanceToSqr(hero) < 64.0D) {
                        hero.setTradingPlayer(player);
                        hero.getOffers(); // Ensure offers are generated
                        
                        // Open the menu and capture the container ID
                        OptionalInt containerId = player.openMenu(new SimpleMenuProvider((id, inventory, p) -> new HeroMerchantMenu(id, inventory, hero), hero.getDisplayName()));
                        
                        // CRITICAL FIX: Send the merchant offers to the client immediately after opening the menu
                        if (containerId.isPresent()) {
                            player.sendMerchantOffers(containerId.getAsInt(), hero.getOffers(), 1, hero.getVillagerXp(), hero.showProgressBar(), hero.canRestock());
                        }
                    }
                }
            }
        });
        context.setPacketHandled(true);
    }
}
