package com.whitecloud233.herobrine_companion.compat.waveycapes;

import com.whitecloud233.herobrine_companion.entity.HeroEntity;
import net.minecraft.util.Mth;

import java.util.Map;
import java.util.WeakHashMap;

final class HeroCapeState {

    private static final Map<HeroEntity, HeroCapeState> STATES = new WeakHashMap<>();

    private Object simulation;
    private double xCloak;
    private double yCloak;
    private double zCloak;
    private double xCloakO;
    private double yCloakO;
    private double zCloakO;
    private boolean cloakInitialized;
    private boolean dirty;
    private int lastSimulationTick = Integer.MIN_VALUE;

    static HeroCapeState get(HeroEntity hero) {
        return STATES.computeIfAbsent(hero, ignored -> new HeroCapeState());
    }

    void prepare(HeroEntity hero, int partCount) {
        updateCloakTracking(hero);
        updateSimulation(partCount);
        if (simulation == null) {
            return;
        }

        HeroCapeDelegate delegate = new HeroCapeDelegate(hero, this);
        if (dirty) {
            WaveyCapesRuntime.nudgeSimulation(simulation);
            for (int i = 0; i < 5; i++) {
                WaveyCapesRuntime.simulate(simulation, delegate);
            }
            dirty = false;
            lastSimulationTick = hero.tickCount;
            return;
        }

        if (lastSimulationTick != hero.tickCount) {
            WaveyCapesRuntime.simulate(simulation, delegate);
            lastSimulationTick = hero.tickCount;
        }
    }

    Object getSimulation() {
        return simulation;
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

    private void updateSimulation(int partCount) {
        simulation = WaveyCapesRuntime.ensureSimulation(simulation);
        if (simulation != null && WaveyCapesRuntime.initSimulation(simulation, partCount)) {
            setDirty();
        }
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

    private void setDirty() {
        this.dirty = true;
        this.lastSimulationTick = Integer.MIN_VALUE;
    }
}
