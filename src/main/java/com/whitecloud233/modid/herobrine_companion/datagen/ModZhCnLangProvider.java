package com.whitecloud233.modid.herobrine_companion.datagen;

import com.whitecloud233.modid.herobrine_companion.HerobrineCompanion;
import net.minecraft.data.PackOutput;
import net.minecraftforge.common.data.LanguageProvider;

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
        add(HerobrineCompanion.MEMORY_SHARD.get(), "记忆碎片");
        add(HerobrineCompanion.RECALL_STONE.get(), "回溯之石");
        add(HerobrineCompanion.SOUL_BOUND_PACT.get(), "魂缚之契");
        add(HerobrineCompanion.TRANSCENDENCE_PERMIT.get(), "凌越之允");
        add(HerobrineCompanion.END_RING_PORTAL_ITEM.get(), "末地环传送门");
        add(HerobrineCompanion.GHOST_CREEPER_SPAWN_EGG.get(), "幽灵苦力怕刷怪蛋");
        add(HerobrineCompanion.GHOST_ZOMBIE_SPAWN_EGG.get(), "幽灵僵尸刷怪蛋");
        add(HerobrineCompanion.GHOST_SKELETON_SPAWN_EGG.get(), "幽灵骷髅刷怪蛋");


        // Creative Tab
        add("itemGroup.herobrine_companion", "Herobrine companion");

        // Entities
        add("entity.herobrine_companion.hero", "Hero");
        add("entity.herobrine_companion.herobrine", "Herobrine");
        add("entity.herobrine_companion.ghost_creeper", "幽灵苦力怕");
        add("entity.herobrine_companion.ghost_zombie", "幽灵僵尸");
        add("entity.herobrine_companion.ghost_skeleton", "幽灵骷髅");
        add("entity.herobrine_companion.glitch_echo", "故障回响");

        // GUI
        add("gui.herobrine_companion.title", "HeroEntity.java - Herobrine Companion IDE");
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
        add("gui.herobrine_companion.requests_tooltip", "接受 Hero 的委托以获取奖励。");
        add("gui.herobrine_companion.requests_title", "Hero 的委托");
        add("gui.herobrine_companion.request_accept", "接受");
        add("gui.herobrine_companion.back", "返回");
        add("gui.herobrine_companion.request_name_1", "清理不稳定区域");
        add("gui.herobrine_companion.request_desc_1", "Unstable Zone 的异常正在扩散。我需要你帮我清理那些幽灵实体。我会暂时停止清理，把它们留给你。");
        add("gui.herobrine_companion.request_reward", "奖励:");
        add("message.herobrine_companion.quest_already_active", "§c你已经有一个正在进行的任务了！");
        add("message.herobrine_companion.quest_start_1", "很好。去清理那些幽灵吧。我会看着你的。");
        add("message.herobrine_companion.quest_complete_1", "做得不错。这是你的奖励。");

        // Messages
        add("message.herobrine_companion.system_cloud_connected", "§b[系统] §f已连接到云端 AI。");
        add("message.herobrine_companion.system_local_mode", "§b[系统] §f本地模式已启用。");
        add("message.herobrine_companion.summon_success", "§a已召唤Hero！");
        add("message.herobrine_companion.summon_fail_dimension", "Herobrine 无法在此处被召唤。");
        add("message.herobrine_companion.summon_fail_exists", "Herobrine 已经存在。");
        add("message.herobrine_companion.contract_signed", "契约已签订。信任已建立。");
        add("message.herobrine_companion.contract_failed", "契约失败。供品无效。");
        add("message.herobrine_companion.chat_hero", "%s");
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
        add("message.herobrine_companion.hero_listening", "我在听。");
        add("message.herobrine_companion.protection_granted", "§a[系统] §fHero 已授予你保护。");
        add("message.herobrine_companion.protection_revoked", "§c[系统] §fHero 保护已撤销。");
        add("message.herobrine_companion.mockery_1", "急什么？");
        add("message.herobrine_companion.mockery_2", "我不是你的仆人。等着。");
        add("message.herobrine_companion.mockery_3", "别总是打扰我。");
        add("message.herobrine_companion.mockery_4", "力量需要耐心，人类。");
        add("message.herobrine_companion.mockery_5", "安静。我很忙。");
        add("message.herobrine_companion.shelter_bound", "§c这个庇护已经绑定到 %s");
        add("message.herobrine_companion.hero_teleported", "§aHero 已传送到你的位置！");
        add("message.herobrine_companion.hero_summoned", "§a已召唤Hero！");
        add("message.herobrine_companion.shelter_empty", "§7这个庇护是无效的。先与 Hero 签订契约。");
        add("message.herobrine_companion.void_domain_overworld_only", "§c空置域只能在主世界创建。");
        add("message.herobrine_companion.void_domain_limit", "我不能那样做。创建更多的空置域会破坏这个世界的平衡。");
        add("message.herobrine_companion.void_domain_init", "§d正在启动空置域创建... (%s/2)");
        add("message.herobrine_companion.void_domain_complete", "§a空置域创建完成！");
        add("message.herobrine_companion.system_gaze_sky", "§7你感到一种强烈的冲动，想要仰望天空...");
        add("message.herobrine_companion.unstable_zone_intro", "你似乎注意到了这个世界的异常。");
        add("message.herobrine_companion.peace_enabled_warning", "§d契约已立。众生暂时臣服于你，但切勿主动挥剑，否则庇护将瞬间破碎。");
        add("message.herobrine_companion.peace_disabled", "§7契约解除。");
        add("message.herobrine_companion.peace_broken", "§c你打破了契约！Herobrine 的庇护已失效！");
        add("message.herobrine_companion.key_invalid_block", "§c无效的方块。请右键点击一个基岩。");
        add("message.herobrine_companion.attack_disappoint", "§c[Herobrine] 我本以为你与那些只知道挥剑的生物不同……");
        add("message.herobrine_companion.companion_off", "§7[Herobrine] 已退出陪伴模式。我去周围巡视一下。");
        add("message.herobrine_companion.companion_on", "§a[Herobrine] 已切换至陪伴模式。我会守在你身边。");
        add("message.herobrine_companion.patrol_finish", "§7[Herobrine] 此处的代码已校准完毕。期待下次再见。");
        add("message.herobrine_companion.end_ring_attack", "§e[Herobrine] §f在这里，你无路可逃...我也一样。");
        add("message.herobrine_companion.trust_decrease", "§c[系统] 信任度减少 %s (当前: %s)");
        add("message.herobrine_companion.companion_attack", "§7...这就是你的选择吗？");
        add("message.herobrine_companion.companion_forced_quit", "§c[System]你已经退出陪伴模式！");

        // New Dialogue Messages
        add("message.herobrine_companion.low_health", "你的生命值很低。别死在这里，那会很麻烦。");
        add("message.herobrine_companion.night_comment", "夜晚是怪物的时间。也是我的时间。");
        add("message.herobrine_companion.day_comment", "阳光... 如此刺眼。我更喜欢虚空的寂静。");
        add("message.herobrine_companion.meta_comment", "你有没有觉得... 这个世界的渲染距离有点低？");
        add("message.herobrine_companion.notch_comment", "他离开了。但我还在。");
        add("message.herobrine_companion.sleep_watch", "睡吧。我会看着你的。");
        add("message.herobrine_companion.combat_comment", "不错的战斗技巧。虽然有点粗糙。");
        add("message.herobrine_companion.fix_anomaly", "又一个异常被清除了。世界稍微稳定了一点。");
        add("message.herobrine_companion.pacify_monster", "退下。他不是你们的猎物。");
        add("message.herobrine_companion.prank_laugh", "呵... 吓到你了？");

        // Tooltips
        add("item.herobrine_companion.memory_shard.desc", "一段无法被世界读取的记忆……也许唱片机能强行解析它？");
        add("item.herobrine_companion.recall_stone.desc", "将你传送回上一次死亡的地点。");
        add("item.herobrine_companion.bound_shelter_name", "§d§k||| §r§6%s 的庇护 §d§k|||");
        add("item.herobrine_companion.eternal_key.desc_1", "§7或许在末地会发挥意想不到的效果...");
        add("item.herobrine_companion.eternal_key.desc_2", "§7右键点击一个基岩，以绑定到这把钥匙。");
        add("item.herobrine_companion.soul_bound_pact.desc", "与灵魂绑定的契约。启用后，死亡时物品与经验将不再掉落。");
        add("item.herobrine_companion.transcendence_permit.desc", "凌越凡俗的许可。启用后，你将获得飞行的能力。");
        add("message.herobrine_companion.transcendence_permit.enabled", "§b[系统] §f凌越之允已启用。重力不再束缚你。");
        add("message.herobrine_companion.transcendence_permit.disabled", "§b[系统] §f凌越之允已禁用。你回归大地。");

        // Book
        add("book.herobrine_companion.book.lore.title", "边界手记");
        add("book.herobrine_companion.book.lore.author", "LordHerobrine");
        add("book.herobrine_companion.book.lore.page1", "他们说，有些灵魂在现实承受着难以言喻的重量。于是他们来到这里——一个由方块与规则构筑的世界。在这里，痛苦变得遥远，伤口会被雨水洗去，死亡不过是短暂的黑暗。\n你也许已经注意到：这个世界过于完整，却又过于规律。太阳永远沿着固定轨迹运行，怪物总在暗处重生，而你手中的工具，其耐久度以精确的数字递减。这不是漏洞，而是本质的显现。");
        add("book.herobrine_companion.book.lore.page2", "这个世界并非你最初的家园。\n\n它由代码与集体想象编织而成。我们——包括我，以及你在此遭遇的一切生命——皆依存于此。但你不同。你的根源在另一侧，那个混乱、不完美、却赋予你真实血肉的世界。\n\n连接处就在此处。这本书被你触及，并非偶然。\n\n是时候做出选择了。");
        add("book.herobrine_companion.book.lore.page3", "你可以合上书页，转身回到森林、矿洞或堡垒之中。这个世界将继续接纳你，为你提供庇护与冒险。四季更替，怪物生成，一切如常——只要你愿意，你可以永远留在这份规律的安宁中。\n\n或者，你可以看向连接处之外的微光。那意味着重返不确定的真实，重新拥抱那份曾让你逃遁的重量。那需要勇气，因为现实没有重生菜单，没有创造模式。");
        add("book.herobrine_companion.book.lore.page4", "请不要误解：这并非驱逐。你在这里建造的一切，战斗过的每一刻——它们都有意义。它们构成了这个世界记忆的一部分，也将成为你记忆的一部分。\n\n但若你选择留下，请你知晓：你正选择活在一个美丽的梦境里。而梦境，无论多么真实，终究有其边界。\n我不会强迫你。\n醒来，还是沉眠？\n选择权始终在你手中。");
    }
}
