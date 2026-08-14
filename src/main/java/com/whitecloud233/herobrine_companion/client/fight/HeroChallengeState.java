package com.whitecloud233.herobrine_companion.client.fight;

import com.whitecloud233.herobrine_companion.entity.HeroEntity;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.entity.player.Player;

// NeoForge 1.21.1 事件相关类
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.BlockEvent;

@EventBusSubscriber(modid = "herobrine_companion")
public class HeroChallengeState {

    // ==========================================
    // 【新增】：方块放置拦截
    // ==========================================
    @SubscribeEvent
    public static void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        if (event.getEntity() instanceof Player player) {
            if (player.getPersistentData().getBoolean("IsChallengeActive")) {
                if (!player.isCreative() && !player.isSpectator()) {
                    event.setCanceled(true);
                    if (!player.level().isClientSide) {
                        player.displayClientMessage(Component.translatable("message.herobrine_companion.challenge.no_place"), true);
                    }
                }
            }
        }
    }

    // ==========================================
    // 【新增】：方块破坏拦截
    // ==========================================
    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        Player player = event.getPlayer();
        if (player != null && player.getPersistentData().getBoolean("IsChallengeActive")) {
            if (!player.isCreative() && !player.isSpectator()) {
                event.setCanceled(true);
                if (!player.level().isClientSide) {
                    player.displayClientMessage(Component.translatable("message.herobrine_companion.challenge.no_break"), true);
                }
            }
        }
    }

    // 在 HeroEntity 的 tick 中调用，实时守护状态
    public static void tick(HeroEntity hero) {
        // ==========================================
        // 【新增】：世界剥落与虚空坠落服务端演出
        // ==========================================
        if (hero.getPersistentData().getBoolean("IsFakeOutPhase")) {
            int timer = hero.getPersistentData().getInt("FakeOutTimer");
            timer++;
            hero.getPersistentData().putInt("FakeOutTimer", timer);

            ServerLevel level = (ServerLevel) hero.level();

            // 在第 60 帧到 135 帧（经过 3 秒死寂后），世界从边缘开始坍塌！
            if (timer >= 60 && timer <= 135) {
                // 【调用新类】：将坍塌逻辑委托给专门的崩坏管理器，以玩家为中心引爆！
                com.whitecloud233.herobrine_companion.client.fight.animation.ArenaCollapseManager.tickCollapse(hero, level, timer);
            }

            // ==========================================
            // 【修改后（正确）：强制精准发包】
            // ==========================================
            // 【核心修复】：在第 200 帧直接定向发送给仍处于假死演出的挑战玩家。
            // 不能依赖 TRACKING_ENTITY，否则玩家掉入虚空后可能因为脱离 Boss 跟踪范围而收不到假崩溃包。
            if (timer == 200) {
                for (ServerPlayer player : level.players()) {
                    if (player.getPersistentData().getBoolean("HeroFakeOutPhase")) {
                        com.whitecloud233.herobrine_companion.network.PacketHandler.sendToPlayer(
                                new com.whitecloud233.herobrine_companion.fight.network.SPacketFakeCrash(),
                                player
                        );
                    }
                }
            }

            // 改为一个极长的保险丝（1200帧 = 60秒）。
            // 正常情况下客户端播完 25 秒的演出就会发包传回主世界，这个只用来防止玩家断网或卡死在虚空里。
            if (timer >= 1200) {
                hero.getPersistentData().remove("FakeOutTimer");
                com.whitecloud233.herobrine_companion.client.fight.HeroChallengeManager.endChallenge(hero, true);
            }

            // 假死演出期间，直接 return，冻结下方所有的挑战检测逻辑！
            return;
        }

        // 1. 状态自愈
        if (hero.getPersistentData().getBoolean("IsChallengeActive")) {
            if (!hero.getEntityData().get(HeroEntity.IS_CHALLENGE_ACTIVE)) {
                hero.getEntityData().set(HeroEntity.IS_CHALLENGE_ACTIVE, true);
                int savedTicks = hero.getPersistentData().getInt("ChallengePhaseTicks");
                hero.getEntityData().set(HeroEntity.CHALLENGE_TICKS, savedTicks);
            }
        }

        if (!hero.getEntityData().get(HeroEntity.IS_CHALLENGE_ACTIVE)) return;

        // 2. 物理状态强制锁定
        hero.setNoGravity(true);
        hero.setDeltaMovement(0, hero.getDeltaMovement().y, 0);

        // 3. 强制劫持移动控制器
        if (!(hero.getMoveControl() instanceof ChallengeMoveControl)) {
            hero.setMoveControl(new ChallengeMoveControl(hero));
        }

        // 4. 禁空领域 - 持续压制玩家飞行
        if (hero.level() instanceof ServerLevel serverLevel) {
            for (ServerPlayer player : serverLevel.players()) {
                if (player.getPersistentData().getBoolean("IsChallengeActive")) {
                    if (!player.isCreative() && !player.isSpectator()) {
                        boolean updateNeeded = false;

                        if (player.getAbilities().mayfly || player.getAbilities().flying) {
                            player.getAbilities().mayfly = false;
                            player.getAbilities().flying = false;
                            updateNeeded = true;
                        }

                        if (updateNeeded) {
                            player.onUpdateAbilities();
                            player.displayClientMessage(Component.translatable("message.herobrine_companion.challenge.no_fly"), true);
                        }

                        if (player.isFallFlying()) {
                            player.stopFallFlying();
                            player.displayClientMessage(Component.translatable("message.herobrine_companion.challenge.no_elytra"), true);
                        }
                    }
                }
            }
        }
    }

    // 在 HeroEntity 读取 NBT 时调用
    public static void onRestoreFromDisk(HeroEntity hero) {
        if (hero.getPersistentData().getBoolean("IsChallengeActive")) {
            hero.getEntityData().set(HeroEntity.IS_CHALLENGE_ACTIVE, true);
            int savedTicks = hero.getPersistentData().getInt("ChallengePhaseTicks");
            hero.getEntityData().set(HeroEntity.CHALLENGE_TICKS, savedTicks);

            hero.setNoGravity(true);
            hero.goalSelector.removeAllGoals(goal -> true);
            hero.targetSelector.removeAllGoals(goal -> true);
            hero.setTarget(null);
            hero.getNavigation().stop();
            hero.setMoveControl(new ChallengeMoveControl(hero));
            hero.goalSelector.addGoal(1, new com.whitecloud233.herobrine_companion.client.fight.goal.HeroPhase1Goal(hero));
        }
    }

    public static class ChallengeMoveControl extends MoveControl {
        public ChallengeMoveControl(HeroEntity hero) {
            super(hero);
        }
        @Override
        public void tick() {}
    }
}