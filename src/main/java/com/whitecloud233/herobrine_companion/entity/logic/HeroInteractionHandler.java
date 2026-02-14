package com.whitecloud233.herobrine_companion.entity.logic;

import com.whitecloud233.herobrine_companion.HerobrineCompanion;
import com.whitecloud233.herobrine_companion.client.ClientHooks;
import com.whitecloud233.herobrine_companion.entity.HeroEntity;
import com.whitecloud233.herobrine_companion.entity.ai.learning.SimpleNeuralNetwork;
import com.whitecloud233.herobrine_companion.item.HeroSummonItem;
import com.whitecloud233.herobrine_companion.world.structure.ModStructures;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;

public class HeroInteractionHandler {

    public static InteractionResult onInteract(HeroEntity hero, Player player, InteractionHand hand) {
        if (hand == InteractionHand.MAIN_HAND) {
            ItemStack itemInHand = player.getItemInHand(hand);
            if (itemInHand.getItem() instanceof HeroSummonItem) {
                return InteractionResult.PASS;
            }

            // [新增] 确保 Hero 绑定了主人
            if (!hero.level().isClientSide) {
                if (hero.getOwnerUUID() == null) {
                    hero.setOwnerUUID(player.getUUID());
                }
                
                // [新增] 授予 "meet_herobrine" 进度
                if (player instanceof ServerPlayer serverPlayer) {
                    // 注意：这里路径要和 ModAdvancementGenerator 中 save 的路径一致
                    ResourceLocation advancementId = ResourceLocation.fromNamespaceAndPath(HerobrineCompanion.MODID, "root");
                    AdvancementHolder advancement = serverPlayer.server.getAdvancements().get(advancementId);
                    if (advancement != null) {
                        AdvancementProgress progress = serverPlayer.getAdvancements().getOrStartProgress(advancement);
                        if (!progress.isDone()) {
                            for (String criterion : progress.getRemainingCriteria()) {
                                serverPlayer.getAdvancements().award(advancement, criterion);
                            }
                        }
                    }
                }
                
                // [新增] 审判者状态下拒绝交互
                if (hero.getHeroBrain().getState() == SimpleNeuralNetwork.MindState.JUDGE) {
                    player.sendSystemMessage(Component.translatable("message.herobrine_companion.judge_refuse"));
                    // [修改] 替换为更符合设定的音效：信标取消激活的声音，听起来像能量消散或拒绝访问
                    hero.playSound(net.minecraft.sounds.SoundEvents.BEACON_DEACTIVATE, 1.0f, 0.5f);
                    return InteractionResult.FAIL;
                }
            }

            if (hero.level().isClientSide && FMLEnvironment.dist == Dist.CLIENT) {
                if (hero.level().dimension() == ModStructures.END_RING_DIMENSION_KEY) {
                    return InteractionResult.PASS;
                }
                // [新增] 客户端也需要检查状态，避免打开 GUI
                // 现在我们可以通过同步数据获取状态了
                if (hero.getMindState() == SimpleNeuralNetwork.MindState.JUDGE) {
                    // 客户端直接拦截，不发送包，也不打开 GUI
                    // [修改] 客户端也播放相同的拒绝音效
                    player.playSound(net.minecraft.sounds.SoundEvents.BEACON_DEACTIVATE, 1.0f, 0.5f);
                    return InteractionResult.FAIL;
                }

                ClientHooks.openHeroScreen(hero.getId());
            }
            return InteractionResult.sidedSuccess(hero.level().isClientSide);
        }
        return InteractionResult.PASS;
    }
}