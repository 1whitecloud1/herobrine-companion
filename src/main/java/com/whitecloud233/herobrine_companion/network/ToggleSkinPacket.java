package com.whitecloud233.herobrine_companion.network;

import com.whitecloud233.herobrine_companion.HerobrineCompanion;
import com.whitecloud233.herobrine_companion.entity.HeroEntity;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.network.handling.IPayloadContext;

// 1. 使用 record 声明所有需要传输的字段
public record ToggleSkinPacket(int entityId, int skinVariant, String customSkinName) implements CustomPacketPayload {

    // 2. 注册包的唯一标识符 Type
    public static final Type<ToggleSkinPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(HerobrineCompanion.MODID, "toggle_skin"));

    // 3. 核心：使用 StreamCodec.composite 按顺序绑定字段和解码器
    public static final StreamCodec<ByteBuf, ToggleSkinPacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.INT, ToggleSkinPacket::entityId,
            ByteBufCodecs.INT, ToggleSkinPacket::skinVariant,
            ByteBufCodecs.STRING_UTF8, ToggleSkinPacket::customSkinName, // 字符串使用 STRING_UTF8
            ToggleSkinPacket::new
    );

    // ==========================================
    // 兼容性构造方法：方便你在其他地方调用，不用每次都传空字符串
    // ==========================================
    public ToggleSkinPacket(int entityId, boolean useHerobrineSkin) {
        this(entityId, useHerobrineSkin ? HeroEntity.SKIN_HEROBRINE : HeroEntity.SKIN_HERO, "");
    }

    public ToggleSkinPacket(int entityId, int skinVariant) {
        this(entityId, skinVariant, "");
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    // 4. 数据包到达服务端的处理逻辑
    public static void handle(ToggleSkinPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                Entity entity = player.level().getEntity(packet.entityId());
                if (entity instanceof HeroEntity hero) {
                    // 更新皮肤变种
                    hero.setSkinVariant(packet.skinVariant());

                    // 补全被遗漏的逻辑：如果是自定义皮肤，则应用传过来的字符串
                    if (packet.skinVariant() == HeroEntity.SKIN_CUSTOM) {
                        hero.setCustomSkinName(packet.customSkinName());
                    }
                }
            }
        });
    }
}