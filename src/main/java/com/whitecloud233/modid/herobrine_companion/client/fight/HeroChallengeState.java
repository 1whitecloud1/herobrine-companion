package com.whitecloud233.modid.herobrine_companion.client.fight;

import com.whitecloud233.modid.herobrine_companion.entity.HeroEntity;
import net.minecraft.network.chat.Component; // [引用]
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.event.level.BlockEvent;

@Mod.EventBusSubscriber(modid = "herobrine_companion", bus = Mod.EventBusSubscriber.Bus.FORGE)
public class HeroChallengeState {

    @SubscribeEvent
    public static void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        if (event.getEntity() instanceof Player player) {
            if (player.getPersistentData().getBoolean("IsChallengeActive")) {
                if (!player.isCreative() && !player.isSpectator()) {
                    event.setCanceled(true);
                    if (!player.level().isClientSide) {
                        // 使用 translatable 替换 literal
                        player.displayClientMessage(Component.translatable("message.herobrine_companion.challenge.no_place"), true);
                    }
                }
            }
        }
    }

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
                com.whitecloud233.modid.herobrine_companion.client.fight.animation.ArenaCollapseManager.tickCollapse(hero, level, timer);
            }

            // ... 前面的方块乱飞爆炸代码保持不变 ...

            // 【新增】：在第 200 帧（大约 10 秒，玩家掉入虚空深处时），强制丢出死机界面包！
            if (timer == 200) {
                com.whitecloud233.modid.herobrine_companion.network.PacketHandler.sendToTracking(
                        new com.whitecloud233.modid.herobrine_companion.client.fight.network.SPacketFakeCrash(),
                        hero
                );
            }

            // 【核心修改】：删除原本 timer >= 300 自动调用 endChallenge 的代码！
            // 改为一个极长的保险丝（1200帧 = 60秒）。
            // 正常情况下客户端播完 25 秒的演出就会发包传回主世界，这个只用来防止玩家断网或卡死在虚空里。
            if (timer >= 1200) {
                hero.getPersistentData().remove("FakeOutTimer");
                com.whitecloud233.modid.herobrine_companion.client.fight.HeroChallengeManager.endChallenge(hero, true);
            }

            // 假死演出期间，直接 return，冻结下方所有的挑战检测逻辑！
            return;
        }



        // ... 下方是你原本的 IS_CHALLENGE_ACTIVE 等逻辑保持不变
        if (hero.getPersistentData().getBoolean("IsChallengeActive")) {
            if (!hero.getEntityData().get(HeroEntity.IS_CHALLENGE_ACTIVE)) {
                hero.getEntityData().set(HeroEntity.IS_CHALLENGE_ACTIVE, true);
                int savedTicks = hero.getPersistentData().getInt("ChallengePhaseTicks");
                hero.getEntityData().set(HeroEntity.CHALLENGE_TICKS, savedTicks);
            }
        }

        if (!hero.getEntityData().get(HeroEntity.IS_CHALLENGE_ACTIVE)) return;

        hero.setNoGravity(true);
        hero.setDeltaMovement(0, hero.getDeltaMovement().y, 0);

        if (!(hero.getMoveControl() instanceof ChallengeMoveControl)) {
            hero.moveControl = new ChallengeMoveControl(hero);
        }

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
            hero.moveControl = new ChallengeMoveControl(hero);
            hero.goalSelector.addGoal(1, new com.whitecloud233.modid.herobrine_companion.client.fight.goal.HeroPhase1Goal(hero));
        }
    }

    public static class ChallengeMoveControl extends MoveControl {
        public ChallengeMoveControl(HeroEntity hero) { super(hero); }
        @Override
        public void tick() {}
    }
}