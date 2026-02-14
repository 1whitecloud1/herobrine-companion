package com.whitecloud233.modid.herobrine_companion.event;

import com.whitecloud233.modid.herobrine_companion.HerobrineCompanion;
import com.whitecloud233.modid.herobrine_companion.entity.HeroEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.ServerChatEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = HerobrineCompanion.MODID)
public class ChatEvents {

    @SubscribeEvent
    public static void onServerChat(ServerChatEvent event) {
        String message = event.getMessage().getString();
        if ("hb".equals(message)) {
            if (event.getPlayer().level() instanceof ServerLevel serverLevel) {
                HeroEntity hero = ModEvents.HERO.get().create(serverLevel);
                if (hero != null) {
                    Vec3 look = event.getPlayer().getLookAngle();
                    Vec3 playerPos = event.getPlayer().position();
                    double x = playerPos.x + look.x * 2.0D;
                    double z = playerPos.z + look.z * 2.0D;
                    double y = playerPos.y;

                    hero.moveTo(x, y, z, event.getPlayer().getYRot() + 180.0F, 0.0F);
                    hero.finalizeSpawn(serverLevel, serverLevel.getCurrentDifficultyAt(hero.blockPosition()), MobSpawnType.COMMAND, null, null);
                    serverLevel.addFreshEntity(hero);
                }
            }
        }
    }
}
