package com.whitecloud233.herobrine_companion.entity.logic;

import com.whitecloud233.herobrine_companion.entity.HeroEntity;
import com.whitecloud233.herobrine_companion.entity.ai.learning.HeroDialogueHandler;
import com.whitecloud233.herobrine_companion.entity.ai.learning.HeroObserver;
import com.whitecloud233.herobrine_companion.entity.ai.learning.HeroPrankHandler;
import com.whitecloud233.herobrine_companion.world.structure.ModStructures;
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
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.Team;

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
        if (hero.tickCount == 20) {
            HeroLifecycleHandler.checkUniqueness(hero);
            // [双重保险] 如果 Spawner 已经设置了 Owner，这里再次尝试恢复数据
            if (hero.getOwnerUUID() != null) {
                HeroDataHandler.restoreTrustFromPlayer(hero);
            }
        }

        // 2. 自动绑定逻辑
        if (hero.getOwnerUUID() == null) {
            // 如果是从磁盘加载的(isLoadedFromDisk)，且 tick 小于 600 (30秒)，则禁止自动绑定
            boolean isFreshSpawn = !hero.isLoadedFromDisk();
            boolean safeToBind = isFreshSpawn ? (hero.tickCount > 20) : (hero.tickCount > 600);

            if (safeToBind && hero.tickCount % 100 == 0) {
                findAndSetOwner(hero);
            }
        }

        // 3. 数据同步与保存
        if (hero.tickCount == 5) {
            HeroDataHandler.syncGlobalTrust(hero); // S2C 包，同步给客户端显示
        }

        // [警告] 这是一个危险操作：将 Entity 数据保存到 Disk
        // 我们必须确保在此之前，Entity 数据已经从 Disk 恢复了，否则会用 0 覆盖存档
        if (hero.tickCount % 100 == 0) {
            HeroDataHandler.updateGlobalTrust(hero);
        }

        // 4. 动态名字
        if (hero.tickCount % 20 == 0) {
            boolean isEndRing = hero.level().dimension() == ModStructures.END_RING_DIMENSION_KEY;
            String key = isEndRing ? "entity.herobrine_companion.herobrine" : "entity.herobrine_companion.hero";
            // 这里简化处理，根据需求可以加回 Variant 判断

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

                // [BUG 修复核心] 认主成功后，立即从存档恢复该玩家的信任度
                // 注意：这里用 restoreTrustFromPlayer (Load from disk)，而不是 sync (Send packet)
                HeroDataHandler.restoreTrustFromPlayer(hero);

                // 恢复完数据后，再同步给客户端
                HeroDataHandler.syncGlobalTrust(hero);
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
            // 立即保存
            HeroDataHandler.updateGlobalTrust(hero);

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

    // --- 1.21.1 Client Utils ---
    public static void setupHiddenTeam(HeroEntity hero) {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.level == null) return;
        Scoreboard scoreboard = mc.level.getScoreboard();
        String teamName = "hero_hidden_hud";
        PlayerTeam team = scoreboard.getPlayerTeam(teamName);
        if (team == null) {
            team = scoreboard.addPlayerTeam(teamName);
            // 1.21 名字标签可见性设置
            team.setNameTagVisibility(Team.Visibility.NEVER);
            team.setCollisionRule(Team.CollisionRule.NEVER);
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