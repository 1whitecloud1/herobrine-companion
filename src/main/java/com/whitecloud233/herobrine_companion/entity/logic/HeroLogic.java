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

import java.util.List;

public class HeroLogic {

    // --- Tick Logic ---
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
        // [修复] 将检查延迟到 Tick 20，避免 Entity Load 过程中的数据未就绪导致误杀
        if (hero.tickCount == 20) {
            HeroLifecycleHandler.checkUniqueness(hero);
        }

        // [修改] 自动绑定最近的玩家为 Owner
        // [修复] 增加严格判断：只有当没有主人时才尝试。
        // 如果是从磁盘加载的(isLoadedFromDisk)，且 tick 小于 600 (30秒)，则禁止自动绑定，防止 NBT 读取延迟导致抢主人
        if (hero.getOwnerUUID() == null) {
            boolean isFreshSpawn = !hero.isLoadedFromDisk();
            // 如果是新刷怪蛋生成的，tick > 20 就绑定；如果是重载的，必须等待 30秒 确认真的丢了才绑定
            boolean safeToBind = isFreshSpawn ? (hero.tickCount > 20) : (hero.tickCount > 600);

            if (safeToBind && hero.tickCount % 100 == 0) {
                findAndSetOwner(hero);
            }
        }

        // 2. 数据同步 (现在基于 Owner 同步)
        if (hero.tickCount == 5) {
            HeroDataHandler.syncGlobalTrust(hero);
        }
        if (hero.tickCount % 100 == 0) {
            HeroDataHandler.updateGlobalTrust(hero);
        }

        // 3. 动态名字
        if (hero.tickCount % 20 == 0) {
            boolean isEndRing = hero.level().dimension() == ModStructures.END_RING_DIMENSION_KEY;

            String key;
            if (isEndRing) {
                key = "entity.herobrine_companion.herobrine";
            } else {
                int skinVariant = hero.getSkinVariant();
                if (skinVariant == HeroEntity.SKIN_HEROBRINE) {
                    key = "entity.herobrine_companion.herobrine";
                } else if (skinVariant == HeroEntity.SKIN_HERO) {
                    key = "entity.herobrine_companion.hero";
                } else {
                    key = "entity.herobrine_companion.hero";
                }
            }

            Component expectedName = Component.translatable(key);
            if (!hero.getCustomName().equals(expectedName)) {
                hero.setCustomName(expectedName);
            }
        }

        // 4. 维度逻辑
        HeroDimensionHandler.handleVoidProtection(hero);


        // 5. 对话逻辑
        HeroDialogueHandler.tick(hero);

        // 6. 恶作剧逻辑
        HeroPrankHandler.tick(hero);

        // 7. 观察者逻辑
        HeroObserver.tick(hero);

        // 8. 检查并结算待处理的委托奖励
        if (hero.tickCount % 100 == 50) {
            checkPendingQuestRewards(hero);
            checkPendingLoreFragments(hero);
        }

        // [新增] 强制头部朝向修正
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
                // [新增] 切换 Owner 时同步数据
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
            // 立即保存到该玩家的 Profile
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
                // [修改] 传入 Owner UUID
                hero.getHeroBrain().inputLoreFragment(owner.getUUID(), fragmentId);
            }

            if (!list.isEmpty()) {
                hero.level().playSound(null, hero.blockPosition(), net.minecraft.sounds.SoundEvents.PLAYER_LEVELUP, net.minecraft.sounds.SoundSource.NEUTRAL, 0.5f, 1.0f);
            }

            data.remove("HeroPendingLore");
        }
    }

    // --- Forwarding Methods (Facade) ---

    public static InteractionResult onInteract(HeroEntity hero, Player player, InteractionHand hand) {
        return HeroInteractionHandler.onInteract(hero, player, hand);
    }

    public static boolean onHurt(HeroEntity hero, DamageSource source, float amount) {
        return HeroCombatHandler.onHurt(hero, source, amount);
    }

    // --- Client Utils ---
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