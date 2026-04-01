package com.whitecloud233.modid.herobrine_companion.client.fight.event;

import com.whitecloud233.modid.herobrine_companion.client.fight.HeroChallengeManager;
import com.whitecloud233.modid.herobrine_companion.entity.HeroEntity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = "herobrine_companion")
public class ChallengeEventHandler {

    // 【修复核心】：使用 LivingDeathEvent 拦截致死一击，100% 准确
    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof HeroEntity hero) {
            if (hero.getPersistentData().getBoolean("IsChallengeActive")
                    && !hero.getPersistentData().getBoolean("IsFakeOutPhase")) {

                event.setCanceled(true); // 取消死亡
                hero.setHealth(1.0f);    // 锁在1滴血

                // 1. 触发第一阶段演出（黑屏发包，Boss悬浮）
                HeroChallengeManager.triggerFakeOutPhase(hero);

                // 【注意】：之前的 server.tell 延时代码已经被彻底删除了！
                // 我们把倒计时交给了 HeroChallengeState 来处理
            }
        }
    }

    // 依然保留 DamageEvent，但只用来处理假死阶段的无敌和玩家锁血
    @SubscribeEvent
    public static void onLivingDamage(LivingDamageEvent event) {
        // 1. 【核心救命补丁】：防止玩家在假死演出期间“鞭尸”把 Herobrine 打死！
        if (event.getEntity() instanceof HeroEntity hero) {
            if (hero.getPersistentData().getBoolean("IsFakeOutPhase")) {
                event.setCanceled(true); // 绝对无敌
                return;
            }
        }

        // 2. 玩家在“虚晃一枪”期间受到的伤害锁血与无敌
        if (event.getEntity() instanceof ServerPlayer player) {
            if (player.getPersistentData().getBoolean("HeroFakeOutPhase")) {
                // 玩家进入绝对的剧情无敌状态，防虚空伤害
                event.setCanceled(true);
                player.setHealth(1.0f);
            }
        }
    }
}