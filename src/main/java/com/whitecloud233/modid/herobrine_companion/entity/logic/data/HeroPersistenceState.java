package com.whitecloud233.modid.herobrine_companion.entity.logic.data;

/** 运行期备份状态，不进入 NBT；未收到变更通知的第三方装备每五秒兜底采样一次。 */
public final class HeroPersistenceState {
    public static final int EQUIPMENT_RECONCILE_INTERVAL = 100;

    private boolean equipmentDirty = true;
    private boolean appearanceDirty = true;
    private boolean equipmentRestored;
    private int lastEquipmentBackupTick;
    private boolean hasEquipmentBackup;

    public void markEquipmentDirty() { equipmentDirty = true; }
    public void markAppearanceDirty() { appearanceDirty = true; }
    public boolean isAppearanceDirty() { return appearanceDirty; }
    public boolean isEquipmentRestored() { return equipmentRestored; }
    public void markEquipmentRestored() { equipmentRestored = true; }

    public boolean needsEquipmentBackup(int tick) {
        return equipmentDirty || !hasEquipmentBackup
                || tick - lastEquipmentBackupTick >= EQUIPMENT_RECONCILE_INTERVAL;
    }

    public void equipmentSaved(int tick) {
        equipmentDirty = false;
        lastEquipmentBackupTick = tick;
        hasEquipmentBackup = true;
    }

    public void appearanceSaved() { appearanceDirty = false; }

    public void loaded(boolean completeEquipment) {
        equipmentRestored = completeEquipment;
        equipmentDirty = true;
        appearanceDirty = true;
        hasEquipmentBackup = false;
    }
}
