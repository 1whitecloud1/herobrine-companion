package com.whitecloud233.modid.herobrine_companion.compat.cooking;

import com.whitecloud233.modid.herobrine_companion.entity.HeroEntity;
import com.whitecloud233.modid.herobrine_companion.entity.cooking.CookingService;
import com.whitecloud233.modid.herobrine_companion.network.PacketDispatch;import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** C→S：玩家在石磨界面里选好材料与份数，由服务端扣料、投料并派 Hero 去拉磨。 */
public class SelectMillstoneOptionPacket {
    private final int heroId;
    private final BlockPos millstonePos;
    private final ResourceLocation recipeId;
    private final int count;

    public SelectMillstoneOptionPacket(int heroId, BlockPos millstonePos, ResourceLocation recipeId, int count) {
        this.heroId = heroId;
        this.millstonePos = millstonePos.immutable();
        this.recipeId = recipeId;
        this.count = count;
    }

    public SelectMillstoneOptionPacket(FriendlyByteBuf buf) {
        this.heroId = buf.readInt();
        this.millstonePos = buf.readBlockPos();
        this.recipeId = buf.readResourceLocation();
        this.count = buf.readVarInt();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeInt(this.heroId);
        buf.writeBlockPos(this.millstonePos);
        buf.writeResourceLocation(this.recipeId);
        buf.writeVarInt(this.count);
    }

    public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        PacketDispatch.enqueueServer(context, () -> {
            ServerPlayer player = context.getSender();
            if (player == null) {
                return;
            }

            Entity entity = player.level().getEntity(this.heroId);
            if (entity instanceof HeroEntity hero) {
                CookingService.requestMillstoneTask(hero, player, this.millstonePos, this.recipeId, this.count);
            }
        });
    }
}
