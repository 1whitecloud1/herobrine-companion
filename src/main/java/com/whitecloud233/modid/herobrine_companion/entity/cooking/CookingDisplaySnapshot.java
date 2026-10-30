package com.whitecloud233.modid.herobrine_companion.entity.cooking;

import net.minecraft.nbt.CompoundTag;

/** 后端负责解释自己的快照；这里仅管理后端标识与快照的持久化边界。 */
public record CookingDisplaySnapshot(String backendId, CompoundTag data) {
    public static final String BACKEND_KEY = "HeroAutoCookBackend";
    public static final String SNAPSHOT_KEY = "HeroAutoCookBackendSnapshot";

    public CookingDisplaySnapshot {
        backendId = backendId == null ? "" : backendId;
        data = data == null ? new CompoundTag() : data.copy();
    }

    @Override public CompoundTag data() { return data.copy(); }

    public void write(CompoundTag target) {
        target.putString(BACKEND_KEY, backendId);
        if (data.isEmpty()) target.remove(SNAPSHOT_KEY);
        else target.put(SNAPSHOT_KEY, data.copy());
    }

    public static CookingDisplaySnapshot read(CompoundTag source) {
        return new CookingDisplaySnapshot(source.getString(BACKEND_KEY), source.getCompound(SNAPSHOT_KEY));
    }

    public static void clear(CompoundTag source) {
        source.remove(BACKEND_KEY);
        source.remove(SNAPSHOT_KEY);
    }
}
