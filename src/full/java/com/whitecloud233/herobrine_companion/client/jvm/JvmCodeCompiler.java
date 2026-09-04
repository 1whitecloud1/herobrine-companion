package com.whitecloud233.herobrine_companion.client.jvm;

/**
 * 编译抽象（DIP 接缝）：把 LLM 提交的 run() 方法体编译并加载为可执行的契约实例。
 * 实现负责封装 JDK 编译器的全部细节（javax.tools）。
 */
public interface JvmCodeCompiler {
    /** 当前运行环境是否具备编译能力（JDK 而非 JRE）。 */
    boolean isAvailable();

    /**
     * 编译方法体。
     *
     * @param methodBody run() 的方法体（不含类声明与包声明）
     * @return 成功携带可执行实例；失败携带格式化后的编译器诊断文本
     */
    JvmCodeCompileResult compile(String methodBody);

    /** 编译结果值类型：要么 action 非空，要么 error 携带诊断。 */
    record JvmCodeCompileResult(JvmCodeAction action, String error) {
        public boolean ok() {
            return this.action != null;
        }
    }
}
