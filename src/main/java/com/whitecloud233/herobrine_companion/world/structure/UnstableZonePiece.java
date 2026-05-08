package com.whitecloud233.herobrine_companion.world.structure;

import com.whitecloud233.herobrine_companion.event.ModEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import java.util.LinkedHashSet;
import java.util.Set;

public class UnstableZonePiece extends StructurePiece {
    
    public UnstableZonePiece(BlockPos origin, int width, int depth) {
        super(ModStructurePieces.UNSTABLE_ZONE_PIECE.get(), 0, 
              new BoundingBox(origin.getX(), -64, origin.getZ(), 
                              origin.getX() + width, 320, origin.getZ() + depth));
        this.setOrientation(null);
    }

    public UnstableZonePiece(StructurePieceSerializationContext context, CompoundTag tag) {
        super(ModStructurePieces.UNSTABLE_ZONE_PIECE.get(), tag);
    }

    @Override
    protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
    }

    @Override
    public void postProcess(WorldGenLevel level, StructureManager structureManager, ChunkGenerator generator, RandomSource random, BoundingBox box, ChunkPos chunkPos, BlockPos pos) {
        int spawnerCount = 0;
        int maxSpawners = 2;
        Set<BlockPos> trackedPositions = new LinkedHashSet<>();

        int centerX = (this.boundingBox.minX() + this.boundingBox.maxX()) / 2;
        int centerZ = (this.boundingBox.minZ() + this.boundingBox.maxZ()) / 2;
        
        BoundingBox spawnerZone = new BoundingBox(centerX - 2, -64, centerZ - 2, centerX + 2, 320, centerZ + 2);

        for (int x = this.boundingBox.minX(); x <= this.boundingBox.maxX(); x++) {
            for (int z = this.boundingBox.minZ(); z <= this.boundingBox.maxZ(); z++) {
                
                int surfaceY = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z);
                BlockPos surfacePos = new BlockPos(x, surfaceY, z);

                if (random.nextFloat() < 0.4F) {
                    int height = random.nextInt(3) + 1;
                    
                    for (int i = 0; i < height; i++) {
                        BlockPos placePos = surfacePos.above(i);

                        if (box.isInside(placePos)) {
                            boolean canSpawnSpawner = spawnerZone.isInside(placePos);

                            if (canSpawnSpawner && spawnerCount < maxSpawners && random.nextFloat() < 0.10F) {
                                placeSpawner(level, placePos, random);
                                trackedPositions.add(placePos.immutable());
                                spawnerCount++;
                            } else {
                                BlockState randomBlock = UnstableZoneRuntime.getRandomGenerationBlock(random);
                                this.placeBlock(level, randomBlock, placePos.getX(), placePos.getY(), placePos.getZ(), box);
                                trackedPositions.add(placePos.immutable());
                            }
                        }
                    }

                    if (random.nextFloat() < 0.05F) {
                        BlockPos floatPos = surfacePos.above(random.nextInt(5) + 4);
                        if (box.isInside(floatPos)) {
                            BlockState floatBlock = UnstableZoneRuntime.getRandomFloatBlock(random);
                            this.placeBlock(level, floatBlock, floatPos.getX(), floatPos.getY(), floatPos.getZ(), box);
                            trackedPositions.add(floatPos.immutable());
                        }
                    }
                }
            }
        }

        UnstableZoneRuntime.registerGeneratedBlocks(level.getLevel(), this.boundingBox, trackedPositions);
    }

    private void placeSpawner(WorldGenLevel level, BlockPos pos, RandomSource random) {
        this.placeBlock(level, Blocks.SPAWNER.defaultBlockState(), pos.getX(), pos.getY(), pos.getZ(), boundingBox);
        
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity instanceof SpawnerBlockEntity spawner) {
            EntityType<?> entityType = getRandomGhostEntity(random);
            
            spawner.setEntityId(entityType, random);
            
            CompoundTag currentNbt = spawner.saveWithFullMetadata(level.registryAccess());
            
            // Adjust spawn delay and count to reduce spawn rate
            currentNbt.putShort("MinSpawnDelay", (short) 300); // 15 seconds
            currentNbt.putShort("MaxSpawnDelay", (short) 900); // 45 seconds
            currentNbt.putShort("SpawnCount", (short) 2);      // Spawn 2 mobs at a time
            currentNbt.putShort("MaxNearbyEntities", (short) 6);
            currentNbt.putShort("RequiredPlayerRange", (short) 16);
            
            CompoundTag spawnPotentials = currentNbt.getCompound("SpawnData");
            if (spawnPotentials.isEmpty()) {
                spawnPotentials = new CompoundTag();
            }
            
            CompoundTag customRules = new CompoundTag();
            CompoundTag lightLimit = new CompoundTag();
            lightLimit.putInt("min_inclusive", 0);
            lightLimit.putInt("max_inclusive", 15);
            
            customRules.put("block_light_limit", lightLimit);
            customRules.put("sky_light_limit", lightLimit);
            
            spawnPotentials.put("custom_spawn_rules", customRules);
            
            currentNbt.put("SpawnData", spawnPotentials);
            
            spawner.loadWithComponents(currentNbt, level.registryAccess());
        }
    }

    private EntityType<?> getRandomGhostEntity(RandomSource random) {
        int r = random.nextInt(3);
        if (r == 0) return ModEvents.GHOST_ZOMBIE.get();
        if (r == 1) return ModEvents.GHOST_SKELETON.get();
        return ModEvents.GHOST_CREEPER.get();
    }

}