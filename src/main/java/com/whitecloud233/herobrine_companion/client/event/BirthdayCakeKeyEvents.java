package com.whitecloud233.herobrine_companion.client.event;

import com.whitecloud233.herobrine_companion.HerobrineCompanion;
import com.whitecloud233.herobrine_companion.item.BirthdayCakeItem;
import com.whitecloud233.herobrine_companion.network.BirthdayCakePlacePacket;
import com.whitecloud233.herobrine_companion.network.PacketHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.InputEvent;

/**
 * 无名之蛋糕的摆放/收回按键拦截(客户端)。
 *
 * <p><b>为什么必须在输入事件层做</b>:原版物品冷却门禁存在于两处 ——
 * 服务端 {@code ServerPlayerGameMode#useItem/useItemOn} 与客户端 {@code MultiPlayerGameMode}
 * (反汇编可见 {@code ItemCooldowns.isOnCooldown})。也就是说物品一旦带原版冷却,
 * **两端都不会调用物品自身的 use/useOn**,任何挂在物品上的摆放逻辑在冷却期间都不可能执行。
 *
 * <p>本类在 {@code InteractionKeyMappingTriggered}(原版开始处理"使用键"之前)拦截:
 * 潜行 + 手持蛋糕时取消原版右键处理并发包给服务端,由服务端权威完成摆放/收回。
 * 这样冷却只影响"能不能吃",不影响"能不能摆"。
 */
@EventBusSubscriber(modid = HerobrineCompanion.MODID, value = Dist.CLIENT)
public final class BirthdayCakeKeyEvents {

    private BirthdayCakeKeyEvents() {
    }

    @SubscribeEvent
    public static void onInteractionKey(InputEvent.InteractionKeyMappingTriggered event) {
        if (!event.isUseItem()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || !player.isShiftKeyDown()) {
            return;
        }
        if (!(player.getMainHandItem().getItem() instanceof BirthdayCakeItem)) {
            return;
        }
        // 抢在原版物品使用流程(含其冷却门禁)之前处理
        event.setCanceled(true);
        event.setSwingHand(false);
        BlockPos pos = null;
        Direction face = null;
        if (minecraft.hitResult != null && minecraft.hitResult.getType() == HitResult.Type.BLOCK) {
            BlockHitResult blockHit = (BlockHitResult) minecraft.hitResult;
            pos = blockHit.getBlockPos();
            face = blockHit.getDirection();
        }
        PacketHandler.sendToServer(new BirthdayCakePlacePacket(pos, face));
    }
}