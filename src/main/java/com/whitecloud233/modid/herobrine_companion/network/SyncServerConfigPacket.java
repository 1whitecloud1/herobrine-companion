package com.whitecloud233.modid.herobrine_companion.network;

import com.whitecloud233.modid.herobrine_companion.config.Config;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * C→S：客户端在模组设置界面修改服务端相关开关时，把整组配置同步给服务端，
 * 让服务端逻辑（拔树叶、方块修复、清理掉落物、物品开关等）立即生效。
 *
 * <p>只允许单机主人或 OP 修改，避免普通玩家改动服务器配置。</p>
 */
public class SyncServerConfigPacket {
    private final boolean poemOfTheEndExplosion;
    private final boolean heroKingAuraEnabled;
    private final boolean heroBlockRestoration;
    private final boolean heroCleanItems;
    private final boolean cleaveSkillEnabled;
    private final boolean soulBoundPactEnabled;
    private final boolean abyssalGazeEnabled;
    private final boolean transcendencePermitEnabled;
    private final int aiVisionInterval;
    private final boolean awakenedMobAiDialogueEnabled;
    private final boolean heroLeafVanishEnabled;

    public SyncServerConfigPacket(
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
            boolean heroLeafVanishEnabled) {
        this.poemOfTheEndExplosion = poemOfTheEndExplosion;
        this.heroKingAuraEnabled = heroKingAuraEnabled;
        this.heroBlockRestoration = heroBlockRestoration;
        this.heroCleanItems = heroCleanItems;
        this.cleaveSkillEnabled = cleaveSkillEnabled;
        this.soulBoundPactEnabled = soulBoundPactEnabled;
        this.abyssalGazeEnabled = abyssalGazeEnabled;
        this.transcendencePermitEnabled = transcendencePermitEnabled;
        this.aiVisionInterval = aiVisionInterval;
        this.awakenedMobAiDialogueEnabled = awakenedMobAiDialogueEnabled;
        this.heroLeafVanishEnabled = heroLeafVanishEnabled;
    }

    public SyncServerConfigPacket(FriendlyByteBuf buf) {
        this.poemOfTheEndExplosion = buf.readBoolean();
        this.heroKingAuraEnabled = buf.readBoolean();
        this.heroBlockRestoration = buf.readBoolean();
        this.heroCleanItems = buf.readBoolean();
        this.cleaveSkillEnabled = buf.readBoolean();
        this.soulBoundPactEnabled = buf.readBoolean();
        this.abyssalGazeEnabled = buf.readBoolean();
        this.transcendencePermitEnabled = buf.readBoolean();
        this.aiVisionInterval = buf.readVarInt();
        this.awakenedMobAiDialogueEnabled = buf.readBoolean();
        this.heroLeafVanishEnabled = buf.readBoolean();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBoolean(this.poemOfTheEndExplosion);
        buf.writeBoolean(this.heroKingAuraEnabled);
        buf.writeBoolean(this.heroBlockRestoration);
        buf.writeBoolean(this.heroCleanItems);
        buf.writeBoolean(this.cleaveSkillEnabled);
        buf.writeBoolean(this.soulBoundPactEnabled);
        buf.writeBoolean(this.abyssalGazeEnabled);
        buf.writeBoolean(this.transcendencePermitEnabled);
        buf.writeVarInt(this.aiVisionInterval);
        buf.writeBoolean(this.awakenedMobAiDialogueEnabled);
        buf.writeBoolean(this.heroLeafVanishEnabled);
    }

    public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        PacketDispatch.enqueueServer(context, () -> {
            ServerPlayer serverPlayer = context.getSender();
            if (serverPlayer == null || serverPlayer.getServer() == null) {
                return;
            }
            if (!serverPlayer.getServer().isSingleplayer() && !serverPlayer.hasPermissions(2)) {
                return;
            }

            Config.POEM_OF_THE_END_EXPLOSION.set(this.poemOfTheEndExplosion);
            Config.HERO_KING_AURA_ENABLED.set(this.heroKingAuraEnabled);
            Config.HERO_BLOCK_RESTORATION.set(this.heroBlockRestoration);
            Config.HERO_CLEAN_ITEMS.set(this.heroCleanItems);
            Config.CLEAVE_SKILL_ENABLED.set(this.cleaveSkillEnabled);
            Config.SOUL_BOUND_PACT_ENABLED.set(this.soulBoundPactEnabled);
            Config.ABYSSAL_GAZE_ENABLED.set(this.abyssalGazeEnabled);
            Config.TRANSCENDENCE_PERMIT_ENABLED.set(this.transcendencePermitEnabled);
            int safeVisionInterval = Math.max(5, Math.min(600, this.aiVisionInterval));
            Config.AI_VISION_INTERVAL.set(safeVisionInterval);
            Config.AWAKENED_MOB_AI_DIALOGUE_ENABLED.set(this.awakenedMobAiDialogueEnabled);
            Config.HERO_LEAF_VANISH_ENABLED.set(this.heroLeafVanishEnabled);

            Config.poemOfTheEndExplosion = this.poemOfTheEndExplosion;
            Config.heroKingAuraEnabled = this.heroKingAuraEnabled;
            Config.heroBlockRestoration = this.heroBlockRestoration;
            Config.heroCleanItems = this.heroCleanItems;
            Config.cleaveSkillEnabled = this.cleaveSkillEnabled;
            Config.soulBoundPactEnabled = this.soulBoundPactEnabled;
            Config.abyssalGazeEnabled = this.abyssalGazeEnabled;
            Config.transcendencePermitEnabled = this.transcendencePermitEnabled;
            Config.aiVisionInterval = safeVisionInterval;
            Config.awakenedMobAiDialogueEnabled = this.awakenedMobAiDialogueEnabled;
            Config.heroLeafVanishEnabled = this.heroLeafVanishEnabled;

            Config.SPEC.save();
        });
    }
}
