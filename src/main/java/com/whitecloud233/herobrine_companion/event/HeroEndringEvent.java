package com.whitecloud233.herobrine_companion.event;

import com.whitecloud233.herobrine_companion.HerobrineCompanion;
import com.whitecloud233.herobrine_companion.entity.HeroEntity;
import com.whitecloud233.herobrine_companion.util.EndRingContext;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityTravelToDimensionEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

@EventBusSubscriber(modid = HerobrineCompanion.MODID)
public class HeroEndringEvent {

    // 介绍动画 Tick
    @SubscribeEvent
    public static void onHeroTick(EntityTickEvent.Post event) {
        if (event.getEntity().level().isClientSide) return;
        if (event.getEntity() instanceof HeroEntity hero && hero.getTags().contains(EndRingContext.TAG_INTRO)) {
            runIntroSequence(hero);
        }
    }



    // 阻止 Hero 自行进传送门
    @SubscribeEvent
    public static void onEntityTravelToDimension(EntityTravelToDimensionEvent event) {
        if (event.getEntity() instanceof HeroEntity && !event.getEntity().getTags().contains(EndRingContext.TAG_TELEPORTING)) {
            event.setCanceled(true);
        }
    }

    // --- Helpers ---

    private static void runIntroSequence(HeroEntity hero) {
        CompoundTag data = hero.getPersistentData();
        int tick = data.getInt("IntroTick");
        Player player = hero.level().getNearestPlayer(hero, 50);

        if (tick == 0 && player != null) {
             if (!player.getPersistentData().getBoolean("HasSeenHeroIntro")) {
                player.displayClientMessage(Component.translatable("message.herobrine_companion.hero_welcome_real_illusion", player.getName().getString()), false);
                player.getPersistentData().putBoolean("HasSeenHeroIntro", true);
            }
        }
        
        if (player == null && tick < 100) return; // Wait for player

        if (tick < 100) {
            hero.setDeltaMovement(0, 0, 0);
            hero.setPos(hero.getX(), 104, hero.getZ());
            if (player != null) hero.lookAt(player, 30, 30);
        } else if (tick == 100 && player != null) {
            teleportHeroNearPlayer(hero, player);
        }
        data.putInt("IntroTick", tick + 1);
    }

    private static void teleportHeroNearPlayer(HeroEntity hero, Player player) {
        Vec3 look = player.getLookAngle();
        double targetX = player.getX() + look.x * 2;
        double targetZ = player.getZ() + look.z * 2;
        hero.teleportTo(targetX, player.getY(), targetZ);
        hero.setNoGravity(false);
        hero.removeTag(EndRingContext.TAG_INTRO);
        hero.level().playSound(null, hero.blockPosition(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.HOSTILE, 1.0f, 1.0f);
    }

    private static void handleHeroJoin(HeroEntity newHero, EntityJoinLevelEvent event) {
        if (newHero.getTags().contains(EndRingContext.TAG_RESPAWNED_SAFE)) {
            newHero.removeTag(EndRingContext.TAG_RESPAWNED_SAFE);
            discardAllOtherHeroes(newHero);
            return;
        }
        if (newHero.getTags().contains(EndRingContext.TAG_INTRO)) {
             discardAllOtherHeroes(newHero);
             return; 
        }
        if (checkForDuplicates(newHero)) {
            event.setCanceled(true);
        }
    }
    
    private static void discardAllOtherHeroes(HeroEntity safeHero) {
        if (safeHero.getServer() == null) return;
        for (ServerLevel level : safeHero.getServer().getAllLevels()) {
            for (var entity : level.getAllEntities()) {
                if (entity instanceof HeroEntity existing && existing != safeHero && existing.isAlive()) {
                    existing.discard();
                }
            }
        }
    }

    private static boolean checkForDuplicates(HeroEntity newHero) {
        if (newHero.getServer() == null) return false;
        for (ServerLevel level : newHero.getServer().getAllLevels()) {
            for (var entity : level.getAllEntities()) {
                if (entity instanceof HeroEntity existing && existing != newHero) {
                    if (existing.isAlive() && !existing.isRemoved()) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}