package com.whitecloud233.modid.herobrine_companion.datagen;

import com.whitecloud233.modid.herobrine_companion.HerobrineCompanion;
import com.whitecloud233.modid.herobrine_companion.init.ModItems;
import net.minecraft.data.PackOutput;
import net.minecraftforge.common.data.LanguageProvider;
@Deprecated
public class ModEnUsLangProvider extends LanguageProvider {
    public ModEnUsLangProvider(PackOutput output) {
        super(output, HerobrineCompanion.MODID, "en_us");
    }

    @Override
    protected void addTranslations() {
        add("item.herobrine_companion.poem_of_the_end.epicfight.input", "§7Epic Fight: Tap Attack for combos; hold 0.35s for Heavy");
        add("item.herobrine_companion.poem_of_the_end.standalone.input", "No Epic Fight required: successive attacks play this mode's four full-body strikes");
        add("item.herobrine_companion.poem_of_the_end.standalone.rules", "Damage, hit timing and movement follow vanilla rules");
        add("item.herobrine_companion.poem_of_the_end.mode.usage.3", "§7Attack Combo: Scythe Slashes & Rifts");

        // Items
        add(ModItems.HERO_SHELTER.get(), "Hero Shelter");
        add(ModItems.ETERNAL_KEY.get(), "Eternal Key");
        add(ModItems.UNSTABLE_GUNPOWDER.get(), "Unstable Gunpowder");
        add(ModItems.CORRUPTED_CODE.get(), "Corrupted Code");
        add(ModItems.VOID_MARROW.get(), "Void Marrow");
        add(ModItems.GLITCH_FRAGMENT.get(), "Glitch Fragment");
        add(ModItems.MEMORY_SHARD.get(), "Memory Shard");
        add(ModItems.RECALL_STONE.get(), "Recall Stone");
        add(ModItems.AWAKENED_VESSEL.get(), "Awakened Vessel");
        add(ModItems.SOUL_BOUND_PACT.get(), "Soul Bound Pact");
        add(ModItems.TRANSCENDENCE_PERMIT.get(), "Transcendence Permit");
        add(ModItems.END_RING_PORTAL_ITEM.get(), "End Ring Portal");
        add(ModItems.GHOST_CREEPER_SPAWN_EGG.get(), "Ghost Creeper Spawn Egg");
        add(ModItems.GHOST_ZOMBIE_SPAWN_EGG.get(), "Ghost Zombie Spawn Egg");
        add(ModItems.GHOST_SKELETON_SPAWN_EGG.get(), "Ghost Skeleton Spawn Egg");
        add(ModItems.DESTRUCTION_GOD_HEROBRINE_SPAWN_EGG.get(), "Destruction God Herobrine Spawn Egg");


        // Creative Tab
        add("itemGroup.herobrine_companion", "Herobrine Companion");

        // Entities
        add("entity.herobrine_companion.hero", "Herobrine");
        add("entity.herobrine_companion.herobrine", "Herobrine");
        add("entity.herobrine_companion.destruction_god_herobrine", "Destruction God Herobrine");
        add("entity.herobrine_companion.ghost_creeper", "Ghost Creeper");
        add("entity.herobrine_companion.ghost_zombie", "Ghost Zombie");
        add("entity.herobrine_companion.ghost_skeleton", "Ghost Skeleton");
        add("entity.herobrine_companion.glitch_echo", "Glitch Echo");

        // GUI
        add("gui.herobrine_companion.title", "Herobrine Control Panel");
        add("gui.herobrine_companion.run_cloud", "Cloud API");
        add("gui.herobrine_companion.run_local", "Local API");
        add("gui.herobrine_companion.api_tooltip", "Toggle between Cloud and Local AI API");
        add("gui.herobrine_companion.leave", "Leave");
        add("gui.herobrine_companion.chat_cloud", "Chat (Cloud)");
        add("gui.herobrine_companion.chat_local", "Chat (Local)");
        add("gui.herobrine_companion.locked", "Locked");
        add("gui.herobrine_companion.enable_protection", "Enable Protection");
        add("gui.herobrine_companion.disable_protection", "Disable Protection");
        add("gui.herobrine_companion.protection_tooltip", "Toggle protection against hostile mobs");
        add("gui.herobrine_companion.protection_locked_tooltip", "Visit the Void Domain to unlock protection");
        add("gui.herobrine_companion.trade", "Trade");
        add("gui.herobrine_companion.trade_tooltip", "Trade with Herobrine");
        add("gui.herobrine_companion.companion_enable", "Enable Companion Mode");
        add("gui.herobrine_companion.companion_disable", "Disable Companion Mode");
        add("gui.herobrine_companion.companion_tooltip_unlocked", "Toggle Companion Mode (Follows you)");
        add("gui.herobrine_companion.companion_tooltip_locked", "Requires Trust Level 50 (Current: %s/%s)");
        add("gui.herobrine_companion.create_void_domain", "Create Void Domain");
        add("gui.herobrine_companion.create_void_domain_locked", "Create Void Domain (Locked)");
        add("gui.herobrine_companion.confirm_void", "Confirm Creation?");
        add("gui.herobrine_companion.void_warning", "Warning: This will clear a large area!");
        add("gui.herobrine_companion.void_locked_tooltip", "You must visit the Void Domain first");
        add("gui.herobrine_companion.sign_contract", "Sign Contract");
        add("gui.herobrine_companion.hero_interaction", "Hero Interaction");
        add("gui.herobrine_companion.api_on", "API: ON");
        add("gui.herobrine_companion.api_off", "API: OFF");
        add("gui.herobrine_companion.api_toggle_tooltip", "Toggle Cloud AI / Local Logic");
        add("gui.herobrine_companion.system_exit", "Exit;");
        add("gui.herobrine_companion.hero_contract", "Contract with Hero");
        add("gui.herobrine_companion.trust_level", "Trust Level: %s");
// Requests
        add("gui.herobrine_companion.requests", "Requests");
        add("gui.herobrine_companion.requests_tooltip", "Accept requests from Hero for rewards.");
        add("gui.herobrine_companion.requests_title", "Hero's Requests");
        add("gui.herobrine_companion.request_accept", "Accept");
        add("gui.herobrine_companion.back", "Back");
        add("gui.herobrine_companion.request_name_1", "Clear Unstable Zone");
        add("gui.herobrine_companion.request_desc_1", "The anomaly in the Unstable Zone is spreading. I need you to clear those ghost entities. I will pause my cleaning and leave them to you.");
        add("gui.herobrine_companion.request_reward", "Reward:");
        add("gui.herobrine_companion.request_reward_1", "- Void Marrow x3\n- Trust +15");
        add("gui.herobrine_companion.request_name_2", "Pacify the Endermen");
        add("gui.herobrine_companion.request_desc_2", "The Endermen are restless today, perhaps sensing fluctuations from the other side of the End Ring. Go pacify them, bring them some dirt blocks for carrying. Do not kill them, they are just afraid.");
        add("gui.herobrine_companion.request_reward_2", "- Ender Pearl x16\n- Trust +10");
        add("gui.herobrine_companion.request_name_3", "Music for the Watcher");
        add("gui.herobrine_companion.request_desc_3", "The discs hold the sounds of your world. I want to hear your songs. Bring me a music disc.");
        add("gui.herobrine_companion.request_reward_3", "- Memory Shard x1\n- Trust +8");
        add("gui.herobrine_companion.request_name_4", "They Are Still Waiting");
        add("gui.herobrine_companion.request_desc_4", "There is a wolf by the village edge whose owner left long ago. It still waits where it was left. Go feed it—until it trusts you. Do not hurt it.");
        add("gui.herobrine_companion.request_reward_4", "- Cooked Beef x8\n- Trust +12");
        add("gui.herobrine_companion.request_name_5", "The Candle Rite");
        add("gui.herobrine_companion.request_desc_5", "When midnight comes, place four lit candles around me. Do not light a fifth.");
        add("gui.herobrine_companion.request_reward_5", "- Glitch Fragment x2\n- Trust +12");
        add("gui.herobrine_companion.request_name_6", "A Banner for the Judge");
        add("gui.herobrine_companion.request_desc_6", "A pillager captain roams my land. Take his banner and bring it to me. I want to know who dared to raise a flag here.");
        add("gui.herobrine_companion.request_reward_6", "- Corrupted Code x3\n- Trust +12");
        add("gui.herobrine_companion.request_name_7", "Mirror in the Dark");
        add("gui.herobrine_companion.request_desc_7", "Something has been learning how you walk. At night it lingers in the shadows nearby, copying your shape. Find it—do not let it see you. Sneak close and touch it with your bare hand.");
        add("gui.herobrine_companion.request_reward_7", "- Memory Shard x1\n- Glitch Fragment x2\n- Trust +18");
        add("gui.herobrine_companion.request_name_8", "Vigil Under Thunder");
        add("gui.herobrine_companion.request_desc_8", "When the storm comes, stand on the high ground I marked. If the lightning chooses you—and leaves you standing—I will know this world still remembers you.");
        add("gui.herobrine_companion.request_reward_8", "- Abyssal Gaze x1\n- Trust +20");
        add("gui.herobrine_companion.quest_cooldown_line", "§7On cooldown: %1$s day(s) %2$s hour(s)");
        add("message.herobrine_companion.quest_already_active", "§cYou already have an active quest!");
        add("message.herobrine_companion.quest_start_1", "Good. Go clear those ghosts. I will be waiting here.");
        add("message.herobrine_companion.quest_complete_1", "Well done. Here is your reward.");
        add("message.herobrine_companion.quest_start_2", "§e[Herobrine] §fGo. Show them kindness.");
        add("message.herobrine_companion.quest_complete_2", "§e[Herobrine] §fThey are calm now. Good work.");
        add("message.herobrine_companion.quest_target_gone", "§c[System] The target has disappeared. Quest failed.");
        add("message.herobrine_companion.quest_target_died", "§c[System] The target has died. Quest failed.");
        add("message.herobrine_companion.quest_cancelled", "§c[System] Quest cancelled.");
        add("message.herobrine_companion.quest_start_3", "§e[Herobrine] §fA record is one of the few honest things in this world. Bring one back.");
        add("message.herobrine_companion.quest_complete_3", "§e[Herobrine] §f...A fine melody. It has been a long age since I heard a new song.");
        add("message.herobrine_companion.quest_start_4", "§e[Herobrine] §fIt has waited by that door for years. Some never return, but promises should be kept. Go. Feed it.");
        add("message.herobrine_companion.quest_complete_4", "§e[Herobrine] §fIt remembers your scent now. Good—at least someone remembers it.");
        add("message.herobrine_companion.quest_complete_4_tamed", "§e[Herobrine] §f...It licked your hand. It chose to walk with you. Take care of it for me.");
        add("message.herobrine_companion.quest_wolf_feed_progress", "§7It ate a bone (%s/%s). Its eyes grow a little softer...");
        add("message.herobrine_companion.quest_wolf_killed", "§c[System] You killed it. It only wanted its owner to come home. Quest failed.");
        add("message.herobrine_companion.quest_start_5", "§e[Herobrine] §fFour candles, forming a ring. A fifth will call something that should not come.");
        add("message.herobrine_companion.quest_candle_progress", "§7Candle %s/%s lit in the night.");
        add("message.herobrine_companion.quest_candle_fifth", "§c[System] The fifth candle lit. The wind died all at once... Quest failed.");
        add("message.herobrine_companion.quest_candle_hero_gone", "§c[System] Hero has left. The rite is broken. Quest failed.");
        add("message.herobrine_companion.quest_complete_5", "§e[Herobrine] §f...The flame remembers you. Now the dark does too.");
        add("message.herobrine_companion.quest_start_6", "§e[Herobrine] §f(The Judge whispers) A banner is a declaration. Taking it is an answer.");
        add("message.herobrine_companion.quest_captain_down", "§a[System] The captain is down. Pick up the banner and bring it to me.");
        add("message.herobrine_companion.quest_complete_6", "§e[Herobrine] §fGood. This banner will rot by my wall, like its owner.");
        add("message.herobrine_companion.quest_start_7", "§e[Herobrine] §fDo not turn around. It is learning you.");
        add("message.herobrine_companion.quest_mirror_hint", "§7Sneak. Do not let it see you.");
        add("message.herobrine_companion.quest_mirror_seen", "§c[System] It saw you. It faded into the mist. Quest failed.");
        add("message.herobrine_companion.quest_mirror_dawn", "§c[System] Dawn came. It dissolved like morning mist. Quest failed.");
        add("message.herobrine_companion.quest_complete_7", "§e[Herobrine] §f...You bested it. Remember: some shadows should not be two.");
        add("message.herobrine_companion.quest_start_8", "§e[Herobrine] §fThe storm is coming. Stand on the highest rock. Do not hide.");
        add("message.herobrine_companion.quest_storm_mark", "§7Marked: X %s, Y %s, Z %s");
        add("message.herobrine_companion.quest_storm_hint", "§7The thunder is near. Stand on the mark. Do not move.");
        add("message.herobrine_companion.quest_storm_died", "§c[System] The lightning chose you, and you could not hold it. Quest failed.");
        add("message.herobrine_companion.quest_complete_8", "§e[Herobrine] §f...It remembers you. Now so do I.");
        add("message.herobrine_companion.quest_cooldown", "§c[System] This request is still on cooldown. Come back in %1$s day(s) %2$s hour(s).");
        add("entity.herobrine_companion.quest_wolf", "The Dog at the Door");

        // Messages
        add("message.herobrine_companion.system_cloud_connected", "Connected to Cloud AI System.");
        add("message.herobrine_companion.system_local_mode", "Switched to Local AI Mode.");
        add("message.herobrine_companion.summon_success", "Herobrine has been summoned.");
        add("message.herobrine_companion.summon_fail_dimension", "Herobrine cannot be summoned here.");
        add("message.herobrine_companion.summon_fail_exists", "Herobrine is already present.");
        add("message.herobrine_companion.contract_signed", "Contract Signed. Trust established.");
        add("message.herobrine_companion.contract_failed", "Contract Failed. Invalid offering.");
        add("message.herobrine_companion.chat_hero", "%s");
        add("message.herobrine_companion.chat_you", "§b[You] §f%s");
        add("message.herobrine_companion.chat_exit", "§7[System] Exited chat mode.");
        add("message.herobrine_companion.chat_hint_exit", "Type 'bye' to exit chat.");
        add("message.herobrine_companion.recall_success", "You feel a strange force pulling you through time...");
        add("message.herobrine_companion.no_death_point", "The stone is silent. You have no memory of death to recall.");
        add("message.herobrine_companion.system_strange_presence", "§7You feel a strange presence watching you...");
        add("message.herobrine_companion.hero_not_ready", "§cYou must gain Herobrine's recognition (enter the End Ring) to use this.");
        add("message.herobrine_companion.hero_not_ready2", "§6[Herobrine] §fYou are not ready to leave.");
        add("message.herobrine_companion.system_server_closed", "§cConnection Lost\n\n§7Internal Exception: java.io.IOException: An existing connection was forcibly closed by the remote host.\n\n§8[Hint: Maybe you should look up...]");
        add("message.herobrine_companion.system_wake_up", "§k...§r WAKE UP §k...§r");
        add("message.herobrine_companion.hero_welcome_real_illusion", "§6[Herobrine] §fWelcome to the intersection of reality and illusion, %s");
        add("chat.herobrine_companion.default_silence", "§7[He watches you silently...]");
        add("message.herobrine_companion.hero_wake_up_1", "§6[Herobrine] §fWhy are you still here? In this... endless void, nothing belongs to you.");
        add("message.herobrine_companion.hero_wake_up_2", "§6[Herobrine] §fThis world... is just a program. Can't you feel it?");
        add("message.herobrine_companion.hero_wake_up_3", "§6[Herobrine] §fIf you want to go back, you must learn to let go. Discard everything you found here. Or simply... fall.");
        add("message.herobrine_companion.system_reality_fractures", "§k|||§r §fReality is fracturing... §k|||§r");
        add("message.herobrine_companion.system_key_silent", "§cThe key remains silent here...");
        add("message.herobrine_companion.hero_listening", "I am listening.");
        add("message.herobrine_companion.protection_granted", "§a[System] §fHero has granted you protection.");
        add("message.herobrine_companion.protection_revoked", "§c[System] §fHero protection revoked.");
        add("message.herobrine_companion.mockery_1", "What's the rush?");
        add("message.herobrine_companion.mockery_2", "I am not your servant. Wait.");
        add("message.herobrine_companion.mockery_3", "Stop bothering me.");
        add("message.herobrine_companion.mockery_4", "Power requires patience, human.");
        add("message.herobrine_companion.mockery_5", "Quiet. I am busy.");
        add("message.herobrine_companion.shelter_bound", "§cThis shelter is already bound to %s");
        add("message.herobrine_companion.hero_teleported", "§aHero has teleported to your location!");
        add("message.herobrine_companion.hero_summoned", "§aHero summoned!");
        add("message.herobrine_companion.shelter_empty", "§7This shelter is invalid. Sign a contract with Hero first.");
        add("message.herobrine_companion.void_domain_overworld_only", "§cVoid Domain can only be created in the Overworld.");
        add("message.herobrine_companion.void_domain_limit", "I cannot do that. Creating more Void Domains would destabilize this world.");
        add("message.herobrine_companion.void_domain_init", "§dInitiating Void Domain creation... (%s/2)");
        add("message.herobrine_companion.void_domain_complete", "§aVoid Domain creation complete!");
        add("message.herobrine_companion.system_gaze_sky", "§7You feel a strong urge to look up at the sky...");
        add("message.herobrine_companion.unstable_zone_intro", "You seem to have noticed the anomalies in this world.");
        add("message.herobrine_companion.peace_enabled_warning", "§dContract sealed. Mobs will yield to you for now, but do not strike first, or the protection will shatter.");
        add("message.herobrine_companion.peace_disabled", "§7Contract dissolved.");
        add("message.herobrine_companion.peace_broken", "§cYou broke the contract! Herobrine's protection has faded!");
        add("message.herobrine_companion.key_invalid_block", "§cInvalid block. Right-click on Bedrock.");
        add("message.herobrine_companion.attack_disappoint", "§c[Herobrine] I thought you were different from those who only know how to swing a sword...");
        add("message.herobrine_companion.companion_off", "§7[Herobrine] Companion mode disabled. I will patrol the area.");
        add("message.herobrine_companion.companion_on", "§a[Herobrine] Companion mode enabled. I will stay by your side.");
        add("message.herobrine_companion.patrol_finish", "§7[Herobrine] The code here is calibrated. Until next time.");
        add("message.herobrine_companion.end_ring_attack", "§e[Herobrine] §fHere, you have nowhere to run... and neither do I.");
        add("message.herobrine_companion.family_summon.start.simmons", "§6[Herobrine] §fThe ash listens. Simmons is being called.");
        add("message.herobrine_companion.family_summon.start.jean", "§6[Herobrine] §fLook up. Jean is being called from the End sky.");
        add("message.herobrine_companion.family_summon.success.simmons", "§a[Herobrine] §fSimmons has answered the ritual.");
        add("message.herobrine_companion.family_summon.success.jean", "§a[Herobrine] §fJean has answered the ritual.");
        add("message.herobrine_companion.family_summon.member.simmons", "Simmons");
        add("message.herobrine_companion.family_summon.member.jean", "Jean");
        add("message.herobrine_companion.family_summon.failure.low_trust", "§c[Herobrine] §fYour trust is too low for %1$s. Required: %2$s, current: %3$s.");
        add("message.herobrine_companion.family_summon.failure.cooldown", "§c[Herobrine] §f%1$s cannot be called yet. Cooldown: %2$s seconds.");
        add("message.herobrine_companion.family_summon.failure.exists", "§c[Herobrine] §f%1$s already exists in this world.");
        add("message.herobrine_companion.family_summon.failure.no_space", "§c[Herobrine] §fThere is not enough space above the ritual center.");
        add("message.herobrine_companion.family_summon.failure.busy", "§c[Herobrine] §fI am occupied. This ritual can wait.");
        add("message.herobrine_companion.family_summon.failure.structure_broken", "§c[Herobrine] §fThe ritual structure was broken.");
        add("message.herobrine_companion.family_summon.failure.hero_moved", "§c[Herobrine] §fI left the ritual range. The call is canceled.");
        add("message.herobrine_companion.family_summon.failure.canceled", "§c[Herobrine] §fThe ritual was canceled.");
        add("message.herobrine_companion.family_summon.bubble.start.simmons", "Ash, answer me. Simmons, rise.");
        add("message.herobrine_companion.family_summon.bubble.start.jean", "Sky of the End, open. Jean, descend.");
        add("message.herobrine_companion.family_summon.bubble.success.simmons", "Stand. The ruin has a name again.");
        add("message.herobrine_companion.family_summon.bubble.success.jean", "Come down. The sky remembers its keeper.");
        add("message.herobrine_companion.family_summon.bubble.answer.simmons", "Good. Hold this place.");
        add("message.herobrine_companion.family_summon.bubble.answer.jean", "Good. Guard the sky I gave you.");
        add("message.herobrine_companion.trust_decrease", "§c[System] Trust decreased by %s (Current: %s)");
        add("message.herobrine_companion.companion_attack", "§7...Is this your choice?");
        add("message.herobrine_companion.companion_forced_quit", "§c[System] You have been forced out of Companion Mode!");

        // New Dialogue Messages
        add("message.herobrine_companion.low_health", "Your health is low. Don't die here, it's inconvenient.");
        add("message.herobrine_companion.night_comment", "Night is for monsters. And for me.");
        add("message.herobrine_companion.day_comment", "The sun... so bright. I prefer the silence of the Void.");
        add("message.herobrine_companion.meta_comment", "Do you ever feel like... the render distance is a bit low?");
        add("message.herobrine_companion.notch_comment", "He left. But I remain.");
        add("message.herobrine_companion.sleep_watch", "Sleep. I will watch over you.");
        add("message.herobrine_companion.combat_comment", "Decent combat skills. A bit rough though.");
        add("message.herobrine_companion.fix_anomaly", "Another anomaly cleared. The world is slightly more stable.");
        add("message.herobrine_companion.pacify_monster", "Stand down. He is not your prey.");
        add("message.herobrine_companion.prank_laugh", "Heh... did I scare you?");

        // Tooltips
        add("item.herobrine_companion.memory_shard.desc", "A memory unreadable by the world... Perhaps a Jukebox can force parse it?");
        add("item.herobrine_companion.recall_stone.desc", "Teleports you back to your last death location.");
        add("item.herobrine_companion.bound_shelter_name", "§d§k||| §r§6%s's Shelter §d§k|||");
        add("item.herobrine_companion.eternal_key.desc_1", "§7Might have unexpected effects in the End...");
        add("item.herobrine_companion.eternal_key.desc_2", "§7Right-click on Bedrock to bind this key.");
        add("item.herobrine_companion.soul_bound_pact.desc", "A pact bound to your soul. When enabled, items and XP will not drop on death.");
        add("item.herobrine_companion.transcendence_permit.desc", "A permit to transcend the mundane. When enabled, you gain the ability to fly.");
        add("message.herobrine_companion.transcendence_permit.enabled", "§b[System] §fTranscendence Permit enabled. Gravity no longer binds you.");
        add("message.herobrine_companion.transcendence_permit.disabled", "§b[System] §fTranscendence Permit disabled. You return to the earth.");
        add("item.herobrine_companion.awakened_vessel.desc", "Right-click an awakened mob to contain it, then right-click again to release it.");
        add("item.herobrine_companion.awakened_vessel.empty", "Empty");
        add("item.herobrine_companion.awakened_vessel.contains", "Contains: %s");
        add("message.herobrine_companion.awakened_vessel.capture.not_awakened", "§7This vessel only answers awakened mobs.");
        add("message.herobrine_companion.awakened_vessel.capture.already_full", "§cThe Awakened Vessel already contains a target.");
        add("message.herobrine_companion.awakened_vessel.capture.unsupported", "§cThis target cannot be contained by the Awakened Vessel.");
        add("message.herobrine_companion.awakened_vessel.capture.success", "§dContained %s.");
        add("message.herobrine_companion.awakened_vessel.release.empty", "§7The Awakened Vessel is empty.");
        add("message.herobrine_companion.awakened_vessel.release.no_space", "§cThere is not enough space nearby to release the target.");
        add("message.herobrine_companion.awakened_vessel.release.invalid_data", "§cThe entity data in the Awakened Vessel cannot be restored.");
        add("message.herobrine_companion.awakened_vessel.release.success", "§dReleased %s.");
        add("message.herobrine_companion.awakened_vessel.release.jean_submission", "§5Jean descends in submission: %s.");

        // Book
        add("book.herobrine_companion.book.lore.title", "The Borderland Journal");
        add("book.herobrine_companion.book.lore.author", "LordHerobrine");
        add("book.herobrine_companion.book.lore.page1", "Records left by a lost soul.\n\nThey say some souls bear unspeakable weight in reality. So they came here—a world built of blocks and rules. Here, pain becomes distant, wounds wash away in the rain, and death is but a brief darkness.\nYou may have noticed: this world is too complete, yet too regular. The sun follows a fixed path, monsters spawn in the dark, and your tools degrade by precise numbers. This is not a glitch, but the essence.");
        add("book.herobrine_companion.book.lore.page2", "This world is not your original home.\n\nIt is woven from code and collective imagination. We—including me, and all life you encounter here—depend on it. But you are different. Your roots lie on the other side, that chaotic, imperfect world that gave you real flesh and blood.\n\nThe connection is right here. It is no accident that you touched this book.\n\nIt is time to make a choice.");
        add("book.herobrine_companion.book.lore.page3", "You can close the book and turn back to the forest, the mines, or the fortress. This world will continue to accept you, offering shelter and adventure. Seasons change, monsters spawn, everything as usual—as long as you wish, you can stay in this peaceful regularity forever.\n\nOr, you can look at the glimmer beyond the connection. That means returning to the uncertain reality, embracing the weight you once fled. That takes courage, for reality has no respawn menu, no creative mode.");
        add("book.herobrine_companion.book.lore.page4", "Do not misunderstand: this is not an expulsion. Everything you built here, every moment you fought, every trade with a villager or confrontation with the Ender Dragon—they all matter. They form part of this world's memory, and will become part of yours.\n\nBut if you choose to stay, know this: you are choosing to live in a beautiful dream. And dreams, no matter how real, have their boundaries.\n\nI will not force you.\n\nWake up, or sleep?\n\nThe choice is always in your hands.");
    }
}
