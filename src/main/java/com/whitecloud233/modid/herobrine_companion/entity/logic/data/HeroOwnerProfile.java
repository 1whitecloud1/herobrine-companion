package com.whitecloud233.modid.herobrine_companion.entity.logic.data;

import com.mojang.authlib.GameProfile;
import com.whitecloud233.modid.herobrine_companion.entity.HeroEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;

import javax.annotation.Nullable;
import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class HeroOwnerProfile {
    private final HeroEntity hero;
    private final Set<Integer> claimedRewards = new HashSet<>();
    private long claimedRewardsRevision;

    public HeroOwnerProfile(HeroEntity hero) {
        this.hero = hero;
    }

    public void setTrustLevel(int level) {
        hero.getEntityData().set(HeroEntity.TRUST_LEVEL, level);
        hero.markAppearanceDirty();
        if (!hero.level().isClientSide) HeroDataHandler.updateGlobalTrust(hero);
    }

    public void setSkinVariant(int variant) {
        hero.getEntityData().set(HeroEntity.SKIN_VARIANT, variant);
        hero.markAppearanceDirty();
        if (hero.level() instanceof ServerLevel serverLevel) {
            UUID owner = hero.getOwnerUUID();
            if (owner != null) HeroWorldData.get(serverLevel).setSkinVariant(owner, variant);
        }
    }

    public void setCustomSkinName(String name) {
        hero.getEntityData().set(HeroEntity.CUSTOM_SKIN_NAME, name);
        hero.markAppearanceDirty();
        if (hero.level() instanceof ServerLevel serverLevel) {
            UUID owner = hero.getOwnerUUID();
            if (owner != null) HeroWorldData.get(serverLevel).setCustomSkinName(owner, name);
        }
    }

    public void setOwnerUUID(@Nullable UUID uuid) {
        UUID previous = hero.getOwnerUUID();
        hero.getEntityData().set(HeroEntity.OWNER_UUID, Optional.ofNullable(uuid));
        if (hero.level() != null && !hero.level().isClientSide
                && !Objects.equals(previous, uuid) && previous != null) {
            // Agent 按缓存的旧 owner UUID 落盘，再清理排队请求。
            hero.getHeroAgent().flushMemoryOnDetach(hero);
            hero.getHeroAgent().onDetached();
        }
    }

    public Component getOwnerDisplayNameComponent() {
        UUID owner = hero.getOwnerUUID();
        if (owner == null) {
            return Component.translatable("message.herobrine_companion.hero_owner_unknown");
        }
        Player onlineOwner = hero.level().getPlayerByUUID(owner);
        if (onlineOwner != null) {
            return Component.literal(onlineOwner.getGameProfile().getName());
        }
        if (hero.level().getServer() != null) {
            Optional<GameProfile> cached = hero.level().getServer().getProfileCache().get(owner);
            if (cached.isPresent() && cached.get().getName() != null && !cached.get().getName().isEmpty()) {
                return Component.literal(cached.get().getName());
            }
        }
        return Component.translatable("message.herobrine_companion.hero_owner_unknown");
    }

    public Component createNotYourHeroMessage() {
        return Component.translatable("message.herobrine_companion.not_your_hero")
                .append(Component.literal(" "))
                .append(Component.translatable("message.herobrine_companion.hero_owner_hint", getOwnerDisplayNameComponent()))
                .withStyle(ChatFormatting.RED);
    }

    public boolean hasClaimedReward(int id) {
        UUID owner = hero.getOwnerUUID();
        if (hero.level() instanceof ServerLevel serverLevel && owner != null) {
            return HeroWorldData.get(serverLevel).isRewardClaimed(owner, id);
        }
        return claimedRewards.contains(id);
    }

    public void claimReward(int id) {
        UUID owner = hero.getOwnerUUID();
        if (hero.level() instanceof ServerLevel serverLevel && owner != null) {
            HeroWorldData.get(serverLevel).setRewardClaimed(owner, id, true);
        }
        claimedRewards.add(id);
    }

    /** A server snapshot replaces the client mirror, including removed claims. */
    public void applyClaimedRewardsSnapshot(Set<Integer> rewards) {
        Set<Integer> snapshot = Set.copyOf(rewards);
        claimedRewards.clear();
        claimedRewards.addAll(snapshot);
        claimedRewardsRevision++;
    }

    public long getClaimedRewardsRevision() {
        return claimedRewardsRevision;
    }

    public Set<Integer> getClaimedRewards() {
        UUID owner = hero.getOwnerUUID();
        if (hero.level() instanceof ServerLevel serverLevel && owner != null) {
            return HeroWorldData.get(serverLevel).getClaimedRewards(owner);
        }
        return Set.copyOf(claimedRewards);
    }
}
