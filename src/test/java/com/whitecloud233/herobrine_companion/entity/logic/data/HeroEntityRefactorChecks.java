package com.whitecloud233.herobrine_companion.entity.logic.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class HeroEntityRefactorChecks {
    private static final String MOD = "com/whitecloud233/herobrine_companion/";
    private static final String HERO = MOD + "entity/HeroEntity";
    private static int checks;

    public static void main(String[] args) throws Exception {
        savedIdentity();
        equipmentSnapshots();
        savedPoses();
        lifecycleWiring();
        syncedDataContract();
        stateOwnership();
        System.out.println("HeroEntityRefactorChecks: " + checks + " checks passed");
    }

    private static void savedIdentity() {
        CompoundTag tag = new CompoundTag();
        check(HeroEntityPersistence.readSkinVariant(tag) == 2, "Fresh saves retain the default skin");
        tag.putBoolean("UseHerobrineSkin", true);
        check(HeroEntityPersistence.readSkinVariant(tag) == 0, "Legacy Herobrine skin migrates");
        tag.putBoolean("UseHerobrineSkin", false);
        check(HeroEntityPersistence.readSkinVariant(tag) == 1, "Legacy Hero skin migrates");
        tag.putInt("SkinVariant", 999);
        check(HeroEntityPersistence.readSkinVariant(tag) == 999, "Modern custom skin takes priority over legacy data");
        tag.putInt("SkinVariant", 0);
        check(HeroEntityPersistence.readSkinVariant(tag) == 0, "Explicit zero is not replaced by the default");
        check(HeroEntityPersistence.readOwnerUUID(tag) == null, "Missing owner stays unbound");
        UUID legacy = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID modern = UUID.fromString("22222222-2222-2222-2222-222222222222");
        tag.putString("OwnerUUID_String", legacy.toString());
        check(legacy.equals(HeroEntityPersistence.readOwnerUUID(tag)), "Legacy owner string migrates");
        tag.putUUID("OwnerUUID", modern);
        check(modern.equals(HeroEntityPersistence.readOwnerUUID(tag)), "UUID tag has priority over legacy text");
        tag.putString("OwnerUUID_String", "invalid");
        check(modern.equals(HeroEntityPersistence.readOwnerUUID(tag)), "A bad legacy fallback cannot replace a valid UUID");
        tag.remove("OwnerUUID");
        for (String invalid : List.of("", "invalid", "not-a-uuid")) {
            tag.putString("OwnerUUID_String", invalid);
            check(HeroEntityPersistence.readOwnerUUID(tag) == null, "Malformed owner text stays unbound");
        }
        tag.putIntArray("OwnerUUID", new int[]{1, 2});
        tag.putString("OwnerUUID_String", legacy.toString());
        check(legacy.equals(HeroEntityPersistence.readOwnerUUID(tag)), "Malformed UUID array falls back to legacy text");
        CompoundTag before = tag.copy();
        HeroEntityPersistence.readSkinVariant(tag);
        HeroEntityPersistence.readOwnerUUID(tag);
        check(tag.equals(before), "Identity decoding never rewrites source NBT");
    }

    private static void equipmentSnapshots() {
        CompoundTag tag = new CompoundTag();
        check(!HeroEntityPersistence.hasCompleteEquipment(tag), "Missing equipment allows global restoration");
        tag.put("ArmorItems", slots(4));
        check(!HeroEntityPersistence.hasCompleteEquipment(tag), "Armor alone is incomplete");
        tag.put("HandItems", slots(2));
        check(HeroEntityPersistence.hasCompleteEquipment(tag), "Explicit empty slots form a complete snapshot");
        for (int armor : new int[]{0, 1, 3, 5}) {
            tag.put("ArmorItems", slots(armor));
            check(!HeroEntityPersistence.hasCompleteEquipment(tag), "Reject incompatible armor slot count " + armor);
        }
        tag.put("ArmorItems", slots(4));
        for (int hands : new int[]{0, 1, 3}) {
            tag.put("HandItems", slots(hands));
            check(!HeroEntityPersistence.hasCompleteEquipment(tag), "Reject incompatible hand slot count " + hands);
        }
        ListTag wrongType = new ListTag();
        wrongType.add(StringTag.valueOf("item"));
        wrongType.add(StringTag.valueOf("item"));
        tag.put("HandItems", wrongType);
        check(!HeroEntityPersistence.hasCompleteEquipment(tag), "Wrong list element type is not a complete snapshot");
        tag.put("HandItems", slots(2));
        CompoundTag before = tag.copy();
        check(HeroEntityPersistence.hasCompleteEquipment(tag) && tag.equals(before), "Snapshot check is read-only");
    }

    private static ListTag slots(int count) {
        ListTag slots = new ListTag();
        for (int i = 0; i < count; i++) slots.add(new CompoundTag());
        return slots;
    }

    private static void savedPoses() {
        CompoundTag tag = new CompoundTag();
        check(HeroDataHandler.readPoseAngles(tag) == null, "Legacy saves without a pose remain animated");
        tag.putBoolean("IsPoseEditing", true);
        check(HeroDataHandler.readPoseAngles(tag) == null, "Enabled pose requires a complete angle list");
        ListTag angles = new ListTag();
        for (int i = 0; i < 30; i++) angles.add(FloatTag.valueOf(0.0F));
        tag.put("CustomPoseAngles", angles);
        check(HeroDataHandler.readPoseAngles(tag) == null, "Legacy untouched zero pose is migrated");
        tag.putInt("PoseDataVersion", 1);
        check(HeroDataHandler.readPoseAngles(tag) != null, "Versioned intentional neutral pose is retained");
        for (float invalid : new float[]{Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY}) {
            angles.set(29, FloatTag.valueOf(invalid));
            check(HeroDataHandler.readPoseAngles(tag) == null, "Invalid angles cannot freeze or corrupt the model");
        }
        angles.set(29, FloatTag.valueOf(0.5F));
        CompoundTag before = tag.copy();
        float[][] first = HeroDataHandler.readPoseAngles(tag);
        float[][] second = HeroDataHandler.readPoseAngles(tag);
        check(first != null && first.length == 10 && first[9][2] == 0.5F, "Joint order survives pose restoration");
        first[0][0] = 1.0F;
        check(second[0][0] == 0.0F && tag.equals(before), "Pose arrays do not alias other entities or NBT");
        tag.getList("CustomPoseAngles", Tag.TAG_FLOAT).remove(0);
        check(HeroDataHandler.readPoseAngles(tag) == null, "Incomplete pose lists are rejected");
    }

    private static void lifecycleWiring() throws Exception {
        String data = MOD + "entity/logic/data/";
        String logic = MOD + "entity/logic/";
        checkCallsInOrder(HERO, "addAdditionalSaveData",
                data + "HeroEntityPersistence.save");
        checkCallsInOrder(HERO, "readAdditionalSaveData",
                data + "HeroEntityPersistence.load");
        checkCallsInOrder(HERO, "remove", data + "HeroLifecycleHandler.beforeRemove",
                "net/minecraft/world/entity/PathfinderMob.remove", data + "HeroLifecycleHandler.afterRemove");
        checkCallsInOrder(HERO, "die", data + "HeroLifecycleHandler.onDeath",
                "net/minecraft/world/entity/PathfinderMob.die");
        checkCallsInOrder(HERO, "startSeenByPlayer", "net/minecraft/world/entity/PathfinderMob.startSeenByPlayer",
                logic + "HeroChallengePresentation.startTracking", data + "HeroTrackingSync.sendSnapshot");
        checkCallsInOrder(HERO, "tick", "net/minecraft/world/entity/PathfinderMob.tick",
                logic + "HeroChallengePresentation.tickAfterimages", logic + "HeroMovementState.syncGroundWalking",
                MOD + "event/HeroVisuals.tickClientAnimations", logic + "HeroActionTiming.tick",
                MOD + "event/HeroVisuals.tickClientActionParticles", MOD + "fight/HeroChallengeState.tick",
                logic + "HeroChallengePresentation.tickBossBar", logic + "HeroServerTick.tickBehavior",
                logic + "HeroOtherProtection.tick", MOD + "event/HeroVisuals.tickClientLocomotion",
                MOD + "event/HeroVisuals.tickClientAmbient");
        checkCallsInOrder(logic + "HeroServerTick", "tickBehavior", data + "HeroLogic.tick",
                MOD + "entity/gift/HeroGiftReturnService.tick", HERO + ".holdForGiftGesture",
                MOD + "entity/ai/learning/HeroBrain.tick", MOD + "entity/ai/agent/HeroAgent.tick",
                MOD + "entity/ai/agent/HeroAgent.tickTasks", logic + "HeroServerTick.handleTick");
        checkCallsInOrder(data + "HeroEntityPersistence", "load", MOD + "fight/HeroChallengeState.onRestoreFromDisk",
                HERO + ".setTrustLevel", HERO + ".setBattleModeActive", HERO + ".setCompanionMode",
                HERO + ".setOwnerUUID", data + "HeroDataHandler.loadPoseData",
                HERO + ".setCuriosBackItemFromTag", HERO + ".setAccessoriesDataFromTag",
                data + "HeroPersistenceState.loaded", MOD + "entity/ai/agent/HeroAgent.loadTasks");
        checkCallsInOrder(data + "HeroEntityPersistence", "save", data + "HeroDataHandler.savePoseData",
                HERO + ".getCuriosBackItemTag", HERO + ".getAccessoriesDataTag",
                MOD + "entity/ai/agent/HeroAgent.saveTasks");
        checkCallsInOrder(data + "HeroTrackingSync", "sendSnapshot", MOD + "network/SavePosePacket.<init>",
                MOD + "network/SyncHeroCosmeticsPacket.<init>", MOD + "network/SyncRewardsPacket.<init>");
        for (String method : List.of("setTrustLevel", "setSkinVariant", "setCustomSkinName", "setOwnerUUID",
                "getOwnerDisplayNameComponent", "createNotYourHeroMessage", "hasClaimedReward", "claimReward")) {
            checkCallsInOrder(HERO, method, data + "HeroOwnerProfile." + method);
        }
        for (var instruction : method(data + "HeroEntityPersistence", "load").instructions) {
            if (instruction instanceof MethodInsnNode call) {
                check(!call.name.equals("setSkinVariant") && !call.name.equals("setCustomSkinName"),
                        "Local NBT restoration must not overwrite the global appearance profile");
            }
        }
    }

    private static void syncedDataContract() throws Exception {
        List<String> expected = List.of("IS_FLOATING", "IS_GROUND_WALKING", "TRUST_LEVEL", "IS_COMPANION_MODE",
                "SKIN_VARIANT", "CUSTOM_SKIN_NAME", "OWNER_UUID", "INVITED_POS", "INVITED_ACTION", "MILLSTONE_BOUND",
                "MIND_STATE", "INSPECTING_SCYTHE", "IS_GLITCHING", "IS_DEBUGGING", "IS_CASTING_THUNDER",
                "IS_CHALLENGE_ACTIVE", "CHALLENGE_TICKS", "BATTLE_MODE_ACTIVE", "AUTO_BATTLE_SCYTHE",
                "BATTLE_ACTION_STATE", "BATTLE_ACTION_TICKS", "BATTLE_COMBO_STEP", "BATTLE_ACTION_SERIAL",
                "BATTLE_POEM_MOVE", "VISUAL_MAIN_HAND_ITEM", "VISUAL_OFF_HAND_ITEM", "VISUAL_EATING_ACTIVE",
                "OFFER_GESTURE_ID", "OFFER_GESTURE_START_TICK", "OFFER_GESTURE_DURATION_TICKS", "OFFER_GESTURE_TOKEN");
        List<String> registered = new ArrayList<>();
        for (var instruction : method(HERO, "<clinit>").instructions) {
            if (instruction instanceof FieldInsnNode field && field.getOpcode() == Opcodes.PUTSTATIC
                    && field.owner.equals(HERO) && field.desc.equals("Lnet/minecraft/network/syncher/EntityDataAccessor;")) {
                registered.add(field.name);
            }
        }
        check(registered.equals(expected), "Entity data registration IDs and order remain compatible");
        List<String> defined = new ArrayList<>();
        for (var instruction : method(HERO, "defineSynchedData").instructions) {
            if (instruction instanceof FieldInsnNode field && field.getOpcode() == Opcodes.GETSTATIC
                    && field.owner.equals(HERO) && expected.contains(field.name)) defined.add(field.name);
        }
        check(defined.equals(expected), "Every synchronized field still receives its original definition");
    }

    private static void stateOwnership() throws Exception {
        Set<String> removed = Set.of("claimedRewards", "clientFloatingAmount", "clientFloatingAmountO",
                "clientImportedWalkTicks", "clientImportedWalkTicksO", "clientSideSetupDone", "idleVariantClip",
                "idleVariantStartTick", "idleVariantNextAt", "idleVariantLast", "idleVariantActive",
                "gestureEventCursor", "gestureEventTokenSeen", "gestureLocalStartTick", "gestureLocalClipId",
                "gestureLocalFinished", "clientOfferBlend", "clientOfferBlendO", "clientVariantBlend", "clientVariantBlendO");
        for (var field : read(HERO).fields) {
            check(!removed.contains(field.name), "Entity no longer owns subsystem state: " + field.name);
        }
        for (var field : read(MOD + "entity/visual/HeroVisualState").fields) {
            check((field.access & Opcodes.ACC_PRIVATE) != 0, "Animation state cannot be mutated as public fields");
        }
        for (var instruction : method(HERO, "<init>").instructions) {
            if (instruction instanceof TypeInsnNode type && type.getOpcode() == Opcodes.NEW) {
                check(!type.desc.equals(MOD + "entity/visual/HeroVisualState"),
                        "Server entities do not eagerly allocate client playback state");
            }
        }
        for (var method : read(HERO).methods) {
            for (var instruction : method.instructions) {
                if (instruction instanceof MethodInsnNode call) {
                    check(!call.owner.equals(MOD + "entity/logic/data/HeroWorldData")
                            && !call.owner.equals(MOD + "network/PacketHandler"),
                            "Entity facade must not perform profile persistence or send tracking packets");
                }
            }
        }
    }

    private static void checkCallsInOrder(String type, String name, String... expected) throws Exception {
        int next = 0;
        for (var instruction : method(type, name).instructions) {
            if (instruction instanceof MethodInsnNode call && next < expected.length
                    && (call.owner + "." + call.name).equals(expected[next])) next++;
        }
        check(next == expected.length, type + "." + name + " preserves lifecycle order; missing "
                + (next < expected.length ? expected[next] : "none"));
    }

    private static MethodNode method(String type, String name) throws Exception {
        return read(type).methods.stream().filter(method -> method.name.equals(name)).findFirst()
                .orElseThrow(() -> new AssertionError("Missing method: " + type + "." + name));
    }

    private static ClassNode read(String type) throws Exception {
        ClassNode node = new ClassNode();
        try (var input = HeroEntityRefactorChecks.class.getClassLoader().getResourceAsStream(type + ".class")) {
            if (input == null) throw new AssertionError("Missing class: " + type);
            new ClassReader(input).accept(node, 0);
        }
        return node;
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        checks++;
    }
}
