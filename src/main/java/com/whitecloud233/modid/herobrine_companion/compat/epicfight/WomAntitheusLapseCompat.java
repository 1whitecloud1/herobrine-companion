package com.whitecloud233.modid.herobrine_companion.compat.epicfight;

import yesman.epicfight.skill.SkillContainer;
import yesman.epicfight.skill.SkillDataKey;
import yesman.epicfight.skill.SkillDataManager;

/** A finishing animation can outlive the weapon skill that started it. */
public final class WomAntitheusLapseCompat {
    private WomAntitheusLapseCompat() { }

    public static <T> void setDataSyncIfOwned(SkillContainer owner, SkillDataManager target,
                                             SkillDataKey<T> key, T value) {
        // Skill slots survive a weapon change, but their data and ownership do not.
        // Shared keys such as ACTIVE must not reset a different weapon's skill.
        if (owner != null && owner.getDataManager() == target && target.hasData(key)) {
            target.setDataSync(key, value);
        }
    }
}
