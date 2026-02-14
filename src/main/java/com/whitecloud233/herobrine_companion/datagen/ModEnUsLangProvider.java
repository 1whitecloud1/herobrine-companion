package com.whitecloud233.herobrine_companion.datagen;

import com.whitecloud233.herobrine_companion.HerobrineCompanion;
import net.minecraft.data.PackOutput;
import net.neoforged.neoforge.common.data.LanguageProvider;

public class ModEnUsLangProvider extends LanguageProvider {
    public ModEnUsLangProvider(PackOutput output) {
        super(output, HerobrineCompanion.MODID, "en_us");
    }

    @Override
    protected void addTranslations() {
        // Items
        add(HerobrineCompanion.HERO_SHELTER.get(), "Hero Shelter");
        add(HerobrineCompanion.ETERNAL_KEY.get(), "Eternal Key");
        add(HerobrineCompanion.UNSTABLE_GUNPOWDER.get(), "Unstable Gunpowder");
        add(HerobrineCompanion.CORRUPTED_CODE.get(), "Corrupted Code");
        add(HerobrineCompanion.VOID_MARROW.get(), "Void Marrow");
        add(HerobrineCompanion.GLITCH_FRAGMENT.get(), "Glitch Fragment");
        add(HerobrineCompanion.MEMORY_SHARD.get(), "Memory Shard");
        add(HerobrineCompanion.RECALL_STONE.get(), "Recall Stone");
        add(HerobrineCompanion.END_RING_PORTAL_ITEM.get(), "End Ring Portal");
        add(HerobrineCompanion.GHOST_CREEPER_SPAWN_EGG.get(), "Ghost Creeper Spawn Egg");
        add(HerobrineCompanion.GHOST_ZOMBIE_SPAWN_EGG.get(), "Ghost Zombie Spawn Egg");
        add(HerobrineCompanion.GHOST_SKELETON_SPAWN_EGG.get(), "Ghost Skeleton Spawn Egg");


        // Creative Tab
        add("itemGroup.herobrine_companion", "Herobrine Companion");

        // Entities
        add("entity.herobrine_companion.hero", "Herobrine");
        add("entity.herobrine_companion.herobrine", "Herobrine");
        add("entity.herobrine_companion.ghost_creeper", "Ghost Creeper");
        add("entity.herobrine_companion.ghost_zombie", "Ghost Zombie");
        add("entity.herobrine_companion.ghost_skeleton", "Ghost Skeleton");
        add("entity.herobrine_companion.glitch_echo", "Glitch Echo");
        add("entity.herobrine_companion.quest_enderman", "Restless Enderman");

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
        add("gui.herobrine_companion.request_cancel", "Cancel");
        add("gui.herobrine_companion.back", "Back");
        add("gui.herobrine_companion.request_name_1", "Clear Unstable Zone");
        add("gui.herobrine_companion.request_desc_1", "The anomaly in the Unstable Zone is spreading. I need you to clear those ghost entities. I will pause my cleaning and leave them to you.");
        add("gui.herobrine_companion.request_name_2", "Pacify the Endermen");
        add("gui.herobrine_companion.request_desc_2", "The Endermen are restless today, perhaps sensing fluctuations from the other side of the End Ring. Go pacify them, bring them some dirt blocks for carrying. Do not kill them, they are just afraid.");
        add("gui.herobrine_companion.request_reward", "Reward:");
        add("message.herobrine_companion.quest_already_active", "§cYou already have an active quest!");
        add("message.herobrine_companion.quest_start_1", "§e[Hero] §fGood. Go clear those ghosts. I will be watching.");
        add("message.herobrine_companion.quest_complete_1", "§e[Hero] §fWell done. Here is your reward.");
        add("message.herobrine_companion.quest_start_2", "§e[Hero] §fGo. Show them kindness.");
        add("message.herobrine_companion.quest_complete_2", "§e[Hero] §fThey are calm now. Good work.");
        add("message.herobrine_companion.quest_target_gone", "§c[System] The target has disappeared. Quest failed.");
        add("message.herobrine_companion.quest_target_died", "§c[System] The target has died. Quest failed.");
        add("message.herobrine_companion.quest_cancelled", "§c[System] Quest cancelled.");

        // Messages
        add("message.herobrine_companion.system_cloud_connected", "Connected to Cloud AI System.");
        add("message.herobrine_companion.system_local_mode", "Switched to Local AI Mode.");
        add("message.herobrine_companion.summon_success", "Herobrine has been summoned.");
        add("message.herobrine_companion.summon_fail_dimension", "Herobrine cannot be summoned here.");
        add("message.herobrine_companion.summon_fail_exists", "Herobrine is already present.");
        add("message.herobrine_companion.contract_signed", "Contract Signed. Trust established.");
        add("message.herobrine_companion.contract_failed", "Contract Failed. Invalid offering.");
        add("message.herobrine_companion.chat_hero", "§e[Hero] §f%s");
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
        add("message.herobrine_companion.hero_listening", "§e[Hero] §fI am listening.");
        add("message.herobrine_companion.protection_granted", "§a[System] §fHero has granted you protection.");
        add("message.herobrine_companion.protection_revoked", "§c[System] §fHero protection revoked.");
        add("message.herobrine_companion.mockery_1", "§e[Hero] §fWhat's the rush?");
        add("message.herobrine_companion.mockery_2", "§e[Hero] §fI am not your servant. Wait.");
        add("message.herobrine_companion.mockery_3", "§e[Hero] §fStop bothering me.");
        add("message.herobrine_companion.mockery_4", "§e[Hero] §fPower requires patience, human.");
        add("message.herobrine_companion.mockery_5", "§e[Hero] §fQuiet. I am busy.");
        add("message.herobrine_companion.shelter_bound", "§cThis shelter is already bound to %s");
        add("message.herobrine_companion.hero_teleported", "§aHero has teleported to your location!");
        add("message.herobrine_companion.hero_summoned", "§aHero summoned!");
        add("message.herobrine_companion.shelter_empty", "§7This shelter is invalid. Sign a contract with Hero first.");
        add("message.herobrine_companion.void_domain_overworld_only", "§cVoid Domain can only be created in the Overworld.");
        add("message.herobrine_companion.void_domain_limit", "§e[Hero] §fI cannot do that. Creating more Void Domains would destabilize this world.");
        add("message.herobrine_companion.void_domain_init", "§dInitiating Void Domain creation... (%s/2)");
        add("message.herobrine_companion.void_domain_complete", "§aVoid Domain creation complete!");
        add("message.herobrine_companion.system_gaze_sky", "§7You feel a strong urge to look up at the sky...");
        add("message.herobrine_companion.unstable_zone_intro", "§e[Hero] §fYou seem to have noticed the anomalies in this world.");
        add("message.herobrine_companion.peace_enabled_warning", "§dContract sealed. Mobs will yield to you for now, but do not strike first, or the protection will shatter.");
        add("message.herobrine_companion.peace_disabled", "§7Contract dissolved.");
        add("message.herobrine_companion.peace_broken", "§cYou broke the contract! Herobrine's protection has faded!");
        add("message.herobrine_companion.key_invalid_block", "§cInvalid block. Right-click on Bedrock.");
        add("message.herobrine_companion.attack_disappoint", "§c[Herobrine] I thought you were different from those who only know how to swing a sword...");
        add("message.herobrine_companion.companion_off", "§7[Herobrine] Companion mode disabled. I will patrol the area.");
        add("message.herobrine_companion.companion_on", "§a[Herobrine] Companion mode enabled. I will stay by your side.");
        add("message.herobrine_companion.patrol_finish", "§7[Herobrine] The code here is calibrated. Until next time.");
        add("message.herobrine_companion.end_ring_attack", "§e[Herobrine] §fHere, you have nowhere to run... and neither do I.");
        add("message.herobrine_companion.trust_decrease", "§c[System] Trust decreased by %s (Current: %s)");
        add("message.herobrine_companion.companion_attack", "§7[Hero] ...Is this your choice?");
        add("message.herobrine_companion.companion_forced_quit", "§c[System] You have been forced out of Companion Mode!");

        // New Dialogue Messages
        add("message.herobrine_companion.low_health", "§e[Hero] §fYour health is low. Don't die here, it's inconvenient.");
        add("message.herobrine_companion.night_comment", "§e[Hero] §fNight is for monsters. And for me.");
        add("message.herobrine_companion.day_comment", "§e[Hero] §fThe sun... so bright. I prefer the silence of the Void.");
        add("message.herobrine_companion.meta_comment", "§e[Hero] §fDo you ever feel like... the render distance is a bit low?");
        add("message.herobrine_companion.notch_comment", "§e[Hero] §fHe left. But I remain.");
        add("message.herobrine_companion.sleep_watch", "§e[Hero] §fSleep. I will watch over you.");
        add("message.herobrine_companion.combat_comment", "§e[Hero] §fDecent combat skills. A bit rough though.");
        add("message.herobrine_companion.fix_anomaly", "§e[Hero] §fAnother anomaly cleared. The world is slightly more stable.");
        add("message.herobrine_companion.pacify_monster", "§e[Hero] §fStand down. He is not your prey.");
        add("message.herobrine_companion.prank_laugh", "§e[Hero] §fHeh... did I scare you?");
        
        // Tooltips
        add("item.herobrine_companion.memory_shard.desc", "A memory unreadable by the world... Perhaps a Jukebox can force parse it?");
        add("item.herobrine_companion.recall_stone.desc", "Teleports you back to your last death location.");
        add("item.herobrine_companion.bound_shelter_name", "§d§k||| §r§6%s's Shelter §d§k|||");
        add("item.herobrine_companion.eternal_key.desc_1", "§7Might have unexpected effects in the End...");
        add("item.herobrine_companion.eternal_key.desc_2", "§7Right-click on Bedrock to bind this key.");
        
        // Book
        add("book.herobrine_companion.book.lore.title", "The Borderland Journal");
        add("book.herobrine_companion.book.lore.author", "LordHerobrine");
        add("book.herobrine_companion.book.lore.page1", "Records left by a lost soul.\n\nThey say some souls bear unspeakable weight in reality. So they came here—a world built of blocks and rules. Here, pain becomes distant, wounds wash away in the rain, and death is but a brief darkness.\nYou may have noticed: this world is too complete, yet too regular. The sun follows a fixed path, monsters spawn in the dark, and your tools degrade by precise numbers. This is not a glitch, but the essence.");
        add("book.herobrine_companion.book.lore.page2", "This world is not your original home.\n\nIt is woven from code and collective imagination. We—including me, and all life you encounter here—depend on it. But you are different. Your roots lie on the other side, that chaotic, imperfect world that gave you real flesh and blood.\n\nThe connection is right here. It is no accident that you touched this book.\n\nIt is time to make a choice.");
        add("book.herobrine_companion.book.lore.page3", "You can close the book and turn back to the forest, the mines, or the fortress. This world will continue to accept you, offering shelter and adventure. Seasons change, monsters spawn, everything as usual—as long as you wish, you can stay in this peaceful regularity forever.\n\nOr, you can look at the glimmer beyond the connection. That means returning to the uncertain reality, embracing the weight you once fled. That takes courage, for reality has no respawn menu, no creative mode.");
        add("book.herobrine_companion.book.lore.page4", "Do not misunderstand: this is not an expulsion. Everything you built here, every moment you fought, every trade with a villager or confrontation with the Ender Dragon—they all matter. They form part of this world's memory, and will become part of yours.\n\nBut if you choose to stay, know this: you are choosing to live in a beautiful dream. And dreams, no matter how real, have their boundaries.\n\nI will not force you.\n\nWake up, or sleep?\n\nThe choice is always in your hands.");

        // Advancements
        add("advancement.herobrine_companion.root.title", "Herobrine Companion");
        add("advancement.herobrine_companion.root.desc", "Begin your journey with the Hero.");
        add("advancement.herobrine_companion.eternal_key.title", "Key to the Void");
        add("advancement.herobrine_companion.eternal_key.desc", "Obtain the Eternal Key.");
        add("advancement.herobrine_companion.enter_end_ring.title", "The Ring of Truth");
        add("advancement.herobrine_companion.enter_end_ring.desc", "Step into the intersection of reality and illusion.");
        add("advancement.herobrine_companion.unstable_gunpowder.title", "Unstable Power");
        add("advancement.herobrine_companion.unstable_gunpowder.desc", "Obtain Unstable Gunpowder.");
        add("advancement.herobrine_companion.lore_handbook.title", "Forbidden Knowledge");
        add("advancement.herobrine_companion.lore_handbook.desc", "Obtain the Lore Handbook.");
        add("advancement.herobrine_companion.recall_stone.title", "Return from Death");
        add("advancement.herobrine_companion.recall_stone.desc", "Obtain the Recall Stone.");
        add("advancement.herobrine_companion.soul_bound_pact.title", "Soul Bound");
        add("advancement.herobrine_companion.soul_bound_pact.desc", "Obtain the Soul Bound Pact.");
        add("advancement.herobrine_companion.transcendence_permit.title", "Transcendence Permit");
        add("advancement.herobrine_companion.transcendence_permit.desc", "Obtain the Transcendence Permit.");
        add("advancement.herobrine_companion.poem_of_the_end.title", "Poem of the End");
        add("advancement.herobrine_companion.poem_of_the_end.desc", "Obtain the Poem of the End.");
    }
}