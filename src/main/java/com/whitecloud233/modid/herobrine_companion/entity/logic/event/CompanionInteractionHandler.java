package com.whitecloud233.modid.herobrine_companion.entity.logic.event;

import com.whitecloud233.modid.herobrine_companion.HerobrineCompanion;
import com.whitecloud233.modid.herobrine_companion.entity.HeroEntity;
import com.whitecloud233.modid.herobrine_companion.entity.logic.data.HeroLogic;
import com.whitecloud233.modid.herobrine_companion.item.HeroSummonItem;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.AbstractMinecart;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = HerobrineCompanion.MODID)
public class CompanionInteractionHandler {

    @SubscribeEvent
    public static void onPlayerInteractEntity(PlayerInteractEvent.EntityInteract event) {
        Player player = event.getEntity();
        if (!(player.getMainHandItem().getItem() instanceof HeroSummonItem) || !player.isShiftKeyDown()) return;

        Entity target = event.getTarget();
        if (target instanceof Boat || target instanceof AbstractMinecart) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);

            if (!player.level().isClientSide) {
                if (target.isVehicle()) {
                    player.sendSystemMessage(Component.translatable("message.herobrine_companion.vehicle_occupied"));
                    return;
                }

                ServerLevel level = (ServerLevel) player.level();
                HeroEntity hero = findOwnedOrNearestHero(level, player);

                if (hero != null) {
                    hero.stopRiding();
                    hero.teleportTo(target.getX(), target.getY(), target.getZ());
                    if (hero.startRiding(target, true)) {
                        player.sendSystemMessage(Component.translatable("message.herobrine_companion.hero_ride_vehicle"));
                    }
                } else {
                    player.sendSystemMessage(Component.translatable("message.herobrine_companion.hero_not_found"));
                }
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getLevel().isClientSide) return;

        BlockPos pos = event.getPos();
        ServerLevel level = (ServerLevel) event.getLevel();
        Player player = event.getEntity();

        for (var entity : level.getAllEntities()) {
            if (entity instanceof HeroEntity hero && hero.isCompanionMode()) {
                BlockPos invitedPos = hero.getInvitedPos();
                if (invitedPos != null && invitedPos.equals(pos) && hero.getInvitedAction() == 2) {
                    if (hero.isPassenger()) {
                        if (player.getMainHandItem().getItem() instanceof HeroSummonItem && player.isShiftKeyDown()) {
                            if (hero.getOwnerUUID() == null || hero.getOwnerUUID().equals(player.getUUID())) {
                                HeroLogic.handlePlayerInvitation(hero, player, pos, 0);
                                event.setCanceled(true);
                                event.setCancellationResult(InteractionResult.SUCCESS);
                                return;
                            }
                        }

                        event.getEntity().displayClientMessage(Component.translatable("message.herobrine_companion.seat_occupied"), true);
                        event.setCanceled(true);
                        event.setCancellationResult(InteractionResult.FAIL);
                        return;
                    }
                }
            }
        }
    }

    private static HeroEntity findOwnedOrNearestHero(ServerLevel level, Player player) {
        HeroEntity nearestHero = null;
        double minDistance = Double.MAX_VALUE;

        for (var entity : level.getAllEntities()) {
            if (entity instanceof HeroEntity h) {
                if (h.getOwnerUUID() != null && h.getOwnerUUID().equals(player.getUUID())) return h; // 直接找到主人的伴侣

                double dist = h.distanceToSqr(player);
                if (dist < 4096.0D && dist < minDistance) {
                    minDistance = dist;
                    nearestHero = h;
                }
            }
        }

        if (nearestHero != null && nearestHero.getOwnerUUID() == null) {
            nearestHero.setOwnerUUID(player.getUUID());
        }
        return nearestHero;
    }
}