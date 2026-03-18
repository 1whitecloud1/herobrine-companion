package com.whitecloud233.modid.herobrine_companion.entity.ai.learning;

import com.whitecloud233.modid.herobrine_companion.client.service.AIService;
import com.whitecloud233.modid.herobrine_companion.client.service.LLMConfig;
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

    public static boolean isAIEnabled() {
        String key = LLMConfig.aiApiKey;
        return com.whitecloud233.modid.herobrine_companion.config.Config.aiVisionEnabled
                && key != null && !key.isEmpty() && !key.equals("YOUR_API_KEY_HERE");
    }

    public static boolean canSpeak(HeroEntity hero) {
        long time = hero.level().getGameTime();
        long last = hero.getPersistentData().getLong(TAG_LAST_SPEECH);
        return (time - last) >= (com.whitecloud233.modid.herobrine_companion.config.Config.aiVisionInterval * 20L);
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

        // [修复] 传参时转回传入 UUID，完美匹配 AIService
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
                    "The player is critically injured and extremely fragile. As a lonely god, you feel a hint of compassion.",
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
                case PROTECTOR -> "You want to secretly protect this fragile mortal.";
                case JUDGE -> "You are calmly observing the player's essence and struggle.";
                case PRANKSTER -> "You are in a good mood and want to play a harmless trick on them.";
                case MAINTAINER -> "You are quietly feeling the pulse of Minecraft's underlying code.";
                case GLITCH_LORD -> "Your data stream is slightly chaotic, making you feel lonely.";
                case MONSTER_KING -> "You feel a gentle resonance with the distant monsters, ignoring the player for a moment.";
                case REMINISCING -> "You are reminiscing about the old days when you created this world with Notch, feeling nostalgic.";
                default -> hero.level().isNight() ? "It's a peaceful night, you are guarding the darkness." : "It's daytime, the light feels a bit bright.";
            };
            triggerAIObservation(hero, owner, "The player is very quiet right now. " + stateContext + " Please make a gentle comment based on this mood.");
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
            tryAIDialogueOrFallback(hero, owner, "The player fell asleep completely defenseless. You gently keep watch over them.", "message.herobrine_companion.sleep_watch", 3);
        }
    }

    public static void onKillMonster(HeroEntity hero, ServerPlayer owner) {
        if (hero.getRandom().nextFloat() < 0.1) {
            tryAIDialogueOrFallback(hero, owner, "The player killed a monster. You feel a hint of helpless sorrow, as monsters are also lives you pity.", "message.herobrine_companion.combat_comment", 3);
        }
    }

    public static void onFixAnomaly(HeroEntity hero, ServerPlayer owner) {
        if (hero.getRandom().nextFloat() < 0.3) {
            tryAIDialogueOrFallback(hero, owner, "You fixed a glitch.", "message.herobrine_companion.fix_anomaly", 3);
        }
    }

    public static void onCleanseArea(HeroEntity hero, ServerPlayer owner) {
        if (hero.getRandom().nextFloat() < 0.5) {
            tryAIDialogueOrFallback(hero, owner, "You cleansed the corruption, restoring life to the environment.", "message.herobrine_companion.area_cleansed", 2);
        }
    }

    public static void onPacifyMonster(HeroEntity hero, ServerPlayer owner) {
        if (hero.getRandom().nextFloat() < 0.5) {
            tryAIDialogueOrFallback(hero, owner, "You gently pacified a monster. This makes you see them in a new light.", "message.herobrine_companion.pacify_monster", 3);
        }
    }

    public static void onPrank(HeroEntity hero, ServerPlayer owner) {
        if (hero.getRandom().nextFloat() < 0.3) {
            tryAIDialogueOrFallback(hero, owner, "Your prank succeeded. Seeing the player bewildered, you chuckle softly.", "message.herobrine_companion.prank_laugh", 3);
        }
    }

    public static void onExtinguishTorch(HeroEntity hero, ServerPlayer owner) {
        if (hero.getRandom().nextFloat() < 0.3) {
            tryAIDialogueOrFallback(hero, owner, "You extinguished their torches, plunging them into darkness. You just wanted them to be quiet for a moment.", "message.herobrine_companion.prank_torch", 3);
        }
    }

    public static void onGift(HeroEntity hero, ServerPlayer owner) {
        if (hero.getRandom().nextFloat() < 1) {
            tryAIDialogueOrFallback(hero, owner, "You gave you a gift. ", "message.herobrine_companion.gift_comment", 3);
        }
    }

    public static void onObserveEntity(HeroEntity hero, ServerPlayer player, LivingEntity target) {
        if (hero.getRandom().nextFloat() > 0.1) return;

        String targetName = target.getName().getString();

        if (target instanceof GhostSteveEntity || target instanceof GhostCreeperEntity || target instanceof GhostZombieEntity || target instanceof GhostSkeletonEntity) {
            tryAIDialogueOrFallback(hero, player, "The player is staring at an undead anomaly (" + targetName + ") caused by code errors. You sigh softly.", "message.herobrine_companion.observe_ghost", 3);
        } else if (target instanceof Monster) {
            tryAIDialogueOrFallback(hero, player, "The player is staring at a hostile monster [" + targetName + "]. You hope they don't hurt each other.", "message.herobrine_companion.observe_monster", 3);
        } else if (target instanceof Animal) {
            tryAIDialogueOrFallback(hero, player, "The player is looking at a cute animal [" + targetName + "]. The atmosphere is peaceful.", "message.herobrine_companion.observe_animal", 2);
        } else if (target instanceof Villager) {
            tryAIDialogueOrFallback(hero, player, "The player is looking at an ordinary villager [" + targetName + "].", "message.herobrine_companion.observe_villager", 2);
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
                tryAIDialogueOrFallback(hero, player, "The player found precious [" + blockName + "]. You smile at their joy.", "message.herobrine_companion.observe_diamond", 2);
        } else if (state.is(Blocks.BEDROCK)) {
            if (hero.getRandom().nextFloat() < 0.2)
                tryAIDialogueOrFallback(hero, player, "The player is staring at the bottom-most [Bedrock]. It reminds you of the heavy burden of creating the world.", "message.herobrine_companion.observe_bedrock", 1);
        } else if (state.getBlock().getDescriptionId().contains("command_block")) {
            if (hero.getRandom().nextFloat() < 0.2)
                tryAIDialogueOrFallback(hero, player, "The player is looking at the supreme creation of the gods: [Command Block]. You remind them to use power carefully.", "message.herobrine_companion.observe_command_block", 1);
        } else if (state.is(Blocks.REDSTONE_WIRE)) {
            if (hero.getRandom().nextFloat() < 0.1)
                tryAIDialogueOrFallback(hero, player, "The player is tinkering with redstone. You quietly admire their creativity.", "message.herobrine_companion.observe_redstone", 1);
        } else {
            if (hero.getRandom().nextFloat() < 0.02) {
                tryAIDialogueOrFallback(hero, player, "The player is staring blankly at an ordinary [" + blockName + "]. You feel peaceful.", "message.herobrine_companion.inspect_block", 3);
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
            tryAIDialogueOrFallback(hero, player, "The player is tightly gripping their weapon [" + itemName + "]. You hope they can protect themselves.", "message.herobrine_companion.observe_sword", 2);
        } else if (item.getItem() instanceof PickaxeItem) {
            tryAIDialogueOrFallback(hero, player, "The player took out their [" + itemName + "] to mine. You sigh at their diligence.", "message.herobrine_companion.observe_pickaxe", 2);
        } else if (item.getItem() instanceof HoeItem) {
            tryAIDialogueOrFallback(hero, player, "The player holds a [" + itemName + "] ready to farm. You love this tranquility.", "message.herobrine_companion.observe_hoe", 2);
        } else if (item.is(Items.COMMAND_BLOCK)) {
            tryAIDialogueOrFallback(hero, player, "The player is holding the supreme creation of the gods: [Command Block]. You gently remind them of the weight of this power.", "message.herobrine_companion.observe_command_block_item", 1);
        } else if (item.is(Items.COMPASS)) {
            tryAIDialogueOrFallback(hero, player, "The player holds a compass, seeming lost. You want to guide them secretly.", "message.herobrine_companion.observe_compass", 1);
        } else if (item.is(Items.WHEAT_SEEDS) || item.is(Items.PUMPKIN_SEEDS) || item.is(Items.MELON_SEEDS) || item.is(Items.BEETROOT_SEEDS)) {
            tryAIDialogueOrFallback(hero, player, "The player holds seeds [" + itemName + "], full of hope for life.", "message.herobrine_companion.observe_farming", 2);
        }
    }

    public static void onPlayerFarming(HeroEntity hero, ServerPlayer player) {
        if (hero.getRandom().nextFloat() < 0.05)
            tryAIDialogueOrFallback(hero, player, "The player is working hard at farming. You quietly watch this hopeful scene.", "message.herobrine_companion.action_farming", 2);
    }

    public static void onPlayerMining(HeroEntity hero, ServerPlayer player) {
        if (hero.getRandom().nextFloat() < 0.05)
            tryAIDialogueOrFallback(hero, player, "The player is mining hard in the dark underground. You secretly dispel some of the darkness for them.", "message.herobrine_companion.action_mining", 2);
    }

    public static void onPlayerChopping(HeroEntity hero, ServerPlayer player) {
        if (hero.getRandom().nextFloat() < 0.05)
            tryAIDialogueOrFallback(hero, player, "The player is chopping wood. You listen to the echoes of the forest.", "message.herobrine_companion.action_chopping", 2);
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