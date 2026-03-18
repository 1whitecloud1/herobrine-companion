package com.whitecloud233.modid.herobrine_companion.world.structure;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePiecesBuilder;

import java.util.Optional;

public class UnstableZoneStructure extends Structure {

    // 1. 极简的 CODEC：只需要基本的 StructureSettings
    public static final Codec<UnstableZoneStructure> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    Structure.settingsCodec(instance)
            ).apply(instance, UnstableZoneStructure::new)
    );

    // 2. 构造函数：只需要接收 config
    public UnstableZoneStructure(Structure.StructureSettings config) {
        super(config);
    }

    @Override
    public Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
        BlockPos blockPos = context.chunkPos().getWorldPosition();

        int landHeight = context.chunkGenerator().getFirstOccupiedHeight(
                blockPos.getX(),
                blockPos.getZ(),
                Heightmap.Types.WORLD_SURFACE_WG,
                context.heightAccessor(),
                context.randomState()
        );

        int seaLevel = context.chunkGenerator().getSeaLevel();

        if (landHeight <= seaLevel) {
            return Optional.empty();
        }

        BlockPos generationPos = new BlockPos(blockPos.getX(), landHeight, blockPos.getZ());

        // 3. 将 generationPos 传递给 generatePieces
        return Optional.of(new GenerationStub(generationPos, (builder) -> generatePieces(builder, generationPos)));
    }

    // 4. 生成 Piece 的方法
    private void generatePieces(StructurePiecesBuilder builder, BlockPos pos) {
        int pieceSize = 32;
        BlockPos startPos = pos.offset(-pieceSize / 2, 0, -pieceSize / 2);
        builder.addPiece(new UnstableZonePiece(startPos, pieceSize, pieceSize));
    }

    @Override
    public StructureType<?> type() {
        return ModStructures.UNSTABLE_ZONE.get();
    }
}