package com.whitecloud233.modid.herobrine_companion.network;

import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class SyncHeroVisitPacket {
    private final boolean visited;

    public SyncHeroVisitPacket(boolean visited) {
        this.visited = visited;
    }

    public SyncHeroVisitPacket(FriendlyByteBuf buf) {
        this.visited = buf.readBoolean();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBoolean(this.visited);
    }

    public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
                if (Minecraft.getInstance().player != null) {
                    Minecraft.getInstance().player.getPersistentData().putBoolean("HasVisitedHeroDimension", visited);
                }
            });
        });
        context.setPacketHandled(true);
    }
}
