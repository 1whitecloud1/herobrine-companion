package com.whitecloud233.modid.herobrine_companion.entity.logic;

import com.whitecloud233.modid.herobrine_companion.entity.HeroEntity;
import static com.whitecloud233.modid.herobrine_companion.entity.HeroEntity.*;
import com.whitecloud233.modid.herobrine_companion.fight.HeroAfterimage;
import com.whitecloud233.modid.herobrine_companion.network.ChallengeAfterimagePacket;
import com.whitecloud233.modid.herobrine_companion.network.PacketHandler;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;

/** Owns the challenge boss bar and expiring afterimages, outside the entity lifecycle shell. */
public final class HeroChallengePresentation {
    private final List<HeroAfterimage> afterimages = new ArrayList<>();
    private final ServerBossEvent bossBar = new ServerBossEvent(Component.translatable("entity.herobrine_companion.hero"),
            BossEvent.BossBarColor.WHITE, BossEvent.BossBarOverlay.PROGRESS);

    public HeroChallengePresentation() { bossBar.setVisible(false); }
    public void tickAfterimages() { afterimages.removeIf(HeroAfterimage::tick); }
    public void tickBossBar(HeroEntity hero) {
        boolean active = hero.getEntityData().get(IS_CHALLENGE_ACTIVE);
        if (active) bossBar.setProgress(hero.getHealth() / hero.getMaxHealth());
        if (bossBar.isVisible() != active) bossBar.setVisible(active);
    }
    public void startTracking(ServerPlayer player) { bossBar.addPlayer(player); }
    public void stopTracking(ServerPlayer player) { bossBar.removePlayer(player); }
    public void addAfterimage(HeroEntity hero, HeroAfterimage afterimage) {
        if (!hero.level().isClientSide) PacketHandler.sendToTracking(new ChallengeAfterimagePacket(hero.getId(), afterimage), hero);
        afterimages.add(afterimage);
    }
    public List<HeroAfterimage> afterimages() { return afterimages; }
    public void clearAfterimages() { afterimages.clear(); }
}
