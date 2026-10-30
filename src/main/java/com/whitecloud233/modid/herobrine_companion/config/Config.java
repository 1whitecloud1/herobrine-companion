package com.whitecloud233.modid.herobrine_companion.config;

import com.mojang.logging.LogUtils;
import com.whitecloud233.modid.herobrine_companion.HerobrineCompanion;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import org.slf4j.Logger;

@EventBusSubscriber(modid = HerobrineCompanion.MODID, bus = EventBusSubscriber.Bus.MOD)
public class Config {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

    public static final ForgeConfigSpec.BooleanValue POEM_OF_THE_END_EXPLOSION = BUILDER
            .comment("Whether the Poem of the End weapon causes explosions in its broken state")
            .comment("终末之诗在破境状态下是否启用爆炸")
            .define("poemOfTheEndExplosion_v2", false);

    // 独立动作总开关：关闭后立即回到原版持械动作（不再播放独立连招/刀光/待机跑步），
    // 同时服务端恢复原版近战判定。运行期可改，无需重启。
    public static final ForgeConfigSpec.BooleanValue POEM_ACTIONS_ENABLED = BUILDER
            .comment("Whether the standalone Poem of the End action set is enabled (combos, blade trails, idle/run poses)")
            .comment("终末之诗独立动作总开关（连招、刀光、待机/跑步动作）。关闭后立即恢复原版持械动作与近战，无需重启")
            .define("poemActionsEnabled", true);

    public static final ForgeConfigSpec.BooleanValue POEM_OUTLINE_ENABLED = BUILDER
            .comment("Render the Poem of the End silhouette outline on this client. Changes apply immediately; glow and blade trails are unaffected.")
            .comment("本机终末之诗描边开关。关闭后跳过描边模型、遮罩与合成，立即生效；保留刀刃发光和攻击刀光，无需 OP 权限")
            .define("poemOutlineEnabled", true);

    public static final ForgeConfigSpec.BooleanValue HERO_KING_AURA_ENABLED = BUILDER
            .comment("Whether the Herobrine King Aura goal is enabled for Herobrine")
            .comment("是否启用 Herobrine 的怪物臣服AI")
            .define("heroKingAuraEnabled_v4", false);

    public static final ForgeConfigSpec.BooleanValue HERO_BLOCK_RESTORATION = BUILDER
            .comment("Whether Herobrine automatically restores broken blocks")
            .comment("Herobrine是否自动修复被破坏的方块")
            .define("heroBlockRestoration_v2", false);

    public static final ForgeConfigSpec.BooleanValue HERO_CLEAN_ITEMS = BUILDER
            .comment("Whether Herobrine automatically cleans up dropped items")
            .comment("Herobrine是否自动清理地上的掉落物")
            .define("heroCleanItems_v2", false);

    public static final ForgeConfigSpec.BooleanValue HERO_MEMORY_ENABLED = BUILDER
            .comment("Whether the Herobrine agent long-term memory and reflection learning are enabled")
            .comment("是否启用 Herobrine 长期记忆与反思学习")
            .define("heroMemoryEnabled", true);

    public static final ForgeConfigSpec.BooleanValue AGENT_TOOL_RESULT_FEEDBACK = BUILDER
            .comment("Whether agent tool execution results are fed back to the LLM via a synthesis call")
            .comment("agent 工具执行结果是否经合成调用回喂给 LLM")
            .define("agentToolResultFeedback", true);

    public static final ForgeConfigSpec.BooleanValue AGENT_TOOL_CONFIRMATION = BUILDER
            .comment("Whether agent tools requiring confirmation show an approval screen before executing")
            .comment("需要确认的 agent 工具是否在执行前弹出确认屏（关闭后视为已确认，回到无 UI 时代）")
            .define("agentToolConfirmation", true);

    public static final ForgeConfigSpec.BooleanValue HERO_AUTONOMY_ENABLED = BUILDER
            .comment("Whether the Herobrine agent may autonomously plan and execute real tasks (repair/inspect) on its own initiative")
            .comment("Herobrine agent 是否可自主规划并执行真实任务（修复/探查）")
            .define("heroAutonomyEnabled", true);

    public static final ForgeConfigSpec.BooleanValue HERO_SKILL_SESSION = BUILDER
            .comment("Whether the hero_use_skill tool tracks skill cooldown and auto-exits battle mode after a forced skill ends")
            .comment("hero_use_skill 工具是否启用技能冷却与强制技能结束后的战斗态退出")
            .define("heroSkillSession", true);

    public static final ForgeConfigSpec.BooleanValue HERO_LEAF_VANISH_ENABLED = BUILDER
            .comment("Whether Herobrine can vanish nearby tree leaves in Prankster state")
            .comment("Herobrine是否允许在恶作剧状态下拔掉附近树叶")
            .define("heroLeafVanishEnabled", true);

    public static final ForgeConfigSpec.BooleanValue PRANKSTER_FAKE_CHEST_ENABLED = BUILDER
            .comment("Whether Herobrine may place fake treasure chests (chest + torches) near the focus player in Prankster state")
            .comment("Herobrine是否允许在恶作剧状态下在玩家附近放置假宝箱（箱子+火把）")
            .define("pranksterFakeChestEnabled", true);

    public static final ForgeConfigSpec.BooleanValue CLEAVE_SKILL_ENABLED = BUILDER
            .comment("Whether the World Cleave skill (5 key) is enabled")
            .comment("是否启用镰刀的 5 键存档毁灭术")
            .define("cleaveSkillEnabled", false);

    // 毁灭之神的地形破坏行为已固定写死（神性破坏 / 可破容器 / 每刻 4096 块 / 允许终局世界崩塌），
    // 不再暴露为配置项，也不写进 toml。相关常量见 destructiongod.world.DestructionTuning。

    public static final ForgeConfigSpec.BooleanValue DEBUG_LOG_ENABLED = BUILDER
            .comment("Print mod debug/diagnostic logs ([CookDiag], [HeroEFDebug], [HeroGift], [HeroScythe], ...) into latest.log")
            .comment("是否把模组的调试/排障日志（[CookDiag]、[HeroEFDebug]、[HeroGift]、[HeroScythe] 等）输出到 latest.log。默认关闭，排障时再开")
            .define("debugLogEnabled", false);

    public static final ForgeConfigSpec.BooleanValue SOUL_BOUND_PACT_ENABLED = BUILDER
            .comment("Whether the Soul Bound Pact item is enabled")
            .comment("是否启用魂缚之契物品")
            .define("soulBoundPactEnabled_v2", true);

    public static final ForgeConfigSpec.BooleanValue ABYSSAL_GAZE_ENABLED = BUILDER
            .comment("Whether the Abyssal Gaze item is enabled")
            .comment("是否启用幽邃之视物品")
            .define("abyssalGazeEnabled_v2", true);

    public static final ForgeConfigSpec.BooleanValue TRANSCENDENCE_PERMIT_ENABLED = BUILDER
            .comment("Whether the Transcendence Permit item is enabled")
            .comment("是否启用凌越之允物品")
            .define("transcendencePermitEnabled_v2", true);

    // 【命名说明】配置键沿用 aiVisionEnabled 以兼容老配置文件；它在游戏里显示为「环境观察(AI生成)」，
    // 实际作用是决定环境观察台词是否由 AI（大模型）即时生成。要完全停止观察发话请用 aiVisionSpeechEnabled。
    public static final ForgeConfigSpec.BooleanValue AI_VISION_ENABLED = BUILDER
            .comment("Whether Herobrine's environment observation lines are generated live by the AI (LLM). When disabled, preset fallback lines are used instead (observation speech itself keeps running)")
            .comment("环境观察台词是否由 AI（大模型）即时生成。关闭后改用内置预设台词，观察发话本身仍在继续")
            .define("aiVisionEnabled", true);

    public static final ForgeConfigSpec.BooleanValue AWAKENED_MOB_AI_DIALOGUE_ENABLED = BUILDER
            .comment("Whether awakened monster AI dialogue is enabled")
            .comment("是否启用觉醒怪物AI对话")
            .define("awakenedMobAiDialogueEnabled", true);

    public static final ForgeConfigSpec.IntValue AWAKENED_DIALOGUE_MIN_INTERVAL = BUILDER
            .comment("The global minimum interval (in seconds) between ANY two awakened LLM dialogues (all speakers share one throttle), to avoid API rate limits")
            .comment("所有觉醒对话共享的最小 LLM 调用间隔（秒，任意说话者之间），防止触发 API 频率限制；冷却期间改用预设台词")
            .defineInRange("awakenedDialogueMinIntervalSeconds", 30, 5, 600);

    public static final ForgeConfigSpec.IntValue GIFT_DIALOGUE_MIN_INTERVAL = BUILDER
            .comment("Minimum interval (in seconds) between two GIFT (offer/托付) LLM dialogues. This channel is separate from the awakened-dialogue throttle, so Herobrine's gift reactions are never skipped because awakened mobs were talking. 0 = no throttle at all")
            .comment("赠礼（托付）LLM 台词的最小间隔（秒）。该通道与觉醒对话限流相互独立，因此不会因为觉醒生物在说话而被挤掉。0 = 完全不限制")
            .defineInRange("giftDialogueMinIntervalSeconds", 0, 0, 600);

    public static final ForgeConfigSpec.IntValue AWAKENED_WORLD_CAP = BUILDER
            .comment("Max total awakened mobs per dimension (world). Design doc 5.2 recommends 20 ~ 40. Awakened mobs keep their state until they die or leave the dimension; new awakenings are refused once the cap is reached")
            .comment("单世界（维度）觉醒怪物总量上限（设计文档 5.2 建议 20 ~ 40）。达到上限后不再产生新的觉醒怪；已觉醒怪物保持觉醒直到死亡或离开本维度")
            .defineInRange("awakenedWorldCap", 30, 20, 40);

    public static final ForgeConfigSpec.IntValue AWAKENED_PLAYER_NEAR_CAP = BUILDER
            .comment("Max awakened mobs simultaneously near a single player. Design doc 5.2 recommends 2 ~ 4")
            .comment("单个玩家附近同时存在的觉醒怪物数量上限（设计文档 5.2 建议 2 ~ 4）")
            .defineInRange("awakenedPlayerNearCap", 3, 2, 4);

    public static final ForgeConfigSpec.IntValue AWAKENED_PLAYER_NEAR_RADIUS = BUILDER
            .comment("Radius (in blocks) around a player used to count nearby awakened mobs against the near cap")
            .comment("计算“玩家附近觉醒怪数量”的半径（格）")
            .defineInRange("awakenedPlayerNearRadius", 64, 16, 128);

    public static final ForgeConfigSpec.IntValue AI_VISION_INTERVAL = BUILDER
            .comment("The global minimum interval (in seconds) between two environment observation lines, to avoid spam")
            .comment("两次环境观察发话之间的最小间隔（秒），建议在 10 ~ 300 之间")
            .defineInRange("aiVisionInterval", 30, 5, 600);

    public static final ForgeConfigSpec.BooleanValue AI_VISION_BUBBLE_ONLY = BUILDER
            .comment("Whether environment observation lines are shown only as Herobrine's overhead speech bubble instead of being printed into the chat box")
            .comment("环境观察台词是否只显示在 Herobrine 头顶的气泡中，不再打印到聊天框")
            .define("aiVisionBubbleOnly", false);

    public static final ForgeConfigSpec.BooleanValue AI_VISION_SPEECH_ENABLED = BUILDER
            .comment("Master switch for Herobrine's environment observation speech (both AI-generated lines and preset fallback lines). When disabled, no observation line is generated, sent or displayed at all")
            .comment("环境观察发话总开关（AI 生成台词与内置预设台词都算）。禁用后不再产生、发送或显示任何环境观察台词")
            .define("aiVisionSpeechEnabled", true);

    public static final ForgeConfigSpec.IntValue SPEECH_BUBBLE_DURATION_SECONDS = BUILDER
            .comment("Minimum seconds a speech bubble stays on screen (longer lines are extended automatically by length, hard cap 30 seconds)")
            .comment("头顶气泡至少显示的秒数（长台词会按字数自动延长，最长 30 秒）")
            .defineInRange("speechBubbleDurationSeconds", 8, 2, 30);

    // 【新增】更新检查器配置
    public static final ForgeConfigSpec.BooleanValue ENABLE_UPDATE_CHECKER = BUILDER
            .comment("Whether to check for mod updates on Modrinth when joining a world")
            .comment("加入世界时是否在 Modrinth 检查模组更新")
            .define("enableUpdateChecker", true);

    // 战斗模式限制（与 ServerRule.BATTLE_MODE_* 对应）：信任门槛默认 200，单次 300 秒，冷却 300 秒，均可用 0 关闭。
    public static final ForgeConfigSpec.IntValue BATTLE_MODE_TRUST_THRESHOLD = BUILDER
            .comment("Trust required before a player may enable Herobrine's battle mode. 0 disables the gate")
            .comment("开启 Herobrine 战斗模式所需的信任值。0 = 不设信任门槛")
            .defineInRange("battleModeTrustThreshold", 200, 0, 1000);

    public static final ForgeConfigSpec.IntValue BATTLE_MODE_DURATION_SECONDS = BUILDER
            .comment("Max seconds one battle-mode session lasts before Herobrine sheathes on his own. 0 = unlimited")
            .comment("单次战斗模式的最长持续秒数，到时自动收刀。0 = 不限时")
            .defineInRange("battleModeDurationSeconds", 300, 0, 3600);

    public static final ForgeConfigSpec.IntValue BATTLE_MODE_COOLDOWN_SECONDS = BUILDER
            .comment("Seconds before battle mode can be re-enabled after it ends (manually or by timeout). 0 = no cooldown")
            .comment("战斗模式收刀（手动或到时）后再次开启需等待的秒数。0 = 无冷却")
            .defineInRange("battleModeCooldownSeconds", 300, 0, 3600);

    public static final ForgeConfigSpec SPEC = BUILDER.build();
    public static boolean poemOfTheEndExplosion;
    public static boolean poemActionsEnabled = true;
    public static boolean poemOutlineEnabled = true;
    public static boolean heroKingAuraEnabled;
    public static boolean heroBlockRestoration;
    public static boolean heroCleanItems;
    public static boolean heroMemoryEnabled;
    public static boolean agentToolResultFeedback;
    public static boolean agentToolConfirmation;
    public static boolean heroAutonomyEnabled;
    public static boolean heroSkillSession;
    public static boolean heroLeafVanishEnabled;
    public static boolean pranksterFakeChestEnabled;
    public static boolean cleaveSkillEnabled;
    public static boolean debugLogEnabled;

    public static boolean soulBoundPactEnabled;
    public static boolean abyssalGazeEnabled;
    public static boolean transcendencePermitEnabled;

    public static boolean aiVisionEnabled;
    public static boolean aiVisionSpeechEnabled;
    public static boolean aiVisionBubbleOnly;
    public static int speechBubbleDurationSeconds;
    public static boolean awakenedMobAiDialogueEnabled;
    public static int aiVisionInterval;
    public static int awakenedDialogueMinIntervalSeconds;
    public static int giftDialogueMinIntervalSeconds;
    public static int awakenedWorldCap;
    public static int awakenedPlayerNearCap;
    public static int awakenedPlayerNearRadius;
    // 【新增】更新检查器静态变量
    public static boolean enableUpdateChecker;

    @SubscribeEvent
    static void onLoad(final ModConfigEvent event) {
        if (event.getConfig().getSpec() != SPEC || event instanceof ModConfigEvent.Unloading) return;
        ServerConfigAccess.reload();
        reloadLocalValues();
    }

    static void reloadLocalValues() {
        poemOutlineEnabled = POEM_OUTLINE_ENABLED.get();
        heroMemoryEnabled = HERO_MEMORY_ENABLED.get();
        agentToolResultFeedback = AGENT_TOOL_RESULT_FEEDBACK.get();
        agentToolConfirmation = AGENT_TOOL_CONFIRMATION.get();
        heroAutonomyEnabled = HERO_AUTONOMY_ENABLED.get();
        heroSkillSession = HERO_SKILL_SESSION.get();
        debugLogEnabled = DEBUG_LOG_ENABLED.get();

        aiVisionEnabled = AI_VISION_ENABLED.get();
        aiVisionSpeechEnabled = AI_VISION_SPEECH_ENABLED.get();
        aiVisionBubbleOnly = AI_VISION_BUBBLE_ONLY.get();
        speechBubbleDurationSeconds = SPEECH_BUBBLE_DURATION_SECONDS.get();
        awakenedDialogueMinIntervalSeconds = AWAKENED_DIALOGUE_MIN_INTERVAL.get();
        giftDialogueMinIntervalSeconds = GIFT_DIALOGUE_MIN_INTERVAL.get();
        awakenedWorldCap = AWAKENED_WORLD_CAP.get();
        awakenedPlayerNearCap = AWAKENED_PLAYER_NEAR_CAP.get();
        awakenedPlayerNearRadius = AWAKENED_PLAYER_NEAR_RADIUS.get();
// 【新增】更新检查器赋值
        enableUpdateChecker = ENABLE_UPDATE_CHECKER.get();
        LOGGER.info("Herobrine Companion Config Loaded: Explosion={}, Aura={}, BlockRestoration={}, CleanItems={}, LeafVanish={}, CleaveSkill={}, DebugLog={}, Pact={}, Gaze={}, Permit={}, AIVision={}, AwakenedMobAIDialogue={}, AIInterval={}, AIVisionBubbleOnly={}, AIVisionSpeech={}, BubbleSeconds={}",
                poemOfTheEndExplosion, heroKingAuraEnabled, heroBlockRestoration, heroCleanItems, heroLeafVanishEnabled, cleaveSkillEnabled,
                debugLogEnabled,
                soulBoundPactEnabled, abyssalGazeEnabled, transcendencePermitEnabled, aiVisionEnabled, awakenedMobAiDialogueEnabled, aiVisionInterval, aiVisionBubbleOnly, aiVisionSpeechEnabled, speechBubbleDurationSeconds);
    }
}
