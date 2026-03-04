package com.whitecloud233.herobrine_companion.event;

import com.whitecloud233.herobrine_companion.HerobrineCompanion;
import com.whitecloud233.herobrine_companion.entity.HeroEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;

import java.util.List;

/**
 * NeoForge 1.21.1 适配版
 * 处理 Herobrine 守卫状态下的物理规则干涉 (绝对防爆、绝对防盗)
 */
@EventBusSubscriber(modid = HerobrineCompanion.MODID, bus = EventBusSubscriber.Bus.GAME)
public class HeroGuardEventHandler {

    @SubscribeEvent
    public static void onExplosionDetonate(ExplosionEvent.Detonate event) {
        if (event.getLevel().isClientSide) return;

        // [修复] 1.21 Mojang 映射下，获取位置的方法是 center()
        Vec3 explosionPos = event.getExplosion().center();

        // 1. 范围检测 (32格)
        double searchRadius = 32.0;
        AABB searchBox = new AABB(
                explosionPos.x - searchRadius, explosionPos.y - searchRadius, explosionPos.z - searchRadius,
                explosionPos.x + searchRadius, explosionPos.y + searchRadius, explosionPos.z + searchRadius
        );

        List<HeroEntity> heroes = event.getLevel().getEntitiesOfClass(HeroEntity.class, searchBox);

        for (HeroEntity hero : heroes) {
            // 2. 检查守卫状态 (Action 3)
            if (hero.getInvitedAction() == 3 && hero.getInvitedPos() != null) {

                BlockPos guardedPos = hero.getInvitedPos();
                double distSqr = guardedPos.distToCenterSqr(explosionPos);

                // 3. 判定神之领域范围 (20格)
                // 只要爆炸源位于守护点 20 格内，抹除伤害
                if (distSqr < 400.0) {
                    event.getAffectedBlocks().clear();
                    event.getAffectedEntities().clear();
                    return;
                }
            }
        }
    }

    /**
     * 神之领域 - 绝对禁锢 (拦截右键交互)
     * 监听玩家右键点击方块的瞬间。
     * 如果点击的是 Herobrine 正在守卫的方块，且玩家不是被认可的主人，
     * 直接在事件层抹除该交互，阻止任何容器界面的打开、声音或动画。
     */
    @SubscribeEvent
    public static void onPlayerRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        Player player = event.getEntity();
        if (player == null || player.isSpectator()) return;

        BlockPos clickedPos = event.getPos();

        // 1. 快速检索：划定一个检测范围 (例如 32 格) 寻找 Herobrine
        double searchRadius = 32.0;
        AABB searchBox = new AABB(clickedPos).inflate(searchRadius);
        List<HeroEntity> heroes = event.getLevel().getEntitiesOfClass(HeroEntity.class, searchBox);

        for (HeroEntity hero : heroes) {
            // 2. 确认 Herobrine 处于守卫模式 (Action 3) 且目标存在
            if (hero.getInvitedAction() == 3 && hero.getInvitedPos() != null) {
                BlockPos guardedPos = hero.getInvitedPos();

                // 3. 精准判定：玩家点击的正是被守卫的方块
                if (clickedPos.equals(guardedPos)) {

                    // 4. 灵魂级甄别：比对 UUID 判断是否为主人
                    if (!player.getUUID().equals(hero.getOwnerUUID())) {

                        // 5. 规则级抹除：彻底取消这次交互事件
                        event.setCanceled(true);
// 如果你想防止客户端因为被拦截而产生短暂的“手部挥动”动画，可以补充设置取消结果：
                        event.setCancellationResult(net.minecraft.world.InteractionResult.FAIL);

                        // 6. 神罚反馈 (仅在服务端执行，给予入侵者视觉和听觉的压迫感)
                        if (!event.getLevel().isClientSide) {
                            ServerLevel serverLevel = (ServerLevel) event.getLevel();

                            // 播放沉闷的心跳警告音和末影人瞬移的低频音
                            serverLevel.playSound(null, clickedPos, SoundEvents.WARDEN_HEARTBEAT, SoundSource.HOSTILE, 1.0F, 0.5F);
                            serverLevel.playSound(null, clickedPos, SoundEvents.ENDERMAN_TELEPORT, SoundSource.HOSTILE, 0.5F, 0.5F);

                            // 在箱子上方爆发幽匿灵魂粒子，警告入侵者
                            serverLevel.sendParticles(ParticleTypes.SCULK_SOUL,
                                    clickedPos.getX() + 0.5, clickedPos.getY() + 1.0, clickedPos.getZ() + 0.5,
                                    15, 0.3, 0.3, 0.3, 0.02);

                            // 创世神的轻微威压：给入侵者施加瞬间的盲目效果 (可选)
                            player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 40, 0, true, false));
                        }

                        // 既然已经成功拦截，跳出循环即可
                        return;
                    }
                }
            }
        }
    }
}