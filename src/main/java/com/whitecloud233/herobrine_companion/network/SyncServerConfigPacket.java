package com.whitecloud233.herobrine_companion.network;

import com.whitecloud233.herobrine_companion.HerobrineCompanion;
import com.whitecloud233.herobrine_companion.config.ServerConfigAccess;
import com.whitecloud233.herobrine_companion.config.ServerConfigSnapshot;
import com.whitecloud233.herobrine_companion.config.ServerConfigUpdate;
import com.whitecloud233.herobrine_companion.config.ServerRule;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.Map;

/** 兼容协议 3 的旧整组配置请求；实际授权与更新统一交给配置服务。 */
public class SyncServerConfigPacket implements CustomPacketPayload {

    public static final Type<SyncServerConfigPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(HerobrineCompanion.MODID, "sync_server_config"));

    public static final StreamCodec<FriendlyByteBuf, SyncServerConfigPacket> STREAM_CODEC =
            StreamCodec.ofMember(SyncServerConfigPacket::encode, SyncServerConfigPacket::new);

    private final ServerConfigUpdate update;

    public SyncServerConfigPacket(ServerConfigSnapshot snapshot) {
        this.update = ServerConfigUpdate.full(snapshot);
    }

    public SyncServerConfigPacket(boolean poemOfTheEndExplosion, boolean heroKingAuraEnabled,
                                  boolean heroBlockRestoration, boolean heroCleanItems, boolean cleaveSkillEnabled,
                                  boolean soulBoundPactEnabled, boolean abyssalGazeEnabled, boolean transcendencePermitEnabled,
                                  int aiVisionInterval, boolean awakenedMobAiDialogueEnabled, boolean heroLeafVanishEnabled,
                                  boolean pranksterFakeChestEnabled) {
        this.update = new ServerConfigUpdate(Map.ofEntries(
                Map.entry(ServerRule.POEM_EXPLOSION, poemOfTheEndExplosion ? 1 : 0),
                Map.entry(ServerRule.KING_AURA, heroKingAuraEnabled ? 1 : 0),
                Map.entry(ServerRule.BLOCK_RESTORATION, heroBlockRestoration ? 1 : 0),
                Map.entry(ServerRule.CLEAN_ITEMS, heroCleanItems ? 1 : 0),
                Map.entry(ServerRule.CLEAVE_SKILL, cleaveSkillEnabled ? 1 : 0),
                Map.entry(ServerRule.SOUL_BOUND_PACT, soulBoundPactEnabled ? 1 : 0),
                Map.entry(ServerRule.ABYSSAL_GAZE, abyssalGazeEnabled ? 1 : 0),
                Map.entry(ServerRule.TRANSCENDENCE_PERMIT, transcendencePermitEnabled ? 1 : 0),
                Map.entry(ServerRule.AI_VISION_INTERVAL, aiVisionInterval),
                Map.entry(ServerRule.AWAKENED_DIALOGUE, awakenedMobAiDialogueEnabled ? 1 : 0),
                Map.entry(ServerRule.LEAF_VANISH, heroLeafVanishEnabled ? 1 : 0),
                Map.entry(ServerRule.PRANKSTER_FAKE_CHEST, pranksterFakeChestEnabled ? 1 : 0)));
    }

    public SyncServerConfigPacket(FriendlyByteBuf buffer) { update = ServerConfigCodec.readLegacy(buffer); }

    public void encode(FriendlyByteBuf buffer) { ServerConfigCodec.writeLegacy(buffer, update); }

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(SyncServerConfigPacket payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            var result = ServerConfigAccess.update(player, payload.update);
            ConfigSyncChannel.reply(player, 0, result.authorized());
            if (result.changed()) ConfigSyncChannel.broadcast(player.getServer());
        });
    }
}
