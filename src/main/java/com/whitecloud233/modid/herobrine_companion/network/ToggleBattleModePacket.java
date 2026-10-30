package com.whitecloud233.modid.herobrine_companion.network;

import com.whitecloud233.modid.herobrine_companion.entity.HeroEntity;
import com.whitecloud233.modid.herobrine_companion.entity.companion.BattleModePolicy;
import com.whitecloud233.modid.herobrine_companion.entity.companion.BattleModeService;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.network.NetworkEvent;

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
        PacketDispatch.enqueueServer(context, () -> {
            ServerPlayer serverPlayer = context.getSender();
            if (serverPlayer == null) {
                return;
            }

            Entity entity = serverPlayer.level().getEntity(this.entityId);
            if (!(entity instanceof HeroEntity hero)) {
                return;
            }

            boolean desired = !hero.isBattleModeActive();
            BattleModePolicy.Result result = BattleModeService.changeMode(serverPlayer.getUUID(), hero, desired);
            if (!result.successful()) {
                if (result == BattleModePolicy.Result.NOT_OWNER) {
                    serverPlayer.sendSystemMessage(hero.createNotYourHeroMessage());
                } else if (result == BattleModePolicy.Result.TRUST_TOO_LOW) {
                    serverPlayer.sendSystemMessage(Component.translatable(result.messageKey(),
                            BattleModeService.trustThreshold(), hero.getTrustLevel()));
                } else if (result == BattleModePolicy.Result.COOLDOWN) {
                    serverPlayer.sendSystemMessage(Component.translatable(result.messageKey(),
                            BattleModeService.cooldownRemainingTicks(hero) / 20L + 1L));
                } else {
                    serverPlayer.sendSystemMessage(Component.translatable(result.messageKey()));
                }
                return;
            }

            if (desired) {
                serverPlayer.sendSystemMessage(Component.translatable("message.herobrine_companion.battle_mode_on"));
                int durationSeconds = BattleModeService.durationTicks() / 20;
                if (durationSeconds > 0) {
                    serverPlayer.sendSystemMessage(Component.translatable(
                            "message.herobrine_companion.battle_mode_on_timed", durationSeconds));
                }
            } else {
                serverPlayer.sendSystemMessage(Component.translatable("message.herobrine_companion.battle_mode_off"));
            }
        });
    }
}
