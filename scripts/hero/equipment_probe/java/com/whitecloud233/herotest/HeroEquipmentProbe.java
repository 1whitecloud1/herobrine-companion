package com.whitecloud233.herotest;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.authlib.GameProfile;
import com.whitecloud233.herobrine_companion.compat.accessories.HeroAccessoriesCompat;
import com.whitecloud233.herobrine_companion.entity.HeroEntity;
import com.whitecloud233.herobrine_companion.entity.ai.learning.HeroBrain;
import com.whitecloud233.herobrine_companion.entity.logic.HeroServerTick;
import com.whitecloud233.herobrine_companion.entity.logic.data.HeroDimensionHandler;
import com.whitecloud233.herobrine_companion.entity.logic.data.HeroLifecycleHandler;
import com.whitecloud233.herobrine_companion.entity.logic.data.HeroStateManager;
import com.whitecloud233.herobrine_companion.entity.logic.data.HeroWorldData;
import com.whitecloud233.herobrine_companion.entity.logic.data.PlayerLifecycleHandler;
import com.whitecloud233.herobrine_companion.init.ModEntities;
import com.whitecloud233.herobrine_companion.item.HeroSummonItem;
import com.whitecloud233.herobrine_companion.world.inventory.HeroWardrobeMenu;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Mod("hero_equipment_probe")
public final class HeroEquipmentProbe {
    private final List<HeroEntity> fixtures = new ArrayList<>();
    private final JsonArray cases = new JsonArray();
    private int assertions;
    private int failures;
    private ServerLevel level;
    private ServerStartedEvent pendingStart;
    private int startupTicks;

    public HeroEquipmentProbe() {
        NeoForge.EVENT_BUS.addListener(this::schedule);
        NeoForge.EVENT_BUS.addListener(this::afterTick);
    }

    private void schedule(ServerStartedEvent event) {
        pendingStart = event;
        event.getServer().overworld().setChunkForced(0, 0, true);
        event.getServer().overworld().setChunkForced(12, 0, true);
        event.getServer().getLevel(Level.NETHER).setChunkForced(0, 0, true);
    }

    private void afterTick(net.neoforged.neoforge.event.tick.ServerTickEvent.Post event) {
        if (pendingStart == null) return;
        // Let normal server ticks make the forced destinations available to entity tracking.
        if (++startupTicks < 40) return;
        ServerStartedEvent started = pendingStart;
        pendingStart = null;
        run(started);
    }

    private void run(ServerStartedEvent event) {
        MinecraftServer server = event.getServer();
        level = server.overworld();
        try {
            test("legacy zero pose releases animations on entity reload and global respawn", () -> {
                ServerPlayer owner = player();
                HeroEntity old = active(owner.getUUID());
                equip(old);
                old.setTrustLevel(87);
                HeroStateManager.backupToGlobal(old);
                Snapshot expected = snapshot(old);
                CompoundTag legacy = saved(old);
                legacy.remove("PoseDataVersion");
                legacy.putBoolean("IsPoseEditing", true);
                ListTag zeroAngles = new ListTag();
                for (int i = 0; i < 30; i++) zeroAngles.add(FloatTag.valueOf(0.0F));
                legacy.put("CustomPoseAngles", zeroAngles);
                CompoundTag globalPose = new CompoundTag();
                globalPose.putBoolean("IsPoseEditing", true);
                globalPose.put("CustomPoseAngles", zeroAngles.copy());
                worldData().setPoseData(owner.getUUID(), globalPose);
                old.discard();

                HeroEntity loaded = fresh(owner.getUUID());
                loaded.load(legacy);
                activate(loaded);
                check(!loaded.isPoseEditing, "loading old entity must release its fixed pose");
                assertEquipment(loaded, expected);
                check(loaded.getTrustLevel() == 87, "pose migration must preserve trust");
                loaded.discard();

                HeroEntity respawned = fresh(owner.getUUID());
                HeroStateManager.restoreFromGlobal(respawned, owner);
                activate(respawned);
                check(!respawned.isPoseEditing, "global backup must not freeze a replacement");
                assertEquipment(respawned, expected);
                check(respawned.getTrustLevel() == 87, "respawn migration must preserve trust");
                check(!worldData().getPoseData(owner.getUUID()).getBoolean("IsPoseEditing"),
                        "migration must write the cleared state back to the global backup");
                check(!worldData().getPoseData(owner.getUUID()).contains("CustomPoseAngles"),
                        "global backup must remove the stale zero angles");
                HeroEntity reloaded = fresh(owner.getUUID());
                reloaded.load(saved(respawned));
                check(!reloaded.isPoseEditing, "a second save/reload must keep normal animation");
            });
            test("newly saved neutral pose survives reload and global respawn", () -> {
                ServerPlayer owner = player();
                HeroEntity old = active(owner.getUUID());
                old.isPoseEditing = true;
                old.customPoseAngles = new float[10][3];
                HeroStateManager.backupToGlobal(old);
                CompoundTag saved = saved(old);
                check(saved.getInt("PoseDataVersion") == 1, "new poses need a format marker");
                old.discard();
                HeroEntity loaded = fresh(owner.getUUID());
                loaded.load(saved);
                check(loaded.isPoseEditing, "intentional new neutral pose must survive entity reload");
                HeroEntity respawned = fresh(owner.getUUID());
                HeroStateManager.restoreFromGlobal(respawned, owner);
                check(respawned.isPoseEditing, "intentional new neutral pose must survive global restore");
            });
            test("stale duplicate cannot overwrite live equipment or backup", () -> {
                UUID owner = UUID.randomUUID();
                HeroEntity live = active(owner);
                equip(live);
                HeroStateManager.backupToGlobal(live);
                Snapshot expected = snapshot(live);
                HeroEntity stale = fresh(owner);
                HeroStateManager.backupToGlobal(stale);
                assertBackup(owner, expected);
                HeroStateManager.syncEntityToEntity(stale, live);
                assertEquipment(live, expected);
                assertBackup(owner, expected);
            });
            test("duplicate tick is rejected before its periodic equipment backup", () -> {
                UUID owner = UUID.randomUUID();
                HeroEntity live = active(owner);
                equip(live);
                HeroStateManager.backupToGlobal(live);
                Snapshot expected = snapshot(live);
                HeroEntity stale = fresh(owner);
                stale.tickCount = 20;
                check(!HeroServerTick.handleTick(stale, level), "duplicate must stop ticking");
                check(stale.isRemoved(), "duplicate must be discarded");
                assertEquipment(live, expected);
                assertBackup(owner, expected);
            });
            test("closing an old wardrobe cannot erase replacement equipment", () -> {
                ServerPlayer owner = player();
                HeroEntity old = active(owner.getUUID());
                equip(old);
                HeroWardrobeMenu oldMenu = new HeroWardrobeMenu(1, owner.getInventory(), old);
                CompoundTag data = saved(old);
                old.discard();
                HeroEntity replacement = fresh(owner.getUUID());
                replacement.load(data);
                activate(replacement);
                HeroStateManager.backupToGlobal(replacement);
                Snapshot expected = snapshot(replacement);
                for (EquipmentSlot slot : new EquipmentSlot[] {EquipmentSlot.FEET, EquipmentSlot.LEGS,
                        EquipmentSlot.CHEST, EquipmentSlot.HEAD, EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND}) {
                    old.setItemSlot(slot, ItemStack.EMPTY);
                }
                oldMenu.removed(owner);
                HeroStateManager.backupToGlobal(old);
                assertBackup(owner.getUUID(), expected);
                assertEquipment(replacement, expected);
            });
            test("canonical handoff carries equipment and intentional empty slots", () -> {
                UUID owner = UUID.randomUUID();
                HeroEntity source = active(owner);
                equip(source);
                source.setItemSlot(EquipmentSlot.FEET, ItemStack.EMPTY);
                source.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
                HeroEntity target = fresh(owner);
                equip(target);
                Snapshot expected = snapshot(source);
                HeroStateManager.syncEntityToEntity(source, target);
                assertEquipment(target, expected);
                check(target.equipmentStateRestored, "handoff must complete initialization");
                check(target.getUUID().equals(worldData().getActiveHeroUUID(owner)), "survivor must be registered");
                source.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
                assertEquipment(target, expected);
            });
            test("unknown duplicate only fills missing slots once", () -> {
                UUID owner = UUID.randomUUID();
                HeroEntity source = fresh(owner);
                equip(source);
                HeroEntity target = fresh(owner);
                ItemStack ownHelmet = named(Items.IRON_HELMET);
                target.setItemSlot(EquipmentSlot.HEAD, ownHelmet.copy());
                HeroStateManager.syncEntityToEntity(source, target);
                check(ItemStack.matches(ownHelmet, target.getItemBySlot(EquipmentSlot.HEAD)), "occupied slot must win");
                check(ItemStack.matches(source.getItemBySlot(EquipmentSlot.FEET), target.getItemBySlot(EquipmentSlot.FEET)), "missing boots must transfer");
                target.setItemSlot(EquipmentSlot.FEET, ItemStack.EMPTY);
                HeroStateManager.syncEntityToEntity(source, target);
                check(target.getItemBySlot(EquipmentSlot.FEET).isEmpty(), "removed boots must stay removed");
            });
            test("several stale duplicates cannot steal canonical identity during cleanup", () -> {
                UUID owner = UUID.randomUUID();
                HeroEntity source = active(owner);
                equip(source);
                Snapshot expected = snapshot(source);
                HeroEntity stale = fresh(owner);
                HeroEntity target = fresh(owner);
                HeroStateManager.syncEntityToEntity(stale, target);
                check(HeroLifecycleHandler.findActiveHero(level, owner) == source, "stale pair must not replace canonical identity");
                HeroStateManager.syncEntityToEntity(source, target);
                assertEquipment(target, expected);
            });
            test("global fallback restores armor around an occupied hand only once", () -> {
                ServerPlayer owner = player();
                HeroEntity old = active(owner.getUUID());
                equip(old);
                HeroStateManager.backupToGlobal(old);
                Snapshot expected = snapshot(old);
                old.discard();
                HeroEntity restored = fresh(owner.getUUID());
                ItemStack held = named(Items.STICK);
                restored.setItemSlot(EquipmentSlot.MAINHAND, held.copy());
                HeroStateManager.restoreFromGlobal(restored, owner);
                check(restored.getArmorItemsTag().equals(expected.armor()), "occupied hand must not block armor recovery");
                check(ItemStack.matches(held, restored.getMainHandItem()), "occupied hand must survive fallback");
                restored.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
                HeroStateManager.restoreFromGlobal(restored, owner);
                check(restored.getItemBySlot(EquipmentSlot.HEAD).isEmpty(), "repeated fallback must not duplicate removed armor");
            });
            test("complete empty NBT remains authoritative over old equipment backup", () -> {
                ServerPlayer owner = player();
                HeroEntity old = active(owner.getUUID());
                equip(old);
                HeroStateManager.backupToGlobal(old);
                old.discard();
                HeroEntity empty = fresh(owner.getUUID());
                Snapshot expected = snapshot(empty);
                HeroEntity restored = fresh(owner.getUUID());
                restored.load(saved(empty));
                HeroStateManager.restoreFromGlobal(restored, owner);
                assertEquipment(restored, expected);
            });
            test("first periodic backup restores partial legacy NBT without an online owner", () -> {
                UUID owner = UUID.randomUUID();
                HeroEntity old = active(owner);
                equip(old);
                HeroStateManager.backupToGlobal(old);
                Snapshot expected = snapshot(old);
                old.discard();
                HeroEntity restored = fresh(owner);
                CompoundTag incomplete = saved(restored);
                incomplete.remove("ArmorItems");
                incomplete.remove("HandItems");
                restored.load(incomplete);
                activate(restored);
                restored.tickCount = 20;
                check(HeroServerTick.handleTick(restored, level), "restored entity must remain active");
                assertEquipment(restored, expected);
                assertBackup(owner, expected);
            });
            test("early player snapshot restores equipment before capturing it", () -> {
                ServerPlayer owner = player();
                HeroEntity old = active(owner.getUUID());
                equip(old);
                HeroStateManager.backupToGlobal(old);
                Snapshot expected = snapshot(old);
                old.discard();
                HeroEntity fresh = fresh(owner.getUUID());
                HeroStateManager.backupToPlayerNBT(fresh, owner, "HeroRespawnData");
                HeroEntity restored = fresh(owner.getUUID());
                check(HeroStateManager.restoreFromPlayerNBT(restored, owner, "HeroRespawnData"), "early snapshot must be readable");
                assertEquipment(restored, expected);
            });
            test("compressed entity NBT preserves item data, empty slots and accessories", () -> {
                HeroEntity source = active(UUID.randomUUID());
                equip(source);
                source.setItemSlot(EquipmentSlot.LEGS, ItemStack.EMPTY);
                Snapshot expected = snapshot(source);
                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                NbtIo.writeCompressed(saved(source), bytes);
                CompoundTag tag = NbtIo.readCompressed(new ByteArrayInputStream(bytes.toByteArray()), net.minecraft.nbt.NbtAccounter.unlimitedHeap());
                HeroEntity target = fresh(source.getOwnerUUID());
                target.load(tag);
                assertEquipment(target, expected);
                check(target.equipmentStateRestored, "complete native NBT must mark restoration complete");
                source.getMainHandItem().setCount(0);
                assertEquipment(target, expected);
            });
            test("legacy capability NBT without explicit accessory tags stays intact", () -> {
                HeroEntity source = active(UUID.randomUUID());
                equip(source);
                Snapshot expected = snapshot(source);
                CompoundTag tag = saved(source);
                tag.remove("CuriosBackItem");
                tag.remove("AccessoriesData");
                Files.writeString(Path.of(System.getProperty("hero.equipmentProbe.report")).resolveSibling("legacy-nbt.snbt"), tag.toString());
                HeroEntity target = fresh(source.getOwnerUUID());
                target.load(tag);
                assertEquipment(target, expected);
            });
            test("restoration chooses HB owner instead of another summoning player", () -> {
                ServerPlayer caller = player();
                HeroEntity source = active(UUID.randomUUID());
                equip(source);
                HeroStateManager.backupToGlobal(source);
                Snapshot expected = snapshot(source);
                HeroEntity target = fresh(source.getOwnerUUID());
                HeroStateManager.restoreFromGlobal(target, caller);
                assertEquipment(target, expected);
                check(source.getOwnerUUID().equals(target.getOwnerUUID()), "bound owner must not change");
            });
            test("summoning near, far and across dimensions preserves all equipment", () -> {
                ServerPlayer owner = player();
                HeroEntity original = active(owner.getUUID());
                equip(original);
                Snapshot expected = snapshot(original);
                check(HeroSummonItem.performSummonOrTeleport(level, owner, new Vec3(3.5, 100, 3.5)), "near summon must succeed");
                assertEquipment(HeroSummonItem.findHeroInAnyDimension(server, owner.getUUID()), expected);
                loadDestination(level, 12, 0);
                check(HeroSummonItem.performSummonOrTeleport(level, owner, new Vec3(203.5, 100, 3.5)), "far summon must succeed");
                HeroEntity far = HeroSummonItem.findHeroInAnyDimension(server, owner.getUUID());
                check(far != null, "far summon must leave an accessible entity in its loaded chunk");
                fixtures.add(far);
                assertEquipment(far, expected);
                ServerLevel nether = server.getLevel(Level.NETHER);
                check(nether != null, "nether must exist for cross-dimension test");
                loadDestination(nether, 0, 0);
                check(HeroSummonItem.performSummonOrTeleport(nether, owner, new Vec3(0.5, 100, 0.5)), "dimension summon must succeed");
                HeroEntity moved = HeroSummonItem.findHeroInAnyDimension(server, owner.getUUID());
                fixtures.add(moved);
                assertEquipment(moved, expected);
                check(moved.level() == nether, "replacement must be in the requested dimension");
            });
            test("leaving the world and reappearing keeps wardrobe contents", () -> {
                ServerPlayer owner = player();
                HeroEntity source = active(owner.getUUID());
                equip(source);
                Snapshot expected = snapshot(source);
                HeroDimensionHandler.leaveWorld(source, null);
                check(source.isRemoved(), "leaving HB must be removed");
                HeroDimensionHandler.respawnNearPlayer(level, owner);
                HeroEntity restored = HeroSummonItem.findHeroInAnyDimension(server, owner.getUUID());
                fixtures.add(restored);
                assertEquipment(restored, expected);
            });
            test("player clone keeps an independent combat respawn snapshot", () -> {
                ServerPlayer originalPlayer = player();
                ServerPlayer newPlayer = player();
                HeroEntity source = active(originalPlayer.getUUID());
                equip(source);
                Snapshot expected = snapshot(source);
                String key = "HeroCombatRespawnData";
                HeroStateManager.backupToPlayerNBT(source, originalPlayer, key);
                CompoundTag originalSnapshot = originalPlayer.getPersistentData().getCompound(key).copy();
                PlayerLifecycleHandler.onPlayerClone(new PlayerEvent.Clone(newPlayer, originalPlayer, true));
                check(newPlayer.getPersistentData().contains(key), "combat snapshot must survive player clone");
                check(originalPlayer.getPersistentData().get(key) != newPlayer.getPersistentData().get(key), "clone snapshot must be independent");
                HeroEntity target = fresh(originalPlayer.getUUID());
                check(HeroStateManager.restoreFromPlayerNBT(target, newPlayer, key), "snapshot must restore");
                assertEquipment(target, expected);
                check(!newPlayer.getPersistentData().contains(key), "successful restore consumes new player's snapshot");
                check(originalSnapshot.equals(originalPlayer.getPersistentData().getCompound(key)), "restore must not mutate original snapshot");
                check(!HeroStateManager.restoreFromPlayerNBT(target, newPlayer, key), "snapshot must not be consumed twice");
            });
        } finally {
            JsonObject report = new JsonObject();
            report.addProperty("status", failures == 0 ? "passed" : "failed");
            report.addProperty("assertions", assertions);
            report.addProperty("failures", failures);
            report.addProperty("curios", ModList.get().isLoaded("curios"));
            report.addProperty("accessories", HeroAccessoriesCompat.isLoaded());
            report.add("cases", cases);
            try {
                Files.writeString(Path.of(System.getProperty("hero.equipmentProbe.report")), new GsonBuilder().setPrettyPrinting().create().toJson(report));
            } catch (Exception e) {
                throw new RuntimeException(e);
            } finally {
                System.out.println("HERO_EQUIPMENT_PROBE: " + report);
                server.halt(false);
            }
        }
    }

    private void test(String name, CheckedRunnable run) {
        JsonObject result = new JsonObject();
        result.addProperty("name", name);
        try {
            run.run();
            result.addProperty("status", "passed");
        } catch (Throwable failure) {
            failures++;
            result.addProperty("status", "failed");
            result.addProperty("error", failure.toString());
            failure.printStackTrace();
        } finally {
            for (HeroEntity hero : fixtures) if (hero != null) hero.discard();
            fixtures.clear();
            cases.add(result);
        }
    }

    private HeroWorldData worldData() { return HeroWorldData.get(level); }

    private ServerPlayer player() {
        ServerPlayer player = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "equipment_probe"));
        player.setPos(0.5, 100, 0.5);
        return player;
    }

    private HeroEntity fresh(UUID owner) {
        HeroEntity hero = ModEntities.HERO.get().create(level);
        if (hero == null) throw new AssertionError("HB entity factory returned null");
        hero.setOwnerUUID(owner);
        hero.setPos(0.5, 100, 0.5);
        hero.setNoAi(true);
        hero.setNoGravity(true);
        fixtures.add(hero);
        return hero;
    }

    private HeroEntity active(UUID owner) {
        HeroEntity hero = fresh(owner);
        activate(hero);
        return hero;
    }

    private void activate(HeroEntity hero) {
        check(level.addFreshEntity(hero), "fixture must join the server level");
        HeroBrain.ACTIVE_HEROES.add(hero);
        worldData().setActiveHeroUUID(hero.getOwnerUUID(), hero.getUUID());
        check(HeroLifecycleHandler.findActiveHero(level, hero.getOwnerUUID()) == hero, "canonical fixture must resolve");
    }

    private void equip(HeroEntity hero) {
        hero.setItemSlot(EquipmentSlot.FEET, named(Items.DIAMOND_BOOTS));
        hero.setItemSlot(EquipmentSlot.LEGS, named(Items.DIAMOND_LEGGINGS));
        hero.setItemSlot(EquipmentSlot.CHEST, named(Items.DIAMOND_CHESTPLATE));
        hero.setItemSlot(EquipmentSlot.HEAD, named(Items.DIAMOND_HELMET));
        ItemStack sword = named(Items.DIAMOND_SWORD);
        sword.enchant(hero.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT).getOrThrow(net.minecraft.world.item.enchantment.Enchantments.SHARPNESS), 3);
        hero.setItemSlot(EquipmentSlot.MAINHAND, sword);
        hero.setItemSlot(EquipmentSlot.OFFHAND, named(Items.SHIELD));
        if (ModList.get().isLoaded("curios")) {
            // Use an item accepted by the actual back-slot tag, including on legacy capability load.
            Item backItem = net.minecraft.core.registries.BuiltInRegistries.ITEM.getTag(
                    net.minecraft.tags.ItemTags.create(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("curios", "back")))
                    .orElseThrow().iterator().next().value();
            hero.setCuriosBackItemFromTag(itemTag(hero, named(backItem)));
            check(!hero.isCuriosBackSlotEmpty(), "Curios back fixture must contain an item");
        }
        if (HeroAccessoriesCompat.isLoaded()) AccessoriesFixture.equip(hero);
    }

    private static ItemStack named(Item item) {
        ItemStack stack = new ItemStack(item);
        stack.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, Component.literal("equipment regression: " + item));
        if (stack.isDamageableItem()) stack.setDamageValue(7);
        return stack;
    }

    private static CompoundTag itemTag(HeroEntity hero, ItemStack stack) {
        return (CompoundTag) stack.saveOptional(hero.registryAccess());
    }

    private void loadDestination(ServerLevel target, int chunkX, int chunkZ) {
        check(target.areEntitiesLoaded(net.minecraft.world.level.ChunkPos.asLong(chunkX, chunkZ)),
                "test destination's entities must be loaded before teleporting");
    }

    private static CompoundTag saved(HeroEntity hero) {
        CompoundTag tag = new CompoundTag();
        hero.saveWithoutId(tag);
        tag.remove("UUID");
        return tag;
    }

    private static Snapshot snapshot(HeroEntity hero) {
        return new Snapshot(hero.getArmorItemsTag().copy(), hero.getHandItemsTag().copy(),
                hero.getCuriosBackItemTag().copy(), hero.getAccessoriesDataTag().copy());
    }

    private void assertEquipment(HeroEntity hero, Snapshot expected) {
        check(hero != null, "restored HB must exist");
        check(expected.armor().equals(hero.getArmorItemsTag()), "armor or item metadata changed");
        check(expected.hands().equals(hero.getHandItemsTag()), "hands or item metadata changed");
        check(expected.curios().equals(hero.getCuriosBackItemTag()), "Curios back item changed");
        check(expected.accessories().equals(hero.getAccessoriesDataTag()), "accessories or cosmetics changed");
    }

    private void assertBackup(UUID owner, Snapshot expected) {
        check(expected.armor().equals(worldData().getArmorItems(owner)), "global armor backup changed");
        check(expected.hands().equals(worldData().getHandItems(owner)), "global hands backup changed");
        check(expected.curios().equals(worldData().getCuriosBackItem(owner)), "global Curios backup changed");
        check(expected.accessories().equals(worldData().getAccessoriesData(owner)), "global accessories backup changed");
    }

    private void check(boolean condition, String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }

    private record Snapshot(ListTag armor, ListTag hands, CompoundTag curios, CompoundTag accessories) {}
    private interface CheckedRunnable { void run() throws Exception; }

    private static final class AccessoriesFixture {
        static void equip(HeroEntity hero) {
            var cap = io.wispforest.accessories.api.AccessoriesCapability.getOptionally(hero).orElseThrow();
            var container = cap.getContainers().values().stream()
                    .filter(c -> c.getAccessories().getContainerSize() > 0).findFirst().orElseThrow();
            container.getAccessories().setItem(0, named(Items.DIAMOND));
            container.getCosmeticAccessories().setItem(0, named(Items.EMERALD));
        }
    }
}
