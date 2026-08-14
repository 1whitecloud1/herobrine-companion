package com.whitecloud233.herobrine_companion.network;

import com.whitecloud233.herobrine_companion.HerobrineCompanion;
import com.whitecloud233.herobrine_companion.client.network.ClientMemoryDigest;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * S→C 记忆摘要：服务端 {@code HeroMemory} 的长期记忆 digest（已含框定语 + 分层文本），
 * 由客户端 {@link ClientMemoryDigest} 缓存，注入聊天 LLM 上下文。
 */
public record HeroMemoryDigestPacket(String digest) implements CustomPacketPayload {

    /** 单包上限：digest 是"事实 + 重要情景"的短文本，4KB 足够且防滥用。 */
    private static final int MAX_DIGEST_CHARS = 4096;

    public static final Type<HeroMemoryDigestPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(HerobrineCompanion.MODID, "hero_memory_digest"));

    public static final StreamCodec<ByteBuf, HeroMemoryDigestPacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.stringUtf8(MAX_DIGEST_CHARS),
            HeroMemoryDigestPacket::digest,
            HeroMemoryDigestPacket::new
    );

    public HeroMemoryDigestPacket {
        digest = digest == null ? "" : digest;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(HeroMemoryDigestPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (FMLEnvironment.dist == Dist.CLIENT) {
                ClientMemoryDigest.accept(packet.digest());
            }
        });
    }
}
