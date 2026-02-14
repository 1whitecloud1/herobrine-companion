package com.whitecloud233.modid.herobrine_companion.entity.ai.learning;

import com.whitecloud233.modid.herobrine_companion.entity.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.UUID;

public class HeroDialogueHandler {

    private static final String TAG_LAST_SPEECH = "HeroLastSpeechTime";
    private static final long COOLDOWN = 200; // Reduced cooldown for responsiveness

    public static void tick(HeroEntity hero) {
        if (hero.level().isClientSide || !hero.isCompanionMode()) return;

        // Only check occasionally (every 5 seconds)
        if (hero.tickCount % 100 != 0) return;

        UUID ownerUUID = hero.getOwnerUUID();
        if (ownerUUID == null) return;

        Player owner = hero.level().getPlayerByUUID(ownerUUID);
        if (owner instanceof ServerPlayer serverPlayer) {
            checkConditions(hero, serverPlayer);
        }
    }

    private static void checkConditions(HeroEntity hero, ServerPlayer owner) {
        long time = hero.level().getGameTime();
        long last = hero.getPersistentData().getLong(TAG_LAST_SPEECH);

        if (time - last < COOLDOWN) return;

        RandomSource random = hero.getRandom();

        // 1. Low Health (High Priority)
        if (owner.getHealth() < owner.getMaxHealth() * 0.3) {
            speakRandom(hero, owner, "message.herobrine_companion.low_health", 3);
            return;
        }


        // 2. Random Ambient Comments based on Brain State
        if (random.nextFloat() < 0.1) { // 50% chance every 5s
            SimpleNeuralNetwork.MindState state = hero.getHeroBrain().getState();
            handleStateSpeech(hero, owner, state);
        }
    }

    private static void handleStateSpeech(HeroEntity hero, ServerPlayer owner, SimpleNeuralNetwork.MindState state) {
        switch (state) {
            case PROTECTOR -> speakRandom(hero, owner, "message.herobrine_companion.state_protector", 2);
            case JUDGE -> speakRandom(hero, owner, "message.herobrine_companion.state_judge", 2);
            case PRANKSTER -> speakRandom(hero, owner, "message.herobrine_companion.state_prankster", 2);
            case MAINTAINER -> speakRandom(hero, owner, "message.herobrine_companion.state_maintainer", 2);
            case GLITCH_LORD -> speakRandom(hero, owner, "message.herobrine_companion.state_glitch_lord", 2);
            case MONSTER_KING -> speakRandom(hero, owner, "message.herobrine_companion.state_monster_king", 2);
            case REMINISCING -> speakRandom(hero, owner, "message.herobrine_companion.state_reminiscing", 2);
            default -> {
                // Fallback to generic ambient
                if (hero.level().isNight()) {
                    speakRandom(hero, owner, "message.herobrine_companion.night_comment", 3);
                } else {
                    speakRandom(hero, owner, "message.herobrine_companion.day_comment", 3);
                }
            }
        }
    }


    public static void onSleep(HeroEntity hero, ServerPlayer owner) {
        if (hero.getRandom().nextFloat() < 0.5) {
            speakRandom(hero, owner, "message.herobrine_companion.sleep_watch", 3);
        }
    }

    public static void onKillMonster(HeroEntity hero, ServerPlayer owner) {
        if (hero.getRandom().nextFloat() < 0.2) { // 50% chance
            speakRandom(hero, owner, "message.herobrine_companion.combat_comment", 3);
        }
    }

    public static void onFixAnomaly(HeroEntity hero, ServerPlayer owner) {
        if (hero.getRandom().nextFloat() < 0.3) { // 50% chance
            speakRandom(hero, owner, "message.herobrine_companion.fix_anomaly", 3);
        }
    }

    public static void onCleanseArea(HeroEntity hero, ServerPlayer owner) {
        if (hero.getRandom().nextFloat() < 1) { // 100% chance
            speakRandom(hero, owner, "message.herobrine_companion.area_cleansed", 2);
        }
    }

    public static void onPacifyMonster(HeroEntity hero, ServerPlayer owner) {
        if (hero.getRandom().nextFloat() < 0.5) { // 100% chance
            speakRandom(hero, owner, "message.herobrine_companion.pacify_monster", 3);
        }
    }

    public static void onPrank(HeroEntity hero, ServerPlayer owner) {
        if (hero.getRandom().nextFloat() < 0.8) {
            speakRandom(hero, owner, "message.herobrine_companion.prank_laugh", 3);
        }
    }

    public static void onExtinguishTorch(HeroEntity hero, ServerPlayer owner) {
        if (hero.getRandom().nextFloat() < 0.8) {
            speakRandom(hero, owner, "message.herobrine_companion.prank_torch", 3);
        }
    }

    public static void onGift(HeroEntity hero, ServerPlayer owner) {
        if (hero.getRandom().nextFloat() < 1) {
            speakRandom(hero, owner, "message.herobrine_companion.gift_comment", 3);
        }
    }

    // --- Observation Handlers (Computer Vision) ---

    public static void onObserveEntity(HeroEntity hero, ServerPlayer player, LivingEntity target) {
        if (hero.getRandom().nextFloat() > 0.3) return; // 30% chance to comment on observation

        if (target instanceof GhostSteveEntity || target instanceof GhostCreeperEntity || target instanceof GhostZombieEntity || target instanceof GhostSkeletonEntity) {
            speakRandom(hero, player, "message.herobrine_companion.observe_ghost", 3);
        } else if (target instanceof Monster) {
            speakRandom(hero, player, "message.herobrine_companion.observe_monster", 3);
        } else if (target instanceof Animal) {
            speakRandom(hero, player, "message.herobrine_companion.observe_animal", 2);
        } else if (target instanceof Villager) {
            speakRandom(hero, player, "message.herobrine_companion.observe_villager", 2);
        } else if (target instanceof Player) {
            speakRandom(hero, player, "message.herobrine_companion.observe_player", 2);
        } else {
            speakRandom(hero, player, "message.herobrine_companion.observe_entity_generic", 2);
        }
    }

    public static void onObserveBlock(HeroEntity hero, ServerPlayer player, BlockState state) {
        if (state.is(Blocks.DIAMOND_ORE) || state.is(Blocks.DEEPSLATE_DIAMOND_ORE)) {
            if (hero.getRandom().nextFloat() < 0.5)
                speakRandom(hero, player, "message.herobrine_companion.observe_diamond", 2);
        } else if (state.is(Blocks.BEDROCK)) {
            if (hero.getRandom().nextFloat() < 0.5)
                speak(hero, player, "message.herobrine_companion.observe_bedrock");
        } else if (state.getBlock().getDescriptionId().contains("command_block")) {
            if (hero.getRandom().nextFloat() < 0.5)
                speak(hero, player, "message.herobrine_companion.observe_command_block");
        } else if (state.is(Blocks.REDSTONE_WIRE)) {
            if (hero.getRandom().nextFloat() < 0.3)
                speak(hero, player, "message.herobrine_companion.observe_redstone");
        } else {
            // Generic block observation
            if (hero.getRandom().nextFloat() < 0.05) { // Very low chance for random blocks
                speakRandom(hero, player, "message.herobrine_companion.inspect_block", 3);
            }
        }
    }
    // [修复] 添加 onInspectBlock 方法，用于 HeroInspectBlockGoal 和 HeroInvitedActionGoal
    public static void onInspectBlock(HeroEntity hero, ServerPlayer player, BlockState state) {
        onObserveBlock(hero, player, state);
    }
    public static void onObserveItem(HeroEntity hero, ServerPlayer player, ItemStack item) {
        if (hero.getRandom().nextFloat() > 0.2) return; // 20% chance

        if (item.getItem() instanceof SwordItem) {
            speakRandom(hero, player, "message.herobrine_companion.observe_sword", 2);
        } else if (item.getItem() instanceof PickaxeItem) {
            speakRandom(hero, player, "message.herobrine_companion.observe_pickaxe", 2);
        } else if (item.getItem() instanceof HoeItem) {
            speakRandom(hero, player, "message.herobrine_companion.observe_hoe", 2);
        } else if (item.is(Items.COMMAND_BLOCK)) {
            speak(hero, player, "message.herobrine_companion.observe_command_block_item");
        } else if (item.is(Items.COMPASS)) {
            speak(hero, player, "message.herobrine_companion.observe_compass");
        } else if (item.is(Items.WHEAT_SEEDS) || item.is(Items.PUMPKIN_SEEDS) || item.is(Items.MELON_SEEDS) || item.is(Items.BEETROOT_SEEDS)) {
            speakRandom(hero, player, "message.herobrine_companion.observe_farming", 2);
        }
    }

    // --- Action Handlers ---

    public static void onPlayerFarming(HeroEntity hero, ServerPlayer player) {
        if (hero.getRandom().nextFloat() < 0.2)
            speakRandom(hero, player, "message.herobrine_companion.action_farming", 2);
    }

    public static void onPlayerMining(HeroEntity hero, ServerPlayer player) {
        if (hero.getRandom().nextFloat() < 0.2)
            speakRandom(hero, player, "message.herobrine_companion.action_mining", 2);
    }

    public static void onPlayerChopping(HeroEntity hero, ServerPlayer player) {
        if (hero.getRandom().nextFloat() < 0.2)
            speakRandom(hero, player, "message.herobrine_companion.action_chopping", 2);
    }

    // --- Core Speech Methods ---

    public static void speak(HeroEntity hero, ServerPlayer player, String key) {
        player.sendSystemMessage(Component.translatable(key));
        hero.getPersistentData().putLong(TAG_LAST_SPEECH, hero.level().getGameTime());
    }

    public static void speakRandom(HeroEntity hero, ServerPlayer player, String baseKey, int variants) {
        if (variants <= 1) {
            speak(hero, player, baseKey);
        } else {
            int r = hero.getRandom().nextInt(variants) + 1;
            speak(hero, player, baseKey + "_" + r);
        }
    }
}
