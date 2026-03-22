package com.whitecloud233.herobrine_companion.event;

import com.whitecloud233.herobrine_companion.HerobrineCompanion;
import com.whitecloud233.herobrine_companion.entity.HeroEntity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

@EventBusSubscriber(modid = HerobrineCompanion.MODID)
public class HeroProtectionEvents {

    // 1. 仇恨锁定保护
    @SubscribeEvent
    public static void onLivingChangeTarget(LivingChangeTargetEvent event) {
        LivingEntity newTarget = event.getNewAboutToBeSetTarget();
        if (newTarget instanceof Player player && player.getTags().contains("herobrine_companion_peaceful")) {
            event.setCanceled(true);
        }
        if (newTarget instanceof HeroEntity hero) {
            // [修复] 如果是在挑战模式下，允许被其他实体锁定
            if (!hero.getEntityData().get(HeroEntity.IS_CHALLENGE_ACTIVE)) {
                event.setCanceled(true);
            }
        }
    }

    // 2. 统一伤害和攻击保护 (替代 LivingAttackEvent 和 LivingHurtEvent)
    @SubscribeEvent
    public static void onLivingIncomingDamage(LivingIncomingDamageEvent event) {
        if (event.getEntity() instanceof Player player && player.getTags().contains("herobrine_companion_peaceful")) {
            if (event.getSource().getEntity() != null) {
                event.setCanceled(true);
            }
        }

        if (event.getEntity() instanceof HeroEntity hero) {
            // [修复] 只有在日常模式下才取消攻击/伤害事件，挑战模式放行
            if (!hero.getEntityData().get(HeroEntity.IS_CHALLENGE_ACTIVE)) {
                event.setCanceled(true);
            }
        }
    }

    // 3. 实体 Tick 逻辑 (替代 LivingEvent.LivingTickEvent)
    @SubscribeEvent
    public static void onMobTick(EntityTickEvent.Pre event) {
        if (event.getEntity().level().isClientSide) return;

        if (event.getEntity() instanceof Mob mob) {
            LivingEntity target = mob.getTarget();
            if (target instanceof Player player && player.getTags().contains("herobrine_companion_peaceful")) {
                mob.setTarget(null);
                if (mob instanceof Warden warden) warden.clearAnger(player);
            }
            if (target instanceof HeroEntity hero) {
                // [修复] 日常模式下清除怪物仇恨，挑战模式允许混战
                if (!hero.getEntityData().get(HeroEntity.IS_CHALLENGE_ACTIVE)) {
                    mob.setTarget(null);
                    if (mob instanceof Warden warden) warden.clearAnger(target);
                }
            }
            if (mob instanceof WitherBoss wither && wither.getTarget() instanceof Player player && player.getTags().contains("herobrine_companion_peaceful")) {
                wither.setTarget(null);
            }
        }
    }
}