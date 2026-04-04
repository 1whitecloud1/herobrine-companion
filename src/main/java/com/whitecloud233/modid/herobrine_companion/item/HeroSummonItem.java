package com.whitecloud233.modid.herobrine_companion.item;

import com.whitecloud233.modid.herobrine_companion.entity.HeroEntity;
import com.whitecloud233.modid.herobrine_companion.entity.logic.data.HeroDataHandler;
import com.whitecloud233.modid.herobrine_companion.entity.logic.data.HeroLogic;
import com.whitecloud233.modid.herobrine_companion.entity.logic.data.HeroStateManager;
import com.whitecloud233.modid.herobrine_companion.event.ModEvents;
import com.whitecloud233.modid.herobrine_companion.world.inventory.HeroContractMenu;
import com.whitecloud233.modid.herobrine_companion.world.structure.ModStructures;
import com.whitecloud233.modid.herobrine_companion.util.EndRingContext;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Collections;
import java.util.Random;
import java.util.UUID;

public class HeroSummonItem extends Item {

    private static final Random random = new Random();
    private static final String[] MOCKERY_MESSAGES = {
            "message.herobrine_companion.mockery_1",
            "message.herobrine_companion.mockery_2",
            "message.herobrine_companion.mockery_3",
            "message.herobrine_companion.mockery_4",
            "message.herobrine_companion.mockery_5"
    };

    private static final long COOLDOWN_TICKS = 100;

    private static final TagKey<Block> FORGE_CHAIRS = BlockTags.create(new ResourceLocation("forge", "chairs"));
    private static final TagKey<Block> C_CHAIRS = BlockTags.create(new ResourceLocation("c", "chairs"));

    public HeroSummonItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity interactionTarget, InteractionHand hand) {
        if (interactionTarget instanceof HeroEntity) {
            Level level = player.level();

            if (level.dimension() == ModStructures.END_RING_DIMENSION_KEY) {
                return InteractionResult.PASS;
            }

            if (isBound(stack)) {
                if (!level.isClientSide) {
                    player.sendSystemMessage(Component.translatable("message.herobrine_companion.shelter_bound", getOwnerName(stack)));
                }
                return InteractionResult.PASS;
            }

            if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
                MenuProvider containerProvider = new SimpleMenuProvider((windowId, playerInventory, p) -> new HeroContractMenu(windowId, playerInventory), Component.translatable("gui.herobrine_companion.hero_contract"));
                serverPlayer.openMenu(containerProvider);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        return super.interactLivingEntity(stack, player, interactionTarget, hand);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        ItemStack stack = context.getItemInHand();
        Level level = context.getLevel();
        Player player = context.getPlayer();
        BlockPos clickedPos = context.getClickedPos();

        if (isBound(stack)) {
            if (!level.isClientSide && level instanceof ServerLevel serverLevel) {
                long currentTime = level.getGameTime();
                long lastUseTime = getLastUseTime(stack);

                if (currentTime < lastUseTime + COOLDOWN_TICKS) {
                    if (player != null) {
                        String mockery = MOCKERY_MESSAGES[random.nextInt(MOCKERY_MESSAGES.length)];
                        player.sendSystemMessage(Component.translatable(mockery));
                    }
                    return InteractionResult.FAIL;
                }

                UUID ownerUUID = getOwnerUUID(stack);
                if (ownerUUID == null && player != null) {
                    ownerUUID = player.getUUID();
                }
                
                HeroEntity existingHero = ownerUUID != null ? findHeroInAnyDimension(serverLevel.getServer(), ownerUUID) : null;
                int actionType = getInteractionType(level, clickedPos);

                if (actionType > 0 && existingHero != null) {
                    if (existingHero.level().dimension() == level.dimension()) {
                        HeroLogic.handlePlayerInvitation(existingHero, player, clickedPos, actionType);
                        setLastUseTime(stack, currentTime);
                        return InteractionResult.SUCCESS;
                    }
                }

                Vec3 targetPos = context.getClickLocation().add(0, 1, 0);

                // ============== [重构] 调用公共召唤逻辑 ==============
                boolean success = performSummonOrTeleport(serverLevel, player, stack, targetPos);
                if (success) {
                    setLastUseTime(stack, currentTime);
                }
                // ===============================================
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        } else {
            if (!level.isClientSide && player != null) {
                player.sendSystemMessage(Component.translatable("message.herobrine_companion.shelter_empty"));
            }
            return InteractionResult.FAIL;
        }
    }

    // ============== [新增] 提取出来的公共核心召唤/传送逻辑 ==============
    public static boolean performSummonOrTeleport(ServerLevel serverLevel, Player player, ItemStack stack, Vec3 targetPos) {
        UUID ownerUUID = stack != null ? getOwnerUUID(stack) : null;
        if (ownerUUID == null && player != null) ownerUUID = player.getUUID();
        
        HeroEntity existingHero = ownerUUID != null ? findHeroInAnyDimension(serverLevel.getServer(), ownerUUID) : null;
        long currentTime = serverLevel.getGameTime();

        if (existingHero != null) {
            if (existingHero.isPassenger()) {
                existingHero.stopRiding();
            }
            existingHero.setInvitedPos(null);
            existingHero.setInvitedAction(0);

            // 跨维度传送逻辑
            if (existingHero.level().dimension() != serverLevel.dimension()) {
                float oldYRot = existingHero.getYRot();
                float oldXRot = existingHero.getXRot();

                HeroStateManager.backupToGlobal(existingHero); // 确保所有装备和状态写入存档

                CompoundTag heroData = new CompoundTag();
                existingHero.saveWithoutId(heroData);
                existingHero.discard();

                HeroEntity newHero = ModEvents.HERO.get().create(serverLevel);
                if (newHero != null) {
                    if (heroData.contains("UUID")) heroData.remove("UUID");
                    if (heroData.contains("UUIDMost")) heroData.remove("UUIDMost");
                    if (heroData.contains("UUIDLeast")) heroData.remove("UUIDLeast");

                    newHero.load(heroData);
                    newHero.moveTo(targetPos.x, targetPos.y, targetPos.z, oldYRot, oldXRot);
                    newHero.setUUID(UUID.randomUUID());

                    if (player != null) {
                        HeroStateManager.restoreFromGlobal(newHero, player);
                    }

                    newHero.addTag(EndRingContext.TAG_RESPAWNED_SAFE);
                    HeroDataHandler.syncGlobalTrust(newHero);
                    serverLevel.addFreshEntity(newHero);
                    newHero.setLastSummonedTime(currentTime);

                    if (player != null) {
                        player.sendSystemMessage(Component.translatable("message.herobrine_companion.hero_teleported"));
                    }
                }
            } else {
                existingHero.teleportTo(serverLevel, targetPos.x, targetPos.y, targetPos.z, Collections.emptySet(), existingHero.getYRot(), existingHero.getXRot());
                existingHero.getNavigation().stop();
                existingHero.setTarget(null);
                existingHero.setLastSummonedTime(currentTime);

                if (player != null) {
                    player.sendSystemMessage(Component.translatable("message.herobrine_companion.hero_teleported"));
                }
            }

            serverLevel.playSound(null, BlockPos.containing(targetPos), SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1.0f, 1.0f);
            return true;
        } else {
            // 召唤新 Hero
            HeroEntity hero = ModEvents.HERO.get().create(serverLevel);
            if (hero != null) {
                hero.moveTo(targetPos);
                hero.finalizeSpawn(serverLevel, serverLevel.getCurrentDifficultyAt(hero.blockPosition()), MobSpawnType.TRIGGERED, null, null);

                if (ownerUUID != null) {
                    hero.setOwnerUUID(ownerUUID);
                }
                
                if (player != null) {
                    HeroStateManager.restoreFromGlobal(hero, player);
                    HeroDataHandler.syncGlobalTrust(hero);
                }

                serverLevel.addFreshEntity(hero);
                hero.setLastSummonedTime(currentTime);
                if (player != null) {
                    player.sendSystemMessage(Component.translatable("message.herobrine_companion.hero_summoned"));
                }
                return true;
            }
        }
        return false;
    }
    // =====================================================================
    
    // 兼容原版 AIService 等调用的签名
    public static boolean performSummonOrTeleport(ServerLevel serverLevel, Player player, Vec3 targetPos) {
        return performSummonOrTeleport(serverLevel, player, null, targetPos);
    }

    // [修改] 增加 ownerUUID 参数，以便在多人游戏中不同玩家能找到自己的 Hero
    public static HeroEntity findHeroInAnyDimension(net.minecraft.server.MinecraftServer server, UUID ownerUUID) {
        if (ownerUUID == null) return null;
        for (ServerLevel level : server.getAllLevels()) {
            var entities = level.getEntities(ModEvents.HERO.get(), entity -> ownerUUID.equals(entity.getOwnerUUID()));
            if (!entities.isEmpty()) {
                return entities.get(0);
            }
        }
        return null;
    }

    private int getInteractionType(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        Block block = state.getBlock();

        if (state.is(BlockTags.BEDS) || block instanceof BedBlock) return 2;
        if (state.is(BlockTags.STAIRS) || block instanceof StairBlock) return 2;
        if (state.is(BlockTags.SLABS) || block instanceof SlabBlock) return 2;

        if (state.is(FORGE_CHAIRS) || state.is(C_CHAIRS)) return 2;

        ResourceLocation key = ForgeRegistries.BLOCKS.getKey(block);
        if (key != null) {
            String path = key.getPath().toLowerCase();
            if (path.contains("chair") || path.contains("seat") || path.contains("sofa") || path.contains("stool") || path.contains("bench")) {
                return 2;
            }
        }

        if (state.is(BlockTags.DOORS) || block instanceof DoorBlock) return 3;
        if (state.is(BlockTags.TRAPDOORS) || block instanceof TrapDoorBlock) return 3;
        if (state.is(BlockTags.FENCE_GATES) || block instanceof FenceGateBlock) return 3;
        if (state.is(Blocks.CHEST) || state.is(Blocks.TRAPPED_CHEST) || state.is(Blocks.ENDER_CHEST) || state.is(Blocks.BARREL) || state.is(Blocks.SHULKER_BOX)) return 3;

        if (state.is(Blocks.SPAWNER)) return 1;
        if (state.is(Blocks.ENCHANTING_TABLE)) return 1;
        if (state.is(Blocks.BEACON)) return 1;
        if (state.is(Blocks.COMMAND_BLOCK) || state.is(Blocks.CHAIN_COMMAND_BLOCK) || state.is(Blocks.REPEATING_COMMAND_BLOCK)) return 1;
        if (state.is(BlockTags.DIAMOND_ORES) || state.is(BlockTags.EMERALD_ORES) || state.is(BlockTags.GOLD_ORES)) return 1;
        if (state.is(Blocks.ANCIENT_DEBRIS)) return 1;

        return 0;
    }

    private boolean isBound(ItemStack stack) {
        CompoundTag tag = stack.getOrCreateTag();
        return tag.contains("BoundHero");
    }

    private String getOwnerName(ItemStack stack) {
        CompoundTag tag = stack.getOrCreateTag();
        return tag.getString("OwnerName");
    }

    public static UUID getOwnerUUID(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag != null && tag.hasUUID("OwnerUUID")) {
            return tag.getUUID("OwnerUUID");
        }
        return null;
    }

    private long getLastUseTime(ItemStack stack) {
        CompoundTag tag = stack.getOrCreateTag();
        return tag.getLong("LastHeroSummonTime");
    }

    private void setLastUseTime(ItemStack stack, long time) {
        CompoundTag tag = stack.getOrCreateTag();
        tag.putLong("LastHeroSummonTime", time);
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return getClientCooldownProgress(stack) > 0.0F;
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        return Math.round(getClientCooldownProgress(stack) * 13.0F);
    }

    @Override
    public int getBarColor(ItemStack stack) {
        return 0xA9A9A9;
    }

    private float getClientCooldownProgress(ItemStack stack) {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            net.minecraft.client.multiplayer.ClientLevel level = net.minecraft.client.Minecraft.getInstance().level;
            if (level != null) {
                long currentTime = level.getGameTime();
                long lastUseTime = getLastUseTime(stack);
                long timePassed = currentTime - lastUseTime;
                if (timePassed < 0 || timePassed >= COOLDOWN_TICKS) return 0.0F;
                return (float)(COOLDOWN_TICKS - timePassed) / (float)COOLDOWN_TICKS;
            }
        }
        return 0.0F;
    }
}