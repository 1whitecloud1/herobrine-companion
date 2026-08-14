package com.whitecloud233.herobrine_companion.network;

import com.whitecloud233.herobrine_companion.HerobrineCompanion;
import com.whitecloud233.herobrine_companion.entity.HeroEntity;
import com.whitecloud233.herobrine_companion.entity.logic.data.HeroWorldData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public class ToggleSkinPacket implements CustomPacketPayload {
    public static final Type<ToggleSkinPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(HerobrineCompanion.MODID, "toggle_skin"));

    public static final StreamCodec<FriendlyByteBuf, ToggleSkinPacket> STREAM_CODEC =
            StreamCodec.ofMember(ToggleSkinPacket::encode, ToggleSkinPacket::new);

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

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(ToggleSkinPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                Entity entity = player.level().getEntity(packet.entityId);
                if (entity instanceof HeroEntity hero) {
                    hero.setSkinVariant(packet.skinVariant);
                    if (hero.getOwnerUUID() != null) {
                        HeroWorldData data = HeroWorldData.get(player.serverLevel());
                        if (packet.skinVariant == HeroEntity.SKIN_CUSTOM) {
                            data.setCustomSkinData(hero.getOwnerUUID(), packet.customSkinData);
                        } else {
                            data.setCustomSkinData(hero.getOwnerUUID(), new byte[0]);
                        }
                    }

                    if (packet.skinVariant == HeroEntity.SKIN_CUSTOM) {
                        hero.setCustomSkinName(packet.customSkinName);
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