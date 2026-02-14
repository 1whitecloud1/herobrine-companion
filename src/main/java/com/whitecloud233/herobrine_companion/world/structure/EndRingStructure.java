package com.whitecloud233.herobrine_companion.world.structure;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePiecesBuilder;

import java.util.Optional;

public class EndRingStructure extends Structure {
    public static final MapCodec<EndRingStructure> CODEC = simpleCodec(EndRingStructure::new);

    public EndRingStructure(StructureSettings settings) {
        super(settings);
    }

    @Override
    public Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
        // Only generate at the center chunk (0,0)
        if (context.chunkPos().x != 0 || context.chunkPos().z != 0) {
            return Optional.empty();
        }

        // Check dimension height to ensure we are in the custom dimension (Height = 1024)
        if (context.heightAccessor().getHeight() != 1024) {
            return Optional.empty();
        }

        // Set the generation height.
        // Y = 100
        int y = 100; 
        BlockPos blockpos = new BlockPos(0, y, 0);

        return Optional.of(new GenerationStub(blockpos, builder -> generatePieces(builder, context)));
    }

    private void generatePieces(StructurePiecesBuilder builder, GenerationContext context) {
        // Center is always (0, 100, 0)
        int x = 0;
        int z = 0;
        int y = 100;

        // Define bounds centered at (0,0)
        // Radius is up to 120, so use 150 for safety
        BoundingBox box = new BoundingBox(x - 150, y, z - 150, x + 150, y + 10, z + 150);
        
        builder.addPiece(new EndRingPiece(box, x, y, z));
    }

    @Override
    public StructureType<?> type() {
        return ModStructures.END_RING.get();
    }

    @Override
    public GenerationStep.Decoration step() {
        return GenerationStep.Decoration.SURFACE_STRUCTURES;
    }
}
