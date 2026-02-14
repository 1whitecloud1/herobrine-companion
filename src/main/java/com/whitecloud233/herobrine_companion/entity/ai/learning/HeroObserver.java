package com.whitecloud233.herobrine_companion.entity.ai.learning;

import com.whitecloud233.herobrine_companion.entity.HeroEntity;
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
    private static final Map<UUID, Vec3> lastPlayerPos = new HashMap<>();
    private static final Map<String, Long> cooldownMap = new HashMap<>();

    public static void tick(HeroEntity hero) {
        if (hero.level().isClientSide) return; 
        if (hero.tickCount % 10 != 0) return; 

        // [修改] 遍历周围所有玩家进行观察，而不仅仅是 Owner
        List<ServerPlayer> players = hero.level().getEntitiesOfClass(ServerPlayer.class, hero.getBoundingBox().inflate(32));
        for (ServerPlayer player : players) {
            observePlayerState(hero, player);
            observePlayerFocus(hero, player);
            observePlayerInventory(hero, player);
            observeCombat(hero, player);
            observePlayerActions(hero, player);
            observePlayerMovement(hero, player);
            observeEnvironment(hero, player);
        }
    }

    private static void observePlayerState(HeroEntity hero, ServerPlayer player) {
        if (isOnCooldown(player, "state", 200)) return; 

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
        double reach = 24.0; 
        Vec3 eyePos = player.getEyePosition();
        Vec3 viewVec = player.getViewVector(1.0F);
        Vec3 endVec = eyePos.add(viewVec.scale(reach));

        BlockHitResult blockHit = hero.level().clip(new ClipContext(eyePos, endVec, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        
        AABB searchBox = player.getBoundingBox().expandTowards(viewVec.scale(reach)).inflate(1.0D);
        EntityHitResult entityHit = ProjectileUtil.getEntityHitResult(hero.level(), player, eyePos, endVec, searchBox, (e) -> !e.isSpectator() && e.isPickable());

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

        if (target instanceof Monster) {
            hero.getHeroBrain().inputMonsterEmpathy(player.getUUID(), 0.02f);
        }

        if (target == hero) {
             if (checkLookTimer(player, target.blockPosition(), 40)) {
                 if (!isOnCooldown(player, "focus_self", 2400)) {
                     HeroDialogueHandler.speak(hero, player, "message.herobrine_companion.observe_self");
                     setCooldown(player, "focus_self");
                     
                     // [修复] 移除强制对视
                     // if (player.getUUID().equals(hero.getOwnerUUID())) {
                     //    hero.getLookControl().setLookAt(player, 30.0F, 30.0F);
                     // }
                     
                     hero.getHeroBrain().inputMeta(player.getUUID(), 0.05f);
                 }
             }
             return;
        }
        
        if (isOnCooldown(player, "focus", 600)) return; 

        if (checkLookTimer(player, target.blockPosition(), 40)) { 
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
        
        if (state.is(Blocks.BEDROCK) || state.getBlock().getDescriptionId().contains("command_block")) {
            hero.getHeroBrain().inputMeta(player.getUUID(), 0.1f);
        }

        if (isOnCooldown(player, "focus", 600)) return;

        if (checkLookTimer(player, pos, 40)) { 
            if (!state.isAir()) {
                HeroDialogueHandler.onObserveBlock(hero, player, state);
                setCooldown(player, "focus");
            }
        }
    }

    private static void observePlayerInventory(HeroEntity hero, ServerPlayer player) {
        ItemStack currentItem = player.getMainHandItem();
        ItemStack lastItem = lastHeldItem.getOrDefault(player.getUUID(), ItemStack.EMPTY);

        if (!ItemStack.isSameItem(currentItem, lastItem)) {
            lastHeldItem.put(player.getUUID(), currentItem);
            
            if (currentItem.is(Items.ENCHANTED_GOLDEN_APPLE)) {
                hero.getHeroBrain().inputNostalgia(player.getUUID(), 0.5f);
            }
            
            if (isOnCooldown(player, "item", 1200)) return; 

            if (!currentItem.isEmpty()) {
                HeroDialogueHandler.onObserveItem(hero, player, currentItem);
                setCooldown(player, "item");
            }
        }
    }

    private static void observeCombat(HeroEntity hero, ServerPlayer player) {
        if (isOnCooldown(player, "combat", 400)) return; 

        if (player.getLastHurtByMob() != null && player.tickCount - player.getLastHurtByMobTimestamp() < 100) {
            HeroDialogueHandler.speak(hero, player, "message.herobrine_companion.observe_combat_hurt");
            hero.getHeroBrain().inputFailure(player.getUUID(), 0.1f);
            setCooldown(player, "combat");
        } 
        else if (player.getLastHurtMob() != null && player.tickCount - player.getLastHurtMobTimestamp() < 100) {
             HeroDialogueHandler.speak(hero, player, "message.herobrine_companion.observe_combat_attack");
             hero.getHeroBrain().inputViolence(player.getUUID(), 0.05f);
             setCooldown(player, "combat");
        }
    }
    
    private static void observePlayerActions(HeroEntity hero, ServerPlayer player) {
        if (isOnCooldown(player, "action", 600)) return; 

        if (player.getMainHandItem().getItem() instanceof net.minecraft.world.item.HoeItem && player.swinging) {
             HeroDialogueHandler.onPlayerFarming(hero, player);
             hero.getHeroBrain().inputCreativity(player.getUUID(), 0.1f);
             setCooldown(player, "action");
        }
        else if (player.getMainHandItem().getItem() instanceof net.minecraft.world.item.PickaxeItem && player.swinging) {
             HeroDialogueHandler.onPlayerMining(hero, player);
              hero.getHeroBrain().inputEntropy(player.getUUID(), 0.01f);
             hero.getHeroBrain().inputExploration(player.getUUID(), 0.05f);
             setCooldown(player, "action");
        }
        else if (player.getMainHandItem().getItem() instanceof net.minecraft.world.item.AxeItem && player.swinging) {
             HeroDialogueHandler.onPlayerChopping(hero, player);
              hero.getHeroBrain().inputEntropy(player.getUUID(), 0.01f);
             setCooldown(player, "action");
        }
        else if (player.getMainHandItem().getItem() instanceof BlockItem && player.swinging) {
            hero.getHeroBrain().inputCreativity(player.getUUID(), 0.05f);
            
            if (!isOnCooldown(player, "building", 1200)) { 
                HeroDialogueHandler.speakRandom(hero, player, "message.herobrine_companion.action_building", 2);
                setCooldown(player, "building");
            }
        }
        else if (player.getMainHandItem().getItem() instanceof net.minecraft.world.item.SwordItem && player.swinging) {
            hero.getHeroBrain().inputViolence(player.getUUID(), 0.05f);
        }
    }
    
    private static void observePlayerMovement(HeroEntity hero, ServerPlayer player) {
        UUID uuid = player.getUUID();
        Vec3 currentPos = player.position();
        Vec3 lastPos = lastPlayerPos.get(uuid);
        
        if (lastPos == null) {
            lastPlayerPos.put(uuid, currentPos);
            return;
        }
        
        double distSqr = currentPos.distanceToSqr(lastPos);
        
        if (distSqr > 100.0) {
            hero.getHeroBrain().inputExploration(player.getUUID(), 0.1f);
            
            lastPlayerPos.put(uuid, currentPos);
            
            if (!isOnCooldown(player, "exploration", 6000)) {
                HeroDialogueHandler.speakRandom(hero, player, "message.herobrine_companion.action_exploring", 2);
                setCooldown(player, "exploration");
            }
        }
    }
    
    private static void observeEnvironment(HeroEntity hero, ServerPlayer player) {
        if (hero.tickCount % 40 != 0) return; 

        List<Entity> items = hero.level().getEntities(hero, hero.getBoundingBox().inflate(16), e -> e instanceof net.minecraft.world.entity.item.ItemEntity);
        if (items.size() > 30) { 
            hero.getHeroBrain().inputEntropy(player.getUUID(), 0.1f); 
            if (!isOnCooldown(player, "clutter", 600)) {
                HeroDialogueHandler.speak(hero, player, "message.herobrine_companion.observe_clutter");
                setCooldown(player, "clutter");
            }
        }

        List<Entity> tnts = hero.level().getEntities(hero, hero.getBoundingBox().inflate(32), e -> e instanceof net.minecraft.world.entity.item.PrimedTnt);
        if (!tnts.isEmpty()) {
            hero.getHeroBrain().inputEntropy(player.getUUID(), 0.2f); 
            if (!isOnCooldown(player, "explosion", 200)) {
                HeroDialogueHandler.speak(hero, player, "message.herobrine_companion.observe_explosion");
                setCooldown(player, "explosion");
            }
        }
        
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

    private static boolean checkLookTimer(ServerPlayer player, BlockPos pos, int threshold) {
        UUID uuid = player.getUUID();
        BlockPos lastPos = lastLookPos.get(uuid);
        
        if (lastPos != null && lastPos.distSqr(pos) < 4.0) {
            int time = lookTimer.getOrDefault(uuid, 0) + 10;
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
