package com.whitecloud233.herobrine_companion.world.structure;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePiecesBuilder;

import java.util.Optional;

public class UnstableZoneStructure extends Structure {

    public static final MapCodec<UnstableZoneStructure> CODEC = RecordCodecBuilder.mapCodec(instance ->
            instance.group(settingsCodec(instance)).apply(instance, UnstableZoneStructure::new));

    public UnstableZoneStructure(Structure.StructureSettings settings) {
        super(settings);
    }

    @Override
    protected Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
        BlockPos blockPos = context.chunkPos().getWorldPosition();
        
        // Get the surface height at the center of the chunk
        int landHeight = context.chunkGenerator().getFirstOccupiedHeight(
                blockPos.getX(), 
                blockPos.getZ(), 
                Heightmap.Types.WORLD_SURFACE_WG, 
                context.heightAccessor(), 
                context.randomState()
        );

        // Get the sea level from the chunk generator (compatible with mods that change sea level)
        int seaLevel = context.chunkGenerator().getSeaLevel();

        // Only spawn if the land height is strictly above sea level
        // This avoids oceans, rivers, and shoreline water
        if (landHeight <= seaLevel) {
            return Optional.empty();
        }

        return Optional.of(new GenerationStub(blockPos, (builder) -> generatePieces(builder, context)));
    }

    private void generatePieces(StructurePiecesBuilder builder, GenerationContext context) {
        BlockPos center = context.chunkPos().getWorldPosition();
        int size = 32; 
        BlockPos startPos = center.offset(-size / 2, 0, -size / 2);

        builder.addPiece(new UnstableZonePiece(startPos, size, size));
    }

    @Override
    public StructureType<?> type() {
        return ModStructures.UNSTABLE_ZONE.get();
    }
}
