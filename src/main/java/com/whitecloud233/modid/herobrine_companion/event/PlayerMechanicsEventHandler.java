package com.whitecloud233.modid.herobrine_companion.event;

import com.whitecloud233.modid.herobrine_companion.HerobrineCompanion;
import com.whitecloud233.modid.herobrine_companion.entity.logic.spawn.GlitchVillagerSpawner;
import com.whitecloud233.modid.herobrine_companion.entity.logic.spawn.HeroSpawner;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.living.LivingExperienceDropEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = HerobrineCompanion.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class PlayerMechanicsEventHandler {

    private static final HeroSpawner spawner = new HeroSpawner();
    private static final GlitchVillagerSpawner glitchVillagerSpawner = new GlitchVillagerSpawner();

    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase == TickEvent.Phase.END && event.level instanceof ServerLevel serverLevel) {
            spawner.tick(serverLevel);
            glitchVillagerSpawner.tick(serverLevel);
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase == TickEvent.Phase.END && !event.player.level().isClientSide) {
            Player player = event.player;
            if (player.getTags().contains("herobrine_companion.abyssal_gaze_active")) {
                player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 220, 0, false, false, false));
            }

            if (player.getPersistentData().getBoolean("herobrine_companion.transcendence_permit_active")) {
                if (!player.getAbilities().mayfly) {
                    player.getAbilities().mayfly = true;
                    player.onUpdateAbilities();
                }

                if (player.getPersistentData().getBoolean("herobrine_companion.should_restore_flying")) {
                    player.getAbilities().flying = true;
                    player.onUpdateAbilities();

                    if (player.tickCount > 20) {
                        player.getPersistentData().remove("herobrine_companion.should_restore_flying");
                    }
                }
            }

            if (player instanceof ServerPlayer serverPlayer) {
                HeroQuestHandler.tickPacifyQuest(serverPlayer);
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerInteractEntity(PlayerInteractEvent.EntityInteract event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            HeroQuestHandler.onEndermanInteract(player, event.getTarget(), event.getItemStack());
        }
    }

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof Player player) {
            // 保存物品栏数据到 PersistentData，供 PlayerLifecycleHandler 恢复
            if (player.getTags().contains("herobrine_companion.soul_bound_pact_active")) {
                ListTag inventoryTag = new ListTag();
                player.getInventory().save(inventoryTag);

                CompoundTag data = player.getPersistentData();
                data.put("SoulBoundInventory", inventoryTag);
                data.putFloat("SoulBoundXP", player.experienceProgress);
                data.putInt("SoulBoundLevel", player.experienceLevel);
                data.putInt("SoulBoundTotalXP", player.totalExperience);

                player.getInventory().clearContent();
            }

            if (player.getPersistentData().getBoolean("herobrine_companion.transcendence_permit_active")) {
                player.getPersistentData().putBoolean("herobrine_companion.transcendence_permit_flying", player.getAbilities().flying);
            }
        }

        DamageSource source = event.getSource();
        if (source.getEntity() instanceof ServerPlayer player) {
            HeroQuestHandler.onMobKill(player, event.getEntity());
        }
    }

    @SubscribeEvent
    public static void onLivingDrops(LivingDropsEvent event) {
        if (event.getEntity() instanceof Player player) {
            if (player.getTags().contains("herobrine_companion.soul_bound_pact_active")) {
                event.setCanceled(true);
            }
        }
    }

    @SubscribeEvent
    public static void onExperienceDrop(LivingExperienceDropEvent event) {
        if (event.getEntity() instanceof Player player) {
            if (player.getTags().contains("herobrine_companion.soul_bound_pact_active")) {
                event.setCanceled(true);
            }
        }
    }
}