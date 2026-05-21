package com.whitecloud233.modid.herobrine_companion.compat.waveycapes;

import com.whitecloud233.modid.herobrine_companion.entity.HeroEntity;
import dev.tr7zw.waveycapes.versionless.CapeHolder;
import dev.tr7zw.waveycapes.versionless.sim.BasicSimulation;
import dev.tr7zw.waveycapes.versionless.util.Mth;
import dev.tr7zw.waveycapes.versionless.util.Vector3;

import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

final class HeroCapeState implements CapeHolder {

    private static final Map<HeroEntity, HeroCapeState> STATES = new WeakHashMap<>();

    private BasicSimulation simulation;
    private Vector3 lastPlayerAnimatorPosition = new Vector3();
    private double xCloak;
    private double yCloak;
    private double zCloak;
    private double xCloakO;
    private double yCloakO;
    private double zCloakO;
    private boolean cloakInitialized;
    private boolean dirty;
    private int lastSimulationTick = Integer.MIN_VALUE;
    private UUID wcUuid;

    static HeroCapeState get(HeroEntity hero) {
        HeroCapeState state = STATES.computeIfAbsent(hero, ignored -> new HeroCapeState());
        state.wcUuid = hero.getUUID();
        return state;
    }

    void prepare(HeroEntity hero, HeroCapeDelegate delegate, int partCount) {
        updateCloakTracking(hero);
        updateSimulation(partCount);
        if (dirty && simulation != null) {
            simulation.applyMovement(new Vector3(1.0F, 1.0F, 0.0F));
            for (int i = 0; i < 5; i++) {
                simulate(delegate);
            }
            dirty = false;
            lastSimulationTick = hero.tickCount;
            return;
        }
        if (lastSimulationTick != hero.tickCount) {
            simulate(delegate);
            lastSimulationTick = hero.tickCount;
        }
    }

    float getXCloak(float partialTick) {
        return (float) Mth.lerp(partialTick, xCloakO, xCloak);
    }

    float getYCloak(float partialTick) {
        return (float) Mth.lerp(partialTick, yCloakO, yCloak);
    }

    float getZCloak(float partialTick) {
        return (float) Mth.lerp(partialTick, zCloakO, zCloak);
    }

    private void updateCloakTracking(HeroEntity hero) {
        double x = hero.getX();
        double y = hero.getY();
        double z = hero.getZ();
        if (!cloakInitialized) {
            xCloak = x;
            yCloak = y;
            zCloak = z;
            xCloakO = x;
            yCloakO = y;
            zCloakO = z;
            cloakInitialized = true;
            return;
        }

        xCloakO = xCloak;
        yCloakO = yCloak;
        zCloakO = zCloak;

        xCloak = smoothCloakComponent(xCloak, x);
        yCloak = smoothCloakComponent(yCloak, y);
        zCloak = smoothCloakComponent(zCloak, z);
    }

    private double smoothCloakComponent(double cloakValue, double entityValue) {
        double delta = entityValue - cloakValue;
        if (delta > 10.0D || delta < -10.0D) {
            return entityValue;
        }
        return cloakValue + delta * 0.25D;
    }

    @Override
    public BasicSimulation getSimulation() {
        return simulation;
    }

    @Override
    public Vector3 getLastPlayerAnimatorPosition() {
        return lastPlayerAnimatorPosition;
    }

    @Override
    public void setLastPlayerAnimatorPosition(Vector3 pos) {
        this.lastPlayerAnimatorPosition = pos;
    }

    @Override
    public void setSimulation(BasicSimulation sim) {
        this.simulation = sim;
    }

    @Override
    public UUID getWCUUID() {
        return wcUuid;
    }

    @Override
    public void setDirty() {
        this.dirty = true;
        this.lastSimulationTick = Integer.MIN_VALUE;
    }
}
