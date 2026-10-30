package com.whitecloud233.modid.herobrine_companion.entity.ai.learning;

import com.whitecloud233.modid.herobrine_companion.entity.GhostCreeperEntity;
import com.whitecloud233.modid.herobrine_companion.entity.GhostSkeletonEntity;
import com.whitecloud233.modid.herobrine_companion.entity.GhostZombieEntity;
import com.whitecloud233.modid.herobrine_companion.entity.HeroEntity;
import com.whitecloud233.modid.herobrine_companion.entity.logic.quest.HeroQuestManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;

import java.util.List;

public class HeroFixAnomalyGoal extends Goal {
    /** 方块扫描最小间隔（tick）。原为 10，配合 O(274625) 全量扫描实测占服务端 3.7%；
     *  改为集合反查后单次成本已极低，但仍保留节流避免无意义高频查询。 */
    private static final int BLOCK_SCAN_INTERVAL_TICKS = 20;
    /** 实体异常查询间隔（tick）。原先每个 canUse()（每 tick）都做一次 24 格 AABB 查询。 */
    private static final int ENTITY_SCAN_INTERVAL_TICKS = 10;

    private final HeroEntity hero;
    private LivingEntity targetAnomaly;
    private BlockPos targetBlock;
    private int scanTimer;
    private int lastScanTick;
    private int lastEntityScanTick;
    private List<LivingEntity> cachedEntityHit = List.of();

    public HeroFixAnomalyGoal(HeroEntity hero) {
        this.hero = hero;
        this.setFlags(java.util.EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (this.hero.isBattleModeActive()) return false;

        // [深度学习] 检查心智状态
        SimpleNeuralNetwork.MindState state = this.hero.getHeroBrain().getState();

        // 追忆者：抑郁中，不干活
        if (state == SimpleNeuralNetwork.MindState.REMINISCING) return false;

        // 1. 检查是否在任务模式
        boolean isQuesting = false;
        if (this.hero.getOwnerUUID() != null) {
            Player owner = this.hero.level().getPlayerByUUID(this.hero.getOwnerUUID());
            if (owner != null && HeroQuestManager.isPlayerDoingQuest(owner)) {
                isQuesting = true;
            }
        }

        // 2. 如果在任务模式，完全忽略实体，只找方块
        if (isQuesting) {
            if (this.hero.tickCount % 20 == 0) {
                BlockPos pos = findGlitchBlock();
                if (pos != null) {
                    this.targetBlock = pos;
                    this.targetAnomaly = null;
                    return true;
                }
            }
            return false;
        }

        // [修改] 3. 优先检查方块
        // 之前的问题是：如果一直有怪物，tickCount % 20 的时机可能刚好被怪物战斗占用了，
        // 导致 Hero 永远没机会进入找方块的逻辑。
        // 现在改为：只要距离上次扫描超过 BLOCK_SCAN_INTERVAL_TICKS，就优先尝试扫描方块。
        // [P0 性能] 扫描本身已改为集合反查（O(已追踪数)），这里的间隔只是避免重复查同一批。
        if (this.hero.tickCount - this.lastScanTick > BLOCK_SCAN_INTERVAL_TICKS) {
            this.lastScanTick = this.hero.tickCount;
            BlockPos pos = findGlitchBlock();
            if (pos != null) {
                this.targetBlock = pos;
                this.targetAnomaly = null;
                return true;
            }
        }

        // 4. 其次检查实体
        // [P0 性能] 原实现每次 canUse()（即每 tick）都做一次 24 格 AABB 查询并过滤类型，
        // 是 HeroEntity.aiStep 下的稳定开销。实体异常不需要每 tick 感知，按间隔缓存结果。
        List<LivingEntity> list;
        if (this.hero.tickCount - this.lastEntityScanTick >= ENTITY_SCAN_INTERVAL_TICKS) {
            this.lastEntityScanTick = this.hero.tickCount;
            list = this.hero.level().getEntitiesOfClass(LivingEntity.class, this.hero.getBoundingBox().inflate(24.0D),
                    e -> (e instanceof GhostZombieEntity || e instanceof GhostCreeperEntity || e instanceof GhostSkeletonEntity) && e.isAlive());
            this.cachedEntityHit = list;
        } else {
            list = this.cachedEntityHit;
            // 缓存过期前也要剔除已死亡的实体，避免锁定尸体。
            if (!list.isEmpty()) {
                list = list.stream().filter(LivingEntity::isAlive).toList();
                this.cachedEntityHit = list;
            }
        }

        if (!list.isEmpty()) {
            this.targetAnomaly = list.get(0);
            this.targetBlock = null;
            return true;
        }

        return false;
    }

    private BlockPos findGlitchBlock() {
        return HeroAnomalySupport.findGlitchBlock(this.hero);
    }

    @Override
    public void start() {
        this.scanTimer = 0;
    }

    @Override
    public void tick() {
        // A. 处理实体异常 (远程雷击)
        if (this.targetAnomaly != null) {
            this.hero.getLookControl().setLookAt(this.targetAnomaly, 30.0F, 30.0F);

            if (this.hero.hasLineOfSight(this.targetAnomaly) || this.hero.distanceToSqr(this.targetAnomaly) < 256.0D) {
                if (this.hero.level() instanceof ServerLevel serverLevel) {
                    LightningBolt lightning = EntityType.LIGHTNING_BOLT.create(serverLevel);
                    if (lightning != null) {
                        lightning.moveTo(this.targetAnomaly.position());
                        lightning.setVisualOnly(true); // [修复] 设置为仅视觉效果，防止产生火焰和伤害
                        serverLevel.addFreshEntity(lightning);
                    }
                }
                this.targetAnomaly.hurt(this.hero.damageSources().magic(), 99999F);

                if (this.hero.getRandom().nextInt(10) == 0 && this.hero.isCompanionMode() && this.hero.getOwnerUUID() != null) {
                    Player owner = this.hero.level().getPlayerByUUID(this.hero.getOwnerUUID());
                    if (owner instanceof ServerPlayer serverPlayer) {
                        HeroDialogueHandler.onFixAnomaly(this.hero, serverPlayer);
                    }
                }
            } else {
                this.hero.getNavigation().moveTo(this.targetAnomaly, 1.5D);
            }
        }
        // B. 处理方块异常 (AOE 清除)
        else if (this.targetBlock != null) {
            this.hero.getLookControl().setLookAt(this.targetBlock.getX(), this.targetBlock.getY(), this.targetBlock.getZ(), 30.0F, 30.0F);

            this.hero.getNavigation().stop();

            if (this.scanTimer++ > 10) {
                performAreaCleanse(this.targetBlock);
                this.targetBlock = null;
                this.scanTimer = 0;
            }
        }
    }

    private void performAreaCleanse(BlockPos center) {
        HeroAnomalySupport.performAreaCleanse(this.hero, center);
    }

    @Override
    public boolean canContinueToUse() {
        if (this.hero.isBattleModeActive()) return false;

        if (this.targetAnomaly != null) {
            return this.targetAnomaly.isAlive();
        }
        if (this.targetBlock != null) {
            // [修复] 只要目标锁定，就坚持执行完清除逻辑，不由方块状态决定中断
            return true;
        }
        return false;
    }
}