package io.takamiya.mio.gamepad;

import android.view.KeyEvent;

/**
 * 手柄逻辑键：配置名 + Android KEYCODE_BUTTON_* + Linux uinput 键码(BTN_*)。
 * 键值语义统一：uinput 通道用 linuxCode，降级注入通道用 androidKeyCode。
 */
public enum GamepadButton {

    A("A", "A键", KeyEvent.KEYCODE_BUTTON_A, 304),
    B("B", "B键", KeyEvent.KEYCODE_BUTTON_B, 305),
    X("X", "X键", KeyEvent.KEYCODE_BUTTON_X, 307),
    Y("Y", "Y键", KeyEvent.KEYCODE_BUTTON_Y, 308),
    LB("LB", "LB肩键", KeyEvent.KEYCODE_BUTTON_L1, 310),
    RB("RB", "RB肩键", KeyEvent.KEYCODE_BUTTON_R1, 311),
    LT("LT", "LT扳机", KeyEvent.KEYCODE_BUTTON_L2, 312),
    RT("RT", "RT扳机", KeyEvent.KEYCODE_BUTTON_R2, 313),
    SELECT("SELECT", "选择", KeyEvent.KEYCODE_BUTTON_SELECT, 314),
    START("START", "开始", KeyEvent.KEYCODE_BUTTON_START, 315),
    LS("LS", "左摇杆按下", KeyEvent.KEYCODE_BUTTON_THUMBL, 317),
    RS("RS", "右摇杆按下", KeyEvent.KEYCODE_BUTTON_THUMBR, 318),
    DPAD_UP("DPAD_UP", "十字键上", KeyEvent.KEYCODE_DPAD_UP, 103),
    DPAD_DOWN("DPAD_DOWN", "十字键下", KeyEvent.KEYCODE_DPAD_DOWN, 108),
    DPAD_LEFT("DPAD_LEFT", "十字键左", KeyEvent.KEYCODE_DPAD_LEFT, 105),
    DPAD_RIGHT("DPAD_RIGHT", "十字键右", KeyEvent.KEYCODE_DPAD_RIGHT, 106);

    private final String name;
    private final String displayName;
    private final int androidKeyCode;
    private final int linuxCode;

    GamepadButton(String name, String displayName, int androidKeyCode, int linuxCode) {
        this.name = name;
        this.displayName = displayName;
        this.androidKeyCode = androidKeyCode;
        this.linuxCode = linuxCode;
    }

    public String getName() {
        return name;
    }

    public String getDisplayName() {
        return displayName;
    }

    public int getAndroidKeyCode() {
        return androidKeyCode;
    }

    public int getLinuxCode() {
        return linuxCode;
    }

    public static GamepadButton fromName(String value) {
        if (value == null) return null;
        String upper = value.toUpperCase().replace("GAMEPAD_", "");
        for (GamepadButton b : values()) {
            if (b.name.equals(upper)) return b;
        }
        return null;
    }
}