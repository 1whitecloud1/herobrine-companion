package com.whitecloud233.modid.herobrine_companion.client.render;

import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.lang.reflect.Method;

@OnlyIn(Dist.CLIENT)
public class IrisPatcher {

    private static boolean isPatched = false;
    private static boolean originalState = true;
    private static int tickCounter = 0;
    private static int cooldown = 0;

    public IrisPatcher() {
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.isPaused()) return;

        if (cooldown > 0) {
            cooldown--;
            return;
        }

        tickCounter++;
        if (tickCounter < 10) return;
        tickCounter = 0;

        String currentDimID = mc.level.dimension().location().toString();

        if (currentDimID.equals("herobrine_companion:end_ring_dimension")) {
            try {
                boolean isEnabled = checkShadersEnabled();

                if (isEnabled) {
                    if (!isPatched) {
                        originalState = true;
                        isPatched = true;
                    }

                    applyShaderState(false);

                    cooldown = 40;
                } else {
                    if (!isPatched) isPatched = true;
                }
            } catch (Exception e) {
                cooldown = 60;
            }
        }
        else if (isPatched && !currentDimID.equals("herobrine_companion:end_ring_dimension")) {
            try {
                if (originalState) {
                    applyShaderState(true);
                }
            } catch (Exception e) {
                // ignore
            } finally {
                isPatched = false;
                cooldown = 40;
            }
        }
    }

    private static Object getIrisConfig() throws Exception {
        Class<?> mainClass = Class.forName("net.irisshaders.iris.Iris");
        Method getMethod = mainClass.getMethod("getIrisConfig");
        Object config = getMethod.invoke(null);
        if (config == null) throw new NullPointerException("Iris AIConfig 为空");
        return config;
    }

    private static boolean checkShadersEnabled() throws Exception {
        Object config = getIrisConfig();
        Method method = config.getClass().getMethod("areShadersEnabled");
        return (boolean) method.invoke(config);
    }

    private static void applyShaderState(boolean enable) throws Exception {
        Object config = getIrisConfig();
        Class<?> configClass = config.getClass();

        Method setMethod = configClass.getMethod("setShadersEnabled", boolean.class);
        setMethod.invoke(config, enable);

        Method saveMethod = configClass.getMethod("save");
        saveMethod.invoke(config);

        Class<?> mainClass = Class.forName("net.irisshaders.iris.Iris");
        Method reloadMethod = mainClass.getMethod("reload");
        reloadMethod.invoke(null);
    }
}
