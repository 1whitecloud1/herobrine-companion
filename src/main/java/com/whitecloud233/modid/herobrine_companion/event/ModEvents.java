package com.whitecloud233.modid.herobrine_companion.event;

import com.whitecloud233.modid.herobrine_companion.HerobrineCompanion;
import com.whitecloud233.modid.herobrine_companion.destructiongod.entity.DestructionGodHerobrineEntity;
import com.whitecloud233.modid.herobrine_companion.entity.*;
import com.whitecloud233.modid.herobrine_companion.init.ModEntities;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Skeleton;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.event.entity.SpawnPlacementRegisterEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = HerobrineCompanion.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class ModEvents {

    @SubscribeEvent
    public static void entityAttributeEvent(EntityAttributeCreationEvent event) {
        event.put(ModEntities.HERO.get(), HeroEntity.createAttributes().build());
        event.put(ModEntities.DESTRUCTION_GOD_HEROBRINE.get(), DestructionGodHerobrineEntity.createAttributes().build());
        event.put(ModEntities.GHOST_CREEPER.get(), Creeper.createAttributes().build());
        event.put(ModEntities.GHOST_ZOMBIE.get(), Zombie.createAttributes().build());
        event.put(ModEntities.GHOST_SKELETON.get(), Skeleton.createAttributes().build());
        event.put(ModEntities.GLITCH_VILLAGER.get(), Villager.createAttributes().build());
        event.put(ModEntities.GHOST_STEVE.get(), GhostSteveEntity.createAttributes().build());
    }

    @SubscribeEvent
    public static void onSpawnPlacementRegister(SpawnPlacementRegisterEvent event) {
        // [修改] 使用 WORLD_SURFACE 而不是 MOTION_BLOCKING_NO_LEAVES，以确保在地面生成
        event.register(ModEntities.GHOST_STEVE.get(), SpawnPlacements.Type.ON_GROUND, Heightmap.Types.WORLD_SURFACE,
                GhostSteveEntity::checkGhostSteveSpawnRules, SpawnPlacementRegisterEvent.Operation.REPLACE);
    }
}
