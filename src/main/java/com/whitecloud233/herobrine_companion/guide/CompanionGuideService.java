package com.whitecloud233.herobrine_companion.guide;

import com.whitecloud233.herobrine_companion.entity.HeroEntity;
import com.whitecloud233.herobrine_companion.entity.ai.learning.SimpleNeuralNetwork;
import com.whitecloud233.herobrine_companion.util.HeroMenuValidity;
import com.whitecloud233.herobrine_companion.world.structure.ModStructures;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import net.neoforged.neoforge.items.wrapper.PlayerMainInvWrapper;

/**
 * 「首次打开主页面时发放同行手册」的服务端逻辑 —— 1.21.1 NeoForge 版本。
 *
 * <h2>与 1.20.1 Forge 版的关键差异：NBT 字符串 → 数据组件</h2>
 * 旧版（Patchouli 1.20.x）靠写物品 NBT 来指定书本：
 * <pre>
 * guide.getOrCreateTag().putString("patchouli:book", BOOK_ID);   // 1.20.1，1.21.1 上已失效
 * </pre>
 * 1.21 起 {@code ItemStack.getOrCreateTag()} 与 {@code getTag()} 已被移除，Patchouli 改用
 * 注册名为 {@code patchouli:book} 的 <b>数据组件</b>，类型是
 * {@code DataComponentType<ResourceLocation>}（见 Patchouli 1.21.x 的
 * {@code vazkii.patchouli.common.item.PatchouliDataComponents#BOOK}），物品本体仍是
 * {@code patchouli:guide_book}。写入方式是：
 * <pre>
 * stack.set(PatchouliDataComponents.BOOK, bookId);   // bookId 直接是 ResourceLocation，不是字符串
 * </pre>
 * 注意两点：组件值是 {@link ResourceLocation} 而不是 String；组件未设置时 {@code getBook}
 * 返回 null，Patchouli 会把这本书显示成「未定义」，右键也不会打开。
 *
 * <h2>为什么用注册表按 ID 取组件，而不是直接 import Patchouli 的类</h2>
 * 沿用在 1.20.1 版里确立的原则（"Registry lookup keeps Patchouli optional and avoids
 * linking its classes on the server"）：本模组对 Patchouli 是<b>软依赖</b>，
 * 未安装时不能因为类缺失而抛 {@code NoClassDefFoundError}。1.21.1 的数据组件是
 * 注册表对象，因此可以像取物品一样按 {@link ResourceLocation} 查出来——
 * 全程不出现任何 {@code vazkii.patchouli.*} 符号，构建期也就不需要 Patchouli 依赖。
 *
 * <p>{@code patchouli:book} 组件本身带 {@code persistent} 与 {@code networkSynchronized}，
 * 所以把 {@code guide_book} 直接丢进玩家背包后，客户端收到的物品同样带着本书 ID，
 * 无需额外同步。</p>
 */
public final class CompanionGuideService {

    /** 玩家持久化数据里的「已发放」标记；重生时由 PlayerLifecycleHandler 一并继承。 */
    public static final String RECEIVED_TAG = "HasReceivedCompanionGuide";

    /** Patchouli 的手册物品本体，注册名在 1.21.x 没有变化。 */
    private static final ResourceLocation GUIDE_ITEM = ResourceLocation.fromNamespaceAndPath("patchouli", "guide_book");

    /** 书本 ID 与 {@code data/herobrine_companion/patchouli_books/companion_guide/book.json} 对应。 */
    private static final ResourceLocation BOOK_COMPONENT = ResourceLocation.fromNamespaceAndPath("patchouli", "book");

    public static final ResourceLocation BOOK_ID =
            ResourceLocation.fromNamespaceAndPath("herobrine_companion", "companion_guide");

    private CompanionGuideService() {
    }

    /**
     * 构造一本指向本模组同行手册的 Patchouli 书本。
     *
     * @return 未安装 Patchouli（物品或数据组件缺失）时返回 {@link ItemStack#EMPTY}
     */
    public static ItemStack createGuideStack() {
        Item item = BuiltInRegistries.ITEM.get(GUIDE_ITEM);
        if (item == null || item == Items.AIR) {
            return ItemStack.EMPTY;
        }
        DataComponentType<ResourceLocation> bookComponent = resolveBookComponent();
        if (bookComponent == null) {
            return ItemStack.EMPTY;
        }
        ItemStack guide = new ItemStack(item);
        try {
            // 1.21.1 的写法；等价于旧版的 getOrCreateTag().putString("patchouli:book", ...)。
            guide.set(bookComponent, BOOK_ID);
            // 回读自检：泛型在字节码里被擦除，若 Patchouli 将来把该组件的值类型改成了
            // 非 ResourceLocation，set 本身不会报错，只有读回来才会暴露。这里当场读写一遍，
            // 把「静默写坏一本书」变成「本次不发放」。
            if (!BOOK_ID.equals(guide.get(bookComponent))) {
                return ItemStack.EMPTY;
            }
        } catch (RuntimeException ex) {
            // ClassCastException / IllegalArgumentException 等都收敛成「未就绪」，不打断交互流程。
            return ItemStack.EMPTY;
        }
        return guide;
    }

    /**
     * 按注册名取 {@code patchouli:book} 数据组件。
     *
     * <p>注册表里的组件是 {@code DataComponentType<?>}，Patchouli 声明时用的就是
     * {@code ResourceLocation} 作为值类型，这里做一次收窄转换即可；若 Patchouli 缺席
     * 或将来改掉了值类型，则会返回 null 而不是在别处炸成 {@code ClassCastException}。</p>
     */
    @SuppressWarnings("unchecked")
    private static DataComponentType<ResourceLocation> resolveBookComponent() {
        DataComponentType<?> type = BuiltInRegistries.DATA_COMPONENT_TYPE.get(BOOK_COMPONENT);
        if (type == null) {
            return null;
        }
        return (DataComponentType<ResourceLocation>) type;
    }

    /** 该玩家是否已经领过手册。 */
    public static boolean hasReceived(ServerPlayer player) {
        return player != null && player.getPersistentData().getBoolean(RECEIVED_TAG);
    }

    /**
     * 玩家成功打开 Herobrine 主页面后的发放入口。每位玩家只发一次。
     *
     * @return 本次是否真的发出了手册
     */
    public static boolean grantOnFirstOpen(ServerPlayer player, int heroId) {
        if (player == null || !player.isAlive() || hasReceived(player)) {
            return false;
        }
        if (!(player.level().getEntity(heroId) instanceof HeroEntity hero)
                || !HeroMenuValidity.isHeroInPlayerLoadedArea(hero, player) || !player.getUUID().equals(hero.getOwnerUUID())
                || hero.isChallengeActiveState()
                || hero.level().dimension() == ModStructures.END_RING_DIMENSION_KEY
                || hero.getHeroBrain().getState() == SimpleNeuralNetwork.MindState.JUDGE) {
            return false;
        }

        ItemStack guide = createGuideStack();
        if (guide.isEmpty()) {
            // 未安装 Patchouli：不发放，也不写标记，装好之后仍能在下次交互时补发。
            return false;
        }

        // 创造模式下 Inventory.add 会直接丢弃溢出物，因此两条路线都走物品处理器。
        ItemStack remainder = ItemHandlerHelper.insertItemStacked(new PlayerMainInvWrapper(player.getInventory()), guide, false);
        if (!remainder.isEmpty() && player.drop(remainder, false) == null) {
            return false;
        }
        player.getPersistentData().putBoolean(RECEIVED_TAG, true);
        player.inventoryMenu.broadcastChanges();
        return true;
    }
}
