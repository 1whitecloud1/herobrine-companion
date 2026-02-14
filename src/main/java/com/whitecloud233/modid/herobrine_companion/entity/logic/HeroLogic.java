package com.whitecloud233.modid.herobrine_companion.entity.logic;

import com.whitecloud233.modid.herobrine_companion.entity.HeroEntity;
import com.whitecloud233.modid.herobrine_companion.entity.ai.learning.HeroDialogueHandler;
import com.whitecloud233.modid.herobrine_companion.entity.ai.learning.HeroObserver;
import com.whitecloud233.modid.herobrine_companion.entity.ai.learning.HeroPrankHandler;
import com.whitecloud233.modid.herobrine_companion.world.structure.ModStructures;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Player;

import java.util.List;

public class HeroLogic {

    public static void tick(HeroEntity hero) {
        if (hero.level().isClientSide) {
            clientTick(hero);
        } else {
            serverTick(hero);
        }
    }

    private static void clientTick(HeroEntity hero) {
        if (!hero.clientSideSetupDone) {
            setupHiddenTeam(hero);
            hero.clientSideSetupDone = true;
        }

        hero.clientFloatingAmountO = hero.clientFloatingAmount;
        if (hero.isFloating()) {
            hero.clientFloatingAmount = Math.min(1.0F, hero.clientFloatingAmount + 0.05F);
        } else {
            hero.clientFloatingAmount = Math.max(0.0F, hero.clientFloatingAmount - 0.05F);
        }
    }

    private static void serverTick(HeroEntity hero) {
        // 1. 唯一性检查
        // [修复] 延迟检查到 Tick 20，避免世界加载初期的不稳定性导致误杀
        if (hero.tickCount == 20) {
            HeroLifecycleHandler.checkUniqueness(hero);
            // 尝试从附近玩家恢复信任度
            HeroDataHandler.restoreTrustFromPlayer(hero);
        }

        // [修复] 自动绑定 Owner 逻辑
        if (hero.getOwnerUUID() == null) {
            // 如果是刚从磁盘加载的 (loadedFromDisk)，则在它“存活”至少 600 tick (30秒) 之前，禁止自动认主。
            // 这样可以给 NBT 数据同步留出足够的时间，防止因为读取延迟而错误地将路人认作主人。
            // 如果是刷怪蛋新生成的 (!loadedFromDisk)，则在 20 tick 后即可认主。
            boolean isFreshSpawn = !hero.isLoadedFromDisk();
            boolean safeToBind = isFreshSpawn ? (hero.tickCount > 20) : (hero.tickCount > 600);

            if (safeToBind && hero.tickCount % 100 == 0) {
                findAndSetOwner(hero);
            }
        }

        if (hero.tickCount == 5) {
            HeroDataHandler.syncGlobalTrust(hero);
        }
        if (hero.tickCount % 100 == 0) {
            HeroDataHandler.updateGlobalTrust(hero);
        }

        if (hero.tickCount % 20 == 0) {
            boolean isEndRing = hero.level().dimension() == ModStructures.END_RING_DIMENSION_KEY;
            String key = isEndRing ? "entity.herobrine_companion.herobrine" : "entity.herobrine_companion.hero";
            Component expectedName = Component.translatable(key);
            if (!hero.getCustomName().equals(expectedName)) {
                hero.setCustomName(expectedName);
            }
        }

        HeroDimensionHandler.handleVoidProtection(hero);

        HeroDialogueHandler.tick(hero);
        HeroPrankHandler.tick(hero);
        HeroObserver.tick(hero);

        if (hero.tickCount % 100 == 50) {
            checkPendingQuestRewards(hero);
            checkPendingLoreFragments(hero);
        }

        if (hero.isPassenger()) {
            hero.setYHeadRot(hero.yBodyRot);
        }
    }

    private static void findAndSetOwner(HeroEntity hero) {
        if (hero.level().isClientSide()) return;
        List<ServerPlayer> players = hero.level().getEntitiesOfClass(ServerPlayer.class, hero.getBoundingBox().inflate(64));
        if (!players.isEmpty()) {
            ServerPlayer closestPlayer = null;
            double closestDistance = Double.MAX_VALUE;
            for (ServerPlayer p : players) {
                double dist = hero.distanceToSqr(p);
                if (dist < closestDistance) {
                    closestDistance = dist;
                    closestPlayer = p;
                }
            }
            if (closestPlayer != null) {
                hero.setOwnerUUID(closestPlayer.getUUID());

                // [关键修复] 自动认主后，必须立即从该玩家/全局存档中恢复数据！
                // 防止 tick 100 的 updateGlobalTrust 将 0 信任度写入存档
                HeroDataHandler.restoreTrustFromPlayer(hero);
            }
        }
    }

    private static void checkPendingQuestRewards(HeroEntity hero) {
        if (hero.getOwnerUUID() == null) return;
        Player owner = hero.level().getPlayerByUUID(hero.getOwnerUUID());
        if (owner == null) return;

        CompoundTag data = owner.getPersistentData();
        if (data.contains("HeroPendingTrustReward")) {
            int reward = data.getInt("HeroPendingTrustReward");
            hero.increaseTrust(reward);
            owner.sendSystemMessage(Component.translatable("message.herobrine_companion.trust_increase", reward, hero.getTrustLevel()));
            data.remove("HeroPendingTrustReward");
        }
        if (data.contains("HeroPendingQuestClear")) {
            hero.removeTag("player_doing_quest");
            data.remove("HeroPendingQuestClear");
        }
    }

    private static void checkPendingLoreFragments(HeroEntity hero) {
        if (hero.getOwnerUUID() == null) return;
        Player owner = hero.level().getPlayerByUUID(hero.getOwnerUUID());
        if (owner == null) return;

        CompoundTag data = owner.getPersistentData();
        if (data.contains("HeroPendingLore", Tag.TAG_LIST)) {
            ListTag list = data.getList("HeroPendingLore", Tag.TAG_STRING);

            for (int i = 0; i < list.size(); i++) {
                String fragmentId = list.getString(i);
                hero.getHeroBrain().inputLoreFragment(owner.getUUID(), fragmentId);
            }

            if (!list.isEmpty()) {
                hero.level().playSound(null, hero.blockPosition(), net.minecraft.sounds.SoundEvents.PLAYER_LEVELUP, net.minecraft.sounds.SoundSource.NEUTRAL, 0.5f, 1.0f);
            }

            data.remove("HeroPendingLore");
        }
    }

    public static InteractionResult onInteract(HeroEntity hero, Player player, InteractionHand hand) {
        return HeroInteractionHandler.onInteract(hero, player, hand);
    }

    public static boolean onHurt(HeroEntity hero, DamageSource source, float amount) {
        return HeroCombatHandler.onHurt(hero, source, amount);
    }

    public static void setupHiddenTeam(HeroEntity hero) {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.level == null) return;
        net.minecraft.world.scores.Scoreboard scoreboard = mc.level.getScoreboard();
        String teamName = "hero_hidden_hud";
        net.minecraft.world.scores.PlayerTeam team = scoreboard.getPlayerTeam(teamName);
        if (team == null) {
            team = scoreboard.addPlayerTeam(teamName);
            team.setNameTagVisibility(net.minecraft.world.scores.Team.Visibility.NEVER);
            team.setCollisionRule(net.minecraft.world.scores.Team.CollisionRule.NEVER);
        }
        scoreboard.addPlayerToTeam(hero.getStringUUID(), team);
    }

    public static void handlePlayerInvitation(HeroEntity hero, Player player, BlockPos pos, int actionType) {
        BlockPos currentInvitedPos = hero.getInvitedPos();
        if (currentInvitedPos != null && currentInvitedPos.equals(pos)) {
            hero.setInvitedPos(null);
            hero.setInvitedAction(0);

            if (hero.isPassenger()) {
                hero.stopRiding();
            }

            player.sendSystemMessage(Component.translatable("message.herobrine_companion.invite_cancel"));
            return;
        }

        hero.setInvitedPos(pos);
        hero.setInvitedAction(actionType);

        if (actionType == 2) {
            if (hero.isFloating()) {
                hero.setFloating(false);
                hero.setNoGravity(false);
            }
        }

        hero.playSound(net.minecraft.sounds.SoundEvents.EXPERIENCE_ORB_PICKUP, 1.0F, 1.0F);

        String baseKey = switch (actionType) {
            case 1 -> "message.herobrine_companion.invite_inspect";
            case 2 -> "message.herobrine_companion.invite_rest";
            case 3 -> "message.herobrine_companion.invite_guard";
            default -> "message.herobrine_companion.invite_confirm";
        };

        int variant = hero.getRandom().nextInt(3) + 1;
        player.sendSystemMessage(Component.translatable(baseKey + "_" + variant));
    }
}