package io.takamiya.mio.config;

public enum ShortcutEventType {
    REGISTER("注册事件"),
    UNREGISTER("注销事件"),
    KEY_ON("开关开启事件"),
    KEY_OFF("开关关闭事件"),
    KEY_PRESS("快捷键按下事件"),
    KEY_RELEASE("快捷键松开事件"),
    BUTTON_PRESS("按钮按下事件"),
    BUTTON_RELEASE("按钮松开事件"),
    PRESSURE_PLATE_PRESS("压力板按下事件"),
    PRESSURE_PLATE_RELEASE("压力板松开事件"),
    PRESSURE_PLATE_LONG_PRESS("压力板长按事件"),
    ENGINE_START("引擎注册事件"),
    ENGINE_STOP("引擎注销事件"),
    SHORTCUT_BAR_SHOW("页面存在快捷键事件"),
    SHORTCUT_BAR_HIDE("页面无快捷键事件");

    private final String displayName;

    ShortcutEventType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public static ShortcutEventType fromString(String value) {
        if (value == null) return null;
        try {
            return valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
