package com.whitecloud233.modid.herobrine_companion.event;

import com.whitecloud233.modid.herobrine_companion.HerobrineCompanion;
import com.whitecloud233.modid.herobrine_companion.fight.ChallengeSession;
import com.whitecloud233.modid.herobrine_companion.entity.HeroEntity;
import com.whitecloud233.modid.herobrine_companion.entity.logic.data.HeroLifecycleHandler;
import com.whitecloud233.modid.herobrine_companion.entity.logic.data.HeroDimensionHandler;
import com.whitecloud233.modid.herobrine_companion.util.EndRingContext;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.EntityTravelToDimensionEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = HerobrineCompanion.MODID)
public class HeroEndringEvent {

    @SubscribeEvent
    public static void onHeroTick(LivingEvent.LivingTickEvent event) {
        if (event.getEntity().level().isClientSide) return;
        if (event.getEntity() instanceof HeroEntity hero && hero.getTags().contains(EndRingContext.TAG_INTRO)) {
            runIntroSequence(hero);
        }
    }

    @SubscribeEvent
    public static void onTravelDimension(EntityTravelToDimensionEvent event) {
        if (event.getEntity() instanceof HeroEntity hero) {
            // 👇 [核心新增] 如果是为了挑战而跨越维度，绝对放行！
            if (ChallengeSession.isHeroInChallenge(hero)) {
                return;
            }

            // ... (下面是你原有的 event.setCanceled(true); 拦截逻辑)
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide) return;
        if (event.getEntity() instanceof HeroEntity hero) {
            handleHeroJoin(hero, event);
        }
    }

    private static void runIntroSequence(HeroEntity hero) {
        if (ChallengeSession.isHeroInChallenge(hero)) return;
        HeroDimensionHandler.handleVoidProtection(hero);
        CompoundTag data = hero.getPersistentData();
        int tick = data.getInt("IntroTick");
        Player player = hero.level().getNearestPlayer(hero, 64);

        if (tick == 0 && player != null) {
             if (!player.getPersistentData().getBoolean("HasSeenHeroIntro")) {
                player.displayClientMessage(Component.translatable("message.herobrine_companion.hero_welcome_real_illusion", player.getName().getString()), false);
                player.getPersistentData().putBoolean("HasSeenHeroIntro", true);
            }
        }
        
        if (player == null && tick < 100) return;

        if (tick < 100) {
            hero.setDeltaMovement(0, 0, 0);
            if (player != null) hero.lookAt(player, 30, 30);
        } else {
            finishIntro(hero);
        }
        data.putInt("IntroTick", tick + 1);
    }

    private static void finishIntro(HeroEntity hero) {
        hero.removeTag(EndRingContext.TAG_INTRO);
        HeroDimensionHandler.handleVoidProtection(hero);
    }

    private static void handleHeroJoin(HeroEntity newHero, EntityJoinLevelEvent event) {
        // Recovery commits its canonical entity only after every join listener accepts the spawn.
        if (newHero.removeTag(com.whitecloud233.modid.herobrine_companion.command.HeroRecoveryService.TAG_STAGED_RECOVERY)
                || newHero.removeTag(EndRingContext.TAG_TRANSFER_STAGED)) {
            return;
        }
        // [修复 1：进场清场]
        if (ChallengeSession.isHeroInChallenge(newHero)) {
            // 必须把之前留在 End Ring 的剧情版假 Hero 强行抹除！只留真身！
            HeroLifecycleHandler.discardAllOtherHeroes(newHero);
            return;
        }

        if (newHero.getTags().contains(EndRingContext.TAG_RESPAWNED_SAFE)) {
            newHero.removeTag(EndRingContext.TAG_RESPAWNED_SAFE);
            if (!event.loadedFromDisk()) {
                HeroLifecycleHandler.discardAllOtherHeroes(newHero);
                return;
            }
        }
        if (newHero.getTags().contains(EndRingContext.TAG_INTRO) && !event.loadedFromDisk()) {
            HeroLifecycleHandler.discardAllOtherHeroes(newHero);
            return;
        }
        if (HeroLifecycleHandler.checkForDuplicates(newHero)) {
            event.setCanceled(true);
        }
    }
}
