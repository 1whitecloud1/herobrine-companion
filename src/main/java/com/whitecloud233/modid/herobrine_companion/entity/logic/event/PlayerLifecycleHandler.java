package com.whitecloud233.modid.herobrine_companion.entity.logic.event;

import com.whitecloud233.modid.herobrine_companion.HerobrineCompanion;
import com.whitecloud233.modid.herobrine_companion.entity.logic.data.HeroDimensionHandler;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = HerobrineCompanion.MODID)
public class PlayerLifecycleHandler {

    @SubscribeEvent
    public static void onPlayerClone(PlayerEvent.Clone event) {
        CompoundTag original = event.getOriginal().getPersistentData();
        CompoundTag cur = event.getEntity().getPersistentData();

        // 复制所有持久化标签
        String[] keysToCopy = {
                "ChallengeFailedMessagePending", "HeroRespawnData", "HeroPendingRespawn",
                "HasSeenUnstableZoneIntro", "EnteredEndRingTime", "WakeUpStage",
                "HasSimulatedCrash", "HeroActiveQuestId", "HeroActiveQuestProgress",
                "HeroPendingTrustReward", "HeroPendingQuestClear", "HasReceivedFragment4",
                "HasReceivedFragment6"
        };

        for (String key : keysToCopy) {
            if (original.contains(key)) {
                cur.put(key, original.get(key));
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            CompoundTag data = player.getPersistentData();

            if (data.getBoolean("ChallengeFailedMessagePending")) {
                player.sendSystemMessage(Component.translatable("message.herobrine_companion.challenge_failed").withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
                player.level().playSound(null, player.blockPosition(), net.minecraft.sounds.SoundEvents.WITHER_SPAWN, net.minecraft.sounds.SoundSource.MASTER, 0.5f, 0.5f);
                data.remove("ChallengeFailedMessagePending");
            }

            if (data.getBoolean("HeroPendingRespawn") && data.contains("HeroRespawnData")) {
                HeroDimensionHandler.respawnNearPlayer((ServerLevel)player.level(), player);
                data.remove("HeroPendingRespawn");
                data.remove("HeroRespawnData");
            }
        }
    }
}