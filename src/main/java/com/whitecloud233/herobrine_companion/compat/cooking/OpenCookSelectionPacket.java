package com.whitecloud233.herobrine_companion.compat.cooking;

import com.whitecloud233.herobrine_companion.HerobrineCompanion;
import com.whitecloud233.herobrine_companion.client.network.ClientUiDispatch;
import com.whitecloud233.herobrine_companion.entity.cooking.CookOption;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;

public class OpenCookSelectionPacket implements CustomPacketPayload {
    public static final Type<OpenCookSelectionPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(HerobrineCompanion.MODID, "open_cook_selection"));
    public static final StreamCodec<RegistryFriendlyByteBuf, OpenCookSelectionPacket> STREAM_CODEC =
            StreamCodec.ofMember(OpenCookSelectionPacket::encode, OpenCookSelectionPacket::new);

    private final int heroId;
    private final BlockPos cookwarePos;
    private final List<CookOption> options;

    public OpenCookSelectionPacket(int heroId, BlockPos cookwarePos, List<CookOption> options) {
        this.heroId = heroId;
        this.cookwarePos = cookwarePos.immutable();
        this.options = List.copyOf(options);
    }

    public OpenCookSelectionPacket(RegistryFriendlyByteBuf buf) {
        this.heroId = buf.readInt();
        this.cookwarePos = buf.readBlockPos();
        int size = buf.readVarInt();
        List<CookOption> values = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            ResourceLocation recipeId = buf.readResourceLocation();
            ItemStack result = ItemStack.OPTIONAL_STREAM_CODEC.decode(buf);
            int maxRepeatCount = buf.readVarInt();
            values.add(new CookOption(recipeId, result, maxRepeatCount));
        }
        this.options = values;
    }

    public void encode(RegistryFriendlyByteBuf buf) {
        buf.writeInt(this.heroId);
        buf.writeBlockPos(this.cookwarePos);
        buf.writeVarInt(this.options.size());
        for (CookOption option : this.options) {
            buf.writeResourceLocation(option.recipeId());
            ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, option.result());
            buf.writeVarInt(option.maxRepeatCount());
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(OpenCookSelectionPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> ClientUiDispatch.openCookSelection(packet.heroId, packet.cookwarePos, packet.options));
    }
}