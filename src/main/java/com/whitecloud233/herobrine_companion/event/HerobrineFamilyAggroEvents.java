package com.whitecloud233.herobrine_companion.event;

import com.whitecloud233.herobrine_companion.HerobrineCompanion;
import com.whitecloud233.herobrine_companion.entity.HeroEntity;
import com.whitecloud233.herobrine_companion.entity.family.HerobrineFamilyMembers;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.WitherSkull;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

import java.util.UUID;

@EventBusSubscriber(modid = HerobrineCompanion.MODID)
public final class HerobrineFamilyAggroEvents {
    private static final String RETALIATION_TARGET_TAG = "HerobrineFamilyRetaliationTarget";
    private static final String RETALIATION_UNTIL_TAG = "HerobrineFamilyRetaliationUntil";
    private static final long RETALIATION_WINDOW_TICKS = 20L * 45L;

    private HerobrineFamilyAggroEvents() {
    }

    @SubscribeEvent
    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()
                || !(event.getEntity() instanceof WitherSkull skull)
                || !(skull.getOwner() instanceof WitherBoss wither)
                || !HerobrineFamilyMembers.isFamilyMember(wither)) {
            return;
        }

        if (!hasActiveRetaliationTarget(wither)) {
            event.setCanceled(true);
            skull.discard();
            clearInvalidWitherHeadTargets(wither);
        }
    }

    @SubscribeEvent
    public static void onLivingChangeTarget(LivingChangeTargetEvent event) {
        if (!(event.getEntity() instanceof Mob mob)
                || !HerobrineFamilyMembers.isFamilyMember(mob)) {
            return;
        }

        LivingEntity newTarget = event.getNewAboutToBeSetTarget();
        if (newTarget != null && !isAllowedFamilyTarget(mob, newTarget)) {
            event.setCanceled(true);
            clearFamilyTarget(mob, newTarget);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLivingIncomingDamage(LivingIncomingDamageEvent event) {
        if (event.getEntity().level().isClientSide) {
            return;
        }

        LivingEntity attacker = getLivingAttacker(event.getSource());
        if (event.getEntity() instanceof Mob familyMember
                && HerobrineFamilyMembers.isFamilyMember(familyMember)
                && attacker != null
                && shouldAllowRetaliationAgainst(attacker)) {
            markRetaliationTarget(familyMember, attacker);
            familyMember.setTarget(attacker);
            return;
        }

        Mob familyAttacker = getFamilyAttacker(event.getSource());
        LivingEntity target = event.getEntity();
        if (familyAttacker != null && !isAllowedFamilyTarget(familyAttacker, target)) {
            event.setCanceled(true);
            clearFamilyTarget(familyAttacker, target);
        }
    }

    @SubscribeEvent
    public static void onLivingTick(EntityTickEvent.Pre event) {
        if (event.getEntity().level().isClientSide
                || !(event.getEntity() instanceof Mob mob)
                || !HerobrineFamilyMembers.isFamilyMember(mob)) {
            return;
        }

        LivingEntity target = mob.getTarget();
        if (target != null && !isAllowedFamilyTarget(mob, target)) {
            clearFamilyTarget(mob, target);
        }

        if (mob instanceof WitherBoss wither) {
            clearInvalidWitherHeadTargets(wither);
        }
    }

    private static boolean isAllowedFamilyTarget(Mob familyMember, LivingEntity target) {
        if (target == null || !target.isAlive()) {
            return false;
        }
        if (target instanceof HeroEntity) {
            return false;
        }
        if (target instanceof Mob targetMob && HerobrineFamilyMembers.isFamilyMember(targetMob)) {
            return false;
        }
        if (target instanceof Player player && (player.isCreative() || player.isSpectator())) {
            return false;
        }
        return isRetaliationTargetActive(familyMember, target);
    }

    private static boolean shouldAllowRetaliationAgainst(LivingEntity attacker) {
        if (attacker instanceof HeroEntity) {
            return false;
        }
        return !(attacker instanceof Mob mob && HerobrineFamilyMembers.isFamilyMember(mob));
    }

    private static void markRetaliationTarget(Mob familyMember, LivingEntity target) {
        long now = familyMember.level().getGameTime();
        familyMember.getPersistentData().putUUID(RETALIATION_TARGET_TAG, target.getUUID());
        familyMember.getPersistentData().putLong(RETALIATION_UNTIL_TAG, now + RETALIATION_WINDOW_TICKS);
    }

    private static boolean isRetaliationTargetActive(Mob familyMember, LivingEntity target) {
        if (!isRetaliationWindowActive(familyMember)) {
            return false;
        }
        return target.getUUID().equals(familyMember.getPersistentData().getUUID(RETALIATION_TARGET_TAG));
    }

    private static boolean hasActiveRetaliationTarget(Mob familyMember) {
        if (!isRetaliationWindowActive(familyMember)) {
            return false;
        }

        UUID targetUuid = familyMember.getPersistentData().getUUID(RETALIATION_TARGET_TAG);
        Entity target = findEntityByUuid(familyMember, targetUuid);
        if (!(target instanceof LivingEntity livingTarget)
                || !livingTarget.isAlive()
                || !isValidRetaliationTarget(livingTarget)) {
            clearRetaliationTarget(familyMember);
            return false;
        }
        return true;
    }

    private static boolean isRetaliationWindowActive(Mob familyMember) {
        if (!familyMember.getPersistentData().hasUUID(RETALIATION_TARGET_TAG)) {
            return false;
        }
        long now = familyMember.level().getGameTime();
        if (now <= familyMember.getPersistentData().getLong(RETALIATION_UNTIL_TAG)) {
            return true;
        }
        clearRetaliationTarget(familyMember);
        return false;
    }

    private static boolean isValidRetaliationTarget(LivingEntity target) {
        if (target instanceof HeroEntity) {
            return false;
        }
        if (target instanceof Mob mob && HerobrineFamilyMembers.isFamilyMember(mob)) {
            return false;
        }
        return !(target instanceof Player player && (player.isCreative() || player.isSpectator()));
    }

    private static Entity findEntityByUuid(Mob familyMember, UUID targetUuid) {
        if (targetUuid == null || familyMember.level().getServer() == null) {
            return null;
        }
        for (var level : familyMember.level().getServer().getAllLevels()) {
            Entity entity = level.getEntity(targetUuid);
            if (entity != null) {
                return entity;
            }
        }
        return null;
    }

    private static void clearRetaliationTarget(Mob familyMember) {
        familyMember.getPersistentData().remove(RETALIATION_TARGET_TAG);
        familyMember.getPersistentData().remove(RETALIATION_UNTIL_TAG);
    }

    private static void clearFamilyTarget(Mob familyMember, LivingEntity target) {
        if (familyMember.getTarget() == target) {
            familyMember.setTarget(null);
        }
        if (familyMember instanceof WitherBoss wither) {
            clearWitherHeadTarget(wither, target);
        }
    }

    private static void clearInvalidWitherHeadTargets(WitherBoss wither) {
        for (int head = 0; head < 3; head++) {
            int targetId = wither.getAlternativeTarget(head);
            if (targetId <= 0) {
                continue;
            }
            Entity target = wither.level().getEntity(targetId);
            if (target instanceof LivingEntity livingTarget && isAllowedFamilyTarget(wither, livingTarget)) {
                continue;
            }
            wither.setAlternativeTarget(head, 0);
        }
    }

    private static void clearWitherHeadTarget(WitherBoss wither, LivingEntity target) {
        for (int head = 0; head < 3; head++) {
            if (wither.getAlternativeTarget(head) == target.getId()) {
                wither.setAlternativeTarget(head, 0);
            }
        }
    }

    private static Mob getFamilyAttacker(DamageSource source) {
        LivingEntity attacker = getLivingAttacker(source);
        if (attacker instanceof Mob mob && HerobrineFamilyMembers.isFamilyMember(mob)) {
            return mob;
        }
        return null;
    }

    private static LivingEntity getLivingAttacker(DamageSource source) {
        if (source == null) {
            return null;
        }

        Entity attacker = source.getEntity();
        if (attacker instanceof LivingEntity livingAttacker) {
            return livingAttacker;
        }

        Entity direct = source.getDirectEntity();
        if (direct instanceof LivingEntity livingDirect) {
            return livingDirect;
        }
        if (direct instanceof Projectile projectile && projectile.getOwner() instanceof LivingEntity owner) {
            return owner;
        }

        return null;
    }
}
