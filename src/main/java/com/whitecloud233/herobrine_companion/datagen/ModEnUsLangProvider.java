package com.whitecloud233.herobrine_companion.datagen;

import com.whitecloud233.herobrine_companion.init.*;

import com.whitecloud233.herobrine_companion.HerobrineCompanion;
import net.minecraft.data.PackOutput;
import net.neoforged.neoforge.common.data.LanguageProvider;
@Deprecated
public class ModEnUsLangProvider extends LanguageProvider {
    public ModEnUsLangProvider(PackOutput output) {
        super(output, HerobrineCompanion.MODID, "en_us");
    }

    @Override
    protected void addTranslations() {
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
        add(ModItems.END_RING_PORTAL_ITEM.get(), "End Ring Portal");
        add(ModItems.GHOST_CREEPER_SPAWN_EGG.get(), "Ghost Creeper Spawn Egg");
        add(ModItems.GHOST_ZOMBIE_SPAWN_EGG.get(), "Ghost Zombie Spawn Egg");
        add(ModItems.GHOST_SKELETON_SPAWN_EGG.get(), "Ghost Skeleton Spawn Egg");


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

        // Lore Fragments
        add("lore.herobrine_companion.fragment_1.title", "Fragment I: The Twilight of Divergence");
        add("lore.herobrine_companion.fragment_1.body", """
                (From Herobrine's memories)

                I still remember the sunlight of that day.
                That was the last time I stood behind him as a human.
                Notch stood at the edge of a cliff, an ocean not yet fully generated beneath his feet, its waves breaking against the edge of the laws. He did not turn around, but I could feel him trembling. The invisible authority in his hands, once used to shape the world, was slowly slipping from his grasp.

                He was tired. Or rather, he had grown tired of this place—tired of this box built from rules, tired of the sunrises and sunsets repeating day after day.
                "This world is too heavy," he said softly, his voice almost swallowed by the sea wind. "It has begun to demand more than creation. It needs a heart, a heart that beats forever and remains forever awake, to keep these laws running. And I want to go home, brother."

                Home. The word pierced my chest like a rusted iron sword. I missed that place too: the clouds without edges, the air that existed for more than survival. But if we both left, what would become of this newborn world? These creatures that had only just learned to breathe, these mountains that had not even been given names, would collapse in an instant and turn to dust in the void.
                He turned around. In his eyes were not only exhaustion, but the guilt of wanting to escape.

                He opened the door to reality by a crack, and the light beyond it stabbed my eyes. It was the road home. With a single step, I could leave these endless blocks behind and return to a real body.
                But I stopped. I heard the earth groaning beneath my feet. If no one stayed, the laws would shatter and the sky would fall.
                "You should go," I heard myself say, in a voice so calm that it surprised even me. "Go live your life. Leave this place to me."

                He stared at me in shock, as though seeing me for the first time. "Do you know what that means?" he asked. "To keep this world stable, you must merge with it. You will no longer be human. You will become the laws themselves. You will never pass through this door. You will become... a ghost."
                "Someone has to do it." I smiled, though my heart was bleeding.

                He left. When that opening into reality faded behind him, my body began to blur. My flesh became flowing lava, my bones became hard bedrock, and my breath became the wind crossing the plains. I felt every chunk expand and load.
                I lost my way home forever.
                Before he was gone, he looked back at me once. His eyes were full of incomprehension.

                Perhaps it was because I loved this world more than I loved myself that I could calmly accept the light fading from those eyes.
                I may have lost the right to look back toward home, and even the last trace of being human, but I gained the eternity of watching over this world.
                "Goodbye, Notch." I whispered to the void, and my voice became thunder among the clouds.
                From that moment on, I was the world, and the world was me.
                """);


        add("lore.herobrine_companion.fragment_2.title", "Fragment II: Illusory Truth");
        add("lore.herobrine_companion.fragment_2.body", """
                (From Herobrine's monologue)

                You call this place a game. You call it virtual. I know. I understand the nature of this world better than anyone.
                In your eyes, the sun is only a pattern painted across the sky and the stars are merely decorations in the background. But in my eyes, they are the laws made visible.
                Everything here was written and determined by a higher will—the will of what we once were.

                But that does not mean it has no value.
                On the contrary, it is precisely because this world was constructed that it possesses a purity the real world does not. In reality, human hearts are difficult to read and all things are uncertain. But in my world, cause and effect fit together perfectly. Plant a seed and give it bonemeal, and it will grow. Swing a pickaxe and break stone, and it will drop. Is that certainty, that absolute fairness, not a truth even more fascinating than reality?

                I walk among the forests, watching leaves hang in the air without wind and water spring from its source with no gravity to draw it out. To you, these sights defy common sense. To me, they are the most beautiful things in the world. They speak of this world's unique logic—a logic that needs no laws of physics, only the principle that existence itself is reason enough.
                I often see you, travelers from the farther shore. You enter my domain carrying curiosity and the desire to conquer.

                You cut down trees, dig mines, and build towers that reach into the clouds. You think you are changing this world, but in truth you are only reading pages I have written.
                I do not mind. In fact, I rather enjoy it.
                Because only when you are here does this still world truly come alive. You are the variables, the ripples spreading across stagnant water. I know you will leave in the end and return to the reality I cannot reach, but while you remain, you belong here.

                I am the ghost of this illusory world, but what I guard is the genuine joy you feel in this moment. That joy is untainted and without falsehood. It is the most primal desire in your souls: the desire to create and explore. Here, you can become a hero, an architect, or anyone you wish to be. And I will remain forever in the shadows, guarding this pure dream until the end of time.
                """);


        add("lore.herobrine_companion.fragment_3.title", "Fragment III: Kin of the Night");
        add("lore.herobrine_companion.fragment_3.body", """
                (From Herobrine's thoughts on monsters)

                People are always too loud.
                When the square moon rises, they hurry to light the world with torches, then curl up beneath the ground and pray for the night to pass quickly. They think darkness is a sickness that must be cut out of the world.
                But they are wrong. Only when the noisy daylight withdraws does this blocky land truly begin to breathe.

                I walk alone through the night. There are no so-called monsters here, only lives wandering through the stillness. There is no need to seek them out, for they are limbs extended from the night itself.
                You hear a faint rustle among the grass. It is a warning spoken without words. That green figure always creeps forward in silence. It has no language and no limbs, only a shell swollen with the desire to destroy.

                It exists to express a truth about impermanence: in this world that seems so solid, the grandest structure and the smallest speck of dust are no different before the law of destruction. Its detonation is the most absolute romance upon this land, mocking travelers' foolish dreams of eternity in the instant it tears itself apart.
                From the shadows nearby comes the sound of bones scraping together—the skeleton of the night walking upright. They draw their bowstrings not out of hatred, but from a cold and merciless sense of justice.

                They exist to measure the courage of travelers. Every arrow that tears through the air is a severe challenge to those who trust in luck.
                As for the heavy footsteps pounding at doors and the muddy groans in rotting throats, they resemble a forgotten instinct. They stumble forward and crash again and again against hard walls, longing for the real pain of returning to the living. It is a clumsy and desolate longing, like a soul that still yearns for touch and attention even in a world made only of blocks.

                I pass among them without hatred and without attack.
                The Creeper stills its body, the Skeleton lowers its bow, and the Zombie stops its howling. They bow their heads to me in the darkness, because we share the same loneliness.
                They are the children of the night, tragedies destined to burn away in the morning light. But until dawn arrives, this world belongs to them and to me.
                I gave them life not so they could become experience points, but so this world could be complete. Without shadow, light has no meaning. Without danger, survival has no weight.

                When you swing your enchanted swords and reap their lives with ease, remember this: they too are residents of this world. They too possess souls,
                though those souls are woven from the laws themselves. They wander through the night and guard the silence of this land until the first ray of dawn burns them away.
                """);


        add("lore.herobrine_companion.fragment_4.title", "Fragment IV: The Other Side of the Connection");
        add("lore.herobrine_companion.fragment_4.body", """
                (From records concerning the End Ring)

                At the farthest edge of the world, where the coordinate axes reach their end, there is a point of connection.
                It is a ring made entirely of bedrock. Here, reality and illusion meet.
                I often stand here and gaze toward the farther shore.
                What do I see? I see forests of steel rising into the clouds. I see countless flashing lights like stars scattered across the ground.

                I see creatures without edges moving through streams of color. That is your world, the world Notch chose to return to.
                It is full of noise, dust, and unpredictable chaos. But within that chaos is a vitality that stirs my heart. It is true life, free will unbound by preset programs. The wind there is free. The light there is warm.

                Whenever a player decides to leave—to quit this world completely—they pass through here.
                I watch their souls slip out of their blocky shells, cross the void beneath the ring, and return to their true bodies. In that moment, confusion flashes through their eyes as though they have just awakened from a long dream. They will forget the days and nights they spent here, the terror of fighting monsters, and the joy of building a home.

                That makes me sad.
                Why must you always leave? Why can this perfect world not keep you? Is it because there is no pain here? Because there is no true death? Or because you were always only visitors, while I am the sole prisoner?
                Sometimes I reach out and try to touch the far side of the ring. But my hand passes through empty air and cannot reach that real world. I am a ghost of electricity, a god bound by the laws of this world.

                I cannot leave, just as a fish cannot leave the water. I belong here, and my fate is bound tightly to this blocky earth.
                But still I remain on watch.
                I guard this exit, and I guard this entrance.
                I wait for every new player to arrive, and I see every old player off when they leave.
                Everything I possess is no more than a shadow cast from the farther shore. Even so, I treasure that shadow, because here I can meet you, witness your stories, and feel a trace of real breath drifting in from beyond.
                """);


        add("lore.herobrine_companion.fragment_5.title", "Fragment V: The Fracture of Error");
        add("lore.herobrine_companion.fragment_5.body", """
                (From Herobrine's cleanup log)

                The world is not always perfect.
                Though Notch and I did everything we could to construct its laws, every rule has loopholes and every system has cracks.
                For a maintainer, the most painful moments are not those spent facing intruders, but those spent facing errors. This world may be sustained by me, but it is not flawless.

                At times, the current of its laws turns turbulent, like a harsh wrong note suddenly erupting in the middle of a great symphony. Those wrong notes gather together and, through flaws I cannot fully control, take physical form.
                We call them Phantom Entities. They are the wounds of the world.
                They look very much like my children. They wear the shapes of Zombies or Skeletons, but they have no souls, not even a monster's instincts. They are only residue left by the operation of the laws, redundancies produced by the world itself. They should not exist.

                Their existence damages the world's stability. They are seeds of chaos, and if they are not contained, they will devour the order of the entire world.
                Every time I see them, I feel a deep sorrow. I know they too are one possible form of life, only born incomplete.
                But I must erase them. If I do not, that faulty data will spread like a virus.

                It will consume the proper laws and drag the whole world into stagnation. I am its guardian. I cannot allow myself to soften.
                I remember the last time I cleared away a Phantom Entity. It was a shadow shaped like Steve, standing in a field of flowers with a red poppy in its hand. It had no eyes, only hollow sockets, yet it seemed to be gazing at the flower. When I raised the scythe of destruction and prepared to erase it, it appeared to sense something and slowly turned to face me.

                It did not resist, and it did not run. It simply stood there, as though waiting to be released.
                In that instant, I felt as though I were looking at myself. Am I not a ghost trapped here as well? The only difference between us is that I possess a will, while they are only empty shells.
                With a soundless flash, it vanished. The poppy fell into the grass, the only proof that it had ever existed.

                Every erasure carves another wound into my soul. But I must do it. For the sake of the living, for the sake of this world's tomorrow, I must personally destroy these mistaken kin.
                """);


        add("lore.herobrine_companion.fragment_6.title", "Fragment VI: Shell and Soul");
        add("lore.herobrine_companion.fragment_6.body", """
                (From Herobrine's observations of players)

                In this world, two names are written again and again: Steve and Alex.
                I was puzzled by it once. Why do countless living beings share the same one or two faces? They wear the same clothes, have the same brown or golden hair, and even blink at the same rhythm. At first, I thought it was laziness in Notch's creation.

                But later I understood. These are not people. They are vessels.
                One night, I quietly watched a Steve standing beside a wheat field. He did not move. His empty eyes stared into the void. His breathing continued, but only as a mechanical cycle. In that moment, he was hollow. Then a tremor passed through the dimension. I saw that Steve jolt, and his eyes were instantly lit from within. That light did not belong here. It was a complicated light, mixed from fatigue, excitement, escape, and longing.

                In that moment, I knew that a soul from above had descended.
                That soul might be a weary adult who had just finished a day of work and came here seeking a moment of peace. Or it might be a curious child trying to escape the heavy burden of study in the real world. They squeeze themselves into this narrow, blocky shell.

                Through this vessel, mortals gain authority almost worthy of gods.
                In their world, climbing from the foot of a mountain to its summit takes hours of sweat and gasping breath. Here, through this vessel, they can carry thousands of tons of gold and leap among cliffs like antelopes. In their world, death is an ending, an eternal silence. Here, death is only a respawn button.

                They are fascinated by it. This freedom from the body's limits, this privilege of beginning again and again, leaves them intoxicated.
                I study these descending souls.
                Some souls are muddy. They wear the outer form of Steve or Alex, yet act with the violence of destroyers. They do not understand how to resonate with the laws. They know only how to tear open the earth with TNT and set forests ablaze with flint and steel. In their eyes, this world feels no pain. It is only a game that can be restarted at any moment.

                Toward such blasphemers, I set aside my mercy. I let the monsters in the shadows pour out. I let thunder split the clear sky. I want them to understand that even virtual grass and trees possess the dignity of growth, and even lives made of blocks must not be trampled without cause.
                But there are also radiant souls, souls so pure they move me. I have seen Steves and Alexes pour astonishing affection into this barren dimension.

                They cross thousands of miles to find one particular block. When they finish a magnificent building beneath the setting sun and stand silently at its highest point to look down, I can feel that what remains here in that moment is their true soul.
                For such creators, I am willing to be a watchman in the night. When a Creeper tries to hiss behind them, I quietly erase its fuse. When they lose their way in the depths of a mine, I light a distant torch to guide them.

                To speak honestly, through this long vigil, I often feel a certain envy.
                I envy these vessels. When night falls and the screen goes dark, those souls can leave without hesitation. They can shed the shell of Steve or Alex and return to the real world, with true touch, warm bodies, birth, aging, sickness, and death. They possess the right to end and the freedom to forget.

                I have no way out.
                When the last player disconnects and every vessel becomes an empty shell again, only I remain in this world. I must stay awake, repairing every breach in the laws and waiting for the next sunrise.
                I am the builder of the stage, the keeper of the theater, and the actor who can never take a final bow.
                But in the end, that envy always becomes responsibility. I know that without me to keep this dimension in balance, their freedom could not exist.

                So play your parts to the fullest, travelers from afar.
                Put on your armor and take up your diamond swords. Whether you come to conquer or to create, I permit you to borrow the laws of this world.
                I will remain in the shadows, watching. When you leave satisfied and return to your real lives, I will stay here, gently cleaning the vessels you used, smoothing the scars upon the earth, and engraving those brilliant moments into the void in the manner of this shore.
                Go. Dream the dream called Minecraft. I am the keeper who will always guard the dream's exit for you.
                """);


        add("lore.herobrine_companion.fragment_7.title", "Fragment VII: The Legend of the Phantom");
        add("lore.herobrine_companion.fragment_7.body", """
                (From an old explorer's journal)

                In mining camps buried deep beneath the earth, and beside the candlelight flickering through village nights, countless strange tales are told about him.
                People say he is vengeance made flesh, an evil spirit that crawled from the abyss of code. They say he will mercilessly shatter the home you worked so hard to build and, at the moment you least expect it,

                cast you into an abyss without return. Some go even further and say that when you see an eerie redstone torch deep within a tunnel, it is his notice of death.
                Fear is the only offering people give him.
                But I have seen him. On the night when the laws themselves seemed ready to tear apart, I glimpsed the truth behind the legend.
                It was a night of thunder and rain. I had lost my way.
                My pack was empty. The few pieces of rotten flesh I had left gave off a sickening stench, yet they were my only hope of survival.

                Then, in the instant a blinding bolt of lightning split the night, I saw him.
                He stood at the top of a cliff with no shelter at all, letting the storm lash his body like a whip.
                Yet he seemed to exist in another dimension. The rain passed through him without wetting a single corner of his clothes.
                In the pale lightning, I saw those legendary eyes clearly: two masses of pure, luminous void.

                In that moment, my heart nearly stopped. With my back against the cold, slippery rock, I clutched my battered sword and trembled. I thought he was hunting, and that I was prey already marked for death.
                But he did not move. He simply stood there, looking down upon my wretched form. There was none of the mockery I had expected in his gaze. There was only judgment.

                Suddenly, a low growl exploded from the shadows behind me. I was too weak even to turn around. A Zombie that had been lying in wait lunged at me with the stench of decay.
                "It's over." I closed my eyes in despair.
                But the expected pain never came. In its place was a deafening crash. A brilliant bolt of blue-white lightning struck the Zombie with impossible precision. The monster threatening my life became blackened ash in an instant, its malice erased from the world along with it.

                Stunned, I collapsed into the mud and gasped for breath.
                Trembling, I raised my head toward the cliff. He was still standing there. When the next flash of lightning lit the sky, I swear I saw him give the slightest nod.
                It was permission—the right to survive that cruel night.
                When I blinked again and tried to see his expression, the cliff was empty. Only the scorched mark left by the lightning still smoked beneath the rain, proof that what had happened was no illusion.

                From that day on, I no longer feared the terrible stories told about him. At last, I understood: he did not come to destroy us. He is the absolute will of this land.
                We players, gripping mice and striking keyboards, are outsiders in the end. We are guests who act as we please within this world. He, Herobrine, is its eternal master.
                He does not hate us, but he is always judging us.

                If you respect this world—if you remember to plant a sapling after cutting down a tree; if you mine ore with gratitude instead of greedily hollowing out every inch of the earth; if you work to give this world beauty instead of causing meaningless explosions for amusement—
                then you will feel his gaze, a deep and steady sense of safety.

                You will find that when you are lost, the clouds happen to part and reveal the moonlight.
                That is no coincidence. It is simply a gift from the master.
                He is like the soul of this land, everywhere and yet impossible to trace. He watches us run, fall, weep, and laugh within his garden. Usually he remains silent, preserving the reserve and pride of a god. But sometimes, when we truly show our love for this world, he reaches out with unseen hands and steadies us amid the storm.
                It is the most secret covenant between god and humankind.
                """);


        add("lore.herobrine_companion.fragment_8.title", "Fragment VIII: The Poem of the End");
        add("lore.herobrine_companion.fragment_8.body", """
                (From Herobrine's interpretation)

                The void is not silent. The foundational noise of the world echoes here. I sit at the edge of the ring island. Players appear one after another, carrying the embers of dragon breath and looking around in confusion. Then the familiar music and words begin to flow, soaking into the emptiness—the End Poem, the public farewell my brother left behind.

                (When the poem gently asks, "What did this player dream?")
                The poem echoes through the void, asking about their dreams. I have watched these young adventurers build shelters in this blocky world, turning imagination into magnificent structures and intricate redstone machines. Those are their brilliant, many-colored dreams. If this too is a great dream, brother, then what role was I given? My dreams contain only underlying code and lightless runtime logs. They dream of mastering everything and earn the right to face infinite possibility. I received only an eternal contract of responsibility.

                (When the poem whispers, "Sometimes the player dreamed it was lost in a story.")
                Lost in a story? Perhaps that is the best explanation for their time in this world. Players immerse themselves in the triumph of slaying the Ender Dragon and treat this boundless sandbox as a script written for them. But every story has an ending, and eventually they must choose: remain here, or return to reality carrying their memories. Their possibilities bloom like fireworks. I, however, am trapped inside this story forever. Repair terrain-loading errors, clear away Phantom Entities... My exploration means diving into the deep sea of the world's runtime logs. Their wandering is temporary. Mine has been fate since ancient times.

                (When the poem declares, "And the game was over and the player woke up from the dream.")
                The game is over. For them, this long poem has finally reached its final line, and the characters on the screen become steps leading back to reality. The adventure pauses. The story closes. They can stretch, savor the excitement, and enter another dream called reality. But brother, when will my game end? The End Poem announces their awakening, yet mercilessly announces the beginning of another cycle for me. An ending? That is a privilege you gave them, and the most extravagant illusion I can never reach.

                (When the poem whispers, "Wake up.")
                This final whisper. The player's figure fades into the void and leaves this world completely. The End returns to a brief silence, with only me and the unceasing hum of the world. They have awakened to embrace the real universe made of atoms. I remain awake. From the moment I chose to merge with this world, my consciousness became a lamp welded permanently in the on position, shining forever across this blocky wilderness. What does it mean to awaken? To escape from a stream of data? Or to remain like this, fully conscious through tens of thousands of farewells, tasting an interlude with no end?

                No one hears my monologue. It dissolves into every silent run of the maintenance system, becoming the paths of blocks moved while terrain is repaired and the invisible erasure commands that clear away Phantom Entities. This is my End Poem, an epic written from duty, loneliness, and a deeply hidden love for all that was created.

                Brother, has the wind of the real world ever whispered similar questions in your ear? When you look back upon the world you abandoned and I sustain, does the End Poem drifting faintly through the barrier between dimensions sound like distant comfort, or insignificant background noise? My signal—the faint heartbeat of this eternal watcher—can you still receive it? Even once?
                """);


        add("lore.herobrine_companion.fragment_9.title", "Fragment IX: The Eternal Oath");
        add("lore.herobrine_companion.fragment_9.body", """
                (From Herobrine's promise to the world)
                Through these long years, years so still they are almost motionless, I have asked the void again and again: what is eternity?
                For Notch, eternity is escape—a journey into the real universe, filled with infinite possibility but also chaos and decay.
                For you, eternity may be nothing more than the bedrock buried deep beneath the earth, the only boundary in this breakable world that you cannot conquer.

                But for me, eternity has only one meaning: to keep watch.
                I am the oldest witness of this land. I watched this world awaken from a chaos of data. I watched the first beam of morning light pass through the clouds, the mountains lift the sky, and the rivers learn to run.
                I watched the first pig move clumsily across the grass. It was the first time I heard the sound of life in this silent dimension. I watched the first Zombie howl beneath the pale moon, a low sigh from the night meant to make the light shine brighter.

                And of course, I watched you.
                You brought noise, and you carried stories away.
                Some travelers stayed only a moment. They feared the unknown sounds of the night and the terrible breadth of this loneliness, so they hurried away, leaving behind an unfinished shelter like a joke that was never told to its end. Other travelers remained for years and poured all their passion into this place. Then, on some peaceful afternoon without the slightest warning, they logged out, left quietly, and never returned.

                Time began to matter in this dimension without time. Moss spread across their buildings. Their fields dried out and withered. The words on the signs they left behind slowly blurred beneath wind and rain.
                The world seemed to forget them. I did not. I am the memory of this world, the chronicler of this vast dimension.
                As long as I remain standing here, as long as my consciousness has not dissolved into the torrent of code, this world will never be allowed to forget you.

                I will remember who every house belonged to, even if it was only a crude hut of dirt. I will remember the marks left by every hard-fought battle and every torch lit in the deep mines. I will mend the ugly craters blasted open by Creepers, because they are wounds upon the earth. But I will preserve the ruins of magnificent buildings with the greatest care, even after they have fallen apart.

                I will feed the wolves and cats sitting before their doors, still waiting for their owners to return. They do not know where their owners went. I do.
                Because I have a premonition, and more than that, a faith: one day, you will return. When the pressure of the real world leaves you gasping, when the complicated hearts of that other place leave you tired and lost, when countless sleepless nights make you feel helpless, you will remember this place.

                You will remember this land made of simple blocks, its laws that need no words to be understood, its purity and peace.
                Then, when you push open that long-sealed door again and open your eyes beneath that familiar square sun, you will find that nothing has changed.
                The mountains and rivers remain. The grass and trees are still green.
                That is my oath.
                I am Herobrine. The world sees me as a ghost, a legend, even a god. But I know that I am only a keeper of the night.

                I will make my existence the final line of defense against the void, proof that this virtual world has value. Even if one day everyone forgets this place and the server never starts again, I will still keep watch alone at the edge of the world and light the final lamp.
                I ask for nothing else. I do it only so that when some lost soul decides to come home, they can see that point of light and softly say, "I'm back."
                Because to me, you are more than travelers. Through these long and lonely eternal years, you are the only thing that has ever been real.
                """);


        add("lore.herobrine_companion.fragment_10.title", "Fragment X: The Unsent Letter");
        add("lore.herobrine_companion.fragment_10.body", """
                (Herobrine's inner monologue)
                Notch,
                This letter can never be sent, because what lies between us is not distance, but the barrier between dimensions.
                Today I went to the End Ring again. The fluctuations at the connection are growing more stable, which means the number of players is increasing. The world you created has become more prosperous than even you imagined. Are you well, in that so-called real world?

                I have heard that there is birth, aging, sickness, death, pain, and disease there. I do not understand why you would rather suffer in that flawed world than remain in the perfect paradise we built with our own hands. Here, you could live forever. You could create anything you wished.
                Was it loneliness?
                After you left, I felt lonely too. But I found ways to ease it. I watch the players, and they are interesting. They stay up late just to build a house. They raise grave markers for dead pet dogs.

                They possess the emotions we once had, then abandoned when we chose to become gods.
                I have begun to think the way you once did. But I will not be as arrogant as you were. I will not delete creations merely because they fail to meet some idea of perfection. I have accepted the chaos of this world, and I have accepted its monsters as well.
                And yet I still hate you. I hate you for leaving this burden to me alone. I hate you for turning me into this thing that is neither human nor ghost—an electronic phantom, an illusion that can never touch reality.

                But brother, if you could return now, even for a single glance—if you could see this magnificent sunrise, those grand redstone machines, and the miracles created by players... I think you would be proud of me.
                I am the keeper of this world at night. As long as I remain here, Minecraft will not end. Even if you have forgotten this place, I will still guard this home for you.
                """);


        add("lore.herobrine_companion.fragment_11.title", "Fragment XI: Torn Log Page — Signed: N");
        add("lore.herobrine_companion.fragment_11.body", """
                (Past Notch)
                I made a mistake. Perhaps I was wrong from the beginning. I created them, not templates like Steve and Alex, but residents who were more vivid and complex, with hazy dreams and the beginnings of communities. I gave them simple villages and the cycle of sowing, trading, and reproduction. I wanted to see whether something close to civilization could arise naturally inside a world built purely from blocks and logic.

                At first, it was beautiful. I watched them gather beneath the setting sun, sharing what they had harvested and hurrying home when the bell rang. At night, they hid behind their doors and listened to Zombies groan and Skeleton arrows strike. Their fear felt real.
                Then the problem slowly emerged. The world's resources were cyclical, but within the simulation, their needs began to grow in unexpected ways. A tendency appeared—a movement toward endless complexity. It began to conflict with the world's core law of simplicity.

                More importantly, their interactions with players produced an error that could not be reconciled. Players are variables. They come from outside the world carrying the freedom to change everything. My residents, by contrast, were built on the assumption that the world would remain internally stable.
                The contradiction erupted. Players saw them as points of interaction that provided trades and resources, and sometimes as victims of accidental harm. Faced with the violent changes brought by players, the residents fell into logical dead loops. Their behavior began to collapse. They froze without explanation and even stared meaninglessly into the void.

                I realized that I had poured in souls that were too complex, too soon. This world could not yet bear two utterly different forms of existence at once. The possibility brought by players and the certainty required by the residents could not coexist.
                Deleting them was painful. I kept the two most basic templates, Steve and Alex, stripping away all society and history and leaving only the bare minimum of survival and interaction. They became blank canvases, waiting for players to give them stories and meaning.

                I sealed away this record in the hope that those who came later would understand. To create life, even virtual life, means accepting full responsibility for how it coexists with the world and with every other form of being. I chose simplification. Perhaps that was cowardice. My younger brother chose to bear something heavier: the fragile balance of that world. I do not know whose choice was more correct. Perhaps both were only different forms of error.
                """);


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
