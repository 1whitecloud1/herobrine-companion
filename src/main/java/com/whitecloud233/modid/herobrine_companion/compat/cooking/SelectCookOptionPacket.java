package com.whitecloud233.modid.herobrine_companion.compat.cooking;

import com.whitecloud233.modid.herobrine_companion.entity.HeroEntity;
import com.whitecloud233.modid.herobrine_companion.entity.logic.data.HeroLogic;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class SelectCookOptionPacket {
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

    public SelectCookOptionPacket(FriendlyByteBuf buf) {
        this.heroId = buf.readInt();
        this.cookwarePos = buf.readBlockPos();
        this.recipeId = buf.readResourceLocation();
        this.repeatCount = buf.readVarInt();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeInt(this.heroId);
        buf.writeBlockPos(this.cookwarePos);
        buf.writeResourceLocation(this.recipeId);
        buf.writeVarInt(this.repeatCount);
    }

    public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null) {
                return;
            }

            Entity entity = player.level().getEntity(this.heroId);
            if (entity instanceof HeroEntity hero) {
                HeroLogic.handleCookSelection(hero, player, this.cookwarePos, this.recipeId, this.repeatCount);
            }
        });
        context.setPacketHandled(true);
    }
}
