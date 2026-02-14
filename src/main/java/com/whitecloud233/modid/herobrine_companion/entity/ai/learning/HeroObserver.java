package com.whitecloud233.modid.herobrine_companion.entity.ai.learning;

import com.whitecloud233.modid.herobrine_companion.entity.HeroEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class HeroObserver {

    private static final Map<UUID, ItemStack> lastHeldItem = new HashMap<>();
    private static final Map<UUID, BlockPos> lastLookPos = new HashMap<>();
    private static final Map<UUID, Integer> lookTimer = new HashMap<>();

    // [新增] 记录玩家上次的位置，用于计算移动距离
    private static final Map<UUID, Vec3> lastPlayerPos = new HashMap<>();
    // Cooldown map: "UUID_Category" -> Last GameTime
    private static final Map<String, Long> cooldownMap = new HashMap<>();

    public static void tick(HeroEntity hero) {
        if (hero.level().isClientSide) return; // Only server-side
        // Removed !hero.isCompanionMode() check to allow observation in all modes

        if (hero.tickCount % 10 != 0) return; // Run every 0.5s

        // [修改] 扫描周围所有玩家，而不仅仅是 Owner
        List<ServerPlayer> nearbyPlayers = hero.level().getEntitiesOfClass(ServerPlayer.class, hero.getBoundingBox().inflate(32));
        
        for (ServerPlayer player : nearbyPlayers) {
            observePlayerState(hero, player);
            observePlayerFocus(hero, player);
            observePlayerInventory(hero, player);
            observeCombat(hero, player);
            observePlayerActions(hero, player);
            observePlayerMovement(hero, player); // [新增] 移动检测
            observeEnvironment(hero, player); // [新增] 环境检测
        }
    }


    private static void observePlayerState(HeroEntity hero, ServerPlayer player) {
        // [修改] 增加状态观察的冷却时间，从 200 ticks (10s) 增加到 1200 ticks (60s)
        if (isOnCooldown(player, "state", 1200)) return; 

        boolean isInTrouble = false;

        if (player.isOnFire()) {
            HeroDialogueHandler.speak(hero, player, "message.herobrine_companion.observe_fire");
            isInTrouble = true;
        } else if (player.isFreezing()) {
            HeroDialogueHandler.speak(hero, player, "message.herobrine_companion.observe_freeze");
            isInTrouble = true;
        } else if (player.fallDistance > 5.0f) {
            HeroDialogueHandler.speak(hero, player, "message.herobrine_companion.observe_fall");
            isInTrouble = true;
        } else if (player.getAirSupply() < player.getMaxAirSupply() / 3) {
            HeroDialogueHandler.speak(hero, player, "message.herobrine_companion.observe_drown");
            isInTrouble = true;
        }

        if (isInTrouble) {
            hero.getHeroBrain().inputFailure(player.getUUID(), 0.1f);
            setCooldown(player, "state");
        }
    }

    private static void observePlayerFocus(HeroEntity hero, ServerPlayer player) {
        // Raycast from player eyes
        double reach = 24.0; // Increased reach to 24 blocks
        Vec3 eyePos = player.getEyePosition();
        Vec3 viewVec = player.getViewVector(1.0F);
        Vec3 endVec = eyePos.add(viewVec.scale(reach));

        // 1. Check Blocks
        BlockHitResult blockHit = hero.level().clip(new ClipContext(eyePos, endVec, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));

        // 2. Check Entities
        AABB searchBox = player.getBoundingBox().expandTowards(viewVec.scale(reach)).inflate(1.0D);
        EntityHitResult entityHit = ProjectileUtil.getEntityHitResult(hero.level(), player, eyePos, endVec, searchBox, (e) -> !e.isSpectator() && e.isPickable());

        // Prioritize entity hit if it is closer than block hit
        if (entityHit != null) {
            double entityDist = eyePos.distanceToSqr(entityHit.getLocation());
            double blockDist = blockHit.getType() != HitResult.Type.MISS ? eyePos.distanceToSqr(blockHit.getLocation()) : Double.MAX_VALUE;

            if (entityDist < blockDist) {
                handleEntityFocus(hero, player, entityHit.getEntity());
                return;
            }
        }

        if (blockHit.getType() != HitResult.Type.MISS) {
            handleBlockFocus(hero, player, blockHit.getBlockPos());
        } else {
            resetLookTimer(player);
        }
    }

    private static void handleEntityFocus(HeroEntity hero, ServerPlayer player, Entity target) {
        // [修复] 移除强制转头逻辑
        // 之前这里会强制 Hero 看向玩家正在看的东西，导致 Hero 频繁低头看玩家脚下的东西
        // if (player.getUUID().equals(hero.getOwnerUUID())) {
        //    hero.getLookControl().setLookAt(target, 30.0F, 30.0F);
        // }

        // [Learning] Looking at monsters implies interest or threat assessment
        if (target instanceof Monster) {
            hero.getHeroBrain().inputMonsterEmpathy(player.getUUID(), 0.02f);
        }

        if (target == hero) {
            // [修改] 增加对视的触发时间阈值， 40 ticks (2s)
            // [修改] 增加对视的冷却时间，从 600 ticks (30s) 增加到 2400 ticks (2m)
            if (checkLookTimer(player, target.blockPosition(), 40)) {
                if (!isOnCooldown(player, "focus_self", 2400)) {
                    HeroDialogueHandler.speak(hero, player, "message.herobrine_companion.observe_self");
                    setCooldown(player, "focus_self");
                    // [修复] 移除强制对视
                    // if (player.getUUID().equals(hero.getOwnerUUID())) {
                    //    hero.getLookControl().setLookAt(player, 30.0F, 30.0F);
                    // }
                    // [Learning] Player looking at Herobrine -> Respect/Attention
                    hero.getHeroBrain().inputMeta(player.getUUID(), 0.05f);
                }
            }
            return;
        }

        if (isOnCooldown(player, "focus", 600)) return; // 30s cooldown for focus comments

        if (checkLookTimer(player, target.blockPosition(), 40)) { // 2 seconds looking at entity
            if (target instanceof LivingEntity living) {
                HeroDialogueHandler.onObserveEntity(hero, player, living);
                setCooldown(player, "focus");
            }
        }
    }

    private static void handleBlockFocus(HeroEntity hero, ServerPlayer player, BlockPos pos) {
        // [修复] 移除强制转头逻辑
        // 之前这里会强制 Hero 看向玩家正在看的方块
        // if (player.getUUID().equals(hero.getOwnerUUID())) {
        //    hero.getLookControl().setLookAt(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 30.0F, 30.0F);
        // }
        BlockState state = hero.level().getBlockState(pos);

        // [Learning] Meta blocks
        if (state.is(Blocks.BEDROCK) || state.getBlock().getDescriptionId().contains("command_block")) {
            hero.getHeroBrain().inputMeta(player.getUUID(), 0.1f);
        }

        if (isOnCooldown(player, "focus", 600)) return;

        if (checkLookTimer(player, pos, 40)) { // 2 seconds looking at block
            if (!state.isAir()) {
                HeroDialogueHandler.onObserveBlock(hero, player, state);
                setCooldown(player, "focus");
            }
        }
    }

    private static void observePlayerInventory(HeroEntity hero, ServerPlayer player) {
        ItemStack currentItem = player.getMainHandItem();
        ItemStack lastItem = lastHeldItem.getOrDefault(player.getUUID(), ItemStack.EMPTY);

        // Only react if the item has changed
        if (!ItemStack.isSameItem(currentItem, lastItem)) {
            lastHeldItem.put(player.getUUID(), currentItem);

            // [Learning] Nostalgia items
            if (currentItem.is(Items.ENCHANTED_GOLDEN_APPLE)) {
                hero.getHeroBrain().inputNostalgia(player.getUUID(), 0.5f);
            }

            if (isOnCooldown(player, "item", 1200)) return; // 1 minute cooldown for item comments

            if (!currentItem.isEmpty()) {
                HeroDialogueHandler.onObserveItem(hero, player, currentItem);
                setCooldown(player, "item");
            }
        }
    }

    private static void observeCombat(HeroEntity hero, ServerPlayer player) {
        if (isOnCooldown(player, "combat", 400)) return; // 20s cooldown

        // Check if player is being hurt
        if (player.getLastHurtByMob() != null && player.tickCount - player.getLastHurtByMobTimestamp() < 100) {
            HeroDialogueHandler.speak(hero, player, "message.herobrine_companion.observe_combat_hurt");
            hero.getHeroBrain().inputFailure(player.getUUID(), 0.1f);
            setCooldown(player, "combat");
        }
        // Check if player is attacking
        else if (player.getLastHurtMob() != null && player.tickCount - player.getLastHurtMobTimestamp() < 100) {
            HeroDialogueHandler.speak(hero, player, "message.herobrine_companion.observe_combat_attack");
            hero.getHeroBrain().inputViolence(player.getUUID(), 0.05f);
            setCooldown(player, "combat");
        }
    }

    private static void observePlayerActions(HeroEntity hero, ServerPlayer player) {
        if (isOnCooldown(player, "action", 600)) return; // 30s cooldown

        // Farming: Check if player is using a hoe or planting seeds (simplified check)
        // A more robust way would be to hook into events, but for an observer tick:
        if (player.getMainHandItem().getItem() instanceof net.minecraft.world.item.HoeItem && player.swinging) {
            HeroDialogueHandler.onPlayerFarming(hero, player);
            hero.getHeroBrain().inputCreativity(player.getUUID(), 0.1f);
            setCooldown(player, "action");
        }

        // Mining: Check if player is swinging a pickaxe and looking at a block
        else if (player.getMainHandItem().getItem() instanceof net.minecraft.world.item.PickaxeItem && player.swinging) {
            // We could check if they are actually breaking a block, but swinging is a good enough proxy for "mining action"
            HeroDialogueHandler.onPlayerMining(hero, player);
            hero.getHeroBrain().inputEntropy(player.getUUID(), 0.01f);
            hero.getHeroBrain().inputExploration(player.getUUID(), 0.05f);
            setCooldown(player, "action");
        }

        // Chopping: Check if player is swinging an axe
        else if (player.getMainHandItem().getItem() instanceof net.minecraft.world.item.AxeItem && player.swinging) {
            HeroDialogueHandler.onPlayerChopping(hero, player);
            hero.getHeroBrain().inputEntropy(player.getUUID(), 0.01f);
            setCooldown(player, "action");
        }

        // [新增] Building: Check if player is placing blocks
        // 这是一个简单的启发式检查：如果玩家手里拿着方块，并且正在挥动（放置），我们认为他在建筑
        // 更精确的方法是监听 BlockEvent.EntityPlaceEvent，但那是事件驱动的，这里是 Tick 驱动的观察者
        else if (player.getMainHandItem().getItem() instanceof BlockItem && player.swinging) {
            // 增加创造值
            hero.getHeroBrain().inputCreativity(player.getUUID(), 0.05f);

            // 如果不在冷却中，偶尔评价一下
            if (!isOnCooldown(player, "building", 1200)) { // 1分钟一次
                HeroDialogueHandler.speakRandom(hero, player, "message.herobrine_companion.action_building", 2);
                setCooldown(player, "building");
            }
        }
        // [新增] 攻击检测：如果玩家在挥动剑，增加暴力值
        else if (player.getMainHandItem().getItem() instanceof net.minecraft.world.item.SwordItem && player.swinging) {
            hero.getHeroBrain().inputViolence(player.getUUID(), 0.05f);
        }
    }

    // [新增] 观察玩家移动
    private static void observePlayerMovement(HeroEntity hero, ServerPlayer player) {
        UUID uuid = player.getUUID();
        Vec3 currentPos = player.position();
        Vec3 lastPos = lastPlayerPos.get(uuid);

        if (lastPos == null) {
            lastPlayerPos.put(uuid, currentPos);
            return;
        }

        // 计算距离平方
        double distSqr = currentPos.distanceToSqr(lastPos);

        // 如果移动距离超过 100 格 (10^2)，视为一次有效的探索
        if (distSqr > 100.0) {
            // 增加探索值
            hero.getHeroBrain().inputExploration(player.getUUID(), 0.1f);

            // 更新记录点
            lastPlayerPos.put(uuid, currentPos);

            // [修改] 增加探索对话的冷却时间，从 1200 ticks (1m) 增加到 6000 ticks (5m)
            if (!isOnCooldown(player, "exploration", 6000)) {
                HeroDialogueHandler.speakRandom(hero, player, "message.herobrine_companion.action_exploring", 2);
                setCooldown(player, "exploration");
            }
        }
    }

    // [新增] 环境检测 (爆炸、掉落物、火灾)
    private static void observeEnvironment(HeroEntity hero, ServerPlayer player) {
        if (hero.tickCount % 40 != 0) return; // 每 2 秒检测一次，节省性能

        // 1. 掉落物检测
        List<Entity> items = hero.level().getEntities(hero, hero.getBoundingBox().inflate(16), e -> e instanceof net.minecraft.world.entity.item.ItemEntity);
        if (items.size() > 30) { // 如果掉落物超过 30 个
            hero.getHeroBrain().inputEntropy(player.getUUID(), 0.1f); // 增加混乱值
            if (!isOnCooldown(player, "clutter", 600)) {
                HeroDialogueHandler.speak(hero, player, "message.herobrine_companion.observe_clutter");
                setCooldown(player, "clutter");
            }
        }

        // 2. 爆炸检测 (通过检测 TNT 实体)
        List<Entity> tnts = hero.level().getEntities(hero, hero.getBoundingBox().inflate(32), e -> e instanceof net.minecraft.world.entity.item.PrimedTnt);
        if (!tnts.isEmpty()) {
            hero.getHeroBrain().inputEntropy(player.getUUID(), 0.2f); // 爆炸大幅增加混乱
            if (!isOnCooldown(player, "explosion", 200)) {
                HeroDialogueHandler.speak(hero, player, "message.herobrine_companion.observe_explosion");
                setCooldown(player, "explosion");
            }
        }
        
        // 3. 火灾检测 (随机抽样周围方块)
        int fireCount = 0;
        for (int i = 0; i < 10; i++) {
            BlockPos randomPos = hero.blockPosition().offset(hero.getRandom().nextInt(16) - 8, hero.getRandom().nextInt(8) - 4, hero.getRandom().nextInt(16) - 8);
            if (hero.level().getBlockState(randomPos).is(Blocks.FIRE) || hero.level().getBlockState(randomPos).is(Blocks.SOUL_FIRE)) {
                fireCount++;
            }
        }
        if (fireCount > 3) {
            hero.getHeroBrain().inputEntropy(player.getUUID(), 0.1f);
            if (!isOnCooldown(player, "fire_hazard", 400)) {
                HeroDialogueHandler.speak(hero, player, "message.herobrine_companion.observe_fire_hazard");
                setCooldown(player, "fire_hazard");
            }
        }
    }


    // --- Helpers ---

    private static boolean checkLookTimer(ServerPlayer player, BlockPos pos, int threshold) {
        UUID uuid = player.getUUID();
        BlockPos lastPos = lastLookPos.get(uuid);

        // Allow for small movement (distance < 2.0 blocks squared)
        if (lastPos != null && lastPos.distSqr(pos) < 4.0) {
            int time = lookTimer.getOrDefault(uuid, 0) + 10; // +10 because we run every 10 ticks
            lookTimer.put(uuid, time);
            return time >= threshold;
        } else {
            lastLookPos.put(uuid, pos);
            lookTimer.put(uuid, 0);
            return false;
        }
    }

    private static void resetLookTimer(ServerPlayer player) {
        lastLookPos.remove(player.getUUID());
        lookTimer.remove(player.getUUID());
    }

    private static boolean isOnCooldown(ServerPlayer player, String category, long durationTicks) {
        String key = player.getStringUUID() + "_" + category;
        long currentTime = player.level().getGameTime();
        long lastTime = cooldownMap.getOrDefault(key, 0L);
        return (currentTime - lastTime) < durationTicks;
    }

    private static void setCooldown(ServerPlayer player, String category) {
        String key = player.getStringUUID() + "_" + category;
        cooldownMap.put(key, player.level().getGameTime());
    }
}
