package com.whitecloud233.modid.herobrine_companion.entity.ai.learning;

import com.whitecloud233.modid.herobrine_companion.entity.HeroEntity;
import com.whitecloud233.modid.herobrine_companion.network.AIObservationPacket;
import com.whitecloud233.modid.herobrine_companion.network.PacketHandler;
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
import com.whitecloud233.modid.herobrine_companion.entity.GhostCreeperEntity;
import com.whitecloud233.modid.herobrine_companion.entity.GhostSkeletonEntity;
import com.whitecloud233.modid.herobrine_companion.entity.GhostSteveEntity;
import com.whitecloud233.modid.herobrine_companion.entity.GhostZombieEntity;
import java.util.UUID;

// 【极其重要】：此类在服务端运行，绝对不能 import 任何 client 相关的包！

public class HeroDialogueHandler {

    private static final String TAG_LAST_SPEECH = "HeroLastSpeechTime";

    public static boolean canSpeak(HeroEntity hero) {
        long time = hero.level().getGameTime();
        long last = hero.getPersistentData().getLong(TAG_LAST_SPEECH);
        // 读取配置中的冷却时间（由于Config类通常不是仅客户端的，所以可以在服务端读取）
        return (time - last) >= (com.whitecloud233.modid.herobrine_companion.config.Config.aiVisionInterval * 20L);
    }

    // 【核心改造】：不再服务端判断AI，而是把大模型提示词和备用台词打包发给客户端，扣对应玩家的钱！
    public static void tryAIDialogueOrFallback(HeroEntity hero, ServerPlayer player, String aiPrompt, String fallbackKey, int fallbackVariants) {
        if (!canSpeak(hero)) return;
        // 记录说话时间，进入冷却
        hero.getPersistentData().putLong(TAG_LAST_SPEECH, hero.level().getGameTime());

        // 发送数据包，将提示词和保底翻译键全部交给该玩家的客户端去处理
        PacketHandler.sendToPlayer(new AIObservationPacket(hero.getId(), aiPrompt, fallbackKey, fallbackVariants), player);
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
        String stateContext;
        String fallbackKey;
        int fallbackVariants = 2;

        switch (state) {
            case PROTECTOR -> {
                stateContext = "You are currently in a protective state.";
                fallbackKey = "message.herobrine_companion.state_protector";
            }
            case JUDGE -> {
                stateContext = "You are observing the player.";
                fallbackKey = "message.herobrine_companion.state_judge";
            }
            case PRANKSTER -> {
                stateContext = "You are in a mischievous state.";
                fallbackKey = "message.herobrine_companion.state_prankster";
            }
            case MAINTAINER -> {
                stateContext = "You are focusing on Minecraft's underlying code.";
                fallbackKey = "message.herobrine_companion.state_maintainer";
            }
            case GLITCH_LORD -> {
                stateContext = "Your data stream is currently experiencing anomalies.";
                fallbackKey = "message.herobrine_companion.state_glitch_lord";
            }
            case MONSTER_KING -> {
                stateContext = "You are sensing the presence of nearby monsters.";
                fallbackKey = "message.herobrine_companion.state_monster_king";
            }
            case REMINISCING -> {
                stateContext = "You are recalling past events regarding the creation of this world.";
                fallbackKey = "message.herobrine_companion.state_reminiscing";
            }
            default -> {
                if (hero.level().isNight()) {
                    stateContext = "It is currently nighttime.";
                    fallbackKey = "message.herobrine_companion.night_comment";
                } else {
                    stateContext = "It is currently daytime.";
                    fallbackKey = "message.herobrine_companion.day_comment";
                }
                fallbackVariants = 3;
            }
        }

        tryAIDialogueOrFallback(hero, owner,
                "The player is quiet right now. " + stateContext + " Please make a comment based on this state.",
                fallbackKey, fallbackVariants);
    }

    // ========================================================
    // 以下是你编写的所有交互事件，已经全部接入安全的发包架构
    // ========================================================

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

    // ========================================================
    // 保留给那些没有 AI 提示词，纯原版固定剧本的对话方法
    // ========================================================

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