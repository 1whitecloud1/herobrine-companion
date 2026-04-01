package com.whitecloud233.modid.herobrine_companion.client.fight.network;

import com.whitecloud233.modid.herobrine_companion.client.fight.event.ClientCollapseHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class SPacketStartCollapse {

    public SPacketStartCollapse() {
        // 服务端发包用的空构造
    }

    public SPacketStartCollapse(FriendlyByteBuf buf) {
        // 客户端接包用的构造（这里不需要传具体数据，所以留空）
    }

    public void toBytes(FriendlyByteBuf buf) {
        // 写入数据流（留空）
    }

    public void handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        // [补全] 客户端收到包后，在主线程触发视觉演出的代码
        context.enqueueWork(ClientCollapseHandler::startCollapse);
        context.setPacketHandled(true);
    }
}
