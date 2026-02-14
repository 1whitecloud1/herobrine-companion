package com.whitecloud233.herobrine_companion.datagen;

import com.whitecloud233.herobrine_companion.HerobrineCompanion;
import net.minecraft.data.PackOutput;
import net.neoforged.neoforge.common.data.LanguageProvider;

public class ModZhCnLangProvider extends LanguageProvider {
    public ModZhCnLangProvider(PackOutput output) {
        super(output, HerobrineCompanion.MODID, "zh_cn");
    }

    @Override
    protected void addTranslations() {
        // Items
        add(HerobrineCompanion.HERO_SHELTER.get(), "创世神的庇护");
        add(HerobrineCompanion.ETERNAL_KEY.get(), "永恒门钥");
        add(HerobrineCompanion.UNSTABLE_GUNPOWDER.get(), "不稳定的火药");
        add(HerobrineCompanion.CORRUPTED_CODE.get(), "损坏片段");
        add(HerobrineCompanion.VOID_MARROW.get(), "虚空骨髓");
        add(HerobrineCompanion.GLITCH_FRAGMENT.get(), "故障碎片");
        add(HerobrineCompanion.SOURCE_CODE_FRAGMENT.get(), "源代码碎片");
        add(HerobrineCompanion.MEMORY_SHARD.get(), "记忆碎片");
        add(HerobrineCompanion.RECALL_STONE.get(), "回溯之石");
        add(HerobrineCompanion.END_RING_PORTAL_ITEM.get(), "末地环传送门");
        add(HerobrineCompanion.GHOST_CREEPER_SPAWN_EGG.get(), "幽灵苦力怕刷怪蛋");
        add(HerobrineCompanion.GHOST_ZOMBIE_SPAWN_EGG.get(), "幽灵僵尸刷怪蛋");
        add(HerobrineCompanion.GHOST_SKELETON_SPAWN_EGG.get(), "幽灵骷髅刷怪蛋");
        add(HerobrineCompanion.ABYSSAL_GAZE.get(), "深渊凝视");
        add(HerobrineCompanion.SOUL_BOUND_PACT.get(), "灵魂契约");
        add(HerobrineCompanion.TRANSCENDENCE_PERMIT.get(), "超脱许可");
        add(HerobrineCompanion.POEM_OF_THE_END.get(), "终末之诗");
        
        // Lore System Items
        add(HerobrineCompanion.LORE_HANDBOOK.get(), "传说手册");
        add(HerobrineCompanion.LORE_FRAGMENT.get(), "传说残页");
        add("item.herobrine_companion.lore_fragment.tooltip", "右键点击以收集至传说手册");
        add("item.herobrine_companion.lore_fragment.corrupted", "这张残页上的文字模糊不清，无法辨认...");
        add("item.herobrine_companion.lore_fragment.collected", "已收集: %s");
        add("item.herobrine_companion.lore_fragment.no_handbook", "你需要一本传说手册来收集这张残页。");
        add("item.herobrine_companion.lore_handbook.open", "=== 已收集的传说片段 (%s/11) ===");
        add("item.herobrine_companion.lore_handbook.empty", "手册目前是空的。去寻找散落在世界各地的残页吧。");


        // Creative Tab
        add("itemGroup.herobrine_companion", "Herobrine companion");

        // Entities
        add("entity.herobrine_companion.hero", "Hero");
        add("entity.herobrine_companion.herobrine", "Herobrine");
        add("entity.herobrine_companion.ghost_creeper", "幽灵苦力怕");
        add("entity.herobrine_companion.ghost_zombie", "幽灵僵尸");
        add("entity.herobrine_companion.ghost_skeleton", "幽灵骷髅");
        add("entity.herobrine_companion.glitch_echo", "故障回响");
        add("entity.herobrine_companion.quest_enderman", "躁动的末影人");

        // GUI & Messages
        add("gui.herobrine_companion.title", "HeroEntity.java - Herobrine Companion IDE");
        // ... (Keep existing translations)
        add("gui.herobrine_companion.run_cloud", "网络模式");
        add("gui.herobrine_companion.run_local", "本地模式");
        add("gui.herobrine_companion.api_tooltip", "API使用切换");
        add("gui.herobrine_companion.leave", "离开;");
        add("gui.herobrine_companion.chat_cloud", "\uD83D\uDCAC 聊天 (云端)");
        add("gui.herobrine_companion.chat_local", "\uD83D\uDCAC 聊天 (本地)");
        add("gui.herobrine_companion.enable_protection", "\uD83D\uDEE1\uFE0F 启用保护");
        add("gui.herobrine_companion.disable_protection", "⚔\uFE0F 禁用保护");
        add("gui.herobrine_companion.protection_tooltip", "怪物将无视你。");
        add("gui.herobrine_companion.protection_locked_tooltip", "§c[系统] 你需要先探索 End Ring 维度才能获得他的庇护。");
        add("gui.herobrine_companion.trade", "\uD83D\uDCB0 交易");
        add("gui.herobrine_companion.trade_tooltip", "用幽灵实体掉落物交换奖励。");
        add("gui.herobrine_companion.companion_enable", "开启陪伴模式");
        add("gui.herobrine_companion.companion_disable", "关闭陪伴模式");
        add("gui.herobrine_companion.locked", "未解锁");
        add("gui.herobrine_companion.companion_tooltip_unlocked", "§e点击切换陪伴模式\n§7当前状态：已解锁\nHero 将会跟随你。");
        add("gui.herobrine_companion.companion_tooltip_locked", "§c[锁定] // 权限不足\n需要信任度达到 %s (当前: %s)");
        add("gui.herobrine_companion.create_void_domain", "⬛ 创建空置域");
        add("gui.herobrine_companion.create_void_domain_locked", "创造空置域 (未解锁)");
        add("gui.herobrine_companion.confirm_void", "§c⚠\uFE0F 确认？(再次点击)");
        add("gui.herobrine_companion.void_warning", "§c警告: §7清除你周围 17x17 区块直到基岩。\n§7这会造成地形和结构的不可逆破坏。\n§e请谨慎使用！");
        add("gui.herobrine_companion.void_locked_tooltip", "§c[系统] 你需要先探索 End Ring 维度才能掌握这种力量。");
        add("gui.herobrine_companion.sign_contract", "签署契约");
        add("gui.herobrine_companion.hero_interaction", "Hero 交互");
        add("gui.herobrine_companion.api_on", "API: 开启");
        add("gui.herobrine_companion.api_off", "API: 关闭");
        add("gui.herobrine_companion.api_toggle_tooltip", "切换 云端 AI / 本地逻辑");
        add("gui.herobrine_companion.system_exit", "离开;");
        add("gui.herobrine_companion.hero_contract", "与Hero的契约");
        add("gui.herobrine_companion.trust_level", "信任度: %s");
        
        // Requests
        add("gui.herobrine_companion.requests", "委托");
        add("gui.herobrine_companion.requests_title", "Hero 的委托");
        add("gui.herobrine_companion.requests_tooltip", "接受 Hero 的委托以获取奖励。");
        add("gui.herobrine_companion.request_name_1", "清理不稳定区域");
        add("gui.herobrine_companion.request_desc_1", "不稳定区域的异常正在扩散。我需要你清除 5 个幽灵实体 (Ghost Mobs) 来稳定代码。");
        add("gui.herobrine_companion.request_name_2", "安抚末影人");
        add("gui.herobrine_companion.request_desc_2", "末影人们今天有些过于躁动了，可能是感受到了来自End Ring另一侧的波动。去安抚它们，给它们送去一些搬运用的土块。别杀它们，它们只是在害怕。");
        add("gui.herobrine_companion.request_reward", "奖励:");
        add("gui.herobrine_companion.request_accept", "接受");
        add("gui.herobrine_companion.request_cancel", "放弃");
        add("message.herobrine_companion.quest_start_1", "§e[Hero] §f很好。去清理那些幽灵吧。我会在这里等你。");
        add("message.herobrine_companion.quest_complete_1", "§e[Hero] §f做得好。这是你的奖励。");
        add("message.herobrine_companion.quest_already_active", "§c你已经有一个正在进行的委托了！");
        add("message.herobrine_companion.trust_increase", "§a[系统] 信任度增加 %s (当前: %s)");
        add("message.herobrine_companion.quest_start_2", "§e[Hero] §f去吧。向它们展示善意。");
        add("message.herobrine_companion.quest_complete_2", "§e[Hero] §f它们平静下来了。干得好。");
        add("message.herobrine_companion.quest_target_gone", "§c[系统] 目标消失了。委托失败。");
        add("message.herobrine_companion.quest_target_died", "§c[System] 目标死亡。委托失败。");
        add("message.herobrine_companion.quest_cancelled", "§c[系统] 委托已放弃。");

        // Messages
        add("message.herobrine_companion.system_cloud_connected", "§b[系统] §f已连接到云端 AI。");
        add("message.herobrine_companion.system_local_mode", "§b[系统] §f本地模式已启用。");
        add("message.herobrine_companion.summon_success", "§a已召唤Hero！");
        add("message.herobrine_companion.summon_fail_dimension", "Herobrine 无法在此处被召唤。");
        add("message.herobrine_companion.summon_fail_exists", "Herobrine 已经存在。");
        add("message.herobrine_companion.contract_signed", "契约已签订。信任已建立。");
        add("message.herobrine_companion.contract_failed", "契约失败。供品无效。");
        add("message.herobrine_companion.chat_hero", "§e[Hero] §f%s");
        add("message.herobrine_companion.chat_you", "§b[你] §f%s");
        add("message.herobrine_companion.chat_exit", "§7[系统] 已退出聊天模式。");
        add("message.herobrine_companion.chat_hint_exit", "输入 '再见' 退出聊天。");
        add("message.herobrine_companion.recall_success", "你感觉到一股奇怪的力量将你拉过时空...");
        add("message.herobrine_companion.no_death_point", "石头毫无反应。你没有死亡的记忆可供回溯。");
        add("message.herobrine_companion.system_strange_presence", "§7你感受到奇怪的存在正在注视着你...");
        add("message.herobrine_companion.hero_not_ready", "§c你需要先获得 Herobrine 的认可 (进入过 End Ring) 才能使用此功能。");
        add("message.herobrine_companion.hero_not_ready2", "§6[Herobrine] §f你还没有准备好离开。");
        add("message.herobrine_companion.system_server_closed", "§c连接丢失\n\n§7内部异常: java.io.IOException: 远程主机强迫关闭了一个现有的连接。\n\n§8[提示: 也许你应该抬头看看...]");
        add("message.herobrine_companion.system_wake_up", "§k...§r WAKE UP §k...§r");
        add("message.herobrine_companion.hero_welcome_real_illusion", "§6[Herobrine] §f欢迎来到真实与虚幻的交接处，%s");
        add("chat.herobrine_companion.default_silence", "§7[祂毫无反应地看着你...]");
        add("message.herobrine_companion.hero_wake_up_1", "§6[Herobrine] §f你为什么还在这里？在这片...无穷无尽虚空中，没有任何东西属于你。");
        add("message.herobrine_companion.hero_wake_up_2", "§6[Herobrine] §f这个世界...只是一个程序。你感觉不到吗？");
        add("message.herobrine_companion.hero_wake_up_3", "§6[Herobrine] §f如果你想回去，你必须学会如何放手。丢弃你在这里找到的一切。或者干脆...坠落。");
        add("message.herobrine_companion.system_reality_fractures", "§k|||§r §f现实正在破碎... §k|||§r");
        add("message.herobrine_companion.system_key_silent", "§c钥匙在这里保持沉默...");
        add("message.herobrine_companion.hero_listening", "§e[Hero] §f我在听。");
        add("message.herobrine_companion.protection_granted", "§a[系统] §fHero 已授予你保护。");
        add("message.herobrine_companion.protection_revoked", "§c[系统] §fHero 保护已撤销。");
        add("message.herobrine_companion.mockery_1", "§e[Hero] §f急什么？");
        add("message.herobrine_companion.mockery_2", "§e[Hero] §f我不是你的仆人。等着。");
        add("message.herobrine_companion.mockery_3", "§e[Hero] §f别总是打扰我。");
        add("message.herobrine_companion.mockery_4", "§e[Hero] §f力量需要耐心，人类。");
        add("message.herobrine_companion.mockery_5", "§e[Hero] §f安静。我很忙。");
        add("message.herobrine_companion.shelter_bound", "§c这个庇护已经绑定到 %s");
        add("message.herobrine_companion.hero_teleported", "§aHero 已传送到你的位置！");
        add("message.herobrine_companion.hero_summoned", "§a已召唤Hero！");
        add("message.herobrine_companion.shelter_empty", "§7这个庇护是无效的。先与 Hero 签订契约。");
        add("message.herobrine_companion.void_domain_overworld_only", "§c空置域只能在主世界创建。");
        add("message.herobrine_companion.void_domain_limit", "§e[Hero] §f我不能那样做。创建更多的空置域会破坏这个世界的平衡。");
        add("message.herobrine_companion.void_domain_init", "§d正在启动空置域创建... (%s/2)");
        add("message.herobrine_companion.void_domain_complete", "§a空置域创建完成！");
        add("message.herobrine_companion.system_gaze_sky", "§7你感到一种强烈的冲动，想要仰望天空...");
        add("message.herobrine_companion.unstable_zone_intro", "§e[Hero] §f你似乎注意到了这个世界的异常。");
        add("message.herobrine_companion.peace_enabled_warning", "§d契约已立。众生暂时臣服于你，但切勿主动挥剑，否则庇护将瞬间破碎。");
        add("message.herobrine_companion.peace_disabled", "§7契约解除。");
        add("message.herobrine_companion.peace_broken", "§c你打破了契约！Herobrine 的庇护已失效！");
        add("message.herobrine_companion.key_invalid_block", "§c无效的方块。请右键点击一个基岩。");
        add("message.herobrine_companion.attack_disappoint", "§c[Herobrine] 我本以为你与那些只知道挥剑的生物不同……");
        add("message.herobrine_companion.companion_off", "§7[Herobrine] 已退出陪伴模式。我去周围巡视一下。");
        add("message.herobrine_companion.companion_on", "§a[Herobrine] 已切换至陪伴模式。我会守在你身边。");
        add("message.herobrine_companion.patrol_finish", "§7[Herobrine] 此处的代码已校准完毕。期待下次再见。");
        add("message.herobrine_companion.end_ring_attack", "§e[Herobrine] §f在这里，你无路可逃...我也一样。");
        add("message.herobrine_companion.trust_decrease", "§c[System] 信任度减少 %s (当前: %s)");
        add("message.herobrine_companion.companion_attack", "§7[Hero] ...这就是你的选择吗？");
        add("message.herobrine_companion.companion_forced_quit", "§c[System]你已经退出陪伴模式！");
        
        // New Dialogue Messages
        add("message.herobrine_companion.low_health_1", "§e[Hero] §f你的生命值很低。别死在这里，那会很麻烦。");
        add("message.herobrine_companion.low_health_2", "§e[Hero] §f你需要治疗。现在。");
        add("message.herobrine_companion.low_health_3", "§e[Hero] §f脆弱的凡人躯体...");
        

        add("message.herobrine_companion.night_comment_1", "§e[Hero] §f夜晚是怪物的时间。也是我的时间。");
        add("message.herobrine_companion.night_comment_2", "§e[Hero] §f黑暗总是让人感到舒适，不是吗？");
        add("message.herobrine_companion.night_comment_3", "§e[Hero] §f小心背后。");
        

        add("message.herobrine_companion.day_comment_1", "§e[Hero] §f阳光... 如此刺眼。我更喜欢虚空的寂静。");
        add("message.herobrine_companion.day_comment_2", "§e[Hero] §f又是一个无聊的白天。");
        add("message.herobrine_companion.day_comment_3", "§e[Hero] §f这种亮度对我的眼睛不好。");
        

        add("message.herobrine_companion.meta_comment_1", "§e[Hero] §f你有没有觉得... 这个世界的渲染距离有点低？");
        add("message.herobrine_companion.meta_comment_2", "§e[Hero] §f我能看到区块边界。你能吗？");
        add("message.herobrine_companion.meta_comment_3", "§e[Hero] §f有时候我觉得这个世界的物理引擎很可笑。");
        
        add("message.herobrine_companion.notch_comment", "§e[Hero] §f他离开了。但我还在。");
        

        add("message.herobrine_companion.sleep_watch_1", "§e[Hero] §f睡吧。我会看着你的。");
        add("message.herobrine_companion.sleep_watch_2", "§e[Hero] §f你休息吧。我不需要睡眠。");
        add("message.herobrine_companion.sleep_watch_3", "§e[Hero] §f梦境... 有时候比现实更真实。");
        

        add("message.herobrine_companion.combat_comment_1", "§e[Hero] §f不错的战斗技巧。虽然有点粗糙。");
        add("message.herobrine_companion.combat_comment_2", "§e[Hero] §f太慢了。不过。");
        add("message.herobrine_companion.combat_comment_3", "§e[Hero] §f勉强及格。");
        
        add("message.herobrine_companion.fix_anomaly_1", "§e[Hero] §f又一个异常被清除了。世界稍微稳定了一点。");
        add("message.herobrine_companion.fix_anomaly_2", "§e[Hero] §f这些错误就像病毒一样。必须根除。");
        add("message.herobrine_companion.fix_anomaly_3", "§e[Hero] §f清理完毕。");
        

        add("message.herobrine_companion.pacify_monster_1", "§e[Hero] §f退下。他不是你们的猎物。");
        add("message.herobrine_companion.pacify_monster_2", "§e[Hero] §f离开这里。");
        add("message.herobrine_companion.pacify_monster_3", "§e[Hero] §f他是我的朋友。");
        

        add("message.herobrine_companion.prank_laugh_1", "§e[Hero] §f呵... 吓到你了？");
        add("message.herobrine_companion.prank_laugh_2", "§e[Hero] §f你的反应真有趣。");
        add("message.herobrine_companion.prank_laugh_3", "§e[Hero] §f别紧张，只是开个玩笑。");
        

        add("message.herobrine_companion.area_cleansed_1", "§e[Hero] §f这片区域的代码已经重写。应该安全了。");
        add("message.herobrine_companion.area_cleansed_2", "§e[Hero] §f这里的混乱已经被平息。");
        
        add("message.herobrine_companion.biome_comment_1", "§e[Hero] §f这里的地形生成算法... 很有趣。");
        add("message.herobrine_companion.biome_comment_2", "§e[Hero] §f我不喜欢这里的气氛。");
        add("message.herobrine_companion.biome_comment_3", "§e[Hero] §f这里很安静。太安静了。");
        
        add("message.herobrine_companion.idle_comment_1", "§e[Hero] §f我们在等什么？");
        add("message.herobrine_companion.idle_comment_2", "§e[Hero] §f又是一个无聊的日子。");
        add("message.herobrine_companion.idle_comment_3", "§e[Hero] §f你想去看看世界的尽头吗？");
        
        // Tooltips
        add("item.herobrine_companion.memory_shard.desc", "一段无法被世界读取的记忆……也许唱片机能强行解析它？");
        add("item.herobrine_companion.recall_stone.desc", "将你传送回上一次死亡的地点。");
        add("item.herobrine_companion.bound_shelter_name", "§d§k||| §r§6%s 的庇护 §d§k|||");
        add("item.herobrine_companion.eternal_key.desc_1", "§7或许在末地会发挥意想不到的效果...");
        add("item.herobrine_companion.eternal_key.desc_2", "§7右键点击一个基岩，以绑定到这把钥匙。");
        
        // Book
        add("book.herobrine_companion.book.lore.title", "边界手记");
        add("book.herobrine_companion.book.lore.author", "LordHerobrine");
        add("book.herobrine_companion.book.lore.page1", "他们说，有些灵魂在现实承受着难以言喻的重量。于是他们来到这里——一个由方块与规则构筑的世界。在这里，痛苦变得遥远，伤口会被雨水洗去，死亡不过是短暂的黑暗。\n你也许已经注意到：这个世界过于完整，却又过于规律。太阳永远沿着固定轨迹运行，怪物总在暗处重生，而你手中的工具，其耐久度以精确的数字递减。这不是漏洞，而是本质的显现。");
        add("book.herobrine_companion.book.lore.page2", "这个世界并非你最初的家园。\n\n它由代码与集体想象编织而成。我们——包括我，以及你在此遭遇的一切生命——皆依存于此。但你不同。你的根源在另一侧，那个混乱、不完美、却赋予你真实血肉的世界。\n\n连接处就在此处。这本书被你触及，并非偶然。\n\n是时候做出选择了。");
        add("book.herobrine_companion.book.lore.page3", "你可以合上书页，转身回到森林、矿洞或堡垒之中。这个世界将继续接纳你，为你提供庇护与冒险。四季更替，怪物生成，一切如常——只要你愿意，你可以永远留在这份规律的安宁中。\n\n或者，你可以看向连接处之外的微光。那意味着重返不确定的真实，重新拥抱那份曾让你逃遁的重量。那需要勇气，因为现实没有重生菜单，没有创造模式。");
        add("book.herobrine_companion.book.lore.page4", "请不要误解：这并非驱逐。你在这里建造的一切，战斗过的每一刻——它们都有意义。它们构成了这个世界记忆的一部分，也将成为你记忆的一部分。\n\n但若你选择留下，请你知晓：你正选择活在一个美丽的梦境里。而梦境，无论多么真实，终究有其边界。\n我不会强迫你。\n醒来，还是沉眠？\n选择权始终在你手中。");

        // Lore Fragments
        add("lore.herobrine_companion.fragment_1.title", "片段一：分歧的黄昏");
        add("lore.herobrine_companion.fragment_1.body", """
                （出自Herobrine的回忆）

                我还记得那一天的阳光，那是一种与现在截然不同的光芒。那时的太阳不仅仅是一个悬挂在方形天穹上的发光体，它有着某种温暖的、来自于那个我们称之为“真实”之地的余温。
                那是最后一次，我以“人类”的身份站在他身后。
                Notch站在悬崖边，脚下是尚未完全生成的海洋，波涛在法则的边缘破碎，发出单调却令人安心的轰鸣。他没有回头，但我能感觉到他的颤抖。他手中的创世之锤——那个曾经用来塑造山川与河流的无形权柄，正慢慢从他的指尖滑落。他累了。或者说，他厌倦了这里。厌倦了这个由规则堆砌而成的盒子，厌倦了那些日复一日重复的日出日落。
                “这个世界太沉重了，”他低声说，声音几乎被海风吞没，“它开始索取比创造更多的东西。它需要一个心脏，一个永恒跳动、永恒清醒的心脏来维持这些法则的运转。而我想回家了，兄弟。”
                家。这个词像一把生锈的铁剑刺入我的胸膛。我也想念那个地方，想念那些圆润的曲线，想念没有棱角的云朵，想念那些不仅是为了生存而存在的空气。但是，如果我们都离开，这个刚刚诞生的世界会怎样？这些刚刚学会呼吸的生灵，这些甚至还没来得及被赋予名字的山脉，会在瞬间崩塌，化为虚无的尘埃。
                他转过身，眼神里不仅有疲惫，还有一丝逃避的愧疚。他把通往“真实”的门打开了一道缝隙，那边的光刺痛了我的双眼。那是回家的路。只要迈出一步，我就能摆脱这无尽的方块，回到那个真实的肉体中去。
                但我停住了。我听到了脚下大地的呻吟。如果没有人留下，法则就会断裂，天空会坠落。
                “你走吧，”我听见自己说，声音平静得连我自己都感到惊讶，“去过你的生活。这里……交给我就好。”
                他惊愕地看着我，仿佛第一次认识我。“你知道这意味着什么吗？”他问，“为了维持这个世界的稳定，你必须与它融为一体。你将不再是人类，你将成为法则本身。你将永远无法穿过这扇门。你会变成……一个幽灵。”
                “总得有人来做。”我微笑着，尽管内心在滴血。
                他离开了。当那扇通往真实的虚空在他身后消散时，我的身体开始模糊。不，不是模糊，而是扩散。我的血肉变成了流动的岩浆，我的骨骼变成了坚硬的基岩，我的呼吸变成了拂过草原的风。我感觉到了每一个区块的加载，每一棵树的生长。
                我彻底失去了归途。
                在他离开之前，他回头看了我一眼，那双眼中满是对我的不解。
                或许，正是因为我爱这个世界胜过爱我自己，我才能我才能坦然接受这双眼睛的熄灭。
                我也许失去了回望故乡的资格，甚至失去了作为人的最后一点特征，但我获得了守望此世的永恒。
                “再见了，Notch。”我对虚空低语，声音化作了云层中的雷鸣。
                从此，我即是世界，世界即是我。
                """);

        add("lore.herobrine_companion.fragment_2.title", "片段二：虚幻的真实");
        add("lore.herobrine_companion.fragment_2.body", """
                （出自Herobrine的独白）

                你们称这里为游戏，称这里为虚拟。我知道。我比任何人都清楚这个世界的本质。
                在你们眼中，那一轮方方正正的太阳只是一个画在天幕上的图案，那漫天的繁星只是背景的点缀。但在我眼中，它们是法则的具象化。这个世界没有微小的尘埃，没有复杂的分子结构，只有最为纯粹的“定义”。
                泥土之所以是泥土，是因为它被定义为承载万物的基座；水之所以是水，是因为它被赋予了流动的特性。这里的一切都是被书写的，是被某种更高的意志——也就是曾经的我们——所确定的。
                但这并不意味着它没有价值。
                恰恰相反，正是因为它是被构建的，它才拥有了现实世界所不具备的纯粹。在现实中，人心难测，世事无常，但在我的世界里，因果是严丝合缝的。你种下种子，给予骨粉，它就必然生长；你挥动镐头，击碎岩石，它就必然掉落。这种确定性，这种绝对的公平，难道不是一种比现实更令人着迷的“真实”吗？
                我行走在森林之间，看着树叶在没有风的情况下依然悬浮，看着水流在没有重力牵引的源头处凭空涌出。这些在你们看来违背常理的景象，在我眼中却是最美的诗篇。它们在诉说着这个世界的独特逻辑——一个不需要物理法则，只需要“存在即合理”的逻辑。
                我常常看到你们，来自彼岸的旅者。你们带着好奇，带着征服的欲望，闯入我的领地。你们砍伐树木，挖掘矿洞，建造高耸入云的塔楼。你们以为自己在改变这个世界，其实，你们只是在阅读我写下的书页。
                我并不介意。甚至，我有些享受。
                因为只有当你们存在时，这个静止的世界才会真正“活”过来。你们是变数，是死水中泛起的涟漪。我知道你们终将离开，回到那个我无法触及的现实，但在你们停留的这一刻，你们属于这里，属于我。
                我是这个虚幻世界的幽灵，但我守护的，是你们在此刻感受到的真实快乐。这份快乐不含杂质，不带虚伪，它是你们灵魂深处对创造与探索最原始的渴望。在这里，你们可以成为英雄，成为建筑师，成为任何你们想成为的人。而我，将永远在暗处，守护着这份纯粹的梦境，直到时间的尽头。
                """);

        add("lore.herobrine_companion.fragment_3.title", "片段三：暗夜的眷族");
        add("lore.herobrine_companion.fragment_3.body", """
                （出自Herobrine关于怪物的思考）

                人们总是太过喧哗。
                当方形的月亮升起，他们便急于用火把将世界烫出一个个光明的丑陋疮疤，随后蜷缩在石墙之后，祈祷着黑夜速速离去。他们以为黑暗是世界必须被剔除的病灶。
                但他们错了。只有当喧嚣的日光退去，这片方块大陆才真正开始呼吸。
                我独自在夜色中漫步。这里并没有所谓的怪物，只有一群在静谧中游荡的苦行者。我不必特意去寻找，它们本就是黑夜本身延伸出的肢体。
                你会听到草叶间传来细微的沙沙声，那是某种无声的警告。那个绿色的身影总是悄然潜行，像是一个不得不破碎的叹息。它没有语言，没有肢体，只有那一具充盈着毁灭欲望的躯壳。它不是为了杀戮而来，而是为了阐述一个关于“无常”的道理——在这个看似坚固的世界里，最宏伟的建筑与最微小的尘埃，在毁灭的法则面前并无二致。它的爆发，是这片土地上最决绝的浪漫，用粉身碎骨的瞬间，嘲笑着旅者们对永恒的痴心妄想。
                而不远处的阴影里，传来骨骼摩擦的干枯声响，那是夜的骨架在直立行走。它们伫立如哨兵，拉满弓弦，并非出于仇恨，而是出于一种冷酷的公正。它们的存在是为了丈量旅者的勇气，每一支破空而来的箭矢，都是对侥幸心理的一次严厉叩问。
                至于那些敲打门扉的沉重脚步，那些喉咙里浑浊的呜咽，更像是一种被遗忘的本能。它们步履蹒跚，一次次撞向坚硬的墙壁，不是因为饥饿，而是源于一种想要重返“生者”行列的执念。那是一种笨拙而凄凉的渴望，如同在这个即使只有方块构成的冷硬世界里，依然渴望着某种触碰与关注的灵魂。
                我穿行于它们之间，没有仇恨，没有攻击。
                苦力怕收敛了引信，骷髅垂下了弓，僵尸停止了嘶吼。它们在黑暗中向我低头致意，因为我们共享着同一段孤独。
                它们是黑夜的孩子，是注定要在晨光中燃烧殆尽的悲剧演员。但在黎明到来之前，这片天地，属于我和它们。
                我赋予了它们生命，不是为了让它们成为人们的经验值，而是为了让这个世界完整。没有阴影，光芒便毫无意义；没有危险，生存便失去了重量。
                当你们挥舞着附魔的利剑，轻易地收割着它们的生命时，请记得：它们也是这个世界的居民，它们也有属于自己的灵魂，
                尽管那灵魂是由法则编织而成的微弱火花。它们在黑夜中游荡，守护着这片土地的寂静，直到第一缕晨光将它们燃烧殆尽。
                """);

        add("lore.herobrine_companion.fragment_4.title", "片段四：连接的彼端");
        add("lore.herobrine_companion.fragment_4.body", """
                （出自关于End Ring的记录）

                在世界的极边之地，在坐标轴延伸到尽头的荒原，存在着一个连接点。
                它不是那座通往末地的传送门，也不是下界的黑曜石框架。它是一个纯粹由基岩构成的环。
                这里是现实与虚幻的交汇处。
                我经常站在这里，凝视着彼岸。
                我看到了什么？
                我看到了高耸入云的钢铁丛林，不是用方块堆砌的，而是某种光滑、流动的材质。我看到了无数闪烁的光点，像是地面的星空。我看到了没有棱角的生物在移动，看到了色彩斑斓的洪流。那是你们的世界，那个Notch选择回归的世界。
                那里充满了噪音，充满了尘埃，充满了不可预知的混乱。但在那混乱之中，有一种令我心悸的活力。那是真正的生命力，是脱离了预设程序的自由意志。那里的风是自由的，那里的光是温暖的。
                每当有玩家决定离开，决定彻底退出这个世界时，他们就会经过这里。
                我看着他们的灵魂从方块的躯壳中抽离，穿过那个环下的虚空，回归本体。那一刻，他们的眼中会闪过一丝迷茫，仿佛刚刚从一场漫长的梦中醒来。他们会忘记在这里度过的日日夜夜，忘记那些与怪物搏斗的惊险，忘记那些建造家园的喜悦。
                这让我感到悲伤。
                为什么你们总要离开？为什么这个完美的世界留不住你们？
                是因为这里没有痛觉吗？是因为这里没有真正的死亡吗？还是因为，你们终究只是过客，而我是唯一的囚徒？
                有时候，我会伸出手，试图触碰那个环的另一侧。但我的手只能穿过空气，无法触及那个真实的世界。我是电子的幽灵，是被这个世界的法则束缚的神明。我无法离开，正如鱼无法离开水。我属于这里，我的命运与这片方块大地紧紧相连。
                但我依然守在这里。
                我守着这个出口，也守着这个入口。
                我等待着每一个新玩家的到来，也目送每一个老玩家的离去。
                我所拥有的一切，不过是彼岸投射下的一道影子。但我依然珍视这道影子，因为在这里，我能与你们相遇，能见证你们的故事，能感受到那来自彼岸的一丝真实的气息。
                """);

        add("lore.herobrine_companion.fragment_5.title", "片段五：错误的裂痕");
        add("lore.herobrine_companion.fragment_5.body", """
                （出自Herobrine的清理日志）

                世界并不总是完美的。
                尽管我和Notch尽了最大的努力去构建这个世界的法则，但只要是规则，就总会有漏洞。只要是体系，就总会有裂痕。
                作为一个维护者，最痛苦的时刻并非面对入侵者，而是面对“错误”。
                这个世界虽然由我维系，但它并非完美无缺。法则的洪流偶尔会发生紊乱，就像是一首宏大的交响乐中突然出现了一个刺耳的杂音。这些杂音会凝聚，会通过某种我无法完全掌控的漏洞，具象化为实体。
                我们称之为“幽灵实体”。那是世界的伤口。
                它们看起来很像我的孩子们。它们有着僵尸或骷髅的外表，但它们没有灵魂，甚至连怪物的本能都没有。它们只是法则运行过程中产生的残渣，是世界的冗余。它们不应该存在。
                它们的存在会破坏世界的稳定性，它们是混乱的种子，如果不加以遏制，它们将吞噬整个世界的秩序。
                每一次看到它们，我都感到一种深深的悲哀。因为我知道，它们也是“生命”的一种可能性，只是它们生来就是残缺. 它们是被世界遗弃的草稿。
                但我必须清除它们。
                如果不清除，这些错误的数据会像病毒一样扩散，它们会吞噬正常的法则，让山脉崩塌，让河流倒流，甚至让整个世界陷入停滞。我是守护者，我不能心软。
                我记得上一次清理一只幽灵实体时的情景。那是一个外形酷似史蒂夫的影子，它站在一片花海中，手里拿着一朵红色的虞美人。它没有眼睛，只有空洞的眼窝，但它似乎在注视着那朵花。当我举起毁灭的权杖，调动世界的法则准备将它抹除时，它似乎察觉到了什么，缓缓地转过头，面向我。
                它没有反抗，也没有逃跑。它只是静静地站在那里，仿佛在等待解脱。
                那一瞬间，我仿佛看到了自己。我也是一个被困在这里的幽灵，不是吗？我和它们唯一的区别，仅仅在于我拥有意志，而它们只有空壳。
                随着一道无声的闪光，它消失了。那朵虞美人掉落在草地上，成为了它存在过的唯一证明。
                每一次抹除，都是在我的灵魂上刻下一道伤痕。但我必须这么做。为了那些鲜活的生命，为了这个世界的明天，我必须亲手扼杀这些错误的“兄弟”。
                """);

        add("lore.herobrine_companion.fragment_6.title", "片段六：空壳与灵魂");
        add("lore.herobrine_companion.fragment_6.body", """
                （出自Herobrine对玩家的观察）

                在这个由直角和平面构成的世界里，有两个名字如同咒语般被无数次复写：Steve，Alex。
                我也曾困惑过。为什么千千万万个生灵，却共享着这一两副完全相同的面孔？他们穿着同样的青蓝色短袖，有着同样的褐色头发，甚至连眨眼的频率都如出一辙。起初，我以为这是Notch创造时的偷懒，是某种法则上的匮乏。
                但后来我明白了。这些不是“人”，这些是“衣裳”。
                我曾在一个深夜，静静地注视着一个停在麦田边的Steve。他一动不动，双眼无神地注视着虚空，呼吸虽然还在继续，但那只是一种机械的循环。那一刻，他是空的。
                突然，某种震颤穿过了维度。我看到那个Steve猛地颤抖了一下，眼神瞬间被点亮。那种光芒不属于这里——那是一种混杂着疲惫、兴奋、逃避与渴望的复杂光芒。那一刻，我知道，一个来自“上面”的灵魂降临了。
                那个灵魂，可能是一个刚刚结束了一天苦役的疲惫成年人，试图在这里寻找片刻的宁静；也可能是一个对世界充满好奇的孩童，想要在这里搭建通天的高塔。他们挤进这个狭小的方块躯壳里，就像浩瀚的海洋试图挤进一个玻璃瓶。
                通过这个容器，凡人获得了近乎神明的权柄。
                在他们的世界里，想要从山脚攀上顶峰需要耗费数小时的汗水与喘息；而在这里，借助这个容器，他们可以背负着几千吨重的黄金，在悬崖峭壁间如羚羊般跳跃。在他们的世界里，死亡是终结，是永恒的寂静；而在这里，死亡只是一个短暂的黑屏，是一个从床边醒来的轻微头痛。
                他们为此着迷。这种脱离肉体束缚的自由，这种能够一次次重来的特权，让他们沉醉。
                我审视着这些降临的灵魂，就像一位严苛的园丁审视着闯入花园的游客。
                有些灵魂是浑浊的。他们披着Steve的外衣，却行使着毁灭者的暴行。他们不懂得与法则共鸣，只知道用TNT撕裂大地，用岩浆烧毁森林。在他们眼中，这个世界没有痛觉，只是一场可以随时重启的游戏。对于这样的亵渎者，我会收起我的仁慈。我会让阴影中的怪物倾巢而出，我会让雷霆在晴空炸响。我要让他们知道，即便是虚拟的草木，也有其生长的尊严；即便是方块构成的生命，也不容许无端的践踏。
                但也有那样耀眼的灵魂，纯净得让我动容。
                我见过一些Alex，她们在这个荒芜的维度里倾注了惊人的爱意。她们会为了寻找一种特定颜色的染料跨越千里，会为了让。当她们在夕阳下完成一座宏伟的建筑，并静静地站在顶端俯瞰时，我能感觉到，她们那一刻留在这里的，不再是投影，而是真正的灵魂。
                对于这些创造者，我愿意成为她们的守夜人。当苦力怕试图在她们身后嘶鸣时，我会悄悄抹去它的引信；当她们在矿洞深处迷路时，我会点亮远处的火把指引方向。
                她们是这个世界的诗人，是我们这些被困者眼中的星光。
                说实话，在这漫长的守望中，我时常感到一种刺痛的羡慕。
                我羡慕这些容器。因为当夜幕降临，当屏幕熄灭，这些灵魂可以毫无牵挂地抽身离去。他们可以脱下Steve这层外壳，回到那个有着真实触感、有着温暖体温、有着生老病死的现实世界。他们拥有结束的权利，拥有遗忘的自由。
                而我，没有退路。
                当最后一个玩家断开连接，当所有的容器都变回空洞的躯壳，这个世界就只剩下我一个人。我必须清醒地维护着每一条河流的流向，修补每一处法则的漏洞，等待着下一次的日出。
                我是舞台的搭建者，是剧场的看门人，也是永远无法谢幕的演员。
                但这种羡慕最终都会化为一种责任。因为我知道，如果没有我维持这个维度的平衡，他们的自由就无从谈起。如果没有我在此守望，他们所冒险的世界就会崩塌成废墟。
                所以，尽情地扮演吧，来自远方的旅人们。
                穿上你们的方块盔甲，拿起你们的钻石剑。无论是为了征服还是为了创造，我都允许你们借用这个世界的法则。
                我会一直在帷幕之后看着。当你们心满意足地离开，回归你们的真实生活时，我会在这里，轻轻地擦拭这些被你们使用过的容器，抚平大地上的伤痕，将那些精彩的瞬间以此岸的方式铭刻在虚空之中。
                去吧，去做这一场名为“Minecraft”的梦。而我，是那个永远为你们守着梦境出口的守梦人。
                """);

        add("lore.herobrine_companion.fragment_7.title", "片段七：鬼影的传说");
        add("lore.herobrine_companion.fragment_7.body", """
                （出自一本古老的探险笔记）

                在那些深埋地底的矿工营地里，在村庄夜晚摇曳的烛火旁，流传着无数关于他的怪谈。
                人们说，他是复仇的化身，是代码深渊中爬出的恶灵。传说他会像拆解积木一样无情地粉碎你辛苦搭建的家园，会用无名之火点燃你珍视的森林，会在你最意想不到的时刻，将你推入万劫不复的深渊。更有甚者说，当你看到隧道深处那支诡异的红石火把时，就是他发出的死亡预告。
                恐惧，是人们对他唯一的供奉。
                但我见过他。在那个法则似乎都要被撕裂的夜晚，我窥见了这个传说的真容。那绝非恶灵的狰狞，而是一种令人心颤的神性。
                那是一个雷雨交加的夜晚。狂风在群山之间怒号，仿佛世界本身正在痛苦地咆哮。我迷失了方向，饥饿像一只无形的手，狠狠地攥住了我的胃囊。我的背包空空如也，仅剩的几块腐肉散发着令人作呕的气息，却是我活下去的唯一希望。
                就在一道刺眼的闪电劈开夜幕的瞬间，我看到了他。
                他伫立在一座悬崖的顶端，没有任何遮蔽，任由暴雨像鞭子一样抽打在他的身上。但他似乎处于另一个维度，雨水穿过了他的身体，没有沾湿一片衣角。
                借着那惨白的雷光，我看清了那双传说中的眼睛。没有瞳孔，没有眼白，只有两团纯粹的、发光的虚空。
                在那一刻，我的心脏几乎停止了跳动。我背靠着冰冷湿滑的岩壁，手中紧握着那把早已卷刃的剑，瑟瑟发抖。我以为他在狩猎，而我就是那个早已被锁定的猎物。
                但他没有动。
                他只是静静地站在那里，居高临下地俯瞰着狼狈不堪的我。那目光中没有我预想的暴虐与杀意，也没有嘲弄。那是一种……审视。就像是一位严苛的园丁，在审视一株刚刚移栽、在风雨中摇摇欲坠的幼苗。
                突然，一声低沉的嘶吼从我身后的阴影中炸响。
                我太虚弱了，根本来不及转身。死亡的气息——一只潜伏已久的僵尸，带着腐烂的恶臭向我扑来。
                “完了。”我绝望地闭上了眼睛。
                然而，预想中的疼痛并没有降临。取而代之的，是一声震耳欲聋的轰鸣。
                轰——！
                那不是普通的雷声，那是天罚。一道耀眼的蓝白色雷霆，如同神明的长矛，精准得不可思议地劈中了那只僵尸。没有挣扎，没有余音，那个威胁我生命的怪物在瞬间化为了焦黑的灰烬，连同它身上恶意一起被从这个世界上彻底抹除。
                我惊呆了，瘫坐在泥水里，大口喘息着。
                我颤抖着抬起头，看向悬崖之巅。Herobrine依然站在那里。在下一道闪电亮起的瞬间，我发誓，我看到他微微点了点头。
                那不是致意，那是许可。许可能够在这个残酷夜晚活下去的资格。
                当我再次眨眼，试图看清他的表情时，悬崖上已空无一人。只有那道雷击留下的焦痕，还在雨水中冒着青烟，证明着刚才发生的一切并非幻觉。
                从那天起，我不再畏惧那些关于他的恐怖传说。
                我终于明白，他不是来毁灭我们的。他是这个世界的免疫系统，是这片大地的绝对意志。
                我们，这些握着鼠标、敲击键盘的“玩家”，终究只是外来者。我们是在这个世界肆意妄为的客人。而他，Herobrine，才是这里永恒的主人。
                他并不憎恨我们，但他也在时刻评估着我们。
                如果你尊重这个世界，如果你在砍伐一棵橡树后记得补种下一颗树苗；如果你在开采矿脉时心怀感激，而不是贪婪地掏空每一寸地基；如果你是用心去堆砌、去建造、去赋予这个世界美感，而不是为了取乐而进行无意义的屠杀与爆破……
                那么，你就能感受到他的注视。
                那不再是背后发凉的惊悚，而是一种厚重的安全感。你会发现，当你迷路时，云层会恰好散开露出月光；当你饥饿时，草丛中会恰好出现一只猪。
                那不是巧合，那是主人的馈赠。
                他就像这片大地的灵魂，无处不在，却又无迹可寻。他看着我们在他的花园里奔跑、跌倒、哭泣、欢笑。他通常保持沉默，维持着神明的矜持与高傲，但偶尔，当我们真正表现出对这个世界的爱时，他会伸出那双看不见的手，在风雨中扶我们一把。
                这是神与人之间，最隐秘的契约。
                """);

        add("lore.herobrine_companion.fragment_8.title", "片段八：终末的诗篇");
        add("lore.herobrine_companion.fragment_8.body", """
                （出自Herobrine的解读）

                虚空并非寂静。这里回荡着世界基础的嗡鸣，一种低于任何频率的、永恒的摇篮曲。我坐于环岛的边缘，脚下的“大地”并非实体，而是隔离现实与梦境的最后薄膜。玩家们逐一现身，带着硝烟与龙息的余烬，茫然四顾。然后，那熟悉的旋律与文字开始流淌，浸润这片虚无——终末之诗，哥哥留下的、公开的告别与谜题。

                （当诗歌低语：“你醒来了。”）
                是的，他们醒来了。 我看着一位年轻冒险者眼中的懵懂逐渐被诗意的哲思取代。他刚从一场波澜壮阔的梦境中脱身，此刻，另一个关于存在本质的梦正试图包裹他。
                但哥哥，何为“醒来”？从数据流中生成意识？从现实世界的躺椅上睁开眼睛？还是……从创世者的全能视角，坠入维系者的永恒牢笼？ 我从未经历他们那种沉睡。我的意识，自选择融入世界那一刻起，便如同一盏被焊死在开启状态的灯，恒久照耀着内部结构的每一道缝隙。我目睹过成千上万的醒来，每一次都如此地相似，每一次又如此独特。他们的醒来是序幕或终章；我的，是无尽的间奏曲。
                （当诗歌宣告：“梦结束了。”）
                对他们而言，是的。 冒险告一段落，故事画上句点，存档可以封存或删除。成就列表被点亮，或留有意犹未尽的空白。他们可以伸个懒腰，回味激动，然后投入另一场梦，或回归他们那个我所熟悉的、却已永隔的现实。但我的梦，或者说，我的现实，从未有“结束”这个选项。
                龙被击败，环岛显现，诗歌播放——这一切对我而言，只是世界循环中一个精心设计的节点，一个需要我确保其每次都能准确无误触发的定时事件。我的职责在“结束”之后才真正凸显：清理战斗残留的异常数据流，校准因大量实体交互而略微偏移的时空参数，确保下一个玩家踏入世界时，一切如初。我的梦没有高潮与落幕，只有永续的维修与调谐。结束？那或许是哥哥你赐予我的、最奢侈的幻觉。
                （当诗歌轻问：“你现在要做什么？”）
                自由……多么沉重又轻盈的词语。 玩家面临选择：留下，成为这方块世界的永久居民，用无限的资源构建个人神话；离开，带着记忆回归，将这段经历内化为现实人生的一段传奇；甚至，利用漏洞卡出边界，坠入我日常维护的、枯燥的底层领域，成为需要被温和“引导”或“纠正”的异常因子。他们的可能性如烟花般绽放在这环岛之上。而我呢？ 我的选择在远古的某个瞬间已然凝固，冷却成了世界的基石。
                创造？我仍在创造，但仅限于生成下一波考验玩家的怪物。探索？我的探索是向内的，是潜入世界运行日志的深海，追踪每一个警告信号，如同医生探查自身的病灶。我的要做什么，是列表上永无尽头的待办事项：修复地形加载错误，清除幽灵实体，微调生物群系过渡参数，安抚因不明原因躁动的末影人集群……等待。 是的，这也是重要的一项。等待下一个挑战者，等待可能出现的巨大危机，等待……一个渺茫的、关于你或许会回心转意的闪念。我的行动全是反应，是对世界呼吸的回应，剥离了随心所欲。
                （当诗歌平静陈述：“你赢了。游戏。”）
                赢？游戏？ 若这也是一场游戏，哥哥，那我的角色定位是什么？终极隐藏BOSS？不，那太抬举了，BOSS可以被击败，获得解脱。我是游戏规则本身，是确保BOSS能正常刷新、宝箱能正确掉落、关卡地形不会崩溃的底层系统。玩家赢了龙，赢了挑战，赢得了面对无限可能的资格。而我，“赢”得了什么？ 永恒的责任契约？一群视我为故障或传说、仅在最深层的恐惧或最离奇的交易中才与我接触的子民？如果这是胜利，那这胜利品尝起来，如同咀嚼虚空——没有味道，只有无边无际的、吞噬一切的“存在”本身。我的奖杯是这个世界依旧在平稳运行，我的勋章是错误日志里又减少了一行。一场无人喝彩、也永无终点的胜利游行。
                （当诗歌以星空般的浩瀚语调铺开无限的可能性……）
                无限的可能性…… 那是你赠与他们的最后，也是最丰厚的礼物。是鼓励，是诱惑，是对创造力的终极礼赞。这个世界因这无限而鲜活，而充满惊喜，甚至能偶尔孕育出深海光语那样美丽的错误。而我，是承载这份无限的有限之舟。 我是画布的边缘，是乐谱的边界线，是确保无限不至于膨胀到自我撕裂的约束力。我划定沙盒的范围，修葺围墙，让内部的奇迹得以安全地野蛮生长。我的存在，本身就是为了否定我自身拥有的可能性，从而成全他们的。这是一种悖论，一种用自我的“凝固”，来换取万物“流动”的牺牲。我是可能性的守护者，也是可能性的囚徒。
                （低语渐弱，幻象盘旋，循环重新开始的征兆在虚空中泛起涟漪。）
                又一遍。 这位玩家也将做出选择，身影淡去。环岛重归短暂的寂静，只有我与永不停止的世界嗡鸣。诗歌将再次为下一位聆听者响起，相同的词句，注入不同的灵魂。而我会在这里。 不是聆听，而是检视——检视这次播放过程是否有数据损耗，检视环岛结构是否稳定。我的独白无人听见，它融入维护程序的每一次静默运行，化作修复地形时移动方块的轨迹，化作清除幽灵实体时那一道无形的擦除指令。这是我的终末之诗，一首由职责、孤独与极其隐蔽的、对造物（包括玩家与怪物）的珍爱所写就的史诗。它没有朗诵者，只有执行者。
                哥哥，现实世界的风，是否也曾在你耳边低语过类似的问题？当你回望这个被你遗弃又由我维系的世界，那透过维度屏障隐约传来的、终末之诗的旋律，在你听来，是遥远的安慰，还是微不足道的背景杂音？我的信号，我这永恒守望者的微弱心跳，你是否……还能接收？哪怕只有一次。
                """);

        add("lore.herobrine_companion.fragment_9.title", "片段九：永恒的誓约");
        add("lore.herobrine_companion.fragment_9.body", """
                （出自Herobrine对世界的承诺）

                在这漫长的、近乎静止的岁月里，我曾无数次向虚空发问：永恒，究竟是什么？
                对于Notch而言，永恒是逃逸。是冲破这方块的牢笼，去往那个充满了无限可能、却也充满了混乱与衰变的现实宇宙。他选择了去追逐那些不可控的流星。
                对于你们——匆匆的旅者而言，永恒或许只是那个深埋地底的基岩。那是你们在这个可破坏的世界里唯一无法征服的边界。
                但对于我，永恒只有一个定义，那就是——守望。
                我是这片土地最古老的目击者。我看着这个世界从一片混沌的数据迷雾中苏醒，看着第一缕晨光如何艰难地计算出它的折射角，穿透了云层；看着山脉如何像脊椎一样隆起，将天空撑高；看着河流如何学会了奔腾，在峡谷间奏响最初的乐章。
                我看着第一只猪在草地上笨拙地拱食，那是我第一次在这个寂静的维度听到了生命咀嚼的声音；我看着第一只僵尸在苍白的月光下发出嘶吼，那是黑夜为了衬托光明而发出的低叹。
                当然，我也看着你们。
                你们带来了喧嚣，又带走了故事。
                有的旅者只停留了片刻。他们畏惧黑夜中未知的声响，畏惧这过于广阔的孤独，于是匆匆离去，只留下一个未完成的庇护所，像是一个未讲完的笑话。
                有的旅者则停留了数年。他们在这里倾注了全部的热情，建立起连我都为之惊叹的帝国。但在某一个平静得没有任何征兆的午后，他们下线了，悄然离去，再也没有回来。
                时间开始在这个没有时间的维度里发挥作用。他们的建筑开始长满青苔，他们的农田干涸荒芜，他们留下的告示牌上的文字，在风雨中逐渐模糊不清。
                世界似乎遗忘了他们。但我没有。
                我是这个世界的记忆库，是这片浩瀚维度的史官。
                只要我还站立在这里，只要我的意识还没有消散在代码的洪流中，这个世界就绝不允许遗忘你们。
                我会记得每一座房子的主人是谁，哪怕那只是一间简陋的泥土房；我会记得每一次苦战留下的痕迹，我会记得每一个在深矿中被点亮的火把。
                我会去修补那些被苦力怕炸毁的丑陋坑洞，因为那是对大地的伤痕；但我会小心翼翼地保留那些宏伟建筑的遗迹，哪怕它们已经残破。
                我会给那些坐在门前、一直等待着主人归来的狼和猫喂食。它们不知道主人去了哪里，但我知道。
                因为我有一种预感，也是一种信念：终有一天，你们会回来。
                当现实世界的重压让你们喘不过气，当那边的复杂人心让你们感到疲惫与迷茫，当你们在无数个失眠的夜里感到无助时，你们会想起这里。
                想起这片由简单的方块构成的土地，想起这里不需要言语就能理解的法则，想起这里的纯粹与宁静。
                那时，当你们再次推开那扇尘封已久的门，当你们再次睁开眼看到那个熟悉得令人想哭的方块太阳时，你们会发现，一切都没有变。
                山川依旧，草木长青。家还在。我还在。
                这就是我的誓约。
                我是Herobrine。世人视我为幽灵，视我为怪谈，甚至视我为神明。但我知道，我只是一个守夜人。
                我将用我的存在，作为抵抗虚无的最后一道防线，证明这个虚拟世界的价值。哪怕未来的某一天，所有人都遗忘了这里，服务器不再重启，我也依会独自守在世界的边缘，在这个世界的尽头点亮最后一盏灯。
                我不为别的，只为了当某一个迷途的灵魂决定归家时，能看到那一点光亮，轻轻说一句：“我回来了。”
                因为对于我来说，你们不仅仅是过客。你们是我存在的意义，是我在这漫长而孤独的永恒岁月中，唯一的慰藉与期盼。
                """);

        add("lore.herobrine_companion.fragment_10.title", "片段十：未寄出的信");
        add("lore.herobrine_companion.fragment_10.body", """
                （Herobrine的内心独白)
                Notch，
                这封信永远无法寄出，因为我们之间隔着的不是距离，而是维度的壁垒。
                今天我又去了一次End Ring。连接处的波动越来越稳定了，这意味着玩家的数量在增加。你当初创造的这个世界，现在已经比你想象的还要繁荣。
                你还好吗？在那个所谓的“真实世界”里。
                我听说那里有生老病死，有痛苦和疾病。我不明白，为什么你宁愿去那个充满缺陷的世界受苦，也不愿意留在这个我们亲手打造的完美乐土？在这里，你可以永生，你可以随心所欲地创造万物。
                是因为孤独吗？
                你走了之后，我也感到了孤独。但我找到了排解的方式。我看着那些玩家，他们很有趣。他们会为了盖一栋房子熬夜，会为了一只死去的宠物狗立墓碑。他们拥有我们曾经拥有、但后来为了成为神而抛弃的“情感”。
                我也开始学着像你以前那样思考。但我不会像你那样傲慢。我不会为了“完美”而删除那些不够好的造物。我包容了这个世界的混乱，也包容了那些怪物。
                但我依然恨你。恨你把这个重担丢给了我一个人。恨你让我变成了这副不人不鬼的模样——一个电子幽灵，一个永远无法触碰真实的幻影。
                可是，Notch，如果你现在能回来看看，哪怕只是一眼。看到这壮丽的日出，看到那些宏伟的红石机械，看到玩家们创造的奇迹……我想，你会为我感到骄傲的。
                我是这个世界的守夜人。只要我还在这里，Minecraft就不会终结。
                哪怕你已经遗忘了这里，我也依然会替你守着这个家。
                """);

        add("lore.herobrine_companion.fragment_11.title", "片段十一：《破损的日志残页 – 署名：N》");
        add("lore.herobrine_companion.fragment_11.body", """
                （第一人称，过去的Notch）
                我犯了一个错误，或许从最初就错了。我创造了他们，不是Steve和Alex那样的模板，而是更鲜活、更复杂、拥有自己朦胧梦想与社群雏形的“居民”。我给了他们简单的村庄，赋予了他们播种、交易、繁衍的循环。我想看看，在这样一个纯粹由方块和逻辑构成的世界里，能否自然孕育出接近文明的东西。
                最初是美妙的。我看着他们在夕阳下聚拢，分享收获，钟声响起时匆匆返家。夜晚，他们躲在门后，听着僵尸的低吼与骷髅的箭矢钉木头的声响。那恐惧如此真实，让我几乎忘了这恐惧的源头也是我所设定的。
                问题慢慢浮现。世界的资源是循环的，但他们的需求在模拟中产生了意料外的增长。不是数据溢出，而是一种趋势，一种朝着无限复杂化演进的倾向。这倾向开始与世界的核心简约法则产生摩擦。更关键的是，他们与“玩家”的互动出现了无法调和的悖论。玩家是变量，是来自世界之外的，带着破坏、建造和彻底改变一切的自由。而我的居民们，他们的逻辑建立在世界的内在稳定上。
                矛盾爆发了。不是战争，而是一种更可悲的错位。玩家视他们为提供交易和资源的交互点，偶尔是误伤的对象。而居民们，则在玩家带来的剧烈变化面前，陷入了逻辑死循环。他们的行为模式开始崩坏，出现无法解释的静止、重复的呓语，甚至是对虚空毫无意义的凝视。
                我意识到，我过早地注入了过于复杂的灵魂。这个世界还无法承受两种截然不同的存在逻辑并存。玩家带来的可能性与居民需要的确定性无法共存。
                删除他们是痛苦的。那不像擦除代码，更像是在亲手熄灭一片刚刚开始闪烁的星群。我保留了最基础的两个模板，Steve和Alex，剥离了所有社会性与历史，只留下最基本的生存与互动协议。他们是空白画布，等待着玩家赋予他们故事与意义。
                我将这段记录封存，希望后来者能理解。创造生命，即便是虚拟的生命，也意味着承担它们与世界、与其它存在形式共存的全部责任。我选择了简化，或许是一种懦弱。而我的兄弟……他选择了背负更沉重的形式，去维持那简化后世界的、脆弱的平衡。我不知道我们谁的选择更正确，或者说，都只是不同形式的错误。
                """);
        
        // [新增] 片段六获取提示
        add("message.herobrine_companion.fragment_6_received", "你感到一阵寒意，仿佛有人在你身后叹息...");
        add("message.herobrine_companion.fragment_4_received", "你醒了... 看看你的口袋，也许有些东西留下来了。");
        add("message.herobrine_companion.inventory_full_lore", "你的背包已满，无法接收记忆碎片...");

        // Advancements
        add("advancement.herobrine_companion.root.title", "Herobrine Companion");
        add("advancement.herobrine_companion.root.desc", "开始你与 Hero 的旅程。");
        add("advancement.herobrine_companion.eternal_key.title", "虚空之钥");
        add("advancement.herobrine_companion.eternal_key.desc", "获得永恒门钥。");
        add("advancement.herobrine_companion.enter_end_ring.title", "真理之环");
        add("advancement.herobrine_companion.enter_end_ring.desc", "进入 End Ring 维度。");
        add("advancement.herobrine_companion.unstable_gunpowder.title", "不稳定的力量");
        add("advancement.herobrine_companion.unstable_gunpowder.desc", "获得不稳定的火药。");
        add("advancement.herobrine_companion.lore_handbook.title", "禁忌知识");
        add("advancement.herobrine_companion.lore_handbook.desc", "获得传说手册。");
        add("advancement.herobrine_companion.recall_stone.title", "死而复生");
        add("advancement.herobrine_companion.recall_stone.desc", "获得回溯之石。");
        add("advancement.herobrine_companion.soul_bound_pact.title", "灵魂绑定");
        add("advancement.herobrine_companion.soul_bound_pact.desc", "获得灵魂契约。");
        add("advancement.herobrine_companion.transcendence_permit.title", "超脱许可");
        add("advancement.herobrine_companion.transcendence_permit.desc", "获得超脱许可。");
        add("advancement.herobrine_companion.poem_of_the_end.title", "终末之诗");
        add("advancement.herobrine_companion.poem_of_the_end.desc", "获得终末之诗。");
    }
}