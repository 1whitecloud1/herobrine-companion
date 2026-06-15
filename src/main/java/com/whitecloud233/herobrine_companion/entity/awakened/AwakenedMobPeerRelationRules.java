package com.whitecloud233.herobrine_companion.entity.awakened;

import net.minecraft.util.RandomSource;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public final class AwakenedMobPeerRelationRules {
    private static final Map<PairRootKey, RelationRule> RULES = createRules();

    private AwakenedMobPeerRelationRules() {
    }

    public static AwakenedMobPeerScene pickDefaultScene(AwakenedMobProfile first, AwakenedMobProfile second,
                                                       RandomSource random) {
        RelationRule rule = getRule(first, second);
        if (rule == null) {
            return null;
        }
        return random.nextFloat() < rule.primaryChance() ? rule.primaryScene() : rule.secondaryScene();
    }

    public static float closeConflictChance(AwakenedMobProfile first, AwakenedMobProfile second, float fallback) {
        RelationRule rule = getRule(first, second);
        return rule == null ? fallback : Math.max(fallback, rule.closeConflictChance());
    }

    public static float scuffleChance(AwakenedMobProfile first, AwakenedMobProfile second, float fallback) {
        RelationRule rule = getRule(first, second);
        return rule == null ? fallback : Math.max(fallback, rule.scuffleChance());
    }

    public static String describeRelationship(AwakenedMobProfile speaker, AwakenedMobProfile listener) {
        RelationRule rule = getRule(speaker, listener);
        return rule == null ? "" : rule.promptHint();
    }

    private static RelationRule getRule(AwakenedMobProfile first, AwakenedMobProfile second) {
        if (first == null || second == null) {
            return null;
        }
        return RULES.get(PairRootKey.of(first.root(), second.root()));
    }

    private static Map<PairRootKey, RelationRule> createRules() {
        Map<PairRootKey, RelationRule> rules = new HashMap<>();
        put(rules, "zombie", "skeleton", AwakenedMobPeerScene.COLLAB, AwakenedMobPeerScene.CASUAL,
                0.62F, 0.30F, 0.18F, "old workmates: one slow, one sharp, used to blocking paths and trading complaints");
        put(rules, "zombie", "creeper", AwakenedMobPeerScene.CONFLICT, AwakenedMobPeerScene.CASUAL,
                0.68F, 0.58F, 0.0F, "careful neighbors: both keep space because nobody wants an accidental blast");
        put(rules, "zombie", "witch", AwakenedMobPeerScene.COLLAB, AwakenedMobPeerScene.CONFLICT,
                0.55F, 0.42F, 0.18F, "the witch gives practical errands; the zombie complains but still moves");
        put(rules, "zombie", "raider", AwakenedMobPeerScene.CONFLICT, AwakenedMobPeerScene.GOSSIP,
                0.64F, 0.50F, 0.24F, "mutual disrespect: noise, mud, camp rules, and bad manners come up fast");
        put(rules, "zombie", "slime", AwakenedMobPeerScene.CONFLICT, AwakenedMobPeerScene.CASUAL,
                0.58F, 0.48F, 0.22F, "two slow messy bodies in one path, always nearly bumping");
        put(rules, "skeleton", "creeper", AwakenedMobPeerScene.COLLAB, AwakenedMobPeerScene.CONFLICT,
                0.54F, 0.42F, 0.0F, "range and pressure: one wants a clear shot, the other hates being pushed forward");
        put(rules, "skeleton", "spider", AwakenedMobPeerScene.COLLAB, AwakenedMobPeerScene.CONFLICT,
                0.60F, 0.46F, 0.22F, "ceiling-and-arrow partners who gripe about webs, beams, and blocked aim");
        put(rules, "skeleton", "raider", AwakenedMobPeerScene.CONFLICT, AwakenedMobPeerScene.GOSSIP,
                0.66F, 0.52F, 0.24F, "rival lookouts, both convinced they noticed danger first");
        put(rules, "skeleton", "phantom", AwakenedMobPeerScene.GOSSIP, AwakenedMobPeerScene.CASUAL,
                0.55F, 0.28F, 0.12F, "sky versus ground, trading remarks about angles, roofs, and who sees better");
        put(rules, "creeper", "spider", AwakenedMobPeerScene.CASUAL, AwakenedMobPeerScene.CONFLICT,
                0.56F, 0.40F, 0.0F, "quiet awkward neighbors, with one guarding corners and one guarding nerves");
        put(rules, "creeper", "enderman", AwakenedMobPeerScene.CONFLICT, AwakenedMobPeerScene.CASUAL,
                0.62F, 0.48F, 0.0F, "sudden space and sudden sparks; both dislike surprises");
        put(rules, "creeper", "raider", AwakenedMobPeerScene.CONFLICT, AwakenedMobPeerScene.COLLAB,
                0.66F, 0.54F, 0.0F, "the raider wants to use the creeper as trouble, and the creeper knows it");
        put(rules, "spider", "enderman", AwakenedMobPeerScene.CONFLICT, AwakenedMobPeerScene.CASUAL,
                0.56F, 0.44F, 0.20F, "wall routes and warped space keep crossing in irritating ways");
        put(rules, "spider", "witch", AwakenedMobPeerScene.COLLAB, AwakenedMobPeerScene.GOSSIP,
                0.58F, 0.28F, 0.12F, "forest acquaintances: web, bottle, smell, and practical favors");
        put(rules, "spider", "phantom", AwakenedMobPeerScene.CONFLICT, AwakenedMobPeerScene.GOSSIP,
                0.62F, 0.46F, 0.20F, "roofline rivals, fighting over beams, wind, wings, and legs");
        put(rules, "enderman", "guardian", AwakenedMobPeerScene.CONFLICT, AwakenedMobPeerScene.GOSSIP,
                0.70F, 0.58F, 0.18F, "water and distance against warped space and staring; neither likes the other's gaze");
        put(rules, "enderman", "phantom", AwakenedMobPeerScene.CASUAL, AwakenedMobPeerScene.GOSSIP,
                0.58F, 0.24F, 0.10F, "cold quiet watchers from different heights");
        put(rules, "witch", "raider", AwakenedMobPeerScene.COLLAB, AwakenedMobPeerScene.CONFLICT,
                0.58F, 0.46F, 0.20F, "deal partners, arguing over payment, potion smell, and who owes whom");
        put(rules, "witch", "slime", AwakenedMobPeerScene.CONFLICT, AwakenedMobPeerScene.CASUAL,
                0.56F, 0.44F, 0.20F, "one sees a useful ingredient, the other sees a suspicious bottle");
        put(rules, "witch", "blaze", AwakenedMobPeerScene.COLLAB, AwakenedMobPeerScene.CONFLICT,
                0.54F, 0.42F, 0.0F, "pot and flame, useful together but always arguing about heat and damp");
        put(rules, "raider", "piglin", AwakenedMobPeerScene.CONFLICT, AwakenedMobPeerScene.CASUAL,
                0.56F, 0.42F, 0.20F, "two kinds of bargaining, both convinced the other is greedy");
        put(rules, "raider", "beast", AwakenedMobPeerScene.COLLAB, AwakenedMobPeerScene.CONFLICT,
                0.56F, 0.46F, 0.30F, "camp handler and charging brute, useful together and hard to manage");
        put(rules, "slime", "blaze", AwakenedMobPeerScene.CONFLICT, AwakenedMobPeerScene.CASUAL,
                0.72F, 0.62F, 0.0F, "wet temper against dry fire; they annoy each other immediately");
        put(rules, "slime", "guardian", AwakenedMobPeerScene.CONFLICT, AwakenedMobPeerScene.CASUAL,
                0.54F, 0.42F, 0.16F, "cool water attracts the slime, and the guardian hates stirred water");
        put(rules, "slime", "piglin", AwakenedMobPeerScene.CONFLICT, AwakenedMobPeerScene.GOSSIP,
                0.58F, 0.46F, 0.22F, "sticky cargo trouble around gold, boxes, and anxious counting");
        put(rules, "blaze", "piglin", AwakenedMobPeerScene.COLLAB, AwakenedMobPeerScene.CONFLICT,
                0.52F, 0.40F, 0.0F, "nether neighbors, one bargaining and one burning through patience");
        put(rules, "blaze", "beast", AwakenedMobPeerScene.CONFLICT, AwakenedMobPeerScene.CASUAL,
                0.62F, 0.50F, 0.0F, "heat near tusks, sparks near snouts, a quick road to irritation");
        put(rules, "piglin", "beast", AwakenedMobPeerScene.COLLAB, AwakenedMobPeerScene.CONFLICT,
                0.56F, 0.48F, 0.30F, "box keeper and charge keeper, arguing because both are useful");
        put(rules, "piglin", "skeleton", AwakenedMobPeerScene.CONFLICT, AwakenedMobPeerScene.GOSSIP,
                0.62F, 0.52F, 0.28F, "old nether resentment around bones, gold, and who stands too close");
        put(rules, "ancient", "guardian", AwakenedMobPeerScene.AUTHORITY, AwakenedMobPeerScene.GOSSIP,
                0.70F, 0.24F, 0.0F, "deep-water pressure: when the ancient one speaks, the water feels quieter");
        put(rules, "ancient", "simmons", AwakenedMobPeerScene.AUTHORITY, AwakenedMobPeerScene.CONFLICT,
                0.56F, 0.40F, 0.0F, "two heavy presences, neither eager to step aside");
        put(rules, "ancient", "jean", AwakenedMobPeerScene.AUTHORITY, AwakenedMobPeerScene.GOSSIP,
                0.56F, 0.34F, 0.0F, "deep listening against high watching, distant and wary");
        put(rules, "simmons", "jean", AwakenedMobPeerScene.CONFLICT, AwakenedMobPeerScene.CASUAL,
                0.48F, 0.34F, 0.0F, "family weight, sibling pride, and sharp remarks without panic");
        return Map.copyOf(rules);
    }

    private static void put(Map<PairRootKey, RelationRule> rules, String firstRoot, String secondRoot,
                            AwakenedMobPeerScene primaryScene, AwakenedMobPeerScene secondaryScene,
                            float primaryChance, float closeConflictChance, float scuffleChance,
                            String promptHint) {
        rules.put(PairRootKey.of(firstRoot, secondRoot), new RelationRule(
                primaryScene,
                secondaryScene,
                primaryChance,
                closeConflictChance,
                scuffleChance,
                promptHint
        ));
    }

    private record RelationRule(AwakenedMobPeerScene primaryScene, AwakenedMobPeerScene secondaryScene,
                                float primaryChance, float closeConflictChance, float scuffleChance,
                                String promptHint) {
    }

    private record PairRootKey(String firstRoot, String secondRoot) {
        private static PairRootKey of(String firstRoot, String secondRoot) {
            String first = normalize(firstRoot);
            String second = normalize(secondRoot);
            return first.compareTo(second) <= 0 ? new PairRootKey(first, second) : new PairRootKey(second, first);
        }

        private static String normalize(String root) {
            return root == null ? "" : root.trim().toLowerCase(Locale.ROOT);
        }
    }
}
