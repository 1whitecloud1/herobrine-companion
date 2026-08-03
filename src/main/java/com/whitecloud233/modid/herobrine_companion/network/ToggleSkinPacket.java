package com.whitecloud233.modid.herobrine_companion.network;

import com.whitecloud233.modid.herobrine_companion.entity.HeroEntity;
import com.whitecloud233.modid.herobrine_companion.entity.logic.data.HeroWorldData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class ToggleSkinPacket {
    private final int entityId;
    private final int skinVariant;
    private final String customSkinName;
    private final byte[] customSkinData;

    public ToggleSkinPacket(int entityId, int skinVariant) {
        this(entityId, skinVariant, "", new byte[0]);
    }

    public ToggleSkinPacket(int entityId, int skinVariant, String customSkinName) {
        this(entityId, skinVariant, customSkinName, new byte[0]);
    }

    public ToggleSkinPacket(int entityId, int skinVariant, String customSkinName, byte[] customSkinData) {
        this.entityId = entityId;
        this.skinVariant = skinVariant;
        this.customSkinName = customSkinName;
        this.customSkinData = customSkinData != null ? customSkinData : new byte[0];
    }

    public ToggleSkinPacket(FriendlyByteBuf buf) {
        this.entityId = buf.readInt();
        this.skinVariant = buf.readInt();
        this.customSkinName = buf.readUtf();
        this.customSkinData = buf.readByteArray();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeInt(this.entityId);
        buf.writeInt(this.skinVariant);
        buf.writeUtf(this.customSkinName);
        buf.writeByteArray(this.customSkinData);
    }

    public void handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        PacketDispatch.enqueueServer(context, () -> {
            ServerPlayer player = context.getSender();
            if (player != null) {
                Entity entity = player.level().getEntity(this.entityId);
                if (entity instanceof HeroEntity hero) {
                    hero.setSkinVariant(this.skinVariant);
                    if (hero.getOwnerUUID() != null) {
                        HeroWorldData data = HeroWorldData.get(player.serverLevel());
                        if (this.skinVariant == HeroEntity.SKIN_CUSTOM) {
                            data.setCustomSkinData(hero.getOwnerUUID(), this.customSkinData);
                        } else {
                            data.setCustomSkinData(hero.getOwnerUUID(), new byte[0]);
                        }
                    }

                    if (this.skinVariant == HeroEntity.SKIN_CUSTOM) {
                        hero.setCustomSkinName(this.customSkinName);
                    } else {
                        hero.setCustomSkinName("");
                    }

                    PacketHandler.sendToTracking(new SyncHeroCosmeticsPacket(hero), hero);
                    PacketHandler.sendToPlayer(new SyncHeroCosmeticsPacket(hero), player);
                }
            }
        });
    }
}