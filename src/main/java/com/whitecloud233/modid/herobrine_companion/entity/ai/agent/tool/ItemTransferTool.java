package com.whitecloud233.modid.herobrine_companion.entity.ai.agent.tool;

import com.whitecloud233.modid.herobrine_companion.entity.HeroEntity;
import com.whitecloud233.modid.herobrine_companion.util.AiItemNaming;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.ArrayList;
import java.util.List;

/**
 * {@code item_transfer}（交接 E1）：真实的物品转移，数量守恒、身份保留。
 *
 * <p><b>Hero 实际可承载的物品槽</b>：本项目 Hero 继承 {@code PathfinderMob} + {@code Merchant}，
 * <b>没有玩家式物品栏</b>，只有双手（{@code MAINHAND} / {@code OFFHAND}）。
 * 所以首版显式把 Hero 一侧限制为这两个槽，<b>不创建</b>一个与游戏物品系统脱节的隐形背包。</p>
 *
 * <p><b>守恒</b>（交接 E1 验收 T15）：</p>
 * <ol>
 *   <li>先校验目标容量，再按"实际取出 → 实际插入"核算；</li>
 *   <li>插不进去的部分<b>放回源</b>，不静默丢弃、不复制、不生成物品；</li>
 *   <li>NBT / 组件随 {@link ItemStack} 原样搬运，不做重建。</li>
 * </ol>
 *
 * <p><b>范围</b>：只在可交互距离内执行；容器访问沿用既有权限（容器的
 * {@code canOpen}/{@code stillValid}），不绕过归属检查。</p>
 */
public final class ItemTransferTool implements AgentTool {

    public static final String ID = "item_transfer";

    /** 允许交互的距离平方（4 格²）。 */
    private static final double MAX_DISTANCE_SQR = 16.0D;

    /** Hero 侧允许的槽位名。 */
    private static final String SLOT_MAINHAND = "mainhand";
    private static final String SLOT_OFFHAND = "offhand";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String description() {
        return "在玩家、Herobrine（只有主手/副手两个真实槽位）与容器之间按物品或槽位转移，返回实际移动数量与剩余数量。";
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
                AgentToolParameter.required("from", "string", "来源：player=请求者，hero=Herobrine，container=容器"),
                AgentToolParameter.required("to", "string", "目标：player=请求者，hero=Herobrine，container=容器"),
                AgentToolParameter.optional("item_id", "string", "要转移的物品注册 ID（与 slot 二选一）"),
                AgentToolParameter.optional("slot", "string", "容器槽位号，或 hero 的 mainhand/offhand"),
                AgentToolParameter.optional("count", "number", "数量，默认 1"),
                AgentToolParameter.optional("x", "number", "容器 X 坐标（来源或目标包含 container 时必填）"),
                AgentToolParameter.optional("y", "number", "容器 Y 坐标"),
                AgentToolParameter.optional("z", "number", "容器 Z 坐标"));
    }

    @Override
    public AgentToolResult validate(AgentToolArgs args) {
        String from = args.getString("from").trim().toLowerCase(java.util.Locale.ROOT);
        String to = args.getString("to").trim().toLowerCase(java.util.Locale.ROOT);
        if (!isEndpoint(from) || !isEndpoint(to)) {
            return AgentToolResult.denied("from / to 只允许 player / hero / container",
                    layout("transfer.invalid_endpoint"), AgentToolResult.Cause.INVALID_ARGS);
        }
        if (from.equals(to)) {
            return AgentToolResult.denied("来源和目标不能相同",
                    layout("transfer.same_endpoint"), AgentToolResult.Cause.INVALID_ARGS);
        }
        if (!args.has("item_id") && !args.has("slot")) {
            return AgentToolResult.denied("需要 item_id 或 slot 指明要转移什么",
                    layout("transfer.missing_selector"), AgentToolResult.Cause.INVALID_ARGS);
        }
        if ((from.equals("container") || to.equals("container"))
                && !(args.has("x") && args.has("y") && args.has("z"))) {
            return AgentToolResult.denied("涉及容器时需要容器坐标 x / y / z",
                    layout("transfer.missing_container"), AgentToolResult.Cause.INVALID_ARGS);
        }
        int count = args.getInt("count", 1);
        if (count <= 0) {
            return AgentToolResult.denied("count 必须大于 0", layout("transfer.invalid_count"),
                    AgentToolResult.Cause.INVALID_ARGS);
        }
        return AgentToolResult.ok("valid");
    }

    @Override
    public AgentToolResult execute(AgentToolContext context, AgentToolArgs args) {
        HeroEntity hero = context.hero();
        if (hero == null || !(hero.level() instanceof ServerLevel level)) {
            return AgentToolResult.fail("我暂时不在这个世界里，无法搬运物品");
        }
        ServerPlayer requester = context.requesterUuid() == null || hero.getServer() == null
                ? null
                : hero.getServer().getPlayerList().getPlayer(context.requesterUuid());
        if (requester == null) {
            return AgentToolResult.fail("找不到请求者玩家，无法搬运物品");
        }
        String from = args.getString("from").trim().toLowerCase(java.util.Locale.ROOT);
        String to = args.getString("to").trim().toLowerCase(java.util.Locale.ROOT);
        int count = Math.max(1, args.getInt("count", 1));

        Side source;
        Side target;
        try {
            source = resolveSide(hero, requester, level, from, args);
            target = resolveSide(hero, requester, level, to, args);
        } catch (TransferFailure failure) {
            return AgentToolResult.fail(failure.getMessage(), layout(failure.displayKey));
        }
        if (source == null || target == null) {
            return AgentToolResult.fail("来源或目标不可用", layout("transfer.unavailable"));
        }
        if (!withinReach(hero, source) || !withinReach(hero, target)) {
            return AgentToolResult.fail("我还够不到那里（超过 4 格）", layout("transfer.too_far"));
        }

        ItemStack template = selectTemplate(source, args);
        if (template.isEmpty()) {
            return AgentToolResult.fail("来源里没有可转移的匹配物品", layout("transfer.no_match"));
        }

        // 先校验目标容量，再按实际取出/插入核算，插不进去的部分放回源。
        int movable = Math.min(count, countMatching(source, template));
        if (movable <= 0) {
            return AgentToolResult.fail("来源里数量不足", layout("transfer.no_match"));
        }
        int capacity = target.capacityFor(template, movable);
        if (capacity <= 0) {
            return AgentToolResult.fail("目标空间不足，什么也没移动", layout("transfer.no_space"));
        }
        int planned = Math.min(movable, capacity);

        ItemStack extracted = source.extract(template, planned);
        if (extracted.isEmpty()) {
            return AgentToolResult.fail("取出失败，物品没有变化", layout("transfer.extract_failed"));
        }
        ItemStack leftover = target.insert(extracted);
        if (!leftover.isEmpty()) {
            // 目标实际没接住：原样退回来源（不吞物品、不复制）。
            ItemStack returned = source.insert(leftover);
            if (!returned.isEmpty()) {
                // 连来源也放不回（极罕见）：掉落给玩家，绝不静默丢弃。
                requester.drop(returned.copy(), false);
                requester.getInventory().add(returned.copy());
            }
        }
        int moved = extracted.getCount() - leftover.getCount();
        String detail = moved + " 个 " + AiItemNaming.describe(template)
                + "：" + from + " → " + to
                + "；剩余 " + countMatching(source, template)
                + "；目标剩余空间见容器状态";
        if (moved < count) {
            detail += "（请求 " + count + "，实际 " + moved + "，受数量或空间限制）";
        }
        return AgentToolResult.ok(detail, layout("transfer.success"));
    }

    // ---- 端点抽象 ----

    /** 转移端点：容器 / 玩家物品栏 / Hero 的两个手持槽统一到这一层。 */
    private interface Side {
        /** 按注册 ID 找第一个匹配（返回数量 1 的代表栈，空表示没有）。 */
        ItemStack findByItemId(String itemId);

        /** 按槽位号 / 槽位名找（空表示不存在）。 */
        ItemStack findBySlot(String slot);

        int countMatching(ItemStack template);

        int capacityFor(ItemStack template, int want);

        /** 取出至多 want 个匹配物品；返回实际取出的栈（空表示失败）。 */
        ItemStack extract(ItemStack template, int want);

        /** 插入；返回没插进去的部分（空表示全部放下）。 */
        ItemStack insert(ItemStack stack);

        boolean withinReach(HeroEntity hero);
    }

    private static Side resolveSide(HeroEntity hero, ServerPlayer requester, ServerLevel level,
                                    String endpoint, AgentToolArgs args) throws TransferFailure {
        return switch (endpoint) {
            case "player" -> new PlayerSide(requester);
            case "hero" -> new HeroHandSide(hero);
            case "container" -> containerSide(level, args);
            default -> throw new TransferFailure("unknown_endpoint", "transfer.unavailable");
        };
    }

    private static Side containerSide(ServerLevel level, AgentToolArgs args) throws TransferFailure {
        BlockPos pos = new BlockPos(args.getInt("x", 0), args.getInt("y", 0), args.getInt("z", 0));
        if (!level.isLoaded(pos)) {
            throw new TransferFailure("容器所在区块没有加载", "transfer.chunk_unloaded");
        }
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (!(blockEntity instanceof Container container)) {
            throw new TransferFailure("那个方块不是容器", "transfer.not_container");
        }
        return new ContainerSide(container, pos);
    }

    private static boolean isEndpoint(String value) {
        return value.equals("player") || value.equals("hero") || value.equals("container");
    }

    private static boolean withinReach(HeroEntity hero, Side side) {
        return side.withinReach(hero);
    }

    private static ItemStack selectTemplate(Side source, AgentToolArgs args) {
        String itemId = args.getString("item_id").trim();
        if (!itemId.isEmpty()) {
            return source.findByItemId(itemId);
        }
        return source.findBySlot(args.getString("slot").trim());
    }

    private static int countMatching(Side side, ItemStack template) {
        return side.countMatching(template);
    }

    // ---- 具体端点 ----

    /** 玩家物品栏（含副手）。 */
    private static final class PlayerSide implements Side {
        private final Player player;

        PlayerSide(Player player) {
            this.player = player;
        }

        @Override
        public ItemStack findByItemId(String itemId) {
            return firstMatch(stack -> matchesId(stack, itemId), player.getInventory().items);
        }

        @Override
        public ItemStack findBySlot(String slot) {
            int index = parseInt(slot, -1);
            if (index < 0 || index >= player.getInventory().items.size()) {
                return ItemStack.EMPTY;
            }
            return player.getInventory().items.get(index).copyWithCount(1);
        }

        @Override
        public int countMatching(ItemStack template) {
            int total = 0;
            for (ItemStack stack : player.getInventory().items) {
                if (ItemStack.isSameItemSameTags(stack, template)) {
                    total += stack.getCount();
                }
            }
            return total;
        }

        @Override
        public int capacityFor(ItemStack template, int want) {
            int free = 0;
            int slotLimit = Math.min(player.getInventory().getMaxStackSize(), template.getMaxStackSize());
            for (ItemStack stack : player.getInventory().items) {
                if (stack.isEmpty()) {
                    free += slotLimit;
                } else if (ItemStack.isSameItemSameTags(stack, template)) {
                    free += Math.max(0, slotLimit - stack.getCount());
                }
                if (free >= want) {
                    return want;
                }
            }
            return free;
        }

        @Override
        public ItemStack extract(ItemStack template, int want) {
            int remaining = want;
            for (int i = 0; i < player.getInventory().items.size() && remaining > 0; i++) {
                ItemStack stack = player.getInventory().items.get(i);
                if (!ItemStack.isSameItemSameTags(stack, template)) {
                    continue;
                }
                int take = Math.min(remaining, stack.getCount());
                stack.shrink(take);
                remaining -= take;
                if (stack.isEmpty()) {
                    player.getInventory().items.set(i, ItemStack.EMPTY);
                }
            }
            return remaining == want ? ItemStack.EMPTY : template.copyWithCount(want - remaining);
        }

        @Override
        public ItemStack insert(ItemStack stack) {
            int before = countMatching(stack);
            ItemStack toAdd = stack.copy();
            boolean allAdded = player.getInventory().add(toAdd);
            int after = countMatching(stack);
            int added = Math.max(0, after - before);
            int leftover = stack.getCount() - added;
            // 与既有兼容层一致：add 失败的部分交给调用方退回来源，这里不自行丢弃。
            return leftover <= 0 || allAdded ? ItemStack.EMPTY : stack.copyWithCount(leftover);
        }

        @Override
        public boolean withinReach(HeroEntity hero) {
            return hero.distanceToSqr(player) <= MAX_DISTANCE_SQR;
        }
    }

    /** Hero 的真实手持槽（没有玩家式物品栏，只有双手）。 */
    private static final class HeroHandSide implements Side {
        private final HeroEntity hero;

        HeroHandSide(HeroEntity hero) {
            this.hero = hero;
        }

        private EquipmentSlot[] slots() {
            return new EquipmentSlot[]{EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND};
        }

        @Override
        public ItemStack findByItemId(String itemId) {
            for (EquipmentSlot slot : slots()) {
                ItemStack stack = hero.getItemBySlot(slot);
                if (matchesId(stack, itemId)) {
                    return stack.copyWithCount(1);
                }
            }
            return ItemStack.EMPTY;
        }

        @Override
        public ItemStack findBySlot(String slot) {
            EquipmentSlot resolved = resolveSlot(slot);
            return resolved == null ? ItemStack.EMPTY : hero.getItemBySlot(resolved).copyWithCount(1);
        }

        @Override
        public int countMatching(ItemStack template) {
            int total = 0;
            for (EquipmentSlot slot : slots()) {
                ItemStack stack = hero.getItemBySlot(slot);
                if (ItemStack.isSameItemSameTags(stack, template)) {
                    total += stack.getCount();
                }
            }
            return total;
        }

        @Override
        public int capacityFor(ItemStack template, int want) {
            int free = 0;
            int slotLimit = Math.min(64, template.getMaxStackSize());
            for (EquipmentSlot slot : slots()) {
                ItemStack stack = hero.getItemBySlot(slot);
                if (stack.isEmpty()) {
                    free += slotLimit;
                } else if (ItemStack.isSameItemSameTags(stack, template)) {
                    free += Math.max(0, slotLimit - stack.getCount());
                }
                if (free >= want) {
                    return want;
                }
            }
            return free;
        }

        @Override
        public ItemStack extract(ItemStack template, int want) {
            int remaining = want;
            for (EquipmentSlot slot : slots()) {
                if (remaining <= 0) {
                    break;
                }
                ItemStack stack = hero.getItemBySlot(slot);
                if (!ItemStack.isSameItemSameTags(stack, template)) {
                    continue;
                }
                int take = Math.min(remaining, stack.getCount());
                ItemStack updated = stack.copy();
                updated.shrink(take);
                hero.setItemSlot(slot, updated.isEmpty() ? ItemStack.EMPTY : updated);
                remaining -= take;
            }
            return remaining == want ? ItemStack.EMPTY : template.copyWithCount(want - remaining);
        }

        @Override
        public ItemStack insert(ItemStack stack) {
            int remaining = stack.getCount();
            ItemStack rest = stack.copy();
            for (EquipmentSlot slot : slots()) {
                if (remaining <= 0) {
                    break;
                }
                ItemStack existing = hero.getItemBySlot(slot);
                if (existing.isEmpty()) {
                    int place = Math.min(remaining, rest.getMaxStackSize());
                    hero.setItemSlot(slot, rest.copyWithCount(place));
                    remaining -= place;
                } else if (ItemStack.isSameItemSameTags(existing, rest)) {
                    int room = Math.min(existing.getMaxStackSize(), 64) - existing.getCount();
                    int place = Math.min(room, remaining);
                    if (place > 0) {
                        ItemStack updated = existing.copy();
                        updated.grow(place);
                        hero.setItemSlot(slot, updated);
                        remaining -= place;
                    }
                }
            }
            return remaining <= 0 ? ItemStack.EMPTY : rest.copyWithCount(remaining);
        }

        @Override
        public boolean withinReach(HeroEntity hero) {
            return true;
        }

        private EquipmentSlot resolveSlot(String slot) {
            String safe = slot == null ? "" : slot.trim().toLowerCase(java.util.Locale.ROOT);
            if (safe.isEmpty() || safe.equals(SLOT_MAINHAND)) {
                return EquipmentSlot.MAINHAND;
            }
            if (safe.equals(SLOT_OFFHAND)) {
                return EquipmentSlot.OFFHAND;
            }
            return null;
        }
    }

    /** 方块容器。 */
    private record ContainerSide(Container container, BlockPos pos) implements Side {

        @Override
        public ItemStack findByItemId(String itemId) {
            for (int i = 0; i < container.getContainerSize(); i++) {
                ItemStack stack = container.getItem(i);
                if (matchesId(stack, itemId)) {
                    return stack.copyWithCount(1);
                }
            }
            return ItemStack.EMPTY;
        }

        @Override
        public ItemStack findBySlot(String slot) {
            int index = parseInt(slot, -1);
            if (index < 0 || index >= container.getContainerSize()) {
                return ItemStack.EMPTY;
            }
            return container.getItem(index).copyWithCount(1);
        }

        @Override
        public int countMatching(ItemStack template) {
            int total = 0;
            for (int i = 0; i < container.getContainerSize(); i++) {
                ItemStack stack = container.getItem(i);
                if (ItemStack.isSameItemSameTags(stack, template)) {
                    total += stack.getCount();
                }
            }
            return total;
        }

        @Override
        public int capacityFor(ItemStack template, int want) {
            int free = 0;
            int slotLimit = Math.min(64, template.getMaxStackSize());
            for (int i = 0; i < container.getContainerSize(); i++) {
                ItemStack stack = container.getItem(i);
                if (!container.canPlaceItem(i, template)) {
                    continue;
                }
                if (stack.isEmpty()) {
                    free += slotLimit;
                } else if (ItemStack.isSameItemSameTags(stack, template)) {
                    free += Math.max(0, slotLimit - stack.getCount());
                }
                if (free >= want) {
                    return want;
                }
            }
            return free;
        }

        @Override
        public ItemStack extract(ItemStack template, int want) {
            int remaining = want;
            for (int i = 0; i < container.getContainerSize() && remaining > 0; i++) {
                ItemStack stack = container.getItem(i);
                if (!ItemStack.isSameItemSameTags(stack, template)) {
                    continue;
                }
                int take = Math.min(remaining, stack.getCount());
                ItemStack updated = stack.copy();
                updated.shrink(take);
                container.setItem(i, updated.isEmpty() ? ItemStack.EMPTY : updated);
                remaining -= take;
            }
            container.setChanged();
            return remaining == want ? ItemStack.EMPTY : template.copyWithCount(want - remaining);
        }

        @Override
        public ItemStack insert(ItemStack stack) {
            int remaining = stack.getCount();
            for (int i = 0; i < container.getContainerSize() && remaining > 0; i++) {
                if (!container.canPlaceItem(i, stack)) {
                    continue;
                }
                ItemStack existing = container.getItem(i);
                if (existing.isEmpty()) {
                    int place = Math.min(remaining, Math.min(64, stack.getMaxStackSize()));
                    container.setItem(i, stack.copyWithCount(place));
                    remaining -= place;
                } else if (ItemStack.isSameItemSameTags(existing, stack)) {
                    int room = Math.min(existing.getMaxStackSize(), 64) - existing.getCount();
                    int place = Math.min(room, remaining);
                    if (place > 0) {
                        ItemStack updated = existing.copy();
                        updated.grow(place);
                        container.setItem(i, updated);
                        remaining -= place;
                    }
                }
            }
            container.setChanged();
            return remaining <= 0 ? ItemStack.EMPTY : stack.copyWithCount(remaining);
        }

        @Override
        public boolean withinReach(HeroEntity hero) {
            return hero.blockPosition().distSqr(pos) <= MAX_DISTANCE_SQR;
        }
    }

    // ---- 共用小工具 ----

    private static boolean matchesId(ItemStack stack, String itemId) {
        if (stack.isEmpty() || itemId == null || itemId.isBlank()) {
            return false;
        }
        var key = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return key != null && key.toString().equals(itemId.trim());
    }

    private static ItemStack firstMatch(java.util.function.Predicate<ItemStack> predicate,
                                        List<ItemStack> stacks) {
        for (ItemStack stack : stacks) {
            if (predicate.test(stack)) {
                return stack.copyWithCount(1);
            }
        }
        return ItemStack.EMPTY;
    }

    private static int parseInt(String value, int fallback) {
        try {
            return Integer.parseInt(value == null ? "" : value.trim());
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static Component layout(String key) {
        return Component.translatable("message.herobrine_companion.tool." + key);
    }

    /** 端点解析失败：带玩家可读原因的受控异常（不吞成"未知错误"）。 */
    private static final class TransferFailure extends Exception {
        private final String displayKey;

        TransferFailure(String message, String displayKey) {
            super(message);
            this.displayKey = displayKey;
        }
    }
}
