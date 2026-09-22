package com.whitecloud233.herobrine_companion.compat.epicfight;

import yesman.epicfight.world.capabilities.item.Style;

/** Public so Epic Fight can initialize this style through its enum registry. */
public enum PoemScytheStyle implements Style {
    POEM_TWO_HAND,
    POEM_NORMAL,
    POEM_REALM_BREAKER,
    POEM_THUNDER,
    POEM_VOID_SHATTER;

    private final int id = Style.ENUM_MANAGER.assign(this);

    @Override
    public int universalOrdinal() {
        return id;
    }

    @Override
    public boolean canUseOffhand() {
        return false;
    }
}
