package com.whitecloud233.herobrine_companion.entity.logic.data;

import com.whitecloud233.herobrine_companion.entity.HeroEntity;
import com.whitecloud233.herobrine_companion.entity.ai.learning.HeroDialogueHandler;
import com.whitecloud233.herobrine_companion.entity.ai.learning.HeroObserver;
import com.whitecloud233.herobrine_companion.entity.ai.learning.HeroPrankHandler;
import com.whitecloud233.herobrine_companion.entity.logic.HeroInteractionHandler;
import com.whitecloud233.herobrine_companion.world.structure.ModStructures;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
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
        if (hero.tickCount == 20) {
            HeroLifecycleHandler.checkUniqueness(hero);
            if (!hero.isLoadedFromDisk() && hero.getOwnerUUID() != null) {
                Player owner = hero.level().getPlayerByUUID(hero.getOwnerUUID());
                if (owner != null) {
                    HeroStateManager.restoreFromGlobal(hero, owner);
                }
            }
        }

        if (hero.tickCount % 100 == 0) {
            HeroLifecycleHandler.checkUniqueness(hero);
        }

        if (hero.getOwnerUUID() == null) {
            boolean isFreshSpawn = !hero.isLoadedFromDisk();
            boolean safeToBind = isFreshSpawn ? (hero.tickCount > 20) : (hero.tickCount > 600);

            if (safeToBind && hero.tickCount % 100 == 0) {
                findAndSetOwner(hero);
            }
        }

        if (hero.tickCount == 5) {
            HeroDataHandler.syncGlobalTrust(hero);
        }

        // ============== [重构精简核心] 定期备份与托底恢复 ==============
        if (hero.tickCount % 100 == 0) {
            if (hero.getTrustLevel() > 0) {
                // 如果信任度大于0，说明当前实体数据是健康的，执行全局备份
                HeroStateManager.backupToGlobal(hero);
            } else if (hero.getOwnerUUID() != null) {
                // 如果信任度为0（可能发生了异常重置），尝试从全局档案托底恢复
                Player owner = hero.level().getPlayerByUUID(hero.getOwnerUUID());
                if (owner != null) {
                    HeroStateManager.restoreFromGlobal(hero, owner);
                }
            }
        }
        // ==========================================================

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
                HeroStateManager.restoreFromGlobal(hero, closestPlayer); // 替换了原本的手动恢复逻辑
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