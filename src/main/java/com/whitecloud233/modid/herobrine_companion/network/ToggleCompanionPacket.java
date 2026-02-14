package com.whitecloud233.modid.herobrine_companion.network;

import com.whitecloud233.modid.herobrine_companion.entity.HeroEntity;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class ToggleCompanionPacket {
    private final int entityId;

    public ToggleCompanionPacket(int entityId) {
        this.entityId = entityId;
    }

    public ToggleCompanionPacket(FriendlyByteBuf buf) {
        this.entityId = buf.readVarInt();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(this.entityId);
    }

    public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer serverPlayer = context.getSender();
            if (serverPlayer != null) {
                Entity entity = serverPlayer.level().getEntity(this.entityId);
                if (entity instanceof HeroEntity hero) {
                    if (hero.getTrustLevel() >= 50) {
                        boolean newState = !hero.isCompanionMode();
                        hero.setCompanionMode(newState);
                        
                        String msgKey = newState ? "message.herobrine_companion.companion_on" : "message.herobrine_companion.companion_off";
                        serverPlayer.sendSystemMessage(Component.translatable(msgKey));
                    }
                }
            }
        });
        context.setPacketHandled(true);
    }
}
