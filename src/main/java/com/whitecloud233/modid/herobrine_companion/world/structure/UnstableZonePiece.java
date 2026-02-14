package com.whitecloud233.modid.herobrine_companion.world.structure;

import com.whitecloud233.modid.herobrine_companion.event.ModEvents;
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

public class UnstableZonePiece extends StructurePiece {
    
    public UnstableZonePiece(BlockPos origin, int width, int depth) {
        super(ModStructurePieces.UNSTABLE_ZONE_PIECE.get(), 0, 
              new BoundingBox(origin.getX(), -64, origin.getZ(), 
                              origin.getX() + width, 320, origin.getZ() + depth));
        this.setOrientation(null);
    }

    public UnstableZonePiece(CompoundTag tag) {
        super(ModStructurePieces.UNSTABLE_ZONE_PIECE.get(), tag);
    }

    public UnstableZonePiece(StructurePieceSerializationContext context, CompoundTag tag) {
        this(tag);
    }

    @Override
    protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
    }

    @Override
    public void postProcess(WorldGenLevel level, StructureManager structureManager, ChunkGenerator generator, RandomSource random, BoundingBox box, ChunkPos chunkPos, BlockPos pos) {
        int spawnerCount = 0;
        int maxSpawners = 2; 
        
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
                                spawnerCount++;
                            } else {
                                BlockState randomBlock = getRandomBlock(random);
                                this.placeBlock(level, randomBlock, placePos.getX(), placePos.getY(), placePos.getZ(), box);
                            }
                        }
                    }
                    
                    if (random.nextFloat() < 0.05F) {
                        BlockPos floatPos = surfacePos.above(random.nextInt(5) + 4);
                        if (box.isInside(floatPos)) {
                            BlockState floatBlock = random.nextBoolean() ? Blocks.CRYING_OBSIDIAN.defaultBlockState() : Blocks.TINTED_GLASS.defaultBlockState();
                            this.placeBlock(level, floatBlock, floatPos.getX(), floatPos.getY(), floatPos.getZ(), box);
                        }
                    }
                }
            }
        }
    }


private void placeSpawner(WorldGenLevel level, BlockPos pos, RandomSource random) {
    this.placeBlock(level, Blocks.SPAWNER.defaultBlockState(), pos.getX(), pos.getY(), pos.getZ(), boundingBox);

    BlockEntity blockEntity = level.getBlockEntity(pos);
    if (blockEntity instanceof SpawnerBlockEntity spawner) {
        EntityType<?> entityType = getRandomGhostEntity(random);

        spawner.setEntityId(entityType, random);

        CompoundTag currentNbt = spawner.saveWithFullMetadata();

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

        spawner.load(currentNbt);
    }
}

private EntityType<?> getRandomGhostEntity(RandomSource random) {
    int r = random.nextInt(3);
    if (r == 0) return ModEvents.GHOST_ZOMBIE.get();
    if (r == 1) return ModEvents.GHOST_SKELETON.get();
    return ModEvents.GHOST_CREEPER.get();
}

private BlockState getRandomBlock(RandomSource random) {
    // [修改] 使用更稀有、更具辨识度的方块，避免与地表常见方块混淆
    int r = random.nextInt(100);
    if (r < 20) return Blocks.NETHERRACK.defaultBlockState(); // 地狱岩 (主世界不自然生成)
    if (r < 35) return Blocks.SOUL_SOIL.defaultBlockState(); // 灵魂土
    if (r < 50) return Blocks.BLACKSTONE.defaultBlockState(); // 黑石
    if (r < 65) return Blocks.BASALT.defaultBlockState(); // 玄武岩
    if (r < 75) return Blocks.MAGMA_BLOCK.defaultBlockState(); // 岩浆块
    if (r < 85) return Blocks.END_STONE.defaultBlockState(); // [修改] 黑曜石 -> 末地石
    if (r < 90) return Blocks.TINTED_GLASS.defaultBlockState(); // 遮光玻璃 (替代普通玻璃)
    if (r < 95) return Blocks.WET_SPONGE.defaultBlockState(); // 湿海绵 (替代干海绵)
    if (r < 98) return Blocks.GILDED_BLACKSTONE.defaultBlockState(); // 镶金黑石
    return Blocks.CRYING_OBSIDIAN.defaultBlockState(); // 哭泣黑曜石
}
}