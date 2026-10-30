package com.whitecloud233.modid.herobrine_companion.client.event;

import com.whitecloud233.modid.herobrine_companion.HerobrineCompanion;
import com.whitecloud233.modid.herobrine_companion.client.tts.TtsSpeechService;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * TTS 生命周期（仅客户端）：离开世界时立刻闭麦。
 *
 * <p>单独成类，让"语音何时该停"归语音模块自己管，聊天处理器不承担这个职责。</p>
 */
@Mod.EventBusSubscriber(modid = HerobrineCompanion.MODID, value = Dist.CLIENT)
public final class TtsClientEvents {

    private TtsClientEvents() {
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        TtsSpeechService.getInstance().stop();
    }
}
