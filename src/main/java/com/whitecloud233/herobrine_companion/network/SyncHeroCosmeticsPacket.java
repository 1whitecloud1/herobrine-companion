package com.whitecloud233.herobrine_companion.network;

import com.whitecloud233.herobrine_companion.HerobrineCompanion;
import com.whitecloud233.herobrine_companion.client.network.ClientStateSync;
import com.whitecloud233.herobrine_companion.entity.HeroEntity;
import com.whitecloud233.herobrine_companion.entity.logic.data.HeroWorldData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public class SyncHeroCosmeticsPacket implements CustomPacketPayload {
    public static final Type<SyncHeroCosmeticsPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(HerobrineCompanion.MODID, "sync_hero_cosmetics"));

    public static final StreamCodec<FriendlyByteBuf, SyncHeroCosmeticsPacket> STREAM_CODEC =
            StreamCodec.ofMember(SyncHeroCosmeticsPacket::encode, SyncHeroCosmeticsPacket::new);

    private final int entityId;
    private final int skinVariant;
    private final String customSkinName;
    private final byte[] customSkinData;
    private final CompoundTag curiosBackItem;
    private final CompoundTag accessoriesData;

    public SyncHeroCosmeticsPacket(HeroEntity hero) {
        this(
                hero.getId(),
                hero.getSkinVariant(),
                hero.getCustomSkinName(),
                getCustomSkinData(hero),
                hero.getCuriosBackItemTag().copy(),
                hero.getAccessoriesDataTag().copy()
        );
    }

    public SyncHeroCosmeticsPacket(int entityId, int skinVariant, String customSkinName, byte[] customSkinData, CompoundTag curiosBackItem, CompoundTag accessoriesData) {
        this.entityId = entityId;
        this.skinVariant = skinVariant;
        this.customSkinName = customSkinName;
        this.customSkinData = customSkinData != null ? customSkinData : new byte[0];
        this.curiosBackItem = curiosBackItem != null ? curiosBackItem.copy() : new CompoundTag();
        this.accessoriesData = accessoriesData != null ? accessoriesData.copy() : new CompoundTag();
    }

    public SyncHeroCosmeticsPacket(FriendlyByteBuf buf) {
        this.entityId = buf.readInt();
        this.skinVariant = buf.readInt();
        this.customSkinName = buf.readUtf(32767);
        this.customSkinData = buf.readByteArray();
        CompoundTag tag = buf.readNbt();
        this.curiosBackItem = tag != null ? tag : new CompoundTag();
        CompoundTag accessoriesTag = buf.readNbt();
        this.accessoriesData = accessoriesTag != null ? accessoriesTag : new CompoundTag();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeInt(this.entityId);
        buf.writeInt(this.skinVariant);
        buf.writeUtf(this.customSkinName);
        buf.writeByteArray(this.customSkinData);
        buf.writeNbt(this.curiosBackItem);
        buf.writeNbt(this.accessoriesData);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SyncHeroCosmeticsPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> ClientStateSync.applySyncHeroCosmetics(
                packet.entityId,
                packet.skinVariant,
                packet.customSkinName,
                packet.customSkinData,
                packet.curiosBackItem,
                packet.accessoriesData));
    }

    private static byte[] getCustomSkinData(HeroEntity hero) {
        if (hero.level() instanceof ServerLevel serverLevel && hero.getOwnerUUID() != null) {
            return HeroWorldData.get(serverLevel).getCustomSkinData(hero.getOwnerUUID());
        }
        return new byte[0];
    }
}
