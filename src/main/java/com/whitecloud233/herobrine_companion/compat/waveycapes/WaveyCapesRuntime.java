package com.whitecloud233.herobrine_companion.compat.waveycapes;

import net.minecraft.util.Mth;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.List;

final class WaveyCapesRuntime {

    enum CapeStyleMode {
        BLOCKY,
        SMOOTH
    }

    enum CapeMovementMode {
        VANILLA,
        BASIC_SIMULATION,
        BASIC_SIMULATION_3D,
        DUNGEONS
    }

    enum WindModeValue {
        NONE,
        WAVES
    }

    record Point(float x, float y, float z) {
    }

    private static final String VECTOR3_CLASS_NAME = "dev.tr7zw.waveycapes.versionless.util.Vector3";
    private static final String MOD_BASE_CLASS_NAME = "dev.tr7zw.waveycapes.versionless.ModBase";
    private static final String MINECRAFT_PLAYER_CLASS_NAME = "dev.tr7zw.waveycapes.versionless.nms.MinecraftPlayer";
    private static final String CONFIG_CLASS_NAME = "dev.tr7zw.waveycapes.versionless.config.Config";
    private static final String BASIC_SIMULATION_CLASS_NAME = "dev.tr7zw.waveycapes.versionless.sim.BasicSimulation";

    private static final boolean AVAILABLE;
    private static final Class<?> vector3Class;
    private static final Class<?> modBaseClass;
    private static final Class<?> minecraftPlayerClass;
    private static final Constructor<?> vector3Constructor;
    private static final Method vectorRotateDegreesMethod;
    private static final Method modBaseGetInstanceMethod;
    private static final Method modBaseApplyModAnimationsMethod;
    private static final Field modBaseConfigField;
    private static final Field configCapeStyleField;
    private static final Field configCapeMovementField;
    private static final Field configWindModeField;
    private static final Field configGravityField;
    private static final Field configHeightMultiplierField;
    private static final Field configStraveMultiplierField;
    private static final Method simulationInitMethod;
    private static final Method simulationEmptyMethod;
    private static final Method simulationApplyMovementMethod;
    private static final Method simulationSimulateMethod;
    private static final Method simulationGetPointsMethod;
    private static final Method simulationSetGravityMethod;
    private static final Method simulationSetSneakingMethod;
    private static final Method simulationIsSneakingMethod;
    private static final Method simulationSetGravityDirectionMethod;
    private static final Method capePointGetLerpXMethod;
    private static final Method capePointGetLerpYMethod;
    private static final Method capePointGetLerpZMethod;

    static {
        boolean available = false;
        Class<?> loadedVector3Class = null;
        Class<?> loadedModBaseClass = null;
        Class<?> loadedMinecraftPlayerClass = null;
        Constructor<?> loadedVector3Constructor = null;
        Method loadedVectorRotateDegreesMethod = null;
        Method loadedModBaseGetInstanceMethod = null;
        Method loadedModBaseApplyModAnimationsMethod = null;
        Field loadedModBaseConfigField = null;
        Field loadedConfigCapeStyleField = null;
        Field loadedConfigCapeMovementField = null;
        Field loadedConfigWindModeField = null;
        Field loadedConfigGravityField = null;
        Field loadedConfigHeightMultiplierField = null;
        Field loadedConfigStraveMultiplierField = null;
        Method loadedSimulationInitMethod = null;
        Method loadedSimulationEmptyMethod = null;
        Method loadedSimulationApplyMovementMethod = null;
        Method loadedSimulationSimulateMethod = null;
        Method loadedSimulationGetPointsMethod = null;
        Method loadedSimulationSetGravityMethod = null;
        Method loadedSimulationSetSneakingMethod = null;
        Method loadedSimulationIsSneakingMethod = null;
        Method loadedSimulationSetGravityDirectionMethod = null;
        Method loadedCapePointGetLerpXMethod = null;
        Method loadedCapePointGetLerpYMethod = null;
        Method loadedCapePointGetLerpZMethod = null;

        try {
            ClassLoader classLoader = WaveyCapesRuntime.class.getClassLoader();
            loadedVector3Class = Class.forName(VECTOR3_CLASS_NAME, false, classLoader);
            loadedModBaseClass = Class.forName(MOD_BASE_CLASS_NAME, false, classLoader);
            loadedMinecraftPlayerClass = Class.forName(MINECRAFT_PLAYER_CLASS_NAME, false, classLoader);
            Class<?> loadedConfigClass = Class.forName(CONFIG_CLASS_NAME, false, classLoader);
            Class<?> loadedBasicSimulationClass = Class.forName(BASIC_SIMULATION_CLASS_NAME, false, classLoader);
            Class<?> loadedCapePointClass = Class.forName("dev.tr7zw.waveycapes.versionless.util.CapePoint", false, classLoader);

            loadedVector3Constructor = loadedVector3Class.getConstructor(float.class, float.class, float.class);
            loadedVectorRotateDegreesMethod = loadedVector3Class.getMethod("rotateDegrees", float.class);

            loadedModBaseGetInstanceMethod = loadedModBaseClass.getMethod("getINSTANCE");
            loadedModBaseApplyModAnimationsMethod = loadedModBaseClass.getMethod("applyModAnimations", loadedMinecraftPlayerClass, loadedVector3Class);
            loadedModBaseConfigField = loadedModBaseClass.getField("config");

            loadedConfigCapeStyleField = loadedConfigClass.getField("capeStyle");
            loadedConfigCapeMovementField = loadedConfigClass.getField("capeMovement");
            loadedConfigWindModeField = loadedConfigClass.getField("windMode");
            loadedConfigGravityField = loadedConfigClass.getField("gravity");
            loadedConfigHeightMultiplierField = loadedConfigClass.getField("heightMultiplier");
            loadedConfigStraveMultiplierField = loadedConfigClass.getField("straveMultiplier");

            loadedSimulationInitMethod = loadedBasicSimulationClass.getMethod("init", int.class);
            loadedSimulationEmptyMethod = loadedBasicSimulationClass.getMethod("empty");
            loadedSimulationApplyMovementMethod = loadedBasicSimulationClass.getMethod("applyMovement", loadedVector3Class);
            loadedSimulationSimulateMethod = loadedBasicSimulationClass.getMethod("simulate");
            loadedSimulationGetPointsMethod = loadedBasicSimulationClass.getMethod("getPoints");
            loadedSimulationSetGravityMethod = loadedBasicSimulationClass.getMethod("setGravity", float.class);
            loadedSimulationSetSneakingMethod = loadedBasicSimulationClass.getMethod("setSneaking", boolean.class);
            loadedSimulationIsSneakingMethod = loadedBasicSimulationClass.getMethod("isSneaking");
            loadedSimulationSetGravityDirectionMethod = loadedBasicSimulationClass.getMethod("setGravityDirection", loadedVector3Class);

            loadedCapePointGetLerpXMethod = loadedCapePointClass.getMethod("getLerpX", float.class);
            loadedCapePointGetLerpYMethod = loadedCapePointClass.getMethod("getLerpY", float.class);
            loadedCapePointGetLerpZMethod = loadedCapePointClass.getMethod("getLerpZ", float.class);
            available = true;
        } catch (Throwable ignored) {
        }

        AVAILABLE = available;
        vector3Class = loadedVector3Class;
        modBaseClass = loadedModBaseClass;
        minecraftPlayerClass = loadedMinecraftPlayerClass;
        vector3Constructor = loadedVector3Constructor;
        vectorRotateDegreesMethod = loadedVectorRotateDegreesMethod;
        modBaseGetInstanceMethod = loadedModBaseGetInstanceMethod;
        modBaseApplyModAnimationsMethod = loadedModBaseApplyModAnimationsMethod;
        modBaseConfigField = loadedModBaseConfigField;
        configCapeStyleField = loadedConfigCapeStyleField;
        configCapeMovementField = loadedConfigCapeMovementField;
        configWindModeField = loadedConfigWindModeField;
        configGravityField = loadedConfigGravityField;
        configHeightMultiplierField = loadedConfigHeightMultiplierField;
        configStraveMultiplierField = loadedConfigStraveMultiplierField;
        simulationInitMethod = loadedSimulationInitMethod;
        simulationEmptyMethod = loadedSimulationEmptyMethod;
        simulationApplyMovementMethod = loadedSimulationApplyMovementMethod;
        simulationSimulateMethod = loadedSimulationSimulateMethod;
        simulationGetPointsMethod = loadedSimulationGetPointsMethod;
        simulationSetGravityMethod = loadedSimulationSetGravityMethod;
        simulationSetSneakingMethod = loadedSimulationSetSneakingMethod;
        simulationIsSneakingMethod = loadedSimulationIsSneakingMethod;
        simulationSetGravityDirectionMethod = loadedSimulationSetGravityDirectionMethod;
        capePointGetLerpXMethod = loadedCapePointGetLerpXMethod;
        capePointGetLerpYMethod = loadedCapePointGetLerpYMethod;
        capePointGetLerpZMethod = loadedCapePointGetLerpZMethod;
    }

    private WaveyCapesRuntime() {
    }

    static CapeStyleMode getCapeStyle() {
        return switch (getEnumName(readConfigField(configCapeStyleField))) {
            case "BLOCKY" -> CapeStyleMode.BLOCKY;
            default -> CapeStyleMode.SMOOTH;
        };
    }

    static CapeMovementMode getCapeMovement() {
        return switch (getEnumName(readConfigField(configCapeMovementField))) {
            case "BASIC_SIMULATION" -> CapeMovementMode.BASIC_SIMULATION;
            case "BASIC_SIMULATION_3D" -> CapeMovementMode.BASIC_SIMULATION_3D;
            case "DUNGEONS" -> CapeMovementMode.DUNGEONS;
            default -> CapeMovementMode.VANILLA;
        };
    }

    static WindModeValue getWindMode() {
        return switch (getEnumName(readConfigField(configWindModeField))) {
            case "WAVES" -> WindModeValue.WAVES;
            default -> WindModeValue.NONE;
        };
    }

    static Object ensureSimulation(Object currentSimulation) {
        if (!AVAILABLE) {
            return null;
        }
        CapeMovementMode movementMode = getCapeMovement();
        if (movementMode == CapeMovementMode.VANILLA) {
            return null;
        }
        if (currentSimulation != null && !incorrectSimulation(currentSimulation, movementMode)) {
            return currentSimulation;
        }
        return createSimulation(movementMode);
    }

    static boolean initSimulation(Object simulation, int partCount) {
        return simulation != null && Boolean.TRUE.equals(invoke(simulationInitMethod, simulation, partCount));
    }

    static boolean isSimulationEmpty(Object simulation) {
        return simulation == null || Boolean.TRUE.equals(invoke(simulationEmptyMethod, simulation));
    }

    static void nudgeSimulation(Object simulation) {
        if (simulation == null) {
            return;
        }
        applyMovement(simulation, 1.0F, 1.0F, 0.0F);
    }

    static void simulate(Object simulation, HeroCapeDelegate delegate) {
        if (!AVAILABLE || simulation == null || isSimulationEmpty(simulation)) {
            return;
        }

        double cloakXOffset = delegate.getXCloak() - delegate.getX();
        double cloakZOffset = delegate.getZCloak() - delegate.getZ();
        float bodyRotation = delegate.getYBodyRot();
        double sin = Mth.sin(bodyRotation * 0.017453292F);
        double cos = -Mth.cos(bodyRotation * 0.017453292F);

        float heightMultiplier = getIntConfig(configHeightMultiplierField, 6);
        float straveMultiplier = getIntConfig(configStraveMultiplierField, 2);
        if (delegate.isUnderWater()) {
            heightMultiplier *= 2.0F;
        }

        double fallHack = Mth.clamp((delegate.getYo() - delegate.getY()) * 10.0D, 0.0D, 1.0D);
        setGravity(simulation, delegate.isUnderWater()
                ? getIntConfig(configGravityField, 25) / 10.0F
                : getIntConfig(configGravityField, 25));

        double forwardMovement = cloakXOffset * sin + cloakZOffset * cos + fallHack;
        if (delegate.isCrouching() && !isSneaking(simulation)) {
            forwardMovement += 3.0D;
        }

        double verticalMovement = (delegate.getY() - delegate.getYo()) * heightMultiplier;
        if (delegate.isCrouching() && !isSneaking(simulation)) {
            verticalMovement += 1.0D;
        }

        float deltaX = (float) (delegate.getX() - delegate.getXo());
        float deltaZ = (float) (delegate.getZ() - delegate.getZo());
        float yawRadians = (float) Math.toRadians(-delegate.getYRot());
        float rotatedDeltaX = Mth.cos(yawRadians) * deltaX - Mth.sin(yawRadians) * deltaZ;
        double sidewaysMovement = -rotatedDeltaX * straveMultiplier;

        setSneaking(simulation, delegate.isCrouching());

        Object gravityDirection = newVector3(0.0F, -1.0F, 0.0F);
        Object movement = newVector3((float) forwardMovement, (float) verticalMovement, (float) sidewaysMovement);
        if (gravityDirection == null || movement == null) {
            return;
        }

        if (delegate.isVisuallySwimming()) {
            float swimmingRotation = delegate.getXRot() + 90.0F;
            rotateVector(gravityDirection, swimmingRotation);
            rotateVector(movement, swimmingRotation);
        }

        invoke(simulationSetGravityDirectionMethod, simulation, gravityDirection);
        movement = applyModAnimations(delegate, movement);
        invoke(simulationApplyMovementMethod, simulation, movement);
        invoke(simulationSimulateMethod, simulation);
    }

    static Point getPoint(Object simulation, int part, float partialTick) {
        Object point = getSimulationPoint(simulation, part);
        if (point == null) {
            return new Point(0.0F, 0.0F, 0.0F);
        }
        return new Point(
                getPointValue(point, capePointGetLerpXMethod, partialTick),
                getPointValue(point, capePointGetLerpYMethod, partialTick),
                getPointValue(point, capePointGetLerpZMethod, partialTick)
        );
    }

    private static Object createSimulation(CapeMovementMode movementMode) {
        String className = switch (movementMode) {
            case BASIC_SIMULATION -> "dev.tr7zw.waveycapes.versionless.sim.StickSimulation";
            case BASIC_SIMULATION_3D -> "dev.tr7zw.waveycapes.versionless.sim.StickSimulation3d";
            case DUNGEONS -> "dev.tr7zw.waveycapes.versionless.sim.StickSimulationDungeons";
            default -> null;
        };
        if (className == null) {
            return null;
        }
        try {
            return Class.forName(className, true, WaveyCapesRuntime.class.getClassLoader()).getConstructor().newInstance();
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static boolean incorrectSimulation(Object simulation, CapeMovementMode movementMode) {
        String expectedName = switch (movementMode) {
            case BASIC_SIMULATION -> "StickSimulation";
            case BASIC_SIMULATION_3D -> "StickSimulation3d";
            case DUNGEONS -> "StickSimulationDungeons";
            default -> null;
        };
        return expectedName != null && !simulation.getClass().getSimpleName().equals(expectedName);
    }

    private static void applyMovement(Object simulation, float x, float y, float z) {
        Object movement = newVector3(x, y, z);
        if (movement == null) {
            return;
        }
        invoke(simulationApplyMovementMethod, simulation, movement);
    }

    private static void setGravity(Object simulation, float gravity) {
        invoke(simulationSetGravityMethod, simulation, gravity);
    }

    private static boolean isSneaking(Object simulation) {
        return Boolean.TRUE.equals(invoke(simulationIsSneakingMethod, simulation));
    }

    private static void setSneaking(Object simulation, boolean sneaking) {
        invoke(simulationSetSneakingMethod, simulation, sneaking);
    }

    private static Object applyModAnimations(HeroCapeDelegate delegate, Object movement) {
        Object modBaseInstance = invoke(modBaseGetInstanceMethod, null);
        if (modBaseInstance == null || !modBaseClass.isInstance(modBaseInstance)) {
            return movement;
        }

        Object playerProxy = Proxy.newProxyInstance(
                minecraftPlayerClass.getClassLoader(),
                new Class<?>[]{minecraftPlayerClass},
                (proxy, method, args) -> switch (method.getName()) {
                    case "isVisuallySwimming" -> delegate.isVisuallySwimming();
                    case "getXRot" -> delegate.getXRot();
                    case "isCrouching" -> delegate.isCrouching();
                    case "getY" -> delegate.getY();
                    case "getYRot" -> delegate.getYRot();
                    case "getZ" -> delegate.getZ();
                    case "getX" -> delegate.getX();
                    case "isUnderWater" -> delegate.isUnderWater();
                    case "getXCloak" -> delegate.getXCloak();
                    case "getZCloak" -> delegate.getZCloak();
                    case "getYBodyRotO" -> delegate.getYBodyRotO();
                    case "getYBodyRot" -> delegate.getYBodyRot();
                    case "getYo" -> delegate.getYo();
                    case "getXo" -> delegate.getXo();
                    case "getZo" -> delegate.getZo();
                    case "toString" -> "HeroCapeDelegateProxy";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> args != null && args.length > 0 && proxy == args[0];
                    default -> null;
                }
        );

        Object animated = invoke(modBaseApplyModAnimationsMethod, modBaseInstance, playerProxy, movement);
        return animated != null ? animated : movement;
    }

    private static void rotateVector(Object vector, float degrees) {
        invoke(vectorRotateDegreesMethod, vector, degrees);
    }

    private static Object newVector3(float x, float y, float z) {
        if (vector3Constructor == null) {
            return null;
        }
        try {
            return vector3Constructor.newInstance(x, y, z);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Object getSimulationPoint(Object simulation, int part) {
        Object pointsObject = invoke(simulationGetPointsMethod, simulation);
        if (!(pointsObject instanceof List<?> points) || part < 0 || part >= points.size()) {
            return null;
        }
        return points.get(part);
    }

    private static float getPointValue(Object point, Method method, float partialTick) {
        Object result = invoke(method, point, partialTick);
        return result instanceof Float value ? value : 0.0F;
    }

    private static Object readConfigField(Field field) {
        if (!AVAILABLE || modBaseConfigField == null || field == null) {
            return null;
        }
        try {
            Object config = modBaseConfigField.get(null);
            return config != null ? field.get(config) : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static int getIntConfig(Field field, int fallback) {
        Object value = readConfigField(field);
        return value instanceof Integer integer ? integer : fallback;
    }

    private static String getEnumName(Object value) {
        return value instanceof Enum<?> enumValue ? enumValue.name() : "";
    }

    private static Object invoke(Method method, Object target, Object... args) {
        if (method == null) {
            return null;
        }
        try {
            return method.invoke(target, args);
        } catch (Throwable ignored) {
            return null;
        }
    }
}
