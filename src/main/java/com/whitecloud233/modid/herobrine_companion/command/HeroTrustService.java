package com.whitecloud233.modid.herobrine_companion.command;

import com.whitecloud233.modid.herobrine_companion.entity.HeroEntity;
import com.whitecloud233.modid.herobrine_companion.entity.logic.data.HeroWorldData;
import com.whitecloud233.modid.herobrine_companion.item.HeroSummonItem;
import net.minecraft.server.level.ServerPlayer;

/** Profile trust is authoritative, including while the companion is unloaded. */
public final class HeroTrustService {
    private HeroTrustService() {}

    public static int add(ServerPlayer player, int amount) {
        if (amount <= 0) throw new IllegalArgumentException("Trust increment must be positive");
        HeroWorldData data = HeroWorldData.get(player.serverLevel());
        int updated = (int) Math.min(Integer.MAX_VALUE, Math.max(0L, data.getTrust(player.getUUID())) + amount);
        data.setTrust(player.getUUID(), updated);
        HeroEntity hero = HeroSummonItem.findHeroInAnyDimension(player.server, player.getUUID());
        if (hero != null) hero.setTrustLevel(updated);
        return updated;
    }
}
