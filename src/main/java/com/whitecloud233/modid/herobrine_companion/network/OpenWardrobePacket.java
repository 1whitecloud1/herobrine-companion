package com.whitecloud233.modid.herobrine_companion.network;

import com.whitecloud233.modid.herobrine_companion.entity.HeroEntity;
import com.whitecloud233.modid.herobrine_companion.world.inventory.HeroWardrobeMenu;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkHooks;

import java.util.function.Supplier;

public class OpenWardrobePacket {
    private final int entityId;

    public OpenWardrobePacket(int entityId) {
        this.entityId = entityId;
    }

    public OpenWardrobePacket(FriendlyByteBuf buf) {
        this.entityId = buf.readInt();
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeInt(this.entityId);
    }

    public boolean handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player != null) {
                // 在服务端获取对应的实体
                Entity entity = player.level().getEntity(this.entityId);
                if (entity instanceof HeroEntity hero) {
                    // 让服务端为玩家打开装扮 Menu
                    NetworkHooks.openScreen(player,
                            new SimpleMenuProvider(
                                    (containerId, playerInv, p) -> new HeroWardrobeMenu(containerId, playerInv, hero),
                                    Component.translatable("gui.herobrine_companion.wardrobe")
                            ),
                            buf -> buf.writeInt(hero.getId()) // 附带实体 ID 给客户端 Screen 构造器
                    );
                }
            }
        });
        return true;
    }
}