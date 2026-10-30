package com.whitecloud233.herobrine_companion.compat.accessories;

import com.sun.source.tree.MethodTree;
import com.whitecloud233.herobrine_companion.regression.SourceFixturesChecks;

import java.nio.file.Files;
import java.nio.file.Path;

/** Exercises the production rebinding methods without starting a Minecraft client. */
public final class AccessoriesEquipmentChecks {
    public static void main(String[] args) throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/whitecloud233/herobrine_companion/compat/"
                + "accessories/HeroAccessoriesMenuCompat.java"));
        String methods = SourceFixturesChecks.members(source,
                member -> member instanceof MethodTree method && method.getReturnType() != null);
        try (var fixture = SourceFixturesChecks.compile("AccessoriesEquipmentFixture",
                "import java.util.*; public class AccessoriesEquipmentFixture {" + HARNESS + methods + "}")) {
            int checks = (int) fixture.type().getMethod("run").invoke(null);
            System.out.println("AccessoriesEquipmentChecks: " + checks + " checks passed");
        }
    }

    private static final String HARNESS = """
        enum EquipmentSlot { MAINHAND, OFFHAND, FEET, LEGS, CHEST, HEAD }
        record ResourceLocation(String name) {}
        record Pair<A, B>(A first, B second) {}
        static class ItemStack {
            final String name;
            final EquipmentSlot fits;
            final boolean locked;
            ItemStack(String name, EquipmentSlot fits, boolean locked) {
                this.name = name; this.fits = fits; this.locked = locked;
            }
            boolean canEquip(EquipmentSlot slot, HeroEntity hero) { return fits == slot; }
        }
        static class Container {
            final Map<Integer, ItemStack> items = new HashMap<>();
            ItemStack getItem(int index) { return items.get(index); }
            void setItem(int index, ItemStack item) { items.put(index, item); }
        }
        static class Inventory extends Container {}
        static class Level { boolean isClientSide; }
        static class HeroEntity {
            final Map<EquipmentSlot, ItemStack> items = new EnumMap<>(EquipmentSlot.class);
            final Level level = new Level();
            int dirty;
            Level level() { return level; }
            void markEquipmentDirty() { dirty++; }
        }
        static class EquipmentSlotBasedContainer {
            static Container of(EquipmentSlot slot, HeroEntity hero) {
                return new Container() {
                    ItemStack getItem(int index) { return hero.items.get(slot); }
                    void setItem(int index, ItemStack item) { hero.items.put(slot, item); }
                };
            }
        }
        static class Player {
            final boolean creative;
            Player(boolean creative) { this.creative = creative; }
            boolean isCreative() { return creative; }
        }
        static class Slot {
            final Container container;
            final int containerSlot, x, y;
            int index;
            Slot(Container container, int containerSlot, int x, int y) {
                this.container = container; this.containerSlot = containerSlot; this.x = x; this.y = y;
            }
            int getContainerSlot() { return containerSlot; }
            ItemStack getItem() { return container.getItem(containerSlot); }
            void set(ItemStack item) { container.setItem(containerSlot, item); setChanged(); }
            void setChanged() {}
            int getMaxStackSize() { return 64; }
            boolean mayPlace(ItemStack item) { return true; }
            boolean mayPickup(Player player) { return true; }
            Pair<ResourceLocation, ResourceLocation> getNoItemIcon() { return null; }
        }
        static class AbstractContainerMenu { final List<Slot> slots = new ArrayList<>(); }
        static class EnchantmentEffectComponents { static final Object PREVENT_ARMOR_CHANGE = new Object(); }
        static class EnchantmentHelper {
            static boolean has(ItemStack item, Object component) { return item != null && item.locked; }
        }
        static class HeroStateManager {
            static int backups;
            static void backupToGlobal(HeroEntity hero) { backups++; }
        }
        record SyncHeroCosmeticsPacket(HeroEntity hero) {}
        static class PacketHandler {
            static int syncs;
            static void sendToTracking(SyncHeroCosmeticsPacket packet, HeroEntity hero) { syncs++; }
        }
        static int checks;
        static void check(boolean condition, String message) {
            if (!condition) throw new AssertionError(message);
            checks++;
        }
        public static int run() {
            Inventory playerInventory = new Inventory();
            HeroEntity hero = new HeroEntity();
            AbstractContainerMenu menu = new AbstractContainerMenu();
            for (int i = 0; i <= 40; i++) {
                Slot slot = new Slot(playerInventory, i, 7 + i, 14 + i);
                slot.index = i;
                menu.slots.add(slot);
                playerInventory.setItem(i, new ItemStack("player-" + i, null, false));
            }
            Slot foreign = new Slot(new Inventory(), 39, 100, 100);
            menu.slots.add(foreign);
            List<Slot> originals = List.copyOf(menu.slots);
            Map<Integer, EquipmentSlot> equipment = Map.of(36, EquipmentSlot.FEET, 37, EquipmentSlot.LEGS,
                    38, EquipmentSlot.CHEST, 39, EquipmentSlot.HEAD, 40, EquipmentSlot.OFFHAND);
            equipment.values().forEach(slot -> hero.items.put(slot, new ItemStack("hero-" + slot, slot, false)));
            bindEquipmentSlots(menu, playerInventory, hero);
            for (int i = 0; i < 36; i++) {
                check(menu.slots.get(i) == originals.get(i), "Player storage/hotbar replaced at " + i);
            }
            check(menu.slots.get(41) == foreign, "Unrelated inventory was rebound");
            for (var entry : equipment.entrySet()) {
                int index = entry.getKey();
                Slot slot = menu.slots.get(index);
                check(slot.getItem() == hero.items.get(entry.getValue()), "Displayed someone else's equipment");
                check(slot.index == index && slot.x == originals.get(index).x && slot.y == originals.get(index).y,
                        "Changed network slot index or layout");
                check(slot.getMaxStackSize() == (index == 40 ? 64 : 1), "Invalid equipment capacity");
            }
            Slot helmet = menu.slots.get(39), offhand = menu.slots.get(40);
            ItemStack newHelmet = new ItemStack("new-helmet", EquipmentSlot.HEAD, true);
            ItemStack bread = new ItemStack("bread", null, false);
            check(helmet.mayPlace(newHelmet) && !helmet.mayPlace(bread), "Armor validation lost");
            check(offhand.mayPlace(bread), "Offhand rejects ordinary items");
            helmet.set(newHelmet);
            offhand.set(bread);
            check(hero.items.get(EquipmentSlot.HEAD) == newHelmet, "Helmet write missed Herobrine");
            check(hero.items.get(EquipmentSlot.OFFHAND) == bread, "Offhand write missed Herobrine");
            check(playerInventory.getItem(39).name.equals("player-39")
                    && playerInventory.getItem(40).name.equals("player-40"), "Modified player's equipment");
            check(!helmet.mayPickup(new Player(false)) && helmet.mayPickup(new Player(true)),
                    "Armor-change enchantment restriction lost");
            check(offhand.mayPickup(new Player(false)), "Armor restriction applied to offhand");
            check(hero.dirty == 2 && HeroStateManager.backups == 2 && PacketHandler.syncs == 2,
                    "Server equipment edits must mark, save and sync");
            hero.level.isClientSide = true;
            helmet.set(newHelmet);
            check(hero.dirty == 3 && HeroStateManager.backups == 2 && PacketHandler.syncs == 2,
                    "Client equipment sync must not perform server persistence");
            bindEquipmentSlots(menu, playerInventory, hero);
            check(menu.slots.get(39) == helmet && menu.slots.get(40) == offhand, "Rebinding is not idempotent");
            return checks;
        }
        """;
}
