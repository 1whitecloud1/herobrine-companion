package com.whitecloud233.herobrine_companion.entity.logic;

import com.whitecloud233.herobrine_companion.HerobrineCompanion;
import com.whitecloud233.herobrine_companion.entity.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.UUID;

public class HeroQuestHandler {

    private static final String TAG_QUEST_ID = "HeroActiveQuestId";
    private static final String TAG_QUEST_PROGRESS = "HeroActiveQuestProgress";
    private static final String TAG_QUEST_TARGET_UUID = "HeroQuestTargetUUID";

    private static final int QUEST_CLEAR_UNSTABLE_ZONE = 1;
    public static final int QUEST_PACIFY_ENDERMAN = 2;

    private static final int TARGET_KILLS = 5;
    private static final int TARGET_PACIFY_POINTS = 100;

    public static void startQuest(HeroEntity hero, ServerPlayer player, int questId) {
        CompoundTag data = player.getPersistentData();

        if (data.getInt(TAG_QUEST_ID) != 0) {
            player.sendSystemMessage(Component.translatable("message.herobrine_companion.quest_already_active"));
            return;
        }

        data.putInt(TAG_QUEST_ID, questId);
        data.putInt(TAG_QUEST_PROGRESS, 0);
        hero.addTag("player_doing_quest");

        if (questId == QUEST_CLEAR_UNSTABLE_ZONE) {
            player.sendSystemMessage(Component.translatable("message.herobrine_companion.quest_start_1"));
        } else if (questId == QUEST_PACIFY_ENDERMAN) {
            player.sendSystemMessage(Component.translatable("message.herobrine_companion.quest_start_2"));
            if (player.level() instanceof ServerLevel serverLevel) {
                EnderMan questEnderman = new EnderMan(EntityType.ENDERMAN, serverLevel);

                // Find a spawn position
                double angle = player.level().random.nextDouble() * 2 * Math.PI;
                double distance = 10 + player.level().random.nextDouble() * 5;
                int x = (int) (player.getX() + Math.cos(angle) * distance);
                int z = (int) (player.getZ() + Math.sin(angle) * distance);
                int y = serverLevel.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);

                questEnderman.teleportTo(x + 0.5, y, z + 0.5);

                questEnderman.setCustomName(Component.translatable("entity.herobrine_companion.quest_enderman"));
                questEnderman.setCustomNameVisible(true);
                questEnderman.setPersistenceRequired();
                questEnderman.getTags().add("quest_target_for:" + player.getUUID().toString());


                serverLevel.addFreshEntity(questEnderman);
                data.putUUID(TAG_QUEST_TARGET_UUID, questEnderman.getUUID());
            }
        }
    }

    public static void cancelQuest(HeroEntity hero, ServerPlayer player) {
        CompoundTag data = player.getPersistentData();
        int questId = data.getInt(TAG_QUEST_ID);

        if (questId == 0) {
            return;
        }

        // Clean up specific quest entities if needed
        if (questId == QUEST_PACIFY_ENDERMAN) {
            if (data.hasUUID(TAG_QUEST_TARGET_UUID)) {
                UUID targetUUID = data.getUUID(TAG_QUEST_TARGET_UUID);
                if (player.level() instanceof ServerLevel serverLevel) {
                    Entity targetEntity = serverLevel.getEntity(targetUUID);
                    if (targetEntity instanceof EnderMan questEnderman) {
                        questEnderman.discard();
                    }
                }
                data.remove(TAG_QUEST_TARGET_UUID);
            }
        }

        data.remove(TAG_QUEST_ID);
        data.remove(TAG_QUEST_PROGRESS);
        hero.removeTag("player_doing_quest");
        
        player.sendSystemMessage(Component.translatable("message.herobrine_companion.quest_cancelled"));
    }

    public static void onMobKill(ServerPlayer player, Entity killedEntity) {
        CompoundTag data = player.getPersistentData();
        int questId = data.getInt(TAG_QUEST_ID);

        if (questId == QUEST_CLEAR_UNSTABLE_ZONE) {
            if (killedEntity instanceof GhostZombieEntity || killedEntity instanceof GhostCreeperEntity || killedEntity instanceof GhostSkeletonEntity || killedEntity instanceof GhostSteveEntity) {
                int progress = data.getInt(TAG_QUEST_PROGRESS) + 1;
                data.putInt(TAG_QUEST_PROGRESS, progress);

                player.displayClientMessage(Component.literal("Progress: " + progress + "/" + TARGET_KILLS), true);

                if (progress >= TARGET_KILLS) {
                    completeQuest(player, questId);
                }
            }
        } else if (questId == QUEST_PACIFY_ENDERMAN) {
            if (data.hasUUID(TAG_QUEST_TARGET_UUID) && killedEntity.getUUID().equals(data.getUUID(TAG_QUEST_TARGET_UUID))) {
                failQuest(player, questId, "message.herobrine_companion.quest_target_killed");
            }
        }
    }

    public static void onEndermanInteract(ServerPlayer player, Entity target, ItemStack itemStack) {
        CompoundTag data = player.getPersistentData();
        if (data.getInt(TAG_QUEST_ID) == QUEST_PACIFY_ENDERMAN) {
            if (!data.hasUUID(TAG_QUEST_TARGET_UUID)) return;
            UUID targetUUID = data.getUUID(TAG_QUEST_TARGET_UUID);

            if (target instanceof EnderMan enderman && enderman.getUUID().equals(targetUUID)) {
                if (itemStack.is(Items.DIRT)) {
                    itemStack.shrink(1);
                    enderman.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIRT));
                    
                    int progress = data.getInt(TAG_QUEST_PROGRESS) + 25;
                    updateProgress(player, progress, TARGET_PACIFY_POINTS, QUEST_PACIFY_ENDERMAN);
                }
            }
        }
    }

    public static void tickPacifyQuest(ServerPlayer player) {
        CompoundTag data = player.getPersistentData();
        if (data.getInt(TAG_QUEST_ID) == QUEST_PACIFY_ENDERMAN) {
            if (!data.hasUUID(TAG_QUEST_TARGET_UUID)) return;
            UUID targetUUID = data.getUUID(TAG_QUEST_TARGET_UUID);
            Entity targetEntity = ((ServerLevel) player.level()).getEntity(targetUUID);

            if (targetEntity instanceof EnderMan questEnderman && targetEntity.isAlive()) {
                if (player.isShiftKeyDown() && player.distanceToSqr(questEnderman) < 100) {
                    if (questEnderman.getTarget() == player) {
                        return;
                    }
                    if (player.tickCount % 20 == 0) {
                        int progress = data.getInt(TAG_QUEST_PROGRESS) + 5;
                        updateProgress(player, progress, TARGET_PACIFY_POINTS, QUEST_PACIFY_ENDERMAN);
                    }
                }
            } else if (player.tickCount % 100 == 0) { // Check periodically
                failQuest(player, QUEST_PACIFY_ENDERMAN, "message.herobrine_companion.quest_target_gone");
            }
        }
    }
    
    public static void failQuest(ServerPlayer player, int questId, String messageKey) {
        CompoundTag data = player.getPersistentData();
        if (data.getInt(TAG_QUEST_ID) == questId) {
            data.remove(TAG_QUEST_ID);
            data.remove(TAG_QUEST_PROGRESS);
            if (data.hasUUID(TAG_QUEST_TARGET_UUID)) {
                data.remove(TAG_QUEST_TARGET_UUID);
            }
            player.sendSystemMessage(Component.translatable(messageKey));
        }
    }

    private static void updateProgress(ServerPlayer player, int progress, int target, int questId) {
        CompoundTag data = player.getPersistentData();
        if (progress > target) progress = target;
        data.putInt(TAG_QUEST_PROGRESS, progress);
        player.displayClientMessage(Component.literal("Pacify Progress: " + progress + "/" + target), true);

        if (progress >= target) {
            completeQuest(player, questId);
        }
    }

    private static void completeQuest(ServerPlayer player, int questId) {
        CompoundTag data = player.getPersistentData();
        
        if (questId == QUEST_CLEAR_UNSTABLE_ZONE) {
            player.sendSystemMessage(Component.translatable("message.herobrine_companion.quest_complete_1"));
            player.getInventory().add(new ItemStack(HerobrineCompanion.VOID_MARROW.get(), 3));
            increaseTrust(player, 15);
        } else if (questId == QUEST_PACIFY_ENDERMAN) {
            player.sendSystemMessage(Component.translatable("message.herobrine_companion.quest_complete_2"));
            player.getInventory().add(new ItemStack(Items.ENDER_PEARL, 16));
            increaseTrust(player, 10);

            if (data.hasUUID(TAG_QUEST_TARGET_UUID)) {
                UUID targetUUID = data.getUUID(TAG_QUEST_TARGET_UUID);
                if (player.level() instanceof ServerLevel serverLevel) {
                    Entity targetEntity = serverLevel.getEntity(targetUUID);
                    if (targetEntity instanceof EnderMan questEnderman) {
                        questEnderman.discard();
                    }
                }
            }
        }

        // Clean up player's quest tags
        data.remove(TAG_QUEST_ID);
        data.remove(TAG_QUEST_PROGRESS);
        if (data.hasUUID(TAG_QUEST_TARGET_UUID)) {
            data.remove(TAG_QUEST_TARGET_UUID);
        }
    }

    private static void increaseTrust(ServerPlayer player, int amount) {
        boolean heroFound = false;
        if (player.level() instanceof ServerLevel serverLevel) {
            for (Entity entity : serverLevel.getAllEntities()) {
                if (entity instanceof HeroEntity hero) {
                    boolean isOwner = hero.getOwnerUUID() != null && hero.getOwnerUUID().equals(player.getUUID());
                    boolean isNearby = hero.getOwnerUUID() == null && hero.distanceToSqr(player) < 1024.0D;

                    if (isOwner || isNearby) {
                        if (hero.getOwnerUUID() == null) {
                            hero.setOwnerUUID(player.getUUID());
                        }

                        hero.increaseTrust(amount);
                        HeroDataHandler.updateGlobalTrust(hero);

                        player.sendSystemMessage(Component.translatable("message.herobrine_companion.trust_increase", amount, hero.getTrustLevel()));
                        hero.removeTag("player_doing_quest");
                        heroFound = true;
                        break;
                    }
                }
            }
        }

        if (!heroFound) {
            player.getPersistentData().putInt("HeroPendingTrustReward", amount);
            player.getPersistentData().putBoolean("HeroPendingQuestClear", true);
        }
    }

    public static boolean isPlayerDoingQuest(Player player) {
        return player.getPersistentData().getInt(TAG_QUEST_ID) != 0;
    }

    public static boolean shouldIgnoreTarget(Entity target) {
        return target instanceof GhostZombieEntity ||
                target instanceof GhostCreeperEntity ||
                target instanceof GhostSkeletonEntity;
    }
}