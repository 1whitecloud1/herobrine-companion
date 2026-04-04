package com.whitecloud233.modid.herobrine_companion.event;

import com.whitecloud233.modid.herobrine_companion.HerobrineCompanion;
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

                event.getPlayer().sendSystemMessage(Component.literal("§e[Debug] 收到召唤指令，您的 UUID: " + playerUUID));

                HeroEntity existingHero = HeroSummonItem.findHeroInAnyDimension(serverLevel.getServer(), playerUUID);

                event.getPlayer().sendSystemMessage(Component.literal("§e[Debug] 查找已存在 Hero: " + (existingHero != null ? "是 (" + existingHero.getUUID() + ")" : "否")));
                event.getPlayer().sendSystemMessage(Component.literal("§e[Debug] 是否已通过指令召唤过: " + worldData.hasSpawnedFromChat(playerUUID)));

                if (existingHero == null && !worldData.hasSpawnedFromChat(playerUUID)) {
                    HeroEntity hero = ModEvents.HERO.get().create(serverLevel);
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

                        event.getPlayer().sendSystemMessage(Component.literal("§e[Debug] 准备加入世界，新 Hero UUID: " + newHeroUUID));

                        // 4. 正式加入世界
                        if (serverLevel.addFreshEntity(hero)) {
                            worldData.setSpawnedFromChat(playerUUID, true);
                            event.getPlayer().sendSystemMessage(Component.literal("§a[System] 成功召唤专属 Hero！实体已锁定在安全坐标。"));
                        } else {
                            event.getPlayer().sendSystemMessage(Component.literal("§c[Debug] 实体加入世界失败 (addFreshEntity = false)。请检查服务端日志是否存在UUID冲突。新UUID: " + newHeroUUID));
                        }
                    } else {
                        event.getPlayer().sendSystemMessage(Component.literal("§c[Debug] 创建实体失败 (ModEvents.HERO.get().create = null)"));
                    }
                } else {
                    event.getPlayer().sendSystemMessage(Component.literal("§c[System] 你已经召唤过 Hero 了，请使用源流（SourceFlowItem）或者庇护所寻找。"));
                }
            }
        }
    }
}