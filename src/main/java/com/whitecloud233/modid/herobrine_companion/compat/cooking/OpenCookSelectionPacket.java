package com.whitecloud233.modid.herobrine_companion.compat.cooking;

import com.whitecloud233.modid.herobrine_companion.entity.cooking.CookOption;
import com.whitecloud233.modid.herobrine_companion.network.NetworkClientBridge;
import com.whitecloud233.modid.herobrine_companion.network.PacketDispatch;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public class OpenCookSelectionPacket {
    private final int heroId;
    private final BlockPos cookwarePos;
    private final List<CookOption> options;

    public OpenCookSelectionPacket(int heroId, BlockPos cookwarePos, List<CookOption> options) {
        this.heroId = heroId;
        this.cookwarePos = cookwarePos.immutable();
        this.options = List.copyOf(options);
    }

    public OpenCookSelectionPacket(FriendlyByteBuf buf) {
        this.heroId = buf.readInt();
        this.cookwarePos = buf.readBlockPos();
        int size = buf.readVarInt();
        List<CookOption> values = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            ResourceLocation recipeId = buf.readResourceLocation();
            ItemStack result = buf.readItem();
            int maxRepeatCount = buf.readVarInt();
            values.add(new CookOption(recipeId, result, maxRepeatCount));
        }
        this.options = values;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeInt(this.heroId);
        buf.writeBlockPos(this.cookwarePos);
        buf.writeVarInt(this.options.size());
        for (CookOption option : this.options) {
            buf.writeResourceLocation(option.recipeId());
            buf.writeItem(option.result());
            buf.writeVarInt(option.maxRepeatCount());
        }
    }

    public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        PacketDispatch.enqueueClient(context, () -> NetworkClientBridge.openCookSelection(this.heroId, this.cookwarePos, this.options));
    }
}
