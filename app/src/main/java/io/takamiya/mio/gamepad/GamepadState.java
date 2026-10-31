package io.takamiya.mio.gamepad;

/**
 * 虚拟手柄引擎状态。
 */
public enum GamepadState {
    /** 未初始化 / 未使用 */
    UNINITIALIZED,
    /** uinput 虚拟手柄已创建并可用 */
    OPEN,
    /** uinput 不可用（无权限 / daemon 未绑定） */
    UINPUT_UNAVAILABLE,
    /** 降级为 SOURCE_GAMEPAD 按键注入 */
    FALLBACK_KEY_INJECTION
}