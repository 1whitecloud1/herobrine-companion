package com.whitecloud233.herobrine_companion.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.structure.BuiltinStructures;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import com.whitecloud233.herobrine_companion.HerobrineCompanion;

public class GlitchEchoEntity extends Entity {

    private static final EntityDataAccessor<Optional<BlockPos>> TARGET_POS = SynchedEntityData.defineId(GlitchEchoEntity.class, EntityDataSerializers.OPTIONAL_BLOCK_POS);
    private static final EntityDataAccessor<Integer> STATE = SynchedEntityData.defineId(GlitchEchoEntity.class, EntityDataSerializers.INT); // 0: Searching, 1: Moving Horizontal, 2: Indicating Down, 3: Diving

    private static final int STATE_SEARCHING = 0;
    private static final int STATE_MOVING_HORIZONTAL = 1;
    private static final int STATE_INDICATING_DOWN = 2;
    private static final int STATE_DIVING = 3;

    private int lifeTime = 0;
    private int indicateTimer = 0;
    
    // [新增] 防卡死变量
    private Vec3 lastPos = Vec3.ZERO;
    private int stuckTimer = 0;

    // [新增] 包含 Eternal Key 的战利品表白名单
    private static final Set<String> VALID_LOOT_TABLES = Set.of(
            "minecraft:chests/ancient_city",
            "minecraft:chests/end_city_treasure",
            "minecraft:chests/stronghold_library"
    );

    public GlitchEchoEntity(EntityType<?> type, Level level) {
        super(type, level);
        this.noCulling = true;
        this.noPhysics = false; // [修复] 禁用穿墙模式，使其受物理碰撞影响
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(TARGET_POS, Optional.empty());
        builder.define(STATE, STATE_SEARCHING);
    }

    @Override
    public void tick() {
        super.tick();
        this.lifeTime++;

        if (this.level().isClientSide) {
            clientTick();
        } else {
            serverTick();
        }

        // Movement logic
        this.move(MoverType.SELF, this.getDeltaMovement());
        this.setDeltaMovement(this.getDeltaMovement().multiply(0.9, 0.9, 0.9)); // Friction
    }

    private void clientTick() {
        // [修复] 使用 setSharedFlag(6, true) 来设置发光，因为 isGlowing/setGlowing 可能在当前映射中不可用
        if (!this.getSharedFlag(6)) {
            this.setSharedFlag(6, true);
        }

        // Glitch particles - Increased density
        for (int i = 0; i < 2; i++) {
            if (this.random.nextFloat() < 0.5f) {
                this.level().addParticle(ParticleTypes.PORTAL, this.getX() + (random.nextDouble() - 0.5), this.getY() + random.nextDouble(), this.getZ() + (random.nextDouble() - 0.5), 0, 0, 0);
            }
        }
        
        if (this.random.nextFloat() < 0.3f) {
            this.level().addParticle(ParticleTypes.ENCHANTED_HIT, this.getX(), this.getY() + 0.5, this.getZ(), 0, 0, 0);
        }
        
        // [新增] 增加发光粒子，使其更明显
        if (this.random.nextFloat() < 0.8f) {
            this.level().addParticle(ParticleTypes.END_ROD, this.getX(), this.getY() + 0.5, this.getZ(), 0, 0, 0);
        }

        // State specific particles
        int state = this.entityData.get(STATE);
        if (state == STATE_INDICATING_DOWN) {
            // Beam down particles
            for (int i = 0; i < 5; i++) {
                this.level().addParticle(ParticleTypes.END_ROD, this.getX(), this.getY() - i * 0.5, this.getZ(), 0, -0.1, 0);
            }
        }
    }

    private void serverTick() {
        // [修复] 寿命逻辑优化
        // 如果正在寻找目标(STATE_SEARCHING)，限制 60秒寿命
        // 如果已经有目标(STATE_MOVING...)，给予更长的寿命 (例如 6000 tick = 5分钟)，防止飞到一半消失
        int maxLife = (this.entityData.get(STATE) == STATE_SEARCHING) ? 1200 : 6000;
        
        if (this.lifeTime > maxLife) {
            this.discard();
            return;
        }

        int state = this.entityData.get(STATE);
        BlockPos target = this.entityData.get(TARGET_POS).orElse(null);

        switch (state) {
            case STATE_SEARCHING:
                if (this.lifeTime > 20) { // Wait 1 second before searching
                    if (target == null) {
                        target = findTarget();
                        if (target != null) {
                            this.entityData.set(TARGET_POS, Optional.of(target));
                            this.entityData.set(STATE, STATE_MOVING_HORIZONTAL);
                            this.playSound(SoundEvents.NOTE_BLOCK_PLING.value(), 1.0f, 2.0f);
                        } else {
                            // No target found, die
                            this.playSound(SoundEvents.NOTE_BLOCK_BASS.value(), 1.0f, 0.5f);
                            this.discard();
                        }
                    }
                }
                break;

            case STATE_MOVING_HORIZONTAL:
                if (target != null) {
                    // 1. 基础目标向量
                    double targetX = target.getX() + 0.5;
                    double targetZ = target.getZ() + 0.5;
                    Vec3 toTarget = new Vec3(targetX - this.getX(), 0, targetZ - this.getZ());
                    double distHorizontal = toTarget.length();

                    // --- [新增] 防卡死检测系统 ---
                    // 如果当前 tick 移动距离极小 (小于 0.05)，说明被墙挡住了
                    if (this.position().distanceToSqr(lastPos) < 0.0025) { // 0.05 * 0.05
                        stuckTimer++;
                    } else {
                        stuckTimer = 0;
                    }
                    lastPos = this.position();

                    // 如果卡住了超过 10 tick (0.5秒)，触发"故障穿梭"
                    if (stuckTimer > 10) {
                        // 向目标方向强制瞬移 1-2 格 (穿墙)
                        Vec3 glitchStep = toTarget.normalize().scale(1.5);
                        this.teleportTo(this.getX() + glitchStep.x, this.getY() + 0.5, this.getZ() + glitchStep.z);
                        this.playSound(SoundEvents.CHORUS_FRUIT_TELEPORT, 0.5f, 2.0f); // 播放音效
                        stuckTimer = 0; // 重置
                        // 瞬移后直接结束这一帧
                        break; 
                    }
                    // ---------------------------

                    if (distHorizontal < 1.5) { // 稍微放宽判定范围
                        this.entityData.set(STATE, STATE_INDICATING_DOWN);
                        this.setDeltaMovement(0, 0, 0);
                        this.indicateTimer = 100;
                    } else {
                        // A. 动态高度适应 (保持之前的逻辑)
                        BlockPos currentPos = this.blockPosition();
                        double groundY = getGroundY(currentPos);
                        double ceilingY = getCeilingY(currentPos);
                        double currentY = this.getY();
                        
                        double safeHeight = groundY + 2.0; // 默认离地 2 格，更低一点以便钻洞
                        if (ceilingY - groundY > 3.0) {
                            double midPoint = (groundY + ceilingY) / 2.0;
                            safeHeight = Math.min(midPoint, groundY + 4.0);
                        } else {
                            // 空间极其狭窄时，贴地 1 格
                            safeHeight = groundY + 1.0; 
                        }

                        // 如果目标在上方，允许抬升
                        if (toTarget.length() < 20.0 && target.getY() > safeHeight && ceilingY > target.getY()) {
                            safeHeight = target.getY() + 1.0;
                        }

                        // B. 垂直速度
                        double verticalForce = (safeHeight - currentY) * 0.08;
                        verticalForce = Mth.clamp(verticalForce, -0.3, 0.3);

                        // C. 水平移动 (滑动算法)
                        double speed = 0.35; // 稍微降低速度以增加控制力
                        Vec3 desiredMove = toTarget.normalize().scale(speed);

                        // [关键修改] 避障不再替换速度，而是作为修正力
                        Vec3 avoidance = calculateAvoidance(desiredMove, speed);
                        
                        // 混合力：80% 目标方向 + 20% 避障方向
                        // 在狭窄通道里，避障力往往是相互抵消的，所以必须保留主方向
                        Vec3 finalMove = desiredMove.scale(0.8).add(avoidance.scale(0.2));

                        // [关键修改] 物理碰撞补偿
                        // 如果这一帧发生了水平碰撞 (撞墙了)，给它一个微小的随机扰动，防止死磕墙角
                        if (this.horizontalCollision) {
                            finalMove = finalMove.add((random.nextDouble() - 0.5) * 0.2, 0.1, (random.nextDouble() - 0.5) * 0.2);
                        }

                        this.setDeltaMovement(finalMove.x, verticalForce, finalMove.z);

                        // D. 转向
                        double angle = Mth.atan2(this.getDeltaMovement().z, this.getDeltaMovement().x) * (180F / Math.PI) - 90F;
                        this.setYRot(rotlerp(this.getYRot(), (float) angle, 20.0F));
                    }
                }
                break;

            case STATE_INDICATING_DOWN:
                this.indicateTimer--;
                if (this.indicateTimer <= 0) {
                    this.entityData.set(STATE, STATE_DIVING);
                }
                break;

            case STATE_DIVING:
                if (target != null) {
                    double dy = target.getY() - this.getY();
                    if (Math.abs(dy) < 1.0) {
                        // Arrived
                        this.level().playSound(null, target, SoundEvents.CHEST_LOCKED, SoundSource.NEUTRAL, 1.0f, 1.0f);
                        this.discard();
                    } else {
                        this.setDeltaMovement(0, -0.5, 0); // Dive down
                    }
                }
                break;
        }
    }

    // 获取正下方地面的 Y 坐标 (修复版：识别水面)
    private double getGroundY(BlockPos pos) {
        int checkDist = 30; // 增加一点探测距离防止高空掉落
        BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos(pos.getX(), pos.getY(), pos.getZ());
        
        for (int i = 0; i < checkDist; i++) {
            net.minecraft.world.level.block.state.BlockState state = this.level().getBlockState(mutable);
            
            // [关键修复]：同时检查是否是固体碰撞箱 OR 是否是液体
            boolean isSolid = !state.getCollisionShape(this.level(), mutable).isEmpty();
            boolean isFluid = !this.level().getFluidState(mutable).isEmpty();

            if (isSolid || isFluid) {
                // 找到了地面或水面，返回上方 1 格的高度
                return mutable.getY() + 1.0;
            }
            
            mutable.move(0, -1, 0);
        }
        
        // 如果下方全是空气（比如在虚空之上），缓慢下降
        return this.getY() - 1.0; 
    }

    // 获取正上方天花板的 Y 坐标 (探测距离 10 格)
    private double getCeilingY(BlockPos pos) {
        BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos(pos.getX(), pos.getY(), pos.getZ());
        int checkDist = 10;
        
        for (int i = 0; i < checkDist; i++) {
            mutable.move(0, 1, 0); // 向上扫描
            net.minecraft.world.level.block.state.BlockState state = this.level().getBlockState(mutable);
            
            // 如果遇到固体或树叶，视为天花板
            if (!state.getCollisionShape(this.level(), mutable).isEmpty()) {
                return mutable.getY() - 1.0; // 返回方块下方一格的高度
            }
        }
        
        // 如果上方 10 格都是空气，返回一个很高的值
        return pos.getY() + 20.0;
    }

    // 计算避障向量 (简化版：推离最近的方块)
    private Vec3 calculateAvoidance(Vec3 currentVelocity, double speed) {
        Vec3 force = Vec3.ZERO;
        
        // 只检测非常近的距离 (1.0 格)
        // 就像昆虫的胡须，太远了不需要躲
        double checkDist = 1.0; 
        
        // 检测前方
        Vec3 forward = currentVelocity.normalize();
        if (isBlocked(this.position().add(0, 0.5, 0), forward, checkDist)) {
            // 前方有墙，产生一个反向力
            force = force.add(forward.scale(-1.0));
        }
        
        // 检测左前方 (45度)
        Vec3 left = forward.yRot((float)Math.toRadians(45));
        if (isBlocked(this.position().add(0, 0.5, 0), left, checkDist)) {
            force = force.add(left.scale(-0.8));
        }

        // 检测右前方 (45度)
        Vec3 right = forward.yRot((float)Math.toRadians(-45));
        if (isBlocked(this.position().add(0, 0.5, 0), right, checkDist)) {
            force = force.add(right.scale(-0.8));
        }
        
        return force.normalize().scale(speed);
    }

    // 辅助方法：射线检测是否堵塞
    private boolean isBlocked(Vec3 origin, Vec3 direction, double length) {
        Vec3 end = origin.add(direction.scale(length));
        net.minecraft.world.phys.BlockHitResult result = this.level().clip(new net.minecraft.world.level.ClipContext(
            origin, end,
            net.minecraft.world.level.ClipContext.Block.COLLIDER, // 只检测有碰撞箱的方块
            net.minecraft.world.level.ClipContext.Fluid.NONE,     // 忽略水
            this
        ));
        return result.getType() != net.minecraft.world.phys.HitResult.Type.MISS;
    }

    // 角度插值辅助方法
    protected float rotlerp(float current, float target, float maxDelta) {
        float f = Mth.wrapDegrees(target - current);
        if (f > maxDelta) {
            f = maxDelta;
        }
        if (f < -maxDelta) {
            f = -maxDelta;
        }
        return current + f;
    }

    private BlockPos findTarget() {
        // 1. Try to find actual chest nearby
        BlockPos chestPos = findNearbyChest(100);
        if (chestPos != null) return chestPos;

        // 2. Fallback: Find nearest structure
        return findNearestStructure();
    }

    // 假设你的目标物品是这个，请替换为你模组里的实际物品引用
    // 例如: ModItems.ETERNAL_KEY.get()
    private boolean isTargetItem(ItemStack stack) {
        return stack.getItem() == HerobrineCompanion.ETERNAL_KEY.get();
    }

    private BlockPos findNearbyChest(int radius) {
        BlockPos origin = this.blockPosition();
        BlockPos nearest = null;
        double minDistSqr = Double.MAX_VALUE;

        if (this.level() instanceof ServerLevel serverLevel) {
            int chunkRadius = (radius >> 4) + 1;
            int originChunkX = origin.getX() >> 4;
            int originChunkZ = origin.getZ() >> 4;

            for (int cx = -chunkRadius; cx <= chunkRadius; cx++) {
                for (int cz = -chunkRadius; cz <= chunkRadius; cz++) {
                    LevelChunk chunk = serverLevel.getChunkSource().getChunk(originChunkX + cx, originChunkZ + cz, false);
                    
                    if (chunk != null) {
                        // 遍历区块内所有方块实体
                        for (BlockEntity be : chunk.getBlockEntities().values()) {
                            if (be instanceof RandomizableContainerBlockEntity chest) {
                                double dist = origin.distSqr(be.getBlockPos());
                                if (dist > radius * radius) continue;
                                if (dist >= minDistSqr) continue; // 已经有更近的目标了，跳过

                                boolean isMatch = false;

                                // --- 检查逻辑 A: 尚未生成的战利品箱 (LootTable) ---
                                CompoundTag tag = chest.saveWithoutMetadata(serverLevel.registryAccess());
                                if (tag.contains("LootTable")) {
                                    String lootTable = tag.getString("LootTable");
                                    if (VALID_LOOT_TABLES.contains(lootTable)) {
                                        isMatch = true;
                                    }
                                }

                                // --- 检查逻辑 B: 已经生成的物品 (Inventory Scan) ---
                                // 只有当逻辑A没命中时，才检查实际物品，节省性能
                                if (!isMatch) {
                                    // 这是一个非破坏性检查，不需要打开箱子
                                    int containerSize = chest.getContainerSize();
                                    for (int i = 0; i < containerSize; i++) {
                                        ItemStack stack = chest.getItem(i);
                                        if (!stack.isEmpty() && isTargetItem(stack)) {
                                            isMatch = true;
                                            break; // 找到了！
                                        }
                                    }
                                }

                                if (isMatch) {
                                    minDistSqr = dist;
                                    nearest = be.getBlockPos();
                                }
                            }
                        }
                    }
                }
            }
        }
        return nearest;
    }

    private BlockPos findNearestStructure() {
        if (this.level() instanceof ServerLevel serverLevel) {
            List<Holder<Structure>> targets = new ArrayList<>();
            
            serverLevel.registryAccess().registry(Registries.STRUCTURE).ifPresent(registry -> {
                registry.getHolder(BuiltinStructures.ANCIENT_CITY).ifPresent(targets::add);
                registry.getHolder(BuiltinStructures.STRONGHOLD).ifPresent(targets::add);
                registry.getHolder(BuiltinStructures.END_CITY).ifPresent(targets::add);
            });

            if (targets.isEmpty()) return null;

            // [修改] 将搜索半径从 100 增加到 500 (单位通常是 chunk)
            // 500 chunk = 8000 格，足以覆盖大部分需求
            com.mojang.datafixers.util.Pair<BlockPos, Holder<Structure>> result = serverLevel.getChunkSource().getGenerator()
                    .findNearestMapStructure(serverLevel, HolderSet.direct(targets), this.blockPosition(), 500, false);

            if (result != null) {
                return result.getFirst();
            }
        }
        return null;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag compound) {
        if (compound.contains("TargetX")) {
            this.entityData.set(TARGET_POS, Optional.of(new BlockPos(compound.getInt("TargetX"), compound.getInt("TargetY"), compound.getInt("TargetZ"))));
        }
        this.entityData.set(STATE, compound.getInt("State"));
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag compound) {
        this.entityData.get(TARGET_POS).ifPresent(pos -> {
            compound.putInt("TargetX", pos.getX());
            compound.putInt("TargetY", pos.getY());
            compound.putInt("TargetZ", pos.getZ());
        });
        compound.putInt("State", this.entityData.get(STATE));
    }
    
    @Override
    public boolean isNoGravity() {
        return true;
    }
}
