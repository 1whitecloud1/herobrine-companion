package com.whitecloud233.herobrine_companion.entity.ai.learning.state;

import com.whitecloud233.herobrine_companion.entity.HeroEntity;
import com.whitecloud233.herobrine_companion.entity.ai.learning.HeroDialogueHandler;
import com.whitecloud233.herobrine_companion.entity.ai.learning.SimpleNeuralNetwork;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.monster.Monster;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class MonsterKingStateDefinition implements HeroMindStateDefinition {

    private static final String COOLDOWN_PACIFY = "MonsterKingCooldownPacify";
    private static final String COOLDOWN_ESCORT = "MonsterKingCooldownEscort";
    private static final String COOLDOWN_PRESSURE = "MonsterKingCooldownPressure";
    private static final String COOLDOWN_GROWL = "MonsterKingCooldownGrowl";
    private static final String COOLDOWN_DIALOGUE = "MonsterKingCooldownDialogue";

    private static final int PACIFY_COOLDOWN = 600;
    private static final int DIALOGUE_COOLDOWN = 2400;
    private static final float DIALOGUE_CHANCE = 0.15F;
    private static final int ESCORT_COOLDOWN = 1200;
    private static final float ESCORT_CHANCE = 0.10F;
    private static final int ESCORT_COUNT = 3;
    private static final double ESCORT_RADIUS = 5.0D;
    private static final int PRESSURE_COOLDOWN = 1800;
    private static final float PRESSURE_CHANCE = 0.12F;
    private static final int PRESSURE_LOOK_LIMIT = 5;
    private static final int GROWL_COOLDOWN = 800;
    private static final float GROWL_CHANCE = 0.10F;
    private static final double SCAN_RANGE = 16.0D;
    private static final float STOP_ATTACK_CHANCE = 0.40F;
    private static final double DISTANCE_MIN = 5.0D;
    private static final double DISTANCE_MAX = 14.0D;
    private static final float ENTER_AFFINITY_MIN = 0.45F;
    private static final float ENTER_EMPATHY_MIN = 0.40F;
    private static final float ENTER_ANNOYANCE_MIN = 0.30F;
    private static final float EXIT_AFFINITY_MAX = 0.25F;
    @Override
    public SimpleNeuralNetwork.MindState state() {
        return SimpleNeuralNetwork.MindState.MONSTER_KING;
    }

    @Override
    public boolean shouldEnter(HeroMindStateSnapshot snapshot) {
        return meetsEntryRequirements(snapshot);
    }

    @Override
    public SimpleNeuralNetwork.MindState shouldExit(HeroMindStateSnapshot snapshot, HeroEntity hero) {
        return snapshot.monsterEmpathyScore() < EXIT_AFFINITY_MAX ? SimpleNeuralNetwork.MindState.OBSERVER : null;
    }

    @Override
    public int minDwellTicks() {
        return 1600;
    }

    @Override
    public void tickServer(HeroEntity hero) {
        ServerPlayer focus = HeroStateBehaviorSupport.getFocusPlayer(hero, 24.0D);
        if (focus == null) return;

        ServerLevel level = (ServerLevel) hero.level();
        HeroStateBehaviorSupport.ensureFloating(hero);
        HeroStateBehaviorSupport.keepDistance(hero, focus, DISTANCE_MIN, DISTANCE_MAX, 0.8D, 0.6D);

        List<Monster> monsters = new ArrayList<>(HeroStateBehaviorSupport.getNearbyMonsters(hero, focus, SCAN_RANGE));
        monsters.sort(Comparator.comparingDouble(hero::distanceToSqr));

        if (isCooldownReady(hero, COOLDOWN_PACIFY) && tryPacifyNearby(hero, focus, level, monsters)) {
            startCooldown(hero, COOLDOWN_PACIFY, PACIFY_COOLDOWN);
            return;
        }

        if (!monsters.isEmpty()
                && isCooldownReady(hero, COOLDOWN_ESCORT)
                && hero.getRandom().nextFloat() < ESCORT_CHANCE
                && tryFormEscortRing(hero, level, monsters)) {
            startCooldown(hero, COOLDOWN_ESCORT, ESCORT_COOLDOWN);
            return;
        }

        if (!monsters.isEmpty()
                && isCooldownReady(hero, COOLDOWN_PRESSURE)
                && hero.getRandom().nextFloat() < PRESSURE_CHANCE
                && doPressureGaze(hero, focus, level, monsters)) {
            startCooldown(hero, COOLDOWN_PRESSURE, PRESSURE_COOLDOWN);
            return;
        }

        if (isCooldownReady(hero, COOLDOWN_GROWL) && hero.getRandom().nextFloat() < GROWL_CHANCE) {
            doGrowl(hero, level);
            startCooldown(hero, COOLDOWN_GROWL, GROWL_COOLDOWN);
            return;
        }

        if (isCooldownReady(hero, COOLDOWN_DIALOGUE) && hero.getRandom().nextFloat() < DIALOGUE_CHANCE) {
            ServerPlayer dialogueTarget = HeroStateBehaviorSupport.getOwner(hero);
            if (dialogueTarget == null) {
                dialogueTarget = focus;
            }
            HeroDialogueHandler.tryAIDialogueOrFallback(
                    hero,
                    dialogueTarget,
                    "You are commanding nearby monsters with cold authority. Hint at a monster trial without sounding friendly.",
                    "message.herobrine_companion.state_monster_king",
                    2
            );
            startCooldown(hero, COOLDOWN_DIALOGUE, DIALOGUE_COOLDOWN);
            return;
        }

        Monster nearestMonster = monsters.isEmpty() ? null : monsters.get(0);
        if (nearestMonster != null) {
            hero.getLookControl().setLookAt(nearestMonster, 20.0F, 20.0F);
        } else {
            HeroStateBehaviorSupport.clearAggroAndLookAt(hero, focus);
        }
    }

    @Override
    public void tickClientAmbient(HeroEntity hero) {
    }

    static boolean meetsEntryRequirements(HeroMindStateSnapshot snapshot) {
        return snapshot.monsterEmpathyScore() >= ENTER_AFFINITY_MIN
                || (snapshot.monsterEmpathyScore() >= ENTER_EMPATHY_MIN
                && snapshot.annoyanceWeight() >= ENTER_ANNOYANCE_MIN);
    }

    private static boolean tryPacifyNearby(HeroEntity hero, ServerPlayer focus, ServerLevel level, List<Monster> monsters) {
        if (monsters.isEmpty()) {
            return false;
        }

        int pacified = 0;
        for (Monster monster : monsters) {
            if (hero.getRandom().nextFloat() > STOP_ATTACK_CHANCE) {
                continue;
            }
            HeroStateBehaviorSupport.pacifyMonster(monster);
            pacified++;
        }

        if (pacified <= 0) {
            return false;
        }

        HeroDialogueHandler.onPacifyMonster(hero, focus);
        return true;
    }

    private static boolean tryFormEscortRing(HeroEntity hero, ServerLevel level, List<Monster> monsters) {
        int count = Math.min(monsters.size(), ESCORT_COUNT);
        if (count <= 0) {
            return false;
        }

        for (int i = 0; i < count; i++) {
            Monster monster = monsters.get(i);
            HeroStateBehaviorSupport.pacifyMonster(monster);
            HeroStateBehaviorSupport.commandMonsterEscort(monster, hero, i, count, ESCORT_RADIUS);
        }

        level.playSound(null, hero.blockPosition(), SoundEvents.WITHER_AMBIENT, SoundSource.HOSTILE, 0.3F, 0.7F);
        return true;
    }

    private static boolean doPressureGaze(HeroEntity hero, ServerPlayer focus, ServerLevel level, List<Monster> monsters) {
        int aimed = 0;
        for (Monster monster : monsters) {
            if (aimed >= PRESSURE_LOOK_LIMIT) {
                break;
            }
            HeroStateBehaviorSupport.pacifyMonster(monster);
            monster.getLookControl().setLookAt(focus, 20.0F, 20.0F);
            aimed++;
        }

        if (aimed <= 0) {
            return false;
        }

        level.playSound(null, focus.blockPosition(), SoundEvents.ENDERMAN_STARE, SoundSource.HOSTILE, 0.35F, 0.7F);
        return true;
    }

    private static void doGrowl(HeroEntity hero, ServerLevel level) {
        SoundEvent sound = pickGrowlSound(hero);
        level.playSound(null, hero.blockPosition(), sound, SoundSource.HOSTILE, 0.5F, 0.6F);
    }

    private static SoundEvent pickGrowlSound(HeroEntity hero) {
        return switch (hero.getRandom().nextInt(3)) {
            case 0 -> SoundEvents.WOLF_GROWL;
            case 1 -> SoundEvents.WITHER_AMBIENT;
            default -> SoundEvents.ENDER_DRAGON_GROWL;
        };
    }

    private static boolean isCooldownReady(HeroEntity hero, String key) {
        return hero.level().getGameTime() >= hero.getPersistentData().getLong(key);
    }

    private static void startCooldown(HeroEntity hero, String key, int duration) {
        hero.getPersistentData().putLong(key, hero.level().getGameTime() + duration);
    }
}
