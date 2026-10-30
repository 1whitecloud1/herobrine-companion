package com.whitecloud233.modid.herobrine_companion.entity.ai.agent.tool;

import com.whitecloud233.modid.herobrine_companion.entity.HeroEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;

/**
 * {@code hero_gather}（交接 E5）：有限区域内拾取指定掉落物，以及收割已成熟作物。
 *
 * <p><b>只做两件事</b>：捡起范围内匹配的掉落物、收割已成熟作物。首版不把挖矿 / 砍树 / 自动建造
 * 塞进 gather——那需要独立论证目标范围、掉落与任务预算。</p>
 *
 * <p><b>真实数量</b>：全部通过实际拿到的物品数结算，不用 give 指令伪造。</p>
 *
 * <p><b>成熟判定与掉落</b>沿用游戏接口：{@link CropBlock#isMaxAge} 判成熟，
 * 破坏时走 {@code level.destroyBlock(pos, true)} 由游戏自己算掉落，
 * 不写死小麦、也不自己重算掉落表。</p>
 */
public final class HeroGatherTool implements AgentTool {

    public static final String ID = "hero_gather";

    /** 采集半径上限（格）：gather 是"身边顺手"的动作，不做远距离作业。 */
    public static final int MAX_RADIUS = 16;

    /** 单次最多处理的作物/掉落物数（有界，避免一次抽干主线程）。 */
    public static final int MAX_ACTIONS = 32;

    public static final String MODE_PICKUP = "pickup";
    public static final String MODE_HARVEST = "harvest";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String description() {
        return "在有限半径内拾取指定掉落物，或收割已成熟作物（可选补种）。返回实际获得数量；不够就是不够。";
    }

    @Override
    public String category() {
        return "action";
    }

    @Override
    public boolean requiresConfirmation() {
        return false;
    }

    @Override
    public List<AgentToolParameter> parameters() {
        return List.of(
                AgentToolParameter.required("mode", "string", "pickup=拾取掉落物，harvest=收割成熟作物"),
                AgentToolParameter.optional("item_id", "string", "pickup 时要拾取的物品注册 ID（省略表示拾取全部）"),
                AgentToolParameter.optional("crop", "string", "harvest 时的作物方块 ID（省略表示所有成熟作物）"),
                AgentToolParameter.optional("radius", "number", "范围半径（格），默认 8，最大 " + MAX_RADIUS),
                AgentToolParameter.optional("count", "number", "目标数量，默认 1"),
                AgentToolParameter.optional("replant", "boolean", "harvest 后是否补种（需要背包里有种子）"));
    }

    @Override
    public AgentToolResult validate(AgentToolArgs args) {
        String mode = args.getString("mode").trim().toLowerCase(java.util.Locale.ROOT);
        if (!MODE_PICKUP.equals(mode) && !MODE_HARVEST.equals(mode)) {
            return AgentToolResult.denied("mode 只允许 pickup 或 harvest",
                    layout("gather.invalid_mode"), AgentToolResult.Cause.INVALID_ARGS);
        }
        int radius = args.getInt("radius", 8);
        if (radius <= 0 || radius > MAX_RADIUS) {
            return AgentToolResult.denied("radius 必须在 1~" + MAX_RADIUS + " 之间",
                    layout("gather.invalid_radius"), AgentToolResult.Cause.INVALID_ARGS);
        }
        if (args.getInt("count", 1) <= 0) {
            return AgentToolResult.denied("count 必须大于 0", layout("gather.invalid_count"),
                    AgentToolResult.Cause.INVALID_ARGS);
        }
        return AgentToolResult.ok("valid");
    }

    @Override
    public AgentToolResult execute(AgentToolContext context, AgentToolArgs args) {
        HeroEntity hero = context.hero();
        if (hero == null || !(hero.level() instanceof ServerLevel level)) {
            return AgentToolResult.fail("我暂时不在这个世界里，无法采集");
        }
        String mode = args.getString("mode").trim().toLowerCase(java.util.Locale.ROOT);
        int radius = Math.min(MAX_RADIUS, Math.max(1, args.getInt("radius", 8)));
        int wanted = Math.max(1, args.getInt("count", 1));
        return MODE_PICKUP.equals(mode)
                ? pickup(hero, level, args.getString("item_id").trim(), radius, wanted)
                : harvest(hero, level, args.getString("crop").trim(), radius, wanted,
                        args.getBoolean("replant", false));
    }

    /** 拾取：走原版物品拾取（实际进入 Hero 的手持槽），以真实到手数量结算。 */
    private AgentToolResult pickup(HeroEntity hero, ServerLevel level, String itemId, int radius, int wanted) {
        AABB area = hero.getBoundingBox().inflate(radius);
        List<ItemEntity> drops = level.getEntitiesOfClass(ItemEntity.class, area, item -> item.isAlive());
        List<String> picked = new ArrayList<>();
        int pickedCount = 0;
        int skippedNoSpace = 0;
        for (ItemEntity drop : drops) {
            if (pickedCount >= wanted) {
                break;
            }
            ItemStack stack = drop.getItem();
            if (!matchesItem(stack, itemId)) {
                continue;
            }
            int want = Math.min(stack.getCount(), wanted - pickedCount);
            ItemStack take = stack.copyWithCount(want);
            int before = countInHands(hero, take);
            // 先腾出手（原版拾取逻辑在 Mob 里，用真实手持槽模拟一次"捡起"）。
            ItemStack leftover = insertIntoHands(hero, take);
            int actual = want - leftover.getCount();
            int after = countInHands(hero, take);
            int gained = Math.max(0, after - before);
            if (gained <= 0) {
                skippedNoSpace++;
                continue;
            }
            // 只有真的进了手持槽才从地上扣除，不复制物品。
            stack.shrink(gained);
            if (stack.isEmpty()) {
                drop.discard();
            } else {
                drop.setItem(stack);
            }
            pickedCount += gained;
            picked.add(describe(take) + " x" + gained);
        }
        if (pickedCount == 0) {
            return AgentToolResult.fail("附近没有可拾取的匹配掉落物（或我的手已经满了）",
                    skippedNoSpace > 0 ? layout("gather.hands_full") : layout("gather.nothing_found"));
        }
        String text = "拾取 " + pickedCount + " 个物品：" + String.join(", ", picked)
                + "；目标 " + wanted + "，实际 " + pickedCount;
        if (pickedCount < wanted) {
            text += "（不足，附近可拾取的就这么多）";
        }
        if (skippedNoSpace > 0) {
            text += "；有 " + skippedNoSpace + " 处因手满未拾取";
        }
        return AgentToolResult.ok(text, layout("gather.picked"));
    }

    /** 收割：成熟判定用 {@link CropBlock#isMaxAge}，掉落交给游戏，补种真实消耗种子。 */
    private AgentToolResult harvest(HeroEntity hero, ServerLevel level, String cropId, int radius, int wanted, boolean replant) {
        BlockPos center = hero.blockPosition();
        List<BlockPos> crops = new ArrayList<>();
        int scanned = 0;
        outer:
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                for (int dy = -2; dy <= 2; dy++) {
                    if (scanned++ >= 4096) {
                        break outer;
                    }
                    BlockPos pos = center.offset(dx, dy, dz);
                    if (!level.isLoaded(pos)) {
                        continue;
                    }
                    BlockState state = level.getBlockState(pos);
                    if (!isHarvestable(state, cropId)) {
                        continue;
                    }
                    crops.add(pos);
                    if (crops.size() >= MAX_ACTIONS) {
                        break outer;
                    }
                }
            }
        }
        if (crops.isEmpty()) {
            return AgentToolResult.fail("附近没有成熟的作物", layout("gather.no_crops"));
        }
        int harvested = 0;
        int replanted = 0;
        int noSeed = 0;
        for (BlockPos pos : crops) {
            if (harvested >= wanted) {
                break;
            }
            BlockState state = level.getBlockState(pos);
            if (!isHarvestable(state, cropId)) {
                continue;
            }
            // 掉落由游戏按方块自身的 loot table 计算，不自己写一份掉落表。
            boolean destroyed = level.destroyBlock(pos, true);
            if (!destroyed) {
                continue;
            }
            harvested++;
            if (replant && state.getBlock() instanceof CropBlock cropBlock) {
                ItemStack seed = seedFor(hero, cropBlock);
                if (seed.isEmpty()) {
                    noSeed++;
                } else {
                    level.setBlockAndUpdate(pos, cropBlock.getStateForAge(0));
                    seed.shrink(1);
                    replanted++;
                }
            }
        }
        if (harvested == 0) {
            return AgentToolResult.fail("没有真正收割到作物", layout("gather.nothing_found"));
        }
        String text = "收割 " + harvested + " 处作物（目标 " + wanted + "）";
        if (harvested < wanted) {
            text += "；附近成熟的就这么多";
        }
        if (replant) {
            text += "；补种 " + replanted + " 处";
            if (noSeed > 0) {
                text += "，有 " + noSeed + " 处因缺少种子未补种";
            }
        }
        return AgentToolResult.ok(text, layout("gather.harvested"));
    }

    private static boolean isHarvestable(BlockState state, String cropId) {
        if (!(state.getBlock() instanceof CropBlock crop)) {
            return false;
        }
        if (!cropId.isEmpty() && !cropId.equals(String.valueOf(BuiltInRegistries.BLOCK.getKey(state.getBlock())))) {
            return false;
        }
        return crop.isMaxAge(state);
    }

    /**
     * 找补种用的种子（只从 Hero 真实手持槽里取，不凭空生成）。
     *
     * <p>不调用 {@code CropBlock#getCloneItemStack}：那个方法需要真实的世界/位置/状态参数，
     * 传 null 会在运行时抛异常（编译期看不出来）。这里按方块注册名映射到原版种子，
     * 映射不到就不补种并如实报告"缺种子"。</p>
     */
    private static ItemStack seedFor(HeroEntity hero, CropBlock crop) {
        net.minecraft.world.item.Item seedItem = seedItemFor(crop);
        if (seedItem == null) {
            return ItemStack.EMPTY;
        }
        return findInHands(hero, seedItem);
    }

    /** 常见原版作物的种子对应关系；未知作物返回 null（宁可报缺种子，也不猜）。 */
    private static net.minecraft.world.item.Item seedItemFor(CropBlock crop) {
        var key = BuiltInRegistries.BLOCK.getKey(crop);
        if (key == null) {
            return null;
        }
        return switch (key.getPath()) {
            case "wheat" -> Items.WHEAT_SEEDS;
            case "carrots" -> Items.CARROT;
            case "potatoes" -> Items.POTATO;
            case "beetroots" -> Items.BEETROOT_SEEDS;
            case "torchflower_crop" -> Items.TORCHFLOWER_SEEDS;
            case "pitcher_crop" -> Items.PITCHER_POD;
            default -> null;
        };
    }

    private static ItemStack findInHands(HeroEntity hero, net.minecraft.world.item.Item item) {
        ItemStack main = hero.getMainHandItem();
        if (main.is(item)) {
            return main;
        }
        ItemStack off = hero.getOffhandItem();
        return off.is(item) ? off : ItemStack.EMPTY;
    }

    private static int countInHands(HeroEntity hero, ItemStack template) {
        int total = 0;
        if (ItemStack.isSameItemSameTags(hero.getMainHandItem(), template)) {
            total += hero.getMainHandItem().getCount();
        }
        if (ItemStack.isSameItemSameTags(hero.getOffhandItem(), template)) {
            total += hero.getOffhandItem().getCount();
        }
        return total;
    }

    /** 放进 Hero 的真实手持槽；放不下的部分原样返回（调用方据此不扣地面物品）。 */
    private static ItemStack insertIntoHands(HeroEntity hero, ItemStack stack) {
        int remaining = stack.getCount();
        ItemStack rest = stack.copy();
        ItemStack main = hero.getMainHandItem();
        if (main.isEmpty()) {
            int place = Math.min(remaining, rest.getMaxStackSize());
            hero.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, rest.copyWithCount(place));
            remaining -= place;
        } else if (ItemStack.isSameItemSameTags(main, rest)) {
            int room = Math.min(main.getMaxStackSize(), 64) - main.getCount();
            int place = Math.min(room, remaining);
            if (place > 0) {
                ItemStack updated = main.copy();
                updated.grow(place);
                hero.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, updated);
                remaining -= place;
            }
        }
        if (remaining > 0) {
            ItemStack off = hero.getOffhandItem();
            if (off.isEmpty()) {
                int place = Math.min(remaining, rest.getMaxStackSize());
                hero.setItemSlot(net.minecraft.world.entity.EquipmentSlot.OFFHAND, rest.copyWithCount(place));
                remaining -= place;
            } else if (ItemStack.isSameItemSameTags(off, rest)) {
                int room = Math.min(off.getMaxStackSize(), 64) - off.getCount();
                int place = Math.min(room, remaining);
                if (place > 0) {
                    ItemStack updated = off.copy();
                    updated.grow(place);
                    hero.setItemSlot(net.minecraft.world.entity.EquipmentSlot.OFFHAND, updated);
                    remaining -= place;
                }
            }
        }
        return remaining <= 0 ? ItemStack.EMPTY : rest.copyWithCount(remaining);
    }

    private static boolean matchesItem(ItemStack stack, String itemId) {
        if (itemId.isEmpty()) {
            return true;
        }
        var key = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return key != null && key.toString().equals(itemId);
    }

    private static String describe(ItemStack stack) {
        var key = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return key == null ? String.valueOf(stack.getItem()) : key.toString();
    }

    private static Component layout(String key) {
        return Component.translatable("message.herobrine_companion.tool." + key);
    }
}
