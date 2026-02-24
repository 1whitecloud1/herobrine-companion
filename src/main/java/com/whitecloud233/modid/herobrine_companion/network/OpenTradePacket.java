package com.whitecloud233.modid.herobrine_companion.network;

import com.whitecloud233.modid.herobrine_companion.entity.HeroEntity;
import com.whitecloud233.modid.herobrine_companion.world.inventory.HeroMerchantMenu;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkHooks;

import java.util.function.Supplier;

public class OpenTradePacket {
    private final int entityId;

    public OpenTradePacket(int entityId) {
        this.entityId = entityId;
    }

    public OpenTradePacket(FriendlyByteBuf buf) {
        this.entityId = buf.readVarInt();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(this.entityId);
    }

    public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player != null) {
                Entity entity = player.level().getEntity(this.entityId);

                if (entity instanceof HeroEntity hero) {
                    if (player.distanceToSqr(hero) < 64.0D) {

                        // 获取交易列表（此时已修复了 KubeJS 导致的崩溃）
                        net.minecraft.world.item.trading.MerchantOffers offers = hero.getOffers();

                        // 判空保护，防止列表为空时导致客户端卡死
                        if (offers == null || offers.isEmpty()) {
                            hero.setTradingPlayer(null);
                            return;
                        }

                        // 使用 Forge 的 NetworkHooks 打开自定义菜单，并附带必要的 Buffer
                        NetworkHooks.openScreen(player, new SimpleMenuProvider(
                                (id, inventory, p) -> new HeroMerchantMenu(id, inventory, hero),
                                hero.getDisplayName()
                        ), buf -> {
                            buf.writeInt(hero.getId());
                        });

                        // 菜单成功打开后，再绑定玩家并同步商品，完美避开闪退 Bug
                        if (player.containerMenu instanceof HeroMerchantMenu) {
                            hero.setTradingPlayer(player);
                            player.sendMerchantOffers(player.containerMenu.containerId, offers, 1, hero.getVillagerXp(), hero.showProgressBar(), hero.canRestock());
                        } else {
                            hero.setTradingPlayer(null);
                        }
                    }
                }
            }
        });
        context.setPacketHandled(true);
    }
}