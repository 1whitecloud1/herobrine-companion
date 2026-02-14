package com.whitecloud233.modid.herobrine_companion.world.structure;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.heightproviders.HeightProvider;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePiecesBuilder;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;

import java.util.Optional;

public class UnstableZoneStructure extends Structure {

    public static final Codec<UnstableZoneStructure> CODEC = RecordCodecBuilder.create(instance -> 
        instance.group(
            Structure.settingsCodec(instance),
            StructureTemplatePool.CODEC.fieldOf("start_pool").forGetter(structure -> structure.startPool),
            ResourceLocation.CODEC.optionalFieldOf("start_jigsaw_name").forGetter(structure -> structure.startJigsawName),
            Codec.intRange(1, 7).fieldOf("size").forGetter(structure -> structure.size),
            HeightProvider.CODEC.fieldOf("start_height").forGetter(structure -> structure.startHeight),
            Heightmap.Types.CODEC.optionalFieldOf("project_start_to_heightmap").forGetter(structure -> structure.projectStartToHeightmap),
            Codec.intRange(1, 128).fieldOf("max_distance_from_center").forGetter(structure -> structure.maxDistanceFromCenter)
        ).apply(instance, UnstableZoneStructure::new)
    );

    private final Holder<StructureTemplatePool> startPool;
    private final Optional<ResourceLocation> startJigsawName;
    private final int size;
    private final HeightProvider startHeight;
    private final Optional<Heightmap.Types> projectStartToHeightmap;
    private final int maxDistanceFromCenter;

    public UnstableZoneStructure(Structure.StructureSettings config, Holder<StructureTemplatePool> startPool, Optional<ResourceLocation> startJigsawName, int size, HeightProvider startHeight, Optional<Heightmap.Types> projectStartToHeightmap, int maxDistanceFromCenter) {
        super(config);
        this.startPool = startPool;
        this.startJigsawName = startJigsawName;
        this.size = size;
        this.startHeight = startHeight;
        this.projectStartToHeightmap = projectStartToHeightmap;
        this.maxDistanceFromCenter = maxDistanceFromCenter;
    }

    @Override
    public Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
        // 1. 获取区块中心坐标
        BlockPos blockPos = context.chunkPos().getWorldPosition();
        
        // 2. 获取地表高度 (WORLD_SURFACE_WG: 地表生成高度，包含流体)
        int landHeight = context.chunkGenerator().getFirstOccupiedHeight(
                blockPos.getX(), 
                blockPos.getZ(), 
                Heightmap.Types.WORLD_SURFACE_WG, 
                context.heightAccessor(), 
                context.randomState()
        );

        // 3. 获取海平面高度
        int seaLevel = context.chunkGenerator().getSeaLevel();

        // 4. 检查是否在水下 (地表高度 <= 海平面)
        // 如果 landHeight <= seaLevel，说明该位置是海洋或河流，不生成
        if (landHeight <= seaLevel) {
            return Optional.empty();
        }

        // 5. 确定生成坐标
        // 我们使用 landHeight 作为 Y 坐标，确保生成在地表
        BlockPos generationPos = new BlockPos(blockPos.getX(), landHeight, blockPos.getZ());

        return Optional.of(new GenerationStub(generationPos, (builder) -> generatePieces(builder, context, generationPos)));
    }

    private void generatePieces(StructurePiecesBuilder builder, GenerationContext context, BlockPos pos) {
        int pieceSize = 32; 
        // 调整 startPos，使其以 pos 为中心 (或者根据 Piece 的逻辑调整)
        // 这里假设 Piece 的 bounding box 是从 startPos 开始向正方向延伸
        BlockPos startPos = pos.offset(-pieceSize / 2, 0, -pieceSize / 2);

        builder.addPiece(new UnstableZonePiece(startPos, pieceSize, pieceSize));
    }

    @Override
    public StructureType<?> type() {
        return ModStructures.UNSTABLE_ZONE.get();
    }
}
