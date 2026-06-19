package com.whitecloud233.herobrine_companion.entity.logic.event;

import com.whitecloud233.herobrine_companion.init.*;

import com.whitecloud233.herobrine_companion.HerobrineCompanion;
import com.whitecloud233.herobrine_companion.entity.HeroEntity;
import com.whitecloud233.herobrine_companion.entity.ai.learning.HeroDialogueHandler;
import com.whitecloud233.herobrine_companion.item.LoreFragmentItem;
import com.whitecloud233.herobrine_companion.network.PacketHandler;
import com.whitecloud233.herobrine_companion.network.TriggerEternalOathPacket;
import com.whitecloud233.herobrine_companion.world.structure.ModStructures;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.CanPlayerSleepEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

@EventBusSubscriber(modid = HerobrineCompanion.MODID)
public class StoryAndLoreHandler {

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !player.isAlive()) return;

        if (player.level().dimension() == Level.OVERWORLD) {
            checkUnstableZone(player);
            checkStillnessForFragment6(player);
        }
    }

    @SubscribeEvent
    public static void onPlayerSleep(CanPlayerSleepEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            ServerLevel level = (ServerLevel) player.level();

            // 伴侣睡眠对话 (已优化)
            for (HeroEntity hero : com.whitecloud233.herobrine_companion.entity.ai.learning.HeroBrain.ACTIVE_HEROES) {
                if (hero.level() == level && hero.isAlive() && hero.isCompanionMode() && hero.getOwnerUUID() != null && hero.getOwnerUUID().equals(player.getUUID())) {
                    HeroDialogueHandler.onSleep(hero, player);
                    break;
                }
            }

            // 碎片9逻辑
            CompoundTag data = player.getPersistentData();
            if (!data.getBoolean("HasReceivedFragment9")) {
                if (player.getRandom().nextFloat() < 0.2f) {
                    ItemStack fragment = new ItemStack(ModItems.LORE_FRAGMENT.get());
                    CompoundTag tag = new CompoundTag();
                    tag.putString(LoreFragmentItem.LORE_ID_KEY, "fragment_9");
                    // 1.21.1 更改：使用 Data Component
                    fragment.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));

                    if (!player.getInventory().add(fragment)) {
                        player.drop(fragment, false);
                        player.displayClientMessage(Component.translatable("message.herobrine_companion.inventory_full_lore_dropped").withStyle(ChatFormatting.YELLOW), true);
                    }

                    data.putBoolean("HasReceivedFragment9", true);
                    PacketHandler.sendToPlayer(new TriggerEternalOathPacket(), player);
                }
            }
        }
    }

    private static void checkUnstableZone(ServerPlayer player) {
        if (player.level() instanceof ServerLevel serverLevel) {
            ResourceKey<Structure> key = ResourceKey.create(Registries.STRUCTURE, ModStructures.UNSTABLE_ZONE.getId());
            Structure structure = serverLevel.registryAccess().registryOrThrow(Registries.STRUCTURE).get(key);

            if (structure != null && serverLevel.structureManager().getStructureWithPieceAt(player.blockPosition(), structure).isValid()) {
                CompoundTag data = player.getPersistentData();
                if (!data.getBoolean("HasSeenUnstableZoneIntro")) {
                    player.displayClientMessage(Component.translatable("message.herobrine_companion.unstable_zone_intro"), false);
                    data.putBoolean("HasSeenUnstableZoneIntro", true);
                    summonHeroNearPlayer(serverLevel, player);
                }
            }
        }
    }

    private static void checkStillnessForFragment6(ServerPlayer player) {
        CompoundTag data = player.getPersistentData();
        if (data.getBoolean("HasReceivedFragment6")) return;

        long time = player.level().getDayTime() % 24000;
        if (time < 14000 || time > 22000) {
            data.putInt("StillTicks", 0);
            return;
        }

        double dx = player.getX() - data.getDouble("LastX");
        double dy = player.getY() - data.getDouble("LastY");
        double dz = player.getZ() - data.getDouble("LastZ");
        double drot = Math.abs(player.getYRot() - data.getFloat("LastYRot")) + Math.abs(player.getXRot() - data.getFloat("LastXRot"));

        if (dx * dx + dy * dy + dz * dz < 0.0001 && drot < 0.1) {
            int stillTicks = data.getInt("StillTicks") + 1;
            data.putInt("StillTicks", stillTicks);

            if (stillTicks >= 400) {
                ItemStack fragment = new ItemStack(ModItems.LORE_FRAGMENT.get());
                CompoundTag tag = new CompoundTag();
                tag.putString(LoreFragmentItem.LORE_ID_KEY, "fragment_6");
                // 1.21.1 更改：使用 Data Component
                fragment.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));

                if (player.getInventory().add(fragment)) {
                    player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PHANTOM_AMBIENT, SoundSource.AMBIENT, 1.0f, 0.5f);
                    player.sendSystemMessage(Component.translatable("message.herobrine_companion.fragment_6_received").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
                    data.putBoolean("HasReceivedFragment6", true);
                    data.remove("StillTicks");
                } else {
                    player.displayClientMessage(Component.translatable("message.herobrine_companion.inventory_full_lore").withStyle(ChatFormatting.RED), true);
                    data.putInt("StillTicks", 500);
                }
            }
        } else {
            data.putInt("StillTicks", 0);
        }

        data.putDouble("LastX", player.getX());
        data.putDouble("LastY", player.getY());
        data.putDouble("LastZ", player.getZ());
        data.putFloat("LastYRot", player.getYRot());
        data.putFloat("LastXRot", player.getXRot());
    }

    private static void summonHeroNearPlayer(ServerLevel level, ServerPlayer player) {
        HeroEntity existingHero = null;
        // 👇 优化：不再遍历 level.getAllEntities()
        for (HeroEntity hero : com.whitecloud233.herobrine_companion.entity.ai.learning.HeroBrain.ACTIVE_HEROES) {
            if (hero.level() == level && hero.isAlive() && player.getUUID().equals(hero.getOwnerUUID())) {
                existingHero = hero;
                break;
            }
        }

        Vec3 targetPos = player.position().add(player.getLookAngle().scale(-3.0));
        BlockPos pos = new BlockPos((int)targetPos.x, (int)targetPos.y, (int)targetPos.z);
        if (!level.isEmptyBlock(pos) || !level.isEmptyBlock(pos.above())) {
            targetPos = player.position();
        }

        if (existingHero != null) {
            existingHero.teleportTo(targetPos.x, targetPos.y, targetPos.z);
            existingHero.getNavigation().stop();
            existingHero.setTarget(null);
        } else {
            HeroEntity hero = ModEntities.HERO.get().create(level);
            if (hero != null) {
                hero.moveTo(targetPos);
                // 1.21.1 更改：移除了最后废弃的 NBT tag 参数
                hero.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), MobSpawnType.EVENT, null);
                hero.setOwnerUUID(player.getUUID());
                level.addFreshEntity(hero);
            }
        }
    }
}