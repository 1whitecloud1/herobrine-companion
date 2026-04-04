package com.whitecloud233.modid.herobrine_companion.event;

import com.whitecloud233.modid.herobrine_companion.HerobrineCompanion;
import com.whitecloud233.modid.herobrine_companion.entity.HeroEntity;
import com.whitecloud233.modid.herobrine_companion.entity.projectile.RealmBreakerLightningEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.event.level.BlockEvent;
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
     * 融合了闪电魔法的免疫和守卫目标的精准保护。
     */
    @SubscribeEvent
    public static void onExplosionDetonate(ExplosionEvent.Detonate event) {
        if (event.getLevel().isClientSide) return;

        // ================= [机制 1：免疫专属落雷伤害] =================
        Entity source = event.getExplosion().getDirectSourceEntity();
        if (source instanceof RealmBreakerLightningEntity lightningEntity) {
            // 从受影响的实体列表中移除 HeroEntity 本身
            event.getAffectedEntities().removeIf(entity -> entity instanceof HeroEntity);

            // 从受影响的实体列表中移除发射者 (Owner)
            Entity owner = lightningEntity.getOwner();
            if (owner != null) {
                event.getAffectedEntities().remove(owner);
            }
        }

        // ================= [机制 2：神之领域 - 守卫防爆] =================
        List<BlockPos> affectedBlocks = event.getAffectedBlocks();
        if (affectedBlocks.isEmpty() && event.getAffectedEntities().isEmpty()) return;

        Vec3 explosionPos = event.getExplosion().getPosition();
        double ex = explosionPos.x;
        double ey = explosionPos.y;
        double ez = explosionPos.z;

        // 1. 快速检测：圈定一个较大的范围(32格)寻找 Herobrine
        AABB searchBox = new AABB(ex - 32, ey - 32, ez - 32, ex + 32, ey + 32, ez + 32);
        List<HeroEntity> heroes = event.getLevel().getEntitiesOfClass(HeroEntity.class, searchBox);

        for (HeroEntity hero : heroes) {
            // 2. 检查 Herobrine 是否处于守卫状态 (Action 3) 且有合法的守卫目标
            if (hero.getInvitedAction() == 3 && hero.getInvitedPos() != null) {
                BlockPos guardedPos = hero.getInvitedPos();

                // 【新增校验】如果守卫的箱子/方块已经消失（变为空气），则自动失效，不产生粒子也不保护
                if (event.getLevel().getBlockState(guardedPos).isAir()) continue;

                // 计算爆炸中心到守护点的距离平方
                double distSqrToExplosion = guardedPos.distToCenterSqr(explosionPos);

                // 3. 判定神之领域范围预警 (爆炸源在守护点 20 格内 -> 400平方)
                if (distSqrToExplosion < 400.0) {

                    // [核心逻辑合并]：精准剔除受保护区域内的方块破坏和实体伤害
                    // 保护半径设为 10 格 (100平方)
                    affectedBlocks.removeIf(pos -> pos.distSqr(guardedPos) < 100);

                    // 同样保护守护点周围的实体免受爆炸伤害
                    event.getAffectedEntities().removeIf(ent -> ent.distanceToSqr(guardedPos.getX(), guardedPos.getY(), guardedPos.getZ()) < 100);

                    // 视觉反馈：在守卫点生成神圣粒子
                    if (event.getLevel() instanceof ServerLevel serverLevel) {
                        serverLevel.sendParticles(ParticleTypes.ENCHANT,
                                guardedPos.getX() + 0.5, guardedPos.getY() + 1.0, guardedPos.getZ() + 0.5,
                                20, 0.5, 0.5, 0.5, 0.1);
                    }

                    // 只要找到一个负责的 Herobrine 处理了这次爆炸，就可以退出了
                    break;
                }
            }
        }
    }

    /**
     * 神之领域 - 绝对禁锢 (拦截右键交互)
     * 如果点击的是 Herobrine 正在守卫的方块，且玩家不是被认可的主人，直接抹除该交互。
     */
    @SubscribeEvent
    public static void onPlayerRightClickBlock(net.minecraftforge.event.entity.player.PlayerInteractEvent.RightClickBlock event) {
        net.minecraft.world.entity.player.Player player = event.getEntity();
        if (player == null || player.isSpectator()) return;

        BlockPos clickedPos = event.getPos();

        // 1. 快速检索：划定一个检测范围 (32 格) 寻找 Herobrine
        double searchRadius = 32.0;
        AABB searchBox = new AABB(clickedPos).inflate(searchRadius);
        List<HeroEntity> heroes = event.getLevel().getEntitiesOfClass(HeroEntity.class, searchBox);

        for (HeroEntity hero : heroes) {
            // 2. 确认 Herobrine 处于守卫模式 (Action 3) 且目标存在
            if (hero.getInvitedAction() == 3 && hero.getInvitedPos() != null) {
                BlockPos guardedPos = hero.getInvitedPos();

                // 【新增校验】如果守卫的箱子/方块已经消失，取消禁锢和粒子效果
                if (event.getLevel().getBlockState(guardedPos).isAir()) continue;

                // 3. 精准判定：玩家点击的正是被守卫的方块
                if (clickedPos.equals(guardedPos)) {

                    // 4. 灵魂级甄别：比对 UUID 判断是否为主人
                    if (!player.getUUID().equals(hero.getOwnerUUID())) {

                        // 5. 规则级抹除：彻底取消这次交互事件
                        event.setCanceled(true);
                        event.setUseBlock(net.minecraftforge.eventbus.api.Event.Result.DENY);
                        event.setUseItem(net.minecraftforge.eventbus.api.Event.Result.DENY);

                        // 6. 神罚反馈 (仅在服务端执行)
                        if (!event.getLevel().isClientSide) {
                            ServerLevel serverLevel = (ServerLevel) event.getLevel();

                            serverLevel.playSound(null, clickedPos, net.minecraft.sounds.SoundEvents.WARDEN_HEARTBEAT, net.minecraft.sounds.SoundSource.HOSTILE, 1.0F, 0.5F);
                            serverLevel.playSound(null, clickedPos, net.minecraft.sounds.SoundEvents.ENDERMAN_TELEPORT, net.minecraft.sounds.SoundSource.HOSTILE, 0.5F, 0.5F);

                            serverLevel.sendParticles(net.minecraft.core.particles.ParticleTypes.SCULK_SOUL,
                                    clickedPos.getX() + 0.5, clickedPos.getY() + 1.0, clickedPos.getZ() + 0.5,
                                    15, 0.3, 0.3, 0.3, 0.02);

                            player.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.BLINDNESS, 40, 0, true, false));
                        }
                        return;
                    }
                }
            }
        }
    }

    /**
     * 神之领域 - 绝对壁垒 (拦截左键破坏)
     * 【新增方法】如果破坏的是 Herobrine 正在守卫的方块，且玩家不是被认可的主人，直接取消破坏行为。
     */
    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        net.minecraft.world.entity.player.Player player = event.getPlayer();
        if (player == null || player.isSpectator()) return;

        BlockPos brokenPos = event.getPos();
        net.minecraft.world.level.LevelAccessor levelAccessor = event.getLevel();

        // 需要转换为真正的 Level 对象来进行实体查询
        if (!(levelAccessor instanceof net.minecraft.world.level.Level level)) return;

        double searchRadius = 32.0;
        AABB searchBox = new AABB(brokenPos).inflate(searchRadius);
        List<HeroEntity> heroes = level.getEntitiesOfClass(HeroEntity.class, searchBox);

        for (HeroEntity hero : heroes) {
            if (hero.getInvitedAction() == 3 && hero.getInvitedPos() != null) {
                BlockPos guardedPos = hero.getInvitedPos();

                // 如果守卫的箱子已经消失，不做保护处理
                if (level.getBlockState(guardedPos).isAir()) continue;

                // 判定：别的玩家正在挖掘被守卫的方块
                if (brokenPos.equals(guardedPos)) {
                    if (!player.getUUID().equals(hero.getOwnerUUID())) {

                        // 抹除破坏事件
                        event.setCanceled(true);

                        // 神罚反馈
                        if (!level.isClientSide && level instanceof ServerLevel serverLevel) {
                            serverLevel.playSound(null, brokenPos, net.minecraft.sounds.SoundEvents.WARDEN_HEARTBEAT, net.minecraft.sounds.SoundSource.HOSTILE, 1.0F, 0.5F);

                            serverLevel.sendParticles(net.minecraft.core.particles.ParticleTypes.SCULK_SOUL,
                                    brokenPos.getX() + 0.5, brokenPos.getY() + 1.0, brokenPos.getZ() + 0.5,
                                    15, 0.3, 0.3, 0.3, 0.02);

                            player.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.BLINDNESS, 40, 0, true, false));
                        }
                        return;
                    }
                }
            }
        }
    }
}