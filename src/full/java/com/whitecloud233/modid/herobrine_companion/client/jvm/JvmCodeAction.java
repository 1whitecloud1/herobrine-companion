package com.whitecloud233.modid.herobrine_companion.client.jvm;

/**
 * 执行契约：LLM 通过 jvm_code_skill 提交的代码被编译成一个实现该接口的类。
 * run() 在客户端渲染线程上执行，返回一句状态文本。
 */
public interface JvmCodeAction {
    String run() throws Throwable;
}
