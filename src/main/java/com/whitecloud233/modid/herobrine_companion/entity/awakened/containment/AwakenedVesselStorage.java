package com.whitecloud233.modid.herobrine_companion.entity.awakened.containment;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.Optional;
import java.util.UUID;

public final class AwakenedVesselStorage {
    private static final String ROOT_TAG = "AwakenedVessel";
    private static final String VERSION_TAG = "Version";
    private static final String ENTITY_TYPE_TAG = "EntityType";
    private static final String ENTITY_DATA_TAG = "EntityData";
    private static final String CAPTURED_NAME_TAG = "CapturedName";
    private static final String CAPTURED_BY_TAG = "CapturedBy";
    private static final String CAPTURED_GAME_TIME_TAG = "CapturedGameTime";
    private static final String CAPTURED_DIMENSION_TAG = "CapturedDimension";
    private static final String IS_JEAN_TAG = "IsJean";
    private static final String FAMILY_MEMBER_ID_TAG = "FamilyMemberId";
    private static final int VERSION = 1;

    private AwakenedVesselStorage() {
    }

    public static boolean hasCapturedMob(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag != null && tag.contains(ROOT_TAG, Tag.TAG_COMPOUND);
    }

    public static Optional<CapturedAwakenedMob> read(ItemStack stack) {
        CompoundTag stackTag = stack.getTag();
        if (stackTag == null || !stackTag.contains(ROOT_TAG, Tag.TAG_COMPOUND)) {
            return Optional.empty();
        }

        CompoundTag root = stackTag.getCompound(ROOT_TAG);
        ResourceLocation entityTypeId = ResourceLocation.tryParse(root.getString(ENTITY_TYPE_TAG));
        if (entityTypeId == null || !root.contains(ENTITY_DATA_TAG, Tag.TAG_COMPOUND)) {
            return Optional.empty();
        }

        UUID capturedBy = root.hasUUID(CAPTURED_BY_TAG) ? root.getUUID(CAPTURED_BY_TAG) : null;
        ResourceLocation dimension = ResourceLocation.tryParse(root.getString(CAPTURED_DIMENSION_TAG));
        return Optional.of(new CapturedAwakenedMob(
                entityTypeId,
                root.getCompound(ENTITY_DATA_TAG),
                root.getString(CAPTURED_NAME_TAG),
                capturedBy,
                root.getLong(CAPTURED_GAME_TIME_TAG),
                dimension,
                root.getBoolean(IS_JEAN_TAG),
                root.getString(FAMILY_MEMBER_ID_TAG)
        ));
    }

    public static void write(ItemStack stack, CapturedAwakenedMob captured) {
        CompoundTag root = new CompoundTag();
        root.putInt(VERSION_TAG, VERSION);
        root.putString(ENTITY_TYPE_TAG, captured.entityTypeId().toString());
        root.put(ENTITY_DATA_TAG, captured.copyEntityData());
        root.putString(CAPTURED_NAME_TAG, captured.capturedName());
        if (captured.capturedBy() != null) {
            root.putUUID(CAPTURED_BY_TAG, captured.capturedBy());
        }
        root.putLong(CAPTURED_GAME_TIME_TAG, captured.capturedGameTime());
        if (captured.capturedDimension() != null) {
            root.putString(CAPTURED_DIMENSION_TAG, captured.capturedDimension().toString());
        }
        root.putBoolean(IS_JEAN_TAG, captured.jean());
        root.putString(FAMILY_MEMBER_ID_TAG, captured.familyMemberId());

        stack.getOrCreateTag().put(ROOT_TAG, root);
    }

    public static void clear(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag == null) {
            return;
        }
        tag.remove(ROOT_TAG);
        if (tag.isEmpty()) {
            stack.setTag(null);
        }
    }

    public static Component capturedName(ItemStack stack) {
        return read(stack)
                .map(captured -> captured.capturedName().isBlank()
                        ? Component.literal(captured.entityTypeId().toString())
                        : Component.literal(captured.capturedName()))
                .orElseGet(() -> Component.translatable("item.herobrine_companion.awakened_vessel.empty"));
    }
}
