package com.whitecloud233.herobrine_companion.network;

import com.whitecloud233.herobrine_companion.HerobrineCompanion;
import com.whitecloud233.herobrine_companion.entity.gift.HeroBirthdayCakeService;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 无名之蛋糕的摆放 / 收回请求 —— 客户端 → 服务端(NeoForge 1.21.1)。
 *
 * <p><b>为什么需要这个包</b>:原版 {@code ServerPlayerGameMode#useItem / useItemOn} 在方法最开头就检查
 * {@code ItemCooldowns.isOnCooldown(item)},物品处于冷却时**根本不会调用物品自身的 use/useOn**。
 * 蛋糕吃完后有 60 分钟冷却(为了显示原版冷却灰条),如果摆放逻辑挂在物品的 useOn 上,
 * 冷却期间就会完全失效。因此摆放/收回改由这个包携带坐标触发,与服务端冷却彻底解耦。
 *
 * <p>坐标由客户端提供,但服务端仍会重新校验可放置性(不信任客户端)。
 */
public class BirthdayCakePlacePacket implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<BirthdayCakePlacePacket> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(
                    HerobrineCompanion.MODID, "birthday_cake_place"));

    public static final StreamCodec<FriendlyByteBuf, BirthdayCakePlacePacket> STREAM_CODEC =
            StreamCodec.ofMember(BirthdayCakePlacePacket::encode, BirthdayCakePlacePacket::new);

    private final boolean hasPos;
    private final BlockPos pos;
    private final int face;

    public BirthdayCakePlacePacket(BlockPos pos, Direction face) {
        this.hasPos = pos != null;
        this.pos = pos == null ? BlockPos.ZERO : pos;
        this.face = face == null ? -1 : face.get3DDataValue();
    }

    public BirthdayCakePlacePacket(FriendlyByteBuf buf) {
        this.hasPos = buf.readBoolean();
        this.pos = buf.readBlockPos();
        this.face = buf.readByte();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBoolean(this.hasPos);
        buf.writeBlockPos(this.pos);
        buf.writeByte(this.face);
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public void handle(IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!context.flow().isServerbound()) {
                return;
            }
            Player player = context.player();
            if (player instanceof ServerPlayer sender) {
                HeroBirthdayCakeService.handleClientSneakUse(sender,
                        this.hasPos ? this.pos : null,
                        this.face < 0 ? null : Direction.from3DDataValue(this.face));
            }
        });
    }
}