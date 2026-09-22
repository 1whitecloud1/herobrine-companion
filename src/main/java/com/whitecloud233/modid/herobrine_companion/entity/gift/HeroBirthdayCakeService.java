package com.whitecloud233.modid.herobrine_companion.entity.gift;

import com.whitecloud233.modid.herobrine_companion.HerobrineCompanion;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import com.whitecloud233.modid.herobrine_companion.entity.BirthdayCakePropEntity;
import com.whitecloud233.modid.herobrine_companion.init.ModEntities;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import com.whitecloud233.modid.herobrine_companion.init.ModItems;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.ForgeRegistries;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 无名之蛋糕的两套行为 —— Bedrock hero_birthday_cake_service.py / hero_birthday_cake_place_service.py 的移植。
 *
 * <p><b>摆放</b>(潜行 + 使用):把蛋糕摆到地上(顶面 → 点击面外侧 → 脚边);
 * 对同一格再用一次即收回;同一玩家同时最多一个摆件,换位置会先收回旧的;
 * <b>物品永不消耗</b>。
 *
 * <p><b>食用</b>:冷却外吃完 → 随机回赠一件(模组全部物品 ∪ 原版精选清单)+
 * 16 种正向效果 30 分钟 + 记 60 分钟冷却(持久化在玩家档案,重进不清零);
 * 冷却内尝试进食 → 直接取消 + 提示,不浪费蛋糕也不加饱食度。
 *
 * <p>摆件是自定义实体 {@code BirthdayCakePropEntity}(模型由 Bedrock 的 birthday_cake.geo.json 转换而来),
 * birthday_cake_prop 的 3D 几何体与烛火(动画由实体的火焰粒子近似)。
 */
public final class HeroBirthdayCakeService {

    private static final Logger LOGGER = LogUtils.getLogger();

    private HeroBirthdayCakeService() {
    }

    // ------------------------------------------------------------------
    // 常量(与 Bedrock 一致)
    // ------------------------------------------------------------------

    /** 再吃发奖冷却:60 分钟。 */
    public static final int EAT_REWARD_COOLDOWN_SECONDS = 3600;

    /** 吃完祝福时长:30 分钟。 */
    public static final int BUFF_DURATION_SECONDS = 1800;

    /** 吃完祝福:除夜视/缓降外的全部正向效果,I 级。 */
    private static final List<String> BUFF_EFFECT_NAMES = List.of(
            "speed", "haste", "strength", "jump_boost", "regeneration", "resistance",
            "fire_resistance", "water_breathing", "invisibility", "absorption",
            "health_boost", "saturation", "luck", "conduit_power", "dolphin_grace",
            "village_hero");

    private static final String PLACED_KEY = "message.herobrine_companion.birthday_cake_placed";
    private static final String RETRACTED_KEY = "message.herobrine_companion.birthday_cake_retracted";
    private static final String BLOCKED_KEY = "message.herobrine_companion.birthday_cake_place_blocked";
    private static final String EAT_COOLDOWN_KEY = "message.herobrine_companion.birthday_cake_cooldown";

    private static final String CAKE_ITEM_ID = HerobrineCompanion.MODID + ":birthday_cake";
    private static final String TAB_ICON_ID = HerobrineCompanion.MODID + ":tab_icon";

    /** 原版奖励池:人工精选清单(食物/矿物/实用器/中高级战利品),与 Bedrock 一致。 */
    private static final List<String> VANILLA_REWARD_ITEMS = List.of(
            // 食物
            "minecraft:apple", "minecraft:bread", "minecraft:baked_potato",
            "minecraft:pumpkin_pie", "minecraft:cookie", "minecraft:cake",
            "minecraft:dried_kelp", "minecraft:mushroom_stew", "minecraft:beetroot_soup",
            "minecraft:rabbit_stew", "minecraft:melon_slice", "minecraft:honey_bottle",
            "minecraft:cooked_beef", "minecraft:cooked_chicken", "minecraft:cooked_porkchop",
            "minecraft:cooked_mutton", "minecraft:cooked_rabbit", "minecraft:cooked_cod",
            "minecraft:cooked_salmon", "minecraft:golden_apple", "minecraft:golden_carrot",
            // 矿物与材料
            "minecraft:iron_ingot", "minecraft:gold_ingot", "minecraft:lapis_lazuli",
            "minecraft:redstone", "minecraft:quartz", "minecraft:emerald",
            "minecraft:diamond", "minecraft:netherite_scrap", "minecraft:netherite_ingot",
            "minecraft:gold_block", "minecraft:emerald_block", "minecraft:diamond_block",
            "minecraft:ender_pearl", "minecraft:ender_eye", "minecraft:obsidian",
            "minecraft:crying_obsidian", "minecraft:spyglass",
            // 实用器与工具
            "minecraft:diamond_sword", "minecraft:diamond_pickaxe", "minecraft:bow",
            "minecraft:arrow", "minecraft:shield", "minecraft:enchanted_book",
            "minecraft:experience_bottle", "minecraft:name_tag", "minecraft:saddle",
            // 中高级战利品
            "minecraft:enchanted_golden_apple", "minecraft:totem_of_undying",
            "minecraft:nether_star", "minecraft:elytra", "minecraft:trident",
            "minecraft:beacon", "minecraft:dragon_breath", "minecraft:heart_of_the_sea",
            "minecraft:shulker_shell", "minecraft:phantom_membrane", "minecraft:blaze_rod",
            "minecraft:ghast_tear", "minecraft:wither_skeleton_skull",
            "minecraft:echo_shard", "minecraft:amethyst_shard");

    /** 摆放记录(Bedrock 为内存态 _placed_records;重启后摆件仍在但不参与同格收回判定)。 */
    private static final Map<UUID, PlacedCake> PLACED = new ConcurrentHashMap<>();

    /**
     * 吃完后延迟这么多 tick 才挂物品冷却覆盖层:让玩家吃东西的动画彻底播完再变灰(20 tick = 1 秒)。
     * 服务端冷却判定(生日礼物冷却)本身仍从吃完那一刻算起,这里只影响显示时机。
     */
    private static final int COOLDOWN_OVERLAY_DELAY_TICKS = 20;

    /** 玩家 UUID → 剩余延迟 tick(等动画播完再挂冷却灰模)。 */
    private static final Map<UUID, Integer> PENDING_COOLDOWN_OVERLAY = new ConcurrentHashMap<>();
    /** 一次已摆放的蛋糕。 */
    public record PlacedCake(ResourceLocation dimension, BlockPos pos, int entityId) {
    }

    // ------------------------------------------------------------------
    // 摆放 / 收回
    // ------------------------------------------------------------------

    /**
     * 潜行 + 使用蛋糕(等价 Bedrock on_use_on / on_try_use)。
     *
     * @param clickedPos 方块目标坐标;无方块目标(对着空气右键)传 null
     * @param face       点击面;无方块目标传 null
     * @return 处理结果(始终为 sidedSuccess:潜行时不会进入进食流程)
     */
    public static InteractionResult handleSneakUse(Player player, BlockPos clickedPos, Direction face) {
        Level level = player.level();
        if (!(player instanceof ServerPlayer serverPlayer) || !(level instanceof ServerLevel serverLevel)) {
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        UUID playerId = serverPlayer.getUUID();
        BlockPos placePos = resolvePlacePos(serverLevel, serverPlayer, clickedPos, face);
        if (placePos == null) {
            notify(serverPlayer, BLOCKED_KEY);
            return InteractionResult.sidedSuccess(false);
        }
        PlacedCake record = PLACED.get(playerId);
        // 对同一格再用一次 → 收回(物品不消耗,蛋糕永不消失)
        if (isSameSpot(record, serverLevel, placePos)) {
            removePlaced(serverLevel, playerId);
            notify(serverPlayer, RETRACTED_KEY);
            return InteractionResult.sidedSuccess(false);
        }
        // 换位置:先收回旧的,保证一人最多一个
        removePlaced(serverLevel, playerId);
        int entityId = spawnProp(serverLevel, placePos, serverPlayer.getYRot());
        if (entityId < 0) {
            notify(serverPlayer, BLOCKED_KEY);
            return InteractionResult.sidedSuccess(false);
        }
        PLACED.put(playerId, new PlacedCake(serverLevel.dimension().location(), placePos, entityId));
        notify(serverPlayer, PLACED_KEY);
        return InteractionResult.sidedSuccess(false);
    }

    /** 同一玩家在此 tick 窗口内的重复摆放请求会被忽略(物品路径与网络包路径可能同时到达)。 */
    private static final Map<UUID, Long> LAST_ACTION_TICK = new ConcurrentHashMap<>();
    private static final int ACTION_DEDUPE_TICKS = 4;

    /**
     * 摆放 / 收回请求入口(来自 {@code BirthdayCakePlacePacket})。
     *
     * <p><b>这是唯一的摆放入口</b>:原版 {@code ServerPlayerGameMode#useItem/useItemOn} 在冷却期间
     * 根本不会调用物品的 use/useOn,所以摆放不能挂在物品上,否则蛋糕一进冷却就再也摆不了。
     */
    public static void handleClientSneakUse(ServerPlayer player, BlockPos clickedPos, Direction face) {
        long now = player.level().getGameTime();
        Long last = LAST_ACTION_TICK.get(player.getUUID());
        if (last != null && now - last < ACTION_DEDUPE_TICKS) {
            LOGGER.debug("[HeroGift] cake place request deduped: player={}", player.getName().getString());
            return;
        }
        LAST_ACTION_TICK.put(player.getUUID(), now);
        handleSneakUse(player, clickedPos, face);
    }
    /** 目标格:点击方块顶面 → 点击面外侧 → 玩家脚边(取第一个可放置的)。 */
    private static BlockPos resolvePlacePos(ServerLevel level, ServerPlayer player, BlockPos clickedPos, Direction face) {
        List<BlockPos> candidates = new ArrayList<>(3);
        if (clickedPos != null) {
            candidates.add(clickedPos.above());
            if (face != null) {
                candidates.add(clickedPos.relative(face));
            }
        }
        candidates.add(player.blockPosition());
        for (BlockPos pos : candidates) {
            if (isReplaceable(level, pos)) {
                return pos;
            }
        }
        return null;
    }

    private static boolean isReplaceable(ServerLevel level, BlockPos pos) {
        if (!level.isLoaded(pos)) {
            return false;
        }
        BlockState state = level.getBlockState(pos);
        return state.isAir() || state.canBeReplaced();
    }

    private static boolean isSameSpot(PlacedCake record, ServerLevel level, BlockPos pos) {
        return record != null
                && record.dimension().equals(level.dimension().location())
                && record.pos().equals(pos);
    }

    private static void removePlaced(ServerLevel level, UUID playerId) {
        PlacedCake record = PLACED.remove(playerId);
        if (record == null) {
            return;
        }
        if (!record.dimension().equals(level.dimension().location())) {
            return;
        }
        if (level.getEntity(record.entityId()) != null) {
            level.getEntity(record.entityId()).discard();
        }
    }

    /** 生成摆件实体(BirthdayCakePropEntity)并返回实体 id;失败返回 -1。 */
    private static int spawnProp(ServerLevel level, BlockPos pos, float yaw) {
        BirthdayCakePropEntity prop = new BirthdayCakePropEntity(ModEntities.BIRTHDAY_CAKE_PROP.get(), level);
        prop.setPos(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D);
        prop.setYRot(yaw);
        if (!level.addFreshEntity(prop)) {
            LOGGER.warn("[HeroGift] birthday cake prop could not be added to the world at {}", pos);
            return -1;
        }
        level.playSound(null, pos, SoundEvents.ITEM_FRAME_ADD_ITEM, SoundSource.BLOCKS, 0.7F, 1.1F);
        LOGGER.info("[HeroGift] birthday cake prop spawned: entityId={} pos={} dim={}",
                prop.getId(), pos, level.dimension().location());
        return prop.getId();
    }

    private static void notify(ServerPlayer player, String langKey) {
        player.sendSystemMessage(Component.translatable(langKey));
    }

    // ------------------------------------------------------------------
    // 食用:冷却拦截 + 吃完发奖
    // ------------------------------------------------------------------

    /** 是否处于「已吃过」冷却内(60 分钟,存玩家档案)。 */
    public static boolean isEatCooldownActive(ServerPlayer player) {
        return player.level().getGameTime() < readRewardTick(player);
    }

    /**
     * 服务端每 tick 推进"待挂冷却覆盖层"(见 HeroGiftBehaviorEvents 的玩家 tick 订阅)。
     * 倒计时结束才真正 addCooldown,保证玩家吃东西的动画已经播完。
     */
    public static void tickPendingCooldownOverlay(ServerPlayer player) {
        Integer remaining = PENDING_COOLDOWN_OVERLAY.get(player.getUUID());
        if (remaining == null) {
            return;
        }
        if (remaining <= 1) {
            PENDING_COOLDOWN_OVERLAY.remove(player.getUUID());
            if (isEatCooldownActive(player)) {
                player.getCooldowns().addCooldown(cakeItem(), EAT_REWARD_COOLDOWN_SECONDS * 20);
            }
            return;
        }
        PENDING_COOLDOWN_OVERLAY.put(player.getUUID(), remaining - 1);
    }
    public static void notifyEatCooldown(ServerPlayer player) {
        // 同步原版物品冷却覆盖层(灰色扇形):玩家重进或覆盖层已消失时重新挂上剩余时间
        long remaining = readRewardTick(player) - player.level().getGameTime();
        if (remaining > 0) {
            player.getCooldowns().addCooldown(cakeItem(), (int) Math.min(Integer.MAX_VALUE, remaining));
        }
        notify(player, EAT_COOLDOWN_KEY);
    }

    /** 蛋糕物品(用于原版冷却覆盖层)。 */
    private static Item cakeItem() {
        return ModItems.BIRTHDAY_CAKE.get();
    }

    /**
     * 吃完蛋糕(等价 Bedrock on_player_eat_food):
     * 冷却外 → 随机奖励 + 30 分钟祝福 + 记冷却;冷却内 → 什么都不发。
     * 蛋糕本身由 {@code BirthdayCakeItem#finishUsingItem} 保留(返回自己)。
     */
    public static void onEaten(ServerPlayer player) {
        long now = player.level().getGameTime();
        if (now < readRewardTick(player)) {
            return;
        }
        RandomSource random = player.getRandom();
        applyEatBuffs(player);
        ItemStack reward = pickReward(random);
        if (!reward.isEmpty() && !player.getInventory().add(reward)) {
            player.drop(reward, true);
        }
        writeRewardTick(player, now + (long) EAT_REWARD_COOLDOWN_SECONDS * 20L);
        // 原版物品冷却栏:等吃东西动画播完(延迟 COOLDOWN_OVERLAY_DELAY_TICKS)再挂,
        // 由 tickPendingCooldownOverlay 在服务端每 tick 推进
        PENDING_COOLDOWN_OVERLAY.put(player.getUUID(), COOLDOWN_OVERLAY_DELAY_TICKS);
    }

    private static void applyEatBuffs(ServerPlayer player) {
        for (String effectName : BUFF_EFFECT_NAMES) {
            HeroGiftFeedbackService.applyEffect(player, effectName, BUFF_DURATION_SECONDS, 0);
        }
        player.level().playSound(null, player.getX(), player.getY() + 0.6D, player.getZ(),
                SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.9F, 1.2F);
        if (player.level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(net.minecraft.core.particles.ParticleTypes.HAPPY_VILLAGER,
                    player.getX(), player.getY() + 0.8D, player.getZ(), 16, 0.4D, 0.4D, 0.4D, 0.02D);
        }
    }

    /**
     * 奖励池 = 当前整合包**所有已装载模组**的注册物品(含原版与全部模组)。
     *
     * <p>只排除:空气类、纯技术性/破坏性的方块(屏障、结构方块、命令方块、传送门、火等)、
     * 调试用品,以及蛋糕自身(避免自我奖励)。
     */
    private static ItemStack pickReward(RandomSource random) {
        List<Item> pool = new ArrayList<>(2048);
        for (Item item : ForgeRegistries.ITEMS) {
            if (item == Items.AIR) {
                continue;
            }
            ResourceLocation key = ForgeRegistries.ITEMS.getKey(item);
            if (key == null || !isRewardEligible(key, item)) {
                continue;
            }
            pool.add(item);
        }
        if (pool.isEmpty()) {
            return ItemStack.EMPTY;
        }
        return new ItemStack(pool.get(random.nextInt(pool.size())));
    }

    /** 奖励池过滤:剔除空气/技术方块/调试物品与蛋糕自身。 */
    private static boolean isRewardEligible(ResourceLocation key, Item item) {
        if (CAKE_ITEM_ID.equals(key.toString())) {
            return false;
        }
        String path = key.getPath();
        if (path.equals("air") || path.endsWith("_air")) {
            return false;
        }
        if (path.equals("barrier") || path.equals("light") || path.equals("structure_void")
                || path.equals("jigsaw") || path.equals("structure_block") || path.equals("spawner")
                || path.equals("bedrock") || path.equals("end_portal") || path.equals("end_portal_frame")
                || path.equals("nether_portal") || path.equals("fire") || path.equals("soul_fire")
                || path.equals("moving_piston") || path.equals("piston_head")
                || path.startsWith("command_block") || path.startsWith("infested_")) {
            return false;
        }
        if (path.equals("debug_stick") || path.equals("knowledge_book")) {
            return false;
        }
        return true;
    }

    // ------------------------------------------------------------------
    // 冷却持久化(玩家档案)
    // ------------------------------------------------------------------

    private static long readRewardTick(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel serverLevel)) {
            return 0L;
        }
        return com.whitecloud233.modid.herobrine_companion.entity.logic.data.HeroWorldData
                .get(serverLevel)
                .getBirthdayCakeRewardTick(player.getUUID());
    }

    private static void writeRewardTick(ServerPlayer player, long tick) {
        if (!(player.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        com.whitecloud233.modid.herobrine_companion.entity.logic.data.HeroWorldData
                .get(serverLevel)
                .setBirthdayCakeRewardTick(player.getUUID(), tick);
    }
}
