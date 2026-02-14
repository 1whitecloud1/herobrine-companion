package com.whitecloud233.modid.herobrine_companion.world.dimension;

import com.whitecloud233.modid.herobrine_companion.HerobrineCompanion;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.BootstapContext;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.valueproviders.ConstantInt;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.FlatLevelSource;
import net.minecraft.world.level.levelgen.flat.FlatLevelGeneratorSettings;

import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;

public class ModDimensions {
    public static final ResourceKey<DimensionType> END_RING_DIM_TYPE = ResourceKey.create(Registries.DIMENSION_TYPE, new ResourceLocation(HerobrineCompanion.MODID, "end_ring_type"));
    public static final ResourceKey<LevelStem> END_RING_STEM = ResourceKey.create(Registries.LEVEL_STEM, new ResourceLocation(HerobrineCompanion.MODID, "end_ring_dimension"));

    public static void bootstrapType(BootstapContext<DimensionType> context) {
        context.register(END_RING_DIM_TYPE, new DimensionType(
                OptionalLong.of(6000), 
                false, 
                false, 
                false, 
                false, 
                1.0, 
                true, 
                false, 
                0, 
                1024, 
                1024, 
                BlockTags.INFINIBURN_OVERWORLD, 
                new ResourceLocation("minecraft:the_void"), 
                0.0f, 
                new DimensionType.MonsterSettings(false, false, ConstantInt.of(0), 0)
        ));
    }

    public static void bootstrapStem(BootstapContext<LevelStem> context) {
        HolderGetter<Biome> biomeGetter = context.lookup(Registries.BIOME);

        // 使用 FlatLevelSource (超平坦生成器) 且不设置任何层 (List.of())
        // 这将生成一个完全空的虚空世界，没有任何地形，只有结构。
        // 生物群系固定为 THE_VOID。
        context.register(END_RING_STEM, new LevelStem(
                context.lookup(Registries.DIMENSION_TYPE).getOrThrow(END_RING_DIM_TYPE),
                new FlatLevelSource(
                        new FlatLevelGeneratorSettings(
                                Optional.empty(), // 不覆盖结构设置，使用生物群系的默认结构 (即我们注册的 End Ring)
                                biomeGetter.getOrThrow(Biomes.THE_VOID),
                                List.of() // 空列表 = 无方块层 = 纯虚空
                        )
                )
        ));
    }
}
