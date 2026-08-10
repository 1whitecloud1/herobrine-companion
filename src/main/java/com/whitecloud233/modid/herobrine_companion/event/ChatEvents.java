package com.whitecloud233.modid.herobrine_companion.event;

import com.whitecloud233.modid.herobrine_companion.HerobrineCompanion;
import com.whitecloud233.modid.herobrine_companion.init.ModEntities;
import com.whitecloud233.modid.herobrine_companion.entity.HeroEntity;
import com.whitecloud233.modid.herobrine_companion.entity.logic.data.HeroWorldData;
import com.whitecloud233.modid.herobrine_companion.item.HeroSummonItem;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraftforge.event.ServerChatEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.UUID;

@Mod.EventBusSubscriber(modid = HerobrineCompanion.MODID)
public class ChatEvents {

    @SubscribeEvent
    public static void onServerChat(ServerChatEvent event) {
        String message = event.getMessage().getString().trim();
        String rawMessage = event.getRawText() != null ? event.getRawText().trim() : "";

        if ("hb".equals(message) || "hb".equals(rawMessage)) {
            if (event.getPlayer().level() instanceof ServerLevel serverLevel) {
                HeroWorldData worldData = HeroWorldData.get(serverLevel);
                UUID playerUUID = event.getPlayer().getUUID();

                event.getPlayer().sendSystemMessage(Component.translatable("message.herobrine_companion.chat_summon.debug.uuid", playerUUID));

                HeroEntity existingHero = HeroSummonItem.findHeroInAnyDimension(serverLevel.getServer(), playerUUID);

                event.getPlayer().sendSystemMessage(Component.translatable("message.herobrine_companion.chat_summon.debug.existing",
                        existingHero != null
                                ? Component.translatable("message.herobrine_companion.debug.found", existingHero.getUUID().toString())
                                : Component.translatable("message.herobrine_companion.debug.not_found")));
                event.getPlayer().sendSystemMessage(Component.translatable("message.herobrine_companion.chat_summon.debug.spawned",
                        worldData.hasSpawnedFromChat(playerUUID)
                                ? Component.translatable("message.herobrine_companion.debug.yes")
                                : Component.translatable("message.herobrine_companion.debug.no")));

                if (existingHero == null && !worldData.hasSpawnedFromChat(playerUUID)) {
                    HeroEntity hero = ModEntities.HERO.get().create(serverLevel);
                    if (hero != null) {
                        // 【最终修复】在加入世界前，手动设置一个全新的、绝对不会冲突的UUID
                        UUID newHeroUUID = UUID.randomUUID();
                        hero.setUUID(newHeroUUID);
                        
                        // 1. 立即绑定主人
                        hero.setOwnerUUID(playerUUID);

                        // 2. 强制在玩家头顶 1.5 格生成！
                        hero.moveTo(event.getPlayer().getX(), event.getPlayer().getY() + 1.5D, event.getPlayer().getZ(), event.getPlayer().getYRot() + 180.0F, 0.0F);
                        hero.finalizeSpawn(serverLevel, serverLevel.getCurrentDifficultyAt(hero.blockPosition()), MobSpawnType.COMMAND, null, null);

                        // 3. 强制提前将它的 UUID 写入世界
                        worldData.setActiveHeroUUID(playerUUID, hero.getUUID());

                        event.getPlayer().sendSystemMessage(Component.translatable("message.herobrine_companion.chat_summon.debug.preparing", newHeroUUID));

                        // 4. 正式加入世界
                        if (serverLevel.addFreshEntity(hero)) {
                            worldData.setSpawnedFromChat(playerUUID, true);
                            event.getPlayer().sendSystemMessage(Component.translatable("message.herobrine_companion.chat_summon.success"));
                        } else {
                            event.getPlayer().sendSystemMessage(Component.translatable("message.herobrine_companion.chat_summon.debug.add_fresh_fail", newHeroUUID));
                        }
                    } else {
                        event.getPlayer().sendSystemMessage(Component.translatable("message.herobrine_companion.chat_summon.debug.create_fail"));
                    }
                } else {
                    event.getPlayer().sendSystemMessage(Component.translatable("message.herobrine_companion.chat_summon.already"));
                }
            }
        }
    }
}