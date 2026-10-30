package com.whitecloud233.modid.herobrine_companion.compat;

import com.whitecloud233.modid.herobrine_companion.api.compat.OptionalCompat;
import com.whitecloud233.modid.herobrine_companion.api.compat.HeroAWCompat;
import com.whitecloud233.modid.herobrine_companion.api.compat.HeroAccessoriesCompat;
import com.whitecloud233.modid.herobrine_companion.api.compat.HeroEpicFightCompat;
import net.minecraft.nbt.CompoundTag;

/** Executes common-side fallback APIs on a classpath without the add-on or target mods. */
public final class CompatAbsentChecks {
    public static void main(String[] args) throws Exception {
        for (String absent : new String[]{
                "com.whitecloud233.modid.herobrine_companion_compat.HerobrineCompanionCompat",
                "yesman.epicfight.main.EpicFightMod", "io.wispforest.accessories.api.AccessoriesCapability",
                "fonnymunkey.simplehats.client.hat.HatLayer", "dev.tr7zw.waveycapes.WaveyCapesBase",
                "moe.plushie.armourers_workshop.core.menu.SkinWardrobeMenu"}) {
            try {
                Class.forName(absent, false, CompatAbsentChecks.class.getClassLoader());
                throw new AssertionError("Fallback verification classpath contains " + absent);
            } catch (ClassNotFoundException expected) { }
        }

        check(OptionalCompat.isInstalled(), "Compatibility implementations are bundled in the main mod");
        check(!HeroEpicFightCompat.isLoaded(), "Epic Fight integration is inactive");
        check(HeroEpicFightCompat.getBridgeStatus() == HeroEpicFightCompat.BridgeStatus.NOT_INSTALLED,
                "Bridge reports its missing installation");
        check(!HeroEpicFightCompat.isRuntimeBridgeReady(), "Native combat retains control");
        check(!HeroEpicFightCompat.shouldUseEpicFightCombatAI(null), "Native combat AI remains available");
        check(!HeroEpicFightCompat.shouldUseEpicFightPose(null), "Native poses remain available");
        check(!HeroEpicFightCompat.shouldUseEpicFightHeldItemLayer(null), "Native held item rendering remains available");
        check(!HeroEpicFightCompat.canUsePoemGestures(null), "No external gesture engine is queried");
        check(HeroEpicFightCompat.triggerSkill(null, "test") == null, "Missing skill backend returns unavailable");
        HeroEpicFightCompat.queuePoemGesture(null, 0, 0, 0);
        HeroEpicFightCompat.onPoemModeChanged(null);

        check(!HeroAccessoriesCompat.isLoaded(), "Accessories integration is inactive");
        check(HeroAccessoriesCompat.addSlots(slot -> { throw new AssertionError("Unexpected slot"); }, null, 0, 0, 1) == 0,
                "No accessory slots are added");
        check(HeroAccessoriesCompat.getAccessoriesDataTag(null).isEmpty(), "No capability is touched");
        check(HeroAccessoriesCompat.describeEquippedItems(null).isEmpty(), "No external equipment description");
        HeroAccessoriesCompat.setAccessoriesDataFromTag(null, new CompoundTag());
        HeroAccessoriesCompat.copyMissingItems(null, null);
        HeroAccessoriesCompat.loadMissingItemsFromTag(null, new CompoundTag());
        check(!HeroAWCompat.isLoaded(), "Armourer's Workshop integration is inactive");
        check(!HeroAWCompat.isAwItem(null), "AW item checks are safe without the add-on");
        check(!HeroAWCompat.openWardrobeFor(null), "No external wardrobe request is sent");
        check(com.whitecloud233.modid.herobrine_companion.combat.poem.StandalonePoemController.standalone(),
                "Standalone combat owns the pose without the add-on");
        check(com.whitecloud233.modid.herobrine_companion.entity.visual.HeroScytheComboLibrary.available(),
                "Shared fallback animation clips remain in the base mod");
        System.out.println("COMPAT_ABSENT_OK");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
