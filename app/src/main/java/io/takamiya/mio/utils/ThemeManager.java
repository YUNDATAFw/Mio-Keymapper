package io.takamiya.mio.utils;

import androidx.appcompat.app.AppCompatDelegate;

import cn.panda.utils.library.AppLogger;
import cn.panda.utils.library.Registry.Registry;

public class ThemeManager {
    private static final String TAG = "ThemeManager";

    public static final int MODE_SYSTEM = 0;
    public static final int MODE_DARK = 1;
    public static final int MODE_LIGHT = 2;

    private ThemeManager() {
    }

    public static int getMode(Registry reg) {
        if (reg == null || !reg.exists(DataManage.Tag.APP_CONFIG_THEME_MODE.getValue())) {
            return MODE_SYSTEM;
        }
        try {
            int mode = reg.getInt(DataManage.Tag.APP_CONFIG_THEME_MODE.getValue());
            if (mode != MODE_DARK && mode != MODE_LIGHT) {
                return MODE_SYSTEM;
            }
            return mode;
        } catch (Exception e) {
            AppLogger.w(TAG, "failed to load theme mode: " + e.getMessage());
            return MODE_SYSTEM;
        }
    }

    public static int toNightMode(int mode) {
        switch (mode) {
            case MODE_DARK:
                return AppCompatDelegate.MODE_NIGHT_YES;
            case MODE_LIGHT:
                return AppCompatDelegate.MODE_NIGHT_NO;
            default:
                return AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
        }
    }

    public static void apply() {
        AppCompatDelegate.setDefaultNightMode(toNightMode(getMode(RegistryUtils.get())));
    }

    public static void setMode(int mode) {
        Registry reg = RegistryUtils.get();
        if (reg != null) {
            try {
                reg.set(DataManage.Tag.APP_CONFIG_THEME_MODE.getValue(), mode);
                reg.saveAsync();
            } catch (Exception e) {
                AppLogger.e(TAG, "save theme mode failed: " + e.getMessage(), e);
            }
        }
        AppCompatDelegate.setDefaultNightMode(toNightMode(mode));
    }
}
