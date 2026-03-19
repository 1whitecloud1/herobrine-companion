package com.whitecloud233.herobrine_companion.item;

import com.whitecloud233.herobrine_companion.entity.HeroEntity;
import com.whitecloud233.herobrine_companion.entity.logic.HeroDataHandler;
import com.whitecloud233.herobrine_companion.entity.logic.HeroLogic;
import com.whitecloud233.herobrine_companion.event.ModEvents;
import com.whitecloud233.herobrine_companion.event.HeroWorldData;
import com.whitecloud233.herobrine_companion.util.EndRingContext;
import com.whitecloud233.herobrine_companion.world.inventory.HeroContractMenu;
import com.whitecloud233.herobrine_companion.world.structure.ModStructures;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
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
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLEnvironment;

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

    // 1.21: 资源位置使用 fromNamespaceAndPath 实例化
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
                    String mockery = MOCKERY_MESSAGES[random.nextInt(MOCKERY_MESSAGES.length)];
                    player.sendSystemMessage(Component.translatable(mockery));
                    return InteractionResult.FAIL;
                }

                HeroEntity existingHero = findHeroInAnyDimension(serverLevel.getServer());

                int actionType = getInteractionType(level, clickedPos);

                if (actionType > 0 && existingHero != null) {
                    if (existingHero.level().dimension() == level.dimension()) {
                        HeroLogic.handlePlayerInvitation(existingHero, player, clickedPos, actionType);
                        setLastUseTime(stack, currentTime);
                        return InteractionResult.SUCCESS;
                    }
                }

                Vec3 targetPos = context.getClickLocation().add(0, 1, 0);

                performHeroTeleport(serverLevel, player, targetPos);
                setLastUseTime(stack, currentTime);

            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        } else {
            if (!level.isClientSide && player != null) {
                player.sendSystemMessage(Component.translatable("message.herobrine_companion.shelter_empty"));
            }
            return InteractionResult.FAIL;
        }
    }

    private int getInteractionType(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        Block block = state.getBlock();

        if (state.is(BlockTags.BEDS) || block instanceof BedBlock) return 2;
        if (state.is(BlockTags.STAIRS) || block instanceof StairBlock) return 2;
        if (state.is(BlockTags.SLABS) || block instanceof SlabBlock) return 2;
        if (state.is(FORGE_CHAIRS) || state.is(C_CHAIRS)) return 2;

        // 1.21: 注册表访问变更为 BuiltInRegistries
        ResourceLocation key = BuiltInRegistries.BLOCK.getKey(block);
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

    public static HeroEntity findHeroInAnyDimension(net.minecraft.server.MinecraftServer server) {
        for (ServerLevel level : server.getAllLevels()) {
            var entities = level.getEntities(ModEvents.HERO.get(), entity -> true);
            if (!entities.isEmpty()) {
                return entities.get(0);
            }
        }
        return null;
    }

    public static void performHeroTeleport(ServerLevel serverLevel, @javax.annotation.Nullable Player player, net.minecraft.world.phys.Vec3 targetPos) {
        HeroEntity existingHero = findHeroInAnyDimension(serverLevel.getServer());
        long currentTime = serverLevel.getGameTime();

        if (existingHero != null) {
            if (existingHero.isPassenger()) existingHero.stopRiding();
            existingHero.setInvitedPos(null);
            existingHero.setInvitedAction(0);

            CompoundTag heroData = new CompoundTag();
            existingHero.saveWithoutId(heroData);

            ListTag armorTags = existingHero.getArmorItemsTag();
            ListTag handTags = existingHero.getHandItemsTag();
            float oldYRot = existingHero.getYRot();
            float oldXRot = existingHero.getXRot();

            CompoundTag curiosTag = null;
            if (ModList.get().isLoaded("curios")) curiosTag = existingHero.getCuriosBackItemTag();

            HeroDataHandler.updateGlobalTrust(existingHero);
            existingHero.discard(); // 彻底销毁旧实体

            HeroEntity newHero = ModEvents.HERO.get().create(serverLevel);
            if (newHero != null) {
                if (heroData.contains("UUID")) heroData.remove("UUID");
                if (heroData.contains("UUIDMost")) heroData.remove("UUIDMost");
                if (heroData.contains("UUIDLeast")) heroData.remove("UUIDLeast");

                newHero.load(heroData);
                newHero.moveTo(targetPos.x, targetPos.y, targetPos.z, oldYRot, oldXRot);
                newHero.setUUID(UUID.randomUUID()); // 赋予新 UUID
                newHero.loadEquipmentFromTag(armorTags, handTags);

                if (ModList.get().isLoaded("curios") && curiosTag != null) newHero.setCuriosBackItemFromTag(curiosTag);

                if (player != null) {
                    HeroWorldData worldData = HeroWorldData.get(serverLevel);
                    boolean isNaked = true;
                    for (ItemStack item : newHero.getArmorSlots()) if (!item.isEmpty()) isNaked = false;
                    for (ItemStack item : newHero.getHandSlots()) if (!item.isEmpty()) isNaked = false;

                    if (isNaked) newHero.loadEquipmentFromTag(worldData.getArmorItems(player.getUUID()), worldData.getHandItems(player.getUUID()));
                    worldData.setEquipment(player.getUUID(), newHero.getArmorItemsTag(), newHero.getHandItemsTag());

                    if (ModList.get().isLoaded("curios")) {
                        if (newHero.isCuriosBackSlotEmpty()) newHero.setCuriosBackItemFromTag(worldData.getCuriosBackItem(player.getUUID()));
                        worldData.setCuriosBackItem(player.getUUID(), newHero.getCuriosBackItemTag());
                    }
                    worldData.setActiveHeroUUID(newHero.getUUID()); // 登记统治权
                }

                newHero.addTag(EndRingContext.TAG_RESPAWNED_SAFE);
                HeroDataHandler.syncGlobalTrust(newHero);
                serverLevel.addFreshEntity(newHero); // 强制刷新客户端
                newHero.setLastSummonedTime(currentTime);

                if (player != null) {
                    player.sendSystemMessage(Component.translatable("message.herobrine_companion.hero_teleported"));
                }
            }
            serverLevel.playSound(null, BlockPos.containing(targetPos), net.minecraft.sounds.SoundEvents.ENDERMAN_TELEPORT, net.minecraft.sounds.SoundSource.PLAYERS, 1.0f, 1.0f);
        } else {
            // 全新生成逻辑
            HeroEntity hero = ModEvents.HERO.get().create(serverLevel);
            if (hero != null) {
                hero.moveTo(targetPos);
                hero.finalizeSpawn(serverLevel, serverLevel.getCurrentDifficultyAt(hero.blockPosition()), MobSpawnType.TRIGGERED, null);

                if (player != null) {
                    hero.setOwnerUUID(player.getUUID());
                    HeroDataHandler.restoreTrustFromPlayer(hero);
                    HeroDataHandler.syncGlobalTrust(hero);

                    HeroWorldData worldData = HeroWorldData.get(serverLevel);
                    hero.setSkinVariant(worldData.getSkinVariant());
                    if (worldData.getSkinVariant() == HeroEntity.SKIN_CUSTOM) hero.setCustomSkinName(worldData.getCustomSkinName());
                    
                    worldData.setActiveHeroUUID(hero.getUUID()); // 登记统治权
                    hero.loadEquipmentFromTag(worldData.getArmorItems(player.getUUID()), worldData.getHandItems(player.getUUID()));
                    
                    if (ModList.get().isLoaded("curios")) {
                        CompoundTag savedCurios = worldData.getCuriosBackItem(player.getUUID());
                        if (savedCurios != null && !savedCurios.isEmpty()) hero.setCuriosBackItemFromTag(savedCurios);
                    }
                }
                serverLevel.addFreshEntity(hero);
                hero.setLastSummonedTime(currentTime);
                if (player != null) player.sendSystemMessage(Component.translatable("message.herobrine_companion.hero_summoned"));
            }
        }
    }


    // ================== [1.21 核心改动：采用数据组件 (Data Components) 读写数据] ==================

    private boolean isBound(ItemStack stack) {
        CustomData customData = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        return customData.contains("BoundHero");
    }

    private String getOwnerName(ItemStack stack) {
        CustomData customData = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        if (customData.contains("OwnerName")) {
            return customData.copyTag().getString("OwnerName");
        }
        return "";
    }

    private long getLastUseTime(ItemStack stack) {
        CustomData customData = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        if (customData.contains("LastHeroSummonTime")) {
            return customData.copyTag().getLong("LastHeroSummonTime");
        }
        return 0L;
    }

    private void setLastUseTime(ItemStack stack, long time) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
            tag.putLong("LastHeroSummonTime", time);
        });
    }

    // =========================================================================================

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