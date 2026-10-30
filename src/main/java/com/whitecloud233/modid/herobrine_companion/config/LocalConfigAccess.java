package com.whitecloud233.modid.herobrine_companion.config;

import net.minecraftforge.common.ForgeConfigSpec;

/** 尚未迁移为服务器同步规则的本机选项保留原文件键和作用范围。 */
public final class LocalConfigAccess {
    private LocalConfigAccess() {
    }

    public static <T> void update(ForgeConfigSpec.ConfigValue<T> option, T value) {
        if (java.util.Objects.equals(option.get(), value)) return;
        option.set(value);
        Config.SPEC.save();
        Config.reloadLocalValues();
    }
}
