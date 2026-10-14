package com.stupidbeauty.hxjsonvalidator;

/**
 * HxJsonValidator
 *
 * 基于有限状态自动机（FSM）的 JSON 语法校验器。
 * 设计目标：
 *  - 按字符位置（line/column/offset）精确定位错误
 *  - 流式可中断，可作为 Android 工具链组件独立发布
 *  - 脱离 Android Framework 运行（便于 JVM 单元测试）
 *
 * 当前阶段：脚手架占位，业务逻辑待 #919489457328（FSM 技术路径调研）完成后实现。
 */
public final class HxJsonValidator {
    public static final String VERSION = "2026.10.14";

    private HxJsonValidator() {
        // utility class, no instances
    }

    /** 占位：返回 true 仅用于让脚手架可编译。后续替换为 FSM 主入口。 */
    public static boolean isValid(String json) {
        return json != null && !json.isEmpty();
    }
}
