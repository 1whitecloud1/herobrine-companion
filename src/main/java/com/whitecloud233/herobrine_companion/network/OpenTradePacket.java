package com.whitecloud233.herobrine_companion.network;

import com.whitecloud233.herobrine_companion.entity.HeroEntity;
import com.whitecloud233.herobrine_companion.world.inventory.HeroMerchantMenu;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.OptionalInt;

public record OpenTradePacket(int entityId) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<OpenTradePacket> TYPE = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("herobrine_companion", "open_trade"));
    
    public static final StreamCodec<FriendlyByteBuf, OpenTradePacket> STREAM_CODEC = StreamCodec.composite(
        net.minecraft.network.codec.ByteBufCodecs.VAR_INT,
        OpenTradePacket::entityId,
        OpenTradePacket::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(OpenTradePacket payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                Entity entity = player.level().getEntity(payload.entityId());

                if (entity instanceof HeroEntity hero) {
                    if (player.distanceToSqr(hero) < 64.0D) {
                        hero.setTradingPlayer(player);
                        hero.getOffers();
                        
                        // Open the custom menu
                        OptionalInt containerId = player.openMenu(new SimpleMenuProvider((id, inventory, p) -> new HeroMerchantMenu(id, inventory, hero), hero.getDisplayName()));
                        
                        if (containerId.isPresent()) {
                            player.sendMerchantOffers(containerId.getAsInt(), hero.getOffers(), 0, hero.getVillagerXp(), hero.showProgressBar(), false);
                        }
                    }
                }
            }
        });
    }
}
