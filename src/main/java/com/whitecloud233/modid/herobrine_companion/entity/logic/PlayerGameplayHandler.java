package com.whitecloud233.modid.herobrine_companion.entity.logic;

import com.whitecloud233.modid.herobrine_companion.HerobrineCompanion;
import com.whitecloud233.modid.herobrine_companion.entity.HeroEntity;
import com.whitecloud233.modid.herobrine_companion.entity.ai.learning.HeroDialogueHandler;
import com.whitecloud233.modid.herobrine_companion.event.ModEvents;
import com.whitecloud233.modid.herobrine_companion.item.HeroSummonItem;
import com.whitecloud233.modid.herobrine_companion.item.LoreFragmentItem;
import com.whitecloud233.modid.herobrine_companion.network.PacketHandler;
import com.whitecloud233.modid.herobrine_companion.network.TriggerEternalOathPacket;
import com.whitecloud233.modid.herobrine_companion.util.EndRingContext;
import com.whitecloud233.modid.herobrine_companion.world.structure.ModStructures;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.AbstractMinecart;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerSleepInBedEvent;

@Mod.EventBusSubscriber(modid = HerobrineCompanion.MODID)
public class PlayerGameplayHandler {

    // --- 事件监听 ---

    @SubscribeEvent
    public static void onPlayerJoinWorld(EntityJoinLevelEvent event) {
        // 1.20.1: getLevel()
        if (event.getEntity() instanceof ServerPlayer player && event.getLevel().dimension() == ModStructures.END_RING_DIMENSION_KEY) {
            
            CompoundTag data = player.getPersistentData();

            // [修复] 登录时虚空保护：防止因 disconnect 导致位置未保存而死循环
            if (player.getY() < -50) {
                player.teleportTo(EndRingContext.CENTER_X, EndRingContext.CENTER_Y, EndRingContext.CENTER_Z);
                player.setDeltaMovement(0, 0, 0);
                player.fallDistance = 0;

                // [新增] 同时也把 Hero 拉回来，防止 Hero 丢失
                if (player.level() instanceof ServerLevel serverLevel) {
                    for (var entity : serverLevel.getAllEntities()) {
                        if (entity instanceof HeroEntity hero && hero.isAlive()) {
                            hero.teleportTo(EndRingContext.CENTER_X, EndRingContext.CENTER_Y, EndRingContext.CENTER_Z);
                            hero.setDeltaMovement(0, 0, 0);
                            break;
                        }
                    }
                }

                // [健壮性修复] 如果玩家在虚空上线，且处于唤醒阶段3以上，说明上次可能触发了模拟崩溃但数据未保存
                // 强制标记已崩溃，防止再次触发
                if (data.getInt("WakeUpStage") >= 3) {
                    data.putBoolean("HasSimulatedCrash", true);
                    // 尝试保存一下，防止再次丢失
                    try {
                        player.server.getPlayerList().saveAll();
                    } catch (Exception ignored) {}
                }
            }

            if (!data.contains("EnteredEndRingTime")) {
                data.putLong("EnteredEndRingTime", player.level().getGameTime());
                data.putInt("WakeUpStage", 0);
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        // 1.20.1 必须检查 Phase 和 Side
        if (event.phase != TickEvent.Phase.END || event.side != LogicalSide.SERVER) return;

        // 1.20.1 TickEvent 的 player 字段
        if (!(event.player instanceof ServerPlayer player)) return;

        // 1. 主世界逻辑
        if (player.level().dimension() == Level.OVERWORLD) {
            checkUnstableZone(player);
            checkStillnessForFragment6(player);
            // 注意：原本的 checkHeroRespawn 已删除，由 HeroDimensionHandler 接管跨维度逻辑
        }

        // 2. End Ring 逻辑
        if (player.level().dimension() == ModStructures.END_RING_DIMENSION_KEY) {
            handleEndRingGameplay(player);
        }
    }

    @SubscribeEvent
    public static void onPlayerClone(PlayerEvent.Clone event) {
        CompoundTag original = event.getOriginal().getPersistentData();
        CompoundTag cur = event.getEntity().getPersistentData();

        // 死亡重生时继承 Hero 的携带数据
        if (original.contains("HeroRespawnData")) {
            cur.put("HeroRespawnData", original.getCompound("HeroRespawnData"));
        }
        if (original.contains("HeroPendingRespawn")) {
            cur.putBoolean("HeroPendingRespawn", original.getBoolean("HeroPendingRespawn"));
        }
        if (original.contains("HasSeenUnstableZoneIntro")) {
            cur.putBoolean("HasSeenUnstableZoneIntro", original.getBoolean("HasSeenUnstableZoneIntro"));
        }
        // 修复：继承 EnteredEndRingTime 和 WakeUpStage，防止死亡后重置
        if (original.contains("EnteredEndRingTime")) {
            cur.putLong("EnteredEndRingTime", original.getLong("EnteredEndRingTime"));
        }
        if (original.contains("WakeUpStage")) {
            cur.putInt("WakeUpStage", original.getInt("WakeUpStage"));
        }
        // [新增] 继承模拟崩溃标记
        if (original.contains("HasSimulatedCrash")) {
            cur.putBoolean("HasSimulatedCrash", original.getBoolean("HasSimulatedCrash"));
        }
        // [新增] 继承任务相关数据
        if (original.contains("HeroActiveQuestId")) {
            cur.putInt("HeroActiveQuestId", original.getInt("HeroActiveQuestId"));
        }
        if (original.contains("HeroActiveQuestProgress")) {
            cur.putInt("HeroActiveQuestProgress", original.getInt("HeroActiveQuestProgress"));
        }
        if (original.contains("HeroPendingTrustReward")) {
            cur.putInt("HeroPendingTrustReward", original.getInt("HeroPendingTrustReward"));
        }
        if (original.contains("HeroPendingQuestClear")) {
            cur.putBoolean("HeroPendingQuestClear", original.getBoolean("HeroPendingQuestClear"));
        }
            // [新增] 继承片段四获取标记
        if (original.contains("HasReceivedFragment4")) {
            cur.putBoolean("HasReceivedFragment4", original.getBoolean("HasReceivedFragment4"));
        }
        if (original.contains("HasReceivedFragment6")) {
            cur.putBoolean("HasReceivedFragment6", original.getBoolean("HasReceivedFragment6"));
        }
    }
    // [新增] 玩家睡觉事件
    @SubscribeEvent
    public static void onPlayerSleep(PlayerSleepInBedEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            ServerLevel level = (ServerLevel) player.level();
            for (var entity : level.getAllEntities()) {
                if (entity instanceof HeroEntity hero && hero.isCompanionMode() && hero.getOwnerUUID() != null && hero.getOwnerUUID().equals(player.getUUID())) {
                    HeroDialogueHandler.onSleep(hero, player);
                    break;
                }
            }
            // [新增] 永恒誓约触发逻辑 (片段九)
            CompoundTag data = player.getPersistentData();
            if (!data.getBoolean("HasReceivedFragment9")) {
                // 1% 概率触发 (或者你可以调高一点方便测试)
                if (player.getRandom().nextFloat() < 0.99f) {
                    // 给予物品
                    ItemStack fragment = new ItemStack(HerobrineCompanion.LORE_FRAGMENT.get());
                    CompoundTag tag = new CompoundTag();
                    tag.putString(LoreFragmentItem.LORE_ID_KEY, "fragment_9");
                    fragment.setTag(tag);

                    // [修改] 只有成功放入背包才算获得
                    if (player.getInventory().add(fragment)) {
                        // 标记已获得
                        data.putBoolean("HasReceivedFragment9", true);
                        // 触发客户端屏幕
                        PacketHandler.sendToPlayer(new TriggerEternalOathPacket(), player);
                    } else {
                        // 背包满了，提示玩家
                        player.displayClientMessage(Component.translatable("message.herobrine_companion.inventory_full_lore").withStyle(ChatFormatting.RED), true);
                    }
                }
            }
        }
    }

    // [新增] 怪物死亡事件 (用于触发战斗评论)
    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        if (event.getSource().getEntity() instanceof ServerPlayer player && event.getEntity() instanceof Monster) {
            ServerLevel level = (ServerLevel) player.level();
            for (var entity : level.getAllEntities()) {
                if (entity instanceof HeroEntity hero && hero.isCompanionMode() && hero.getOwnerUUID() != null && hero.getOwnerUUID().equals(player.getUUID())) {
                    // 只有距离比较近才说话
                    if (hero.distanceToSqr(player) < 400) {
                        HeroDialogueHandler.onKillMonster(hero, player);
                    }
                    break;
                }
            }
        }
    }

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
                HeroEntity hero = null;
                
                // 1. 优先找绑定的 Hero
                for (var entity : level.getAllEntities()) {
                    if (entity instanceof HeroEntity h && h.getOwnerUUID() != null && h.getOwnerUUID().equals(player.getUUID())) {
                        hero = h;
                        break;
                    }
                }
                
                // 2. 如果没找到，找最近的 Hero (放宽条件)
                if (hero == null) {
                    HeroEntity nearestHero = null;
                    double minDistance = Double.MAX_VALUE;
                    
                    for (var entity : level.getAllEntities()) {
                        if (entity instanceof HeroEntity h) {
                            double dist = h.distanceToSqr(player);
                            // [修复] 扩大搜索范围到 64 格 (4096.0D)，防止视线内能看到但距离稍远就提示找不到
                            if (dist < 4096.0D && dist < minDistance) {
                                minDistance = dist;
                                nearestHero = h;
                            }
                        }
                    }
                    
                    if (nearestHero != null) {
                        hero = nearestHero;
                        // 顺便绑定一下，防止下次找不到
                        if (hero.getOwnerUUID() == null) {
                            hero.setOwnerUUID(player.getUUID());
                        }
                    }
                }

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
        for (var entity : level.getAllEntities()) {
            if (entity instanceof HeroEntity hero && hero.isCompanionMode()) {
                BlockPos invitedPos = hero.getInvitedPos();
                // 如果 Hero 被邀请到这个位置，且正在执行“休息”动作 (Action=2)
                if (invitedPos != null && invitedPos.equals(pos) && hero.getInvitedAction() == 2) {
                    // 并且 Hero 确实在骑乘状态 (说明已经坐下了)
                    if (hero.isPassenger()) {
                        // [修改] 如果玩家手持 HeroSummonItem 且按住 Shift，则让 Hero 起来
                        if (player.getMainHandItem().getItem() instanceof HeroSummonItem && player.isShiftKeyDown()) {
                            // 只有主人可以让他起来
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

    // --- 内部逻辑 ---

    private static void checkUnstableZone(ServerPlayer player) {
        if (player.level() instanceof ServerLevel serverLevel) {
            ResourceKey<Structure> key = ResourceKey.create(Registries.STRUCTURE, ModStructures.UNSTABLE_ZONE.getId());
            Structure structure = serverLevel.registryAccess().registryOrThrow(Registries.STRUCTURE).get(key);

            if (structure != null && serverLevel.structureManager().getStructureWithPieceAt(player.blockPosition(), structure).isValid()) {
                CompoundTag data = player.getPersistentData();
                if (!data.getBoolean("HasSeenUnstableZoneIntro")) {
                    player.displayClientMessage(Component.translatable("message.herobrine_companion.unstable_zone_intro"), false);
                    data.putBoolean("HasSeenUnstableZoneIntro", true);

                    // [新增] 第一次进入 Unstable Zone 时，如果 Hero 不在附近，强制召唤他到玩家身边
                    summonHeroNearPlayer(serverLevel, player);
                }
            }
        }
    }

    // [新增] 检查静止状态以获取片段六
    private static void checkStillnessForFragment6(ServerPlayer player) {
        CompoundTag data = player.getPersistentData();
        if (data.getBoolean("HasReceivedFragment6")) return;

        // 检查是否是夜晚 (14000 - 22000)
        long time = player.level().getDayTime() % 24000;
        if (time < 14000 || time > 22000) {
            data.putInt("StillTicks", 0);
            return;
        }

        // 检查是否静止 (位置和旋转角变化极小)
        double dx = player.getX() - data.getDouble("LastX");
        double dy = player.getY() - data.getDouble("LastY");
        double dz = player.getZ() - data.getDouble("LastZ");
        double drot = Math.abs(player.getYRot() - data.getFloat("LastYRot")) + Math.abs(player.getXRot() - data.getFloat("LastXRot"));

        if (dx * dx + dy * dy + dz * dz < 0.0001 && drot < 0.1) {
            int stillTicks = data.getInt("StillTicks") + 1;
            data.putInt("StillTicks", stillTicks);

            // 20秒 (400 ticks)
            if (stillTicks >= 400) {
                // 给予片段六
                ItemStack fragment = new ItemStack(HerobrineCompanion.LORE_FRAGMENT.get());
                CompoundTag tag = new CompoundTag();
                tag.putString(LoreFragmentItem.LORE_ID_KEY, "fragment_6");
                fragment.setTag(tag);

                // [修改] 只有成功放入背包才算获得
                if (player.getInventory().add(fragment)) {
                    // 播放音效
                    player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PHANTOM_AMBIENT, SoundSource.AMBIENT, 1.0f, 0.5f);

                    // 发送消息
                    player.sendSystemMessage(Component.translatable("message.herobrine_companion.fragment_6_received").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));

                    data.putBoolean("HasReceivedFragment6", true);
                    data.remove("StillTicks");
                } else {
                    // 背包满了，提示玩家
                    player.displayClientMessage(Component.translatable("message.herobrine_companion.inventory_full_lore").withStyle(ChatFormatting.RED), true);
                    // 重置计时器，让玩家动一下再试，或者保持满状态直到清理
                    // 这里选择重置为 500 (25秒)，给玩家一点时间清理，不用完全重等
                    data.putInt("StillTicks", 500);
                }
            }
        } else {
            data.putInt("StillTicks", 0);
        }

        // 更新上一 tick 的位置
        data.putDouble("LastX", player.getX());
        data.putDouble("LastY", player.getY());
        data.putDouble("LastZ", player.getZ());
        data.putFloat("LastYRot", player.getYRot());
        data.putFloat("LastXRot", player.getXRot());
    }
    private static void summonHeroNearPlayer(ServerLevel level, ServerPlayer player) {
        // 1. 检查 Hero 是否已经存在
        HeroEntity existingHero = null;
        for (var entity : level.getAllEntities()) {
            if (entity instanceof HeroEntity hero) {
                existingHero = hero;
                break;
            }
        }

        Vec3 targetPos = player.position().add(player.getLookAngle().scale(-3.0)); // 玩家身后3格
        // 简单的防卡墙
        BlockPos pos = new BlockPos((int)targetPos.x, (int)targetPos.y, (int)targetPos.z);
        if (!level.isEmptyBlock(pos) || !level.isEmptyBlock(pos.above())) {
            targetPos = player.position();
        }

        if (existingHero != null) {
            // [修改] 无论是否处于陪伴模式，都强制传送过来
            existingHero.teleportTo(targetPos.x, targetPos.y, targetPos.z);
            existingHero.getNavigation().stop();
            existingHero.setTarget(null);
        } else {
            // 如果 Hero 不存在，生成一个新的
            HeroEntity hero = ModEvents.HERO.get().create(level);
            if (hero != null) {
                hero.moveTo(targetPos);
                hero.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), MobSpawnType.EVENT, null, null);
                level.addFreshEntity(hero);
                 // 移除标记新生成的 Hero
            }
        }
    }

    private static void handleEndRingGameplay(ServerPlayer player) {
        CompoundTag data = player.getPersistentData();
        long timeEntered = data.getLong("EnteredEndRingTime");

        // 阶段 1: 30秒后提示 (20 ticks * 30 = 600)
        if (player.level().getGameTime() - timeEntered > 600 && data.getInt("WakeUpStage") == 0) {
            data.putInt("WakeUpStage", 1);
            player.sendSystemMessage(Component.translatable("message.herobrine_companion.system_strange_presence"));
        }

        // 掉入虚空保护 (-50 判定)
        if (player.getY() < -50) {
            handleVoidFall(player, data);
        }

        // 抬头凝视判定 (Stage >= 3)
        if (data.getInt("WakeUpStage") >= 3) {
            handleSkyGaze(player, data);
        }
    }

    private static void handleVoidFall(ServerPlayer player, CompoundTag data) {
        int stage = data.getInt("WakeUpStage");
        boolean hasCrashed = data.getBoolean("HasSimulatedCrash");
        
        // [恢复] 无论什么阶段，先复位到 End Ring 安全地带
        // 这一步必须最先执行，确保玩家位置被修正
        player.teleportTo(EndRingContext.CENTER_X, EndRingContext.CENTER_Y, EndRingContext.CENTER_Z);
        player.setDeltaMovement(0, 0, 0);
        player.fallDistance = 0;

        // [健壮性] 无论是否崩溃，都尝试把 Hero 拉回安全地带，防止 Hero 掉出世界
        ServerLevel level = (ServerLevel) player.level();
        for (var entity : level.getAllEntities()) {
            if (entity instanceof HeroEntity hero && hero.isAlive()) {
                hero.teleportTo(EndRingContext.CENTER_X, EndRingContext.CENTER_Y, EndRingContext.CENTER_Z);
                hero.setDeltaMovement(0, 0, 0);
                hero.addTag(EndRingContext.TAG_FIXED);
                break;
            }
        }

        // [修复] 防止刚登录/重连时因位置未同步或数据未保存导致的死循环崩溃
        // 给玩家 5 秒 (100 ticks) 的缓冲时间，这期间只进行位置修正，不触发崩溃逻辑
        if (player.tickCount < 100) {
            return;
        }

        if (stage >= 3 && !hasCrashed) {
            // 标记已崩溃，防止重复触发
            data.putBoolean("HasSimulatedCrash", true);

            // [关键修复] 立即保存数据！确保 HasSimulatedCrash 标记被写入磁盘
            // 这样即使后续踢出过程异常，或者玩家重连，标记依然存在
            try {
                player.server.getPlayerList().saveAll();
            } catch (Exception ignored) {}

            // [优化] 延迟踢出，确保位置保存
            // 先发送消息模拟崩溃
            player.sendSystemMessage(Component.translatable("message.herobrine_companion.system_server_closed"));

            // 延迟 60 ticks (3秒) 后踢出，给服务器时间保存位置
            player.server.tell(new net.minecraft.server.TickTask(player.server.getTickCount() + 60, () -> {
                if (player.connection != null) {
                    // 尝试再次保存数据，双重保险
                    try {
                        player.server.getPlayerList().saveAll();
                    } catch (Exception ignored) {}
                    player.connection.disconnect(Component.translatable("message.herobrine_companion.system_server_closed"));
                }
            }));
        } else {
            // 还没结束，只是掉下去了，或者已经崩溃过了
            player.sendSystemMessage(Component.translatable("message.herobrine_companion.hero_not_ready2"));
        }
    }

    private static void handleSkyGaze(ServerPlayer player, CompoundTag data) {
        if (player.getXRot() < -60) {
            int gazeTicks = data.getInt("SkyGazeTicks") + 1;
            data.putInt("SkyGazeTicks", gazeTicks);

            if (gazeTicks >= 200) { // 10秒触发
                executeReturnToOverworld(player, data);
                data.remove("SkyGazeTicks");
            }
        } else {
            if (data.contains("SkyGazeTicks")) data.putInt("SkyGazeTicks", 0);
        }
    }

    private static void executeReturnToOverworld(ServerPlayer player, CompoundTag data) {
        ServerLevel overworld = player.server.getLevel(Level.OVERWORLD);
        if (overworld != null) {
            BlockPos spawnPos = overworld.getSharedSpawnPos();
            if (player.getRespawnPosition() != null) spawnPos = player.getRespawnPosition();

            ServerLevel endLevel = player.server.getLevel(ModStructures.END_RING_DIMENSION_KEY);
            if (endLevel != null) {
                for (var entity : endLevel.getAllEntities()) {
                    if (entity instanceof HeroEntity hero && hero.isAlive()) {

                        // [重要逻辑复刻] 1. 保存完整数据
                        CompoundTag heroData = new CompoundTag();
                        hero.saveWithoutId(heroData); // 保存所有 NBT

                        // [重要逻辑复刻] 2. 更新信任度到 NBT 和 全局存档
                        HeroDataHandler.updateGlobalTrust(hero); // 更新到 WorldData
                        heroData.putInt("TrustLevel", hero.getTrustLevel()); // 确保 NBT 里也有

                        // 3. 强制标记状态
                        heroData.putBoolean("CompanionMode", hero.isCompanionMode());
                        heroData.putBoolean("IsFloating", false);
                        // [修复] 保存皮肤状态
                        heroData.putBoolean("UseHerobrineSkin", hero.shouldUseHerobrineSkin());

                        // 4. 存入玩家数据，等待 DimensionChange 事件处理重生
                        data.put("HeroRespawnData", heroData);
                        data.putBoolean("HeroPendingRespawn", true);

                        hero.discard(); // 销毁旧维度的实体
                        break;
                    }
                }
            }

            // 1.20.1 传送写法 (teleportTo 代替 1.21 的 DimensionTransition)
            player.teleportTo(overworld,
                    spawnPos.getX() + 0.5,
                    spawnPos.getY() + 1,
                    spawnPos.getZ() + 0.5,
                    player.getYRot(),
                    player.getXRot()
            );

            // 播放苏醒特效
            player.sendSystemMessage(Component.translatable("message.herobrine_companion.system_wake_up"));
            player.addEffect(new net.minecraft.world.effect.MobEffectInstance(MobEffects.BLINDNESS, 100, 0));
            player.addEffect(new net.minecraft.world.effect.MobEffectInstance(MobEffects.CONFUSION, 200, 0));
            // [新增] 给予片段四：连接的彼端
            if (!data.getBoolean("HasReceivedFragment4")) {
                ItemStack fragment = new ItemStack(HerobrineCompanion.LORE_FRAGMENT.get());
                CompoundTag tag = new CompoundTag();
                tag.putString(LoreFragmentItem.LORE_ID_KEY, "fragment_4");
                fragment.setTag(tag);

                if (player.getInventory().add(fragment)) {
                    // 成功放入背包
                } else {
                    player.drop(fragment, false);
                }

                player.sendSystemMessage(Component.translatable("message.herobrine_companion.chat_hero",
                        Component.translatable("message.herobrine_companion.fragment_4_received")).withStyle(ChatFormatting.YELLOW));

                data.putBoolean("HasReceivedFragment4", true);
            }
        }
    }
}