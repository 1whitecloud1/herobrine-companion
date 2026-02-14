package com.whitecloud233.modid.herobrine_companion.world.structure;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;

public class EndRingPiece extends StructurePiece {
    private final int centerX;
    private final int centerY;
    private final int centerZ;

    public EndRingPiece(BoundingBox boundingBox, int centerX, int centerY, int centerZ) {
        super(ModStructurePieces.END_RING_PIECE.get(), 0, boundingBox);
        this.centerX = centerX;
        this.centerY = centerY;
        this.centerZ = centerZ;
        this.setOrientation(null); 
    }

    public EndRingPiece(CompoundTag tag) {
        super(ModStructurePieces.END_RING_PIECE.get(), tag);
        this.centerX = tag.getInt("CenterX");
        this.centerY = tag.getInt("CenterY");
        this.centerZ = tag.getInt("CenterZ");
    }

    public EndRingPiece(StructurePieceSerializationContext context, CompoundTag tag) {
        this(tag);
    }

    @Override
    protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
        tag.putInt("CenterX", this.centerX);
        tag.putInt("CenterY", this.centerY);
        tag.putInt("CenterZ", this.centerZ);
    }

    @Override
    public void postProcess(WorldGenLevel level, StructureManager structureManager, ChunkGenerator generator, RandomSource random, BoundingBox box, ChunkPos chunkPos, BlockPos pos) {
        int[][] rings = {
            {50, 60}, 
            {80, 90}, 
            {110, 120}  
        };
        
        int thickness = 3; 

        BlockState block = Blocks.BEDROCK.defaultBlockState();

        int minX = chunkPos.getMinBlockX();
        int maxX = chunkPos.getMaxBlockX();
        int minZ = chunkPos.getMinBlockZ();
        int maxZ = chunkPos.getMaxBlockZ();

        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                double distSq = (x - centerX) * (x - centerX) + (z - centerZ) * (z - centerZ);
                double dist = Math.sqrt(distSq);

                boolean isRing = false;
                for (int[] ring : rings) {
                    if (dist >= ring[0] && dist <= ring[1]) {
                        isRing = true;
                        break;
                    }
                }

                if (isRing) {
                    for (int y = centerY; y < centerY + thickness; y++) {
                        if (box.isInside(x, y, z)) {
                            this.placeBlock(level, block, x, y, z, box);
                        }
                    }
                }

                if (dist < 50) {
                     if (box.isInside(x, centerY, z)) {
                         this.placeBlock(level, Blocks.END_PORTAL.defaultBlockState(), x, centerY, z, box);
                         this.placeBlock(level, Blocks.BARRIER.defaultBlockState(), x, centerY + 1, z, box);
                     }
                }
            }
        }
    }
}
