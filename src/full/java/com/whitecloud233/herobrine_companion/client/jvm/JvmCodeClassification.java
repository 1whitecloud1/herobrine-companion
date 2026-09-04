package com.whitecloud233.herobrine_companion.client.jvm;

/**
 * jvm_code_skill 方法体的安全分级（单一职责：表达一次静态安全扫描的结论）。
 *
 * <p>{@link #SAFE} —— 未命中任何危险标记，可免确认自动执行；
 * {@link #LIFECYCLE} —— 命中会<b>关闭 / 退出游戏</b>的生命周期操作（即使其余代码"只碰游戏 API"），
 * 也必须弹确认并向玩家用大白话说明后果；
 * {@link #DANGEROUS} —— 命中文件 / 网络 / 进程 / 反射等主机级危险标记，必须弹确认。</p>
 */
public enum JvmCodeClassification {
    SAFE,
    LIFECYCLE,
    DANGEROUS
}
