package com.whitecloud233.modid.herobrine_companion.entity;

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

import com.whitecloud233.modid.herobrine_companion.HerobrineCompanion;

public class GlitchEchoEntity extends Entity {

    private static final EntityDataAccessor<Optional<BlockPos>> TARGET_POS = SynchedEntityData.defineId(GlitchEchoEntity.class, EntityDataSerializers.OPTIONAL_BLOCK_POS);
    private static final EntityDataAccessor<Integer> STATE = SynchedEntityData.defineId(GlitchEchoEntity.class, EntityDataSerializers.INT); // 0: Searching, 1: Moving Horizontal, 2: Indicating Down, 3: Diving

    private static final int STATE_SEARCHING = 0;
    private static final int STATE_MOVING_HORIZONTAL = 1;
    private static final int STATE_INDICATING_DOWN = 2;
    private static final int STATE_DIVING = 3;

    private int lifeTime = 0;
    private int indicateTimer = 0;
    
    private Vec3 lastPos = Vec3.ZERO;
    private int stuckTimer = 0;

    private static final Set<String> VALID_LOOT_TABLES = Set.of(
            "minecraft:chests/ancient_city",
            "minecraft:chests/end_city_treasure",
            "minecraft:chests/stronghold_library"
    );

    public GlitchEchoEntity(EntityType<?> type, Level level) {
        super(type, level);
        this.noCulling = true;
        this.noPhysics = false;
    }

    @Override
    protected void defineSynchedData() {
        this.entityData.define(TARGET_POS, Optional.empty());
        this.entityData.define(STATE, STATE_SEARCHING);
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

        this.move(MoverType.SELF, this.getDeltaMovement());
        this.setDeltaMovement(this.getDeltaMovement().multiply(0.9, 0.9, 0.9)); // Friction
    }

    private void clientTick() {
        if (!this.getSharedFlag(6)) {
            this.setSharedFlag(6, true);
        }

        for (int i = 0; i < 2; i++) {
            if (this.random.nextFloat() < 0.5f) {
                this.level().addParticle(ParticleTypes.PORTAL, this.getX() + (random.nextDouble() - 0.5), this.getY() + random.nextDouble(), this.getZ() + (random.nextDouble() - 0.5), 0, 0, 0);
            }
        }
        
        if (this.random.nextFloat() < 0.3f) {
            this.level().addParticle(ParticleTypes.ENCHANTED_HIT, this.getX(), this.getY() + 0.5, this.getZ(), 0, 0, 0);
        }
        
        if (this.random.nextFloat() < 0.8f) {
            this.level().addParticle(ParticleTypes.END_ROD, this.getX(), this.getY() + 0.5, this.getZ(), 0, 0, 0);
        }

        int state = this.entityData.get(STATE);
        if (state == STATE_INDICATING_DOWN) {
            for (int i = 0; i < 5; i++) {
                this.level().addParticle(ParticleTypes.END_ROD, this.getX(), this.getY() - i * 0.5, this.getZ(), 0, -0.1, 0);
            }
        }
    }

    private void serverTick() {
        int maxLife = (this.entityData.get(STATE) == STATE_SEARCHING) ? 1200 : 6000;
        
        if (this.lifeTime > maxLife) {
            this.remove(RemovalReason.DISCARDED);
            return;
        }

        int state = this.entityData.get(STATE);
        BlockPos target = this.entityData.get(TARGET_POS).orElse(null);

        switch (state) {
            case STATE_SEARCHING:
                if (this.lifeTime > 20) {
                    if (target == null) {
                        target = findTarget();
                        if (target != null) {
                            this.entityData.set(TARGET_POS, Optional.of(target));
                            this.entityData.set(STATE, STATE_MOVING_HORIZONTAL);
                            this.playSound(SoundEvents.NOTE_BLOCK_PLING.get(), 1.0f, 2.0f);
                        } else {
                            this.playSound(SoundEvents.NOTE_BLOCK_BASS.get(), 1.0f, 0.5f);
                            this.remove(RemovalReason.DISCARDED);
                        }
                    }
                }
                break;

            case STATE_MOVING_HORIZONTAL:
                if (target != null) {
                    double targetX = target.getX() + 0.5;
                    double targetZ = target.getZ() + 0.5;
                    Vec3 toTarget = new Vec3(targetX - this.getX(), 0, targetZ - this.getZ());
                    double distHorizontal = toTarget.length();

                    if (this.position().distanceToSqr(lastPos) < 0.0025) {
                        stuckTimer++;
                    } else {
                        stuckTimer = 0;
                    }
                    lastPos = this.position();

                    if (stuckTimer > 10) {
                        Vec3 glitchStep = toTarget.normalize().scale(1.5);
                        this.teleportTo(this.getX() + glitchStep.x, this.getY() + 0.5, this.getZ() + glitchStep.z);
                        this.playSound(SoundEvents.CHORUS_FRUIT_TELEPORT, 0.5f, 2.0f);
                        stuckTimer = 0;
                        break; 
                    }

                    if (distHorizontal < 1.5) {
                        this.entityData.set(STATE, STATE_INDICATING_DOWN);
                        this.setDeltaMovement(0, 0, 0);
                        this.indicateTimer = 100;
                    } else {
                        BlockPos currentPos = this.blockPosition();
                        double groundY = getGroundY(currentPos);
                        double ceilingY = getCeilingY(currentPos);
                        double currentY = this.getY();
                        
                        double safeHeight = groundY + 2.0;
                        if (ceilingY - groundY > 3.0) {
                            double midPoint = (groundY + ceilingY) / 2.0;
                            safeHeight = Math.min(midPoint, groundY + 4.0);
                        } else {
                            safeHeight = groundY + 1.0; 
                        }

                        if (toTarget.length() < 20.0 && target.getY() > safeHeight && ceilingY > target.getY()) {
                            safeHeight = target.getY() + 1.0;
                        }

                        double verticalForce = (safeHeight - currentY) * 0.08;
                        verticalForce = Mth.clamp(verticalForce, -0.3, 0.3);

                        double speed = 0.35;
                        Vec3 desiredMove = toTarget.normalize().scale(speed);

                        Vec3 avoidance = calculateAvoidance(desiredMove, speed);
                        
                        Vec3 finalMove = desiredMove.scale(0.8).add(avoidance.scale(0.2));

                        if (this.horizontalCollision) {
                            finalMove = finalMove.add((random.nextDouble() - 0.5) * 0.2, 0.1, (random.nextDouble() - 0.5) * 0.2);
                        }

                        this.setDeltaMovement(finalMove.x, verticalForce, finalMove.z);

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
                        this.level().playSound(null, target, SoundEvents.CHEST_LOCKED, SoundSource.NEUTRAL, 1.0f, 1.0f);
                        this.remove(RemovalReason.DISCARDED);
                    } else {
                        this.setDeltaMovement(0, -0.5, 0);
                    }
                }
                break;
        }
    }

    private double getGroundY(BlockPos pos) {
        int checkDist = 30;
        BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos(pos.getX(), pos.getY(), pos.getZ());
        
        for (int i = 0; i < checkDist; i++) {
            net.minecraft.world.level.block.state.BlockState state = this.level().getBlockState(mutable);
            
            boolean isSolid = !state.getCollisionShape(this.level(), mutable).isEmpty();
            boolean isFluid = !this.level().getFluidState(mutable).isEmpty();

            if (isSolid || isFluid) {
                return mutable.getY() + 1.0;
            }
            
            mutable.move(0, -1, 0);
        }
        
        return this.getY() - 1.0; 
    }

    private double getCeilingY(BlockPos pos) {
        BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos(pos.getX(), pos.getY(), pos.getZ());
        int checkDist = 10;
        
        for (int i = 0; i < checkDist; i++) {
            mutable.move(0, 1, 0);
            net.minecraft.world.level.block.state.BlockState state = this.level().getBlockState(mutable);
            
            if (!state.getCollisionShape(this.level(), mutable).isEmpty()) {
                return mutable.getY() - 1.0;
            }
        }
        
        return pos.getY() + 20.0;
    }

    private Vec3 calculateAvoidance(Vec3 currentVelocity, double speed) {
        Vec3 force = Vec3.ZERO;
        
        double checkDist = 1.0; 
        
        Vec3 forward = currentVelocity.normalize();
        if (isBlocked(this.position().add(0, 0.5, 0), forward, checkDist)) {
            force = force.add(forward.scale(-1.0));
        }
        
        Vec3 left = forward.yRot((float)Math.toRadians(45));
        if (isBlocked(this.position().add(0, 0.5, 0), left, checkDist)) {
            force = force.add(left.scale(-0.8));
        }

        Vec3 right = forward.yRot((float)Math.toRadians(-45));
        if (isBlocked(this.position().add(0, 0.5, 0), right, checkDist)) {
            force = force.add(right.scale(-0.8));
        }
        
        return force.normalize().scale(speed);
    }

    private boolean isBlocked(Vec3 origin, Vec3 direction, double length) {
        Vec3 end = origin.add(direction.scale(length));
        net.minecraft.world.phys.BlockHitResult result = this.level().clip(new net.minecraft.world.level.ClipContext(
            origin, end,
            net.minecraft.world.level.ClipContext.Block.COLLIDER,
            net.minecraft.world.level.ClipContext.Fluid.NONE,
            this
        ));
        return result.getType() != net.minecraft.world.phys.HitResult.Type.MISS;
    }

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
        BlockPos chestPos = findNearbyChest(100);
        if (chestPos != null) return chestPos;

        return findNearestStructure();
    }

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
                        for (BlockEntity be : chunk.getBlockEntities().values()) {
                            if (be instanceof RandomizableContainerBlockEntity chest) {
                                double dist = origin.distSqr(be.getBlockPos());
                                if (dist > radius * radius) continue;
                                if (dist >= minDistSqr) continue;

                                boolean isMatch = false;

                                CompoundTag tag = chest.saveWithoutMetadata();
                                if (tag.contains("LootTable")) {
                                    String lootTable = tag.getString("LootTable");
                                    if (VALID_LOOT_TABLES.contains(lootTable)) {
                                        isMatch = true;
                                    }
                                }

                                if (!isMatch) {
                                    int containerSize = chest.getContainerSize();
                                    for (int i = 0; i < containerSize; i++) {
                                        ItemStack stack = chest.getItem(i);
                                        if (!stack.isEmpty() && isTargetItem(stack)) {
                                            isMatch = true;
                                            break;
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
