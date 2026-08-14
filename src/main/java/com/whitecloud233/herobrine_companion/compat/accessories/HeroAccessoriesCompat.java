package com.whitecloud233.herobrine_companion.compat.accessories;

import com.whitecloud233.herobrine_companion.client.render.HeroRenderer;
import com.whitecloud233.herobrine_companion.entity.HeroEntity;
import com.whitecloud233.herobrine_companion.entity.logic.data.HeroStateManager;
import com.whitecloud233.herobrine_companion.network.PacketHandler;
import com.whitecloud233.herobrine_companion.network.SyncHeroCosmeticsPacket;
import com.whitecloud233.herobrine_companion.util.AiItemNaming;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

public class HeroAccessoriesCompat {

    private static final String[] DEFAULT_SLOT_ORDER = new String[] {
            "hat",
            "face",
            "necklace",
            "cape",
            "back",
            "hand",
            "ring",
            "wrist",
            "belt",
            "anklet",
            "shoes",
            "charm"
    };

    public static boolean isLoaded() {
        return ModList.get().isLoaded("accessories");
    }


    public static int addSlots(Consumer<Slot> slotConsumer, HeroEntity hero, int startX, int startY, int columns) {
        if (!isLoaded() || slotConsumer == null || hero == null || columns <= 0) {
            return 0;
        }
        return AccessoriesSafeInvoker.addSlots(slotConsumer, hero, startX, startY, columns);
    }

    public static CompoundTag getAccessoriesDataTag(HeroEntity hero) {
        if (!isLoaded() || hero == null) {
            return new CompoundTag();
        }
        return AccessoriesSafeInvoker.getAccessoriesDataTag(hero);
    }

    public static void setAccessoriesDataFromTag(HeroEntity hero, CompoundTag tag) {
        if (!isLoaded() || hero == null) {
            return;
        }
        AccessoriesSafeInvoker.setAccessoriesDataFromTag(hero, tag);
    }

    /**
     * 把当前佩戴的饰品整理成 AI 可读的 "槽位=物品, ..." 字符串（未佩戴任何饰品返回 ""）。
     * 供 Omniscient Eye / agent 工具做<b>装备感知</b>，外部依赖隔离在本类内部。
     */
    public static String describeEquippedItems(HeroEntity hero) {
        if (!isLoaded() || hero == null) {
            return "";
        }
        return AccessoriesSafeInvoker.describeEquippedItems(hero);
    }

    private static class AccessoriesSafeInvoker {

        static int addSlots(Consumer<Slot> slotConsumer, HeroEntity hero, int startX, int startY, int columns) {
            var capability = io.wispforest.accessories.api.AccessoriesCapability.getOptionally(hero).orElse(null);
            if (capability == null) {
                return 0;
            }

            Map<String, io.wispforest.accessories.api.slot.SlotType> entitySlots =
                    io.wispforest.accessories.data.EntitySlotLoader.getEntitySlots(hero);
            if (entitySlots.isEmpty()) {
                return 0;
            }

            Set<String> orderedNames = new LinkedHashSet<>();
            for (String slotName : DEFAULT_SLOT_ORDER) {
                if (entitySlots.containsKey(slotName)) {
                    orderedNames.add(slotName);
                }
            }

            entitySlots.values().stream()
                    .sorted(Comparator.comparingInt(io.wispforest.accessories.api.slot.SlotType::order)
                            .thenComparing(io.wispforest.accessories.api.slot.SlotType::name))
                    .map(io.wispforest.accessories.api.slot.SlotType::name)
                    .forEach(orderedNames::add);

            int count = 0;
            for (String slotName : orderedNames) {
                io.wispforest.accessories.api.slot.SlotType slotType = entitySlots.get(slotName);
                if (slotType == null) {
                    continue;
                }

                io.wispforest.accessories.api.AccessoriesContainer container = capability.getContainer(slotType);
                if (container == null) {
                    continue;
                }

                int slotCount = Math.max(slotType.amount(), container.getAccessories().getContainerSize());
                for (int slotIndex = 0; slotIndex < slotCount; slotIndex++) {
                    if (slotIndex >= container.getAccessories().getContainerSize()) {
                        break;
                    }

                    int x = startX + (count % columns) * 18;
                    int y = startY + (count / columns) * 18;
                    slotConsumer.accept(new ManagedAccessorySlot(hero, container, slotIndex, x, y));
                    count++;
                }
            }

            return count;
        }

        static CompoundTag getAccessoriesDataTag(HeroEntity hero) {
            CompoundTag root = new CompoundTag();
            CompoundTag containersTag = new CompoundTag();
            var capability = io.wispforest.accessories.api.AccessoriesCapability.getOptionally(hero).orElse(null);
            if (capability == null) {
                return root;
            }

            for (var entry : capability.getContainers().entrySet()) {
                var container = entry.getValue();
                CompoundTag containerTag = new CompoundTag();
                containerTag.put("Items", container.getAccessories().createTag(hero.registryAccess()));
                containerTag.put("Cosmetics", container.getCosmeticAccessories().createTag(hero.registryAccess()));

                byte[] renderOptions = new byte[container.renderOptions().size()];
                for (int i = 0; i < container.renderOptions().size(); i++) {
                    Boolean value = container.renderOptions().get(i);
                    renderOptions[i] = (byte) ((value != null && value) ? 1 : 0);
                }
                containerTag.putByteArray("RenderOptions", renderOptions);
                containersTag.put(entry.getKey(), containerTag);
            }

            root.put("Containers", containersTag);
            return root;
        }

        static void setAccessoriesDataFromTag(HeroEntity hero, CompoundTag tag) {
            var capability = io.wispforest.accessories.api.AccessoriesCapability.getOptionally(hero).orElse(null);
            if (capability == null) {
                return;
            }

            CompoundTag containersTag = tag != null && tag.contains("Containers", Tag.TAG_COMPOUND)
                    ? tag.getCompound("Containers")
                    : new CompoundTag();

            for (var entry : capability.getContainers().entrySet()) {
                var container = entry.getValue();
                CompoundTag containerTag = containersTag.contains(entry.getKey(), Tag.TAG_COMPOUND)
                        ? containersTag.getCompound(entry.getKey())
                        : new CompoundTag();

                ListTag itemTag = containerTag.contains("Items", Tag.TAG_LIST)
                        ? containerTag.getList("Items", Tag.TAG_COMPOUND)
                        : new ListTag();
                ListTag cosmeticTag = containerTag.contains("Cosmetics", Tag.TAG_LIST)
                        ? containerTag.getList("Cosmetics", Tag.TAG_COMPOUND)
                        : new ListTag();
                byte[] renderOptions = containerTag.contains("RenderOptions", Tag.TAG_BYTE_ARRAY)
                        ? containerTag.getByteArray("RenderOptions")
                        : new byte[0];

                container.getAccessories().fromTag(itemTag, hero.registryAccess());
                container.getCosmeticAccessories().fromTag(cosmeticTag, hero.registryAccess());

                var renderOptionList = container.renderOptions();
                renderOptionList.clear();
                int renderSize = Math.max(container.getSize(), renderOptions.length);
                for (int i = 0; i < renderSize; i++) {
                    boolean shouldRender = i >= renderOptions.length || renderOptions[i] != 0;
                    renderOptionList.add(shouldRender);
                }
            }
        }

        static String describeEquippedItems(HeroEntity hero) {
            var capability = io.wispforest.accessories.api.AccessoriesCapability.getOptionally(hero).orElse(null);
            if (capability == null) {
                return "";
            }

            List<String> parts = new ArrayList<>();
            for (var entry : capability.getContainers().entrySet()) {
                var container = entry.getValue();
                var stacks = container.getAccessories();
                String slotName = entry.getKey();
                for (int i = 0; i < stacks.getContainerSize(); i++) {
                    ItemStack stack = stacks.getItem(i);
                    if (!stack.isEmpty()) {
                        parts.add(slotName + "=" + AiItemNaming.describe(stack));
                    }
                }
            }
            return String.join(", ", parts);
        }

        private static class ManagedAccessorySlot extends io.wispforest.accessories.api.menu.AccessoriesBasedSlot {

            private final HeroEntity hero;
            private final io.wispforest.accessories.api.AccessoriesContainer container;

            ManagedAccessorySlot(HeroEntity hero, io.wispforest.accessories.api.AccessoriesContainer container, int slotIndex, int x, int y) {
                super(container, container.getAccessories(), slotIndex, x, y);
                this.hero = hero;
                this.container = container;
            }

            @Override
            public boolean mayPickup(Player player) {
                return io.wispforest.accessories.api.AccessoriesAPI.canUnequip(
                        this.getItem(),
                        io.wispforest.accessories.api.slot.SlotReference.of(this.hero, this.container.getSlotName(), this.getContainerSlot())
                );
            }

            @Override
            public void setChanged() {
                super.setChanged();
                this.hero.isStateDirty = true;
                if (!this.hero.level().isClientSide) {
                    HeroStateManager.backupToGlobal(this.hero);
                    PacketHandler.sendToTracking(new SyncHeroCosmeticsPacket(this.hero), this.hero);
                }
            }
        }
    }
}
