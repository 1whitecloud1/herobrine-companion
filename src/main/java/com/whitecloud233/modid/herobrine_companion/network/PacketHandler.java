package com.whitecloud233.modid.herobrine_companion.network;

import com.whitecloud233.modid.herobrine_companion.HerobrineCompanion;
import com.whitecloud233.modid.herobrine_companion.compat.cooking.OpenCookSelectionPacket;
import com.whitecloud233.modid.herobrine_companion.compat.cooking.SelectCookOptionPacket;
import com.whitecloud233.modid.herobrine_companion.destructiongod.network.DestructionGodFaultSplitPacket;
import com.whitecloud233.modid.herobrine_companion.destructiongod.network.DestructionGodLightningArcPacket;
import com.whitecloud233.modid.herobrine_companion.destructiongod.network.DestructionGodLightningPacket;
import com.whitecloud233.modid.herobrine_companion.destructiongod.network.DestructionGodOrbPacket;
import com.whitecloud233.modid.herobrine_companion.destructiongod.network.DestructionGodThunderSkyNetPacket;
import com.whitecloud233.modid.herobrine_companion.destructiongod.network.SPacketWorldRendCinematic;
import com.whitecloud233.modid.herobrine_companion.fight.network.CPacketCollapseFinished;
import com.whitecloud233.modid.herobrine_companion.fight.network.SPacketChallengeArenaSlice;
import com.whitecloud233.modid.herobrine_companion.fight.network.SPacketFakeCrash;
import com.whitecloud233.modid.herobrine_companion.fight.network.SPacketStartCollapse;
import com.whitecloud233.modid.herobrine_companion.network.ai.ActorDialoguePromptPacket;
import com.whitecloud233.modid.herobrine_companion.network.ai.ActorDialogueResultPacket;
import com.whitecloud233.modid.herobrine_companion.network.ai.AppendCrossChatHistoryPacket;
import com.whitecloud233.modid.herobrine_companion.network.ai.CloseCrossChatSessionPacket;
import com.whitecloud233.modid.herobrine_companion.network.ai.HeroCrossChatPromptPacket;
import com.whitecloud233.modid.herobrine_companion.network.ai.HeroCrossChatResultPacket;
import com.whitecloud233.modid.herobrine_companion.network.ai.OpenCrossChatInvitePacket;
import com.whitecloud233.modid.herobrine_companion.network.ai.OpenCrossSessionHubPacket;
import com.whitecloud233.modid.herobrine_companion.network.ai.OpenHeroChatPacket;
import com.whitecloud233.modid.herobrine_companion.network.ai.PresentCrossChatAiLinePacket;
import com.whitecloud233.modid.herobrine_companion.network.ai.RequestCrossChatSessionPacket;
import com.whitecloud233.modid.herobrine_companion.network.ai.RespondCrossChatInvitePacket;
import com.whitecloud233.modid.herobrine_companion.network.ai.SendCrossChatHbMessagePacket;
import com.whitecloud233.modid.herobrine_companion.network.ai.SendCrossChatMessagePacket;
import com.whitecloud233.modid.herobrine_companion.network.ai.SetCrossChatAutoChatPacket;
import com.whitecloud233.modid.herobrine_companion.network.ai.SetCrossChatAutoTurnLimitPacket;
import com.whitecloud233.modid.herobrine_companion.network.ai.SetCrossChatPermissionPacket;
import com.whitecloud233.modid.herobrine_companion.network.ai.SyncCrossChatStatePacket;
import com.whitecloud233.modid.herobrine_companion.network.ai.UpdateClientLanguagePacket;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;

public class PacketHandler {
    private static final String PROTOCOL_VERSION = "1";
    public static final SimpleChannel INSTANCE = NetworkRegistry.newSimpleChannel(
            ResourceLocation.tryParse(HerobrineCompanion.MODID + ":main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals
    );

    /**
     * 已分配的 wire ID → 包类型。用于在加载期检出 ID 冲突。
     * Forge 的 SimpleChannel 按 index 注册,冲突会静默覆盖,这里改为加载即失败。
     * <p>
     * 注意:ID 由注册顺序决定(首个包为 0,依次 +1)。顺序一字不能改,
     * 否则会破坏与已发布版本的联机协议兼容。
     */
    private static final Map<Integer, Class<?>> REGISTERED_IDS = new HashMap<>();

    public static void register() {
        AtomicInteger id = new AtomicInteger(0);

        // ---------- 核心交互(C→S 指令 / S→C 同步) ----------
        reg(id, PeacefulPacket.class, PeacefulPacket::encode, PeacefulPacket::new, PeacefulPacket::handle);
        reg(id, ContractPacket.class, ContractPacket::encode, ContractPacket::new, ContractPacket::handle);
        reg(id, ClearAreaPacket.class, ClearAreaPacket::encode, ClearAreaPacket::new, ClearAreaPacket::handle);
        reg(id, OpenTradePacket.class, OpenTradePacket::encode, OpenTradePacket::new, OpenTradePacket::handle);
        reg(id, ToggleCompanionPacket.class, ToggleCompanionPacket::encode, ToggleCompanionPacket::new, ToggleCompanionPacket::handle);
        reg(id, SyncHeroVisitPacket.class, SyncHeroVisitPacket::encode, SyncHeroVisitPacket::new, SyncHeroVisitPacket::handle);
        reg(id, RequestActionPacket.class, RequestActionPacket::encode, RequestActionPacket::new, RequestActionPacket::handle);
        reg(id, DesolateAreaPacket.class, DesolateAreaPacket::encode, DesolateAreaPacket::new, DesolateAreaPacket::handle);
        reg(id, FlattenAreaPacket.class, FlattenAreaPacket::encode, FlattenAreaPacket::new, FlattenAreaPacket::handle);
        reg(id, ClaimRewardPacket.class, ClaimRewardPacket::encode, ClaimRewardPacket::new, ClaimRewardPacket::handle);
        reg(id, ToggleSkinPacket.class, ToggleSkinPacket::encode, ToggleSkinPacket::new, ToggleSkinPacket::handle);
        reg(id, SyncHeroCosmeticsPacket.class, SyncHeroCosmeticsPacket::encode, SyncHeroCosmeticsPacket::new, SyncHeroCosmeticsPacket::handle);
        reg(id, SyncRewardsPacket.class, SyncRewardsPacket::encode, SyncRewardsPacket::new, SyncRewardsPacket::handle);
        reg(id, TriggerEternalOathPacket.class, TriggerEternalOathPacket::encode, TriggerEternalOathPacket::new, TriggerEternalOathPacket::handle);
        reg(id, CleaveSkillPacket.class, CleaveSkillPacket::encode, CleaveSkillPacket::new, CleaveSkillPacket::handle);
        reg(id, OpenWardrobePacket.class, OpenWardrobePacket::encode, OpenWardrobePacket::new, OpenWardrobePacket::handle);

        // ---------- 挑战模式 / 战斗演出(S→C FX) ----------
        reg(id, StartChallengePacket.class, StartChallengePacket::encode, StartChallengePacket::new, StartChallengePacket::handle);
        reg(id, PaleLightningPacket.class, PaleLightningPacket::encode, PaleLightningPacket::new, PaleLightningPacket::handle);
        reg(id, PaleLightningArcPacket.class, PaleLightningArcPacket::encode, PaleLightningArcPacket::new, PaleLightningArcPacket::handle);
        reg(id, ChallengeAfterimagePacket.class, ChallengeAfterimagePacket::encode, ChallengeAfterimagePacket::new, ChallengeAfterimagePacket::handle);
        reg(id, DestructionGodLightningPacket.class, DestructionGodLightningPacket::encode, DestructionGodLightningPacket::new, DestructionGodLightningPacket::handle);
        reg(id, DestructionGodLightningArcPacket.class, DestructionGodLightningArcPacket::encode, DestructionGodLightningArcPacket::new, DestructionGodLightningArcPacket::handle);
        reg(id, DestructionGodOrbPacket.class, DestructionGodOrbPacket::encode, DestructionGodOrbPacket::new, DestructionGodOrbPacket::handle);
        reg(id, DestructionGodThunderSkyNetPacket.class, DestructionGodThunderSkyNetPacket::encode, DestructionGodThunderSkyNetPacket::new, DestructionGodThunderSkyNetPacket::handle);
        reg(id, DestructionGodFaultSplitPacket.class, DestructionGodFaultSplitPacket::encode, DestructionGodFaultSplitPacket::new, DestructionGodFaultSplitPacket::handle);
        reg(id, SavePosePacket.class, SavePosePacket::encode, SavePosePacket::new, SavePosePacket::handle);

        // ---------- AI 观察 / 指令 ----------
        reg(id, AIObservationPacket.class, AIObservationPacket::encode, AIObservationPacket::new, AIObservationPacket::handle);
        reg(id, HeroAIActionPacket.class, HeroAIActionPacket::encode, HeroAIActionPacket::new, HeroAIActionPacket::handle);
        reg(id, ToggleBattleModePacket.class, ToggleBattleModePacket::encode, ToggleBattleModePacket::new, ToggleBattleModePacket::handle);

        // ---------- 演出:世界崩坏 / 假死机 / 灭世演出(C→S 与 S→C) ----------
        reg(id, SPacketStartCollapse.class, SPacketStartCollapse::encode, SPacketStartCollapse::new, SPacketStartCollapse::handle);
        reg(id, SPacketFakeCrash.class, SPacketFakeCrash::encode, SPacketFakeCrash::new, SPacketFakeCrash::handle);
        reg(id, SPacketWorldRendCinematic.class, SPacketWorldRendCinematic::encode, SPacketWorldRendCinematic::new, SPacketWorldRendCinematic::handle);
        reg(id, CPacketCollapseFinished.class, CPacketCollapseFinished::encode, CPacketCollapseFinished::new, CPacketCollapseFinished::handle);

        // ---------- 召唤 / 传送 / 惩罚(C→S) ----------
        reg(id, SummonHeroPacket.class, SummonHeroPacket::encode, SummonHeroPacket::new, SummonHeroPacket::handle);
        reg(id, TeleportToHeroPacket.class, TeleportToHeroPacket::encode, TeleportToHeroPacket::new, TeleportToHeroPacket::handle);
        reg(id, HeroPunishmentPacket.class, HeroPunishmentPacket::encode, HeroPunishmentPacket::new, HeroPunishmentPacket::handle);

        // ---------- 烹饪兼容 ----------
        reg(id, OpenCookSelectionPacket.class, OpenCookSelectionPacket::encode, OpenCookSelectionPacket::new, OpenCookSelectionPacket::handle);
        reg(id, SelectCookOptionPacket.class, SelectCookOptionPacket::encode, SelectCookOptionPacket::new, SelectCookOptionPacket::handle);

        // ---------- AI 对话 / 跨服聊天(S→C 指令, C→S 结果) ----------
        reg(id, OpenHeroChatPacket.class, OpenHeroChatPacket::encode, OpenHeroChatPacket::new, OpenHeroChatPacket::handle);
        reg(id, UpdateClientLanguagePacket.class, UpdateClientLanguagePacket::encode, UpdateClientLanguagePacket::new, UpdateClientLanguagePacket::handle);
        reg(id, AppendCrossChatHistoryPacket.class, AppendCrossChatHistoryPacket::encode, AppendCrossChatHistoryPacket::new, AppendCrossChatHistoryPacket::handle);
        reg(id, PresentCrossChatAiLinePacket.class, PresentCrossChatAiLinePacket::encode, PresentCrossChatAiLinePacket::new, PresentCrossChatAiLinePacket::handle);
        reg(id, SyncCrossChatStatePacket.class, SyncCrossChatStatePacket::encode, SyncCrossChatStatePacket::new, SyncCrossChatStatePacket::handle);
        reg(id, OpenCrossChatInvitePacket.class, OpenCrossChatInvitePacket::encode, OpenCrossChatInvitePacket::new, OpenCrossChatInvitePacket::handle);
        reg(id, OpenCrossSessionHubPacket.class, OpenCrossSessionHubPacket::encode, OpenCrossSessionHubPacket::new, OpenCrossSessionHubPacket::handle);
        reg(id, RequestCrossChatSessionPacket.class, RequestCrossChatSessionPacket::encode, RequestCrossChatSessionPacket::new, RequestCrossChatSessionPacket::handle);
        reg(id, RespondCrossChatInvitePacket.class, RespondCrossChatInvitePacket::encode, RespondCrossChatInvitePacket::new, RespondCrossChatInvitePacket::handle);
        reg(id, SetCrossChatPermissionPacket.class, SetCrossChatPermissionPacket::encode, SetCrossChatPermissionPacket::new, SetCrossChatPermissionPacket::handle);
        reg(id, SetCrossChatAutoChatPacket.class, SetCrossChatAutoChatPacket::encode, SetCrossChatAutoChatPacket::new, SetCrossChatAutoChatPacket::handle);
        reg(id, SetCrossChatAutoTurnLimitPacket.class, SetCrossChatAutoTurnLimitPacket::encode, SetCrossChatAutoTurnLimitPacket::new, SetCrossChatAutoTurnLimitPacket::handle);
        reg(id, SendCrossChatMessagePacket.class, SendCrossChatMessagePacket::encode, SendCrossChatMessagePacket::new, SendCrossChatMessagePacket::handle);
        reg(id, SendCrossChatHbMessagePacket.class, SendCrossChatHbMessagePacket::encode, SendCrossChatHbMessagePacket::new, SendCrossChatHbMessagePacket::handle);
        reg(id, CloseCrossChatSessionPacket.class, CloseCrossChatSessionPacket::encode, CloseCrossChatSessionPacket::new, CloseCrossChatSessionPacket::handle);
        reg(id, ActorDialoguePromptPacket.class, ActorDialoguePromptPacket::encode, ActorDialoguePromptPacket::new, ActorDialoguePromptPacket::handle);
        reg(id, ActorDialogueResultPacket.class, ActorDialogueResultPacket::encode, ActorDialogueResultPacket::new, ActorDialogueResultPacket::handle);
        reg(id, HeroCrossChatPromptPacket.class, HeroCrossChatPromptPacket::encode, HeroCrossChatPromptPacket::new, HeroCrossChatPromptPacket::handle);
        reg(id, HeroCrossChatResultPacket.class, HeroCrossChatResultPacket::encode, HeroCrossChatResultPacket::new, HeroCrossChatResultPacket::handle);

        // ---------- 骑乘输入 / 竞技场切片 ----------
        reg(id, JeanMountInputPacket.class, JeanMountInputPacket::encode, JeanMountInputPacket::new, JeanMountInputPacket::handle);
        reg(id, SPacketChallengeArenaSlice.class, SPacketChallengeArenaSlice::encode, SPacketChallengeArenaSlice::new, SPacketChallengeArenaSlice::handle);
    }

    /**
     * 注册一个包,并在加载期校验 wire ID 安全:
     * <ul>
     *   <li>ID 必须严格自增(漏写一次增量立刻抛异常,避免错位);</li>
     *   <li>同一 ID 不能重复分配给不同包(把 SimpleChannel 的静默覆盖变成加载即失败)。</li>
     * </ul>
     */
    private static <MSG> void reg(AtomicInteger id,
                                  Class<MSG> type,
                                  BiConsumer<MSG, FriendlyByteBuf> encoder,
                                  Function<FriendlyByteBuf, MSG> decoder,
                                  BiConsumer<MSG, Supplier<NetworkEvent.Context>> handler) {
        int current = id.get();
        int expected = REGISTERED_IDS.size();
        if (current != expected) {
            throw new IllegalStateException("Packet ID drift while registering "
                    + type.getSimpleName() + ": id=" + current + ", expected " + expected
                    + ". A registerMessage call likely forgot to advance the id counter.");
        }
        Class<?> existing = REGISTERED_IDS.putIfAbsent(current, type);
        if (existing != null) {
            throw new IllegalStateException("Duplicate packet id " + current + " for "
                    + type.getSimpleName() + " (already assigned to " + existing.getSimpleName() + ")");
        }
        INSTANCE.registerMessage(current, type, encoder, decoder, handler);
        id.incrementAndGet();
    }



    public static void sendToServer(Object packet) {
        INSTANCE.send(PacketDistributor.SERVER.noArg(), packet);
    }

    public static void sendToPlayer(Object packet, ServerPlayer player) {
        INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), packet);
    }
    // [新增] 发送给所有能看到某个实体的玩家 (非常适合 Boss 技能)
    public static void sendToTracking(Object packet, Entity entity) {
        INSTANCE.send(PacketDistributor.TRACKING_ENTITY.with(() -> entity), packet);
    }
}
