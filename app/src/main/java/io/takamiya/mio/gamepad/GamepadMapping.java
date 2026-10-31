package io.takamiya.mio.gamepad;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 配置名 → GamepadButton：支持 "GAMEPAD_A"、"A" 两种写法，向前兼容历史扩展。
 */
public final class GamepadMapping {

    private static final Map<String, GamepadButton> NAME_MAP = new LinkedHashMap<>();

    static {
        for (GamepadButton b : GamepadButton.values()) {
            NAME_MAP.put(b.getName(), b);
            NAME_MAP.put("GAMEPAD_" + b.getName(), b);
        }
    }

    private GamepadMapping() {}

    public static GamepadButton getButton(String name) {
        if (name == null) return null;
        return NAME_MAP.get(name.toUpperCase());
    }

    public static GamepadButton getButtonByAndroidKeyCode(int keyCode) {
        for (GamepadButton b : GamepadButton.values()) {
            if (b.getAndroidKeyCode() == keyCode) return b;
        }
        return null;
    }

    public static String[] getAllConfigNames() {
        java.util.List<String> names = new ArrayList<>();
        for (GamepadButton b : GamepadButton.values()) {
            names.add("GAMEPAD_" + b.getName());
        }
        Collections.sort(names);
        return names.toArray(new String[0]);
    }
}