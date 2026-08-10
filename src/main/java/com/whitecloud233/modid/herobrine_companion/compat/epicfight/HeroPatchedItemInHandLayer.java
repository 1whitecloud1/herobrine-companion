package com.whitecloud233.modid.herobrine_companion.compat.epicfight;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.whitecloud233.modid.herobrine_companion.entity.HeroEntity;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;
import yesman.epicfight.api.utils.math.OpenMatrix4f;
import yesman.epicfight.client.ClientEngine;
import yesman.epicfight.client.events.engine.RenderEngine;
import yesman.epicfight.client.renderer.patched.layer.PatchedItemInHandLayer;
import yesman.epicfight.world.capabilities.entitypatch.LivingEntityPatch;

public class HeroPatchedItemInHandLayer<E extends LivingEntity, T extends LivingEntityPatch<E>, M extends EntityModel<E>> extends PatchedItemInHandLayer<E, T, M> {
    @Override
    protected void renderLayer(T entitypatch, E entityliving, RenderLayer<E, M> vanillaLayer, PoseStack postStack, MultiBufferSource buffer, int packedLight, OpenMatrix4f[] poses, float bob, float yRot, float xRot, float partialTicks) {
        RenderEngine renderEngine = ClientEngine.getInstance().renderEngine;
        ItemStack mainHandStack = entitypatch.getOriginal().getMainHandItem();
        if (mainHandStack.getItem() != Items.AIR) {
            renderItem(renderEngine, mainHandStack, entitypatch, InteractionHand.MAIN_HAND, poses, buffer, postStack, packedLight, partialTicks);
        }

        ItemStack offHandStack = entitypatch.getOriginal().getOffhandItem();
        if (entitypatch.isOffhandItemValid()) {
            renderItem(renderEngine, offHandStack, entitypatch, InteractionHand.OFF_HAND, poses, buffer, postStack, packedLight, partialTicks);
        }
    }

    private void renderItem(RenderEngine renderEngine, ItemStack stack, T entitypatch, InteractionHand hand, OpenMatrix4f[] poses, MultiBufferSource buffer, PoseStack poseStack, int packedLight, float partialTicks) {
        poseStack.pushPose();
        try {
            // 诊断（节流）：记录该物品用的渲染器类与 getArmature 结果，定位爪/轮模型分离。
            // Avalon 物品应命中 com.merlin204.avalon...RenderAnimationItem（走 avalon$renderLayer），
            // 若是默认 RenderItemBase 则武器以普通物品渲染、pose 对不上 → 分离。
            ResourceLocation itemId = ForgeRegistries.ITEMS.getKey(stack.getItem());
            if (itemId != null && "efn".equals(itemId.getNamespace())) {
                HeroEntity hero = entitypatch.getOriginal() instanceof HeroEntity h ? h : null;
                HeroEpicFightDebugLog.repeatedEvent(hero, "weaponRender", itemId.toString(),
                        "renderer=" + renderEngine.getItemRenderer(stack).getClass().getName()
                                + ",armatureJoints=" + (entitypatch.getArmature() != null ? entitypatch.getArmature().getJointNumber() : -1)
                                + ",hand=" + hand);
            }

            applyHeroNightfallWeaponOffset(stack, hand, poseStack);
            renderEngine.getItemRenderer(stack).renderItemInHand(stack, entitypatch, hand, poses, buffer, poseStack, packedLight, partialTicks);
        } catch (RuntimeException exception) {
            // 持有物渲染异常（例如某些模组物品/Geo 物品在怪物持有者身上缺上下文）不能拖垮渲染线程；
            // 跳过该帧的持有物渲染即可，避免武器切换时卡死
        } finally {
            poseStack.popPose();
        }
    }

    private void applyHeroNightfallWeaponOffset(ItemStack stack, InteractionHand hand, PoseStack poseStack) {
        ResourceLocation itemId = ForgeRegistries.ITEMS.getKey(stack.getItem());
        if (itemId == null || !"efn".equals(itemId.getNamespace()) || hand != InteractionHand.MAIN_HAND) {
            return;
        }

        String path = itemId.getPath();
        if ("thornwheel".equals(path)) {
            poseStack.translate(0.0D, -0.18D, 0.02D);
            poseStack.mulPose(Axis.XP.rotationDegrees(-7.5F));
            poseStack.mulPose(Axis.ZP.rotationDegrees(4.0F));
            poseStack.scale(0.92F, 0.92F, 0.92F);
        } else if ("nf_claw".equals(path)) {
            poseStack.translate(0.0D, -0.20D, 0.02D);
        }
    }
}

