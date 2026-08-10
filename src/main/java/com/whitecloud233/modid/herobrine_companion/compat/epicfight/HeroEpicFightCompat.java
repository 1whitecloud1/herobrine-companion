package com.whitecloud233.modid.herobrine_companion.compat.epicfight;

import com.mojang.logging.LogUtils;
import com.whitecloud233.modid.herobrine_companion.entity.HeroEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.loading.FMLEnvironment;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.Filter;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.filter.AbstractFilter;
import org.slf4j.Logger;

import java.util.Set;

public final class HeroEpicFightCompat {
    private static final String EPIC_FIGHT_MOD_ID = "epicfight";
    private static final String EPIC_FIGHT_ANIMATION_MANAGER = "yesman.epicfight.api.animation.AnimationManager";
    private static final String EPIC_FIGHT_MAIN_CLASS = "yesman.epicfight.main.EpicFightMod";
    private static final String EFN_ANIMATION_NAMESPACE = "efn";
    private static final String BRIDGE_CLASS = "com.whitecloud233.modid.herobrine_companion.compat.epicfight.HeroEpicFightBridge";
    private static final String CLIENT_BRIDGE_CLASS = "com.whitecloud233.modid.herobrine_companion.compat.epicfight.HeroEpicFightClientBridge";
    private static final String WEAPON_PROFILES_CLASS = "com.whitecloud233.modid.herobrine_companion.compat.epicfight.HeroEpicFightWeaponProfiles";
    private static final String PATCH_CLASS = "com.whitecloud233.modid.herobrine_companion.compat.epicfight.HeroEpicFightPatch";
    private static final String NIGHTFALL_MOVESETS_CLASS = "com.whitecloud233.modid.herobrine_companion.compat.epicfight.HeroNightfallMovesets";
    private static final String NIGHTFALL_ANIMATION_REGISTRY_CLASS = "com.whitecloud233.modid.herobrine_companion.compat.epicfight.HeroNightfallAnimationRegistry";
    private static final String NIGHTFALL_SKILL_EFFECTS_CLASS = "com.whitecloud233.modid.herobrine_companion.compat.epicfight.HeroNightfallSkillEffects";
    private static final String DEBUG_LOG_CLASS = "com.whitecloud233.modid.herobrine_companion.compat.epicfight.HeroEpicFightDebugLog";
    private static final String PATCHED_HUMANOID_RENDERER_CLASS = "com.whitecloud233.modid.herobrine_companion.compat.epicfight.HeroPatchedHumanoidRenderer";
    private static final String PATCHED_EYES_LAYER_CLASS = "com.whitecloud233.modid.herobrine_companion.compat.epicfight.HeroPatchedEyesLayer";
    private static final String PATCHED_ITEM_IN_HAND_LAYER_CLASS = "com.whitecloud233.modid.herobrine_companion.compat.epicfight.HeroPatchedItemInHandLayer";
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String[][] BRIDGE_PROBES = new String[][]{
            {EPIC_FIGHT_MAIN_CLASS, "yesman.epicfight.api.animation.LivingMotions", "yesman.epicfight.gameasset.Animations"},
            {"yesman.epicfight.api.forgeevent.EntityPatchRegistryEvent", "yesman.epicfight.gameasset.Armatures", "yesman.epicfight.world.capabilities.EpicFightCapabilities"},
            {"yesman.epicfight.world.capabilities.item.WeaponCapabilityPresets", "yesman.epicfight.world.capabilities.item.WeaponCapability"},
            {"yesman.epicfight.api.ex_cap.core.managers.BuilderManager", "yesman.epicfight.gameasset.ex_cap.Builders", "yesman.epicfight.world.capabilities.item.WeaponCapability"}
    };
    private static final String[][] CLIENT_BRIDGE_PROBES = new String[][]{
            {"yesman.epicfight.api.client.forgeevent.PatchedRenderersEvent", "yesman.epicfight.client.renderer.patched.entity.PCustomHumanoidEntityRenderer", "yesman.epicfight.client.renderer.patched.layer.PatchedItemInHandLayer"}
    };
    private static final String[] COMMON_BRIDGE_RUNTIME_CLASSES = new String[]{
            BRIDGE_CLASS,
            WEAPON_PROFILES_CLASS,
            PATCH_CLASS,
            NIGHTFALL_MOVESETS_CLASS,
            NIGHTFALL_ANIMATION_REGISTRY_CLASS,
            NIGHTFALL_SKILL_EFFECTS_CLASS,
            DEBUG_LOG_CLASS
    };
    private static final String[] CLIENT_BRIDGE_RUNTIME_CLASSES = new String[]{
            CLIENT_BRIDGE_CLASS,
            PATCHED_HUMANOID_RENDERER_CLASS,
            PATCHED_EYES_LAYER_CLASS,
            PATCHED_ITEM_IN_HAND_LAYER_CLASS
    };

    private static volatile BridgeStatus bridgeStatus = BridgeStatus.UNINITIALIZED;
    private static volatile boolean bridgeInitialized;
    private static volatile boolean bridgeBootstrapped;
    private static volatile boolean externalAnimationWarningsSuppressed;
    private static volatile boolean efnKinematicsWarningsSuppressed;

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

        if (!preloadBridgeClasses(COMMON_BRIDGE_RUNTIME_CLASSES)) {
            markBridgeMismatch("Epic Fight is installed, but Hero common bridge classes could not be loaded safely. Hero will keep the fallback battle renderer without hard failure.", null);
            return;
        }

        if (FMLEnvironment.dist == Dist.CLIENT && !preloadBridgeClasses(CLIENT_BRIDGE_RUNTIME_CLASSES)) {
            markBridgeMismatch("Epic Fight is installed, but Hero client bridge classes could not be loaded safely. Hero will keep vanilla renderer poses.", null);
            return;
        }

        suppressExternalAnimationResourceWarnings();
        suppressEfnKinematicsWarnings();

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
            suppressExternalAnimationResourceWarnings();
            suppressEfnKinematicsWarnings();
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

    /**
     * Hero 是否正处在 Epic Fight 攻击/动作状态。
     * 飞行追击（HeroEpicFightChaseGoal）据此在悬停时让出空中连段窗口、不落地打断。
     */
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

    /**
     * 让 Hero 立即施放一个夜幕技能（agent 工具 {@code hero_use_skill} 的门面）。
     * 返回 {@code "OK|…"} / {@code "FAIL|…"}；桥未就绪或调用异常返回 {@code null}。
     * 本类只反射调用 bridge，不直接触碰任何 EpicFight 类。
     */
    public static String triggerSkill(HeroEntity hero, String skillName) {
        if (!isRuntimeBridgeReady() || hero == null) {
            return null;
        }
        try {
            Object result = invokeStatic(BRIDGE_CLASS, "triggerSkill",
                    new Class<?>[]{HeroEntity.class, String.class}, hero, skillName);
            return result instanceof String message ? message : null;
        } catch (ReflectiveOperationException | LinkageError exception) {
            return null;
        }
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

    private static boolean preloadBridgeClasses(String[] classNames) {
        for (String className : classNames) {
            if (!classLoads(className, true)) {
                return false;
            }
        }
        return true;
    }

    private static boolean classLoads(String className, boolean initialize) {
        try {
            Class.forName(className, initialize, HeroEpicFightCompat.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException exception) {
            LOGGER.warn("Epic Fight bridge preflight failed because {} was not found.", className, exception);
            return false;
        } catch (LinkageError error) {
            LOGGER.warn("Epic Fight bridge preflight failed while loading {}", className, error);
            return false;
        }
    }

    private static synchronized void suppressExternalAnimationResourceWarnings() {
        if (externalAnimationWarningsSuppressed) {
            return;
        }

        try {
            Class<?> animationManager = Class.forName(EPIC_FIGHT_ANIMATION_MANAGER, false, HeroEpicFightCompat.class.getClassLoader());
            animationManager.getMethod("addNoWarningModId", String.class).invoke(null, EFN_ANIMATION_NAMESPACE);
            externalAnimationWarningsSuppressed = true;
            LOGGER.info("Registered Epic Fight Nightfall animation namespace '{}' as no-warning user animation resources.", EFN_ANIMATION_NAMESPACE);
        } catch (ReflectiveOperationException | LinkageError exception) {
            LOGGER.warn("Epic Fight is installed, but failed to register external animation warning suppressions. EFN resourcepack animation warnings may still be printed.", exception);
        }
    }

    private static synchronized void suppressEfnKinematicsWarnings() {
        if (efnKinematicsWarningsSuppressed) {
            return;
        }

        try {
            Object logger = LogManager.getLogger(EPIC_FIGHT_MOD_ID);
            if (logger instanceof org.apache.logging.log4j.core.Logger coreLogger) {
                coreLogger.addFilter(EfnKinematicsLogFilter.INSTANCE);
                efnKinematicsWarningsSuppressed = true;
                LOGGER.info("Installed Epic Fight logger filter for EFN .KINEMATICS joint debug noise.");
            }
        } catch (RuntimeException exception) {
            LOGGER.warn("Epic Fight is installed, but failed to install the EFN .KINEMATICS log filter. Debug noise may still be printed.", exception);
        }
    }

    private static boolean shouldSuppressKinematicsMessage(String message) {
        return message != null
                && message.contains("No joint named ")
                && message.contains(".KINEMATICS")
                && (message.contains(" efn:") || message.startsWith("efn:") || message.contains("efn:animmodels/"));
    }

    private static final class EfnKinematicsLogFilter extends AbstractFilter {
        private static final Filter INSTANCE = new EfnKinematicsLogFilter();

        @Override
        public Result filter(LogEvent event) {
            if (event == null || event.getMessage() == null) {
                return Result.NEUTRAL;
            }

            return shouldSuppressKinematicsMessage(event.getMessage().getFormattedMessage()) ? Result.DENY : Result.NEUTRAL;
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
            Object result = invokeStatic(WEAPON_PROFILES_CLASS, "hasHeroControlledCombatAnimations", new Class<?>[]{HeroEntity.class}, hero);
            return result instanceof Boolean enabled && enabled;
        } catch (ReflectiveOperationException | LinkageError exception) {
            markBridgeMismatch("Epic Fight weapon profile resolution failed. Hero will keep fallback battle behavior.", exception);
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

