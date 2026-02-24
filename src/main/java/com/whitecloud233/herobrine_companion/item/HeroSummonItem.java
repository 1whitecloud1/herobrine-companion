package com.whitecloud233.herobrine_companion.item;

import com.whitecloud233.herobrine_companion.entity.HeroEntity;
import com.whitecloud233.herobrine_companion.entity.logic.HeroDataHandler;
import com.whitecloud233.herobrine_companion.entity.logic.HeroLogic;
import com.whitecloud233.herobrine_companion.event.ModEvents;
import com.whitecloud233.herobrine_companion.util.EndRingContext;
import com.whitecloud233.herobrine_companion.world.inventory.HeroContractMenu;
import com.whitecloud233.herobrine_companion.world.structure.ModStructures;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
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
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;

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
    // 定义常见模组的椅子标签
    private static final TagKey<Block> FORGE_CHAIRS = BlockTags.create(ResourceLocation.fromNamespaceAndPath("forge", "chairs"));
    private static final TagKey<Block> C_CHAIRS = BlockTags.create(ResourceLocation.fromNamespaceAndPath("c", "chairs"));


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
                return InteractionResult.SUCCESS; // 修改为 SUCCESS 以防止后续交互
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
                    String mockery = MOCKERY_MESSAGES[random.nextInt(MOCKERY_MESSAGES.length)];
                    player.sendSystemMessage(Component.translatable(mockery));
                    return InteractionResult.FAIL; // 修改为 SUCCESS，阻止方块原版交互
                }

                HeroEntity existingHero = findHeroInAnyDimension(serverLevel.getServer());

                // [新增] 检查是否是特殊互动方块
                int actionType = getInteractionType(level, clickedPos);
                // 如果是特殊方块，且 Hero 存在，则触发邀请
                if (actionType > 0 && existingHero != null) {
                    // 只有当 Hero 在同一个维度时才能互动
                    if (existingHero.level().dimension() == level.dimension()) {
                        HeroLogic.handlePlayerInvitation(existingHero, player, clickedPos, actionType);
                        setLastUseTime(stack, currentTime); // 互动也算使用，触发冷却
                        return InteractionResult.SUCCESS;
                    }
                }

                // 否则执行原有的召唤/传送逻辑
                Vec3 targetPos = context.getClickLocation().add(0, 1, 0);

                if (existingHero != null) {
                    // [新增] 如果 Hero 正在骑乘（例如坐在椅子上），强制下车
                    if (existingHero.isPassenger()) {
                        existingHero.stopRiding();
                    }

                    // 清除之前的邀请状态
                    existingHero.setInvitedPos(null);
                    existingHero.setInvitedAction(0);

                    // [修复] 跨维度传送逻辑
                    if (existingHero.level().dimension() != serverLevel.dimension()) {
                        // 1. 保存旧实体数据
                        CompoundTag heroData = new CompoundTag();
                        existingHero.saveWithoutId(heroData);
                        HeroDataHandler.updateGlobalTrust(existingHero); // 确保信任度保存
                        existingHero.discard(); // 销毁旧实体

                        // 2. 在新维度创建新实体
                        HeroEntity newHero = ModEvents.HERO.get().create(serverLevel);
                        if (newHero != null) {
                            // 清洗 UUID
                            if (heroData.contains("UUID")) heroData.remove("UUID");
                            if (heroData.contains("UUIDMost")) heroData.remove("UUIDMost");
                            if (heroData.contains("UUIDLeast")) heroData.remove("UUIDLeast");

                            newHero.load(heroData);
                            newHero.moveTo(targetPos.x, targetPos.y, targetPos.z, existingHero.getYRot(), existingHero.getXRot());
                            newHero.setUUID(UUID.randomUUID());
                            
                            // 添加特权标签，防止被误杀
                            newHero.addTag(EndRingContext.TAG_RESPAWNED_SAFE);
                            
                            // 确保信任度同步
                            HeroDataHandler.syncGlobalTrust(newHero);

                            serverLevel.addFreshEntity(newHero);
                            
                            if (player != null) {
                                player.sendSystemMessage(Component.translatable("message.herobrine_companion.hero_teleported"));
                                setLastUseTime(stack, currentTime);
                            }
                        }
                    } else {
                        // 同维度传送
                        existingHero.teleportTo(serverLevel, targetPos.x, targetPos.y, targetPos.z, Collections.emptySet(), existingHero.getYRot(), existingHero.getXRot());
                        existingHero.getNavigation().stop();
                        existingHero.setTarget(null);
                        
                        if (player != null) {
                            player.sendSystemMessage(Component.translatable("message.herobrine_companion.hero_teleported"));
                            setLastUseTime(stack, currentTime);
                        }
                    }

                    level.playSound(null, context.getClickedPos(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1.0f, 1.0f);
                    
                } else {
                    // 召唤新 Hero
                    HeroEntity hero = ModEvents.HERO.get().create(serverLevel);
                    if (hero != null) {
                        hero.moveTo(targetPos);
                        hero.finalizeSpawn(serverLevel, serverLevel.getCurrentDifficultyAt(hero.blockPosition()), MobSpawnType.TRIGGERED, null);
                        
                        // [修复] 绑定主人并同步信任度
                        if (player != null) {
                            hero.setOwnerUUID(player.getUUID());
                            HeroDataHandler.restoreTrustFromPlayer(hero); // 恢复信任度
                            HeroDataHandler.syncGlobalTrust(hero);
                        }

                        serverLevel.addFreshEntity(hero);

                        if (player != null) {
                            player.sendSystemMessage(Component.translatable("message.herobrine_companion.hero_summoned"));
                            setLastUseTime(stack, currentTime);
                        }
                    }
                }
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        } else {
            if (!level.isClientSide && player != null) {
                player.sendSystemMessage(Component.translatable("message.herobrine_companion.shelter_empty"));
            }
            return InteractionResult.FAIL; // 未绑定状态保持 FAIL，允许玩家使用该物品进行普通交互（如开门）
        }
    }

    // [新增] 判断方块互动类型 (增强版)
    private int getInteractionType(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        Block block = state.getBlock();

        // 2 = Rest (休息)
        // 原版
        if (state.is(BlockTags.BEDS) || block instanceof BedBlock) return 2;
        if (state.is(BlockTags.STAIRS) || block instanceof StairBlock) return 2;
        if (state.is(BlockTags.SLABS) || block instanceof SlabBlock) return 2;

        // 模组兼容：检查标签
        if (state.is(FORGE_CHAIRS) || state.is(C_CHAIRS)) return 2;

        // 模组兼容：检查名称关键词
        ResourceLocation key = BuiltInRegistries.BLOCK.getKey(block);
        if (key != null) {
            String path = key.getPath().toLowerCase();
            if (path.contains("chair") || path.contains("seat") || path.contains("sofa") || path.contains("stool") || path.contains("bench")) {
                return 2;
            }
        }

        // 3 = Guard (守卫)
        if (state.is(BlockTags.DOORS) || block instanceof DoorBlock) return 3;
        if (state.is(BlockTags.TRAPDOORS) || block instanceof TrapDoorBlock) return 3;
        if (state.is(BlockTags.FENCE_GATES) || block instanceof FenceGateBlock) return 3;
        if (state.is(Blocks.CHEST) || state.is(Blocks.TRAPPED_CHEST) || state.is(Blocks.ENDER_CHEST) || state.is(Blocks.BARREL) || state.is(Blocks.SHULKER_BOX)) return 3;

        // 1 = Inspect (查看)
        if (state.is(Blocks.SPAWNER)) return 1;
        if (state.is(Blocks.ENCHANTING_TABLE)) return 1;
        if (state.is(Blocks.BEACON)) return 1;
        if (state.is(Blocks.COMMAND_BLOCK) || state.is(Blocks.CHAIN_COMMAND_BLOCK) || state.is(Blocks.REPEATING_COMMAND_BLOCK)) return 1;
        if (state.is(BlockTags.DIAMOND_ORES) || state.is(BlockTags.EMERALD_ORES) || state.is(BlockTags.GOLD_ORES)) return 1;
        if (state.is(Blocks.ANCIENT_DEBRIS)) return 1;

        return 0; // 普通方块
    }

    private HeroEntity findHeroInAnyDimension(net.minecraft.server.MinecraftServer server) {
        for (ServerLevel level : server.getAllLevels()) {
            var entities = level.getEntities(ModEvents.HERO.get(), entity -> true);
            if (!entities.isEmpty()) {
                return entities.get(0);
            }
        }
        return null;
    }

    private boolean isBound(ItemStack stack) {
        CustomData customData = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        CompoundTag tag = customData.copyTag();
        return tag.contains("BoundHero");
    }

    private String getOwnerName(ItemStack stack) {
        CustomData customData = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        CompoundTag tag = customData.copyTag();
        return tag.getString("OwnerName");
    }

    private long getLastUseTime(ItemStack stack) {
        CustomData customData = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        CompoundTag tag = customData.copyTag();
        return tag.getLong("LastHeroSummonTime");
    }

    private void setLastUseTime(ItemStack stack, long time) {
        CustomData customData = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        CompoundTag tag = customData.copyTag();
        tag.putLong("LastHeroSummonTime", time);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
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