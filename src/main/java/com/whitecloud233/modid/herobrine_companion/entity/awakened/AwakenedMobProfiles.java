package com.whitecloud233.modid.herobrine_companion.entity.awakened;

import com.whitecloud233.modid.herobrine_companion.entity.family.HerobrineFamilyMembers;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import java.util.List;
import java.util.Map;

public final class AwakenedMobProfiles {
    private static final AwakenedMobProfile ZOMBIE_FAMILY = profile(
            "zombie",
            0.08F,
            List.of("Gravewalker", "Mirehand", "Dusk Rot", "Still-Fang"),
            Items.ROTTEN_FLESH,
            Items.IRON_NUGGET
    );
    private static final AwakenedMobProfile SKELETON_FAMILY = profile(
            "skeleton",
            0.08F,
            List.of("Ashstring", "Pale Fletch", "Rattlemark", "Dry Aim"),
            Items.ARROW,
            Items.BONE
    );
    private static final AwakenedMobProfile CREEPER_PROFILE = profile(
            "creeper",
            0.06F,
            List.of("Hushspark", "Fusemoss", "Quiet Bloom", "Softstatic"),
            Items.GUNPOWDER,
            Items.TNT
    );
    private static final AwakenedMobProfile SPIDER_FAMILY = profile(
            "spider",
            0.07F,
            List.of("Beamweaver", "Silkstep", "Rafter-Eye", "Corner-Hush"),
            Items.STRING,
            Items.SPIDER_EYE
    );
    private static final AwakenedMobProfile ENDER_FAMILY = profile(
            "enderman",
            0.06F,
            List.of("Seamwalker", "Null Gaze", "Far-Hinge", "Hollow Span"),
            Items.CHORUS_FRUIT,
            3,
            Items.ENDER_PEARL,
            1
    );
    private static final AwakenedMobProfile WITCH_PROFILE = profile(
            "witch",
            0.05F,
            List.of("Mireglass", "Bottle-Crow", "Fen Mutter", "Hex Kettle"),
            Items.GLASS_BOTTLE,
            Items.REDSTONE
    );
    private static final AwakenedMobProfile RAIDER_FAMILY = profile(
            "raider",
            0.05F,
            List.of("Tolltaker", "Banner Debt", "Ash Ledger", "Crossmark"),
            Items.ARROW,
            Items.EMERALD
    );
    private static final AwakenedMobProfile SLIME_FAMILY = profile(
            "slime",
            0.06F,
            List.of("Softmass", "Seep", "Crackspawn", "Cinder Jelly"),
            Items.SLIME_BALL,
            Items.MAGMA_CREAM
    );
    private static final AwakenedMobProfile NETHERFIRE_FAMILY = profile(
            "blaze",
            0.05F,
            List.of("Cinder Choir", "Ember Maw", "Glass Wail", "Soot Psalm"),
            Items.FIRE_CHARGE,
            Items.GLOWSTONE_DUST
    );
    private static final AwakenedMobProfile GUARDIAN_FAMILY = profile(
            "guardian",
            0.05F,
            List.of("Tidemind", "Prism Wake", "Deep Circuit", "Anchor Eye"),
            Items.PRISMARINE_SHARD,
            Items.PRISMARINE_CRYSTALS
    );
    private static final AwakenedMobProfile PHANTOM_PROFILE = profile(
            "phantom",
            0.05F,
            List.of("Night Draft", "Sky Scar", "Wing Hush", "Moon Tatter"),
            Items.FEATHER,
            Items.PHANTOM_MEMBRANE
    );
    private static final AwakenedMobProfile PIGLIN_FAMILY = profile(
            "piglin",
            0.06F,
            List.of("Gilt Snout", "Coin-Bite", "Ash Tusks", "Vault-Runner"),
            Items.GOLD_INGOT,
            Items.CRYING_OBSIDIAN
    );
    private static final AwakenedMobProfile BEAST_FAMILY = profile(
            "beast",
            0.05F,
            List.of("Tusk Drum", "Red Throat", "Bramblehide", "Snarehoof"),
            Items.CRIMSON_FUNGUS,
            Items.LEATHER
    );
    private static final AwakenedMobProfile WARDEN_PROFILE = profile(
            "ancient",
            0.03F,
            List.of("Deep Bell", "Sculk Oath", "Blind Resonance", "Hollow Toll"),
            Items.SCULK,
            Items.ECHO_SHARD
    );
    private static final AwakenedMobProfile WITHER_PROFILE = profile(
            "simmons",
            0.02F,
            List.of("simmons"),
            Items.WITHER_SKELETON_SKULL,
            Items.NETHER_STAR
    );
    private static final AwakenedMobProfile DRAGON_PROFILE = profile(
            "jean",
            0.015F,
            List.of("jean"),
            Items.DRAGON_BREATH,
            Items.END_CRYSTAL
    );

    private static final Map<EntityType<?>, AwakenedMobProfile> PROFILES = Map.ofEntries(
            entry(EntityType.ZOMBIE, ZOMBIE_FAMILY),
            entry(EntityType.HUSK, ZOMBIE_FAMILY),
            entry(EntityType.DROWNED, ZOMBIE_FAMILY),
            entry(EntityType.ZOMBIE_VILLAGER, ZOMBIE_FAMILY),
            entry(EntityType.ZOMBIFIED_PIGLIN, ZOMBIE_FAMILY),
            entry(EntityType.GIANT, ZOMBIE_FAMILY),
            entry(EntityType.SKELETON, SKELETON_FAMILY),
            entry(EntityType.STRAY, SKELETON_FAMILY),
            entry(EntityType.WITHER_SKELETON, SKELETON_FAMILY),
            entry(EntityType.CREEPER, CREEPER_PROFILE),
            entry(EntityType.SPIDER, SPIDER_FAMILY),
            entry(EntityType.CAVE_SPIDER, SPIDER_FAMILY),
            entry(EntityType.ENDERMAN, ENDER_FAMILY),
            entry(EntityType.ENDERMITE, ENDER_FAMILY),
            entry(EntityType.SHULKER, ENDER_FAMILY),
            entry(EntityType.WITCH, WITCH_PROFILE),
            entry(EntityType.PILLAGER, RAIDER_FAMILY),
            entry(EntityType.VINDICATOR, RAIDER_FAMILY),
            entry(EntityType.EVOKER, RAIDER_FAMILY),
            entry(EntityType.VEX, RAIDER_FAMILY),
            entry(EntityType.RAVAGER, RAIDER_FAMILY),
            entry(EntityType.ILLUSIONER, RAIDER_FAMILY),
            entry(EntityType.SLIME, SLIME_FAMILY),
            entry(EntityType.MAGMA_CUBE, SLIME_FAMILY),
            entry(EntityType.SILVERFISH, SLIME_FAMILY),
            entry(EntityType.BLAZE, NETHERFIRE_FAMILY),
            entry(EntityType.GHAST, NETHERFIRE_FAMILY),
            entry(EntityType.GUARDIAN, GUARDIAN_FAMILY),
            entry(EntityType.ELDER_GUARDIAN, GUARDIAN_FAMILY),
            entry(EntityType.PHANTOM, PHANTOM_PROFILE),
            entry(EntityType.PIGLIN, PIGLIN_FAMILY),
            entry(EntityType.PIGLIN_BRUTE, PIGLIN_FAMILY),
            entry(EntityType.HOGLIN, BEAST_FAMILY),
            entry(EntityType.ZOGLIN, BEAST_FAMILY),
            entry(EntityType.WARDEN, WARDEN_PROFILE),
            entry(EntityType.WITHER, WITHER_PROFILE),
            entry(EntityType.ENDER_DRAGON, DRAGON_PROFILE)
    );

    private AwakenedMobProfiles() {
    }

    public static AwakenedMobProfile get(Mob mob) {
        if (mob == null) {
            return null;
        }

        if (mob.getType() == EntityType.WITHER || mob.getType() == EntityType.ENDER_DRAGON) {
            return HerobrineFamilyMembers.isFamilyMember(mob) ? PROFILES.get(mob.getType()) : null;
        }

        return PROFILES.get(mob.getType());
    }

    public static boolean supports(Entity entity) {
        return entity instanceof Mob mob && get(mob) != null;
    }

    public static boolean sharesFamily(Mob first, Mob second) {
        if (first == null || second == null) {
            return false;
        }
        AwakenedMobProfile firstProfile = get(first);
        AwakenedMobProfile secondProfile = get(second);
        return firstProfile != null && firstProfile == secondProfile;
    }

    public static String getForcedAwakenedName(Mob mob) {
        return HerobrineFamilyMembers.getForcedName(mob);
    }

    public static String getHerobrineOriginLore(Mob mob) {
        return HerobrineFamilyMembers.getOriginLore(mob);
    }

    private static Map.Entry<EntityType<?>, AwakenedMobProfile> entry(EntityType<?> type, AwakenedMobProfile profile) {
        return Map.entry(type, profile);
    }

    private static AwakenedMobProfile profile(String root, float awakeningChance, List<String> names, Item preferredItem, Item rewardItem) {
        return profile(root, awakeningChance, names, preferredItem, 1, rewardItem, 1);
    }

    private static AwakenedMobProfile profile(String root, float awakeningChance, List<String> names,
                                              Item preferredItem, int preferredItemCount,
                                              Item rewardItem, int rewardItemCount) {
        return new AwakenedMobProfile(
                root,
                awakeningChance,
                List.copyOf(names),
                keys(root, "ambient"),
                keys(root, "first_meet"),
                keys(root, "repeat_meet"),
                keys(root, "request"),
                keys(root, "player"),
                keys(root, "gift"),
                keys(root, "reminder"),
                keys(root, "hostile"),
                keys(root, "hero"),
                peerKeys(root, "same"),
                peerKeys(root, "casual"),
                peerKeys(root, "collab"),
                peerKeys(root, "gossip"),
                peerKeys(root, "conflict"),
                peerKeys(root, "scuffle"),
                peerKeys(root, "authority"),
                peerKeys(root, "reply"),
                preferredItem,
                preferredItemCount,
                rewardItem,
                rewardItemCount
        );
    }

    private static List<String> keys(String root, String scene) {
        String base = "message.herobrine_companion.awakened_mob." + root + "." + scene;
        return List.of(base + ".0", base + ".1", base + ".2", base + ".3");
    }

    private static List<String> peerKeys(String root, String scene) {
        String base = "message.herobrine_companion.awakened_mob." + root + ".peer." + scene;
        return java.util.stream.IntStream.range(0, 4)
                .mapToObj(index -> base + "." + index)
                .toList();
    }
}
