package com.whitecloud233.modid.herobrine_companion.item;

import com.whitecloud233.modid.herobrine_companion.entity.gift.HeroBirthdayCakeService;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import com.whitecloud233.modid.herobrine_companion.network.BirthdayCakePlacePacket;
import com.whitecloud233.modid.herobrine_companion.network.PacketHandler;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 无名之蛋糕 —— Bedrock `herobrine_companion:birthday_cake` 的移植。
 *
 * <p>原版定义:食物(营养 20 / 饱和极高 / 可随时进食)、不可堆叠、稀有度 uncommon、
 * use_duration 32 tick、附多行 lore(netease:customtips)。生日仪式上祂回赠的纪念物,
 * 也是玩家可以真的吃掉的一块蛋糕。
 *
 * <p>三种行为(与 Bedrock 的 hero_birthday_cake_service / hero_birthday_cake_place_service 对齐):
 * <ul>
 *   <li><b>潜行 + 使用</b> → 把蛋糕摆到地上,对同一格再用一次收回(见 {@link HeroBirthdayCakeService});</li>
 *   <li><b>直接使用</b> → 正常进食;冷却内会被取消并提示(蛋糕不浪费、不加饱食度);</li>
 *   <li><b>吃完</b> → 蛋糕永不消失(本物品原样保留)+ 冷却外随机回赠一件物品与 30 分钟祝福。</li>
 * </ul>
 */
public class BirthdayCakeItem extends Item {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** Bedrock use_duration(32 tick = 1.6 秒)。 */
    public static final int USE_DURATION_TICKS = 32;

    private static final String TOOLTIP_KEY = "tooltip.herobrine_companion.birthday_cake";

    public BirthdayCakeItem(Properties properties) {
        super(properties);
    }

    @Override
    public int getUseDuration(ItemStack stack) {
        return USE_DURATION_TICKS;
    }

    /** 潜行 + 右键空气 → 摆在脚边/收回;否则交回原版进食流程。 */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        LOGGER.info("[HeroGift] cake use: player={} sneaking={} held={}",
                player.getName().getString(), player.isShiftKeyDown(), held.getCount());
        if (player.isShiftKeyDown()) {
            // 摆放/收回一律走网络包:原版物品冷却期间不会调用 use/useOn(见 BirthdayCakePlacePacket)
            if (level.isClientSide) {
                PacketHandler.sendToServer(new BirthdayCakePlacePacket(null, null));
            }
            return InteractionResultHolder.sidedSuccess(held, level.isClientSide);
        }
        // 冷却内不允许开始进食(等价 Bedrock ServerItemTryUse 的 cancel + 提示)
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer
                && HeroBirthdayCakeService.isEatCooldownActive(serverPlayer)) {
            HeroBirthdayCakeService.notifyEatCooldown(serverPlayer);
            return InteractionResultHolder.fail(held);
        }
        return super.use(level, player, hand);
    }

    /** 潜行 + 右键方块 → 摆在方块顶面/点击面外侧/脚边,或对同一格收回。 */
    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        if (player != null && player.isShiftKeyDown()) {
            // 摆放/收回一律走网络包(携带精确坐标),与服务端物品冷却门禁解耦
            if (context.getLevel().isClientSide) {
                PacketHandler.sendToServer(new BirthdayCakePlacePacket(context.getClickedPos(), context.getClickedFace()));
            }
            return InteractionResult.sidedSuccess(context.getLevel().isClientSide);
        }
        return super.useOn(context);
    }

    /**
     * 进食完成:蛋糕原样保留(等价 Bedrock「吃完立刻补回、蛋糕永不消失」),
     * 并交给服务发奖 / 上祝福 / 记冷却。
     */
    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        ItemStack leftover = super.finishUsingItem(stack, level, entity);
        if (!level.isClientSide && entity instanceof ServerPlayer serverPlayer) {
            HeroBirthdayCakeService.onEaten(serverPlayer);
        }
        // 原版会消耗一份;这里返回一份新的蛋糕,保证它永不消失
        return new ItemStack(this);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        String raw = Component.translatable(TOOLTIP_KEY).getString();
        if (raw == null || raw.isBlank() || raw.equals(TOOLTIP_KEY)) {
            return;
        }
        for (String line : raw.split("\n")) {
            if (!line.isEmpty()) {
                tooltip.add(Component.literal(line));
            }
        }
    }
}