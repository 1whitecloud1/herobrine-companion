package com.whitecloud233.herobrine_companion.client.render;

import net.minecraft.client.Minecraft;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import java.lang.reflect.Method;

public class IrisPatcher {

    private boolean isPatched = false;      // 标记是否由我们接管了光影
    private boolean originalState = true;   // 记录进入维度前的状态
    private int tickCounter = 0;
    private int cooldown = 0;               // 冷却计时器

    public IrisPatcher() {
    }

    @SubscribeEvent
    public void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.isPaused()) return;

        // 冷却中...
        if (cooldown > 0) {
            cooldown--;
            return;
        }

        tickCounter++;
        if (tickCounter < 10) return; // 每0.5秒检查一次
        tickCounter = 0;

        String currentDimID = mc.level.dimension().location().toString();

        // ================= 进入目标维度 =================
        if (currentDimID.equals("herobrine_companion:end_ring_dimension")) {
            try {
                // 1. 检查当前是否开启了光影
                boolean isEnabled = checkShadersEnabled();

                if (isEnabled) {
                    // 记录一下：原来是开着的，所以我才关的
                    if (!isPatched) {
                        originalState = true;
                        isPatched = true;
                    }

                    // 2. 执行关闭流程
                    applyShaderState(false);

                    // 3. 设置长冷却 (Iris重载需要时间)
                    cooldown = 40;
                } else {
                    // 已经是关的，标记一下“我在监控中”
                    if (!isPatched) isPatched = true;
                }
            } catch (Exception e) {
                cooldown = 60;
            }
        }

        // ================= 离开目标维度 =================
        else if (isPatched && !currentDimID.equals("herobrine_companion:end_ring_dimension")) {
            try {
                // 只有当原来是开着的时候，我们才重新开启
                if (originalState) {
                    applyShaderState(true);
                }
            } catch (Exception e) {
                // ignore
            } finally {
                // 无论成功失败，都解除接管状态
                isPatched = false;
                cooldown = 40;
            }
        }
    }

    // ================== 核心反射逻辑 ==================

    /**
     * 获取真正的 LLMConfig 对象
     */
    private Object getIrisConfig() throws Exception {
        Class<?> mainClass = Class.forName("net.irisshaders.iris.Iris");
        Method getMethod = mainClass.getMethod("getIrisConfig");
        Object config = getMethod.invoke(null);
        if (config == null) throw new NullPointerException("Iris LLMConfig 为空");
        return config;
    }

    /**
     * 检查光影是否开启 (使用 areShadersEnabled 方法)
     */
    private boolean checkShadersEnabled() throws Exception {
        Object config = getIrisConfig();
        // 根据你的日志: -> boolean areShadersEnabled[]
        Method method = config.getClass().getMethod("areShadersEnabled");
        return (boolean) method.invoke(config);
    }

    /**
     * 设置状态 -> 保存 -> 重载
     */
    private void applyShaderState(boolean enable) throws Exception {
        Object config = getIrisConfig();
        Class<?> configClass = config.getClass();

        // 1. 设置状态 (使用 setShadersEnabled 方法)
        // 根据你的日志: -> void setShadersEnabled[boolean]
        Method setMethod = configClass.getMethod("setShadersEnabled", boolean.class);
        setMethod.invoke(config, enable);

        // 2. 保存到硬盘 (使用 save 方法)
        // 根据你的日志: -> void save[]
        Method saveMethod = configClass.getMethod("save");
        saveMethod.invoke(config);

        // 3. 触发重载
        Class<?> mainClass = Class.forName("net.irisshaders.iris.Iris");
        Method reloadMethod = mainClass.getMethod("reload");
        reloadMethod.invoke(null);
    }
}
