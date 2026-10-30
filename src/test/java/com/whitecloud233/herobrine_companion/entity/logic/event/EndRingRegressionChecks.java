package com.whitecloud233.herobrine_companion.entity.logic.event;

import com.whitecloud233.herobrine_companion.entity.logic.data.HeroDimensionSnapshot;
import com.whitecloud233.herobrine_companion.util.EndRingContext;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.List;
import java.util.UUID;

public final class EndRingRegressionChecks {
    private static int checks;

    public static void main(String[] args) throws Exception {
        storyReload();
        equipmentRoundTrip();
        lifecycleWiring();
        System.out.println("EndRingRegressionChecks: " + checks + " checks passed");
    }

    private static void storyReload() {
        CompoundTag player = new CompoundTag();
        player.putInt("WakeUpStage", 3);
        player.putInt("SkyGazeTicks", 199);
        check(EndRingStoryState.beginVoidReturn(player), "First completed-story fall starts the disconnect sequence");
        check(player.getBoolean("HasSimulatedCrash"), "Crash progress must survive saving");
        check(!player.contains("SkyGazeTicks"), "Falling cancels the near-complete gaze timer");
        for (int i = 0; i < 240; i++) {
            check(!EndRingStoryState.tickSkyGaze(player, -90), "Disconnect sequence cannot return to overworld");
        }
        CompoundTag reloaded = player.copy();
        check(EndRingStoryState.resume(reloaded, 1000), "Login consumes the saved ring-return request");
        check(reloaded.getInt("WakeUpStage") == 3, "Missing legacy entry time cannot reset completed dialogue");
        check(!EndRingStoryState.resume(reloaded, 1001), "Ring-return request is consumed only once");
        for (int i = 0; i < 199; i++) {
            check(!EndRingStoryState.tickSkyGaze(reloaded, -90), "Login requires a fresh full gaze duration");
        }
        check(EndRingStoryState.tickSkyGaze(reloaded, -90), "200 fresh gaze ticks return to overworld");
        check(!EndRingStoryState.beginVoidReturn(reloaded), "Repeated fall never repeats the forced disconnect");
        reloaded.putInt("SkyGazeTicks", 199);
        check(!EndRingStoryState.tickSkyGaze(reloaded, -60), "Gaze threshold is strict");
        check(!reloaded.contains("SkyGazeTicks"), "Looking away resets the timer");
        reloaded.putInt("SkyGazeTicks", 199);
        EndRingStoryState.resume(reloaded, 2000);
        check(!EndRingStoryState.tickSkyGaze(reloaded, -90), "Ordinary relog also clears stale gaze progress");
        for (int stage = 0; stage < 3; stage++) {
            CompoundTag early = new CompoundTag();
            early.putInt("WakeUpStage", stage);
            check(!EndRingStoryState.beginVoidReturn(early), "Incomplete dialogue cannot trigger disconnect");
            check(!EndRingStoryState.tickSkyGaze(early, -90), "Incomplete dialogue cannot wake the player");
        }
        double radius = Math.hypot(Math.floor(EndRingContext.SAFE_X), Math.floor(EndRingContext.SAFE_Z));
        check(radius >= 50 && radius <= 60, "Recovery must land on the bedrock inner ring, outside the portal disk");
        check(EndRingContext.SAFE_Y >= 103, "Recovery must be above the three bedrock layers");
        var introduction = EndRingContext.heroAnchor();
        check(introduction.x == 0.5 && introduction.y == 102 && introduction.z == 0.5,
                "First entry and fixed introduction must keep Herobrine at the arena center");
        var recovered = EndRingContext.heroAnchor();
        check(recovered.equals(introduction),
                "Introduction, reload and void rescue must keep Herobrine at the same center anchor");
    }

    private static void equipmentRoundTrip() {
        CompoundTag original = new CompoundTag();
        UUID owner = UUID.randomUUID();
        original.putUUID("OwnerUUID", owner);
        original.putUUID("UUID", UUID.randomUUID());
        original.putInt("TrustLevel", 77);
        original.put("ArmorItems", slots("minecraft:diamond_boots", "minecraft:netherite_leggings", "minecraft:diamond_chestplate", "minecraft:diamond_helmet"));
        original.put("HandItems", slots("minecraft:diamond_axe", "minecraft:shield"));
        for (String key : List.of("CuriosBackItem", "AccessoriesData", "ForgeCaps", "neoforge:attachments")) {
            CompoundTag equipment = new CompoundTag();
            equipment.putString("custom", "retained-" + key);
            original.put(key, equipment);
        }
        ListTag tags = new ListTag();
        for (String tag : List.of(EndRingContext.TAG_INTRO, EndRingContext.TAG_FIXED,
                EndRingContext.TAG_RESPAWNED_SAFE, EndRingContext.TAG_TELEPORTING, "player_custom_tag")) {
            tags.add(StringTag.valueOf(tag));
        }
        original.put("Tags", tags);
        CompoundTag persistent = new CompoundTag();
        persistent.putInt("IntroTick", 101);
        persistent.putString("OtherModState", "keep");
        original.put("ForgeData", persistent);
        CompoundTag before = original.copy();
        CompoundTag entered = HeroDimensionSnapshot.prepare(original);
        CompoundTag returned = HeroDimensionSnapshot.prepare(entered);
        for (String key : List.of("ArmorItems", "HandItems", "CuriosBackItem", "AccessoriesData", "ForgeCaps", "neoforge:attachments")) {
            check(original.get(key).equals(returned.get(key)), "Full round trip preserves " + key);
        }
        check(returned.getUUID("OwnerUUID").equals(owner), "Owner identity survives both directions");
        check(returned.getInt("TrustLevel") == 77, "Trust survives both directions");
        check(!returned.hasUUID("UUID"), "Replacement receives its own entity identity");
        check(returned.getList("Tags", Tag.TAG_STRING).equals(strings("player_custom_tag")), "Only End Ring presentation tags are cleared");
        check(!returned.getCompound("ForgeData").contains("IntroTick"), "Re-entry does not inherit a completed intro timer");
        check(returned.getCompound("ForgeData").getString("OtherModState").equals("keep"), "Other persistent data is preserved");
        returned.getList("ArmorItems", Tag.TAG_COMPOUND).clear();
        check(original.equals(before), "Preparing and modifying a transfer never consumes the source snapshot");
        CompoundTag emptyEquipment = new CompoundTag();
        emptyEquipment.put("ArmorItems", slots("", "", "", ""));
        emptyEquipment.put("HandItems", slots("", ""));
        check(HeroDimensionSnapshot.prepare(emptyEquipment).equals(emptyEquipment), "Intentionally empty slots stay empty");
    }

    private static void lifecycleWiring() throws Exception {
        String root = EndRingRegressionChecks.class.getPackageName().replace(".entity.logic.event", "").replace('.', '/');
        MethodNode transfer = method(root + "/entity/logic/data/HeroDimensionHandler", "transferHero");
        check(call(transfer, "addFreshEntity") < call(transfer, "setActiveHeroUUID"), "Canonical identity changes only after the target joins");
        check(call(transfer, "setActiveHeroUUID") < call(transfer, "discard"), "Source deletion follows replacement and canonical commit");
        MethodNode wake = method(root + "/entity/logic/event/EndRingDimensionHandler", "executeReturnToOverworld");
        check(call(wake, "discard") == Integer.MAX_VALUE, "Story exit cannot independently delete a Hero");
        MethodNode tick = method(root + "/entity/logic/HeroServerTick", "tickBehavior");
        check(call(tick, "maintainPersistence") < call(tick, "handleVoidProtection"), "End Ring preserves identity and equipment before its early return");
        MethodNode duplicates = method(root + "/entity/logic/data/HeroLifecycleHandler", "checkForDuplicates");
        check(call(duplicates, "getLoadedHeroes") != Integer.MAX_VALUE, "Join dedup sees loaded entities before their first tick");
    }

    private static MethodNode method(String className, String methodName) throws Exception {
        ClassNode node = new ClassNode();
        new ClassReader(className).accept(node, 0);
        return node.methods.stream().filter(method -> method.name.equals(methodName)).findFirst().orElseThrow();
    }

    private static int call(MethodNode method, String name) {
        for (int i = 0; i < method.instructions.size(); i++) {
            if (method.instructions.get(i) instanceof MethodInsnNode call && call.name.equals(name)) return i;
        }
        return Integer.MAX_VALUE;
    }

    private static ListTag slots(String... ids) {
        ListTag result = new ListTag();
        for (String id : ids) {
            CompoundTag item = new CompoundTag();
            if (!id.isEmpty()) {
                item.putString("id", id);
                item.putByte("Count", (byte) 1);
                CompoundTag custom = new CompoundTag();
                custom.putInt("Damage", 37);
                custom.putString("Name", "equipment with custom data");
                item.put("tag", custom);
                item.put("components", custom.copy());
            }
            result.add(item);
        }
        return result;
    }

    private static ListTag strings(String value) {
        ListTag result = new ListTag();
        result.add(StringTag.valueOf(value));
        return result;
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        checks++;
    }
}
