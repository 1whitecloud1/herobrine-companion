package com.whitecloud233.herobrine_companion.entity.logic.data;

public final class HeroPersistenceChecks {
    private static int checks;

    public static void main(String[] args) {
        HeroPersistenceState state = new HeroPersistenceState();
        check(!state.isEquipmentRestored(), "Fresh entity still needs fallback restoration");
        check(state.needsEquipmentBackup(20), "Fresh entity must be backed up");
        check(state.isAppearanceDirty(), "Fresh appearance must be backed up");
        state.markEquipmentRestored();
        state.equipmentSaved(20);
        state.appearanceSaved();
        for (int tick : new int[]{20, 40, 60, 80, 100, 119}) {
            check(!state.needsEquipmentBackup(tick), "Unchanged equipment must not serialize at tick " + tick);
        }
        check(state.needsEquipmentBackup(120), "Direct third-party mutations need bounded fallback sampling");
        check(!state.isAppearanceDirty(), "Equipment polling must not dirty appearance");
        state.markAppearanceDirty();
        check(!state.needsEquipmentBackup(40), "Appearance updates must not serialize equipment");
        state.markEquipmentDirty();
        check(state.needsEquipmentBackup(40), "Slot changes must be visible before fallback deadline");
        check(state.needsEquipmentBackup(41), "Skipped backups must not clear dirtiness");
        state.equipmentSaved(41);
        check(state.isAppearanceDirty(), "Equipment success must not clear unsaved appearance");
        check(state.isEquipmentRestored(), "Equipment changes, including empty slots, must not re-enable fallback restore");
        state.loaded(true);
        check(state.isEquipmentRestored(), "Complete NBT owns empty equipment slots");
        check(state.needsEquipmentBackup(0), "NBT load invalidates previous backup cache");
        state.loaded(false);
        check(!state.isEquipmentRestored(), "Incomplete legacy NBT may use one-time fallback restore");
        System.out.println("HeroPersistenceChecks: " + checks + " checks passed");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        checks++;
    }
}
