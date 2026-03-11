package com.whitecloud233.modid.herobrine_companion.network;

import com.whitecloud233.modid.herobrine_companion.entity.HeroEntity;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class ToggleSkinPacket {
    private final int entityId;
    private final int skinVariant;
    private final String customSkinName;

    public ToggleSkinPacket(int entityId, boolean useHerobrineSkin) {
        this(entityId, useHerobrineSkin ? HeroEntity.SKIN_HEROBRINE : HeroEntity.SKIN_HERO, "");
    }

    public ToggleSkinPacket(int entityId, int skinVariant) {
        this(entityId, skinVariant, "");
    }

    public ToggleSkinPacket(int entityId, int skinVariant, String customSkinName) {
        this.entityId = entityId;
        this.skinVariant = skinVariant;
        this.customSkinName = customSkinName;
    }

    public ToggleSkinPacket(FriendlyByteBuf buf) {
        this.entityId = buf.readInt();
        this.skinVariant = buf.readInt();
        this.customSkinName = buf.readUtf();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeInt(this.entityId);
        buf.writeInt(this.skinVariant);
        buf.writeUtf(this.customSkinName);
    }

    public boolean handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player != null) {
                Entity entity = player.level().getEntity(this.entityId);
                if (entity instanceof HeroEntity hero) {
                    hero.setSkinVariant(this.skinVariant);
                    if (this.skinVariant == HeroEntity.SKIN_CUSTOM) {
                        hero.setCustomSkinName(this.customSkinName);
                    }
                }
            }
        });
        return true;
    }
}