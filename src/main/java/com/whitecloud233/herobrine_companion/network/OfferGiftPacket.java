package com.whitecloud233.herobrine_companion.network;

import com.whitecloud233.herobrine_companion.HerobrineCompanion;
import com.whitecloud233.herobrine_companion.entity.HeroEntity;
import com.whitecloud233.herobrine_companion.entity.gift.HeroOfferService;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 托付(赠礼)动作 —— 客户端 → 服务端(NeoForge 1.21.1 payload)。
 *
 * <p>只携带"英雄 id + 动作名";物品读取、校验、扣除全部在服务端重做
 * (等价 Bedrock HeroGiftConfirmEvent / not-a-trust-the-client 原则)。
 */
public class OfferGiftPacket implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<OfferGiftPacket> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(HerobrineCompanion.MODID, "offer_gift"));

    public static final StreamCodec<FriendlyByteBuf, OfferGiftPacket> STREAM_CODEC =
            StreamCodec.ofMember(OfferGiftPacket::encode, OfferGiftPacket::new);

    private final int entityId;
    private final String action;

    public OfferGiftPacket(int entityId, String action) {
        this.entityId = entityId;
        this.action = action;
    }

    public OfferGiftPacket(FriendlyByteBuf buf) {
        this.entityId = buf.readInt();
        this.action = buf.readUtf(64);
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeInt(this.entityId);
        buf.writeUtf(this.action, 64);
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public void handle(IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!context.flow().isServerbound()) {
                return;
            }
            Player player = context.player();
            if (player instanceof ServerPlayer sender && sender.level() != null) {
                Entity entity = sender.level().getEntity(this.entityId);
                if (entity instanceof HeroEntity hero) {
                    HeroOfferService.handleUIOfferAction(hero, sender, this.action);
                }
            }
        });
    }
}