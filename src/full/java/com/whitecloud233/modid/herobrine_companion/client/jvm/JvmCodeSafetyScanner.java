package com.whitecloud233.modid.herobrine_companion.client.jvm;

import java.util.Locale;

/**
 * JVM 代码静态安全扫描（单一职责：对一段 jvm_code_skill 方法体做安全分级）。
 *
 * <p>这是<b>启发式</b>而非沙箱：它按词法查找引用主机资源的危险标记（文件 / 网络 / 系统命令 / 反射 /
 * 剪贴板等），以及会关闭 / 退出游戏的游戏生命周期标记。未命中任何标记 → 判定 {@link JvmCodeClassification#SAFE}，
 * 可免确认自动执行；命中生命周期标记 → {@link JvmCodeClassification#LIFECYCLE}；命中主机级标记 →
 * {@link JvmCodeClassification#DANGEROUS}。后两者都必须弹确认框。</p>
 *
 * <p>方向是<b>fail-closed</b>：宁可误报（多弹一次确认）也不放行危险代码。注意它<b>不是绝对安全</b>
 * ——代码跑在游戏进程里，游戏自身方法也可能间接碰文件，静态扫描总有绕过空间。</p>
 *
 * <p>生命周期标记专门解决这类事故：AI 生成的"召唤末影龙"等代码可能顺手调用
 * {@code Minecraft.getInstance().stop()} / {@code Window.close()} 把整个游戏正常退出（表现为
 * "Stopping!" 后静默退出，而非崩溃）。这类调用只碰游戏 API、普通玩家却看不出后果，所以<b>永远不走白名单
 * 自动放行</b>，命中即要求确认并向玩家解释。</p>
 */
final class JvmCodeSafetyScanner {

    /** 主机级危险标记（小写匹配；命中任意一个 → DANGEROUS，需要玩家确认）。 */
    private static final String[] DANGEROUS_MARKERS = {
            // 文件 / NIO
            "java.io.", "java.nio.", "fileinputstream", "fileoutputstream", "filereader", "filewriter",
            "randomaccessfile", "filechannel", "directorystream", "files.", "paths.", "path.", "new file",
            // 网络
            "java.net.", "socket", "serversocket", "httpurlconnection", "urlconnection", "datagramsocket",
            "inetaddress", "httpclient", "websocket",
            // 系统命令 / 进程
            "runtime", "processbuilder", "process.", "exec(", "getruntime(", "waitfor(", "shutdown(",
            "destroyforcibly", "deleteonexit",
            // 系统属性 / 退出 / 环境
            "system.exit", "system.setproperty", "system.setsecuritymanager", "system.getproperty", "system.getenv",
            // 反射 / 类加载
            "java.lang.reflect.", "java.lang.invoke.", "class.forname", "classloader", "getdeclaredmethod",
            "getdeclaredfield", "getdeclaredconstructor", "setaccessible", "methodhandle", "sun.", "com.sun.",
            // 剪贴板 / AWT
            "java.awt.", "javax.", "toolkit", "clipboard", "stringselection"
    };

    /**
     * 游戏生命周期标记（小写匹配；命中任意一个 → LIFECYCLE，即使其余代码"安全"也必须确认）。
     * 覆盖关闭 / 退出游戏与关闭世界的常见入口：Minecraft.stop()、Window.close()、
     * MinecraftServer.halt() / stopServer() / stopRunning() 等。刻意选"方法名+左括号"而非裸 "stop("，
     * 避免把 getNavigation().stop() / soundManager.stop() 等正常游戏 API 误伤。
     */
    private static final String[] LIFECYCLE_MARKERS = {
            // Minecraft 实例的 stop / close
            "getinstance().stop(", "minecraft.stop(", "mc.stop(",
            "getwindow().stop(", "window.stop(",
            "getwindow().close(", "window.close(",
            // 服务端停止（会关闭世界 / 回到标题）
            "halt(", "stopserver(", "stoprunning("
    };

    private JvmCodeSafetyScanner() {
    }

    /**
     * 对方法体做安全分级。
     *
     * @param methodBody run() 的方法体
     * @return {@link JvmCodeClassification#SAFE} / {@link JvmCodeClassification#LIFECYCLE} /
     *         {@link JvmCodeClassification#DANGEROUS}
     */
    static JvmCodeClassification scan(String methodBody) {
        if (methodBody == null || methodBody.isBlank()) {
            return JvmCodeClassification.SAFE;
        }
        String normalized = methodBody.toLowerCase(Locale.ROOT);
        if (containsAnyMarker(normalized, DANGEROUS_MARKERS)) {
            return JvmCodeClassification.DANGEROUS;
        }
        if (containsAnyMarker(normalized, LIFECYCLE_MARKERS)) {
            return JvmCodeClassification.LIFECYCLE;
        }
        return JvmCodeClassification.SAFE;
    }

    private static boolean containsAnyMarker(String normalized, String[] markers) {
        for (String marker : markers) {
            if (normalized.contains(marker)) {
                return true;
            }
        }
        return false;
    }
}
