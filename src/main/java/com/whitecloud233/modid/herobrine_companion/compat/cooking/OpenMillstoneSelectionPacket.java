package com.whitecloud233.modid.herobrine_companion.compat.cooking;

import com.whitecloud233.modid.herobrine_companion.entity.cooking.MillstoneOption;
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

/** S→C：玩家用契约右键石磨时，把「能磨什么、最多磨几份」发到客户端开界面。 */
public class OpenMillstoneSelectionPacket {
    private final int heroId;
    private final BlockPos millstonePos;
    private final List<MillstoneOption> options;

    public OpenMillstoneSelectionPacket(int heroId, BlockPos millstonePos, List<MillstoneOption> options) {
        this.heroId = heroId;
        this.millstonePos = millstonePos.immutable();
        this.options = List.copyOf(options);
    }

    public OpenMillstoneSelectionPacket(FriendlyByteBuf buf) {
        this.heroId = buf.readInt();
        this.millstonePos = buf.readBlockPos();
        int size = buf.readVarInt();
        List<MillstoneOption> values = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            ResourceLocation recipeId = buf.readResourceLocation();
            ItemStack result = buf.readItem();
            ItemStack input = buf.readItem();
            int maxCount = buf.readVarInt();
            values.add(new MillstoneOption(recipeId, result, input, maxCount));
        }
        this.options = values;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeInt(this.heroId);
        buf.writeBlockPos(this.millstonePos);
        buf.writeVarInt(this.options.size());
        for (MillstoneOption option : this.options) {
            buf.writeResourceLocation(option.recipeId());
            buf.writeItem(option.result());
            buf.writeItem(option.inputExample());
            buf.writeVarInt(option.maxCount());
        }
    }

    public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        PacketDispatch.enqueueClient(context, () ->
                NetworkClientBridge.openMillstoneSelection(this.heroId, this.millstonePos, this.options));
    }
}
