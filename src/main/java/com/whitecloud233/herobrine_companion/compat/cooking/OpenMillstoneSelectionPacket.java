package com.whitecloud233.herobrine_companion.compat.cooking;

import com.whitecloud233.herobrine_companion.HerobrineCompanion;
import com.whitecloud233.herobrine_companion.client.network.ClientUiDispatch;
import com.whitecloud233.herobrine_companion.entity.cooking.MillstoneOption;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;

/** S→C：玩家用契约右键石磨时，把「能磨什么、最多磨几份」发到客户端开界面。 */
public record OpenMillstoneSelectionPacket(int heroId, BlockPos millstonePos, List<MillstoneOption> options)
        implements CustomPacketPayload {

    public static final Type<OpenMillstoneSelectionPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(HerobrineCompanion.MODID, "open_millstone_selection"));

    public static final StreamCodec<RegistryFriendlyByteBuf, OpenMillstoneSelectionPacket> STREAM_CODEC =
            StreamCodec.of(
                    (buf, packet) -> {
                        buf.writeInt(packet.heroId());
                        buf.writeBlockPos(packet.millstonePos());
                        buf.writeVarInt(packet.options().size());
                        for (MillstoneOption option : packet.options()) {
                            buf.writeResourceLocation(option.recipeId());
                            ItemStack.STREAM_CODEC.encode(buf, option.result());
                            ItemStack.STREAM_CODEC.encode(buf, option.inputExample());
                            buf.writeVarInt(option.maxCount());
                        }
                    },
                    buf -> {
                        int heroId = buf.readInt();
                        BlockPos pos = buf.readBlockPos();
                        int size = buf.readVarInt();
                        List<MillstoneOption> values = new ArrayList<>(size);
                        for (int i = 0; i < size; i++) {
                            ResourceLocation recipeId = buf.readResourceLocation();
                            ItemStack result = ItemStack.STREAM_CODEC.decode(buf);
                            ItemStack input = ItemStack.STREAM_CODEC.decode(buf);
                            int maxCount = buf.readVarInt();
                            values.add(new MillstoneOption(recipeId, result, input, maxCount));
                        }
                        return new OpenMillstoneSelectionPacket(heroId, pos, List.copyOf(values));
                    }
            );

    public OpenMillstoneSelectionPacket {
        millstonePos = millstonePos.immutable();
        options = List.copyOf(options);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(OpenMillstoneSelectionPacket payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (FMLEnvironment.dist != Dist.CLIENT) {
                return;
            }
            ClientUiDispatch.openMillstoneSelection(payload.heroId(), payload.millstonePos(), payload.options());
        });
    }
}
