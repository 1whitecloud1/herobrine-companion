package com.whitecloud233.herobrine_companion.entity.ai.learning;

import com.whitecloud233.herobrine_companion.client.service.LLMConfig;
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
import net.minecraft.world.item.UseAnim;
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

    private static void triggerObserverDialogue(HeroEntity hero, ServerPlayer player, String aiPrompt, String fallbackKey, int variants) {
        if (!HeroDialogueHandler.canSpeak(hero)) return;
        boolean isAIEnabled = HeroDialogueHandler.isAIEnabled();

        if (isAIEnabled) {
            HeroDialogueHandler.triggerAIObservation(hero, player, aiPrompt);
        } else {
            if (variants > 1) {
                HeroDialogueHandler.speakRandom(hero, player, fallbackKey, variants);
            } else {
                HeroDialogueHandler.speak(hero, player, fallbackKey);
            }
        }
    }

    public static void tick(HeroEntity hero) {
        if (hero.level().isClientSide) return;
        if (hero.tickCount % 10 != 0) return;

        List<ServerPlayer> players = hero.level().getEntitiesOfClass(ServerPlayer.class, hero.getBoundingBox().inflate(32));
        for (ServerPlayer player : players) {
            observePlayerState(hero, player);
            observePlayerFocus(hero, player);
            observePlayerInventory(hero, player);
            observeCombat(hero, player);
            observePlayerActions(hero, player);
            observeExtendedActions(hero, player);
            observePlayerMovement(hero, player);
            observeEnvironment(hero, player);
        }
    }

    private static void observeExtendedActions(HeroEntity hero, ServerPlayer player) {
        if (player.isPassenger() && player.getVehicle() != null) {
            if (!isOnCooldown(player, "riding", 2400)) {
                String vehicleName = player.getVehicle().getName().getString();
                triggerObserverDialogue(hero, player, "The player is riding on [" + vehicleName + "].", "message.herobrine_companion.action_riding", 2);
                setCooldown(player, "riding");
            }
        }

        if (player.fishing != null) {
            if (!isOnCooldown(player, "fishing", 2400)) {
                triggerObserverDialogue(hero, player, "The player is fishing.", "message.herobrine_companion.action_fishing", 2);
                setCooldown(player, "fishing");
            }
        }

        if (player.isUsingItem()) {
            ItemStack usingItem = player.getUseItem();
            if (usingItem.getUseAnimation() == UseAnim.EAT) {
                if (!isOnCooldown(player, "eating", 1200)) {
                    String itemName = usingItem.getHoverName().getString();
                    triggerObserverDialogue(hero, player, "The player is eating [" + itemName + "].", "message.herobrine_companion.action_eating", 2);
                    setCooldown(player, "eating");
                }
            } else if (usingItem.getUseAnimation() == UseAnim.DRINK) {
                if (!isOnCooldown(player, "drinking", 1200)) {
                    String itemName = usingItem.getHoverName().getString();
                    triggerObserverDialogue(hero, player, "The player is drinking [" + itemName + "].", "message.herobrine_companion.action_drinking", 2);
                    setCooldown(player, "drinking");
                }
            }
        }

        if (player.isCrouching()) {
            if (!isOnCooldown(player, "sneaking", 1200)) {
                triggerObserverDialogue(hero, player, "The player is sneaking around.", "message.herobrine_companion.action_sneaking", 2);
                setCooldown(player, "sneaking");
            }
        }
    }

    private static void observePlayerState(HeroEntity hero, ServerPlayer player) {
        if (isOnCooldown(player, "state", 2400)) return;

        boolean isInTrouble = false;

        if (player.isOnFire()) {
            triggerObserverDialogue(hero, player, "The player fell into the fire and is burning.", "message.herobrine_companion.observe_fire", 1);
            isInTrouble = true;
        } else if (player.isFreezing()) {
            triggerObserverDialogue(hero, player, "The player is freezing in the extreme cold.", "message.herobrine_companion.observe_freeze", 1);
            isInTrouble = true;
        } else if (player.fallDistance > 5.0f) {
            triggerObserverDialogue(hero, player, "The player fell from a high place.", "message.herobrine_companion.observe_fall", 1);
            isInTrouble = true;
        } else if (player.getAirSupply() < player.getMaxAirSupply() / 3) {
            triggerObserverDialogue(hero, player, "The player is drowning.", "message.herobrine_companion.observe_drown", 1);
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
        if (target instanceof Monster) {
            hero.getHeroBrain().inputMonsterEmpathy(player.getUUID(), 0.02f);
        }

        if (target == hero) {
            if (checkLookTimer(player, target.blockPosition(), 40)) {
                if (!isOnCooldown(player, "focus_self", 4800)) {
                    triggerObserverDialogue(hero, player, "The player is staring at you.", "message.herobrine_companion.observe_self", 1);
                    setCooldown(player, "focus_self");
                    hero.getHeroBrain().inputMeta(player.getUUID(), 0.05f);
                }
            }
            return;
        }

        if (isOnCooldown(player, "focus", 1200)) return;

        if (checkLookTimer(player, target.blockPosition(), 40)) {
            if (target instanceof LivingEntity living) {
                HeroDialogueHandler.onObserveEntity(hero, player, living);
                setCooldown(player, "focus");
            }
        }
    }

    private static void handleBlockFocus(HeroEntity hero, ServerPlayer player, BlockPos pos) {
        BlockState state = hero.level().getBlockState(pos);

        if (state.is(Blocks.BEDROCK) || state.getBlock().getDescriptionId().contains("command_block")) {
            hero.getHeroBrain().inputMeta(player.getUUID(), 0.1f);
        }

        if (isOnCooldown(player, "focus", 1200)) return;

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
                hero.getHeroBrain().inputNostalgia(player.getUUID(), 0.05f);
            }

            if (isOnCooldown(player, "item", 2400)) return;

            if (!currentItem.isEmpty()) {
                HeroDialogueHandler.onObserveItem(hero, player, currentItem);
                setCooldown(player, "item");
            }
        }
    }

    private static void observeCombat(HeroEntity hero, ServerPlayer player) {
        if (isOnCooldown(player, "combat", 1200)) return;

        if (player.getLastHurtByMob() != null && player.tickCount - player.getLastHurtByMobTimestamp() < 100) {
            triggerObserverDialogue(hero, player, "The player is being attacked by monsters and is hurt.", "message.herobrine_companion.observe_combat_hurt", 1);
            hero.getHeroBrain().inputFailure(player.getUUID(), 0.1f);
            setCooldown(player, "combat");
        }
        else if (player.getLastHurtMob() != null && player.tickCount - player.getLastHurtMobTimestamp() < 100) {
            triggerObserverDialogue(hero, player, "The player is fighting against monsters.", "message.herobrine_companion.observe_combat_attack", 1);
            hero.getHeroBrain().inputViolence(player.getUUID(), 0.05f);
            setCooldown(player, "combat");
        }
    }

    private static void observePlayerActions(HeroEntity hero, ServerPlayer player) {
        if (isOnCooldown(player, "action", 1200)) return;

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

            if (!isOnCooldown(player, "building", 2400)) {
                triggerObserverDialogue(hero, player, "The player is building.", "message.herobrine_companion.action_building", 2);
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

            if (!isOnCooldown(player, "exploration", 12000)) {
                triggerObserverDialogue(hero, player, "The player is exploring the world.", "message.herobrine_companion.action_exploring", 2);
                setCooldown(player, "exploration");
            }
        }
    }

    private static void observeEnvironment(HeroEntity hero, ServerPlayer player) {
        if (hero.tickCount % 40 != 0) return;

        List<Entity> items = hero.level().getEntities(hero, hero.getBoundingBox().inflate(16), e -> e instanceof net.minecraft.world.entity.item.ItemEntity);
        if (items.size() > 30) {
            hero.getHeroBrain().inputEntropy(player.getUUID(), 0.1f);
            if (!isOnCooldown(player, "clutter", 2400)) {
                triggerObserverDialogue(hero, player, "There are dropped items all over the ground around the player.", "message.herobrine_companion.observe_clutter", 1);
                setCooldown(player, "clutter");
            }
        }

        List<Entity> tnts = hero.level().getEntities(hero, hero.getBoundingBox().inflate(32), e -> e instanceof net.minecraft.world.entity.item.PrimedTnt);
        if (!tnts.isEmpty()) {
            hero.getHeroBrain().inputEntropy(player.getUUID(), 0.2f);
            if (!isOnCooldown(player, "explosion", 600)) {
                triggerObserverDialogue(hero, player, "A TNT explosion just occurred near the player.", "message.herobrine_companion.observe_explosion", 1);
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
            if (!isOnCooldown(player, "fire_hazard", 1200)) {
                triggerObserverDialogue(hero, player, "A fire has started in the player's area.", "message.herobrine_companion.observe_fire_hazard", 1);
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