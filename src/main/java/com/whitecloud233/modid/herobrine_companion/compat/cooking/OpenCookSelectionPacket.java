package com.whitecloud233.modid.herobrine_companion.compat.cooking;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public class OpenCookSelectionPacket {
    private final int heroId;
    private final BlockPos cookwarePos;
    private final List<HeroCookingCompat.CookOptionView> options;

    public OpenCookSelectionPacket(int heroId, BlockPos cookwarePos, List<HeroCookingCompat.CookOptionView> options) {
        this.heroId = heroId;
        this.cookwarePos = cookwarePos;
        this.options = List.copyOf(options);
    }

    public OpenCookSelectionPacket(FriendlyByteBuf buf) {
        this.heroId = buf.readInt();
        this.cookwarePos = buf.readBlockPos();
        int size = buf.readVarInt();
        List<HeroCookingCompat.CookOptionView> values = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            ResourceLocation recipeId = buf.readResourceLocation();
            ItemStack result = buf.readItem();
            int maxRepeatCount = buf.readVarInt();
            values.add(new HeroCookingCompat.CookOptionView(recipeId, result, maxRepeatCount));
        }
        this.options = values;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeInt(this.heroId);
        buf.writeBlockPos(this.cookwarePos);
        buf.writeVarInt(this.options.size());
        for (HeroCookingCompat.CookOptionView option : this.options) {
            buf.writeResourceLocation(option.recipeId());
            buf.writeItem(option.result());
            buf.writeVarInt(option.maxRepeatCount());
        }
    }

    public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
            Minecraft minecraft = Minecraft.getInstance();
            minecraft.setScreen(new HeroCookSelectionScreen(this.heroId, this.cookwarePos, this.options, minecraft.screen));
        }));
        context.setPacketHandled(true);
    }
}
