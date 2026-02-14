package com.whitecloud233.herobrine_companion.entity.ai.learning;

import com.whitecloud233.herobrine_companion.entity.GhostCreeperEntity;
import com.whitecloud233.herobrine_companion.entity.GhostSkeletonEntity;
import com.whitecloud233.herobrine_companion.entity.GhostZombieEntity;
import com.whitecloud233.herobrine_companion.entity.HeroEntity;
import com.whitecloud233.herobrine_companion.entity.logic.HeroQuestHandler;
import com.whitecloud233.herobrine_companion.world.structure.ModStructures;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;

import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;

import java.util.List;

public class HeroFixAnomalyGoal extends Goal {
    private final HeroEntity hero;
    private LivingEntity targetAnomaly;
    private BlockPos targetBlock;
    private int scanTimer;
    private int lastScanTick;

    public HeroFixAnomalyGoal(HeroEntity hero) {
        this.hero = hero;
        this.setFlags(java.util.EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        // [深度学习] 检查心智状态
        SimpleNeuralNetwork.MindState state = this.hero.getHeroBrain().getState();
        
        // 追忆者：抑郁中，不干活
        if (state == SimpleNeuralNetwork.MindState.REMINISCING) return false;

        // 1. 检查是否在任务模式
        boolean isQuesting = false;
        if (this.hero.getOwnerUUID() != null) {
            Player owner = this.hero.level().getPlayerByUUID(this.hero.getOwnerUUID());
            if (owner != null && HeroQuestHandler.isPlayerDoingQuest(owner)) {
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
        // 维护者 (MAINTAINER) 状态下，扫描频率加快 (每 10 tick)
        // 其他状态下，每 20 tick
        int scanInterval = (state == SimpleNeuralNetwork.MindState.MAINTAINER) ? 10 : 20;

        if (this.hero.tickCount - this.lastScanTick > scanInterval) {
            this.lastScanTick = this.hero.tickCount;
            BlockPos pos = findGlitchBlock();
            if (pos != null) {
                this.targetBlock = pos;
                this.targetAnomaly = null;
                return true;
            }
        }

        // 4. 其次检查实体
        // 代码之神 (GLITCH_LORD) 可能会忽略实体异常，觉得那是“特性”
        if (state == SimpleNeuralNetwork.MindState.GLITCH_LORD && this.hero.getRandom().nextBoolean()) {
            return false;
        }

        List<LivingEntity> list = this.hero.level().getEntitiesOfClass(LivingEntity.class, this.hero.getBoundingBox().inflate(24.0D), 
            e -> (e instanceof GhostZombieEntity || e instanceof GhostCreeperEntity || e instanceof GhostSkeletonEntity) && e.isAlive());
        
        if (!list.isEmpty()) {
            this.targetAnomaly = list.get(0);
            this.targetBlock = null;
            return true;
        }

        return false;
    }

    private BlockPos findGlitchBlock() {
        BlockPos heroPos = this.hero.blockPosition();
        // [修改] 扩大扫描范围，特别是 Y 轴
        // 原来: 16x16x8 (x: -32~32, y: -8~8, z: -32~32)
        // 现在: 32x32x32 (x: -32~32, y: -32~32, z: -32~32)
        for (int x = -32; x <= 32; x++) {
            for (int y = -32; y <= 32; y++) { // Y轴范围扩大到上下32格
                for (int z = -32; z <= 32; z++) {
                    BlockPos p = heroPos.offset(x, y, z);
                    BlockState state = this.hero.level().getBlockState(p);

                    if (isGlitchBlock(state)) {
                        if (this.hero.level() instanceof ServerLevel serverLevel && isInUnstableZone(serverLevel, p)) {
                            return p;
                        }
                    }
                }
            }
        }
        return null;
    }
    private boolean isInUnstableZone(ServerLevel level, BlockPos pos) {
        Structure structure = level.registryAccess().registryOrThrow(Registries.STRUCTURE).get(ModStructures.UNSTABLE_ZONE_KEY);
        if (structure == null) return false;

        StructureStart start = level.structureManager().getStructureAt(pos, structure);
        return start.isValid();
    }
    private boolean isGlitchBlock(BlockState state) {
        // [修改] 匹配 UnstableZonePiece 中新生成的方块列表
        // 必须与 UnstableZonePiece.getRandomBlock 保持一致
        if (state.is(Blocks.SPAWNER)) return true;
        if (state.is(Blocks.CRYING_OBSIDIAN)) return true;
        if (state.is(Blocks.GILDED_BLACKSTONE)) return true;
        if (state.is(Blocks.TINTED_GLASS)) return true; // 遮光玻璃
        if (state.is(Blocks.WET_SPONGE)) return true;   // 湿海绵
        if (state.is(Blocks.NETHERRACK)) return true;   // 地狱岩
        if (state.is(Blocks.SOUL_SOIL)) return true;    // 灵魂土
        if (state.is(Blocks.BLACKSTONE)) return true;   // 黑石
        if (state.is(Blocks.BASALT)) return true;       // 玄武岩
        if (state.is(Blocks.MAGMA_BLOCK)) return true;  // 岩浆块
        if (state.is(Blocks.END_STONE)) return true;    // [修改] 末地石
        return false;
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
        if (!(this.hero.level() instanceof ServerLevel serverLevel)) return;

        serverLevel.playSound(null, center, SoundEvents.WITHER_DEATH, SoundSource.HOSTILE, 0.5F, 1.5F);

        int radius = 8; // 扩大范围
        for (int i = 0; i < 10; i++) { // 召唤10道雷电
            int x = center.getX() + this.hero.getRandom().nextInt(radius * 2) - radius;
            int z = center.getZ() + this.hero.getRandom().nextInt(radius * 2) - radius;
            int y = serverLevel.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, x, z);

            LightningBolt lightning = EntityType.LIGHTNING_BOLT.create(serverLevel);
            if (lightning != null) {
                lightning.moveTo(x, y, z);
                lightning.setVisualOnly(true); // [修复] 设置为仅视觉效果，防止产生火焰和伤害
                serverLevel.addFreshEntity(lightning);
            }
        }

        // 延迟一小段时间后清除方块，让雷电效果更明显
        serverLevel.getServer().tell(new net.minecraft.server.TickTask(serverLevel.getServer().getTickCount() + 5, () -> {
            for (int x = -radius; x <= radius; x++) {
                for (int y = -radius; y <= radius; y++) {
                    for (int z = -radius; z <= radius; z++) {
                        BlockPos p = center.offset(x, y, z);
                        BlockState state = serverLevel.getBlockState(p);

                        if (isGlitchBlock(state)) {
                            if (isInUnstableZone(serverLevel, p)) {
                                serverLevel.destroyBlock(p, false);
                                serverLevel.sendParticles(ParticleTypes.LARGE_SMOKE, p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5, 5, 0.5, 0.5, 0.5, 0.05);
                            }
                        }
                    }
                }
            }

            // [新增] 触发对话
            if (this.hero.isCompanionMode() && this.hero.getOwnerUUID() != null) {
                Player owner = this.hero.level().getPlayerByUUID(this.hero.getOwnerUUID());
                if (owner instanceof ServerPlayer serverPlayer) {
                    HeroDialogueHandler.onCleanseArea(this.hero, serverPlayer);
                }
            }
        }));
    }

    @Override
    public boolean canContinueToUse() {
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