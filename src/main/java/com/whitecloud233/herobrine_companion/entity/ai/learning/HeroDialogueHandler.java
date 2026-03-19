package com.whitecloud233.herobrine_companion.entity.ai.learning;

import com.whitecloud233.herobrine_companion.client.service.AIService;
import com.whitecloud233.herobrine_companion.client.service.LLMConfig;
import com.whitecloud233.herobrine_companion.entity.GhostCreeperEntity;
import com.whitecloud233.herobrine_companion.entity.GhostSkeletonEntity;
import com.whitecloud233.herobrine_companion.entity.GhostSteveEntity;
import com.whitecloud233.herobrine_companion.entity.GhostZombieEntity;
import com.whitecloud233.herobrine_companion.entity.HeroEntity;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.PickaxeItem;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.UUID;

public class HeroDialogueHandler {

    private static final String TAG_LAST_SPEECH = "HeroLastSpeechTime";

    public static boolean isAIEnabled() {
        String key = LLMConfig.aiApiKey;
        return com.whitecloud233.herobrine_companion.config.Config.aiVisionEnabled
                && key != null && !key.isEmpty() && !key.equals("YOUR_API_KEY_HERE");
    }

    public static boolean canSpeak(HeroEntity hero) {
        long time = hero.level().getGameTime();
        long last = hero.getPersistentData().getLong(TAG_LAST_SPEECH);
        return (time - last) >= (com.whitecloud233.herobrine_companion.config.Config.aiVisionInterval * 20L);
    }

    private static void tryAIDialogueOrFallback(HeroEntity hero, ServerPlayer player, String aiPrompt, String fallbackKey, int fallbackVariants) {
        if (isAIEnabled()) {
            triggerAIObservation(hero, player, aiPrompt);
        } else {
            speakRandom(hero, player, fallbackKey, fallbackVariants);
        }
    }

    public static void triggerAIObservation(HeroEntity hero, ServerPlayer player, String observationDesc) {
        hero.getPersistentData().putLong(TAG_LAST_SPEECH, hero.level().getGameTime());

        AIService.observeEnvironment(observationDesc, player.getUUID())
                .thenAccept(reply -> {
                    if (reply != null && !reply.isEmpty() && !reply.startsWith("§c")) {
                        player.getServer().execute(() -> {
                            player.sendSystemMessage(Component.literal("§e<Herobrine> §f" + reply));
                        });
                    }
                });
    }

    public static void tick(HeroEntity hero) {
        if (hero.level().isClientSide || !hero.isCompanionMode()) return;
        if (hero.tickCount % 100 != 0) return;
        UUID ownerUUID = hero.getOwnerUUID();
        if (ownerUUID == null) return;
        Player owner = hero.level().getPlayerByUUID(ownerUUID);
        if (owner instanceof ServerPlayer serverPlayer) {
            checkConditions(hero, serverPlayer);
        }
    }

    private static void checkConditions(HeroEntity hero, ServerPlayer owner) {
        if (!canSpeak(hero)) return;
        RandomSource random = hero.getRandom();

        if (owner.getHealth() < owner.getMaxHealth() * 0.3) {
            tryAIDialogueOrFallback(hero, owner,
                    "The player is critically injured and has low health.",
                    "message.herobrine_companion.low_health", 3);
            return;
        }

        if (random.nextFloat() < 0.02) {
            SimpleNeuralNetwork.MindState state = hero.getHeroBrain().getState();
            handleStateSpeech(hero, owner, state);
        }
    }

    private static void handleStateSpeech(HeroEntity hero, ServerPlayer owner, SimpleNeuralNetwork.MindState state) {
        if (isAIEnabled()) {
            String stateContext = switch (state) {
                case PROTECTOR -> "You are currently in a protective state.";
                case JUDGE -> "You are observing the player.";
                case PRANKSTER -> "You are in a mischievous state.";
                case MAINTAINER -> "You are focusing on Minecraft's underlying code.";
                case GLITCH_LORD -> "Your data stream is currently experiencing anomalies.";
                case MONSTER_KING -> "You are sensing the presence of nearby monsters.";
                case REMINISCING -> "You are recalling past events regarding the creation of this world.";
                default -> hero.level().isNight() ? "It is currently nighttime." : "It is currently daytime.";
            };
            triggerAIObservation(hero, owner, "The player is quiet right now. " + stateContext + " Please make a comment based on this state.");
        } else {
            switch (state) {
                case PROTECTOR -> speakRandom(hero, owner, "message.herobrine_companion.state_protector", 2);
                case JUDGE -> speakRandom(hero, owner, "message.herobrine_companion.state_judge", 2);
                case PRANKSTER -> speakRandom(hero, owner, "message.herobrine_companion.state_prankster", 2);
                case MAINTAINER -> speakRandom(hero, owner, "message.herobrine_companion.state_maintainer", 2);
                case GLITCH_LORD -> speakRandom(hero, owner, "message.herobrine_companion.state_glitch_lord", 2);
                case MONSTER_KING -> speakRandom(hero, owner, "message.herobrine_companion.state_monster_king", 2);
                case REMINISCING -> speakRandom(hero, owner, "message.herobrine_companion.state_reminiscing", 2);
                default -> {
                    if (hero.level().isNight()) speakRandom(hero, owner, "message.herobrine_companion.night_comment", 3);
                    else speakRandom(hero, owner, "message.herobrine_companion.day_comment", 3);
                }
            }
        }
    }

    public static void onSleep(HeroEntity hero, ServerPlayer owner) {
        if (hero.getRandom().nextFloat() < 0.2) {
            tryAIDialogueOrFallback(hero, owner, "The player fell asleep.", "message.herobrine_companion.sleep_watch", 3);
        }
    }

    public static void onKillMonster(HeroEntity hero, ServerPlayer owner) {
        if (hero.getRandom().nextFloat() < 0.1) {
            tryAIDialogueOrFallback(hero, owner, "The player killed a monster.", "message.herobrine_companion.combat_comment", 3);
        }
    }

    public static void onFixAnomaly(HeroEntity hero, ServerPlayer owner) {
        if (hero.getRandom().nextFloat() < 0.3) {
            tryAIDialogueOrFallback(hero, owner, "You fixed a glitch.", "message.herobrine_companion.fix_anomaly", 3);
        }
    }

    public static void onCleanseArea(HeroEntity hero, ServerPlayer owner) {
        if (hero.getRandom().nextFloat() < 0.5) {
            tryAIDialogueOrFallback(hero, owner, "You cleansed the corruption and restored the environment.", "message.herobrine_companion.area_cleansed", 2);
        }
    }

    public static void onPacifyMonster(HeroEntity hero, ServerPlayer owner) {
        if (hero.getRandom().nextFloat() < 0.5) {
            tryAIDialogueOrFallback(hero, owner, "You pacified a monster.", "message.herobrine_companion.pacify_monster", 3);
        }
    }

    public static void onPrank(HeroEntity hero, ServerPlayer owner) {
        if (hero.getRandom().nextFloat() < 0.3) {
            tryAIDialogueOrFallback(hero, owner, "Your prank on the player succeeded.", "message.herobrine_companion.prank_laugh", 3);
        }
    }

    public static void onExtinguishTorch(HeroEntity hero, ServerPlayer owner) {
        if (hero.getRandom().nextFloat() < 0.3) {
            tryAIDialogueOrFallback(hero, owner, "You extinguished the player's torches, plunging them into darkness.", "message.herobrine_companion.prank_torch", 3);
        }
    }

    public static void onGift(HeroEntity hero, ServerPlayer owner) {
        if (hero.getRandom().nextFloat() < 1) {
            tryAIDialogueOrFallback(hero, owner, "You gave the player a gift.", "message.herobrine_companion.gift_comment", 3);
        }
    }

    public static void onObserveEntity(HeroEntity hero, ServerPlayer player, LivingEntity target) {
        if (hero.getRandom().nextFloat() > 0.1) return;

        String targetName = target.getName().getString();

        if (target instanceof GhostSteveEntity || target instanceof GhostCreeperEntity || target instanceof GhostZombieEntity || target instanceof GhostSkeletonEntity) {
            tryAIDialogueOrFallback(hero, player, "The player is looking at an undead anomaly (" + targetName + ").", "message.herobrine_companion.observe_ghost", 3);
        } else if (target instanceof Monster) {
            tryAIDialogueOrFallback(hero, player, "The player is looking at a monster [" + targetName + "].", "message.herobrine_companion.observe_monster", 3);
        } else if (target instanceof Animal) {
            tryAIDialogueOrFallback(hero, player, "The player is looking at an animal [" + targetName + "].", "message.herobrine_companion.observe_animal", 2);
        } else if (target instanceof Villager) {
            tryAIDialogueOrFallback(hero, player, "The player is looking at a villager [" + targetName + "].", "message.herobrine_companion.observe_villager", 2);
        } else if (target instanceof Player) {
            tryAIDialogueOrFallback(hero, player, "The player is observing another human.", "message.herobrine_companion.observe_player", 2);
        } else {
            tryAIDialogueOrFallback(hero, player, "The player is observing an entity [" + targetName + "].", "message.herobrine_companion.observe_entity_generic", 2);
        }
    }

    public static void onObserveBlock(HeroEntity hero, ServerPlayer player, BlockState state) {
        String blockName = state.getBlock().getName().getString();

        if (state.is(Blocks.DIAMOND_ORE) || state.is(Blocks.DEEPSLATE_DIAMOND_ORE)) {
            if (hero.getRandom().nextFloat() < 0.2)
                tryAIDialogueOrFallback(hero, player, "The player found [" + blockName + "].", "message.herobrine_companion.observe_diamond", 2);
        } else if (state.is(Blocks.BEDROCK)) {
            if (hero.getRandom().nextFloat() < 0.2)
                tryAIDialogueOrFallback(hero, player, "The player is looking at [Bedrock].", "message.herobrine_companion.observe_bedrock", 1);
        } else if (state.getBlock().getDescriptionId().contains("command_block")) {
            if (hero.getRandom().nextFloat() < 0.2)
                tryAIDialogueOrFallback(hero, player, "The player is looking at a [Command Block].", "message.herobrine_companion.observe_command_block", 1);
        } else if (state.is(Blocks.REDSTONE_WIRE)) {
            if (hero.getRandom().nextFloat() < 0.1)
                tryAIDialogueOrFallback(hero, player, "The player is tinkering with redstone.", "message.herobrine_companion.observe_redstone", 1);
        } else {
            if (hero.getRandom().nextFloat() < 0.02) {
                tryAIDialogueOrFallback(hero, player, "The player is looking at [" + blockName + "].", "message.herobrine_companion.inspect_block", 3);
            }
        }
    }

    public static void onInspectBlock(HeroEntity hero, ServerPlayer player, BlockState state) {
        onObserveBlock(hero, player, state);
    }

    public static void onObserveItem(HeroEntity hero, ServerPlayer player, ItemStack item) {
        if (hero.getRandom().nextFloat() > 0.05) return;

        String itemName = item.getHoverName().getString();

        if (item.getItem() instanceof SwordItem) {
            tryAIDialogueOrFallback(hero, player, "The player is holding their weapon [" + itemName + "].", "message.herobrine_companion.observe_sword", 2);
        } else if (item.getItem() instanceof PickaxeItem) {
            tryAIDialogueOrFallback(hero, player, "The player is holding their [" + itemName + "] to mine.", "message.herobrine_companion.observe_pickaxe", 2);
        } else if (item.getItem() instanceof HoeItem) {
            tryAIDialogueOrFallback(hero, player, "The player is holding a [" + itemName + "] to farm.", "message.herobrine_companion.observe_hoe", 2);
        } else if (item.is(Items.COMMAND_BLOCK)) {
            tryAIDialogueOrFallback(hero, player, "The player is holding a [Command Block].", "message.herobrine_companion.observe_command_block_item", 1);
        } else if (item.is(Items.COMPASS)) {
            tryAIDialogueOrFallback(hero, player, "The player is holding a compass.", "message.herobrine_companion.observe_compass", 1);
        } else if (item.is(Items.WHEAT_SEEDS) || item.is(Items.PUMPKIN_SEEDS) || item.is(Items.MELON_SEEDS) || item.is(Items.BEETROOT_SEEDS)) {
            tryAIDialogueOrFallback(hero, player, "The player is holding seeds [" + itemName + "].", "message.herobrine_companion.observe_farming", 2);
        }
    }

    public static void onPlayerFarming(HeroEntity hero, ServerPlayer player) {
        if (hero.getRandom().nextFloat() < 0.05)
            tryAIDialogueOrFallback(hero, player, "The player is farming.", "message.herobrine_companion.action_farming", 2);
    }

    public static void onPlayerMining(HeroEntity hero, ServerPlayer player) {
        if (hero.getRandom().nextFloat() < 0.05)
            tryAIDialogueOrFallback(hero, player, "The player is mining underground.", "message.herobrine_companion.action_mining", 2);
    }

    public static void onPlayerChopping(HeroEntity hero, ServerPlayer player) {
        if (hero.getRandom().nextFloat() < 0.05)
            tryAIDialogueOrFallback(hero, player, "The player is chopping wood.", "message.herobrine_companion.action_chopping", 2);
    }

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