package com.whitecloud233.herobrine_companion.network.ai;
import com.whitecloud233.herobrine_companion.HerobrineCompanion;
import com.whitecloud233.herobrine_companion.entity.logic.data.HeroCrossChatManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import java.util.Locale;
public class RequestCrossChatSessionPacket implements CustomPacketPayload {
    public static final Type<RequestCrossChatSessionPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(HerobrineCompanion.MODID, "request_cross_chat_session"));
    public static final StreamCodec<FriendlyByteBuf, RequestCrossChatSessionPacket> STREAM_CODEC = StreamCodec.ofMember(RequestCrossChatSessionPacket::encode, RequestCrossChatSessionPacket::new);
    private final String targetName;
    public RequestCrossChatSessionPacket(String targetName) {
        this.targetName = targetName == null ? "" : targetName;
    }
    public RequestCrossChatSessionPacket(FriendlyByteBuf buf) {
        this.targetName = buf.readUtf(256);
    }
    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(this.targetName, 256);
    }
    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
    public static void handle(RequestCrossChatSessionPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer sender) || sender.server == null) {
                return;
            }
            String requestedName = packet.targetName == null ? "" : packet.targetName.trim();
            if (requestedName.isEmpty()) {
                sender.sendSystemMessage(Component.literal("[Cross HB] Target player name cannot be empty."));
                return;
            }
            ServerPlayer target = sender.server.getPlayerList().getPlayerByName(requestedName);
            if (target == null) {
                String lower = requestedName.toLowerCase(Locale.ROOT);
                for (Player player : sender.server.getPlayerList().getPlayers()) {
                    if (player.getGameProfile().getName().toLowerCase(Locale.ROOT).equals(lower) && player instanceof ServerPlayer serverPlayer) {
                        target = serverPlayer;
                        break;
                    }
                }
            }
            if (target == null) {
                sender.sendSystemMessage(Component.literal("[Cross HB] Online player not found: " + requestedName));
                return;
            }
            HeroCrossChatManager.INSTANCE.requestSession(sender, target);
        });
    }
}
