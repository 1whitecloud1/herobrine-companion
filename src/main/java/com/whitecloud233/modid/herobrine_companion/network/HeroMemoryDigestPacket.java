package com.whitecloud233.modid.herobrine_companion.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * S→C 记忆摘要：服务端 {@code HeroMemory} 的长期记忆 digest（已含框定语 + 分层文本），
 * 由客户端 {@code ClientMemoryDigest} 缓存，注入聊天 LLM 上下文。
 *
 * <p><b>单一职责</b>：只搬运文本。构造/缓存/注入分别由服务端 agent、客户端仓储、
 * {@code AIService} 负责。</p>
 */
public class HeroMemoryDigestPacket {

    /** 单包上限：digest 是"事实 + 重要情景"的短文本，4KB 足够且防滥用。 */
    private static final int MAX_DIGEST_CHARS = 4096;

    private final String digest;

    public HeroMemoryDigestPacket(String digest) {
        this.digest = digest == null ? "" : digest;
    }

    public HeroMemoryDigestPacket(FriendlyByteBuf buf) {
        this.digest = buf.readUtf(MAX_DIGEST_CHARS);
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(this.digest, MAX_DIGEST_CHARS);
    }

    public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        PacketDispatch.assertClient(context);
        context.enqueueWork(() -> NetworkClientBridge.acceptMemoryDigest(this.digest));
        context.setPacketHandled(true);
    }
}
