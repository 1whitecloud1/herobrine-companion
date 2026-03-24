package com.whitecloud233.modid.herobrine_companion.entity.logic.event;

import com.whitecloud233.modid.herobrine_companion.HerobrineCompanion;
import com.whitecloud233.modid.herobrine_companion.entity.HeroEntity;
import com.whitecloud233.modid.herobrine_companion.entity.logic.data.HeroStateManager;
import com.whitecloud233.modid.herobrine_companion.item.LoreFragmentItem;
import com.whitecloud233.modid.herobrine_companion.util.EndRingContext;
import com.whitecloud233.modid.herobrine_companion.world.structure.ModStructures;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = HerobrineCompanion.MODID)
public class EndRingDimensionHandler {

    @SubscribeEvent
    public static void onPlayerJoinWorld(EntityJoinLevelEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && event.getLevel().dimension() == ModStructures.END_RING_DIMENSION_KEY) {
            CompoundTag data = player.getPersistentData();
            if (data.getBoolean("IsChallengeActive")) return;

            // 登录时虚空保护
            if (player.getY() < -50) {
                teleportToCenter(player);
                if (data.getInt("WakeUpStage") >= 3) {
                    data.putBoolean("HasSimulatedCrash", true);
                    try { player.server.getPlayerList().saveAll(); } catch (Exception ignored) {}
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
        if (event.phase != TickEvent.Phase.END || event.side != LogicalSide.SERVER) return;
        if (!(event.player instanceof ServerPlayer player) || !player.isAlive()) return;

        if (player.level().dimension() == ModStructures.END_RING_DIMENSION_KEY) {
            if (!player.getPersistentData().getBoolean("IsChallengeActive")) {
                handleEndRingGameplay(player);
            }
        }
    }

    private static void handleEndRingGameplay(ServerPlayer player) {
        CompoundTag data = player.getPersistentData();
        long timeEntered = data.getLong("EnteredEndRingTime");

        if (player.level().getGameTime() - timeEntered > 600 && data.getInt("WakeUpStage") == 0) {
            data.putInt("WakeUpStage", 1);
            player.sendSystemMessage(Component.translatable("message.herobrine_companion.system_strange_presence"));
        }

        if (player.getY() < -50) handleVoidFall(player, data);
        if (data.getInt("WakeUpStage") >= 3) handleSkyGaze(player, data);
    }

    private static void handleVoidFall(ServerPlayer player, CompoundTag data) {
        int stage = data.getInt("WakeUpStage");
        boolean hasCrashed = data.getBoolean("HasSimulatedCrash");

        teleportToCenter(player);

        if (player.tickCount < 100) return;

        if (stage >= 3 && !hasCrashed) {
            data.putBoolean("HasSimulatedCrash", true);
            try { player.server.getPlayerList().saveAll(); } catch (Exception ignored) {}

            player.sendSystemMessage(Component.translatable("message.herobrine_companion.system_server_closed"));
            player.server.tell(new net.minecraft.server.TickTask(player.server.getTickCount() + 60, () -> {
                if (player.connection != null) {
                    try { player.server.getPlayerList().saveAll(); } catch (Exception ignored) {}
                    player.connection.disconnect(Component.translatable("message.herobrine_companion.system_server_closed"));
                }
            }));
        } else {
            player.sendSystemMessage(Component.translatable("message.herobrine_companion.hero_not_ready2"));
        }
    }

    private static void handleSkyGaze(ServerPlayer player, CompoundTag data) {
        if (player.getXRot() < -60) {
            int gazeTicks = data.getInt("SkyGazeTicks") + 1;
            data.putInt("SkyGazeTicks", gazeTicks);

            if (gazeTicks >= 200) {
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
            BlockPos spawnPos = player.getRespawnPosition() != null ? player.getRespawnPosition() : overworld.getSharedSpawnPos();
            ServerLevel endLevel = player.server.getLevel(ModStructures.END_RING_DIMENSION_KEY);

            if (endLevel != null) {
                for (var entity : endLevel.getAllEntities()) {
                    if (entity instanceof HeroEntity hero && hero.isAlive()) {
                        hero.setFloating(false);
                        HeroStateManager.backupToPlayerNBT(hero, player, "HeroRespawnData");
                        data.putBoolean("HeroPendingRespawn", true);
                        hero.discard();
                        break;
                    }
                }
            }

            player.teleportTo(overworld, spawnPos.getX() + 0.5, spawnPos.getY() + 1, spawnPos.getZ() + 0.5, player.getYRot(), player.getXRot());
            player.sendSystemMessage(Component.translatable("message.herobrine_companion.system_wake_up"));
            player.addEffect(new net.minecraft.world.effect.MobEffectInstance(MobEffects.BLINDNESS, 100, 0));
            player.addEffect(new net.minecraft.world.effect.MobEffectInstance(MobEffects.CONFUSION, 200, 0));

            if (!data.getBoolean("HasReceivedFragment4")) {
                ItemStack fragment = new ItemStack(HerobrineCompanion.LORE_FRAGMENT.get());
                CompoundTag tag = new CompoundTag();
                tag.putString(LoreFragmentItem.LORE_ID_KEY, "fragment_4");
                fragment.setTag(tag);

                if (!player.getInventory().add(fragment)) player.drop(fragment, false);
                player.sendSystemMessage(Component.translatable("message.herobrine_companion.chat_hero",
                        Component.translatable("message.herobrine_companion.fragment_4_received")).withStyle(ChatFormatting.YELLOW));
                data.putBoolean("HasReceivedFragment4", true);
            }
        }
    }

    private static void teleportToCenter(ServerPlayer player) {
        player.teleportTo(EndRingContext.CENTER_X, EndRingContext.CENTER_Y, EndRingContext.CENTER_Z);
        player.setDeltaMovement(0, 0, 0);
        player.fallDistance = 0;

        if (player.level() instanceof ServerLevel serverLevel) {
            for (var entity : serverLevel.getAllEntities()) {
                if (entity instanceof HeroEntity hero && hero.isAlive()) {
                    hero.teleportTo(EndRingContext.CENTER_X, EndRingContext.CENTER_Y, EndRingContext.CENTER_Z);
                    hero.setDeltaMovement(0, 0, 0);
                    hero.addTag(EndRingContext.TAG_FIXED);
                    break;
                }
            }
        }
    }
}