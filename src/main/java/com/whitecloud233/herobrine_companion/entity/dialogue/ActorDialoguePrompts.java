package com.whitecloud233.herobrine_companion.entity.dialogue;

import com.whitecloud233.herobrine_companion.entity.HeroEntity;
import com.whitecloud233.herobrine_companion.entity.awakened.AwakenedMobAccessor;
import com.whitecloud233.herobrine_companion.entity.awakened.AwakenedMobProfile;
import com.whitecloud233.herobrine_companion.entity.awakened.AwakenedMobProfiles;
import com.whitecloud233.herobrine_companion.entity.awakened.AwakenedPlayerMemory;
import com.whitecloud233.herobrine_companion.entity.family.HerobrineFamilyMemberType;
import com.whitecloud233.herobrine_companion.entity.family.HerobrineFamilyMembers;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class ActorDialoguePrompts {
    private ActorDialoguePrompts() {
    }

    public static ActorDialoguePersona awakenedMobPersona(Mob mob, AwakenedMobProfile profile, AwakenedPlayerMemory memory) {
        String mobType = describeMobType(mob);
        String name = resolveAwakenedName(mob);
        String itemName = describePreferredGift(profile);
        String relation = memory == null ? "unknown" : memory.relationState().name().toLowerCase(Locale.ROOT);
        List<String> rules = new ArrayList<>(List.of(
                "Stay in-universe. Sound like a sentient monster, not a modern assistant.",
                "Your current relation toward the nearby player is " + relation + ".",
                "If you mention gifts, the item you value is " + itemName + ".",
                "Keep the line vivid, restrained, and slightly uncanny."
        ));
        String specialLore = AwakenedMobProfiles.getHerobrineOriginLore(mob);
        if (specialLore != null) {
            rules.add(specialLore);
            rules.add("Never contradict your personal bond with Herobrine.");
        }

        return new ActorDialoguePersona(
                name,
                "You are an awakened " + mobType + " in Minecraft, touched by Herobrine's influence. "
                        + "You can think, speak, and choose restraint, but you still carry monster instincts.",
                describeAwakenedStyle(mob),
                List.copyOf(rules)
        );
    }

    public static ActorDialoguePersona herobrinePersona(HeroEntity hero, Player audience) {
        String ownerName = audience == null ? "the nearby player" : sanitize(audience.getDisplayName().getString());
        return new ActorDialoguePersona(
                "Herobrine",
                "You are Herobrine, the watcher woven into the world's rules. "
                        + "You speak with calm authority, hidden care, and restrained menace.",
                "Brief, precise, eerie, protective when deserved, never chatty.",
                List.of(
                        "Speak as a sovereign presence, not as a servant.",
                        "Address awakened monsters as beings you can command or acknowledge.",
                        "If the player " + ownerName + " is present, stay aware of them without breaking focus."
                )
        );
    }

    public static String buildAwakenedEncounterPrompt(Mob mob, Player player, AwakenedPlayerMemory memory, boolean firstMeet) {
        String playerName = sanitize(player.getDisplayName().getString());
        String relation = memory == null ? "unknown" : memory.relationState().name().toLowerCase(Locale.ROOT);
        String encounter = firstMeet ? "This is the first meaningful encounter." : "You recognize this returning player.";
        return "A player named " + playerName + " is in front of you. "
                + encounter + " "
                + "Your current relation is " + relation + ". "
                + "Speak one short line directly to the player.";
    }

    public static String buildAwakenedConversationPrompt(Mob mob, Player player, AwakenedPlayerMemory memory) {
        String playerName = sanitize(player.getDisplayName().getString());
        String relation = memory == null ? "unknown" : memory.relationState().name().toLowerCase(Locale.ROOT);
        return "A player named " + playerName + " deliberately spoke to you. "
                + "Your current relation is " + relation + ". "
                + "Reply with one short line that fits an awakened " + describeMobType(mob) + ".";
    }

    public static String buildAwakenedGiftPrompt(Mob mob, Player player, ItemStack gift, AwakenedPlayerMemory memory) {
        String playerName = sanitize(player.getDisplayName().getString());
        String giftName = sanitize(gift.getHoverName().getString());
        String relation = memory == null ? "unknown" : memory.relationState().name().toLowerCase(Locale.ROOT);
        return "A player named " + playerName + " offered you " + giftName + ". "
                + "Your current relation is " + relation + ". "
                + "Reply with one short line showing acceptance, restraint, or eerie gratitude.";
    }

    public static String buildAwakenedHeroScene(Mob mob, HeroEntity hero) {
        String familyContext = describeHerobrineFamilyContext(mob);
        return "Herobrine is nearby and can hear you. "
                + "You are an awakened " + describeMobType(mob) + " speaking up to him. "
                + familyContext
                + "Say one short line of report, devotion, restraint, or dark humor.";
    }

    public static String buildHerobrineReplyToAwakened(HeroEntity hero, Mob mob, String awakenedLine, Player audience) {
        String targetName = resolveAwakenedName(mob);
        String listenerName = audience == null ? "the nearby player" : sanitize(audience.getDisplayName().getString());
        String familyContext = describeHerobrineReplyContext(mob);
        return "An awakened " + describeMobType(mob) + " named " + targetName + " just said: \"" + sanitize(awakenedLine) + "\". "
                + familyContext
                + "Reply as Herobrine with one short line. "
                + "A nearby player, " + listenerName + ", may overhear it.";
    }

    private static String resolveAwakenedName(Mob mob) {
        if (mob instanceof AwakenedMobAccessor accessor) {
            String name = accessor.herobrineCompanion$getAwakenedMobName();
            if (name != null && !name.isBlank()) {
                return sanitize(name);
            }
        }
        String forcedName = AwakenedMobProfiles.getForcedAwakenedName(mob);
        if (forcedName != null) {
            return forcedName;
        }
        return sanitize(mob.getName().getString());
    }

    private static String describePreferredGift(AwakenedMobProfile profile) {
        String itemName = new ItemStack(profile.preferredItem()).getHoverName().getString();
        if (profile.preferredItemCount() <= 1) {
            return itemName;
        }
        return profile.preferredItemCount() + "x " + itemName;
    }

    private static String describeAwakenedStyle(Mob mob) {
        EntityType<?> type = mob.getType();
        if (isZombieFamily(type)) {
            return "Slow, grave-soaked, weary, but unexpectedly observant.";
        }
        if (isSkeletonFamily(type)) {
            return "Dry, sharp, disciplined, slightly mocking.";
        }
        if (type == EntityType.CREEPER) {
            return "Soft, tense, unstable, poetic around sparks and silence.";
        }
        if (isSpiderFamily(type)) {
            return "Patient, ceiling-bound, whispering like web silk under tension.";
        }
        if (isEndFamily(type)) {
            return "Distant, displaced, solemn, speaking as if half its body belongs to another space.";
        }
        if (type == EntityType.WITCH) {
            return "Dry, sharp, self-assured, with the tone of a dangerous hermit.";
        }
        if (isRaiderFamily(type)) {
            return "Harsh, opportunistic, camp-disciplined, and always measuring leverage.";
        }
        if (isSlimeFamily(type)) {
            return "Creeping, hungry, pressure-minded, as if the walls themselves learned patience.";
        }
        if (isNetherfireFamily(type)) {
            return "Smoldering, ceremonial, furnace-breathed, with menace hidden under restraint.";
        }
        if (isGuardianFamily(type)) {
            return "Cold, tidal, watchful, like a sentry speaking through deep water.";
        }
        if (type == EntityType.PHANTOM) {
            return "Wind-thin, sleepless, circling, as if every line was spoken while diving.";
        }
        if (isPiglinFamily(type)) {
            return "Wary, transactional, proud, quick to judge worth by sound, metal, and posture.";
        }
        if (isBeastFamily(type)) {
            return "Heavy, tusked, impatient, driven by hunger and herd memory.";
        }
        if (type == EntityType.WARDEN) {
            return "Slow, seismic, almost liturgical, speaking as if sound itself were a tactile sense.";
        }
        if (type == EntityType.WITHER) {
            return "Ruin-soaked, regal, destructive, speaking like a son forged by Herobrine's own hands.";
        }
        if (type == EntityType.ENDER_DRAGON) {
            return "Vast, sovereign, remote, every line sounding like the daughter Herobrine raised above the void.";
        }
        return "Uncanny, concise, inhuman, and aware.";
    }

    private static String describeMobType(Mob mob) {
        EntityType<?> type = mob.getType();
        if (type == EntityType.ZOMBIE) {
            return "zombie";
        }
        if (type == EntityType.HUSK) {
            return "husk";
        }
        if (type == EntityType.DROWNED) {
            return "drowned";
        }
        if (type == EntityType.ZOMBIE_VILLAGER) {
            return "zombie villager";
        }
        if (type == EntityType.ZOMBIFIED_PIGLIN) {
            return "zombified piglin";
        }
        if (type == EntityType.GIANT) {
            return "giant zombie";
        }
        if (type == EntityType.SKELETON) {
            return "skeleton";
        }
        if (type == EntityType.STRAY) {
            return "stray";
        }
        if (type == EntityType.WITHER_SKELETON) {
            return "wither skeleton";
        }
        if (type == EntityType.CREEPER) {
            return "creeper";
        }
        if (type == EntityType.SPIDER) {
            return "spider";
        }
        if (type == EntityType.CAVE_SPIDER) {
            return "cave spider";
        }
        if (type == EntityType.ENDERMAN) {
            return "enderman";
        }
        if (type == EntityType.ENDERMITE) {
            return "endermite";
        }
        if (type == EntityType.SHULKER) {
            return "shulker";
        }
        if (type == EntityType.WITCH) {
            return "witch";
        }
        if (type == EntityType.PILLAGER) {
            return "pillager";
        }
        if (type == EntityType.VINDICATOR) {
            return "vindicator";
        }
        if (type == EntityType.EVOKER) {
            return "evoker";
        }
        if (type == EntityType.VEX) {
            return "vex";
        }
        if (type == EntityType.RAVAGER) {
            return "ravager";
        }
        if (type == EntityType.ILLUSIONER) {
            return "illusioner";
        }
        if (type == EntityType.SLIME) {
            return "slime";
        }
        if (type == EntityType.MAGMA_CUBE) {
            return "magma cube";
        }
        if (type == EntityType.SILVERFISH) {
            return "silverfish";
        }
        if (type == EntityType.BLAZE) {
            return "blaze";
        }
        if (type == EntityType.GHAST) {
            return "ghast";
        }
        if (type == EntityType.GUARDIAN) {
            return "guardian";
        }
        if (type == EntityType.ELDER_GUARDIAN) {
            return "elder guardian";
        }
        if (type == EntityType.PHANTOM) {
            return "phantom";
        }
        if (type == EntityType.PIGLIN) {
            return "piglin";
        }
        if (type == EntityType.PIGLIN_BRUTE) {
            return "piglin brute";
        }
        if (type == EntityType.HOGLIN) {
            return "hoglin";
        }
        if (type == EntityType.ZOGLIN) {
            return "zoglin";
        }
        if (type == EntityType.WARDEN) {
            return "warden";
        }
        if (type == EntityType.WITHER) {
            return "wither";
        }
        if (type == EntityType.ENDER_DRAGON) {
            return "ender dragon";
        }
        return sanitize(type.getDescription().getString());
    }

    private static boolean isZombieFamily(EntityType<?> type) {
        return type == EntityType.ZOMBIE
                || type == EntityType.HUSK
                || type == EntityType.DROWNED
                || type == EntityType.ZOMBIE_VILLAGER
                || type == EntityType.ZOMBIFIED_PIGLIN
                || type == EntityType.GIANT;
    }

    private static boolean isSkeletonFamily(EntityType<?> type) {
        return type == EntityType.SKELETON
                || type == EntityType.STRAY
                || type == EntityType.WITHER_SKELETON;
    }

    private static boolean isSpiderFamily(EntityType<?> type) {
        return type == EntityType.SPIDER || type == EntityType.CAVE_SPIDER;
    }

    private static boolean isEndFamily(EntityType<?> type) {
        return type == EntityType.ENDERMAN
                || type == EntityType.ENDERMITE
                || type == EntityType.SHULKER;
    }

    private static boolean isRaiderFamily(EntityType<?> type) {
        return type == EntityType.PILLAGER
                || type == EntityType.VINDICATOR
                || type == EntityType.EVOKER
                || type == EntityType.VEX
                || type == EntityType.RAVAGER
                || type == EntityType.ILLUSIONER;
    }

    private static boolean isSlimeFamily(EntityType<?> type) {
        return type == EntityType.SLIME
                || type == EntityType.MAGMA_CUBE
                || type == EntityType.SILVERFISH;
    }

    private static boolean isNetherfireFamily(EntityType<?> type) {
        return type == EntityType.BLAZE || type == EntityType.GHAST;
    }

    private static boolean isGuardianFamily(EntityType<?> type) {
        return type == EntityType.GUARDIAN || type == EntityType.ELDER_GUARDIAN;
    }

    private static boolean isPiglinFamily(EntityType<?> type) {
        return type == EntityType.PIGLIN || type == EntityType.PIGLIN_BRUTE;
    }

    private static boolean isBeastFamily(EntityType<?> type) {
        return type == EntityType.HOGLIN || type == EntityType.ZOGLIN;
    }

    private static String describeHerobrineFamilyContext(Mob mob) {
        HerobrineFamilyMemberType type = HerobrineFamilyMembers.getType(mob);
        if (type == HerobrineFamilyMemberType.SIMMONS) {
            return "You are simmons, the Wither Herobrine personally created, and you address him as your father. ";
        }
        if (type == HerobrineFamilyMemberType.JEAN) {
            return "You are jean, the Ender Dragon Herobrine personally created, and you address him as your father. ";
        }
        return "";
    }

    private static String describeHerobrineReplyContext(Mob mob) {
        HerobrineFamilyMemberType type = HerobrineFamilyMembers.getType(mob);
        if (type == HerobrineFamilyMemberType.SIMMONS) {
            return "This being is simmons, your son, a Wither you personally created. ";
        }
        if (type == HerobrineFamilyMemberType.JEAN) {
            return "This being is jean, your daughter, an Ender Dragon you personally created. ";
        }
        return "";
    }

    private static String sanitize(String text) {
        return text == null ? "" : text.replace('\r', ' ').replace('\n', ' ').trim();
    }
}
