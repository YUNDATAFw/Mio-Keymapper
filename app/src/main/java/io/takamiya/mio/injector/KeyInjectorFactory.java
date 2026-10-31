package io.takamiya.mio.injector;

import android.content.Context;

import cn.panda.utils.library.AppLogger;

public class KeyInjectorFactory {

    private static final String TAG = "KeyInjectorFactory";

    public static KeyInjector create(KeyInjectorType type, Context context) throws IllegalStateException {
        AppLogger.d(TAG, "create: type=" + type
                + " context=" + (context != null ? context.getPackageName() : "null"));
        switch (type) {
            case SHIZUKU:
                if (!ShizukuKeyInjector.isShizukuRunning()) {
                    AppLogger.e(TAG, "create: SHIZUKU rejected, Shizuku not running");
                    throw new IllegalStateException("Shizuku未运行，请先安装并启动Shizuku");
                }
                if (!ShizukuKeyInjector.isPermissionGranted()) {
                    AppLogger.e(TAG, "create: SHIZUKU rejected, permission not granted");
                    throw new IllegalStateException("Shizuku权限未授予");
                }
                if (!ShizukuKeyInjector.isServiceBound()) {
                    AppLogger.e(TAG, "create: SHIZUKU rejected, service not bound");
                    throw new IllegalStateException("Shizuku服务未连接，请稍后重试");
                }
                AppLogger.d(TAG, "create: returning ShizukuKeyInjector");
                return new ShizukuKeyInjector();

            case GAMEPAD:
                if (!ShizukuKeyInjector.isShizukuRunning()) {
                    AppLogger.e(TAG, "create: GAMEPAD rejected, Shizuku not running");
                    throw new IllegalStateException("Shizuku未运行，游戏手柄需要Shizuku权限");
                }
                if (!ShizukuKeyInjector.isPermissionGranted()) {
                    AppLogger.e(TAG, "create: GAMEPAD rejected, permission not granted");
                    throw new IllegalStateException("Shizuku权限未授予，游戏手柄需要Shizuku权限");
                }
                AppLogger.d(TAG, "create: returning GamepadKeyInjector");
                return new GamepadKeyInjector(context);

            case IME:
                if (!ImeKeyInjector.isImeEnabled(context)) {
                    AppLogger.e(TAG, "create: IME rejected, not enabled");
                    throw new IllegalStateException("输入法未启用，请在系统设置中启用");
                }
                if (!ImeKeyInjector.isImeCurrent(context)) {
                    AppLogger.e(TAG, "create: IME rejected, not current");
                    throw new IllegalStateException("输入法未切换为当前输入法");
                }
                AppLogger.d(TAG, "create: returning ImeKeyInjector");
                return new ImeKeyInjector(context);

            default:
                AppLogger.e(TAG, "create: unknown type " + type);
                throw new IllegalArgumentException("Unknown type: " + type);
        }
    }

    public static KeyInjector createSafe(KeyInjectorType type, Context context) {
        AppLogger.d(TAG, "createSafe: type=" + type);
        try {
            KeyInjector injector = new SafeKeyInjector(create(type, context));
            AppLogger.d(TAG, "createSafe: ok, injector=" + injector.getName());
            return injector;
        } catch (IllegalStateException e) {
            AppLogger.w(TAG, "createSafe: creation failed: " + e.getMessage());
            return null;
        }
    }
}
