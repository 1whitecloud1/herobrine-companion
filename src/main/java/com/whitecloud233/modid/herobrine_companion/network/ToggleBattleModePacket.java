package com.whitecloud233.modid.herobrine_companion.network;

import com.whitecloud233.modid.herobrine_companion.entity.HeroEntity;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.network.NetworkEvent;

import java.util.Objects;
import java.util.function.Supplier;

public class ToggleBattleModePacket {
    private final int entityId;

    public ToggleBattleModePacket(int entityId) {
        this.entityId = entityId;
    }

    public ToggleBattleModePacket(FriendlyByteBuf buf) {
        this.entityId = buf.readVarInt();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(this.entityId);
    }

    public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer serverPlayer = context.getSender();
            if (serverPlayer == null) {
                return;
            }

            Entity entity = serverPlayer.level().getEntity(this.entityId);
            if (!(entity instanceof HeroEntity hero)) {
                return;
            }

            if (hero.getOwnerUUID() != null && !Objects.equals(hero.getOwnerUUID(), serverPlayer.getUUID())) {
                serverPlayer.sendSystemMessage(hero.createNotYourHeroMessage());
                return;
            }

            if (hero.getEntityData().get(HeroEntity.IS_CHALLENGE_ACTIVE)) {
                serverPlayer.sendSystemMessage(Component.translatable("message.herobrine_companion.battle_mode_blocked_challenge"));
                return;
            }

            boolean newState = !hero.isBattleModeActive();
            hero.setBattleModeActiveFrom("ToggleBattleModePacket.handle", newState);

            String key = newState
                    ? "message.herobrine_companion.battle_mode_on"
                    : "message.herobrine_companion.battle_mode_off";
            serverPlayer.sendSystemMessage(Component.translatable(key));
        });
        context.setPacketHandled(true);
    }
}

