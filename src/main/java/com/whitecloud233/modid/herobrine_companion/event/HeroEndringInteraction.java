package com.whitecloud233.modid.herobrine_companion.event;

import com.whitecloud233.modid.herobrine_companion.HerobrineCompanion;
import com.whitecloud233.modid.herobrine_companion.item.HeroSummonItem;
import com.whitecloud233.modid.herobrine_companion.entity.HeroEntity;
import com.whitecloud233.modid.herobrine_companion.util.BookUtils;
import com.whitecloud233.modid.herobrine_companion.world.structure.ModStructures;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = HerobrineCompanion.MODID)
public class HeroEndringInteraction {

    @SubscribeEvent
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (event.getItemStack().getItem() instanceof HeroSummonItem) {
            return;
        }

        if (event.getTarget() instanceof HeroEntity hero && !event.getLevel().isClientSide) {

            // 👇 [核心拦截] 如果正在打架，什么剧情都不准触发，直接放行给战斗管理器！
            if (hero.getEntityData().get(HeroEntity.IS_CHALLENGE_ACTIVE) ||
                    hero.getPersistentData().getBoolean("IsChallengeActive")) {
                return; // 挑战期间完全禁止剧情交互
            }

            // 下面是你原来判断维度的代码
            if (hero.level().dimension() != ModStructures.END_RING_DIMENSION_KEY) {
                return;
            }

            Player player = event.getEntity();
            CompoundTag data = player.getPersistentData();
            // ... 后面的剧情 stage 判断保持不变 ...
            int stage = data.getInt("WakeUpStage");
            
            if (stage == 0) data.putInt("WakeUpStage", 1);
            
            boolean handled = false;
            if (stage == 1) {
                hero.teleportTo(player.getX() + player.getLookAngle().x * 3, player.getY(), player.getZ() + player.getLookAngle().z * 3);
                player.sendSystemMessage(Component.translatable("message.herobrine_companion.hero_wake_up_1"));
                data.putInt("WakeUpStage", 2);
                handled = true;
            } else if (stage == 2) {
                player.sendSystemMessage(Component.translatable("message.herobrine_companion.hero_wake_up_2"));
                
                ItemStack book = BookUtils.createHerobrineLoreBook();
                player.getInventory().add(book);

                data.putInt("WakeUpStage", 3);
                handled = true;
            } else if (stage == 3) {
                player.sendSystemMessage(Component.translatable("message.herobrine_companion.hero_wake_up_3"));
                handled = true;
            }
            
            if (handled) {
                event.setCanceled(true);
                event.setCancellationResult(InteractionResult.SUCCESS);
            }
        }
    }
}
