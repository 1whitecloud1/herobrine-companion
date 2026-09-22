package com.whitecloud233.modid.herobrine_companion.entity.logic.quest.impl;

import com.whitecloud233.modid.herobrine_companion.entity.GhostSteveEntity;
import com.whitecloud233.modid.herobrine_companion.entity.logic.quest.HeroQuest;
import com.whitecloud233.modid.herobrine_companion.entity.logic.quest.HeroQuestManager;
import com.whitecloud233.modid.herobrine_companion.entity.logic.quest.QuestProgress;
import com.whitecloud233.modid.herobrine_companion.init.ModEntities;
import com.whitecloud233.modid.herobrine_companion.init.ModItems;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.UUID;

/**
 * 委托 7「镜中倒影」：复制玩家皮肤与姿态的幽灵出现在夜色中。
 * 潜行靠近（不可让它视线直达），用空手触碰驱散即完成；
 * 被它“看见”（未潜行且视线内）或天亮都会失败。
 * 奖励记忆碎片 x1 + 故障碎片 x2 + 信任 +18。
 */
public class MirrorQuest implements HeroQuest {

    private static final double SEE_RANGE_SQR = 12.0 * 12.0;
    private static final double COMPLETE_RANGE_SQR = 3.0 * 3.0;

    @Override
    public void onStart(ServerPlayer player, QuestProgress progress) {
        player.sendSystemMessage(Component.translatable("message.herobrine_companion.quest_start_7"));
        if (player.level() instanceof ServerLevel serverLevel) {
            GhostSteveEntity mirror = new GhostSteveEntity(ModEntities.GHOST_STEVE.get(), serverLevel);

            double angle = player.level().random.nextDouble() * 2 * Math.PI;
            double distance = 16 + player.level().random.nextDouble() * 8;
            int x = (int) (player.getX() + Math.cos(angle) * distance);
            int z = (int) (player.getZ() + Math.sin(angle) * distance);
            int y = serverLevel.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);

            mirror.moveTo(x + 0.5, y, z + 0.5, 0, 0);
            mirror.setInvulnerable(true);
            mirror.setNoAi(true); // 僵立不动，只在学你
            mirror.setPersistenceRequired();
            mirror.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.POPPY));
            mirror.setMirrorSkinUuid(player.getUUID());
            mirror.getTags().add("herobrine_quest_mirror");
            mirror.getTags().add("quest_target_for:" + player.getUUID());

            serverLevel.addFreshEntity(mirror);
            progress.setTargetUuid(mirror.getUUID());
        }
    }

    @Override
    public void onTick(ServerPlayer player, QuestProgress progress) {
        GhostSteveEntity mirror = findMirror(player, progress);
        if (mirror == null || !mirror.isAlive()) {
            HeroQuestManager.failQuest(player, "message.herobrine_companion.quest_target_gone");
            return;
        }
        // 天亮时它随晨雾消散
        if (player.level().isDay()) {
            HeroQuestManager.failQuest(player, "message.herobrine_companion.quest_mirror_dawn");
            return;
        }

        double distanceSqr = player.distanceToSqr(mirror);
        if (!player.isShiftKeyDown() && distanceSqr < SEE_RANGE_SQR && mirror.hasLineOfSight(player)) {
            HeroQuestManager.failQuest(player, "message.herobrine_companion.quest_mirror_seen");
            return;
        }
        // 接近时提示潜行
        if (distanceSqr < 8.0 * 8.0 && player.tickCount % 200 == 0) {
            player.displayClientMessage(Component.translatable("message.herobrine_companion.quest_mirror_hint"), true);
        }
    }

    @Override
    public boolean onEntityInteract(ServerPlayer player, Entity target, ItemStack stack, QuestProgress progress) {
        UUID targetUuid = progress.targetUuid();
        if (targetUuid == null) return false;
        if (!(target instanceof GhostSteveEntity mirror) || !mirror.getUUID().equals(targetUuid)) return false;
        if (!player.isShiftKeyDown() || player.distanceToSqr(mirror) >= COMPLETE_RANGE_SQR) return false;

        // 驱散：声音 + 烟雾
        player.level().playSound(null, mirror.getX(), mirror.getY(), mirror.getZ(), SoundEvents.ENDERMAN_TELEPORT, mirror.getSoundSource(), 1.0F, 0.6F);
        if (player.level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.SMOKE, mirror.getX(), mirror.getY() + 1, mirror.getZ(), 24, 0.2, 0.4, 0.2, 0.02);
        }
        mirror.discard();
        HeroQuestManager.completeQuest(player);
        return true;
    }

    @Override
    public void onCancel(ServerPlayer player, QuestProgress progress) {
        GhostSteveEntity mirror = findMirror(player, progress);
        if (mirror != null) {
            mirror.discard();
        }
    }

    @Override
    public void onComplete(ServerPlayer player, QuestProgress progress) {
        player.sendSystemMessage(Component.translatable("message.herobrine_companion.quest_complete_7"));
        HeroQuestManager.giveItem(player, new ItemStack(ModItems.MEMORY_SHARD.get(), 1));
        HeroQuestManager.giveItem(player, new ItemStack(ModItems.GLITCH_FRAGMENT.get(), 2));
        HeroQuestManager.addTrust(player, 18);
    }

    private static GhostSteveEntity findMirror(ServerPlayer player, QuestProgress progress) {
        UUID targetUuid = progress.targetUuid();
        if (targetUuid == null || !(player.level() instanceof ServerLevel serverLevel)) return null;
        Entity target = serverLevel.getEntity(targetUuid);
        return target instanceof GhostSteveEntity mirror ? mirror : null;
    }
}