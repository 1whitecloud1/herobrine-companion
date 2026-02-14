package com.whitecloud233.modid.herobrine_companion.network;

import com.whitecloud233.modid.herobrine_companion.entity.HeroEntity;
import com.whitecloud233.modid.herobrine_companion.entity.logic.HeroQuestHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class RequestActionPacket {
    private final int entityId;
    private final int questId;
    private final Action action;

    public RequestActionPacket(int entityId, int questId, Action action) {
        this.entityId = entityId;
        this.questId = questId;
        this.action = action;
    }

    public RequestActionPacket(FriendlyByteBuf buf) {
        this.entityId = buf.readInt();
        this.questId = buf.readInt();
        this.action = buf.readEnum(Action.class);
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeInt(this.entityId);
        buf.writeInt(this.questId);
        buf.writeEnum(this.action);
    }

    public static void handle(RequestActionPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player != null) {
                Entity entity = player.level().getEntity(packet.entityId);
                if (entity instanceof HeroEntity hero) {
                    if (packet.action == Action.ACCEPT) {
                        HeroQuestHandler.startQuest(hero, player, packet.questId);
                    } else if (packet.action == Action.CANCEL) {
                        HeroQuestHandler.cancelQuest(hero, player);
                    }
                }
            }
        });
        context.setPacketHandled(true);
    }

    public enum Action {
        ACCEPT,
        COMPLETE,
        CANCEL
    }
}