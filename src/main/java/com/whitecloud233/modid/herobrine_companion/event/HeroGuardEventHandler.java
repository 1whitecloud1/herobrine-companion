package com.whitecloud233.modid.herobrine_companion.event;

import com.whitecloud233.modid.herobrine_companion.HerobrineCompanion;
import com.whitecloud233.modid.herobrine_companion.entity.HeroEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;

/**
 * 专门处理 Herobrine 守卫状态下的物理规则干涉
 * 防止代码逻辑耦合到 Entity 或 AI 类中
 */
@Mod.EventBusSubscriber(modid = HerobrineCompanion.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class HeroGuardEventHandler {

    /**
     * 神之领域 - 绝对防爆
     * 监听所有爆炸的引爆瞬间 (Detonate)。
     * 这种方式比删除实体更靠谱，因为它直接清空了爆炸的"破坏列表"，
     * 无论是 TNT、核弹、机器爆炸还是魔法爆炸，只要走 Forge 事件系统，全部无效化。
     */
    @SubscribeEvent
    public static void onExplosionDetonate(ExplosionEvent.Detonate event) {
        if (event.getLevel().isClientSide) return;

        Vec3 explosionPos = event.getExplosion().getPosition();

        // 1. 快速检测：先圈定一个较大的范围，看有没有 Herobrine
        // 范围设大一点(32格)，因为有些模组的核弹波及范围很大
        double searchRadius = 32.0;
        AABB searchBox = new AABB(
                explosionPos.x - searchRadius, explosionPos.y - searchRadius, explosionPos.z - searchRadius,
                explosionPos.x + searchRadius, explosionPos.y + searchRadius, explosionPos.z + searchRadius
        );

        List<HeroEntity> heroes = event.getLevel().getEntitiesOfClass(HeroEntity.class, searchBox);

        for (HeroEntity hero : heroes) {
            // 2. 检查 Herobrine 是否处于守卫状态 (Action 3) 且有合法的守卫目标
            if (hero.getInvitedAction() == 3 && hero.getInvitedPos() != null) {

                // 3. 计算爆炸点距离"被守护点" (门/箱子) 的距离
                // 核心逻辑：我们保护的是"财宝"，而不是 Herobrine 本身
                BlockPos guardedPos = hero.getInvitedPos();
                double distSqr = guardedPos.distToCenterSqr(explosionPos);

                // 4. 判定神之领域范围 (半径 20 格 -> 400 平方)
                // 只要爆炸源位于守护点 20 格内，无论威力多大，伤害全部抹除
                if (distSqr < 400.0) {
                    // --- 抹除爆炸伤害 ---

                    // 清空受影响的方块列表 -> 方块不会被破坏
                    event.getAffectedBlocks().clear();

                    // 清空受影响的实体列表 -> 玩家和生物不会受伤
                    event.getAffectedEntities().clear();

                    // (可选) 可以在这里生成一点神圣粒子效果，或者在服务端打印日志
                    // System.out.println("Herobrine suppressed an explosion at " + explosionPos);

                    // 只要找到一个负责的 Herobrine 处理了这次爆炸，就可以退出了
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
    public static void onPlayerRightClickBlock(net.minecraftforge.event.entity.player.PlayerInteractEvent.RightClickBlock event) {
        net.minecraft.world.entity.player.Player player = event.getEntity();
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
                        // 强制拒绝方块和物品的使用逻辑，防止漏网之鱼
                        event.setUseBlock(net.minecraftforge.eventbus.api.Event.Result.DENY);
                        event.setUseItem(net.minecraftforge.eventbus.api.Event.Result.DENY);

                        // 6. 神罚反馈 (仅在服务端执行，给予入侵者视觉和听觉的压迫感)
                        if (!event.getLevel().isClientSide) {
                            net.minecraft.server.level.ServerLevel serverLevel = (net.minecraft.server.level.ServerLevel) event.getLevel();

                            // 播放沉闷的心跳警告音和末影人瞬移的低频音
                            serverLevel.playSound(null, clickedPos, net.minecraft.sounds.SoundEvents.WARDEN_HEARTBEAT, net.minecraft.sounds.SoundSource.HOSTILE, 1.0F, 0.5F);
                            serverLevel.playSound(null, clickedPos, net.minecraft.sounds.SoundEvents.ENDERMAN_TELEPORT, net.minecraft.sounds.SoundSource.HOSTILE, 0.5F, 0.5F);

                            // 在箱子上方爆发幽匿灵魂粒子，警告入侵者
                            serverLevel.sendParticles(net.minecraft.core.particles.ParticleTypes.SCULK_SOUL,
                                    clickedPos.getX() + 0.5, clickedPos.getY() + 1.0, clickedPos.getZ() + 0.5,
                                    15, 0.3, 0.3, 0.3, 0.02);

                            // 创世神的轻微威压：给入侵者施加瞬间的盲目效果 (可选)
                            player.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.BLINDNESS, 40, 0, true, false));
                        }

                        // 既然已经成功拦截，跳出循环即可
                        return;
                    }
                }
            }
        }
    }
}