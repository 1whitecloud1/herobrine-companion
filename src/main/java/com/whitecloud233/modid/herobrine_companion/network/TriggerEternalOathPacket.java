package com.whitecloud233.modid.herobrine_companion.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class TriggerEternalOathPacket {

    public TriggerEternalOathPacket() {
    }

    public TriggerEternalOathPacket(FriendlyByteBuf buf) {
    }

    public void encode(FriendlyByteBuf buf) {
    }

    public void handle(Supplier<NetworkEvent.Context> context) {
        context.get().enqueueWork(() -> {
            // 【终极安全调用】
            // 只有在客户端时，才会去加载并执行 ClientHooks 里的方法
            // 由于使用了全限定类名和方法引用，这个类本身不会带有任何客户端依赖
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> com.whitecloud233.modid.herobrine_companion.client.ClientHooks::triggerEternalOath);
        });
        context.get().setPacketHandled(true);
    }
}