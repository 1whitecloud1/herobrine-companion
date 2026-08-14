package com.whitecloud233.herobrine_companion.compat.epicfight;

import com.mojang.logging.LogUtils;
import com.whitecloud233.herobrine_companion.entity.HeroEntity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLEnvironment;
import org.slf4j.Logger;

import java.util.Set;

public final class HeroEpicFightCompat {
    private static final String EPIC_FIGHT_MOD_ID = "epicfight";
    private static final String EPIC_FIGHT_MAIN_CLASS = "yesman.epicfight.main.EpicFightMod";
    private static final String BRIDGE_CLASS = "com.whitecloud233.herobrine_companion.compat.epicfight.HeroEpicFightBridge";
    private static final String CLIENT_BRIDGE_CLASS = "com.whitecloud233.herobrine_companion.compat.epicfight.HeroEpicFightClientBridge";
    private static final String WEAPON_PROFILES_CLASS = "com.whitecloud233.herobrine_companion.compat.epicfight.HeroEpicFightWeaponProfiles";
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String[][] BRIDGE_PROBES = new String[][]{
            {EPIC_FIGHT_MAIN_CLASS, "yesman.epicfight.api.animation.LivingMotions", "yesman.epicfight.gameasset.Animations"},
            {"yesman.epicfight.api.event.types.registry.EntityPatchRegistryEvent", "yesman.epicfight.gameasset.Armatures", "yesman.epicfight.world.capabilities.EpicFightCapabilities"},
            {"yesman.epicfight.api.ex_cap.modules.core.managers.BuilderManager", "yesman.epicfight.api.ex_cap.modules.assets.Builders", "yesman.epicfight.world.capabilities.item.WeaponCapability"}
    };
    private static final String[][] CLIENT_BRIDGE_PROBES = new String[][]{
            {"yesman.epicfight.api.client.event.types.registry.RegisterPatchedRenderersEvent", "yesman.epicfight.client.renderer.patched.entity.PCustomHumanoidEntityRenderer", "yesman.epicfight.client.renderer.patched.layer.PatchedItemInHandLayer"}
    };

    private static volatile BridgeStatus bridgeStatus = BridgeStatus.UNINITIALIZED;
    private static volatile boolean bridgeInitialized;
    private static volatile boolean bridgeBootstrapped;

    private HeroEpicFightCompat() {}

    public enum BridgeStatus {
        UNINITIALIZED,
        NOT_INSTALLED,
        READY,
        API_MISMATCH
    }

    public static boolean isLoaded() {
        return ModList.get().isLoaded(EPIC_FIGHT_MOD_ID);
    }

    public static synchronized void bootstrap(IEventBus modEventBus) {
        if (bridgeBootstrapped) {
            return;
        }
        bridgeBootstrapped = true;

        if (!isLoaded()) {
            bridgeStatus = BridgeStatus.NOT_INSTALLED;
            bridgeInitialized = true;
            LOGGER.info("Epic Fight not detected; Hero battle mode will continue using the vanilla fallback bridge.");
            return;
        }

        if (!hasSupportedRuntimeShape()) {
            bridgeStatus = BridgeStatus.API_MISMATCH;
            bridgeInitialized = true;
            LOGGER.warn("Epic Fight is installed but its runtime API shape was not recognized. Hero will keep the fallback battle renderer without hard failure.");
            return;
        }

        if (modEventBus != null) {
            if (!registerCommonBridge(modEventBus)) {
                markBridgeMismatch("Epic Fight bridge registration failed. Hero will keep the fallback battle renderer without hard failure.", null);
                return;
            }
            if (FMLEnvironment.dist == Dist.CLIENT) {
                registerClientBridge(modEventBus);
                if (bridgeStatus == BridgeStatus.API_MISMATCH) {
                    return;
                }
            }
        }

        bridgeStatus = BridgeStatus.READY;
        bridgeInitialized = true;
        LOGGER.info("Epic Fight detected. Hero runtime bridge is active. Exported states: {}", HeroEpicFightStateMapper.getExportedStateNames());
    }

    public static synchronized void tryRegisterRuntimeBridge() {
        if (!bridgeInitialized) {
            bootstrap(null);
        }

        if (bridgeStatus == BridgeStatus.READY) {
            initializeRuntimeBridge();
        }
    }

    public static BridgeStatus getBridgeStatus() {
        if (!bridgeInitialized) {
            tryRegisterRuntimeBridge();
        }
        return bridgeStatus;
    }

    public static boolean isRuntimeBridgeReady() {
        return getBridgeStatus() == BridgeStatus.READY;
    }

    /** Hero 是否正处在 Epic Fight 攻击/动作状态（飞行追击据此让出空中连段、不落地打断）。 */
    public static boolean isHeroMidAttack(HeroEntity hero) {
        if (!isRuntimeBridgeReady() || hero == null) {
            return false;
        }
        try {
            Object result = invokeStatic(BRIDGE_CLASS, "isHeroMidAttack", new Class<?>[]{HeroEntity.class}, hero);
            return result instanceof Boolean midAttack && midAttack;
        } catch (ReflectiveOperationException | LinkageError exception) {
            return false;
        }
    }

    /** 让 Hero 立即施放一个夜幕技能（agent 工具 {@code hero_use_skill} 的桥接实现）。返回 {@code "OK|…"} / {@code "FAIL|…"}。 */
    public static String triggerSkill(HeroEntity hero, String skillName) {
        if (!isRuntimeBridgeReady() || hero == null) {
            return "FAIL|夜幕技能桥未就绪";
        }
        try {
            Object result = invokeStatic(BRIDGE_CLASS, "triggerSkill", new Class<?>[]{HeroEntity.class, String.class}, hero, skillName);
            return result instanceof String str ? str : "FAIL|未知结果";
        } catch (ReflectiveOperationException | LinkageError exception) {
            LOGGER.warn("Epic Fight nightfall skill trigger failed", exception);
            return "FAIL|夜幕技能桥调用失败";
        }
    }

    public static String getCurrentBattleStateKey(HeroEntity hero) {
        return HeroEpicFightStateMapper.mapHeroBattleState(hero);
    }

    public static String getCurrentWeaponStyleKey(HeroEntity hero) {
        return HeroEpicFightStateMapper.mapWeaponStyle(hero);
    }

    public static String describeCurrentSnapshot(HeroEntity hero) {
        return HeroEpicFightStateMapper.describeCurrentSnapshot(hero);
    }

    public static Set<String> getExportedStateNames() {
        return HeroEpicFightStateMapper.getExportedStateNames();
    }

    public static boolean shouldUseEpicFightPose(HeroEntity hero) {
        return isRuntimeBridgeReady()
                && hero != null
                && hero.isBattleModeActive()
                && isPatched(hero)
                && hasHeroControlledCombatAnimations(hero)
                && !hero.isInspectingScythe()
                && !hero.isCastingThunder()
                && !hero.isDebugAnim()
                && !hero.getEntityData().get(HeroEntity.IS_CHALLENGE_ACTIVE);
    }

    public static boolean shouldUseEpicFightCombatAI(HeroEntity hero) {
        return isRuntimeBridgeReady()
                && hero != null
                && hero.isBattleModeActive()
                && hasHeroControlledCombatAnimations(hero)
                && !hero.isInspectingScythe()
                && !hero.isCastingThunder()
                && !hero.isDebugAnim()
                && !hero.getEntityData().get(HeroEntity.IS_CHALLENGE_ACTIVE);
    }

    public static boolean shouldUseEpicFightHeldItemLayer(HeroEntity hero) {
        return isRuntimeBridgeReady()
                && hero != null
                && hero.isBattleModeActive()
                && isPatched(hero)
                && hasHeroControlledCombatAnimations(hero)
                && !hero.isInspectingScythe()
                && !hero.isCastingThunder()
                && !hero.isDebugAnim()
                && !hero.getEntityData().get(HeroEntity.IS_CHALLENGE_ACTIVE);
    }

    public static String getInstallHintKey() {
        return "gui.herobrine_companion.battle_mode_epicfight_hint";
    }

    private static boolean hasSupportedRuntimeShape() {
        for (String[] probeGroup : BRIDGE_PROBES) {
            boolean matched = true;
            for (String className : probeGroup) {
                if (!classExists(className)) {
                    matched = false;
                    break;
                }
            }
            if (matched) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasSupportedClientRuntimeShape() {
        for (String[] probeGroup : CLIENT_BRIDGE_PROBES) {
            boolean matched = true;
            for (String className : probeGroup) {
                if (!classExists(className)) {
                    matched = false;
                    break;
                }
            }
            if (matched) {
                return true;
            }
        }
        return false;
    }

    private static boolean classExists(String className) {
        try {
            Class.forName(className, false, HeroEpicFightCompat.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException exception) {
            return false;
        } catch (LinkageError error) {
            LOGGER.warn("Epic Fight class probe failed for {}", className, error);
            return false;
        }
    }


    private static boolean registerCommonBridge(IEventBus modEventBus) {
        try {
            invokeStatic(BRIDGE_CLASS, "register", new Class<?>[]{IEventBus.class}, modEventBus);
            return true;
        } catch (ReflectiveOperationException | LinkageError exception) {
            LOGGER.warn("Epic Fight is installed, but Hero common bridge failed to initialize. Falling back to vanilla Hero battle behavior.", exception);
            return false;
        }
    }

    private static synchronized void initializeRuntimeBridge() {
        if (bridgeStatus != BridgeStatus.READY) {
            return;
        }

        try {
            invokeStatic(BRIDGE_CLASS, "registerArmature", new Class<?>[0]);
            invokeStatic(WEAPON_PROFILES_CLASS, "bootstrap", new Class<?>[0]);
        } catch (ReflectiveOperationException | LinkageError exception) {
            markBridgeMismatch("Epic Fight runtime bridge initialization failed. Hero will keep fallback battle behavior.", exception);
        }
    }

    private static boolean isPatched(HeroEntity hero) {
        try {
            Object result = invokeStatic(BRIDGE_CLASS, "isPatched", new Class<?>[]{HeroEntity.class}, hero);
            return result instanceof Boolean patched && patched;
        } catch (ReflectiveOperationException | LinkageError exception) {
            markBridgeMismatch("Epic Fight patch lookup failed. Hero will keep fallback battle behavior.", exception);
            return false;
        }
    }

    private static boolean hasHeroControlledCombatAnimations(HeroEntity hero) {
        try {
            Object result = invokeStatic(WEAPON_PROFILES_CLASS, "hasHeroControlledCombatAnimations",
                    new Class<?>[]{HeroEntity.class}, hero);
            return result instanceof Boolean enabled && enabled;
        } catch (ReflectiveOperationException | LinkageError exception) {
            markBridgeMismatch("Epic Fight weapon profile lookup failed. Hero will keep fallback battle behavior.", exception);
            return false;
        }
    }

    private static synchronized void markBridgeMismatch(String message, Throwable throwable) {
        bridgeStatus = BridgeStatus.API_MISMATCH;
        bridgeInitialized = true;
        if (throwable == null) {
            LOGGER.warn(message);
        } else {
            LOGGER.warn(message, throwable);
        }
    }

    private static Object invokeStatic(String className, String methodName, Class<?>[] parameterTypes, Object... args)
            throws ReflectiveOperationException {
        Class<?> targetClass = Class.forName(className, false, HeroEpicFightCompat.class.getClassLoader());
        return targetClass.getMethod(methodName, parameterTypes).invoke(null, args);
    }

    private static void registerClientBridge(IEventBus modEventBus) {
        if (!hasSupportedClientRuntimeShape()) {
            markBridgeMismatch("Epic Fight is installed but its client renderer bridge API shape was not recognized. Hero will keep vanilla renderer poses.", null);
            return;
        }

        try {
            Class<?> bridgeClass = Class.forName(CLIENT_BRIDGE_CLASS, false, HeroEpicFightCompat.class.getClassLoader());
            bridgeClass.getMethod("register", IEventBus.class).invoke(null, modEventBus);
        } catch (ReflectiveOperationException | LinkageError exception) {
            markBridgeMismatch("Epic Fight is installed, but Hero client renderer bridge failed to initialize. Falling back to vanilla Hero renderer poses.", exception);
        }
    }
}

