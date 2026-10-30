package com.whitecloud233.modid.herobrine_companion.util;

import com.whitecloud233.modid.herobrine_companion.config.Config;
import org.slf4j.Logger;
import org.slf4j.Marker;

/**
 * Debug 日志统一闸门。
 *
 * <p>模组的排障日志（{@code [CookDiag]}、{@code [HeroEFDebug]}、{@code [HeroGift]}、{@code [HeroScythe]}
 * 等）默认全部关闭，只走本类的出口；关闭时连字符串拼接都不会发生，不会污染 {@code latest.log}。</p>
 *
 * <p>开关唯一来源是 common 配置 {@code debugLogEnabled}（默认 {@code false}）。
 * 配置属于 COMMON 类型，服务端与客户端都读得到，因此集成服/专用服的日志行为一致。</p>
 *
 * <p><b>单一职责</b>：本类只回答“这条 debug 日志要不要输出”，不负责分类、分级或落盘。
 * 业务侧仍需自行决定日志内容与去重（例如每秒最多一条）。</p>
 */
public final class DebugLog {

    private DebugLog() {
    }

    /** 开关是否开启。配置尚未加载时按关闭处理。 */
    public static boolean enabled() {
        return Config.debugLogEnabled;
    }

    /** 关闭时立刻返回，避免任何参数求值/字符串拼接开销。 */
    public static void info(Logger logger, String format, Object... args) {
        if (!enabled() || logger == null || !logger.isInfoEnabled()) {
            return;
        }
        logger.info(format, args);
    }

    public static void info(Logger logger, Marker marker, String format, Object... args) {
        if (!enabled() || logger == null || !logger.isInfoEnabled()) {
            return;
        }
        logger.info(marker, format, args);
    }

    public static void info(Logger logger, String message) {
        if (!enabled() || logger == null || !logger.isInfoEnabled()) {
            return;
        }
        logger.info(message);
    }

    public static void debug(Logger logger, String format, Object... args) {
        if (!enabled() || logger == null || !logger.isDebugEnabled()) {
            return;
        }
        logger.debug(format, args);
    }

    public static void warn(Logger logger, String format, Object... args) {
        if (!enabled() || logger == null || !logger.isWarnEnabled()) {
            return;
        }
        logger.warn(format, args);
    }

    /** 供已经自带一次性去重（{@code onceLog}）的调用方判断是否要继续往下拼日志。 */
    public static boolean isEnabled(Logger logger) {
        return enabled() && logger != null && logger.isInfoEnabled();
    }
}
