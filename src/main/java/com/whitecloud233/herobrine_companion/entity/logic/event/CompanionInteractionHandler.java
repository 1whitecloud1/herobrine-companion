package com.whitecloud233.herobrine_companion.entity.logic.event;

import com.whitecloud233.herobrine_companion.HerobrineCompanion;
import com.whitecloud233.herobrine_companion.entity.HeroEntity;
import com.whitecloud233.herobrine_companion.entity.logic.data.HeroLogic;
import com.whitecloud233.herobrine_companion.item.HeroSummonItem;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.AbstractMinecart;
import net.minecraft.world.entity.vehicle.Boat;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

@EventBusSubscriber(modid = HerobrineCompanion.MODID)
public class CompanionInteractionHandler {

    // [新增] 玩家右键实体事件 (处理载具邀请)
    @SubscribeEvent
    public static void onPlayerInteractEntity(PlayerInteractEvent.EntityInteract event) {
        Player player = event.getEntity();

        // 检查手持物品
        if (!(player.getMainHandItem().getItem() instanceof HeroSummonItem)) return;

        // [修改] 必须按住 Shift 才能触发 Hero 骑乘
        if (!player.isShiftKeyDown()) return;

        Entity target = event.getTarget();
        // 检查目标是否是载具 (船或矿车)
        if (target instanceof Boat || target instanceof AbstractMinecart) {

            // 取消事件，防止玩家自己坐上去
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);

            // 只在服务端执行逻辑
            if (!player.level().isClientSide) {
                // 检查是否已经有乘客
                if (target.isVehicle()) {
                    player.sendSystemMessage(Component.translatable("message.herobrine_companion.vehicle_occupied"));
                    return;
                }

                ServerLevel level = (ServerLevel) player.level();
                // 寻找 Hero
                HeroEntity hero = findOwnedOrNearestHero(level, player);

                if (hero != null) {
                    // [修复] 强制瞬移到载具位置，并停止骑乘旧物体
                    hero.stopRiding();
                    hero.teleportTo(target.getX(), target.getY(), target.getZ());

                    // 让 Hero 骑乘载具
                    boolean success = hero.startRiding(target, true); // force = true

                    if (success) {
                        player.sendSystemMessage(Component.translatable("message.herobrine_companion.hero_ride_vehicle"));
                    } else {
                        // 如果骑乘失败 (比如距离太远或者其他原因)，尝试再次传送并骑乘
                        // 或者给玩家一个提示
                        // player.sendSystemMessage(Component.literal("Failed to mount."));
                    }
                } else {
                    // [新增] 如果没找到 Hero，提示玩家
                    player.sendSystemMessage(Component.translatable("message.herobrine_companion.hero_not_found"));
                }
            }
        }
    }
    // [新增] 玩家右键方块事件 (防止与 Hero 抢座位)
    @SubscribeEvent
    public static void onPlayerRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getLevel().isClientSide) return;

        BlockPos pos = event.getPos();
        ServerLevel level = (ServerLevel) event.getLevel();
        Player player = event.getEntity();

        // 检查是否有 Hero 正在“占用”这个方块
        for (HeroEntity hero : com.whitecloud233.herobrine_companion.entity.ai.learning.HeroBrain.ACTIVE_HEROES) {
            if (hero.level() == level && hero.isAlive() && hero.isCompanionMode()) {
                BlockPos invitedPos = hero.getInvitedPos();
                // 如果 Hero 被邀请到这个位置，且正在执行“休息”动作 (Action=2)
                if (invitedPos != null && invitedPos.equals(pos) && hero.getInvitedAction() == 2) {
                    // 并且 Hero 确实在骑乘状态 (说明已经坐下了)
                    if (hero.isPassenger()) {
                        // [修改] 如果玩家手持 HeroSummonItem 且按住 Shift，则让 Hero 起来
                        if (player.getMainHandItem().getItem() instanceof HeroSummonItem && player.isShiftKeyDown()) {
                            // 只有主人可以让他起来 (或者没有主人的时候)
                            if (hero.getOwnerUUID() == null || hero.getOwnerUUID().equals(player.getUUID())) {
                                // 复用 HeroLogic 的逻辑来取消邀请
                                HeroLogic.handlePlayerInvitation(hero, player, pos, 0);

                                // 阻止方块的默认交互（比如坐下）
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

        // 💡 顺手优化：使用 ACTIVE_HEROES 高速缓存，抛弃卡顿的 getAllEntities
        for (HeroEntity h : com.whitecloud233.herobrine_companion.entity.ai.learning.HeroBrain.ACTIVE_HEROES) {
            if (h.level() != level || !h.isAlive()) continue;

            // 1. 如果找到主人的伴侣，直接返回
            if (h.getOwnerUUID() != null && h.getOwnerUUID().equals(player.getUUID())) {
                return h;
            }

            // 👇👇👇【核心修复】：如果这个 Hero 是别人的，绝对不能把它当成备选目标！直接跳过！
            if (h.getOwnerUUID() != null) continue;
            // 👆👆👆

            // 2. 只有“完全无主”的野生 Hero，才能作为备选
            double dist = h.distanceToSqr(player);
            if (dist < 4096.0D && dist < minDistance) {
                minDistance = dist;
                nearestHero = h;
            }
        }

        if (nearestHero != null && nearestHero.getOwnerUUID() == null) {
            nearestHero.setOwnerUUID(player.getUUID());
        }
        return nearestHero;
    }
}