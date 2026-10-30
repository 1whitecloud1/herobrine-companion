package com.whitecloud233.modid.herobrine_companion.compat.screens;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.logging.LogUtils;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import org.slf4j.Logger;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * 用「模拟一次原版按键」或「延迟执行一段动作」来打开第三方模组的界面。
 *
 * <h2>为什么时装工坊走按键模拟，而不是直接调用它的 API</h2>
 * <ul>
 *   <li>AW 打开玩家时装栏的入口
 *       {@code ClientMenuHandler.sendOpenWardrobePacket()} <b>只有 3.4.0+ 才有</b>，
 *       3.2.7（本工程编译期依赖的版本）里该方法不存在。
 *       直接调用会在老版本上抛 {@code NoSuchMethodError}。</li>
 *   <li>把编译依赖升到 3.4.0 又会让 3.2.7 环境出问题 —— 这正是之前
 *       {@code OpenResourceLocation} 导致整个整合包崩溃的同一类坑。</li>
 *   <li>按名字查找原版 {@link KeyMapping} 只用原版类型，与 AW 版本完全解耦。</li>
 * </ul>
 *
 * <h2>两种按键消费机制都要照顾</h2>
 * <ul>
 *   <li><b>原版风格</b>：{@code consumeClick()} 读内部的 {@code clickCount}
 *       ⇒ 需要 {@link KeyMapping#click} 去给计数加一。</li>
 *   <li><b>时装工坊</b>：3.4.0 覆写了 {@code consumeClick()}，判定条件是
 *       {@code canConsumeClick && isDown()}，而 {@code canConsumeClick} 只在
 *       <b>松开</b>（{@code setDown(false)}）时才置位，轮询发生在每渲染帧
 *       （{@code RenderFrameEvent.Post}）⇒ 必须还原真实的
 *       「先松一次布防 → 按下 → 稍后松开」序列；
 *       3.2.7 则委托原版、读 {@code clickCount}。
 *       两个版本机制不同，所以两种动作都做。</li>
 * </ul>
 * 两种动作互相无害：覆写过 {@code consumeClick()} 的按键不读 {@code clickCount}，
 * 反向也只是一次多余的 {@code setDown}。
 *
 * <h2>为什么要先关掉当前界面</h2>
 * 第三方模组普遍会在「已经有界面打开」时跳过按键处理，所以先 {@code setScreen(null)}
 * 并把真正动作推迟到下一个客户端 tick 执行。顺带一提，原版
 * {@code Minecraft.setScreen} 内部会调用 {@code KeyMapping.releaseAll()}，
 * 这恰好帮时装工坊的按键完成了「布防」。
 */
public final class ExternalScreenShortcuts {

    /**
     * 时装工坊：打开玩家时装栏的按键名。
     *
     * <p>实测（实机日志）它的运行时名字是<b>复数</b>的 {@code keys.} 前缀，
     * 而其语言文件里写的是 {@code key.} 前缀 —— 两者不一致，所以两段都保留。
     */
    public static final String AW_OPEN_WARDROBE = "keys.armourers_workshop.open-wardrobe";

    /** 时装工坊按键的模糊匹配片段（键名是运行时拼的，分隔符可能随版本变化）。 */
    public static final String AW_OPEN_WARDROBE_FRAGMENT = "open-wardrobe";

    /** Accessories：打开饰品栏（键名 key.open_accessories_screen）。 */
    public static final String ACCESSORIES_OPEN_SCREEN = "key.open_accessories_screen";

    /** Accessories 按键的模糊匹配片段。 */
    public static final String ACCESSORIES_OPEN_SCREEN_FRAGMENT = "open_accessories_screen";

    private static final Logger LOGGER = LogUtils.getLogger();

    /** 需要在下一个客户端 tick 执行的动作（此时界面已关闭）。 */
    private static final Deque<Runnable> PENDING_ACTIONS = new ArrayDeque<>();

    /** 已按下、等待松开的按键。 */
    private static final List<KeyMapping> HELD_KEYS = new ArrayList<>();
    /** 与 {@link #HELD_KEYS} 一一对应的剩余保持 tick 数。 */
    private static final List<Integer> HELD_TICKS = new ArrayList<>();

    /**
     * 按住多少 tick 后才松开。必须大于 1：第三方模组按自己的节奏轮询
     * （时装工坊是每渲染帧），只保持一瞬有可能被整帧跳过。
     */
    private static final int HOLD_TICKS = 3;

    private ExternalScreenShortcuts() {
    }

    /**
     * 关闭当前界面，并在下一个客户端 tick 模拟按下指定翻译键对应的按键。
     *
     * @param translationKey 按键的翻译键（{@link KeyMapping#getName()}）
     * @param nameFragment   精确匹配失败时用于模糊匹配的片段。第三方模组在不同版本里
     *                       可能改变键名里的分隔符（{@code _} / {@code -} / {@code :}），
     *                       归一化后按片段包含判断，避免一个版本能用、另一个版本点不动。
     * @return 是否找到了该按键（模组未安装或按键改名时为 {@code false}）
     */
    public static boolean pressKeyAfterClosingScreen(String translationKey, String nameFragment) {
        KeyMapping mapping = findKeyMapping(translationKey, nameFragment);
        if (mapping == null) {
            LOGGER.info("[Herobrine Companion] 未找到按键 {}（片段 {}），对应模组可能未安装或已改名。",
                    translationKey, nameFragment);
            return false;
        }
        return runAfterClosingScreen(() -> pressNow(mapping));
    }

    /**
     * 关闭当前界面，并在下一个客户端 tick 执行给定动作。
     *
     * @return 是否已入队
     */
    public static boolean runAfterClosingScreen(Runnable action) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) {
            return false;
        }
        if (mc.screen != null) {
            mc.setScreen(null);
        }
        PENDING_ACTIONS.addLast(action);
        return true;
    }

    /** 客户端 tick 调用：先执行排队的动作，再维护模拟按键的按住/松开。 */
    public static void tick() {
        Runnable action;
        while ((action = PENDING_ACTIONS.pollFirst()) != null) {
            try {
                action.run();
            } catch (Throwable t) {
                LOGGER.warn("[Herobrine Companion] 打开第三方界面时出错。", t);
            }
        }

        for (int i = HELD_KEYS.size() - 1; i >= 0; i--) {
            int remaining = HELD_TICKS.get(i) - 1;
            if (remaining > 0) {
                HELD_TICKS.set(i, remaining);
                continue;
            }
            safeSetDown(HELD_KEYS.get(i), false);
            HELD_KEYS.remove(i);
            HELD_TICKS.remove(i);
        }
    }

    /** 松开所有仍处于模拟按住状态的按键（界面卸载/退出时兜底）。 */
    public static void releaseAll() {
        for (KeyMapping mapping : HELD_KEYS) {
            safeSetDown(mapping, false);
        }
        HELD_KEYS.clear();
        HELD_TICKS.clear();
        PENDING_ACTIONS.clear();
    }

    private static void pressNow(KeyMapping mapping) {
        Minecraft mc = Minecraft.getInstance();

        // ① 先松一次：给「松开才布防」的实现（时装工坊 3.4.0）布防
        safeSetDown(mapping, false);
        // ② 按下：让 isDown() 为真
        safeSetDown(mapping, true);
        // ③ 照顾读 clickCount 的实现（时装工坊 3.2.7 等）。
        //    注意：原版 KeyMapping.click(key) 会把「绑定到同一个键的所有按键」全部 +1，
        //    键位冲突时会连带触发别的模组（1.21.1 上实测就是这样弹出了无关的社交界面）。
        //    所以只有确认该键值被这个按键独占了，才做第三步。
        if (isUniquelyBound(mc, mapping)) {
            try {
                KeyMapping.click(mapping.getKey());
            } catch (Throwable t) {
                LOGGER.warn("[Herobrine Companion] 模拟按键点击计数失败：{}", mapping.getName(), t);
            }
        } else {
            LOGGER.info("[Herobrine Companion] 按键 {} 与其它按键共用同一键值，"
                    + "跳过 clickCount 模拟以免误触发其它模组。", mapping.getName());
        }

        HELD_KEYS.add(mapping);
        HELD_TICKS.add(HOLD_TICKS);
        LOGGER.info("[Herobrine Companion] 已模拟按下按键 {}。", mapping.getName());
    }

    /** 该按键当前绑定的键值是否被它独占（独占时 {@code KeyMapping.click} 才安全）。 */
    private static boolean isUniquelyBound(Minecraft mc, KeyMapping mapping) {
        if (mc == null || mc.options == null) {
            return false;
        }
        InputConstants.Key key = mapping.getKey();
        if (key == null || InputConstants.UNKNOWN.equals(key)) {
            return false;
        }
        for (KeyMapping other : mc.options.keyMappings) {
            if (other != mapping && key.equals(other.getKey())) {
                return false;
            }
        }
        return true;
    }

    private static void safeSetDown(KeyMapping mapping, boolean down) {
        try {
            mapping.setDown(down);
        } catch (Throwable t) {
            LOGGER.warn("[Herobrine Companion] 设置按键状态失败：{}", mapping.getName(), t);
        }
    }

    private static KeyMapping findKeyMapping(String translationKey, String nameFragment) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.options == null || translationKey == null) {
            return null;
        }

        // 1) 精确匹配：正常情况下走这里
        for (KeyMapping mapping : mc.options.keyMappings) {
            if (translationKey.equals(mapping.getName())) {
                return mapping;
            }
        }

        // 2) 归一化模糊匹配：兜底那些改了分隔符/命名空间的版本
        if (nameFragment == null || nameFragment.isEmpty()) {
            return null;
        }
        String wanted = normalize(nameFragment);
        for (KeyMapping mapping : mc.options.keyMappings) {
            String name = mapping.getName();
            if (name != null && normalize(name).contains(wanted)) {
                LOGGER.info("[Herobrine Companion] 按键 {} 未精确命中，回退匹配到 {}。", translationKey, name);
                return mapping;
            }
        }
        return null;
    }

    /** 去掉大小写与常见分隔符差异，便于跨版本比对键名。 */
    private static String normalize(String name) {
        StringBuilder out = new StringBuilder(name.length());
        for (int i = 0; i < name.length(); i++) {
            char c = Character.toLowerCase(name.charAt(i));
            if (c == '_' || c == '-' || c == ':' || c == '.' || c == ' ') {
                continue;
            }
            out.append(c);
        }
        return out.toString();
    }
}
