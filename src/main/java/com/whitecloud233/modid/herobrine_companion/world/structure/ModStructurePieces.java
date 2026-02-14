package com.whitecloud233.modid.herobrine_companion.world.structure;

import com.whitecloud233.modid.herobrine_companion.HerobrineCompanion;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public class ModStructurePieces {
    
    public static final DeferredRegister<StructurePieceType> STRUCTURE_PIECES = 
        DeferredRegister.create(Registries.STRUCTURE_PIECE, HerobrineCompanion.MODID);

    public static final RegistryObject<StructurePieceType> END_RING_PIECE = 
        STRUCTURE_PIECES.register("end_ring_piece", () -> (StructurePieceType) EndRingPiece::new);

    public static final RegistryObject<StructurePieceType> UNSTABLE_ZONE_PIECE = 
        STRUCTURE_PIECES.register("unstable_zone_piece", () -> UnstableZonePiece::new);

    public static void register(IEventBus eventBus) {
        STRUCTURE_PIECES.register(eventBus);
    }
}
