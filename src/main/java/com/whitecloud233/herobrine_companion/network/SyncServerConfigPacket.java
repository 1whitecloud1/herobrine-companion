package com.whitecloud233.herobrine_companion.network;

import com.whitecloud233.herobrine_companion.config.Config;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * C→S：客户端在模组设置界面修改服务端相关开关时，把整组配置同步给服务端，
 * 让服务端逻辑（拔树叶、方块修复、清理掉落物、物品开关等）立即生效。
 *
 * <p>只允许单机主人或 OP 修改，避免普通玩家改动服务器配置。</p>
 */
public record SyncServerConfigPacket(
        boolean poemOfTheEndExplosion,
        boolean heroKingAuraEnabled,
        boolean heroBlockRestoration,
        boolean heroCleanItems,
        boolean cleaveSkillEnabled,
        boolean soulBoundPactEnabled,
        boolean abyssalGazeEnabled,
        boolean transcendencePermitEnabled,
        int aiVisionInterval,
        boolean awakenedMobAiDialogueEnabled,
        boolean heroLeafVanishEnabled
) implements CustomPacketPayload {

    public static final Type<SyncServerConfigPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("herobrine_companion", "sync_server_config"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SyncServerConfigPacket> STREAM_CODEC =
            StreamCodec.of(
                    (buf, pkt) -> {
                        buf.writeBoolean(pkt.poemOfTheEndExplosion());
                        buf.writeBoolean(pkt.heroKingAuraEnabled());
                        buf.writeBoolean(pkt.heroBlockRestoration());
                        buf.writeBoolean(pkt.heroCleanItems());
                        buf.writeBoolean(pkt.cleaveSkillEnabled());
                        buf.writeBoolean(pkt.soulBoundPactEnabled());
                        buf.writeBoolean(pkt.abyssalGazeEnabled());
                        buf.writeBoolean(pkt.transcendencePermitEnabled());
                        buf.writeVarInt(pkt.aiVisionInterval());
                        buf.writeBoolean(pkt.awakenedMobAiDialogueEnabled());
                        buf.writeBoolean(pkt.heroLeafVanishEnabled());
                    },
                    buf -> new SyncServerConfigPacket(
                            buf.readBoolean(),
                            buf.readBoolean(),
                            buf.readBoolean(),
                            buf.readBoolean(),
                            buf.readBoolean(),
                            buf.readBoolean(),
                            buf.readBoolean(),
                            buf.readBoolean(),
                            buf.readVarInt(),
                            buf.readBoolean(),
                            buf.readBoolean()
                    )
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SyncServerConfigPacket payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer serverPlayer) {
                if (serverPlayer.getServer() != null
                        && (serverPlayer.getServer().isSingleplayer() || serverPlayer.hasPermissions(2))) {
                    Config.POEM_OF_THE_END_EXPLOSION.set(payload.poemOfTheEndExplosion());
                    Config.HERO_KING_AURA_ENABLED.set(payload.heroKingAuraEnabled());
                    Config.HERO_BLOCK_RESTORATION.set(payload.heroBlockRestoration());
                    Config.HERO_CLEAN_ITEMS.set(payload.heroCleanItems());
                    Config.CLEAVE_SKILL_ENABLED.set(payload.cleaveSkillEnabled());
                    Config.SOUL_BOUND_PACT_ENABLED.set(payload.soulBoundPactEnabled());
                    Config.ABYSSAL_GAZE_ENABLED.set(payload.abyssalGazeEnabled());
                    Config.TRANSCENDENCE_PERMIT_ENABLED.set(payload.transcendencePermitEnabled());
                    int safeVisionInterval = Math.max(5, Math.min(600, payload.aiVisionInterval()));
                    Config.AI_VISION_INTERVAL.set(safeVisionInterval);
                    Config.AWAKENED_MOB_AI_DIALOGUE_ENABLED.set(payload.awakenedMobAiDialogueEnabled());
                    Config.HERO_LEAF_VANISH_ENABLED.set(payload.heroLeafVanishEnabled());

                    Config.poemOfTheEndExplosion = payload.poemOfTheEndExplosion();
                    Config.heroKingAuraEnabled = payload.heroKingAuraEnabled();
                    Config.heroBlockRestoration = payload.heroBlockRestoration();
                    Config.heroCleanItems = payload.heroCleanItems();
                    Config.cleaveSkillEnabled = payload.cleaveSkillEnabled();
                    Config.soulBoundPactEnabled = payload.soulBoundPactEnabled();
                    Config.abyssalGazeEnabled = payload.abyssalGazeEnabled();
                    Config.transcendencePermitEnabled = payload.transcendencePermitEnabled();
                    Config.aiVisionInterval = safeVisionInterval;
                    Config.awakenedMobAiDialogueEnabled = payload.awakenedMobAiDialogueEnabled();
                    Config.heroLeafVanishEnabled = payload.heroLeafVanishEnabled();

                    Config.SPEC.save();
                }
            }
        });
    }
}
