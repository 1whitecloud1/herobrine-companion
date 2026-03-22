package com.whitecloud233.modid.herobrine_companion.client.fight;

import com.whitecloud233.modid.herobrine_companion.entity.HeroEntity;
import net.minecraft.network.chat.Component; // [引用]
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
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