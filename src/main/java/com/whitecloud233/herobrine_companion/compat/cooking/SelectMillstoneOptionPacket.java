package com.whitecloud233.herobrine_companion.compat.cooking;

import com.whitecloud233.herobrine_companion.HerobrineCompanion;
import com.whitecloud233.herobrine_companion.entity.HeroEntity;
import com.whitecloud233.herobrine_companion.entity.cooking.CookingService;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** C→S：玩家在石磨界面里选好材料与份数，由服务端扣料、投料并派 Hero 去拉磨。 */
public record SelectMillstoneOptionPacket(int heroId, BlockPos millstonePos, ResourceLocation recipeId, int count)
        implements CustomPacketPayload {

    public static final Type<SelectMillstoneOptionPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(HerobrineCompanion.MODID, "select_millstone_option"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SelectMillstoneOptionPacket> STREAM_CODEC =
            StreamCodec.of(
                    (buf, packet) -> {
                        buf.writeInt(packet.heroId());
                        buf.writeBlockPos(packet.millstonePos());
                        buf.writeResourceLocation(packet.recipeId());
                        buf.writeVarInt(packet.count());
                    },
                    buf -> new SelectMillstoneOptionPacket(
                            buf.readInt(), buf.readBlockPos(), buf.readResourceLocation(), buf.readVarInt())
            );

    public SelectMillstoneOptionPacket {
        millstonePos = millstonePos.immutable();
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SelectMillstoneOptionPacket payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            Entity entity = player.level().getEntity(payload.heroId());
            if (entity instanceof HeroEntity hero) {
                CookingService.requestMillstoneTask(
                        hero, player, payload.millstonePos(), payload.recipeId(), payload.count());
            }
        });
    }
}
