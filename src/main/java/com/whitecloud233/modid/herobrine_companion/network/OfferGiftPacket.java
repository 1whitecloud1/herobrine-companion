package com.whitecloud233.modid.herobrine_companion.network;

import com.whitecloud233.modid.herobrine_companion.entity.HeroEntity;
import com.whitecloud233.modid.herobrine_companion.entity.gift.HeroOfferService;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 托付(赠礼)动作 —— 客户端 → 服务端。
 *
 * <p>只携带"英雄 id + 动作名";物品读取、校验、扣除全部在服务端重做
 * (等价 Bedrock HeroGiftConfirmEvent / not-a-trust-the-client 原则)。
 */
public class OfferGiftPacket {
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

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        if (PacketDispatch.isServer(context)) {
            PacketDispatch.enqueueServer(context, () -> {
                ServerPlayer sender = context.getSender();
                if (sender == null || sender.level() == null) {
                    return;
                }
                Entity entity = sender.level().getEntity(this.entityId);
                if (entity instanceof HeroEntity hero) {
                    HeroOfferService.handleUIOfferAction(hero, sender, this.action);
                }
            });
        } else {
            PacketDispatch.assertClient(context);
            context.setPacketHandled(true);
        }
    }
}