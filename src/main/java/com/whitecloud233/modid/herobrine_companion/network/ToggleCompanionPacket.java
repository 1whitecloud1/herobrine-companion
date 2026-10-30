package com.whitecloud233.modid.herobrine_companion.network;

import com.whitecloud233.modid.herobrine_companion.entity.HeroEntity;
import com.whitecloud233.modid.herobrine_companion.entity.companion.CompanionModePolicy;
import com.whitecloud233.modid.herobrine_companion.entity.companion.CompanionModeService;
import com.whitecloud233.modid.herobrine_companion.util.HeroMenuValidity;
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
        PacketDispatch.enqueueServer(context, () -> {
            ServerPlayer serverPlayer = context.getSender();
            if (serverPlayer != null) {
                Entity entity = serverPlayer.level().getEntity(this.entityId);
                if (entity instanceof HeroEntity hero) {
                    if (!serverPlayer.isAlive() || !HeroMenuValidity.isHeroInPlayerLoadedArea(hero, serverPlayer)) {
                        serverPlayer.sendSystemMessage(Component.translatable("message.herobrine_companion.companion_too_far"));
                        return;
                    }
                    boolean desiredMode = !hero.isCompanionMode();
                    CompanionModePolicy.Result result = CompanionModeService.changeMode(serverPlayer.getUUID(), hero, desiredMode);
                    if (result.successful()) {
                        String key = desiredMode ? "message.herobrine_companion.companion_on" : "message.herobrine_companion.companion_off";
                        serverPlayer.sendSystemMessage(Component.translatable(key));
                    } else if (result == CompanionModePolicy.Result.NOT_OWNER) {
                        serverPlayer.sendSystemMessage(hero.createNotYourHeroMessage());
                    } else {
                        serverPlayer.sendSystemMessage(Component.translatable(result.messageKey()));
                    }
                }
            }
        });
    }
}
