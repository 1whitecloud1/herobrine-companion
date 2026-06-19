package com.whitecloud233.herobrine_companion.event;

import com.whitecloud233.herobrine_companion.HerobrineCompanion;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;

@EventBusSubscriber(modid = HerobrineCompanion.MODID)
public class CombatMobsHandler {

    private static final String PEACEFUL_TAG = "herobrine_companion_peaceful";

    // =========================================================================
    // 逻辑 B：破碎 - 攻击即失效 (易碎的伪装)
    // =========================================================================
    @SubscribeEvent
    public static void onPlayerAttack(AttackEntityEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            
            // 只有攻击 "活体生物" 才会打破伪装 (打矿车、画框没事)
            if (event.getTarget() instanceof LivingEntity) {
                
                if (player.getTags().contains(PEACEFUL_TAG)) {
                    
                    // 1. 移除标签 (核心：立刻破除和平)
                    player.removeTag(PEACEFUL_TAG);
                    
                    // 2. 发送提示消息
                    player.sendSystemMessage(Component.translatable("message.herobrine_companion.peace_broken"));
                    
                    // 3. 视觉/听觉反馈 (破碎感)
                    player.level().playSound(null, player.blockPosition(), SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 1.0f, 0.5f);
                    player.level().playSound(null, player.blockPosition(), SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 1.0f, 1.0f);

                    // 4. 惩罚：给予发光效果 (让怪物更容易锁定你)
                    player.addEffect(new MobEffectInstance(MobEffects.GLOWING, 100, 0)); // 发光 5 秒
                    
                    // 5. 瞬间激怒周围的怪物 (强制拉仇恨)
                    // 这会防止玩家利用“砍一刀就跑”的策略
                    double range = 20.0D;
                    for (Mob mob : player.level().getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(range))) {
                        // 如果怪物原本在发呆，现在强制让它攻击玩家
                        if (mob.getTarget() == null) {
                            mob.setTarget(player);
                        }
                    }
                }
            }
        }
    }
}
