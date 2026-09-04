package com.whitecloud233.herobrine_companion.compat.cooking;

import com.whitecloud233.herobrine_companion.HerobrineCompanion;
import com.whitecloud233.herobrine_companion.entity.HeroEntity;
import com.whitecloud233.herobrine_companion.entity.logic.data.HeroLogic;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public class SelectCookOptionPacket implements CustomPacketPayload {
    public static final Type<SelectCookOptionPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(HerobrineCompanion.MODID, "select_cook_option"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SelectCookOptionPacket> STREAM_CODEC =
            StreamCodec.ofMember(SelectCookOptionPacket::encode, SelectCookOptionPacket::new);

    private final int heroId;
    private final BlockPos cookwarePos;
    private final ResourceLocation recipeId;
    private final int repeatCount;

    public SelectCookOptionPacket(int heroId, BlockPos cookwarePos, ResourceLocation recipeId, int repeatCount) {
        this.heroId = heroId;
        this.cookwarePos = cookwarePos;
        this.recipeId = recipeId;
        this.repeatCount = repeatCount;
    }

    public SelectCookOptionPacket(RegistryFriendlyByteBuf buf) {
        this.heroId = buf.readInt();
        this.cookwarePos = buf.readBlockPos();
        this.recipeId = buf.readResourceLocation();
        this.repeatCount = buf.readVarInt();
    }

    public void encode(RegistryFriendlyByteBuf buf) {
        buf.writeInt(this.heroId);
        buf.writeBlockPos(this.cookwarePos);
        buf.writeResourceLocation(this.recipeId);
        buf.writeVarInt(this.repeatCount);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SelectCookOptionPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            com.mojang.logging.LogUtils.getLogger().info(
                    "[CookDebug] 收到选菜包 hero={} pos={} recipe={} repeat={}",
                    packet.heroId, packet.cookwarePos, packet.recipeId, packet.repeatCount);
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }

            Entity entity = player.level().getEntity(packet.heroId);
            if (entity instanceof HeroEntity hero) {
                HeroLogic.handleCookSelection(hero, player, packet.cookwarePos, packet.recipeId, packet.repeatCount);
            }
        });
    }
}
