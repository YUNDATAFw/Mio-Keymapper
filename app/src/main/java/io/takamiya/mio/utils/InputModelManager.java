package io.takamiya.mio.utils;

import android.content.Context;

import cn.panda.utils.library.AppLogger;
import io.takamiya.mio.injector.KeyInjectorFactory;
import io.takamiya.mio.injector.KeyInjectorType;
import io.takamiya.mio.injector.ShizukuKeyInjector;

public final class InputModelManager {

    public static final int MODEL_IME = 0;
    public static final int MODEL_SHIZUKU = 1;
    public static final int MODEL_GAMEPAD = 2;
    public static final int MODEL_NONE = -1;

    private static final String TAG = "InputModelManager";

    private InputModelManager() {}

    public static boolean isModeAvailable(int model, Context context) {
        if (model == MODEL_SHIZUKU) {
            return ShizukuKeyInjector.isShizukuRunning()
                    && ShizukuKeyInjector.isPermissionGranted();
        }
        try {
            KeyInjectorFactory.create(KeyInjectorType.IME, context);
            return true;
        } catch (IllegalStateException e) {
            AppLogger.w(TAG, "mode " + model + " unavailable: " + e.getMessage());
            return false;
        }
    }

    public static int availableMask(Context context) {
        int mask = 0;
        if (isModeAvailable(MODEL_IME, context)) mask |= 1;
        if (isModeAvailable(MODEL_SHIZUKU, context)) mask |= 2;
        if (isGamepadAvailable(context)) mask |= 4;
        return mask;
    }

    public static boolean isGamepadAvailable(Context context) {
        return ShizukuKeyInjector.isShizukuRunning()
                && ShizukuKeyInjector.isPermissionGranted();
    }

    public static int resolveModel(int savedModel, Context context) {
        boolean shizuku = isModeAvailable(MODEL_SHIZUKU, context);
        boolean ime = isModeAvailable(MODEL_IME, context);
        if (shizuku && ime) {
            return (savedModel == MODEL_SHIZUKU || savedModel == MODEL_IME) ? savedModel : MODEL_IME;
        }
        if (shizuku) return MODEL_SHIZUKU;
        if (ime) return MODEL_IME;
        return MODEL_NONE;
    }
}
