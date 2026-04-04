package com.whitecloud233.modid.herobrine_companion.entity.logic.data;

import com.whitecloud233.modid.herobrine_companion.HerobrineCompanion;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = HerobrineCompanion.MODID)
public class PlayerLifecycleHandler {
    // 在 PlayerLifecycleHandler.java 中新增登录监听：
    @SubscribeEvent
    public static void onPlayerLogin(net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            CompoundTag data = player.getPersistentData();

            // 检查玩家登录时的维度
            if (player.level().dimension() == com.whitecloud233.modid.herobrine_companion.world.structure.ModStructures.END_RING_DIMENSION_KEY) {

                // 【核心修复】：精准定位“试炼逃兵”！
                // 逃兵在拔网线下线时会被 failChallenge 强行挂上 ChallengeFailedMessagePending 标记。
                // 正常探索、或者因为坠入虚空假死剧情被踢出服务器的玩家，身上没有这个标记，会直接放行！
                if (data.getBoolean("ChallengeFailedMessagePending")) {
                    ServerLevel overworld = player.getServer().overworld();
                    net.minecraft.core.BlockPos spawnPos = overworld.getSharedSpawnPos();

                    // 将逃兵强行流放回主世界出生点
                    player.teleportTo(overworld, spawnPos.getX(), spawnPos.getY(), spawnPos.getZ(), player.getYRot(), player.getXRot());
                    player.sendSystemMessage(Component.literal("§c[系统] 侦测到你在试炼中途退出，已被强制判定为失败并流放回主世界！"));

                    // 清除原有的普通战败提示标记，防止重复发消息
                    data.remove("ChallengeFailedMessagePending");
                }

                // 如果没有上面的标记，说明是正常的剧情回归者。
                // 代码执行到这里就结束了，随后的 EndRingDimensionHandler.onPlayerJoinWorld 会自动把他安全地拉回擂台中心！
            }
        }
    }
    /**
     * 处理玩家跨维度、死亡复活时的数据继承
     */
    @SubscribeEvent
    public static void onPlayerClone(PlayerEvent.Clone event) {
        Player original = event.getOriginal();
        Player newPlayer = event.getEntity();
        CompoundTag origData = original.getPersistentData();
        CompoundTag curData = newPlayer.getPersistentData();

        // ================= [1. 继承核心能力与状态 (Tags)] =================
        if (original.getTags().contains("herobrine_companion.abyssal_gaze_active")) {
            newPlayer.addTag("herobrine_companion.abyssal_gaze_active");
        }
        if (original.getTags().contains("herobrine_companion.soul_bound_pact_active")) {
            newPlayer.addTag("herobrine_companion.soul_bound_pact_active");
        }
        if (original.getTags().contains("herobrine_companion_peaceful")) {
            newPlayer.addTag("herobrine_companion_peaceful");
        }

        // ================= [2. 继承特殊物品效果 (Transcendence Permit)] =================
        if (origData.getBoolean("herobrine_companion.transcendence_permit_active")) {
            curData.putBoolean("herobrine_companion.transcendence_permit_active", true);
            newPlayer.getAbilities().mayfly = true;
            if (origData.getBoolean("herobrine_companion.transcendence_permit_flying")) {
                curData.putBoolean("herobrine_companion.should_restore_flying", true);
            }
            newPlayer.onUpdateAbilities();
        }

        // ================= [3. 继承灵魂绑定物品与经验 (Soul Bound Pact)] =================
        if (event.isWasDeath() && origData.contains("SoulBoundInventory")) {
            ListTag inventoryTag = origData.getList("SoulBoundInventory", 10);
            newPlayer.getInventory().load(inventoryTag);

            if (origData.contains("SoulBoundXP")) {
                newPlayer.experienceProgress = origData.getFloat("SoulBoundXP");
                newPlayer.experienceLevel = origData.getInt("SoulBoundLevel");
                newPlayer.totalExperience = origData.getInt("SoulBoundTotalXP");
            }

            // 清理旧数据防止残留
            origData.remove("SoulBoundInventory");
            origData.remove("SoulBoundXP");
            origData.remove("SoulBoundLevel");
            origData.remove("SoulBoundTotalXP");
        }

        // ================= [4. 批量继承任务、进度与系统标记 (NBT)] =================
        String[] keysToCopy = {
                "ChallengeFailedMessagePending", "HeroRespawnData", "HeroPendingRespawn",
                "HasSeenUnstableZoneIntro", "EnteredEndRingTime", "WakeUpStage",
                "HasSimulatedCrash", "HeroActiveQuestId", "HeroActiveQuestProgress",
                "HeroPendingTrustReward", "HeroPendingQuestClear", "HasReceivedFragment4",
                "HasReceivedFragment6", "HasVisitedHeroDimension", "VoidDomainUsageCount"
        };

        for (String key : keysToCopy) {
            if (origData.contains(key)) {
                curData.put(key, origData.get(key));
            }
        }
    }

    /**
     * 处理玩家复活后需要立即执行的逻辑（发消息、召唤实体等）
     */
    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            CompoundTag data = player.getPersistentData();

            // 1. 恢复创世神飞行能力
            if (data.getBoolean("herobrine_companion.transcendence_permit_active")) {
                player.getAbilities().mayfly = true;
                player.onUpdateAbilities();
            }

            // 2. 挑战失败的嘲讽提示
            if (data.getBoolean("ChallengeFailedMessagePending")) {
                player.sendSystemMessage(Component.translatable("message.herobrine_companion.challenge_failed").withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
                player.level().playSound(null, player.blockPosition(), net.minecraft.sounds.SoundEvents.WITHER_SPAWN, net.minecraft.sounds.SoundSource.MASTER, 0.5f, 0.5f);
                data.remove("ChallengeFailedMessagePending");
            }

            // 3. 复活 Herobrine 实体
            if (data.getBoolean("HeroPendingRespawn") && data.contains("HeroRespawnData")) {
                HeroDimensionHandler.respawnNearPlayer((ServerLevel) player.level(), player);
                data.remove("HeroPendingRespawn");
                data.remove("HeroRespawnData");
            }
        }
    }
}